import { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Button,
  Card,
  Col,
  Empty,
  Radio,
  Row,
  Select,
  Skeleton,
  Space,
  Statistic,
  Table,
  Tabs,
  theme,
  Typography,
  InputNumber,
} from 'antd';
import {
  Line,
  LineChart,
  ResponsiveContainer,
  XAxis,
  YAxis,
  Tooltip,
  Legend,
  CartesianGrid,
} from 'recharts';
import { formatCurrency } from '@/utils/formatDate';
import { HEADING_FONT } from '@/theme/antdTheme';
import {
  buildFyMonthCols,
  billableRatio,
  currentFyMonth,
  FY_MONTH_LABELS,
  num,
  pct,
  TYPE_LABELS,
} from './utils';
import type {
  BuMetricsResult,
  CostPerEmployeeResult,
  DeltaResult,
  FyMonthCol,
  PlanDetail,
  PlanSummary,
  PlanVsActualResult,
  RollingForecastResult,
} from './types';
import {
  fetchBuMetrics,
  fetchCostPerEmployee,
  fetchDelta,
  fetchPlan,
  fetchPlans,
  fetchPlanVsActual,
  fetchRollingForecast,
} from './api';

const { Title, Text } = Typography;

export default function BudgetingDashboardPage() {
  const { token } = theme.useToken();
  const navigate = useNavigate();
  const [plans, setPlans] = useState<PlanSummary[]>([]);
  const [selectedPlanId, setSelectedPlanId] = useState<string | null>(null);
  const [plan, setPlan] = useState<PlanDetail | null>(null);
  const [selectedTypeId, setSelectedTypeId] = useState<string | null>(null);
  const [pva, setPva] = useState<PlanVsActualResult | null>(null);
  const [rf, setRf] = useState<RollingForecastResult | null>(null);
  const [delta, setDelta] = useState<DeltaResult | null>(null);
  const [loading, setLoading] = useState(false);

  const [selectedMonth, setSelectedMonth] = useState<{
    month: number;
    year: number;
  } | null>(null);
  const [buMetrics, setBuMetrics] = useState<BuMetricsResult | null>(null);
  const [costPerEmp, setCostPerEmp] = useState<CostPerEmployeeResult | null>(
    null,
  );

  const loadPlans = useCallback(async () => {
    try {
      const data = await fetchPlans();
      setPlans(data);
      if (data.length > 0) {
        setSelectedPlanId(data[0].id);
      }
    } catch (error) {
      console.error('Failed to load plans', error);
    }
  }, []);

  useEffect(() => {
    loadPlans();
  }, [loadPlans]);

  const loadPlan = useCallback(async (planId: string) => {
    setLoading(true);
    try {
      const planData = await fetchPlan(planId);
      setPlan(planData);
      const primaryType = planData.forecastTypes.find((t) => t.primary);
      setSelectedTypeId(primaryType?.id ?? null);
    } catch (error) {
      console.error('Failed to load plan', error);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (selectedPlanId) {
      loadPlan(selectedPlanId);
    }
  }, [selectedPlanId, loadPlan]);

  const loadDashboardData = useCallback(
    async (planId: string, typeId: string | null) => {
      setLoading(true);
      try {
        const [pvaData, rfData, deltaData] = await Promise.all([
          fetchPlanVsActual(planId, typeId ?? undefined),
          fetchRollingForecast(planId),
          fetchDelta(planId),
        ]);
        setPva(pvaData);
        setRf(rfData);
        setDelta(deltaData);
      } catch (error) {
        console.error('Failed to load dashboard data', error);
      } finally {
        setLoading(false);
      }
    },
    [],
  );

  useEffect(() => {
    if (selectedPlanId && plan) {
      loadDashboardData(selectedPlanId, selectedTypeId);
      const monthState = currentFyMonth(plan);
      setSelectedMonth(monthState);
    }
  }, [selectedPlanId, selectedTypeId, plan, loadDashboardData]);

  const loadMonthData = useCallback(
    async (
      planId: string,
      month: number,
      year: number,
      typeId: string | null,
    ) => {
      try {
        const [buData, costData] = await Promise.all([
          fetchBuMetrics(planId, month, year, typeId ?? undefined),
          fetchCostPerEmployee(planId, month, year, typeId ?? undefined),
        ]);
        setBuMetrics(buData);
        setCostPerEmp(costData);
      } catch (error) {
        console.error('Failed to load month data', error);
      }
    },
    [],
  );

  useEffect(() => {
    if (selectedPlanId && selectedMonth) {
      loadMonthData(
        selectedPlanId,
        selectedMonth.month,
        selectedMonth.year,
        selectedTypeId,
      );
    }
  }, [selectedPlanId, selectedMonth, selectedTypeId, loadMonthData]);

  const cols = useMemo(() => buildFyMonthCols(plan), [plan]);

  if (!plans.length) {
    return (
      <div style={{ padding: 24 }}>
        <Empty description="No financial year plans available" />
      </div>
    );
  }

  if (loading && !plan) {
    return (
      <div style={{ padding: 24 }}>
        <Skeleton active />
      </div>
    );
  }

  return (
    <div style={{ padding: 24 }}>
      <Space direction="vertical" size="large" style={{ width: '100%' }}>
        <Card>
          <Space>
            <Select
              style={{ minWidth: 200 }}
              placeholder="Select financial year"
              value={selectedPlanId}
              onChange={setSelectedPlanId}
              options={plans.map((p) => ({
                label: p.fiscalYear,
                value: p.id,
              }))}
            />
            {plan && (
              <Select
                style={{ minWidth: 200 }}
                placeholder="Select forecast type"
                value={selectedTypeId}
                onChange={setSelectedTypeId}
                options={plan.forecastTypes.map((t) => ({
                  label: TYPE_LABELS[t.typeName] ?? t.typeName,
                  value: t.id,
                }))}
              />
            )}
          </Space>
        </Card>

        {loading && <Skeleton active />}

        {!loading && pva && rf && delta && plan && selectedMonth && (
          <>
            <HeadlineKPIsPanel pva={pva} token={token} />

            <RollingForecastPanel rf={rf} delta={delta} token={token} />

            <PvaRevenuePanel
              pva={pva}
              selectedMonth={selectedMonth}
              setSelectedMonth={setSelectedMonth}
              cols={cols}
              token={token}
            />

            <PvaHcPanel
              pva={pva}
              selectedMonth={selectedMonth}
              setSelectedMonth={setSelectedMonth}
              cols={cols}
              token={token}
            />

            <PvaCostsPanel
              pva={pva}
              selectedMonth={selectedMonth}
              setSelectedMonth={setSelectedMonth}
              cols={cols}
              token={token}
            />

            {buMetrics && (
              <BuMetricsPanel
                buMetrics={buMetrics}
                token={token}
                navigate={navigate}
              />
            )}

            <PlSummaryPanel pva={pva} cols={cols} token={token} />

            {costPerEmp && (
              <CostPerEmployeePanel costPerEmp={costPerEmp} token={token} />
            )}

            <DeltaViewPanel delta={delta} cols={cols} token={token} />
          </>
        )}
      </Space>
    </div>
  );
}

interface HeadlineKPIsPanelProps {
  pva: PlanVsActualResult;
  token: ReturnType<typeof theme.useToken>['token'];
}

function HeadlineKPIsPanel({ pva, token }: HeadlineKPIsPanelProps) {
  const fyTotals = pva.fy;

  const firstMonthWithActuals = pva.months.find((m) => m.hasActuals);
  const billableRatioMonth = firstMonthWithActuals ?? pva.months[0];
  const billableHcPlan = billableRatioMonth?.hc.plan.billableHc ?? 0;
  const billableHcActual = billableRatioMonth?.hc.actual.billableHc ?? 0;
  const totalHcPlan = billableRatioMonth?.hc.plan.totalHc ?? 0;
  const totalHcActual = billableRatioMonth?.hc.actual.totalHc ?? 0;
  const ratioVariance =
    totalHcActual > 0
      ? billableRatio(billableHcActual, totalHcActual) -
        billableRatio(billableHcPlan, totalHcPlan)
      : null;

  return (
    <Card>
      <Title level={4} style={{ fontFamily: HEADING_FONT }}>
        Headline KPIs
      </Title>
      <Row gutter={16}>
        <Col span={8}>
          <Statistic
            title="Total Revenue (Rs L)"
            value={formatCurrency(fyTotals.totalRevenue.actual ?? fyTotals.totalRevenue.plan)}
            valueStyle={{
              color:
                fyTotals.totalRevenue.variance != null &&
                fyTotals.totalRevenue.variance > 0
                  ? token.colorSuccess
                  : undefined,
            }}
          />
          {fyTotals.totalRevenue.variance != null && (
            <Text
              style={{
                color:
                  fyTotals.totalRevenue.variance > 0
                    ? token.colorSuccess
                    : token.colorError,
              }}
            >
              Variance: {formatCurrency(fyTotals.totalRevenue.variance)} (
              {pct(
                fyTotals.totalRevenue.variance,
                fyTotals.totalRevenue.plan,
              )?.toFixed(1)}
              %)
            </Text>
          )}
        </Col>
        <Col span={8}>
          <Statistic
            title="EBITDA (Rs L)"
            value={formatCurrency(fyTotals.ebitda.actual ?? fyTotals.ebitda.plan)}
            valueStyle={{
              color:
                fyTotals.ebitda.variance != null && fyTotals.ebitda.variance > 0
                  ? token.colorSuccess
                  : undefined,
            }}
          />
          {fyTotals.ebitda.variance != null && (
            <Text
              style={{
                color:
                  fyTotals.ebitda.variance > 0
                    ? token.colorSuccess
                    : token.colorError,
              }}
            >
              Variance: {formatCurrency(fyTotals.ebitda.variance)} (
              {pct(fyTotals.ebitda.variance, fyTotals.ebitda.plan)?.toFixed(1)}
              %)
            </Text>
          )}
        </Col>
        <Col span={8}>
          <Statistic
            title="Billable Ratio %"
            value={billableRatio(
              totalHcActual > 0 ? billableHcActual : billableHcPlan,
              totalHcActual > 0 ? totalHcActual : totalHcPlan,
            ).toFixed(1)}
            valueStyle={{
              color:
                ratioVariance != null && ratioVariance > 0
                  ? token.colorSuccess
                  : undefined,
            }}
          />
          {ratioVariance != null && (
            <Text
              style={{
                color:
                  ratioVariance > 0 ? token.colorSuccess : token.colorError,
              }}
            >
              Variance: {ratioVariance.toFixed(1)}%
            </Text>
          )}
        </Col>
      </Row>
    </Card>
  );
}

interface RollingForecastPanelProps {
  rf: RollingForecastResult;
  delta: DeltaResult;
  token: ReturnType<typeof theme.useToken>['token'];
}

function RollingForecastPanel({ rf, delta, token }: RollingForecastPanelProps) {
  const [metric, setMetric] = useState<
    'totalRevenue' | 'ebitda' | 'billableHc'
  >('totalRevenue');

  const chartData = useMemo(() => {
    return rf.months.map((m, i) => {
      const deltaMonth = delta.months[i];
      let rfValue = 0;
      let deltaValue = 0;

      if (metric === 'billableHc') {
        rfValue = num(m.hc.billableHc);
        deltaValue = deltaMonth ? num(deltaMonth.hc.billableHc) : 0;
      } else {
        rfValue = num(m[metric]);
        deltaValue = deltaMonth ? num(deltaMonth[metric]) : 0;
      }

      const baselineValue = rfValue - deltaValue;

      return {
        month: FY_MONTH_LABELS[i],
        baseline: baselineValue,
        forecast: rfValue,
        actual: m.fromActuals ? rfValue : null,
      };
    });
  }, [rf.months, delta.months, metric]);

  return (
    <Card>
      <Space
        direction="vertical"
        size="middle"
        style={{ width: '100%' }}
      >
        <div
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
          }}
        >
          <Title level={4} style={{ fontFamily: HEADING_FONT, margin: 0 }}>
            Rolling Forecast vs Baseline
          </Title>
          <Select
            style={{ minWidth: 200 }}
            value={metric}
            onChange={setMetric}
            options={[
              { label: 'Total Revenue', value: 'totalRevenue' },
              { label: 'EBITDA', value: 'ebitda' },
              { label: 'Billable HC', value: 'billableHc' },
            ]}
          />
        </div>
        <ResponsiveContainer width="100%" height={320}>
          <LineChart data={chartData}>
            <CartesianGrid strokeDasharray="3 3" />
            <XAxis dataKey="month" />
            <YAxis />
            <Tooltip
              formatter={(value: any) => {
                const numValue = typeof value === 'number' ? value : Number(value);
                return metric === 'billableHc'
                  ? numValue.toFixed(0)
                  : formatCurrency(numValue);
              }}
            />
            <Legend />
            <Line
              type="monotone"
              dataKey="baseline"
              stroke={token.colorTextDescription}
              strokeDasharray="5 5"
              name="Baseline"
              dot={false}
            />
            <Line
              type="monotone"
              dataKey="forecast"
              stroke={token.colorPrimary}
              name="Rolling Forecast"
              strokeWidth={2}
            />
            <Line
              type="monotone"
              dataKey="actual"
              stroke={token.colorSuccess}
              name="Actuals"
              strokeWidth={2}
              connectNulls={false}
            />
          </LineChart>
        </ResponsiveContainer>
      </Space>
    </Card>
  );
}

