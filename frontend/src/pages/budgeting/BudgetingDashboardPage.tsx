import { useCallback, useEffect, useMemo, useState, type CSSProperties, type ReactNode } from 'react';
import {
  Card,
  Col,
  Empty,
  Popover,
  Radio,
  Row,
  Segmented,
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
import { QuestionCircleOutlined } from '@ant-design/icons';
import {
  Line,
  LineChart,
  ResponsiveContainer,
  XAxis,
  YAxis,
  Tooltip,
  Legend,
  CartesianGrid,
  ReferenceArea,
} from 'recharts';
import { formatCurrency } from '@/utils/formatDate';
import { HEADING_FONT } from '@/theme/antdTheme';
import {
  buildFyMonthCols,
  buildFyMonthOptions,
  buildFyQuarterOptions,
  billableRatio,
  defaultDashboardPeriod,
  FY_MONTH_LABELS,
  num,
  pct,
  quarterForMonth,
  TYPE_LABELS,
} from './utils';
import type {
  CostPerEmployeeResult,
  DeltaResult,
  FyMonthCol,
  PeriodGranularity,
  PeriodQuery,
  PlanDetail,
  PlanSummary,
  PlanVsActualResult,
  RollingForecastResult,
} from './types';
import {
  fetchCostPerEmployee,
  fetchDelta,
  fetchPlan,
  fetchPlans,
  fetchPlanVsActual,
  fetchRollingForecast,
} from './api';

const { Title, Text } = Typography;

const PANEL_HELP = {
  headlineKpis:
    'Key financial metrics for the selected period. Plan = your budgeted target. Actual = what was invoiced/incurred. Variance = Actual minus Plan (positive is good for Revenue and EBITDA, negative is good for Costs). Green = favorable, Red = unfavorable.',
  rollingForecast:
    'Shows the full financial year trend. Baseline (dashed) = your published plan. Rolling Forecast (solid red) = actuals for past months + current plan for future months. Actuals (green) = months where real data has been finalised. The gap between Rolling Forecast and Baseline is your Delta.',
  pvaRevenue:
    'Compares planned revenue against actual invoiced revenue per client for the selected period. Planned TM Revenue = what you budgeted to invoice on Time & Material basis. Planned Fixed Bid = contracted fixed amounts. Actual = what was actually invoiced in Zoho Books.',
  pvaHc:
    'Compares planned headcount against actual headcount per category for the selected period. Billable = employees deployed on client projects. Bench = delivery staff not yet deployed. Support = non-delivery staff (HR, Admin, Finance). Leadership = senior management. Management = co-founders.',
  pvaCosts:
    'Compares planned salary and overhead costs against actuals. Salary actuals flow automatically from finalised People & Payroll periods. Overhead actuals are entered manually. Total Payroll Cost includes gross pay plus employer contributions (EPF, EPS, EDLI, Gratuity etc.).',
  plSummary:
    'Consolidated Profit & Loss statement. Revenue flows from client invoices. COGS (Cost of Goods Sold) = billable and bench staff salaries + delivery overheads. Gross Profit = Revenue minus COGS. OpEx = support, leadership, and management costs plus non-delivery overheads. EBITDA = Gross Profit minus OpEx.',
  costPerEmployee:
    'Fully loaded cost per head by employee category using Full Absorption Costing. Layer 1 = direct salary + employer statutory contributions. Layer 2 = direct overhead per head (insurance, software, training). Layer 3 = shared overhead allocated to billable employees only (rent, electricity etc.). The Minimum Billing Rate for billable staff = Layer 1 + 2 + 3 — this is your break-even rate for client negotiations.',
  deltaView:
    'Delta = Rolling Forecast minus Baseline. Shows how your current trajectory differs from your original plan. For Revenue and Margin: positive delta = tracking above plan (good). For Costs: negative delta = tracking below plan (good = under-budget). Traffic light colors: green = favorable, red = unfavorable.',
} as const;

function PanelHelpTitle({
  title,
  helpTitle,
  helpContent,
  style,
}: {
  title: ReactNode;
  helpTitle: string;
  helpContent: string;
  style?: CSSProperties;
}) {
  const { token } = theme.useToken();
  return (
    <Space size={8} align="center" style={style}>
      <Title level={4} style={{ fontFamily: HEADING_FONT, margin: 0 }}>
        {title}
      </Title>
      <Popover
        title={helpTitle}
        content={
          <div style={{ maxWidth: 360, whiteSpace: 'pre-wrap' }}>
            {helpContent}
          </div>
        }
        trigger="click"
      >
        <QuestionCircleOutlined
          style={{
            color: token.colorTextSecondary,
            fontSize: 14,
            cursor: 'pointer',
          }}
          aria-label={`About ${helpTitle}`}
        />
      </Popover>
    </Space>
  );
}

export default function BudgetingDashboardPage() {
  const { token } = theme.useToken();
  const [plans, setPlans] = useState<PlanSummary[]>([]);
  const [selectedPlanId, setSelectedPlanId] = useState<string | null>(null);
  const [plan, setPlan] = useState<PlanDetail | null>(null);
  const [selectedTypeId, setSelectedTypeId] = useState<string | null>(null);
  const [granularity, setGranularity] = useState<PeriodGranularity>('MONTHLY');
  const [selectedMonth, setSelectedMonth] = useState<number>(4);
  const [selectedYear, setSelectedYear] = useState<number>(2026);
  const [selectedQuarter, setSelectedQuarter] = useState<number>(1);
  const [pva, setPva] = useState<PlanVsActualResult | null>(null);
  const [rf, setRf] = useState<RollingForecastResult | null>(null);
  const [delta, setDelta] = useState<DeltaResult | null>(null);
  const [loading, setLoading] = useState(false);
  const [costPerEmp, setCostPerEmp] = useState<CostPerEmployeeResult | null>(
    null,
  );

  const periodQuery: PeriodQuery = useMemo(() => {
    if (granularity === 'MONTHLY') {
      return { granularity, month: selectedMonth, year: selectedYear };
    }
    if (granularity === 'QUARTERLY') {
      return { granularity, quarter: selectedQuarter, year: selectedYear };
    }
    return { granularity: 'ANNUAL' };
  }, [granularity, selectedMonth, selectedYear, selectedQuarter]);

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
      const defaults = defaultDashboardPeriod(planData);
      setGranularity(defaults.granularity);
      setSelectedMonth(defaults.month);
      setSelectedYear(defaults.year);
      setSelectedQuarter(defaults.quarter);
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
    async (planId: string, typeId: string | null, period: PeriodQuery) => {
      setLoading(true);
      try {
        const [pvaData, rfData, deltaData, costData] = await Promise.all([
          fetchPlanVsActual(planId, typeId ?? undefined, period),
          fetchRollingForecast(planId, period),
          fetchDelta(planId, period),
          fetchCostPerEmployee(planId, period, typeId ?? undefined),
        ]);
        setPva(pvaData);
        setRf(rfData);
        setDelta(deltaData);
        setCostPerEmp(costData);
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
      loadDashboardData(selectedPlanId, selectedTypeId, periodQuery);
    }
  }, [selectedPlanId, selectedTypeId, plan, periodQuery, loadDashboardData]);

  const cols = useMemo(() => buildFyMonthCols(plan), [plan]);
  const monthOptions = useMemo(() => buildFyMonthOptions(plan), [plan]);
  const quarterOptions = useMemo(() => buildFyQuarterOptions(plan), [plan]);

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
          <Space wrap size="middle">
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
            <Space size="small">
              <Text type="secondary">Granularity</Text>
              <Segmented
                value={granularity}
                onChange={(v) => {
                  const g = v as PeriodGranularity;
                  setGranularity(g);
                  if (g === 'QUARTERLY') {
                    setSelectedQuarter(quarterForMonth(selectedMonth));
                    const qOpt = quarterOptions.find(
                      (q) => q.quarter === quarterForMonth(selectedMonth),
                    );
                    if (qOpt) setSelectedYear(qOpt.year);
                  }
                }}
                options={[
                  { label: 'Monthly', value: 'MONTHLY' },
                  { label: 'Quarterly', value: 'QUARTERLY' },
                  { label: 'Annual', value: 'ANNUAL' },
                ]}
              />
            </Space>
            {granularity === 'MONTHLY' && (
              <Select
                style={{ minWidth: 180 }}
                value={`${selectedYear}-${selectedMonth}`}
                onChange={(val) => {
                  const opt = monthOptions.find((o) => o.value === val);
                  if (opt) {
                    setSelectedMonth(opt.month);
                    setSelectedYear(opt.year);
                    setSelectedQuarter(quarterForMonth(opt.month));
                  }
                }}
                options={monthOptions.map((o) => ({
                  label: o.label,
                  value: o.value,
                }))}
              />
            )}
            {granularity === 'QUARTERLY' && (
              <Select
                style={{ minWidth: 160 }}
                value={String(selectedQuarter)}
                onChange={(val) => {
                  const opt = quarterOptions.find((o) => o.value === val);
                  if (opt) {
                    setSelectedQuarter(opt.quarter);
                    setSelectedYear(opt.year);
                    setSelectedMonth(
                      opt.quarter === 1
                        ? 4
                        : opt.quarter === 2
                          ? 7
                          : opt.quarter === 3
                            ? 10
                            : 1,
                    );
                  }
                }}
                options={quarterOptions.map((o) => ({
                  label: o.label,
                  value: o.value,
                }))}
              />
            )}
          </Space>
        </Card>

        {loading && <Skeleton active />}

        {!loading && pva && rf && delta && plan && (
          <>
            <HeadlineKPIsPanel pva={pva} token={token} />

            <RollingForecastPanel
              rf={rf}
              delta={delta}
              granularity={granularity}
              token={token}
            />

            <PvaRevenuePanel pva={pva} token={token} />

            <PvaHcPanel pva={pva} token={token} />

            <PvaCostsPanel pva={pva} token={token} />

            <PlSummaryPanel
              pva={pva}
              cols={cols}
              granularity={granularity}
              selectedMonth={selectedMonth}
              selectedYear={selectedYear}
              selectedQuarter={selectedQuarter}
              token={token}
            />

            {costPerEmp && (
              <CostPerEmployeePanel costPerEmp={costPerEmp} token={token} />
            )}

            <DeltaViewPanel delta={delta} token={token} />
          </>
        )}
      </Space>
    </div>
  );
}

