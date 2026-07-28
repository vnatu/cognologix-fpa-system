import { useCallback, useEffect, useMemo, useState } from 'react';
import {
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
  Tabs,
  theme,
  Typography,
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
import { formatCurrency } from '@/utils/formatDate';
import { HEADING_FONT } from '@/theme/antdTheme';
import {
  buildFyMonthOptions,
  buildFyQuarterOptions,
  defaultDashboardPeriod,
  quarterForMonth,
} from './utils';
import type {
  BuAnalysisResult,
  ExternalBuAnalysisRow,
  InternalBuAnalysisRow,
  PeriodGranularity,
  PeriodQuery,
  PlanDetail,
  PlanSummary,
  PositionBreakdownRow,
} from './types';
import { fetchBuAnalysis, fetchPlan, fetchPlans } from './api';

const { Title, Text } = Typography;

export default function BuAnalysisPage() {
  const { token } = theme.useToken();
  const [plans, setPlans] = useState<PlanSummary[]>([]);
  const [selectedPlanId, setSelectedPlanId] = useState<string | null>(null);
  const [plan, setPlan] = useState<PlanDetail | null>(null);
  const [granularity, setGranularity] = useState<PeriodGranularity>('MONTHLY');
  const [selectedMonth, setSelectedMonth] = useState<number>(4);
  const [selectedYear, setSelectedYear] = useState<number>(2026);
  const [selectedQuarter, setSelectedQuarter] = useState<number>(1);
  const [data, setData] = useState<BuAnalysisResult | null>(null);
  const [loading, setLoading] = useState(false);

  const periodQuery: PeriodQuery = useMemo(() => {
    if (granularity === 'MONTHLY') {
      return { granularity, month: selectedMonth, year: selectedYear };
    }
    if (granularity === 'QUARTERLY') {
      return { granularity, quarter: selectedQuarter, year: selectedYear };
    }
    return { granularity: 'ANNUAL' };
  }, [granularity, selectedMonth, selectedYear, selectedQuarter]);

  const monthOptions = useMemo(() => buildFyMonthOptions(plan), [plan]);
  const quarterOptions = useMemo(() => buildFyQuarterOptions(plan), [plan]);

  const pieColors = useMemo(
    () => [
      token.colorPrimary,
      token.colorSuccess,
      token.colorWarning,
      token.colorError,
      token.colorInfo,
      token.colorTextSecondary,
      token.colorPrimaryHover,
      token.colorSuccessHover,
      token.colorWarningHover,
      token.colorErrorHover,
    ],
    [token],
  );

  const loadPlans = useCallback(async () => {
    try {
      const list = await fetchPlans();
      setPlans(list);
      if (list.length > 0) {
        setSelectedPlanId(list[0].id);
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

  const loadAnalysis = useCallback(
    async (planId: string, period: PeriodQuery) => {
      setLoading(true);
      try {
        const result = await fetchBuAnalysis(planId, period);
        setData(result);
      } catch (error) {
        console.error('Failed to load BU analysis', error);
        setData(null);
      } finally {
        setLoading(false);
      }
    },
    [],
  );

  useEffect(() => {
    if (selectedPlanId && plan) {
      loadAnalysis(selectedPlanId, periodQuery);
    }
  }, [selectedPlanId, plan, periodQuery, loadAnalysis]);

  const overallGrossMarginPct = useMemo(() => {
    if (!data || data.totalCompanyRevenue === 0) return null;
    return (
      ((data.totalCompanyRevenue - data.totalCompanyPayrollCost) /
        data.totalCompanyRevenue) *
      100
    );
  }, [data]);

  const internalSummary = useMemo(() => {
    if (!data) return null;
    const totalInternalHc = data.internalBUs.reduce((s, r) => s + r.totalHc, 0);
    const totalInternalCost = data.internalBUs.reduce(
      (s, r) => s + r.totalPayrollCost,
      0,
    );
    const pctOfCompany =
      data.totalCompanyPayrollCost === 0
        ? 0
        : (totalInternalCost / data.totalCompanyPayrollCost) * 100;
    return { totalInternalHc, totalInternalCost, pctOfCompany };
  }, [data]);

  const costPieData = useMemo(() => {
    if (!data) return [];
    const slices = data.externalBUs.map((r) => ({
      name: r.customerName,
      value: r.totalPayrollCost,
    }));
    const internalTotal = data.internalBUs.reduce(
      (s, r) => s + r.totalPayrollCost,
      0,
    );
    if (internalTotal > 0) {
      slices.push({ name: 'Internal BUs', value: internalTotal });
    }
    return slices.filter((s) => s.value > 0);
  }, [data]);

  const revenuePieData = useMemo(() => {
    if (!data) return [];
    return data.externalBUs
      .map((r) => ({ name: r.customerName, value: r.actualRevenue }))
      .filter((s) => s.value > 0);
  }, [data]);

  const internalCostPieData = useMemo(() => {
    if (!data) return [];
    return data.internalBUs
      .map((r) => ({ name: r.customerName, value: r.totalPayrollCost }))
      .filter((s) => s.value > 0);
  }, [data]);

  if (!plans.length) {
    return (
      <div style={{ padding: 24 }}>
        <Empty description="No financial year plans available" />
      </div>
    );
  }

  const positionColumns: ColumnsType<PositionBreakdownRow> = [
    { title: 'Title', dataIndex: 'title', key: 'title' },
    {
      title: 'Headcount',
      dataIndex: 'headcount',
      key: 'headcount',
      align: 'right',
    },
    {
      title: 'Avg Payroll Cost (Rs L)',
      dataIndex: 'avgPayrollCost',
      key: 'avgPayrollCost',
      align: 'right',
      render: (v: number) => formatCurrency(v),
    },
    {
      title: '% of BU HC',
      dataIndex: 'pctOfBuHc',
      key: 'pctOfBuHc',
      align: 'right',
      render: (v: number) => `${Number(v).toFixed(1)}%`,
    },
  ];

  const externalColumns: ColumnsType<ExternalBuAnalysisRow> = [
    {
      title: 'Client',
      dataIndex: 'customerName',
      key: 'customerName',
      fixed: 'left',
      width: 160,
    },
    {
      title: 'Total HC',
      dataIndex: 'totalHc',
      key: 'totalHc',
      align: 'right',
      width: 90,
    },
    {
      title: 'Billable HC',
      dataIndex: 'billableHc',
      key: 'billableHc',
      align: 'right',
      width: 100,
    },
    {
      title: 'Non-Billable HC',
      dataIndex: 'nonBillableHc',
      key: 'nonBillableHc',
      align: 'right',
      width: 120,
    },
    {
      title: 'Billable %',
      key: 'billablePct',
      align: 'right',
      width: 100,
      render: (_, r) =>
        r.totalHc === 0
          ? '—'
          : `${((r.billableHc / r.totalHc) * 100).toFixed(1)}%`,
    },
    {
      title: 'Total Payroll Cost (Rs L)',
      dataIndex: 'totalPayrollCost',
      key: 'totalPayrollCost',
      align: 'right',
      width: 160,
      render: (v: number) => formatCurrency(v),
    },
    {
      title: 'Avg Cost per Head (Rs L)',
      dataIndex: 'avgPayrollCostPerHead',
      key: 'avgPayrollCostPerHead',
      align: 'right',
      width: 160,
      render: (v: number) => formatCurrency(v),
    },
    {
      title: 'Actual Revenue (Rs L)',
      dataIndex: 'actualRevenue',
      key: 'actualRevenue',
      align: 'right',
      width: 150,
      render: (v: number) => formatCurrency(v),
    },
    {
      title: 'Gross Margin (Rs L)',
      dataIndex: 'grossMargin',
      key: 'grossMargin',
      align: 'right',
      width: 140,
      render: (v: number) => (
        <Text
          style={{
            color: v >= 0 ? token.colorSuccess : token.colorError,
          }}
        >
          {formatCurrency(v)}
        </Text>
      ),
    },
    {
      title: 'Gross Margin %',
      dataIndex: 'grossMarginPct',
      key: 'grossMarginPct',
      align: 'right',
      width: 120,
      render: (v: number) => (
        <Text
          style={{
            color: v >= 0 ? token.colorSuccess : token.colorError,
          }}
        >
          {Number(v).toFixed(1)}%
        </Text>
      ),
    },
    {
      title: 'BU Cost % of Total',
      dataIndex: 'buCostPctOfTotal',
      key: 'buCostPctOfTotal',
      align: 'right',
      width: 130,
      render: (v: number) => `${Number(v).toFixed(1)}%`,
    },
    {
      title: 'BU Revenue % of Total',
      dataIndex: 'buRevenuePctOfTotal',
      key: 'buRevenuePctOfTotal',
      align: 'right',
      width: 150,
      render: (v: number) => `${Number(v).toFixed(1)}%`,
    },
  ];

  const internalColumns: ColumnsType<InternalBuAnalysisRow> = [
    {
      title: 'Business Unit',
      dataIndex: 'customerName',
      key: 'customerName',
      fixed: 'left',
      width: 180,
    },
    {
      title: 'Total HC',
      dataIndex: 'totalHc',
      key: 'totalHc',
      align: 'right',
      width: 90,
    },
    {
      title: 'Billable HC',
      dataIndex: 'billableHc',
      key: 'billableHc',
      align: 'right',
      width: 100,
    },
    {
      title: 'Non-Billable HC',
      dataIndex: 'nonBillableHc',
      key: 'nonBillableHc',
      align: 'right',
      width: 120,
    },
    {
      title: 'Total Payroll Cost (Rs L)',
      dataIndex: 'totalPayrollCost',
      key: 'totalPayrollCost',
      align: 'right',
      width: 160,
      render: (v: number) => formatCurrency(v),
    },
    {
      title: 'Avg Cost per Head (Rs L)',
      dataIndex: 'avgPayrollCostPerHead',
      key: 'avgPayrollCostPerHead',
      align: 'right',
      width: 160,
      render: (v: number) => formatCurrency(v),
    },
    {
      title: 'Cost % of Total',
      dataIndex: 'buCostPctOfTotal',
      key: 'buCostPctOfTotal',
      align: 'right',
      width: 130,
      render: (v: number) => `${Number(v).toFixed(1)}%`,
    },
  ];

  return (
    <div style={{ padding: 24 }}>
      <Space direction="vertical" size="large" style={{ width: '100%' }}>
        <Title level={3} style={{ fontFamily: HEADING_FONT, margin: 0 }}>
          BU Analysis
        </Title>

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

        {!loading && data && (
          <Tabs
            items={[
              {
                key: 'external',
                label: 'External BUs',
                children: (
                  <Space
                    direction="vertical"
                    size="large"
                    style={{ width: '100%' }}
                  >
                    <Row gutter={[16, 16]}>
                      <Col xs={24} sm={8}>
                        <Card>
                          <Statistic
                            title="Total Company Revenue"
                            value={formatCurrency(data.totalCompanyRevenue)}
                          />
                        </Card>
                      </Col>
                      <Col xs={24} sm={8}>
                        <Card>
                          <Statistic
                            title="Total Company Payroll Cost"
                            value={formatCurrency(
                              data.totalCompanyPayrollCost,
                            )}
                          />
                        </Card>
                      </Col>
                      <Col xs={24} sm={8}>
                        <Card>
                          <Statistic
                            title="Overall Gross Margin %"
                            value={
                              overallGrossMarginPct != null
                                ? `${overallGrossMarginPct.toFixed(1)}%`
                                : '—'
                            }
                            valueStyle={{
                              color:
                                overallGrossMarginPct != null &&
                                overallGrossMarginPct >= 0
                                  ? token.colorSuccess
                                  : token.colorError,
                            }}
                          />
                        </Card>
                      </Col>
                    </Row>

                    <Row gutter={[16, 16]}>
                      <Col xs={24} lg={12}>
                        <Card
                          title={
                            <span style={{ fontFamily: HEADING_FONT }}>
                              BU Cost as % of Total Payroll
                            </span>
                          }
                        >
                          {costPieData.length === 0 ? (
                            <Empty description="No payroll cost data" />
                          ) : (
                            <ResponsiveContainer width="100%" height={280}>
                              <PieChart>
                                <Pie
                                  data={costPieData}
                                  dataKey="value"
                                  nameKey="name"
                                  cx="50%"
                                  cy="50%"
                                  outerRadius={90}
                                  label={({ name, percent }) =>
                                    `${name}: ${((percent ?? 0) * 100).toFixed(0)}%`
                                  }
                                >
                                  {costPieData.map((_, i) => (
                                    <Cell
                                      key={`cost-${i}`}
                                      fill={pieColors[i % pieColors.length]}
                                    />
                                  ))}
                                </Pie>
                                <Tooltip
                                  formatter={(value) =>
                                    formatCurrency(Number(value ?? 0))
                                  }
                                />
                                <Legend />
                              </PieChart>
                            </ResponsiveContainer>
                          )}
                        </Card>
                      </Col>
                      <Col xs={24} lg={12}>
                        <Card
                          title={
                            <span style={{ fontFamily: HEADING_FONT }}>
                              BU Revenue as % of Total Revenue
                            </span>
                          }
                        >
                          {revenuePieData.length === 0 ? (
                            <Empty description="No revenue data" />
                          ) : (
                            <ResponsiveContainer width="100%" height={280}>
                              <PieChart>
                                <Pie
                                  data={revenuePieData}
                                  dataKey="value"
                                  nameKey="name"
                                  cx="50%"
                                  cy="50%"
                                  outerRadius={90}
                                  label={({ name, percent }) =>
                                    `${name}: ${((percent ?? 0) * 100).toFixed(0)}%`
                                  }
                                >
                                  {revenuePieData.map((_, i) => (
                                    <Cell
                                      key={`rev-${i}`}
                                      fill={pieColors[i % pieColors.length]}
                                    />
                                  ))}
                                </Pie>
                                <Tooltip
                                  formatter={(value) =>
                                    formatCurrency(Number(value ?? 0))
                                  }
                                />
                                <Legend />
                              </PieChart>
                            </ResponsiveContainer>
                          )}
                        </Card>
                      </Col>
                    </Row>

                    <Card
                      title={
                        <span style={{ fontFamily: HEADING_FONT }}>
                          External BUs — {data.periodLabel}
                        </span>
                      }
                    >
                      <Table
                        rowKey="customerCode"
                        columns={externalColumns}
                        dataSource={data.externalBUs}
                        scroll={{ x: 1600 }}
                        pagination={false}
                        expandable={{
                          expandedRowRender: (record) => (
                            <Table
                              rowKey="title"
                              size="small"
                              columns={positionColumns}
                              dataSource={record.positionBreakdown}
                              pagination={false}
                            />
                          ),
                          rowExpandable: (record) =>
                            record.positionBreakdown.length > 0,
                        }}
                      />
                    </Card>
                  </Space>
                ),
              },
              {
                key: 'internal',
                label: 'Internal BUs',
                children: (
                  <Space
                    direction="vertical"
                    size="large"
                    style={{ width: '100%' }}
                  >
                    <Row gutter={[16, 16]}>
                      <Col xs={24} sm={8}>
                        <Card>
                          <Statistic
                            title="Total Internal HC"
                            value={internalSummary?.totalInternalHc ?? 0}
                          />
                        </Card>
                      </Col>
                      <Col xs={24} sm={8}>
                        <Card>
                          <Statistic
                            title="Total Internal Payroll Cost"
                            value={formatCurrency(
                              internalSummary?.totalInternalCost ?? 0,
                            )}
                          />
                        </Card>
                      </Col>
                      <Col xs={24} sm={8}>
                        <Card>
                          <Statistic
                            title="Internal Cost as % of Company Total"
                            value={`${(internalSummary?.pctOfCompany ?? 0).toFixed(1)}%`}
                          />
                        </Card>
                      </Col>
                    </Row>

                    <Card
                      title={
                        <span style={{ fontFamily: HEADING_FONT }}>
                          Internal BU Cost as % of Company Payroll
                        </span>
                      }
                    >
                      {internalCostPieData.length === 0 ? (
                        <Empty description="No internal payroll cost data" />
                      ) : (
                        <ResponsiveContainer width="100%" height={280}>
                          <PieChart>
                            <Pie
                              data={internalCostPieData}
                              dataKey="value"
                              nameKey="name"
                              cx="50%"
                              cy="50%"
                              outerRadius={90}
                              label={({ name, percent }) =>
                                `${name}: ${((percent ?? 0) * 100).toFixed(0)}%`
                              }
                            >
                              {internalCostPieData.map((_, i) => (
                                <Cell
                                  key={`intl-${i}`}
                                  fill={pieColors[i % pieColors.length]}
                                />
                              ))}
                            </Pie>
                            <Tooltip
                              formatter={(value) =>
                                formatCurrency(Number(value ?? 0))
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
                          Internal BUs — {data.periodLabel}
                        </span>
                      }
                    >
                      <Table
                        rowKey="customerCode"
                        columns={internalColumns}
                        dataSource={data.internalBUs}
                        scroll={{ x: 1000 }}
                        pagination={false}
                        expandable={{
                          expandedRowRender: (record) => (
                            <Table
                              rowKey="title"
                              size="small"
                              columns={positionColumns}
                              dataSource={record.positionBreakdown}
                              pagination={false}
                            />
                          ),
                          rowExpandable: (record) =>
                            record.positionBreakdown.length > 0,
                        }}
                      />
                    </Card>
                  </Space>
                ),
              },
            ]}
          />
        )}

        {!loading && !data && (
          <Empty description="No BU analysis data for the selected period" />
        )}
      </Space>
    </div>
  );
}
