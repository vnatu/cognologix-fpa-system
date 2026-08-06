import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Card,
  Col,
  Empty,
  Row,
  Segmented,
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
import { fetchDashboard, fetchDashboardPeriods } from './api';
import type {
  DashboardResponse,
  DsoRow,
  PeriodWithData,
  RevenueDashboardGranularity,
  RevenueVsPlanRow,
} from './types';

const { Title, Text } = Typography;

/** Dashboard money fields are already Rs Lakhs (ADR-046) — do not divide by 100000. */
function formatRsL(amount: number | null | undefined): string {
  return formatCurrency(amount);
}

function quarterForMonth(month: number): number {
  if (month >= 4 && month <= 6) return 1;
  if (month >= 7 && month <= 9) return 2;
  if (month >= 10 && month <= 12) return 3;
  return 4;
}

function fiscalStartYear(month: number, year: number): number {
  return month >= 4 ? year : year - 1;
}

function fiscalYearLabel(month: number, year: number): string {
  const start = fiscalStartYear(month, year);
  return `FY${String(start % 100).padStart(2, '0')}${String((start + 1) % 100).padStart(2, '0')}`;
}

function periodKey(month: number, year: number): string {
  return `${year}-${month}`;
}

export default function RevenueDashboardPage() {
  const { token } = theme.useToken();
  const { formatDate } = useDateFormat();
  const [granularity, setGranularity] =
    useState<RevenueDashboardGranularity>('MONTHLY');
  const [periodMonth, setPeriodMonth] = useState(4);
  const [periodYear, setPeriodYear] = useState(new Date().getFullYear());
  const [selectedQuarter, setSelectedQuarter] = useState(1);
  const [availablePeriods, setAvailablePeriods] = useState<PeriodWithData[]>([]);
  const [periodsLoaded, setPeriodsLoaded] = useState(false);
  const [data, setData] = useState<DashboardResponse | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetchDashboardPeriods()
      .then((periods) => {
        setAvailablePeriods(periods);
        if (periods.length > 0) {
          // API returns newest-first (Indian FY order)
          const latest = periods[0];
          setPeriodMonth(latest.month);
          setPeriodYear(latest.year);
          setSelectedQuarter(quarterForMonth(latest.month));
        }
      })
      .catch(() => {
        notification.error({ message: 'Failed to load revenue periods' });
      })
      .finally(() => setPeriodsLoaded(true));
  }, []);

  const load = useCallback(async () => {
    if (!periodsLoaded) return;
    setLoading(true);
    try {
      setData(
        await fetchDashboard(
          periodMonth,
          periodYear,
          granularity,
          granularity === 'QUARTERLY' ? selectedQuarter : undefined,
        ),
      );
    } catch {
      notification.error({ message: 'Failed to load revenue dashboard' });
      setData(null);
    } finally {
      setLoading(false);
    }
  }, [
    periodsLoaded,
    periodMonth,
    periodYear,
    granularity,
    selectedQuarter,
  ]);

  useEffect(() => {
    load();
  }, [load]);

  const monthOptions = useMemo(
    () =>
      availablePeriods.map((p) => ({
        value: periodKey(p.month, p.year),
        label: p.label,
        month: p.month,
        year: p.year,
      })),
    [availablePeriods],
  );

  const quarterOptions = useMemo(() => {
    const fy = fiscalYearLabel(periodMonth, periodYear);
    return [1, 2, 3, 4].map((q) => ({
      value: String(q),
      label: `Q${q} ${fy}`,
      quarter: q,
    }));
  }, [periodMonth, periodYear]);

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
      render: (v: number, r) => (
        <div>
          <div>{formatRsL(v)}</div>
          {r.actualAmountUsd != null && r.key !== '__total__' && (
            <Text
              style={{
                color: token.colorTextSecondary,
                fontSize: 12,
              }}
            >
              USD: $
              {r.actualAmountUsd.toLocaleString('en-US', {
                minimumFractionDigits: 2,
                maximumFractionDigits: 2,
              })}
            </Text>
          )}
        </div>
      ),
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
      actualAmountUsd: null,
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
      render: (v: number | null) => (v == null ? '—' : v.toFixed(1)),
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

  const periodTitle = data?.periodLabel ?? '';

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
        <Space wrap>
          <Text type="secondary">Granularity</Text>
          <Segmented
            value={granularity}
            onChange={(v) => {
              const g = v as RevenueDashboardGranularity;
              setGranularity(g);
              if (g === 'QUARTERLY') {
                setSelectedQuarter(quarterForMonth(periodMonth));
              }
            }}
            options={[
              { label: 'Monthly', value: 'MONTHLY' },
              { label: 'Quarterly', value: 'QUARTERLY' },
              { label: 'Annual', value: 'ANNUAL' },
            ]}
          />
          {granularity === 'MONTHLY' && (
            <Select
              style={{ minWidth: 180 }}
              placeholder="Select month"
              value={
                monthOptions.some(
                  (o) => o.value === periodKey(periodMonth, periodYear),
                )
                  ? periodKey(periodMonth, periodYear)
                  : undefined
              }
              onChange={(val) => {
                const opt = monthOptions.find((o) => o.value === val);
                if (opt) {
                  setPeriodMonth(opt.month);
                  setPeriodYear(opt.year);
                  setSelectedQuarter(quarterForMonth(opt.month));
                }
              }}
              options={monthOptions.map((o) => ({
                label: o.label,
                value: o.value,
              }))}
              notFoundContent="No months with invoice data"
            />
          )}
          {granularity === 'QUARTERLY' && (
            <Select
              style={{ minWidth: 160 }}
              value={String(selectedQuarter)}
              onChange={(val) => {
                const q = Number(val);
                setSelectedQuarter(q);
                // Anchor path month/year to first month of quarter within current FY
                const fyStart = fiscalStartYear(periodMonth, periodYear);
                const firstMonth = q === 1 ? 4 : q === 2 ? 7 : q === 3 ? 10 : 1;
                setPeriodMonth(firstMonth);
                setPeriodYear(q === 4 ? fyStart + 1 : fyStart);
              }}
              options={quarterOptions.map((o) => ({
                label: o.label,
                value: o.value,
              }))}
            />
          )}
        </Space>
      </Space>

      {!periodsLoaded || loading ? (
        <Skeleton active paragraph={{ rows: 12 }} />
      ) : (
        <>
          {periodTitle && (
            <Text type="secondary" style={{ display: 'block', marginBottom: 12 }}>
              Period: {periodTitle}
            </Text>
          )}
          {data?.actualsCoverageNote && (
            <Alert
              type="info"
              showIcon
              style={{ marginBottom: 16 }}
              message={data.actualsCoverageNote}
            />
          )}

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
                      formatRsL(
                        typeof value === 'number' ? value : Number(value),
                      )
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
              locale={{
                emptyText: <Empty description="No outstanding invoices" />,
              }}
            />
          </Card>
        </>
      )}
    </div>
  );
}