function VarianceText({
  variance,
  plan,
  favorPositive = true,
  token,
}: {
  variance: number | null | undefined;
  plan: number | null | undefined;
  favorPositive?: boolean;
  token: ReturnType<typeof theme.useToken>['token'];
}) {
  if (variance == null) return null;
  const good = favorPositive ? variance > 0 : variance < 0;
  return (
    <Text style={{ color: good ? token.colorSuccess : token.colorError }}>
      Variance: {formatCurrency(variance)} ({pct(variance, plan)?.toFixed(1)}%)
    </Text>
  );
}

interface HeadlineKPIsPanelProps {
  pva: PlanVsActualResult;
  token: ReturnType<typeof theme.useToken>['token'];
}

function HeadlineKPIsPanel({ pva, token }: HeadlineKPIsPanelProps) {
  const totals = pva.selectedPeriod;

  const billableRatioMonth = useMemo(() => {
    if (pva.granularity === 'ANNUAL') {
      const withActuals = pva.months.filter((m) => m.hasActuals);
      return withActuals[withActuals.length - 1] ?? pva.months[0];
    }
    if (pva.granularity === 'MONTHLY') {
      return (
        selectedPvaMonth(pva) ??
        pva.months.find((m) => m.hasActuals) ??
        pva.months[0]
      );
    }
    // Quarterly: last month in the quarter that has data
    const qMatch = /^Q([1-4])/.exec(pva.periodLabel);
    const q = qMatch ? Number(qMatch[1]) : 1;
    const inQ = pva.months.filter((m) => quarterForMonth(m.month) === q);
    return (
      [...inQ].reverse().find((m) => m.hasActuals) ??
      inQ[inQ.length - 1] ??
      pva.months[0]
    );
  }, [pva]);

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
      <Space direction="vertical" size="small" style={{ width: '100%' }}>
        <div
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'baseline',
            gap: 16,
            flexWrap: 'wrap',
          }}
        >
          <PanelHelpTitle
            title="Headline KPIs"
            helpTitle="Headline KPIs"
            helpContent={PANEL_HELP.headlineKpis}
          />
          <Title level={4} style={{ fontFamily: HEADING_FONT, margin: 0 }}>
            {pva.periodLabel}
          </Title>
        </div>
        {pva.actualsCoverageNote && (
          <Text type="secondary">{pva.actualsCoverageNote}</Text>
        )}
        <Row gutter={16}>
          <Col span={8}>
            <Statistic
              title="Total Revenue (Rs L)"
              value={formatCurrency(
                totals.totalRevenue.actual ?? totals.totalRevenue.plan,
              )}
              valueStyle={{
                color:
                  totals.totalRevenue.variance != null &&
                  totals.totalRevenue.variance > 0
                    ? token.colorSuccess
                    : undefined,
              }}
            />
            <VarianceText
              variance={totals.totalRevenue.variance}
              plan={totals.totalRevenue.plan}
              token={token}
            />
            <div>
              <Text type="secondary">
                Plan {formatCurrency(totals.totalRevenue.plan)}
                {totals.totalRevenue.actual != null &&
                  ` · Actual ${formatCurrency(totals.totalRevenue.actual)}`}
              </Text>
            </div>
          </Col>
          <Col span={8}>
            <Statistic
              title="EBITDA (Rs L)"
              value={formatCurrency(totals.ebitda.actual ?? totals.ebitda.plan)}
              valueStyle={{
                color:
                  totals.ebitda.variance != null && totals.ebitda.variance > 0
                    ? token.colorSuccess
                    : undefined,
              }}
            />
            <VarianceText
              variance={totals.ebitda.variance}
              plan={totals.ebitda.plan}
              token={token}
            />
            <div>
              <Text type="secondary">
                Plan {formatCurrency(totals.ebitda.plan)}
                {totals.ebitda.actual != null &&
                  ` · Actual ${formatCurrency(totals.ebitda.actual)}`}
              </Text>
            </div>
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
      </Space>
    </Card>
  );
}