interface PvaRevenuePanelProps {
  pva: PlanVsActualResult;
  selectedMonth: { month: number; year: number };
  setSelectedMonth: (month: { month: number; year: number }) => void;
  cols: FyMonthCol[];
  token: ReturnType<typeof theme.useToken>['token'];
}

function PvaRevenuePanel({
  pva,
  selectedMonth,
  setSelectedMonth,
  cols,
  token,
}: PvaRevenuePanelProps) {
  const monthData = useMemo(() => {
    return pva.months.find(
      (m) => m.month === selectedMonth.month && m.year === selectedMonth.year,
    );
  }, [pva.months, selectedMonth]);

  const dataSource = useMemo(() => {
    if (!monthData) return [];
    const rows = monthData.revenueByClient.map((c) => ({
      key: c.customerId,
      client: c.customerCode,
      plan: formatCurrency(c.totalRevenue.plan),
      actual: c.totalRevenue.actual != null ? formatCurrency(c.totalRevenue.actual) : '—',
      variance:
        c.totalRevenue.variance != null
          ? formatCurrency(c.totalRevenue.variance)
          : '—',
      variancePct:
        c.totalRevenue.variance != null
          ? `${pct(c.totalRevenue.variance, c.totalRevenue.plan)?.toFixed(1) ?? '—'}%`
          : '—',
      varianceColor:
        c.totalRevenue.variance != null && c.totalRevenue.variance > 0
          ? token.colorSuccess
          : token.colorError,
    }));

    // Total row
    rows.push({
      key: 'total',
      client: 'Total',
      plan: formatCurrency(monthData.totalRevenue.plan),
      actual:
        monthData.totalRevenue.actual != null
          ? formatCurrency(monthData.totalRevenue.actual)
          : '—',
      variance:
        monthData.totalRevenue.variance != null
          ? formatCurrency(monthData.totalRevenue.variance)
          : '—',
      variancePct:
        monthData.totalRevenue.variance != null
          ? `${pct(monthData.totalRevenue.variance, monthData.totalRevenue.plan)?.toFixed(1) ?? '—'}%`
          : '—',
      varianceColor:
        monthData.totalRevenue.variance != null &&
        monthData.totalRevenue.variance > 0
          ? token.colorSuccess
          : token.colorError,
    });

    return rows;
  }, [monthData, token]);

  const columns = [
    { title: 'Client', dataIndex: 'client', key: 'client' },
    {
      title: 'Plan (Rs L)',
      dataIndex: 'plan',
      key: 'plan',
      align: 'right' as const,
    },
    {
      title: 'Actual (Rs L)',
      dataIndex: 'actual',
      key: 'actual',
      align: 'right' as const,
    },
    {
      title: 'Variance (Rs L)',
      dataIndex: 'variance',
      key: 'variance',
      align: 'right' as const,
      render: (text: string, record: any) => (
        <span style={{ color: record.varianceColor }}>{text}</span>
      ),
    },
    {
      title: 'Variance %',
      dataIndex: 'variancePct',
      key: 'variancePct',
      align: 'right' as const,
      render: (text: string, record: any) => (
        <span style={{ color: record.varianceColor }}>{text}</span>
      ),
    },
  ];

  return (
    <Card>
      <Space
        direction="vertical"
        size="middle"
        style={{ width: '100%' }}
      >
        <div
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
          }}
        >
          <Title level={4} style={{ fontFamily: HEADING_FONT, margin: 0 }}>
            PvA Revenue
          </Title>
          <Select
            style={{ minWidth: 150 }}
            value={`${selectedMonth.year}-${selectedMonth.month}`}
            onChange={(val) => {
              const col = cols.find((c) => c.key === val);
              if (col) {
                setSelectedMonth({ month: col.planMonth, year: col.planYear });
              }
            }}
            options={cols.map((c) => ({
              label: c.label,
              value: c.key,
            }))}
          />
        </div>
        <Table
          dataSource={dataSource}
          columns={columns}
          pagination={false}
          scroll={{ x: true }}
          size="small"
        />
      </Space>
    </Card>
  );
}

