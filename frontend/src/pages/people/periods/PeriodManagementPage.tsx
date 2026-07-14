import { Link } from 'react-router-dom';
import { useCallback, useEffect, useRef, useState } from 'react';
import {
  Button,
  Empty,
  Form,
  InputNumber,
  Modal,
  Select,
  Skeleton,
  Space,
  Spin,
  Table,
  Tag,
  Typography,
  notification,
} from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { HEADING_FONT } from '@/theme/antdTheme';
import {
  buildMaster,
  createPeriod,
  createPeriodVersion,
  fetchPeriods,
  fetchPeriodVersionDetail,
  finalisePeriod,
} from '../api';
import { IMPORT_TYPE_LABELS, MONTH_NAMES, PERIOD_STATUS_LABELS, isPayrollImportType, payrollSnapshotDetailPath, snapshotDetailPath } from '../constants';
import type { PeriodResponse, PeriodVersionSummary, SnapshotUploadSummary } from '../types';
import { useDateFormat } from '@/context/DateFormatContext';
import {
  EXPAND_PERIOD_AFTER_UPLOAD_KEY,
  formatPeriodLabel,
  isActiveVersion,
  latestVersionForPeriod,
  periodStatusBadgeColor,
} from '../utils';

const { Text } = Typography;

interface VersionRow extends PeriodVersionSummary {
  periodId: string;
  key: string;
  isActive: boolean;
}

