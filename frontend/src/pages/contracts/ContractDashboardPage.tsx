import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Alert, Card, Col, Empty, Row, Skeleton, Table, Typography, theme } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Legend,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import dayjs from 'dayjs';
import { HEADING_FONT, EXPIRY_BAND_90 } from '@/theme/antdTheme';
import { useDateFormat } from '@/context/DateFormatContext';
import { fetchContractDashboard } from './api';
import type { ContractDashboard, ContractSummary } from './types';

const { Title } = Typography;

export default function ContractDashboardPage() {
  const navigate = useNavigate();
  const { token } = theme.useToken();
  const { formatDate } = useDateFormat();
  const [data, setData] = useState<ContractDashboard | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetchContractDashboard()
      .then(setData)
      .finally(() => setLoading(false));
  }, []);

  const pieColors = [
    token.colorPrimary,
    token.colorWarning,
    token.colorSuccess,
    token.colorError,
    token.colorTextSecondary,
  ];

  const recent = useMemo(() => (data?.recentlyAdded ?? []).slice(0, 10), [data]);

  const openRange = (fromOffset: number, toOffset: number) => {
    const from = dayjs().add(fromOffset, 'day').format('YYYY-MM-DD');
    const to = dayjs().add(toOffset, 'day').format('YYYY-MM-DD');
    navigate(`/contracts/list?expiryFrom=${from}&expiryTo=${to}`);
  };

  const columns: ColumnsType<ContractSummary> = [
    { title: 'Contract #', dataIndex: 'contractNumber', width: 140 },
    { title: 'Title', dataIndex: 'title' },
    { title: 'Party', dataIndex: 'partyDisplayName' },
    { title: 'Type', dataIndex: 'contractTypeName', width: 180 },
    {
      title: 'Added',
      dataIndex: 'createdAt',
      width: 140,
      render: (value: string) => formatDate(value),
    },
  ];

  if (loading) {
    return (
      <div style={{ padding: 28 }}>
        <Skeleton active />
      </div>
    );
  }

  const bands = [
    {
      key: '30',
      message: 'Expiring in 30 days',
      count: data?.expiringIn30Days.length ?? 0,
      type: 'error' as const,
      style: undefined,
      onClick: () => openRange(0, 30),
    },
    {
      key: '60',
      message: 'Expiring in 31–60 days',
      count: data?.expiringIn31To60Days.length ?? 0,
      type: 'warning' as const,
      style: undefined,
      onClick: () => openRange(31, 60),
    },
    {
      key: '90',
      message: 'Expiring in 61–90 days',
      count: data?.expiringIn61To90Days.length ?? 0,
      type: 'warning' as const,
      style: {
        background: token.colorBgContainer,
        borderColor: EXPIRY_BAND_90,
      },
      onClick: () => openRange(61, 90),
    },
  ];

  return (
    <div style={{ padding: 28 }}>
      <Title level={3} style={{ fontFamily: HEADING_FONT, marginTop: 0 }}>
        Contracts
      </Title>
      <Row gutter={[16, 16]}>
        {bands.map((band) => (
          <Col xs={24} md={8} key={band.key}>
            <div onClick={band.onClick} style={{ cursor: 'pointer' }}>
              <Alert
                type={band.type}
                showIcon
                style={band.style}
                message={
                  <span style={{ fontFamily: HEADING_FONT }}>
                    {band.message}
                  </span>
                }
                description={`${band.count} contract${band.count === 1 ? '' : 's'}`}
              />
            </div>
          </Col>
        ))}
      </Row>

      <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
        <Col xs={24} lg={12}>
          <Card title={<span style={{ fontFamily: HEADING_FONT }}>By contract type</span>}>
            {(data?.countByType.length ?? 0) === 0 ? (
              <Empty description="No contracts yet" />
            ) : (
              <ResponsiveContainer width="100%" height={280}>
                <PieChart>
                  <Pie data={data?.countByType} dataKey="count" nameKey="label" outerRadius={90} label>
                    {data?.countByType.map((entry, index) => (
                      <Cell key={entry.label} fill={pieColors[index % pieColors.length]} />
                    ))}
                  </Pie>
                  <Tooltip />
                  <Legend />
                </PieChart>
              </ResponsiveContainer>
            )}
          </Card>
        </Col>
        <Col xs={24} lg={12}>
          <Card title={<span style={{ fontFamily: HEADING_FONT }}>By status</span>}>
            <ResponsiveContainer width="100%" height={280}>
              <BarChart data={data?.countByStatus ?? []}>
                <CartesianGrid stroke={token.colorBorderSecondary} strokeDasharray="3 3" />
                <XAxis dataKey="label" />
                <YAxis allowDecimals={false} />
                <Tooltip />
                <Bar dataKey="count" fill={token.colorPrimary} />
              </BarChart>
            </ResponsiveContainer>
          </Card>
        </Col>
      </Row>

      <Card
        style={{ marginTop: 16 }}
        title={<span style={{ fontFamily: HEADING_FONT }}>Recently added</span>}
      >
        <Table
          rowKey="id"
          size="middle"
          columns={columns}
          dataSource={recent}
          pagination={false}
          locale={{ emptyText: 'No contracts added in the last 30 days' }}
          onRow={(record) => ({
            onClick: () => navigate(`/contracts/${record.id}`),
            style: { cursor: 'pointer' },
          })}
        />
      </Card>
    </div>
  );
}