interface PvaHcPanelProps {
  pva: PlanVsActualResult;
  selectedMonth: { month: number; year: number };
  setSelectedMonth: (month: { month: number; year: number }) => void;
  cols: FyMonthCol[];
  token: ReturnType<typeof theme.useToken>['token'];
}

function PvaHcPanel({
  pva,
  selectedMonth,
  setSelectedMonth,
  cols,
  token,
}: PvaHcPanelProps) {
  const monthData = useMemo(() => {
    return pva.months.find(
      (m) => m.month === selectedMonth.month && m.year === selectedMonth.year,
    );
  }, [pva.months, selectedMonth]);

  const dataSource = useMemo(() => {
    if (!monthData) return [];
    const hc = monthData.hc;

    const rows = [
      {
        key: 'billable',
        category: 'Billable HC',
        plan: hc.plan.billableHc,
        actual: hc.actual.billableHc,
        variance: hc.variance.billableHc,
      },
      {
        key: 'bench',
        category: 'Bench',
        plan: hc.plan.benchHc,
        actual: hc.actual.benchHc,
        variance: hc.variance.benchHc,
      },
      {
        key: 'support',
        category: 'Support',
        plan: hc.plan.supportHc,
        actual: hc.actual.supportHc,
        variance: hc.variance.supportHc,
      },
      {
        key: 'leadership',
        category: 'Leadership',
        plan: hc.plan.leadershipHc,
        actual: hc.actual.leadershipHc,
        variance: hc.variance.leadershipHc,
      },
      {
        key: 'management',
        category: 'Management',
        plan: hc.plan.managementHc,
        actual: hc.actual.managementHc,
        variance: hc.variance.managementHc,
      },
      {
        key: 'total',
        category: 'Total',
        plan: hc.plan.totalHc,
        actual: hc.actual.totalHc,
        variance: hc.variance.totalHc,
      },
    ];

    // Add billable ratio row
            const ratioPlan = billableRatio(hc.plan.billableHc, hc.plan.totalHc);
            const ratioActual = billableRatio(hc.actual.billableHc, hc.actual.totalHc);
            const ratioVariance = ratioActual - ratioPlan;

            rows.push({
              key: 'ratio',
              category: 'Billable Ratio %',
              plan: ratioPlan.toFixed(1) as any,
              actual: ratioActual.toFixed(1) as any,
              variance: ratioVariance.toFixed(1) as any,
            });

    return rows.map((r) => ({
      ...r,
      varianceColor:
        typeof r.variance === 'number' && r.variance > 0
          ? token.colorSuccess
          : token.colorError,
    }));
  }, [monthData, token]);

  const columns = [
    { title: 'Category', dataIndex: 'category', key: 'category' },
    { title: 'Plan', dataIndex: 'plan', key: 'plan', align: 'right' as const },
    {
      title: 'Actual',
      dataIndex: 'actual',
      key: 'actual',
      align: 'right' as const,
    },
    {
      title: 'Variance',
      dataIndex: 'variance',
      key: 'variance',
      align: 'right' as const,
      render: (text: number | string, record: any) => (
        <span style={{ color: record.varianceColor }}>{text}</span>
      ),
    },
  ];

  return (
    <Card>
      <Space
        direction="vertical"
        size="middle"
        style={{ width: '100%' }}
      >
        <div
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
          }}
        >
          <Title level={4} style={{ fontFamily: HEADING_FONT, margin: 0 }}>
            PvA HC
          </Title>
          <Select
            style={{ minWidth: 150 }}
            value={`${selectedMonth.year}-${selectedMonth.month}`}
            onChange={(val) => {
              const col = cols.find((c) => c.key === val);
              if (col) {
                setSelectedMonth({ month: col.planMonth, year: col.planYear });
              }
            }}
            options={cols.map((c) => ({
              label: c.label,
              value: c.key,
            }))}
          />
        </div>
        <Table
          dataSource={dataSource}
          columns={columns}
          pagination={false}
          scroll={{ x: true }}
          size="small"
        />
      </Space>
    </Card>
  );
}

