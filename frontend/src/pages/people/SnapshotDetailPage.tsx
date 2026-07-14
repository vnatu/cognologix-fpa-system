import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  Alert,
  Button,
  Collapse,
  Descriptions,
  Empty,
  Input,
  Skeleton,
  Space,
  Table,
  Tabs,
  Tag,
  Typography,
  notification,
} from 'antd';
import { ArrowLeftOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { useDateFormat } from '@/context/DateFormatContext';
import { HEADING_FONT } from '@/theme/antdTheme';
import { fetchSnapshotDetail } from './api';
import {
  IMPORT_TYPE_LABELS,
  isPayrollImportType,
  payrollTemplateLabel,
} from './constants';
import type {
  ExitedRegistryRow,
  ImportType,
  PayrollImportType,
  PayrollSnapshotRow,
  PeopleSnapshotRow,
  SnapshotDetail,
  SnapshotUploadMetadata,
} from './types';
import { formatCurrencyInr, formatVersionLabel } from './utils';

const { Title, Text } = Typography;
const { Search } = Input;

const VALID_IMPORT_TYPES = new Set<string>([
  'ZOHO_PEOPLE',
  'ZOHO_PAYROLL',
  'ZOHO_PAYROLL_FNF',
  'ZOHO_PEOPLE_EXITED',
]);

const PAYROLL_TAB_ORDER: PayrollImportType[] = [
  'ZOHO_PAYROLL',
  'ZOHO_PAYROLL_FNF',
];

function billableTag(status: string | null | undefined) {
  const isYes = status?.toUpperCase() === 'Y';
  return (
    <Tag color={isYes ? 'green' : 'default'}>{isYes ? 'Y' : 'N'}</Tag>
  );
}

function exitPrecisionLabel(precision: string | null | undefined) {
  if (precision === 'DAY_LEVEL') return 'Exact';
  if (precision === 'MONTH_LEVEL') return 'Approximate';
  return '—';
}

function uploadWarnings(upload: SnapshotUploadMetadata) {
  const items: Array<{ key: string; message: string; description: string }> = [];
  if (upload.unmappedColumns.length > 0) {
    items.push({
      key: 'unmapped',
      message: 'Unmapped columns',
      description: upload.unmappedColumns.join(', '),
    });
  }
  if (upload.missingColumns.length > 0) {
    items.push({
      key: 'missing',
      message: 'Template columns not found in file',
      description: upload.missingColumns.join(', '),
    });
  }
  if (upload.unrecognizedBuCodes.length > 0) {
    items.push({
      key: 'bu',
      message: 'Unrecognised BU codes',
      description: upload.unrecognizedBuCodes.join(', '),
    });
  }
  return items;
}

function ColumnWarningsSection({ upload }: { upload: SnapshotUploadMetadata }) {
  const warnings = uploadWarnings(upload);
  if (warnings.length === 0) return null;

  return (
    <Collapse
      size="small"
      items={[
        {
          key: 'column-warnings',
          label: `Column warnings (${warnings.length})`,
          children: (
            <Space direction="vertical" style={{ width: '100%' }} size="small">
              {warnings.map((w) => (
                <Alert
                  key={w.key}
                  type="warning"
                  showIcon
                  message={w.message}
                  description={w.description}
                />
              ))}
            </Space>
          ),
        },
      ]}
    />
  );
}

const TABLE_PAGINATION = {
  defaultPageSize: 20,
  showSizeChanger: true,
  pageSizeOptions: ['10', '20', '50', '100'],
};

export default function SnapshotDetailPage() {
  const navigate = useNavigate();
  const { formatDate, formatDateTime } = useDateFormat();
  const { periodVersionId, importType: importTypeParam } = useParams<{
    periodVersionId: string;
    importType: string;
  }>();
  const importType = VALID_IMPORT_TYPES.has(importTypeParam ?? '')
    ? (importTypeParam as ImportType)
    : null;

  const [detail, setDetail] = useState<SnapshotDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [payrollTab, setPayrollTab] = useState<PayrollImportType>(
    importType === 'ZOHO_PAYROLL_FNF' ? 'ZOHO_PAYROLL_FNF' : 'ZOHO_PAYROLL',
  );

  useEffect(() => {
    if (!periodVersionId || !importType) return;
    (async () => {
      setLoading(true);
      try {
        setDetail(await fetchSnapshotDetail(periodVersionId, importType));
      } catch {
        notification.error({ message: 'Failed to load snapshot detail' });
      } finally {
        setLoading(false);
      }
    })();
  }, [periodVersionId, importType]);

  const isPayroll = isPayrollImportType(importType ?? 'ZOHO_PEOPLE');
  const isExited = importType === 'ZOHO_PEOPLE_EXITED';

  const payrollUploadsByType = useMemo(() => {
    if (!detail) return new Map<PayrollImportType, SnapshotUploadMetadata>();
    const uploads =
      detail.payrollUploads.length > 0
        ? detail.payrollUploads
        : isPayroll
          ? [detail.upload]
          : [];
    const byType = new Map<PayrollImportType, SnapshotUploadMetadata>();
    for (const upload of uploads) {
      if (isPayrollImportType(upload.importType)) {
        byType.set(upload.importType, upload);
      }
    }
    return byType;
  }, [detail, isPayroll]);

  const availablePayrollTabs = useMemo(
    () => PAYROLL_TAB_ORDER.filter((type) => payrollUploadsByType.has(type)),
    [payrollUploadsByType],
  );

  useEffect(() => {
    if (availablePayrollTabs.length === 0) return;
    if (!availablePayrollTabs.includes(payrollTab)) {
      setPayrollTab(availablePayrollTabs[0]);
    }
  }, [availablePayrollTabs, payrollTab]);

  useEffect(() => {
    setSearch('');
  }, [payrollTab]);

  const filteredPeople = useMemo(() => {
    if (!detail) return [];
    const q = search.trim().toLowerCase();
    if (!q) return detail.peopleRows;
    return detail.peopleRows.filter(
      (r) =>
        r.employeeId.toLowerCase().includes(q) ||
        r.fullName.toLowerCase().includes(q),
    );
  }, [detail, search]);

  const filteredExited = useMemo(() => {
    if (!detail) return [];
    const q = search.trim().toLowerCase();
    if (!q) return detail.exitedRegistryRows;
    return detail.exitedRegistryRows.filter(
      (r) =>
        r.employeeId.toLowerCase().includes(q) ||
        r.fullName.toLowerCase().includes(q),
    );
  }, [detail, search]);

  const peopleColumns: ColumnsType<PeopleSnapshotRow> = [
    { title: 'Employee ID', dataIndex: 'employeeId', key: 'employeeId' },
    { title: 'Full Name', dataIndex: 'fullName', key: 'fullName' },
    { title: 'Practice Unit', dataIndex: 'practiceUnit', key: 'practiceUnit' },
    { title: 'Business Unit', dataIndex: 'businessUnit', key: 'businessUnit' },
    {
      title: 'BU Code',
      dataIndex: 'buCode',
      key: 'buCode',
      render: (v) => v ?? '—',
    },
    {
      title: 'Project Code',
      dataIndex: 'projectCode',
      key: 'projectCode',
      render: (v) => v ?? '—',
    },
    {
      title: 'Billable Status',
      dataIndex: 'billableStatus',
      key: 'billableStatus',
      render: billableTag,
    },
    {
      title: 'Job Level',
      dataIndex: 'jobLevel',
      key: 'jobLevel',
      render: (v) => v ?? '—',
    },
    {
      title: 'Title',
      dataIndex: 'title',
      key: 'title',
      render: (v) => v ?? '—',
    },
    {
      title: 'Date of Joining',
      dataIndex: 'dateOfJoining',
      key: 'dateOfJoining',
      render: (v) => (v ? formatDate(v) : '—'),
    },
  ];

  const payrollColumns: ColumnsType<PayrollSnapshotRow> = [
    { title: 'Employee No', dataIndex: 'employeeNo', key: 'employeeNo' },
    { title: 'Full Name', dataIndex: 'fullName', key: 'fullName' },
    {
      title: 'Gross Pay',
      dataIndex: 'grossPay',
      key: 'grossPay',
      render: formatCurrencyInr,
    },
    {
      title: 'Net Pay',
      dataIndex: 'netPay',
      key: 'netPay',
      render: formatCurrencyInr,
    },
    {
      title: 'CTC Per Annum',
      dataIndex: 'ctcPerAnnum',
      key: 'ctcPerAnnum',
      render: (v) => (v != null ? formatCurrencyInr(v) : '—'),
    },
  ];

  const exitedColumns: ColumnsType<ExitedRegistryRow> = [
    { title: 'Employee ID', dataIndex: 'employeeId', key: 'employeeId' },
    { title: 'Full Name', dataIndex: 'fullName', key: 'fullName' },
    {
      title: 'Exit Date',
      dataIndex: 'exitDate',
      key: 'exitDate',
      render: (v) => (v ? formatDate(v) : '—'),
    },
    {
      title: 'Exit Date Precision',
      dataIndex: 'exitDatePrecision',
      key: 'exitDatePrecision',
      render: exitPrecisionLabel,
    },
    {
      title: 'Exit Status',
      dataIndex: 'exitStatus',
      key: 'exitStatus',
      render: (status: string) => (
        <Tag color={status === 'EXITED' ? 'default' : 'green'}>{status}</Tag>
      ),
    },
  ];

  if (!periodVersionId || !importType) {
    return (
      <div style={{ padding: 24 }}>
        <Empty description="Invalid snapshot link" />
      </div>
    );
  }

  if (loading) {
    return (
      <div style={{ padding: 24 }}>
        <Skeleton active paragraph={{ rows: 12 }} />
      </div>
    );
  }

  if (!detail) {
    return (
      <div style={{ padding: 24 }}>
        <Empty description="Snapshot not found" />
        <Button
          type="link"
          icon={<ArrowLeftOutlined />}
          onClick={() => navigate(-1)}
          style={{ marginTop: 16 }}
        >
          Back
        </Button>
      </div>
    );
  }

  const periodLabel = formatVersionLabel(
    detail.periodMonth,
    detail.periodYear,
    detail.versionNumber,
  );

  const renderUploadMeta = (upload: SnapshotUploadMetadata) => (
    <Descriptions bordered size="small" column={{ xs: 1, sm: 2, md: 3 }}>
      <Descriptions.Item label="Uploaded by">{upload.uploadedBy}</Descriptions.Item>
      <Descriptions.Item label="Uploaded at">
        {formatDateTime(upload.uploadedAt)}
      </Descriptions.Item>
      <Descriptions.Item label="Original filename">
        {upload.originalFilename}
      </Descriptions.Item>
      <Descriptions.Item label="Row count">{upload.rowCount}</Descriptions.Item>
    </Descriptions>
  );

  const payrollRowsForTab = (type: PayrollImportType) => {
    if (!detail) return [];
    const rows = detail.payrollRows.filter((r) => r.importType === type);
    const q = search.trim().toLowerCase();
    if (!q) return rows;
    return rows.filter(
      (r) =>
        r.employeeNo.toLowerCase().includes(q) ||
        r.fullName.toLowerCase().includes(q),
    );
  };

  const renderPayrollTab = (type: PayrollImportType) => {
    const upload = payrollUploadsByType.get(type);
    if (!upload) {
      return <Empty description={`No ${payrollTemplateLabel(type)} upload for this version`} />;
    }
    const rows = payrollRowsForTab(type);

    return (
      <Space direction="vertical" size="middle" style={{ width: '100%' }}>
        {renderUploadMeta(upload)}
        <ColumnWarningsSection upload={upload} />
        <Search
          placeholder="Search by employee no or name"
          allowClear
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          style={{ maxWidth: 400 }}
        />
        {rows.length === 0 ? (
          <Empty description="No snapshot rows" />
        ) : (
          <Table
            rowKey="id"
            columns={payrollColumns}
            dataSource={rows}
            pagination={TABLE_PAGINATION}
            scroll={{ x: 'max-content' }}
          />
        )}
      </Space>
    );
  };

  return (
    <div style={{ padding: 24 }}>
      <Space direction="vertical" size="large" style={{ width: '100%' }}>
        <Space>
          <Button icon={<ArrowLeftOutlined />} onClick={() => navigate(-1)}>
            Back
          </Button>
        </Space>

        <div>
          <Title level={4} style={{ fontFamily: HEADING_FONT, marginBottom: 4 }}>
            Snapshot Detail
          </Title>
          <Text type="secondary">
            {periodLabel} ·{' '}
            {isPayroll ? 'Zoho Payroll' : IMPORT_TYPE_LABELS[detail.importType]}
          </Text>
        </div>

        {isPayroll ? (
          availablePayrollTabs.length === 0 ? (
            <Empty description="No payroll uploads for this version" />
          ) : (
            <Tabs
              activeKey={payrollTab}
              onChange={(key) => setPayrollTab(key as PayrollImportType)}
              items={availablePayrollTabs.map((type) => ({
                key: type,
                label: `${payrollTemplateLabel(type)} (${payrollUploadsByType.get(type)?.rowCount ?? 0})`,
                children: renderPayrollTab(type),
              }))}
            />
          )
        ) : (
          <Space direction="vertical" size="middle" style={{ width: '100%' }}>
            {renderUploadMeta(detail.upload)}
            <ColumnWarningsSection upload={detail.upload} />
            <Search
              placeholder="Search by employee ID or name"
              allowClear
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              style={{ maxWidth: 400 }}
            />
            {isExited ? (
              filteredExited.length === 0 ? (
                <Empty description="No snapshot rows" />
              ) : (
                <Table
                  rowKey="id"
                  columns={exitedColumns}
                  dataSource={filteredExited}
                  pagination={TABLE_PAGINATION}
                  scroll={{ x: 'max-content' }}
                />
              )
            ) : filteredPeople.length === 0 ? (
              <Empty description="No snapshot rows" />
            ) : (
              <Table
                rowKey="id"
                columns={peopleColumns}
                dataSource={filteredPeople}
                pagination={TABLE_PAGINATION}
                scroll={{ x: 'max-content' }}
              />
            )}
          </Space>
        )}
      </Space>
    </div>
  );
}