interface RollingForecastPanelProps {
  rf: RollingForecastResult;
  delta: DeltaResult;
  granularity: PeriodGranularity;
  token: ReturnType<typeof theme.useToken>['token'];
}

function RollingForecastPanel({
  rf,
  delta,
  granularity,
  token,
}: RollingForecastPanelProps) {
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
      const highlighted =
        granularity === 'ANNUAL'
          ? false
          : granularity === 'MONTHLY'
            ? m.month === rf.highlightMonth && m.year === rf.highlightYear
            : quarterForMonth(m.month) === rf.highlightQuarter;

      return {
        month: FY_MONTH_LABELS[i],
        baseline: baselineValue,
        forecast: rfValue,
        actual: m.fromActuals ? rfValue : null,
        highlighted,
      };
    });
  }, [rf, delta.months, metric, granularity]);

  const highlightRange = useMemo(() => {
    const idxs = chartData
      .map((d, i) => (d.highlighted ? i : -1))
      .filter((i) => i >= 0);
    if (idxs.length === 0) return null;
    return {
      x1: FY_MONTH_LABELS[idxs[0]],
      x2: FY_MONTH_LABELS[idxs[idxs.length - 1]],
    };
  }, [chartData]);

  return (
    <Card>
      <Space direction="vertical" size="middle" style={{ width: '100%' }}>
        <div
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
          }}
        >
          <PanelHelpTitle
            title="Rolling Forecast vs Baseline"
            helpTitle="Rolling Forecast vs Baseline"
            helpContent={PANEL_HELP.rollingForecast}
          />
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
              formatter={(value) => {
                const numValue = typeof value === 'number' ? value : Number(value);
                return metric === 'billableHc'
                  ? numValue.toFixed(0)
                  : formatCurrency(numValue);
              }}
            />
            <Legend />
            {highlightRange && (
              <ReferenceArea
                x1={highlightRange.x1}
                x2={highlightRange.x2}
                fill={token.colorPrimary}
                fillOpacity={0.08}
              />
            )}
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

