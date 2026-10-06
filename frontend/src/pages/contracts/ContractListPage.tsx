import { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import {
  Button,
  DatePicker,
  Input,
  Select,
  Skeleton,
  Space,
  Table,
  Tag,
  Typography,
  theme,
  notification,
} from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import dayjs, { type Dayjs } from 'dayjs';
import { AdminGate } from '@/components/AdminGate';
import { HEADING_FONT } from '@/theme/antdTheme';
import { useDateFormat } from '@/context/DateFormatContext';
import { formatCurrency } from '@/utils/formatDate';
import { apiError, fetchContractTypes, fetchContracts } from './api';
import ContractFormModal from './ContractFormModal';
import type { ContractStatus, ContractSummary, ContractType, PaperType } from './types';

const { Title, Text } = Typography;

const STATUS_COLOR: Record<ContractStatus, string> = {
  DRAFT: 'default',
  ACTIVE: 'success',
  EXPIRED: 'warning',
  TERMINATED: 'error',
};

function paperLabel(value: PaperType): string {
  return value === 'THIRD_PARTY' ? 'Third Party' : 'Own';
}

export default function ContractListPage() {
  const navigate = useNavigate();
  const [params, setParams] = useSearchParams();
  const { token } = theme.useToken();
  const { formatDate } = useDateFormat();
  const [loading, setLoading] = useState(true);
  const [rows, setRows] = useState<ContractSummary[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [types, setTypes] = useState<ContractType[]>([]);
  const [search, setSearch] = useState('');
  const [typeId, setTypeId] = useState<string | undefined>();
  const [paperType, setPaperType] = useState<PaperType | undefined>();
  const [status, setStatus] = useState<ContractStatus | undefined>();
  const [expiry, setExpiry] = useState<[Dayjs, Dayjs] | null>(null);
  const [modalOpen, setModalOpen] = useState(false);

  useEffect(() => {
    fetchContractTypes()
      .then(setTypes)
      .catch(() => notification.error({ message: 'Failed to load contract types' }));
  }, []);

  useEffect(() => {
    const from = params.get('expiryFrom');
    const to = params.get('expiryTo');
    if (from && to) {
      setExpiry([dayjs(from), dayjs(to)]);
      setPage(1);
    }
  }, [params]);

  useEffect(() => {
    setLoading(true);
    fetchContracts({
      page: page - 1,
      size: 20,
      search: search || undefined,
      typeId,
      paperType,
      status,
      expiryFrom: expiry?.[0].format('YYYY-MM-DD'),
      expiryTo: expiry?.[1].format('YYYY-MM-DD'),
    })
      .then((result) => {
        setRows(result.content);
        setTotal(result.totalElements);
      })
      .catch((error) => notification.error({ message: apiError(error, 'Failed to load contracts') }))
      .finally(() => setLoading(false));
  }, [page, search, typeId, paperType, status, expiry]);

  const daysColor = (row: ContractSummary) => {
    if (row.evergreen || row.daysRemaining == null) return token.colorTextDescription;
    if (row.daysRemaining < 30) return token.colorError;
    if (row.daysRemaining < 60) return token.colorWarning;
    return token.colorSuccess;
  };

  const columns: ColumnsType<ContractSummary> = [
    { title: 'Contract #', dataIndex: 'contractNumber', width: 140 },
    { title: 'Title', dataIndex: 'title' },
    { title: 'Party / Client', dataIndex: 'partyDisplayName' },
    { title: 'Type', dataIndex: 'contractTypeName', width: 200 },
    {
      title: 'Paper Type',
      dataIndex: 'paperType',
      width: 130,
      render: (value: PaperType) => paperLabel(value),
    },
    {
      title: 'Status',
      dataIndex: 'status',
      width: 130,
      render: (value: ContractStatus) => <Tag color={STATUS_COLOR[value]}>{value}</Tag>,
    },
    {
      title: 'Effective',
      dataIndex: 'effectiveDate',
      width: 130,
      render: (value: string | null) => formatDate(value),
    },
    {
      title: 'Expiry',
      dataIndex: 'expiryDate',
      width: 130,
      render: (value: string | null, row) => (row.evergreen ? '—' : formatDate(value)),
    },
    {
      title: 'Days Remaining',
      key: 'days',
      width: 140,
      render: (_, row) => (
        <Text style={{ color: daysColor(row), fontWeight: 600 }}>
          {row.evergreen ? 'Evergreen' : row.daysRemaining == null ? '—' : row.daysRemaining}
        </Text>
      ),
    },
    {
      title: 'Value',
      dataIndex: 'contractValue',
      width: 140,
      render: (value: number | null, row) =>
        value == null ? '—' : `${row.billingCurrency ?? ''} ${formatCurrency(value)}`.trim(),
    },
  ];

  return (
    <div style={{ padding: 28 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <Title level={3} style={{ fontFamily: HEADING_FONT, marginTop: 0 }}>
          All Contracts
        </Title>
        <AdminGate fallback="hide">
          <Button type="primary" icon={<PlusOutlined />} onClick={() => setModalOpen(true)}>
            Add Contract
          </Button>
        </AdminGate>
      </div>

      <Space wrap style={{ marginBottom: 16 }}>
        <Input.Search
          allowClear
          placeholder="Search title or party"
          style={{ width: 240 }}
          onSearch={(value) => {
            setPage(1);
            setSearch(value);
          }}
        />
        <Select
          allowClear
          placeholder="Contract type"
          style={{ width: 220 }}
          options={types.map((type) => ({ value: type.id, label: type.displayName }))}
          value={typeId}
          onChange={(value) => {
            setPage(1);
            setTypeId(value);
          }}
        />
        <Select
          allowClear
          placeholder="Paper type"
          style={{ width: 160 }}
          options={[
            { value: 'THIRD_PARTY', label: 'Third Party' },
            { value: 'OWN', label: 'Own' },
          ]}
          value={paperType}
          onChange={(value) => {
            setPage(1);
            setPaperType(value);
          }}
        />
        <Select
          allowClear
          placeholder="Status"
          style={{ width: 150 }}
          options={['DRAFT', 'ACTIVE', 'EXPIRED', 'TERMINATED'].map((value) => ({
            value,
            label: value,
          }))}
          value={status}
          onChange={(value) => {
            setPage(1);
            setStatus(value);
          }}
        />
        <DatePicker.RangePicker
          value={expiry}
          onChange={(value) => {
            setPage(1);
            if (value && value[0] && value[1]) {
              setExpiry([value[0], value[1]]);
              setParams({
                expiryFrom: value[0].format('YYYY-MM-DD'),
                expiryTo: value[1].format('YYYY-MM-DD'),
              });
            } else {
              setExpiry(null);
              setParams({});
            }
          }}
        />
      </Space>

      {loading ? (
        <Skeleton active />
      ) : (
        <Table
          rowKey="id"
          columns={columns}
          dataSource={rows}
          pagination={{
            current: page,
            pageSize: 20,
            total,
            showSizeChanger: false,
            onChange: setPage,
          }}
          onRow={(record) => ({
            onClick: () => navigate(`/contracts/${record.id}`),
            style: { cursor: 'pointer' },
          })}
        />
      )}

      <ContractFormModal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        onSaved={(id) => {
          setModalOpen(false);
          navigate(`/contracts/${id}`);
        }}
      />
    </div>
  );
}
