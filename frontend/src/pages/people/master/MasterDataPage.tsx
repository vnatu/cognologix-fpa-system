import { useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import {
  Button,
  Checkbox,
  Descriptions,
  Drawer,
  Empty,
  Input,
  List,
  Popover,
  Select,
  Skeleton,
  Space,
  Statistic,
  Table,
  Tag,
  Typography,
  notification,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { WarningOutlined } from '@ant-design/icons';
import { HEADING_FONT } from '@/theme/antdTheme';
import {
  fetchMasterRecords,
  fetchMasterSummary,
  fetchPeriods,
  fetchRegistry,
  reconcileMaster,
} from '../api';
import {
  DATA_QUALITY_FLAG_LABELS,
  PERIOD_STATUS_LABELS,
  RECONCILIATION_STATUS_LABELS,
} from '../constants';
import type {
  EmployeeRegistryEntry,
  MasterRecord,
  MasterSummary,
  PeriodResponse,
  PeriodVersionOption,
  ReconciliationStatus,
} from '../types';
import { useDateFormat } from '@/context/DateFormatContext';
import {
  buildMasterVersionOptions,
  formatCurrencyInr,
  getClassification,
  periodStatusBadgeColor,
  pickDefaultMasterVersion,
  reconciliationTagColor,
  totalGrossPay,
  totalHeadcount,
} from '../utils';

const { Search } = Input;
const { Title, Text } = Typography;

export default function MasterDataPage() {
  const { formatDate } = useDateFormat();
  const [searchParams] = useSearchParams();
  const [loading, setLoading] = useState(true);
  const [periods, setPeriods] = useState<PeriodResponse[]>([]);
  const [showSuperseded, setShowSuperseded] = useState(false);
  const [selectedVersion, setSelectedVersion] =
    useState<PeriodVersionOption | null>(null);
  const [records, setRecords] = useState<MasterRecord[]>([]);
  const [summary, setSummary] = useState<MasterSummary | null>(null);
  const [recordsLoading, setRecordsLoading] = useState(false);

  const initialStatus = searchParams.get('reconciliationStatus');
  const [statusFilter, setStatusFilter] = useState<ReconciliationStatus[]>(
    initialStatus ? [initialStatus as ReconciliationStatus] : [],
  );
  const [classificationFilter, setClassificationFilter] = useState<string[]>([]);
  const [buFilter, setBuFilter] = useState<string[]>([]);
  const [warningsOnlyFilter, setWarningsOnlyFilter] = useState(
    searchParams.get('hasWarnings') === 'true',
  );

  const [reconcileRecord, setReconcileRecord] = useState<MasterRecord | null>(
    null,
  );
  const [registry, setRegistry] = useState<EmployeeRegistryEntry[]>([]);
  const [searchQuery, setSearchQuery] = useState('');
  const [reconciling, setReconciling] = useState(false);
  const [openWarningId, setOpenWarningId] = useState<string | null>(null);

  useEffect(() => {
    const status = searchParams.get('reconciliationStatus');
    if (status) {
      setStatusFilter([status as ReconciliationStatus]);
    }
    if (searchParams.get('hasWarnings') === 'true') {
      setWarningsOnlyFilter(true);
    }
  }, [searchParams]);

  const versionOptions = useMemo(
    () => buildMasterVersionOptions(periods, showSuperseded),
    [periods, showSuperseded],
  );

  useEffect(() => {
    (async () => {
      setLoading(true);
      try {
        const loaded = await fetchPeriods();
        setPeriods(loaded);
        setSelectedVersion(pickDefaultMasterVersion(loaded));
      } catch {
        notification.error({ message: 'Failed to load periods' });
      } finally {
        setLoading(false);
      }
    })();
  }, []);

  useEffect(() => {
    if (!selectedVersion) return;
    (async () => {
      setRecordsLoading(true);
      try {
        const [recs, sum] = await Promise.all([
          fetchMasterRecords(selectedVersion.periodVersionId),
          fetchMasterSummary(selectedVersion.periodVersionId),
        ]);
        setRecords(recs);
        setSummary(sum);
      } catch {
        notification.error({ message: 'Failed to load master data' });
      } finally {
        setRecordsLoading(false);
      }
    })();
  }, [selectedVersion]);

  const businessUnits = useMemo(
    () =>
      [...new Set(records.map((r) => r.businessUnit).filter(Boolean))].sort() as string[],
    [records],
  );

  const classifications = useMemo(
    () => [...new Set(records.map(getClassification).filter((c) => c !== '—'))],
    [records],
  );

  const warningCount = useMemo(
    () => records.filter((r) => r.hasWarnings).length,
    [records],
  );

  const filteredRecords = useMemo(() => {
    return records.filter((r) => {
      if (warningsOnlyFilter && !r.hasWarnings) {
        return false;
      }
      if (
        statusFilter.length > 0 &&
        !statusFilter.includes(r.reconciliationStatus)
      ) {
        return false;
      }
      const cls = getClassification(r);
      if (classificationFilter.length > 0 && !classificationFilter.includes(cls)) {
        return false;
      }
      if (
        buFilter.length > 0 &&
        (!r.businessUnit || !buFilter.includes(r.businessUnit))
      ) {
        return false;
      }
      return true;
    });
  }, [records, statusFilter, classificationFilter, buFilter, warningsOnlyFilter]);

  const formatDataQualityWarnings = (flags: string | null) => {
    if (!flags) return [];
    return flags
      .split(',')
      .map((f) => f.trim())
      .filter(Boolean)
      .map((flag) => DATA_QUALITY_FLAG_LABELS[flag] ?? flag);
  };

  const renderWarningIndicator = (record: MasterRecord) => {
    if (!record.hasWarnings) return null;
    const messages = formatDataQualityWarnings(record.dataQualityFlags);
    return (
      <Popover
        title="Data quality issues"
        open={openWarningId === record.id}
        onOpenChange={(open) => setOpenWarningId(open ? record.id : null)}
        content={
          <ul style={{ margin: 0, paddingLeft: 20 }}>
            {messages.map((msg) => (
              <li key={msg}>{msg}</li>
            ))}
          </ul>
        }
        trigger="click"
      >
        <WarningOutlined
          style={{ color: '#faad14', fontSize: 16, cursor: 'pointer' }}
          onClick={(e) => e.stopPropagation()}
        />
      </Popover>
    );
  };

  const openReconcile = async (record: MasterRecord) => {
    setReconcileRecord(record);
    setSearchQuery('');
    try {
      setRegistry(await fetchRegistry());
    } catch {
      notification.error({ message: 'Failed to load employee registry' });
    }
  };

  const searchResults = useMemo(() => {
    const q = searchQuery.trim().toLowerCase();
    if (!q) return [];
    return registry
      .filter(
        (e) =>
          e.employeeId.toLowerCase().includes(q) ||
          e.fullName.toLowerCase().includes(q),
      )
      .slice(0, 20);
  }, [registry, searchQuery]);

  const handleReconcile = async (employee: EmployeeRegistryEntry) => {
    if (!reconcileRecord?.payrollSnapshotId || !selectedVersion) return;
    setReconciling(true);
    try {
      await reconcileMaster(selectedVersion.periodVersionId, {
        payrollSnapshotId: reconcileRecord.payrollSnapshotId,
        employeeRegistryId: employee.id,
      });
      notification.success({ message: 'Mapping confirmed' });
      setReconcileRecord(null);
      const [recs, sum] = await Promise.all([
        fetchMasterRecords(selectedVersion.periodVersionId),
        fetchMasterSummary(selectedVersion.periodVersionId),
      ]);
      setRecords(recs);
      setSummary(sum);
    } catch {
      notification.error({ message: 'Failed to confirm mapping' });
    } finally {
      setReconciling(false);
    }
  };

  const columns: ColumnsType<MasterRecord> = [
    {
      title: '',
      key: 'warnings',
      width: 40,
      render: (_, record) => renderWarningIndicator(record),
    },
    { title: 'Employee ID', dataIndex: 'employeeId', key: 'employeeId' },
    { title: 'Full Name', dataIndex: 'fullName', key: 'fullName' },
    {
      title: 'Practice Unit',
      dataIndex: 'practiceUnit',
      key: 'practiceUnit',
      render: (v) => v ?? '—',
    },
    {
      title: 'Business Unit',
      dataIndex: 'businessUnit',
      key: 'businessUnit',
      render: (v) => v ?? '—',
    },
    {
      title: 'Classification',
      key: 'classification',
      render: (_, r) => getClassification(r),
    },
    {
      title: 'Gross Pay',
      dataIndex: 'grossPay',
      key: 'grossPay',
      render: formatCurrencyInr,
    },
    {
      title: 'Reconciliation Status',
      dataIndex: 'reconciliationStatus',
      key: 'reconciliationStatus',
      render: (status: ReconciliationStatus) => (
        <Tag color={reconciliationTagColor(status)}>
          {RECONCILIATION_STATUS_LABELS[status]}
        </Tag>
      ),
    },
  ];

  const isFinalised = selectedVersion?.status === 'FINALISED';

  if (loading) {
    return (
      <div style={{ padding: 24 }}>
        <Skeleton active paragraph={{ rows: 10 }} />
      </div>
    );
  }

  return (
    <div style={{ padding: 24 }}>
      <Title level={4} style={{ fontFamily: HEADING_FONT, marginBottom: 20 }}>
        Master Data
      </Title>

      <Space align="center" wrap style={{ marginBottom: 20 }}>
        <Select
          style={{ width: '100%', minWidth: 280, maxWidth: 420 }}
          placeholder="Select period version"
          value={selectedVersion?.periodVersionId}
          options={versionOptions.map((o) => ({
            label: (
              <Space>
                <span>{o.label}</span>
                <Tag color={periodStatusBadgeColor(o.status)}>
                  {PERIOD_STATUS_LABELS[o.status]}
                </Tag>
              </Space>
            ),
            value: o.periodVersionId,
          }))}
          onChange={(id) =>
            setSelectedVersion(
              versionOptions.find((o) => o.periodVersionId === id) ?? null,
            )
          }
        />
        <Checkbox
          checked={showSuperseded}
          onChange={(e) => {
            const checked = e.target.checked;
            setShowSuperseded(checked);
            if (!checked && selectedVersion?.status === 'SUPERSEDED') {
              setSelectedVersion(pickDefaultMasterVersion(periods));
            }
          }}
        >
          Show Superseded
        </Checkbox>
      </Space>

      {summary && (
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fill, minmax(140px, 1fr))',
            gap: 12,
            marginBottom: 20,
          }}
        >
          {[
            { label: 'Total HC', value: totalHeadcount(summary) },
            { label: 'Billable HC', value: summary.billable.headcount },
            { label: 'Bench HC', value: summary.bench.headcount },
            { label: 'Support HC', value: summary.support.headcount },
            { label: 'Leadership HC', value: summary.leadership.headcount },
            { label: 'Management HC', value: summary.management.headcount },
          ].map((card) => (
            <div
              key={card.label}
              style={{
                background: 'var(--ant-color-bg-container)',
                border: '1px solid var(--ant-color-border)',
                borderRadius: 8,
                padding: '8px 16px',
              }}
            >
              <Statistic title={card.label} value={card.value} />
            </div>
          ))}
          <div
            style={{
              background: 'var(--ant-color-bg-container)',
              border: '1px solid var(--ant-color-border)',
              borderRadius: 8,
              padding: '8px 16px',
            }}
          >
            <Statistic
              title="Total Gross Pay"
              value={totalGrossPay(summary)}
              formatter={(v) => formatCurrencyInr(Number(v))}
            />
          </div>
          <div
            role="button"
            tabIndex={0}
            onClick={() => setWarningsOnlyFilter(true)}
            onKeyDown={(e) => {
              if (e.key === 'Enter' || e.key === ' ') {
                setWarningsOnlyFilter(true);
              }
            }}
            style={{
              background: warningCount > 0 ? '#fffbe6' : 'var(--ant-color-bg-container)',
              border:
                warningCount > 0
                  ? '1px solid #ffe58f'
                  : '1px solid var(--ant-color-border)',
              borderRadius: 8,
              padding: '8px 16px',
              cursor: warningCount > 0 ? 'pointer' : 'default',
            }}
          >
            <Statistic
              title="Data Quality Issues"
              value={warningCount}
              suffix="employees"
              valueStyle={warningCount > 0 ? { color: '#d48806' } : undefined}
            />
          </div>
        </div>
      )}

      <Space wrap style={{ marginBottom: 16 }}>
        <Select
          mode="multiple"
          allowClear
          placeholder="Reconciliation status"
          style={{ minWidth: 200 }}
          value={statusFilter}
          onChange={setStatusFilter}
          options={Object.entries(RECONCILIATION_STATUS_LABELS).map(
            ([value, label]) => ({ value, label }),
          )}
        />
        <Select
          mode="multiple"
          allowClear
          placeholder="Classification"
          style={{ minWidth: 180 }}
          value={classificationFilter}
          onChange={setClassificationFilter}
          options={classifications.map((c) => ({ value: c, label: c }))}
        />
        <Select
          mode="multiple"
          allowClear
          placeholder="Business Unit"
          style={{ minWidth: 200 }}
          value={buFilter}
          onChange={setBuFilter}
          options={businessUnits.map((bu) => ({ value: bu, label: bu }))}
        />
        <Select
          allowClear
          placeholder="Has Warnings"
          style={{ minWidth: 160 }}
          value={warningsOnlyFilter ? 'warnings' : undefined}
          onChange={(value) => setWarningsOnlyFilter(value === 'warnings')}
          options={[{ value: 'warnings', label: 'Has Warnings' }]}
        />
      </Space>

      {recordsLoading ? (
        <Skeleton active paragraph={{ rows: 8 }} />
      ) : filteredRecords.length === 0 ? (
        <Empty description="No master records for this version" />
      ) : (
        <Table
          rowKey="id"
          columns={columns}
          dataSource={filteredRecords}
          pagination={{ defaultPageSize: 25, showSizeChanger: true, pageSizeOptions: ['10', '25', '50', '100'] }}
          onRow={(record) => ({
            onClick: () => {
              if (record.hasWarnings) {
                setOpenWarningId(record.id);
              }
              if (
                record.reconciliationStatus === 'UNMATCHED' &&
                !isFinalised
              ) {
                openReconcile(record);
              }
            },
            style: {
              cursor:
                record.reconciliationStatus === 'UNMATCHED' && !isFinalised
                  ? 'pointer'
                  : 'default',
            },
          })}
        />
      )}

      <Drawer
        title="Reconcile unmatched payroll row"
        open={!!reconcileRecord}
        onClose={() => setReconcileRecord(null)}
        width="40%"
      >
        {reconcileRecord && (
          <Space direction="vertical" size="large" style={{ width: '100%' }}>
            <Descriptions column={1} size="small" title="Payroll snapshot">
              <Descriptions.Item label="Employee No">
                {reconcileRecord.employeeId}
              </Descriptions.Item>
              <Descriptions.Item label="Name on payroll row">
                {reconcileRecord.fullName}
              </Descriptions.Item>
              <Descriptions.Item label="Gross Pay">
                {formatCurrencyInr(reconcileRecord.grossPay)}
              </Descriptions.Item>
            </Descriptions>

            <div>
              <Text strong>Search employee registry</Text>
              <Search
                placeholder="Search by name or employee ID"
                style={{ marginTop: 8 }}
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                allowClear
              />
            </div>

            {searchQuery.trim() ? (
              <List
                dataSource={searchResults}
                locale={{ emptyText: 'No matching employees' }}
                renderItem={(emp) => (
                  <List.Item
                    actions={[
                      <Button
                        key="confirm"
                        type="primary"
                        size="small"
                        loading={reconciling}
                        onClick={() => handleReconcile(emp)}
                      >
                        Confirm Mapping
                      </Button>,
                    ]}
                  >
                    <List.Item.Meta
                      title={`${emp.employeeId} — ${emp.fullName}`}
                      description={`DOJ: ${emp.dateOfJoining ? formatDate(emp.dateOfJoining) : '—'} · ${emp.exitStatus}`}
                    />
                  </List.Item>
                )}
              />
            ) : (
              <Text type="secondary">
                Enter a name or employee ID to search the registry.
              </Text>
            )}
          </Space>
        )}
      </Drawer>
    </div>
  );
}