interface PvaCostsPanelProps {
  pva: PlanVsActualResult;
  selectedMonth: { month: number; year: number };
  setSelectedMonth: (month: { month: number; year: number }) => void;
  cols: FyMonthCol[];
  token: ReturnType<typeof theme.useToken>['token'];
}

function PvaCostsPanel({
  pva,
  selectedMonth,
  setSelectedMonth,
  cols,
  token,
}: PvaCostsPanelProps) {
  const monthData = useMemo(() => {
    return pva.months.find(
      (m) => m.month === selectedMonth.month && m.year === selectedMonth.year,
    );
  }, [pva.months, selectedMonth]);

  const salaryDataSource = useMemo(() => {
    if (!monthData) return [];
    const sal = monthData.salary;

    return [
      {
        key: 'billable',
        category: 'Billable',
        plan: formatCurrency(sal.plan.billable),
        actual: formatCurrency(sal.actual.billable),
        variance: formatCurrency(sal.variance.billable),
        varianceColor:
          sal.variance.billable < 0 ? token.colorSuccess : token.colorError,
      },
      {
        key: 'bench',
        category: 'Bench',
        plan: formatCurrency(sal.plan.bench),
        actual: formatCurrency(sal.actual.bench),
        variance: formatCurrency(sal.variance.bench),
        varianceColor:
          sal.variance.bench < 0 ? token.colorSuccess : token.colorError,
      },
      {
        key: 'support',
        category: 'Support',
        plan: formatCurrency(sal.plan.support),
        actual: formatCurrency(sal.actual.support),
        variance: formatCurrency(sal.variance.support),
        varianceColor:
          sal.variance.support < 0 ? token.colorSuccess : token.colorError,
      },
      {
        key: 'cofounders',
        category: 'Co-Founders',
        plan: formatCurrency(sal.plan.cofounders),
        actual: formatCurrency(sal.actual.cofounders),
        variance: formatCurrency(sal.variance.cofounders),
        varianceColor:
          sal.variance.cofounders < 0 ? token.colorSuccess : token.colorError,
      },
      {
        key: 'senior',
        category: 'Senior Mgmt',
        plan: formatCurrency(sal.plan.seniorMgmt),
        actual: formatCurrency(sal.actual.seniorMgmt),
        variance: formatCurrency(sal.variance.seniorMgmt),
        varianceColor:
          sal.variance.seniorMgmt < 0 ? token.colorSuccess : token.colorError,
      },
      {
        key: 'total',
        category: 'Total',
        plan: formatCurrency(sal.plan.total),
        actual: formatCurrency(sal.actual.total),
        variance: formatCurrency(sal.variance.total),
        varianceColor:
          sal.variance.total < 0 ? token.colorSuccess : token.colorError,
      },
    ];
  }, [monthData, token]);

  const overheadDataSource = useMemo(() => {
    if (!monthData) return [];

    const rows = monthData.overhead.map((oh) => ({
      key: oh.lineCode,
      lineCode: oh.lineCode,
      plan: formatCurrency(oh.amount.plan),
      actual: oh.amount.actual != null ? formatCurrency(oh.amount.actual) : '—',
      variance:
        oh.amount.variance != null ? formatCurrency(oh.amount.variance) : '—',
      varianceColor:
        oh.amount.variance != null && oh.amount.variance < 0
          ? token.colorSuccess
          : token.colorError,
    }));

    // Total row
    rows.push({
      key: 'total',
      lineCode: 'Total',
      plan: formatCurrency(monthData.totalOverhead.plan),
      actual:
        monthData.totalOverhead.actual != null
          ? formatCurrency(monthData.totalOverhead.actual)
          : '—',
      variance:
        monthData.totalOverhead.variance != null
          ? formatCurrency(monthData.totalOverhead.variance)
          : '—',
      varianceColor:
        monthData.totalOverhead.variance != null &&
        monthData.totalOverhead.variance < 0
          ? token.colorSuccess
          : token.colorError,
    });

    return rows;
  }, [monthData, token]);

  const columns = [
    { title: 'Category', dataIndex: 'category', key: 'category', width: 120 },
    {
      title: 'Plan (Rs L)',
      dataIndex: 'plan',
      key: 'plan',
      align: 'right' as const,
      width: 120,
    },
    {
      title: 'Actual (Rs L)',
      dataIndex: 'actual',
      key: 'actual',
      align: 'right' as const,
      width: 120,
    },
    {
      title: 'Variance (Rs L)',
      dataIndex: 'variance',
      key: 'variance',
      align: 'right' as const,
      width: 120,
      render: (text: string, record: any) => (
        <span style={{ color: record.varianceColor }}>{text}</span>
      ),
    },
  ];

  const overheadColumns = [
    { title: 'Line Code', dataIndex: 'lineCode', key: 'lineCode', width: 150 },
    {
      title: 'Plan (Rs L)',
      dataIndex: 'plan',
      key: 'plan',
      align: 'right' as const,
      width: 120,
    },
    {
      title: 'Actual (Rs L)',
      dataIndex: 'actual',
      key: 'actual',
      align: 'right' as const,
      width: 120,
    },
    {
      title: 'Variance (Rs L)',
      dataIndex: 'variance',
      key: 'variance',
      align: 'right' as const,
      width: 120,
      render: (text: string, record: any) => (
        <span style={{ color: record.varianceColor }}>{text}</span>
      ),
    },
  ];

  return (
    <Card>
      <Space
        direction="vertical"
        size="middle"
        style={{ width: '100%' }}
      >
        <div
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
          }}
        >
          <Title level={4} style={{ fontFamily: HEADING_FONT, margin: 0 }}>
            PvA Costs
          </Title>
          <Select
            style={{ minWidth: 150 }}
            value={`${selectedMonth.year}-${selectedMonth.month}`}
            onChange={(val) => {
              const col = cols.find((c) => c.key === val);
              if (col) {
                setSelectedMonth({ month: col.planMonth, year: col.planYear });
              }
            }}
            options={cols.map((c) => ({
              label: c.label,
              value: c.key,
            }))}
          />
        </div>
        <Row gutter={16}>
          <Col span={12}>
            <Card title="Salary" size="small">
              <Table
                dataSource={salaryDataSource}
                columns={columns}
                pagination={false}
                scroll={{ x: true }}
                size="small"
              />
            </Card>
          </Col>
          <Col span={12}>
            <Card title="Overhead" size="small">
              <Table
                dataSource={overheadDataSource}
                columns={overheadColumns}
                pagination={false}
                scroll={{ x: true }}
                size="small"
              />
            </Card>
          </Col>
        </Row>
      </Space>
    </Card>
  );
}