function selectedPvaMonth(pva: PlanVsActualResult) {
  if (pva.granularity === 'MONTHLY') {
    const names = [
      '',
      'January',
      'February',
      'March',
      'April',
      'May',
      'June',
      'July',
      'August',
      'September',
      'October',
      'November',
      'December',
    ];
    return (
      pva.months.find((m) => pva.periodLabel === `${names[m.month]} ${m.year}`) ??
      pva.months[0]
    );
  }
  if (pva.granularity === 'QUARTERLY') {
    const qMatch = /^Q([1-4])/.exec(pva.periodLabel);
    const q = qMatch ? Number(qMatch[1]) : 1;
    const inQ = pva.months.filter((m) => quarterForMonth(m.month) === q);
    return (
      [...inQ].reverse().find((m) => m.hasActuals) ??
      inQ[inQ.length - 1] ??
      null
    );
  }
  // Annual: latest month with actuals for stock metrics (HC)
  const withActuals = pva.months.filter((m) => m.hasActuals);
  return withActuals[withActuals.length - 1] ?? pva.months[0] ?? null;
}

interface PvaRevenuePanelProps {
  pva: PlanVsActualResult;
  token: ReturnType<typeof theme.useToken>['token'];
}

function PvaRevenuePanel({ pva, token }: PvaRevenuePanelProps) {
  const monthData = useMemo(
    () => (pva.granularity === 'MONTHLY' ? selectedPvaMonth(pva) : null),
    [pva],
  );

  const dataSource = useMemo(() => {
    // Monthly: client breakdown for selected month
    // Quarterly/Annual: total row from selectedPeriod only (client roll-up deferred)
    if (monthData) {
      const rows = monthData.revenueByClient.map((c) => ({
        key: c.customerId,
        client: c.customerCode,
        plan: formatCurrency(c.totalRevenue.plan),
        actual:
          c.totalRevenue.actual != null
            ? formatCurrency(c.totalRevenue.actual)
            : '—',
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
    }

    const t = pva.selectedPeriod.totalRevenue;
    return [
      {
        key: 'total',
        client: `Total (${pva.periodLabel})`,
        plan: formatCurrency(t.plan),
        actual: t.actual != null ? formatCurrency(t.actual) : '—',
        variance: t.variance != null ? formatCurrency(t.variance) : '—',
        variancePct:
          t.variance != null
            ? `${pct(t.variance, t.plan)?.toFixed(1) ?? '—'}%`
            : '—',
        varianceColor:
          t.variance != null && t.variance > 0
            ? token.colorSuccess
            : token.colorError,
      },
    ];
  }, [monthData, pva, token]);

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
      render: (text: string, record: { varianceColor: string }) => (
        <span style={{ color: record.varianceColor }}>{text}</span>
      ),
    },
    {
      title: 'Variance %',
      dataIndex: 'variancePct',
      key: 'variancePct',
      align: 'right' as const,
      render: (text: string, record: { varianceColor: string }) => (
        <span style={{ color: record.varianceColor }}>{text}</span>
      ),
    },
  ];

  return (
    <Card>
      <Space direction="vertical" size="middle" style={{ width: '100%' }}>
        <PanelHelpTitle
          title={`PvA Revenue — ${pva.periodLabel}`}
          helpTitle="Plan vs Actual: Revenue"
          helpContent={PANEL_HELP.pvaRevenue}
        />
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
  token: ReturnType<typeof theme.useToken>['token'];
}

function PvaHcPanel({ pva, token }: PvaHcPanelProps) {
  // HC is a stock metric — show end-of-period month (latest actuals in scope)
  const monthData = useMemo(() => selectedPvaMonth(pva), [pva]);

  const dataSource = useMemo(() => {
    if (!monthData) {
      return [
        {
          key: 'note',
          category: `See period totals for ${pva.periodLabel}`,
          plan: '—',
          actual: '—',
          variance: '—',
          varianceColor: undefined as string | undefined,
        },
      ];
    }
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

    const ratioPlan = billableRatio(hc.plan.billableHc, hc.plan.totalHc);
    const ratioActual = billableRatio(hc.actual.billableHc, hc.actual.totalHc);
    const ratioVariance = ratioActual - ratioPlan;
    rows.push({
      key: 'ratio',
      category: 'Billable Ratio %',
      plan: ratioPlan.toFixed(1) as unknown as number,
      actual: ratioActual.toFixed(1) as unknown as number,
      variance: ratioVariance.toFixed(1) as unknown as number,
    });

    return rows.map((r) => ({
      ...r,
      varianceColor:
        typeof r.variance === 'number' && r.variance > 0
          ? token.colorSuccess
          : token.colorError,
    }));
  }, [monthData, pva.periodLabel, token]);

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
      render: (
        text: number | string,
        record: { varianceColor?: string },
      ) => <span style={{ color: record.varianceColor }}>{text}</span>,
    },
  ];

  return (
    <Card>
      <Space direction="vertical" size="middle" style={{ width: '100%' }}>
        <PanelHelpTitle
          title={`PvA HC — ${pva.periodLabel}`}
          helpTitle="Plan vs Actual: HC"
          helpContent={PANEL_HELP.pvaHc}
        />
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
  token: ReturnType<typeof theme.useToken>['token'];
}

function PvaCostsPanel({ pva, token }: PvaCostsPanelProps) {
  const monthData = useMemo(
    () => (pva.granularity === 'MONTHLY' ? selectedPvaMonth(pva) : null),
    [pva],
  );

  const salaryDataSource = useMemo(() => {
    if (!monthData) {
      const t = pva.selectedPeriod;
      return [
        {
          key: 'total',
          category: `Total Salary (${pva.periodLabel})`,
          plan: formatCurrency(t.totalSalaryCost.plan),
          actual:
            t.totalSalaryCost.actual != null
              ? formatCurrency(t.totalSalaryCost.actual)
              : '—',
          variance:
            t.totalSalaryCost.variance != null
              ? formatCurrency(t.totalSalaryCost.variance)
              : '—',
          varianceColor:
            t.totalSalaryCost.variance != null &&
            t.totalSalaryCost.variance > 0
              ? token.colorError
              : token.colorSuccess,
        },
      ];
    }
    const sal = monthData.salary;
    return [
      {
        key: 'billable',
        category: 'Billable',
        plan: formatCurrency(sal.plan.billable),
        actual: formatCurrency(sal.actual.billable),
        variance: formatCurrency(sal.variance.billable),
        varianceColor:
          sal.variance.billable > 0 ? token.colorError : token.colorSuccess,
      },
      {
        key: 'bench',
        category: 'Bench',
        plan: formatCurrency(sal.plan.bench),
        actual: formatCurrency(sal.actual.bench),
        variance: formatCurrency(sal.variance.bench),
        varianceColor:
          sal.variance.bench > 0 ? token.colorError : token.colorSuccess,
      },
      {
        key: 'support',
        category: 'Support',
        plan: formatCurrency(sal.plan.support),
        actual: formatCurrency(sal.actual.support),
        variance: formatCurrency(sal.variance.support),
        varianceColor:
          sal.variance.support > 0 ? token.colorError : token.colorSuccess,
      },
      {
        key: 'cofounders',
        category: 'Cofounders',
        plan: formatCurrency(sal.plan.cofounders),
        actual: formatCurrency(sal.actual.cofounders),
        variance: formatCurrency(sal.variance.cofounders),
        varianceColor:
          sal.variance.cofounders > 0 ? token.colorError : token.colorSuccess,
      },
      {
        key: 'seniorMgmt',
        category: 'Senior Mgmt',
        plan: formatCurrency(sal.plan.seniorMgmt),
        actual: formatCurrency(sal.actual.seniorMgmt),
        variance: formatCurrency(sal.variance.seniorMgmt),
        varianceColor:
          sal.variance.seniorMgmt > 0 ? token.colorError : token.colorSuccess,
      },
      {
        key: 'total',
        category: 'Total',
        plan: formatCurrency(sal.plan.total),
        actual: formatCurrency(sal.actual.total),
        variance: formatCurrency(sal.variance.total),
        varianceColor:
          sal.variance.total > 0 ? token.colorError : token.colorSuccess,
      },
    ];
  }, [monthData, pva, token]);

  const overheadDataSource = useMemo(() => {
    if (!monthData) {
      const t = pva.selectedPeriod;
      return [
        {
          key: 'total',
          line: `Total Overhead (${pva.periodLabel})`,
          plan: formatCurrency(t.totalOverhead.plan),
          actual:
            t.totalOverhead.actual != null
              ? formatCurrency(t.totalOverhead.actual)
              : '—',
          variance:
            t.totalOverhead.variance != null
              ? formatCurrency(t.totalOverhead.variance)
              : '—',
          varianceColor:
            t.totalOverhead.variance != null && t.totalOverhead.variance > 0
              ? token.colorError
              : token.colorSuccess,
        },
      ];
    }
    return monthData.overhead.map((o) => ({
      key: o.lineCode,
      line: o.lineCode,
      plan: formatCurrency(o.amount.plan),
      actual:
        o.amount.actual != null ? formatCurrency(o.amount.actual) : '—',
      variance:
        o.amount.variance != null ? formatCurrency(o.amount.variance) : '—',
      varianceColor:
        o.amount.variance != null && o.amount.variance > 0
          ? token.colorError
          : token.colorSuccess,
    }));
  }, [monthData, pva, token]);

  const columns = [
    { title: 'Category', dataIndex: 'category', key: 'category' },
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
      render: (text: string, record: { varianceColor: string }) => (
        <span style={{ color: record.varianceColor }}>{text}</span>
      ),
    },
  ];

  const overheadColumns = [
    { title: 'Line', dataIndex: 'line', key: 'line' },
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
      render: (text: string, record: { varianceColor: string }) => (
        <span style={{ color: record.varianceColor }}>{text}</span>
      ),
    },
  ];

  return (
    <Card>
      <Space direction="vertical" size="middle" style={{ width: '100%' }}>
        <PanelHelpTitle
          title={`PvA Costs — ${pva.periodLabel}`}
          helpTitle="Plan vs Actual: Costs"
          helpContent={PANEL_HELP.pvaCosts}
        />
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

interface PlSummaryPanelProps {
  pva: PlanVsActualResult;
  cols: FyMonthCol[];
  granularity: PeriodGranularity;
  selectedMonth: number;
  selectedYear: number;
  selectedQuarter: number;
  token: ReturnType<typeof theme.useToken>['token'];
}

function PeriodSummaryCard({
  pva,
  token,
}: {
  pva: PlanVsActualResult;
  token: ReturnType<typeof theme.useToken>['token'];
}) {
  const t = pva.selectedPeriod;
  return (
    <Card size="small" title={`${pva.periodLabel} summary`}>
      {pva.actualsCoverageNote && (
        <Text type="secondary" style={{ display: 'block', marginBottom: 8 }}>
          {pva.actualsCoverageNote}
        </Text>
      )}
      <Row gutter={16}>
        {(
          [
            ['Revenue', t.totalRevenue],
            ['Salary', t.totalSalaryCost],
            ['Overhead', t.totalOverhead],
            ['EBITDA', t.ebitda],
          ] as const
        ).map(([label, triad]) => (
          <Col span={6} key={label}>
            <Statistic
              title={label}
              value={formatCurrency(triad.actual ?? triad.plan)}
            />
            <VarianceText
              variance={triad.variance}
              plan={triad.plan}
              favorPositive={label === 'Revenue' || label === 'EBITDA'}
              token={token}
            />
          </Col>
        ))}
      </Row>
    </Card>
  );
}

function PlSummaryPanel({
  pva,
  cols,
  granularity,
  selectedMonth,
  selectedYear,
  selectedQuarter,
  token,
}: PlSummaryPanelProps) {
  const [mode, setMode] = useState<'plan' | 'actual'>('plan');

  const dataSource = useMemo(() => {
    const rows = [
      { key: 'revenue', label: 'Total Revenue', field: 'totalRevenue' as const },
      { key: 'cogs', label: 'Total COGS', field: 'totalCogs' as const },
      {
        key: 'grossProfit',
        label: 'Gross Profit',
        field: 'grossProfit' as const,
      },
      { key: 'grossMargin', label: 'Gross Margin %', field: null },
      { key: 'opex', label: 'Total OpEx', field: null },
      { key: 'ebitda', label: 'EBITDA', field: 'ebitda' as const },
    ];

    return rows.map((row) => {
      const record: Record<string, string> = {
        key: row.key,
        label: row.label,
      };

      const fillFromPeriod = (
        key: string,
        data: {
          totalRevenue: { plan: number; actual: number | null };
          totalCogs: { plan: number; actual: number | null };
          grossProfit: { plan: number; actual: number | null };
          ebitda: { plan: number; actual: number | null };
        },
      ) => {
        if (row.field === null) {
          if (row.key === 'grossMargin') {
            const revenue =
              mode === 'plan'
                ? data.totalRevenue.plan
                : (data.totalRevenue.actual ?? data.totalRevenue.plan);
            const gp =
              mode === 'plan'
                ? data.grossProfit.plan
                : (data.grossProfit.actual ?? data.grossProfit.plan);
            record[key] =
              revenue > 0 ? `${((gp / revenue) * 100).toFixed(1)}%` : '—';
          } else if (row.key === 'opex') {
            const gp =
              mode === 'plan'
                ? data.grossProfit.plan
                : (data.grossProfit.actual ?? data.grossProfit.plan);
            const ebitda =
              mode === 'plan'
                ? data.ebitda.plan
                : (data.ebitda.actual ?? data.ebitda.plan);
            record[key] = formatCurrency(gp - ebitda);
          }
        } else {
          const value =
            mode === 'plan'
              ? data[row.field].plan
              : (data[row.field].actual ?? data[row.field].plan);
          record[key] = formatCurrency(value);
        }
      };

      if (granularity === 'MONTHLY') {
        cols.forEach((col) => {
          const monthData = pva.months.find(
            (m) => m.month === col.planMonth && m.year === col.planYear,
          );
          if (!monthData) {
            record[col.key] = '—';
            return;
          }
          fillFromPeriod(col.key, monthData);
        });
      } else if (granularity === 'QUARTERLY') {
        (['q1', 'q2', 'q3', 'q4'] as const).forEach((q) => {
          fillFromPeriod(q, pva[q]);
        });
      } else {
        fillFromPeriod('selected', pva.selectedPeriod);
      }

      return record;
    });
  }, [pva, cols, mode, granularity]);

  const columns = useMemo(() => {
    const metricCol = {
      title: 'Metric',
      dataIndex: 'label',
      key: 'label',
      fixed: 'left' as const,
      width: 150,
    };

    if (granularity === 'MONTHLY') {
      return [
        metricCol,
        ...cols.map((col) => {
          const selected =
            col.planMonth === selectedMonth && col.planYear === selectedYear;
          return {
            title: `${col.label} (Rs L)`,
            key: col.key,
            dataIndex: col.key,
            width: 110,
            align: 'right' as const,
            onHeaderCell: () => ({
              style: selected
                ? { background: token.colorPrimaryBg, fontWeight: 600 }
                : undefined,
            }),
            onCell: () => ({
              style: selected
                ? { background: token.colorPrimaryBg }
                : undefined,
            }),
          };
        }),
      ];
    }

    if (granularity === 'QUARTERLY') {
      return [
        metricCol,
        ...(['q1', 'q2', 'q3', 'q4'] as const).map((q, i) => {
          const selected = i + 1 === selectedQuarter;
          return {
            title: `${q.toUpperCase()} (Rs L)`,
            key: q,
            dataIndex: q,
            width: 120,
            align: 'right' as const,
            onHeaderCell: () => ({
              style: selected
                ? { background: token.colorPrimaryBg, fontWeight: 600 }
                : undefined,
            }),
            onCell: () => ({
              style: selected
                ? { background: token.colorPrimaryBg }
                : undefined,
            }),
          };
        }),
      ];
    }

    return [
      metricCol,
      {
        title: `${pva.periodLabel} (Rs L)`,
        key: 'selected',
        dataIndex: 'selected',
        width: 160,
        align: 'right' as const,
      },
    ];
  }, [
    granularity,
    cols,
    selectedMonth,
    selectedYear,
    selectedQuarter,
    pva.periodLabel,
    token,
  ]);

  return (
    <Card>
      <Space direction="vertical" size="middle" style={{ width: '100%' }}>
        <div
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
          }}
        >
          <PanelHelpTitle
            title="P&L Summary"
            helpTitle="P&L Summary"
            helpContent={PANEL_HELP.plSummary}
          />
          <Radio.Group value={mode} onChange={(e) => setMode(e.target.value)}>
            <Radio.Button value="plan">Plan</Radio.Button>
            <Radio.Button value="actual">Actual</Radio.Button>
          </Radio.Group>
        </div>
        <PeriodSummaryCard pva={pva} token={token} />
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
      <PanelHelpTitle
        title={`Cost per Employee — ${costPerEmp.periodLabel}`}
        helpTitle="Cost per Employee"
        helpContent={PANEL_HELP.costPerEmployee}
      />
      <Tabs
        items={categories.map((cat) => ({
          key: cat.key,
          label: cat.label,
          children: (
            <Space direction="vertical" size="middle" style={{ width: '100%' }}>
              <Table
                dataSource={[
                  {
                    key: 'gross',
                    layer: 'Avg Gross Pay per Head',
                    amount: formatCurrency(cat.data.grossPayPerHead),
                  },
                  {
                    key: 'contrib',
                    layer:
                      cat.data.employerContributionsSource === 'ACTUAL'
                        ? 'Avg Employer Contributions per Head (actual)'
                        : 'Avg Employer Contributions per Head (13% estimate)',
                    amount: formatCurrency(
                      cat.data.employerContributionsPerHead,
                    ),
                  },
                  {
                    key: 'layer1',
                    layer: 'Total Layer 1',
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
                    <strong>Minimum Billing Rate:</strong>{' '}
                    {formatCurrency(minBillingRate)} Rs L/head
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
                    <strong>Target Rate:</strong> {formatCurrency(targetRate)}{' '}
                    Rs L/head
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
  token: ReturnType<typeof theme.useToken>['token'];
}

function DeltaViewPanel({ delta, token }: DeltaViewPanelProps) {
  const period = delta.periodTotal;

  const dataSource = useMemo(() => {
    const rows = [
      {
        key: 'revenue',
        label: 'Revenue',
        value: num(period.totalRevenue),
        favorPositive: true,
      },
      {
        key: 'billableHc',
        label: 'Billable HC',
        value: num(period.hc.billableHc),
        favorPositive: true,
        format: 'hc' as const,
      },
      {
        key: 'billableRatio',
        label: 'Billable Ratio %',
        value: billableRatio(period.hc.billableHc, period.hc.totalHc),
        favorPositive: true,
        format: 'pct' as const,
      },
      {
        key: 'salary',
        label: 'Salary',
        value: num(period.totalSalaryCost),
        favorPositive: false,
      },
      {
        key: 'overhead',
        label: 'Overhead',
        value: num(period.totalOverhead),
        favorPositive: false,
      },
      {
        key: 'grossProfit',
        label: 'Gross Profit',
        value: num(period.grossProfit),
        favorPositive: true,
      },
      {
        key: 'ebitda',
        label: 'EBITDA',
        value: num(period.ebitda),
        favorPositive: true,
      },
    ];

    return rows.map((row) => {
      const color =
        row.value === 0
          ? undefined
          : row.favorPositive
            ? row.value > 0
              ? token.colorSuccess
              : token.colorError
            : row.value > 0
              ? token.colorError
              : token.colorSuccess;
      const display =
        row.format === 'hc'
          ? row.value.toFixed(0)
          : row.format === 'pct'
            ? `${row.value.toFixed(1)}%`
            : formatCurrency(row.value);
      return {
        key: row.key,
        label: row.label,
        value: display,
        color,
      };
    });
  }, [period, token]);

  const columns = [
    {
      title: 'Metric',
      dataIndex: 'label',
      key: 'label',
      width: 180,
    },
    {
      title: `Delta — ${delta.periodLabel}`,
      dataIndex: 'value',
      key: 'value',
      align: 'right' as const,
      render: (text: string, record: { color?: string }) => (
        <span style={{ color: record.color }}>{text}</span>
      ),
    },
  ];

  return (
    <Card>
      <PanelHelpTitle
        title={`Delta View — ${delta.periodLabel}`}
        helpTitle="Delta View"
        helpContent={PANEL_HELP.deltaView}
      />
      <Table
        dataSource={dataSource}
        columns={columns}
        pagination={false}
        size="small"
      />
    </Card>
  );
}