export default function PeriodManagementPage() {
  const { formatDateTime } = useDateFormat();
  const [periods, setPeriods] = useState<PeriodResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [modalOpen, setModalOpen] = useState(false);
  const [creating, setCreating] = useState(false);
  const [buildingId, setBuildingId] = useState<string | null>(null);
  const [finalisingId, setFinalisingId] = useState<string | null>(null);
  const [versionCreatingId, setVersionCreatingId] = useState<string | null>(null);
  const [finaliseTarget, setFinaliseTarget] = useState<{
    periodId: string;
    version: PeriodVersionSummary;
  } | null>(null);
  const [expandedPeriodIds, setExpandedPeriodIds] = useState<string[]>([]);
  const [form] = Form.useForm();
  const [versionUploads, setVersionUploads] = useState<
    Record<string, SnapshotUploadSummary[]>
  >({});
  const [loadingVersionIds, setLoadingVersionIds] = useState<Set<string>>(
    new Set(),
  );
  const fetchedUploadsRef = useRef<Set<string>>(new Set());

  const loadVersionUploads = useCallback(
    async (periodId: string, versionId: string) => {
      if (fetchedUploadsRef.current.has(versionId)) return;
      fetchedUploadsRef.current.add(versionId);
      setLoadingVersionIds((prev) => new Set(prev).add(versionId));
      try {
        const detail = await fetchPeriodVersionDetail(periodId, versionId);
        setVersionUploads((prev) => ({ ...prev, [versionId]: detail.uploads }));
      } catch {
        notification.error({ message: 'Failed to load version uploads' });
        setVersionUploads((prev) => ({ ...prev, [versionId]: [] }));
      } finally {
        setLoadingVersionIds((prev) => {
          const next = new Set(prev);
          next.delete(versionId);
          return next;
        });
      }
    },
    [],
  );

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const nextPeriods = await fetchPeriods();
      setPeriods(nextPeriods);
      const expandId = sessionStorage.getItem(EXPAND_PERIOD_AFTER_UPLOAD_KEY);
      if (expandId && nextPeriods.some((p) => p.id === expandId)) {
        setExpandedPeriodIds([expandId]);
        sessionStorage.removeItem(EXPAND_PERIOD_AFTER_UPLOAD_KEY);
        const period = nextPeriods.find((p) => p.id === expandId);
        period?.versions.forEach((v) => loadVersionUploads(expandId, v.id));
      }
    } catch {
      notification.error({ message: 'Failed to load periods' });
    } finally {
      setLoading(false);
    }
  }, [loadVersionUploads]);

  useEffect(() => {
    load();
  }, [load]);

  const handleCreatePeriod = async () => {
    try {
      const values = await form.validateFields();
      setCreating(true);
      await createPeriod(values);
      notification.success({ message: 'Period created' });
      setModalOpen(false);
      form.resetFields();
      load();
    } catch {
      notification.error({ message: 'Failed to create period' });
    } finally {
      setCreating(false);
    }
  };

  const handleBuildMaster = async (periodId: string, versionId: string) => {
    setBuildingId(versionId);
    try {
      await buildMaster(periodId, versionId);
      notification.success({ message: 'Master records built' });
      load();
    } catch {
      notification.error({ message: 'Failed to build master' });
    } finally {
      setBuildingId(null);
    }
  };

  const handleFinalise = async () => {
    if (!finaliseTarget) return;
    setFinalisingId(finaliseTarget.version.id);
    try {
      await finalisePeriod(finaliseTarget.periodId, finaliseTarget.version.id);
      notification.success({ message: 'Period version finalised' });
      setFinaliseTarget(null);
      load();
    } catch {
      notification.error({ message: 'Failed to finalise period' });
    } finally {
      setFinalisingId(null);
    }
  };

  const handleNewVersion = async (periodId: string) => {
    setVersionCreatingId(periodId);
    try {
      await createPeriodVersion(periodId);
      notification.success({ message: 'New version created' });
      load();
    } catch {
      notification.error({ message: 'Failed to create new version' });
    } finally {
      setVersionCreatingId(null);
    }
  };

  const versionColumns: ColumnsType<VersionRow> = [
    {
      title: 'Version',
      dataIndex: 'versionNumber',
      key: 'versionNumber',
      render: (n: number, version) => (
        <Space size="small">
          <Text
            type={version.status === 'SUPERSEDED' ? 'secondary' : undefined}
            strong={version.isActive}
          >
            v{n}
          </Text>
          {version.status === 'SUPERSEDED' && <Tag color="default">Superseded</Tag>}
          {version.isActive && version.status !== 'SUPERSEDED' && (
            <Tag color="processing">Current</Tag>
          )}
        </Space>
      ),
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      render: (status: string, version) => (
        <Tag color={periodStatusBadgeColor(status as never)}>
          {PERIOD_STATUS_LABELS[status] ?? status}
        </Tag>
      ),
    },
    {
      title: 'Created by',
      dataIndex: 'createdBy',
      key: 'createdBy',
      render: (v: string | null) => v ?? '—',
    },
    {
      title: 'Created at',
      dataIndex: 'createdAt',
      key: 'createdAt',
      render: (v: string) => formatDateTime(v),
    },
    {
      title: 'Finalised at',
      dataIndex: 'finalisedAt',
      key: 'finalisedAt',
      render: (v: string | null) => formatDateTime(v),
    },
    {
      title: 'View Snapshots',
      key: 'snapshots',
      render: (_, version) => {
        if (version.status === 'SUPERSEDED') {
          return <Text type="secondary">—</Text>;
        }
        const uploads = versionUploads[version.id];
        const uploadsLoading = loadingVersionIds.has(version.id);
        if (uploadsLoading) {
          return <Text type="secondary">Loading…</Text>;
        }
        if (!uploads?.length) {
          return <Text type="secondary">—</Text>;
        }
        const nonPayrollUploads = uploads.filter(
          (u) => !isPayrollImportType(u.importType),
        );
        const hasPayrollUpload = uploads.some((u) =>
          isPayrollImportType(u.importType),
        );
        return (
          <Space wrap>
            {nonPayrollUploads.map((u) => (
              <Link
                key={u.id}
                to={snapshotDetailPath(version.id, u.importType)}
              >
                <Button size="small">
                  {IMPORT_TYPE_LABELS[u.importType]}
                </Button>
              </Link>
            ))}
            {hasPayrollUpload && (
              <Link to={payrollSnapshotDetailPath(version.id)}>
                <Button size="small">View Payroll</Button>
              </Link>
            )}
          </Space>
        );
      },
    },
    {
      title: 'Actions',
      key: 'actions',
      render: (_, version) =>
        version.status === 'SUPERSEDED' ? null : (
        <Space wrap>
          <Button
            size="small"
            disabled={version.status !== 'SNAPSHOTS_UPLOADED'}
            loading={buildingId === version.id}
            onClick={() => handleBuildMaster(version.periodId, version.id)}
          >
            Build Master
          </Button>
          <Button
            size="small"
            disabled={version.status !== 'MASTER_BUILT'}
            onClick={() =>
              setFinaliseTarget({ periodId: version.periodId, version })
            }
          >
            Finalise
          </Button>
          <Button
            size="small"
            disabled={version.status !== 'FINALISED'}
            loading={versionCreatingId === version.periodId}
            onClick={() => handleNewVersion(version.periodId)}
          >
            New Version
          </Button>
        </Space>
      ),
    },
  ];

  const periodColumns: ColumnsType<PeriodResponse> = [
    {
      title: 'Period',
      key: 'period',
      render: (_, p) => formatPeriodLabel(p.periodMonth, p.periodYear),
    },
    {
      title: 'Versions',
      key: 'versions',
      render: (_, p) => p.versions.length,
    },
    {
      title: 'Latest Version Status',
      key: 'latestStatus',
      render: (_, p) => {
        const latest = latestVersionForPeriod(p);
        if (!latest) return '—';
        return (
          <Tag color={periodStatusBadgeColor(latest.status)}>
            {PERIOD_STATUS_LABELS[latest.status]}
          </Tag>
        );
      },
    },
    {
      title: 'Actions',
      key: 'actions',
      render: () => null,
    },
  ];

  if (loading) {
    return (
      <div style={{ padding: 24 }}>
        <Skeleton active paragraph={{ rows: 8 }} />
      </div>
    );
  }

  return (
    <div style={{ padding: 24 }}>
      <Space
        style={{
          width: '100%',
          justifyContent: 'space-between',
          marginBottom: 20,
        }}
      >
        <h2 style={{ fontFamily: HEADING_FONT, margin: 0, fontSize: 18 }}>
          Period Management
        </h2>
        <Button type="primary" icon={<PlusOutlined />} onClick={() => setModalOpen(true)}>
          New Period
        </Button>
      </Space>

      {periods.length === 0 ? (
        <Empty description="No periods yet" />
      ) : (
        <Table
          rowKey="id"
          columns={periodColumns}
          dataSource={periods}
          pagination={false}
          expandable={{
            expandedRowKeys: expandedPeriodIds,
            onExpandedRowsChange: (keys) =>
              setExpandedPeriodIds(keys.map(String)),
            onExpand: (expanded, period) => {
              if (expanded) {
                period.versions.forEach((v) =>
                  loadVersionUploads(period.id, v.id),
                );
              }
            },
            expandedRowRender: (period) => {
              const versionRows: VersionRow[] = [...period.versions]
                .sort((a, b) => b.versionNumber - a.versionNumber)
                .map((v) => ({
                  ...v,
                  periodId: period.id,
                  key: v.id,
                  isActive: isActiveVersion(period, v),
                }));
              return (
                <Spin spinning={buildingId !== null && versionRows.some((v) => v.id === buildingId)}>
                  <Table
                    rowKey="id"
                    columns={versionColumns}
                    dataSource={versionRows}
                    pagination={false}
                    size="small"
                    rowClassName={(version) =>
                      version.status === 'SUPERSEDED'
                        ? 'period-version-superseded'
                        : version.isActive
                          ? 'period-version-active'
                          : ''
                    }
                    onRow={(version) => ({
                      style:
                        version.status === 'SUPERSEDED'
                          ? { color: 'rgba(0, 0, 0, 0.45)' }
                          : version.isActive
                            ? { backgroundColor: '#f6ffed' }
                            : undefined,
                    })}
                  />
                </Spin>
              );
            },
          }}
        />
      )}

      <Modal
        title="New Period"
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={handleCreatePeriod}
        confirmLoading={creating}
        okText="Create"
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="periodMonth"
            label="Month"
            rules={[{ required: true, message: 'Select a month' }]}
          >
            <Select
              options={MONTH_NAMES.map((name, i) => ({
                label: name,
                value: i + 1,
              }))}
            />
          </Form.Item>
          <Form.Item
            name="periodYear"
            label="Year"
            rules={[{ required: true, message: 'Enter a year' }]}
          >
            <InputNumber min={2020} max={2099} style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="Finalise period version?"
        open={!!finaliseTarget}
        onCancel={() => setFinaliseTarget(null)}
        onOk={handleFinalise}
        confirmLoading={finalisingId !== null}
        okText="Finalise"
      >
        Finalising this version will lock it permanently. Are you sure?
      </Modal>
    </div>
  );
}