interface BuMetricsPanelProps {
  buMetrics: BuMetricsResult;
  token: ReturnType<typeof theme.useToken>['token'];
  navigate: (path: string) => void;
}

function BuMetricsPanel({ buMetrics, navigate }: BuMetricsPanelProps) {
  const dataSource = useMemo(() => {
    return buMetrics.rows
      .filter((r) => !r.internal)
      .map((r) => ({
        key: r.customerId,
        customer: r.customerName,
        plannedRevenue: formatCurrency(r.plannedRevenue),
        actualRevenue: r.actualRevenue != null ? formatCurrency(r.actualRevenue) : '—',
        plannedSalaryCost: formatCurrency(r.plannedSalaryCost),
        actualSalaryCost:
          r.actualSalaryCost != null ? formatCurrency(r.actualSalaryCost) : '—',
        plannedBillableHc: r.plannedBillableHc ?? '—',
        actualBillableHc: r.actualBillableHc ?? '—',
        plannedGrossMargin: formatCurrency(r.plannedGrossMargin),
        actualGrossMargin:
          r.actualGrossMargin != null ? formatCurrency(r.actualGrossMargin) : '—',
        plannedGrossMarginPct: `${r.plannedGrossMarginPct.toFixed(1)}%`,
        actualGrossMarginPct:
          r.actualGrossMarginPct != null
            ? `${r.actualGrossMarginPct.toFixed(1)}%`
            : '—',
        avgSalaryPerHead:
          r.avgSalaryPerHead != null ? formatCurrency(r.avgSalaryPerHead) : '—',
        customerName: r.customerName,
      }));
  }, [buMetrics.rows]);

  const columns = [
    { title: 'Customer', dataIndex: 'customer', key: 'customer', fixed: 'left' as const, width: 150 },
    {
      title: 'Planned Revenue (Rs L)',
      dataIndex: 'plannedRevenue',
      key: 'plannedRevenue',
      align: 'right' as const,
      width: 140,
    },
    {
      title: 'Actual Revenue (Rs L)',
      dataIndex: 'actualRevenue',
      key: 'actualRevenue',
      align: 'right' as const,
      width: 140,
    },
    {
      title: 'Planned Salary Cost (Rs L)',
      dataIndex: 'plannedSalaryCost',
      key: 'plannedSalaryCost',
      align: 'right' as const,
      width: 160,
    },
    {
      title: 'Actual Salary Cost (Rs L)',
      dataIndex: 'actualSalaryCost',
      key: 'actualSalaryCost',
      align: 'right' as const,
      width: 160,
    },
    {
      title: 'Planned Billable HC',
      dataIndex: 'plannedBillableHc',
      key: 'plannedBillableHc',
      align: 'right' as const,
      width: 140,
    },
    {
      title: 'Actual Billable HC',
      dataIndex: 'actualBillableHc',
      key: 'actualBillableHc',
      align: 'right' as const,
      width: 140,
    },
    {
      title: 'Planned Gross Margin (Rs L)',
      dataIndex: 'plannedGrossMargin',
      key: 'plannedGrossMargin',
      align: 'right' as const,
      width: 180,
    },
    {
      title: 'Actual Gross Margin (Rs L)',
      dataIndex: 'actualGrossMargin',
      key: 'actualGrossMargin',
      align: 'right' as const,
      width: 180,
    },
    {
      title: 'Planned GM %',
      dataIndex: 'plannedGrossMarginPct',
      key: 'plannedGrossMarginPct',
      align: 'right' as const,
      width: 120,
    },
    {
      title: 'Actual GM %',
      dataIndex: 'actualGrossMarginPct',
      key: 'actualGrossMarginPct',
      align: 'right' as const,
      width: 120,
    },
    {
      title: 'Avg Salary/Head (Rs L)',
      dataIndex: 'avgSalaryPerHead',
      key: 'avgSalaryPerHead',
      align: 'right' as const,
      width: 160,
    },
    {
      title: 'Action',
      key: 'action',
      width: 140,
      render: (_: any, record: any) => (
        <Button
          size="small"
          onClick={() =>
            navigate(
              `/people-payroll/master?bu=${encodeURIComponent(record.customerName)}`,
            )
          }
        >
          View Employees
        </Button>
      ),
    },
  ];

  return (
    <Card>
      <Title level={4} style={{ fontFamily: HEADING_FONT }}>
        BU Metrics
      </Title>
      <Table
        dataSource={dataSource}
        columns={columns}
        pagination={false}
        scroll={{ x: true }}
        size="small"
      />
    </Card>
  );
}

