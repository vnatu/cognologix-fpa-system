import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Card,
  Col,
  Empty,
  Row,
  Select,
  Skeleton,
  Space,
  Statistic,
  Table,
  Typography,
  theme,
  notification,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  Cell,
  Legend,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
} from 'recharts';
import { HEADING_FONT } from '@/theme/antdTheme';
import { useDateFormat } from '@/context/DateFormatContext';
import { formatCurrency } from '@/utils/formatDate';
import { fetchDashboard } from './api';
import {
  MONTH_OPTIONS,
  toLakhs,
  yearOptions,
} from './constants';
import type {
  DashboardResponse,
  DsoRow,
  RevenueVsPlanRow,
} from './types';

const { Title, Text } = Typography;

function formatRsL(amount: number | null | undefined): string {
  const lakhs = toLakhs(amount);
  return formatCurrency(lakhs);
}

export default function RevenueDashboardPage() {
  const { token } = theme.useToken();
  const { formatDate } = useDateFormat();
  const now = new Date();
  const [periodMonth, setPeriodMonth] = useState(now.getMonth() + 1);
  const [periodYear, setPeriodYear] = useState(now.getFullYear());
  const [data, setData] = useState<DashboardResponse | null>(null);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setData(await fetchDashboard(periodMonth, periodYear));
    } catch {
      notification.error({ message: 'Failed to load revenue dashboard' });
      setData(null);
    } finally {
      setLoading(false);
    }
  }, [periodMonth, periodYear]);

  useEffect(() => {
    load();
  }, [load]);

  const statusTotals = useMemo(() => {
    const buckets = data?.invoiceStatusSummary ?? [];
    const byStatus = (name: string) =>
      buckets
        .filter((b) => b.status.toLowerCase() === name.toLowerCase())
        .reduce((sum, b) => sum + (b.totalAmountInr ?? b.totalAmount), 0);
    const total = buckets.reduce(
      (sum, b) => sum + (b.totalAmountInr ?? b.totalAmount),
      0,
    );
    const paid = byStatus('Paid');
    const partiallyPaid = byStatus('Partially Paid');
    const sent = byStatus('Sent');
    const overdue = byStatus('Overdue');
    return {
      total,
      paid,
      outstanding: partiallyPaid + sent,
      overdue,
    };
  }, [data]);

  const pieData = useMemo(
    () =>
      (data?.invoiceStatusSummary ?? []).map((b) => ({
        name: b.status,
        value: b.totalAmountInr ?? b.totalAmount,
      })),
    [data],
  );

  const pieColors = [
    token.colorSuccess,
    token.colorWarning,
    token.colorPrimary,
    token.colorError,
    token.colorTextSecondary,
  ];

  const vsPlanColumns: ColumnsType<RevenueVsPlanRow & { key: string }> = [
    { title: 'Client', dataIndex: 'customerName', key: 'customerName' },
    {
      title: 'Planned Revenue (Rs L)',
      dataIndex: 'plannedRevenue',
      key: 'planned',
      align: 'right',
      render: (v: number) => formatRsL(v),
    },
    {
      title: 'Actual Invoiced (Rs L)',
      dataIndex: 'actualNetRevenueInr',
      key: 'actual',
      align: 'right',
      render: (v: number) => formatRsL(v),
    },
    {
      title: 'Variance (Rs L)',
      dataIndex: 'varianceInr',
      key: 'variance',
      align: 'right',
      render: (v: number) => (
        <Text
          style={{
            color: v >= 0 ? token.colorSuccess : token.colorError,
          }}
        >
          {formatRsL(v)}
        </Text>
      ),
    },
    {
      title: 'Variance %',
      key: 'variancePct',
      align: 'right',
      render: (_, r) => {
        const pct =
          r.plannedRevenue === 0
            ? null
            : (r.varianceInr / r.plannedRevenue) * 100;
        if (pct == null) return '—';
        return (
          <Text
            style={{
              color: pct >= 0 ? token.colorSuccess : token.colorError,
            }}
          >
            {pct.toFixed(1)}%
          </Text>
        );
      },
    },
  ];

  const vsPlanRows = useMemo(() => {
    const rows = (data?.revenueVsPlan ?? []).map((r) => ({
      ...r,
      key: r.customerId,
    }));
    if (rows.length === 0) return rows;
    const totals = rows.reduce(
      (acc, r) => ({
        plannedRevenue: acc.plannedRevenue + r.plannedRevenue,
        actualNetRevenueInr: acc.actualNetRevenueInr + r.actualNetRevenueInr,
        varianceInr: acc.varianceInr + r.varianceInr,
      }),
      { plannedRevenue: 0, actualNetRevenueInr: 0, varianceInr: 0 },
    );
    rows.push({
      key: '__total__',
      customerId: '__total__',
      customerName: 'Total',
      plannedRevenue: totals.plannedRevenue,
      actualNetRevenue: 0,
      actualNetRevenueInr: totals.actualNetRevenueInr,
      variance: 0,
      varianceInr: totals.varianceInr,
    });
    return rows;
  }, [data]);

  const dsoColumns: ColumnsType<DsoRow> = [
    { title: 'Client', dataIndex: 'customerName', key: 'customerName' },
    {
      title: 'Average Days Outstanding',
      dataIndex: 'avgDaysOutstanding',
      key: 'avg',
      align: 'right',
      render: (v: number | null) =>
        v == null ? '—' : v.toFixed(1),
    },
    {
      title: 'Oldest Outstanding Invoice Date',
      dataIndex: 'oldestOutstandingInvoiceDate',
      key: 'oldest',
      render: (d: string | null) => formatDate(d),
    },
    {
      title: 'Outstanding Amount (Rs L)',
      dataIndex: 'outstandingBalance',
      key: 'outstanding',
      align: 'right',
      render: (v: number) => formatRsL(v),
    },
  ];

  return (
    <div style={{ padding: 24 }}>
      <Space
        style={{
          width: '100%',
          justifyContent: 'space-between',
          marginBottom: 20,
        }}
        wrap
      >
        <Title level={4} style={{ fontFamily: HEADING_FONT, margin: 0 }}>
          Revenue Dashboard
        </Title>
        <Space>
          <Select
            style={{ width: 160 }}
            value={periodMonth}
            options={MONTH_OPTIONS}
            onChange={setPeriodMonth}
          />
          <Select
            style={{ width: 110 }}
            value={periodYear}
            options={yearOptions().map((y) => ({
              label: String(y),
              value: y,
            }))}
            onChange={setPeriodYear}
          />
        </Space>
      </Space>

      {loading ? (
        <Skeleton active paragraph={{ rows: 12 }} />
      ) : (
        <>
          <Card
            title={
              <span style={{ fontFamily: HEADING_FONT }}>
                Revenue vs Plan per Client
              </span>
            }
            style={{ marginBottom: 24 }}
          >
            <Table
              size="small"
              rowKey="key"
              columns={vsPlanColumns}
              dataSource={vsPlanRows}
              pagination={false}
              locale={{ emptyText: <Empty description="No revenue data" /> }}
              rowClassName={(r) =>
                r.customerId === '__total__' ? 'ant-table-row-selected' : ''
              }
            />
          </Card>

          <Card
            title={
              <span style={{ fontFamily: HEADING_FONT }}>
                Invoice Status Summary
              </span>
            }
            style={{ marginBottom: 24 }}
          >
            <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
              <Col xs={24} sm={12} md={6}>
                <Statistic
                  title="Total Invoiced (Rs L)"
                  value={formatRsL(statusTotals.total)}
                />
              </Col>
              <Col xs={24} sm={12} md={6}>
                <Statistic
                  title="Paid (Rs L)"
                  value={formatRsL(statusTotals.paid)}
                  valueStyle={{ color: token.colorSuccess }}
                />
              </Col>
              <Col xs={24} sm={12} md={6}>
                <Statistic
                  title="Outstanding (Rs L)"
                  value={formatRsL(statusTotals.outstanding)}
                  valueStyle={{ color: token.colorWarning }}
                />
              </Col>
              <Col xs={24} sm={12} md={6}>
                <Statistic
                  title="Overdue (Rs L)"
                  value={formatRsL(statusTotals.overdue)}
                  valueStyle={{ color: token.colorError }}
                />
              </Col>
            </Row>
            {pieData.length === 0 ? (
              <Empty description="No invoice status data" />
            ) : (
              <ResponsiveContainer width="100%" height={260}>
                <PieChart>
                  <Pie
                    data={pieData}
                    dataKey="value"
                    nameKey="name"
                    cx="50%"
                    cy="50%"
                    outerRadius={90}
                    label
                  >
                    {pieData.map((_, i) => (
                      <Cell
                        key={pieData[i].name}
                        fill={pieColors[i % pieColors.length]}
                      />
                    ))}
                  </Pie>
                  <Tooltip
                    formatter={(value) =>
                      formatRsL(typeof value === 'number' ? value : Number(value))
                    }
                  />
                  <Legend />
                </PieChart>
              </ResponsiveContainer>
            )}
          </Card>

          <Card
            title={
              <span style={{ fontFamily: HEADING_FONT }}>
                DSO Informational
              </span>
            }
          >
            <Alert
              type="info"
              showIcon
              style={{ marginBottom: 16 }}
              message="DSO figures are informational. Zoho Books is the system of record for collections."
            />
            <Table
              size="small"
              rowKey="customerId"
              columns={dsoColumns}
              dataSource={data?.dso ?? []}
              pagination={false}
              locale={{ emptyText: <Empty description="No outstanding invoices" /> }}
            />
          </Card>
        </>
      )}
    </div>
  );
}