interface PlSummaryPanelProps {
  pva: PlanVsActualResult;
  cols: FyMonthCol[];
  token: ReturnType<typeof theme.useToken>['token'];
}

function PlSummaryPanel({ pva, cols }: PlSummaryPanelProps) {
  const [mode, setMode] = useState<'plan' | 'actual'>('plan');

  const dataSource = useMemo(() => {
    const rows = [
      { key: 'revenue', label: 'Total Revenue', field: 'totalRevenue' as const },
      { key: 'cogs', label: 'Total COGS', field: 'totalCogs' as const },
      { key: 'grossProfit', label: 'Gross Profit', field: 'grossProfit' as const },
      { key: 'grossMargin', label: 'Gross Margin %', field: null },
      { key: 'opex', label: 'Total OpEx', field: null },
      { key: 'ebitda', label: 'EBITDA', field: 'ebitda' as const },
    ];

    return rows.map((row) => {
      const record: Record<string, any> = {
        key: row.key,
        label: row.label,
      };

      cols.forEach((col) => {
        const monthData = pva.months.find(
          (m) => m.month === col.planMonth && m.year === col.planYear,
        );
        if (!monthData) {
          record[col.key] = '—';
          return;
        }

        if (row.field === null) {
          if (row.key === 'grossMargin') {
            const revenue = mode === 'plan' ? monthData.totalRevenue.plan : (monthData.totalRevenue.actual ?? monthData.totalRevenue.plan);
            const gp = mode === 'plan' ? monthData.grossProfit.plan : (monthData.grossProfit.actual ?? monthData.grossProfit.plan);
            record[col.key] = revenue > 0 ? `${((gp / revenue) * 100).toFixed(1)}%` : '—';
          } else if (row.key === 'opex') {
            const gp = mode === 'plan' ? monthData.grossProfit.plan : (monthData.grossProfit.actual ?? monthData.grossProfit.plan);
            const ebitda = mode === 'plan' ? monthData.ebitda.plan : (monthData.ebitda.actual ?? monthData.ebitda.plan);
            record[col.key] = formatCurrency(gp - ebitda);
          }
        } else {
          const value = mode === 'plan' ? monthData[row.field].plan : (monthData[row.field].actual ?? monthData[row.field].plan);
          record[col.key] = formatCurrency(value);
        }
      });

      // Q1-Q4
      ['q1', 'q2', 'q3', 'q4'].forEach((q) => {
        const qData = pva[q as 'q1' | 'q2' | 'q3' | 'q4'];
        if (row.field === null) {
          if (row.key === 'grossMargin') {
            const revenue = mode === 'plan' ? qData.totalRevenue.plan : (qData.totalRevenue.actual ?? qData.totalRevenue.plan);
            const gp = mode === 'plan' ? qData.grossProfit.plan : (qData.grossProfit.actual ?? qData.grossProfit.plan);
            record[q] = revenue > 0 ? `${((gp / revenue) * 100).toFixed(1)}%` : '—';
          } else if (row.key === 'opex') {
            const gp = mode === 'plan' ? qData.grossProfit.plan : (qData.grossProfit.actual ?? qData.grossProfit.plan);
            const ebitda = mode === 'plan' ? qData.ebitda.plan : (qData.ebitda.actual ?? qData.ebitda.plan);
            record[q] = formatCurrency(gp - ebitda);
          }
        } else {
          const value = mode === 'plan' ? qData[row.field].plan : (qData[row.field].actual ?? qData[row.field].plan);
          record[q] = formatCurrency(value);
        }
      });

      // FY
      const fyData = pva.fy;
      if (row.field === null) {
        if (row.key === 'grossMargin') {
          const revenue = mode === 'plan' ? fyData.totalRevenue.plan : (fyData.totalRevenue.actual ?? fyData.totalRevenue.plan);
          const gp = mode === 'plan' ? fyData.grossProfit.plan : (fyData.grossProfit.actual ?? fyData.grossProfit.plan);
          record.fy = revenue > 0 ? `${((gp / revenue) * 100).toFixed(1)}%` : '—';
        } else if (row.key === 'opex') {
          const gp = mode === 'plan' ? fyData.grossProfit.plan : (fyData.grossProfit.actual ?? fyData.grossProfit.plan);
          const ebitda = mode === 'plan' ? fyData.ebitda.plan : (fyData.ebitda.actual ?? fyData.ebitda.plan);
          record.fy = formatCurrency(gp - ebitda);
        }
      } else {
        const value = mode === 'plan' ? fyData[row.field].plan : (fyData[row.field].actual ?? fyData[row.field].plan);
        record.fy = formatCurrency(value);
      }

      return record;
    });
  }, [pva, cols, mode]);

  const columns = [
    {
      title: 'Metric',
      dataIndex: 'label',
      key: 'label',
      fixed: 'left' as const,
      width: 150,
    },
    ...cols.map((col) => ({
      title: `${col.label} (Rs L)`,
      key: col.key,
      dataIndex: col.key,
      width: 110,
      align: 'right' as const,
    })),
    { title: 'Q1 (Rs L)', key: 'q1', dataIndex: 'q1', width: 110, align: 'right' as const },
    { title: 'Q2 (Rs L)', key: 'q2', dataIndex: 'q2', width: 110, align: 'right' as const },
    { title: 'Q3 (Rs L)', key: 'q3', dataIndex: 'q3', width: 110, align: 'right' as const },
    { title: 'Q4 (Rs L)', key: 'q4', dataIndex: 'q4', width: 110, align: 'right' as const },
    { title: 'FY (Rs L)', key: 'fy', dataIndex: 'fy', width: 110, align: 'right' as const },
  ];

  return (
    <Card>
      <Space
        direction="vertical"
        size="middle"
        style={{ width: '100%' }}
      >
        <div
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
          }}
        >
          <Title level={4} style={{ fontFamily: HEADING_FONT, margin: 0 }}>
            P&L Summary
          </Title>
          <Radio.Group value={mode} onChange={(e) => setMode(e.target.value)}>
            <Radio.Button value="plan">Plan</Radio.Button>
            <Radio.Button value="actual">Actual</Radio.Button>
          </Radio.Group>
        </div>
        <Table
          dataSource={dataSource}
          columns={columns}
          pagination={false}
          scroll={{ x: true }}
          size="small"
        />
      </Space>
    </Card>
  );
}

interface CostPerEmployeePanelProps {
  costPerEmp: CostPerEmployeeResult;
  token: ReturnType<typeof theme.useToken>['token'];
}

function CostPerEmployeePanel({ costPerEmp }: CostPerEmployeePanelProps) {
  const [targetMargin, setTargetMargin] = useState<number>(20);

  const categories = [
    { key: 'billable', label: 'Billable', data: costPerEmp.billable },
    { key: 'bench', label: 'Bench', data: costPerEmp.bench },
    { key: 'support', label: 'Support', data: costPerEmp.support },
    { key: 'leadership', label: 'Leadership', data: costPerEmp.leadership },
  ];

  const minBillingRate = costPerEmp.totalCostPerBillableHead;
  const targetRate = minBillingRate * (1 + targetMargin / 100);

  return (
    <Card>
      <Title level={4} style={{ fontFamily: HEADING_FONT }}>
        Cost per Employee
      </Title>
      <Tabs
        items={categories.map((cat) => ({
          key: cat.key,
          label: cat.label,
          children: (
            <Space direction="vertical" size="middle" style={{ width: '100%' }}>
              <Table
                dataSource={[
                  {
                    key: 'layer1',
                    layer: 'Layer 1',
                    amount: formatCurrency(cat.data.layer1),
                  },
                  {
                    key: 'layer2',
                    layer: 'Layer 2',
                    amount: formatCurrency(cat.data.layer2),
                  },
                  {
                    key: 'layer3',
                    layer: 'Layer 3',
                    amount: formatCurrency(cat.data.layer3),
                  },
                  {
                    key: 'total',
                    layer: 'Total',
                    amount: formatCurrency(cat.data.total),
                  },
                ]}
                columns={[
                  { title: 'Layer', dataIndex: 'layer', key: 'layer' },
                  {
                    title: 'Amount (Rs L/head)',
                    dataIndex: 'amount',
                    key: 'amount',
                    align: 'right' as const,
                  },
                ]}
                pagination={false}
                size="small"
              />
              {cat.key === 'billable' && (
                <Space direction="vertical">
                  <Text>
                    <strong>Minimum Billing Rate:</strong> {formatCurrency(minBillingRate)} Rs L/head
                  </Text>
                  <Space>
                    <Text>Target Margin %:</Text>
                    <InputNumber
                      min={0}
                      max={100}
                      value={targetMargin}
                      onChange={(v) => setTargetMargin(v ?? 20)}
                      style={{ width: 100 }}
                    />
                  </Space>
                  <Text>
                    <strong>Target Rate:</strong> {formatCurrency(targetRate)} Rs L/head
                  </Text>
                </Space>
              )}
            </Space>
          ),
        }))}
      />
    </Card>
  );
}

interface DeltaViewPanelProps {
  delta: DeltaResult;
  cols: FyMonthCol[];
  token: ReturnType<typeof theme.useToken>['token'];
}

function DeltaViewPanel({ delta, cols, token }: DeltaViewPanelProps) {
  const dataSource = useMemo(() => {
    const rows = [
      { key: 'revenue', label: 'Revenue', field: 'totalRevenue' as const, favorPositive: true },
      { key: 'billableHc', label: 'Billable HC', field: null, favorPositive: true },
      { key: 'billableRatio', label: 'Billable Ratio %', field: null, favorPositive: true },
      { key: 'salary', label: 'Salary', field: 'totalSalaryCost' as const, favorPositive: false },
      { key: 'overhead', label: 'Overhead', field: 'totalOverhead' as const, favorPositive: false },
      { key: 'grossProfit', label: 'Gross Profit', field: 'grossProfit' as const, favorPositive: true },
      { key: 'ebitda', label: 'EBITDA', field: 'ebitda' as const, favorPositive: true },
    ];

    return rows.map((row) => {
      const record: Record<string, any> = {
        key: row.key,
        label: row.label,
      };

      let fyTotal = 0;

      cols.forEach((col) => {
        const monthData = delta.months.find(
          (m) => m.month === col.planMonth && m.year === col.planYear,
        );
        if (!monthData) {
          record[col.key] = { value: '—', color: undefined };
          return;
        }

        let value = 0;
        if (row.field === null) {
          if (row.key === 'billableHc') {
            value = monthData.hc.billableHc;
          } else if (row.key === 'billableRatio') {
            value = billableRatio(monthData.hc.billableHc, monthData.hc.totalHc);
          }
        } else {
          value = num(monthData[row.field]);
        }

        fyTotal += value;

        const color =
          value === 0
            ? undefined
            : row.favorPositive
            ? value > 0
              ? token.colorSuccess
              : token.colorError
            : value > 0
            ? token.colorError
            : token.colorSuccess;

        record[col.key] = {
          value:
            row.key === 'billableHc'
              ? value.toFixed(0)
              : row.key === 'billableRatio'
              ? `${value.toFixed(1)}%`
              : formatCurrency(value),
          color,
        };
      });

      const fyColor =
        fyTotal === 0
          ? undefined
          : row.favorPositive
          ? fyTotal > 0
            ? token.colorSuccess
            : token.colorError
          : fyTotal > 0
          ? token.colorError
          : token.colorSuccess;

      record.fy = {
        value:
          row.key === 'billableHc'
            ? fyTotal.toFixed(0)
            : row.key === 'billableRatio'
            ? `${fyTotal.toFixed(1)}%`
            : formatCurrency(fyTotal),
        color: fyColor,
      };

      return record;
    });
  }, [delta, cols, token]);

  const columns = [
    {
      title: 'Metric',
      dataIndex: 'label',
      key: 'label',
      fixed: 'left' as const,
      width: 150,
    },
    ...cols.map((col) => ({
      title: col.label,
      key: col.key,
      dataIndex: col.key,
      width: 100,
      align: 'right' as const,
      render: (cell: { value: string; color?: string }) =>
        cell ? (
          <span style={{ color: cell.color }}>{cell.value}</span>
        ) : (
          '—'
        ),
    })),
    {
      title: 'FY',
      key: 'fy',
      dataIndex: 'fy',
      width: 120,
      align: 'right' as const,
      render: (cell: { value: string; color?: string }) =>
        cell ? (
          <span style={{ color: cell.color }}>{cell.value}</span>
        ) : (
          '—'
        ),
    },
  ];

  return (
    <Card>
      <Title level={4} style={{ fontFamily: HEADING_FONT }}>
        Delta View
      </Title>
      <Table
        dataSource={dataSource}
        columns={columns}
        pagination={false}
        scroll={{ x: true }}
        size="small"
      />
    </Card>
  );
}
