import { useCallback, useEffect, useMemo, useState, type CSSProperties } from 'react';
import {
  Alert,
  Button,
  Card,
  Select,
  Skeleton,
  Space,
  Table,
  Tabs,
  Tag,
  Typography,
  notification,
  theme,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { DownloadOutlined } from '@ant-design/icons';
import { formatCurrency } from '@/utils/formatDate';
import { HEADING_FONT } from '@/theme/antdTheme';
import { fetchCustomers } from '@/pages/customers/api';
import type { CustomerSummary } from '@/pages/customers/types';
import {
  billableRatio,
  buildFyMonthCols,
  num,
  STATUS_COLOR,
  TYPE_LABELS,
} from './utils';
import type {
  ClientRevenuePlanEntry,
  ForecastType,
  ForecastVersion,
  FyMonthCol,
  HcPlanMonth,
  OverheadBudgetEntry,
  OverheadLineItem,
  PlanDetail,
  PlanSummary,
  SalaryBudgetMonth,
} from './types';
import {
  exportAllPlanInputs,
  fetchHcPlan,
  fetchOverheadBudget,
  fetchOverheadLineItems,
  fetchPlan,
  fetchPlans,
  fetchRevenuePlan,
  fetchSalaryBudget,
} from './api';

const { Title, Text } = Typography;

const FORECAST_TYPE_ORDER = ['NORMAL', 'AGGRESSIVE', 'CONSERVATIVE'];

const OVERHEAD_CATEGORY_ORDER = [
  'Facilities',
  'Technology',
  'People and Welfare',
  'People & Welfare',
  'Travel and Transport',
  'Travel & Transport',
  'Finance and Legal',
  'Finance & Legal',
  'Delivery Costs',
  'Other Administrative Expenses',
];

type RowKind = 'data' | 'total' | 'section';

interface MetricRow {
  key: string;
  label: string;
  kind: RowKind;
  values: Record<string, string | number>;
  children?: MetricRow[];
}

export default function PlanSummaryPage() {
  const { token } = theme.useToken();
  const [plans, setPlans] = useState<PlanSummary[]>([]);
  const [selectedPlanId, setSelectedPlanId] = useState<string | null>(null);
  const [plan, setPlan] = useState<PlanDetail | null>(null);
  const [selectedTypeId, setSelectedTypeId] = useState<string | null>(null);
  const [selectedVersionId, setSelectedVersionId] = useState<string | null>(null);
  const [loadingPlan, setLoadingPlan] = useState(false);
  const [loadingData, setLoadingData] = useState(false);
  const [exporting, setExporting] = useState(false);

  const [hcPlan, setHcPlan] = useState<HcPlanMonth[]>([]);
  const [salaryBudget, setSalaryBudget] = useState<SalaryBudgetMonth[]>([]);
  const [revenuePlan, setRevenuePlan] = useState<ClientRevenuePlanEntry[]>([]);
  const [overheadBudget, setOverheadBudget] = useState<OverheadBudgetEntry[]>([]);
  const [lineItems, setLineItems] = useState<OverheadLineItem[]>([]);
  const [customers, setCustomers] = useState<CustomerSummary[]>([]);

  const totalRowStyle: CSSProperties = {
    fontWeight: 600,
    background: token.colorFillAlter,
  };
  const sectionHeaderStyle: CSSProperties = {
    fontWeight: 600,
    background: token.colorTextHeading,
    color: token.colorBgContainer,
  };

  useEffect(() => {
    fetchPlans()
      .then((data) => {
        setPlans(data);
        setSelectedPlanId((prev) => prev ?? data[0]?.id ?? null);
      })
      .catch((error) => {
        notification.error({
          message: 'Failed to load plans',
          description: String(error),
        });
      });
  }, []);

  const loadPlan = useCallback(async (planId: string) => {
    setLoadingPlan(true);
    try {
      const data = await fetchPlan(planId);
      setPlan(data);
      const sorted = [...data.forecastTypes].sort((a, b) => {
        const ai = FORECAST_TYPE_ORDER.indexOf(a.typeName);
        const bi = FORECAST_TYPE_ORDER.indexOf(b.typeName);
        return (ai < 0 ? 99 : ai) - (bi < 0 ? 99 : bi);
      });
      const primary =
        sorted.find((t) => t.primary) ??
        sorted.find((t) => t.typeName === 'NORMAL') ??
        sorted[0] ??
        null;
      setSelectedTypeId(primary?.id ?? null);
    } catch (error) {
      notification.error({
        message: 'Failed to load plan',
        description: String(error),
      });
      setPlan(null);
    } finally {
      setLoadingPlan(false);
    }
  }, []);

  useEffect(() => {
    if (selectedPlanId) {
      loadPlan(selectedPlanId);
    } else {
      setPlan(null);
      setSelectedTypeId(null);
      setSelectedVersionId(null);
    }
  }, [selectedPlanId, loadPlan]);

  const forecastType: ForecastType | null = useMemo(() => {
    if (!plan || !selectedTypeId) return null;
    return plan.forecastTypes.find((t) => t.id === selectedTypeId) ?? null;
  }, [plan, selectedTypeId]);

  const versions = useMemo(() => {
    if (!forecastType) return [];
    return [...forecastType.versions].sort(
      (a, b) => b.versionNumber - a.versionNumber,
    );
  }, [forecastType]);

  useEffect(() => {
    if (!forecastType) {
      setSelectedVersionId(null);
      return;
    }
    const active = forecastType.versions.find((v) => v.status === 'ACTIVE');
    const fallback =
      active ??
      forecastType.versions.find((v) => v.status === 'DRAFT') ??
      forecastType.versions[0] ??
      null;
    setSelectedVersionId(fallback?.id ?? null);
  }, [forecastType]);

  const selectedVersion: ForecastVersion | null = useMemo(() => {
    if (!forecastType || !selectedVersionId) return null;
    return forecastType.versions.find((v) => v.id === selectedVersionId) ?? null;
  }, [forecastType, selectedVersionId]);

  const cols = useMemo(() => buildFyMonthCols(plan), [plan]);

  const loadInputs = useCallback(async () => {
    if (!plan || !forecastType || !selectedVersion) {
      setHcPlan([]);
      setSalaryBudget([]);
      setRevenuePlan([]);
      setOverheadBudget([]);
      setLineItems([]);
      setCustomers([]);
      return;
    }
    setLoadingData(true);
    try {
      const [hc, salary, revenue, overhead, lines, custs] = await Promise.all([
        fetchHcPlan(plan.id, forecastType.id, selectedVersion.id),
        fetchSalaryBudget(plan.id, forecastType.id, selectedVersion.id),
        fetchRevenuePlan(plan.id, forecastType.id, selectedVersion.id),
        fetchOverheadBudget(plan.id, forecastType.id, selectedVersion.id),
        fetchOverheadLineItems(),
        fetchCustomers(false),
      ]);
      setHcPlan(hc);
      setSalaryBudget(salary);
      setRevenuePlan(revenue);
      setOverheadBudget(overhead);
      setLineItems(lines);
      setCustomers(custs.filter((c) => !c.internal));
    } catch (error) {
      notification.error({
        message: 'Failed to load plan inputs',
        description: String(error),
      });
    } finally {
      setLoadingData(false);
    }
  }, [plan, forecastType, selectedVersion]);

  useEffect(() => {
    loadInputs();
  }, [loadInputs]);

  const handleExport = useCallback(async () => {
    if (!plan || !forecastType || !selectedVersion) return;
    setExporting(true);
    try {
      await exportAllPlanInputs(plan.id, forecastType.id, selectedVersion.id);
      notification.success({ message: 'Plan inputs exported' });
    } catch (error) {
      notification.error({
        message: 'Failed to export plan inputs',
        description: String(error),
      });
    } finally {
      setExporting(false);
    }
  }, [plan, forecastType, selectedVersion]);

  const typeOptions = useMemo(() => {
    if (!plan) return [];
    return [...plan.forecastTypes]
      .sort((a, b) => {
        const ai = FORECAST_TYPE_ORDER.indexOf(a.typeName);
        const bi = FORECAST_TYPE_ORDER.indexOf(b.typeName);
        return (ai < 0 ? 99 : ai) - (bi < 0 ? 99 : bi);
      })
      .map((t) => ({
        value: t.id,
        label: TYPE_LABELS[t.typeName] ?? t.typeName,
      }));
  }, [plan]);

  const versionOptions = useMemo(
    () =>
      versions.map((v) => ({
        value: v.id,
        label: `v${v.versionNumber} — ${v.status}`,
      })),
    [versions],
  );

  const onRowStyle = (record: MetricRow): { style?: CSSProperties } => {
    if (record.kind === 'section') return { style: sectionHeaderStyle };
    if (record.kind === 'total') return { style: totalRowStyle };
    return {};
  };

  return (
    <div style={{ padding: 24 }}>
      <Space direction="vertical" size="large" style={{ width: '100%' }}>
        <div
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'flex-start',
            gap: 16,
            flexWrap: 'wrap',
          }}
        >
          <div>
            <Title level={3} style={{ fontFamily: HEADING_FONT, margin: 0 }}>
              Plan Summary
            </Title>
            <Text type="secondary">
              Read-only view of all plan inputs for a selected forecast version
            </Text>
          </div>
          <Button
            type="primary"
            icon={<DownloadOutlined />}
            loading={exporting}
            disabled={!plan || !forecastType || !selectedVersion}
            onClick={handleExport}
          >
            Export to Excel
          </Button>
        </div>

        <Card size="small">
          <Space wrap size="middle">
            <div>
              <Text type="secondary" style={{ display: 'block', marginBottom: 4 }}>
                Financial Year
              </Text>
              <Select
                style={{ minWidth: 160 }}
                placeholder="Select FY"
                value={selectedPlanId}
                onChange={setSelectedPlanId}
                options={plans.map((p) => ({
                  label: p.fiscalYear,
                  value: p.id,
                }))}
              />
            </div>
            <div>
              <Text type="secondary" style={{ display: 'block', marginBottom: 4 }}>
                Forecast Type
              </Text>
              <Select
                style={{ minWidth: 160 }}
                placeholder="Forecast type"
                value={selectedTypeId}
                onChange={setSelectedTypeId}
                options={typeOptions}
                disabled={!plan}
              />
            </div>
            <div>
              <Text type="secondary" style={{ display: 'block', marginBottom: 4 }}>
                Version
              </Text>
              <Space>
                <Select
                  style={{ minWidth: 200 }}
                  placeholder="Version"
                  value={selectedVersionId}
                  onChange={setSelectedVersionId}
                  options={versionOptions}
                  disabled={!forecastType}
                />
                {selectedVersion && (
                  <Tag color={STATUS_COLOR[selectedVersion.status]}>
                    {selectedVersion.status}
                  </Tag>
                )}
              </Space>
            </div>
          </Space>
        </Card>

        {selectedVersion?.status === 'DRAFT' && (
          <Alert
            type="warning"
            showIcon
            message="This is a draft version — figures may change before publishing."
          />
        )}
        {selectedVersion?.status === 'SUPERSEDED' && (
          <Alert
            type="info"
            showIcon
            message="This version has been superseded by a newer version."
          />
        )}

        {(loadingPlan || loadingData) && <Skeleton active paragraph={{ rows: 8 }} />}

        {!loadingPlan && !loadingData && plan && selectedVersion && (
          <Tabs
            items={[
              {
                key: 'hc',
                label: 'HC Plan',
                children: (
                  <HcPlanSummaryTable
                    cols={cols}
                    data={hcPlan}
                    onRow={onRowStyle}
                  />
                ),
              },
              {
                key: 'salary',
                label: 'Salary Budget',
                children: (
                  <SalaryBudgetSummaryTable
                    cols={cols}
                    data={salaryBudget}
                    onRow={onRowStyle}
                  />
                ),
              },
              {
                key: 'revenue',
                label: 'Client Revenue Plan',
                children: (
                  <RevenuePlanSummaryTable
                    cols={cols}
                    data={revenuePlan}
                    customers={customers}
                    onRow={onRowStyle}
                  />
                ),
              },
              {
                key: 'overhead',
                label: 'Overhead Budget',
                children: (
                  <OverheadBudgetSummaryTable
                    cols={cols}
                    data={overheadBudget}
                    lineItems={lineItems}
                    onRow={onRowStyle}
                  />
                ),
              },
            ]}
          />
        )}

        {!loadingPlan && plans.length === 0 && (
          <Card>
            <Text type="secondary">No financial year plans available.</Text>
          </Card>
        )}
      </Space>
    </div>
  );
}

function monthColumns(
  cols: FyMonthCol[],
  labelTitle: string,
  currency = false,
): ColumnsType<MetricRow> {
  return [
    {
      title: labelTitle,
      dataIndex: 'label',
      key: 'label',
      fixed: 'left',
      width: 220,
      onCell: (record) =>
        record.kind === 'section' || record.kind === 'total'
          ? { style: { fontWeight: 600 } }
          : {},
    },
    ...cols.map((col) => ({
      title: currency ? `${col.label} (Rs L)` : col.label,
      key: col.key,
      width: 100,
      align: 'right' as const,
      render: (_: unknown, record: MetricRow) => record.values[col.key] ?? '—',
    })),
    {
      title: currency ? 'FY Total (Rs L)' : 'FY Total',
      key: 'fyTotal',
      width: 110,
      align: 'right' as const,
      fixed: 'right' as const,
      render: (_: unknown, record: MetricRow) => record.values.fyTotal ?? '—',
    },
  ];
}

function HcPlanSummaryTable({
  cols,
  data,
  onRow,
}: {
  cols: FyMonthCol[];
  data: HcPlanMonth[];
  onRow: (record: MetricRow) => { style?: CSSProperties };
}) {
  const monthMap = useMemo(() => {
    const map = new Map<string, HcPlanMonth>();
    data.forEach((m) => map.set(`${m.planYear}-${m.planMonth}`, m));
    return map;
  }, [data]);

  const dataSource = useMemo(() => {
    const get = (col: FyMonthCol, field: keyof HcPlanMonth) => {
      const m = monthMap.get(col.key);
      return m ? num(m[field]) : 0;
    };
    const fieldRows: Array<{
      key: string;
      label: string;
      field: keyof HcPlanMonth | null;
      kind: RowKind;
      computed?: 'totalHc' | 'ratio';
    }> = [
      { key: 'hires', label: 'Planned Hires', field: 'plannedHires', kind: 'data' },
      { key: 'exits', label: 'Planned Exits', field: 'plannedExits', kind: 'data' },
      {
        key: 'billable',
        label: 'Planned Billable HC',
        field: 'plannedBillableHc',
        kind: 'data',
      },
      {
        key: 'bench',
        label: 'Planned Bench HC',
        field: 'plannedBenchHc',
        kind: 'data',
      },
      {
        key: 'support',
        label: 'Planned Support HC',
        field: 'plannedSupportHc',
        kind: 'data',
      },
      {
        key: 'leadership',
        label: 'Planned Leadership HC',
        field: 'plannedLeadershipHc',
        kind: 'data',
      },
      {
        key: 'management',
        label: 'Planned Management HC',
        field: 'plannedManagementHc',
        kind: 'data',
      },
      {
        key: 'totalHc',
        label: 'Total HC',
        field: null,
        kind: 'total',
        computed: 'totalHc',
      },
      {
        key: 'ratio',
        label: 'Planned Billable Ratio %',
        field: null,
        kind: 'total',
        computed: 'ratio',
      },
    ];

    return fieldRows.map((row) => {
      const values: Record<string, string | number> = {};
      let sum = 0;
      let ratioSum = 0;
      cols.forEach((col) => {
        if (row.computed === 'totalHc') {
          const total =
            get(col, 'plannedBillableHc') +
            get(col, 'plannedBenchHc') +
            get(col, 'plannedSupportHc') +
            get(col, 'plannedLeadershipHc') +
            get(col, 'plannedManagementHc');
          values[col.key] = total;
          sum += total;
        } else if (row.computed === 'ratio') {
          const billable = get(col, 'plannedBillableHc');
          const total =
            billable +
            get(col, 'plannedBenchHc') +
            get(col, 'plannedSupportHc') +
            get(col, 'plannedLeadershipHc') +
            get(col, 'plannedManagementHc');
          const ratio = billableRatio(billable, total);
          values[col.key] = `${ratio.toFixed(1)}%`;
          ratioSum += ratio;
        } else if (row.field) {
          const v = get(col, row.field);
          values[col.key] = v;
          sum += v;
        }
      });
      if (row.computed === 'ratio') {
        values.fyTotal =
          cols.length > 0
            ? `${(ratioSum / cols.length).toFixed(1)}%`
            : '0.0%';
      } else {
        values.fyTotal = sum;
      }
      return {
        key: row.key,
        label: row.label,
        kind: row.kind,
        values,
      } satisfies MetricRow;
    });
  }, [cols, monthMap]);

  return (
    <Table<MetricRow>
      size="small"
      pagination={false}
      scroll={{ x: true }}
      columns={monthColumns(cols, 'Metric')}
      dataSource={dataSource}
      onRow={onRow}
    />
  );
}

function SalaryBudgetSummaryTable({
  cols,
  data,
  onRow,
}: {
  cols: FyMonthCol[];
  data: SalaryBudgetMonth[];
  onRow: (record: MetricRow) => { style?: CSSProperties };
}) {
  const monthMap = useMemo(() => {
    const map = new Map<string, SalaryBudgetMonth>();
    data.forEach((m) => map.set(`${m.planYear}-${m.planMonth}`, m));
    return map;
  }, [data]);

  const dataSource = useMemo(() => {
    const get = (col: FyMonthCol, field: keyof SalaryBudgetMonth) => {
      const m = monthMap.get(col.key);
      return m ? num(m[field]) : 0;
    };
    const monthTotal = (col: FyMonthCol) =>
      get(col, 'billableSalaries') +
      get(col, 'benchSalaries') +
      get(col, 'supportSalaries') +
      get(col, 'cofoundersSalaries') +
      get(col, 'seniorMgmtSalaries');

    const defs: Array<{
      key: string;
      label: string;
      kind: RowKind;
      field?: keyof SalaryBudgetMonth;
      computed?: 'total' | 'contrib' | 'payroll';
    }> = [
      {
        key: 'billable',
        label: 'Billable Staff Salaries',
        field: 'billableSalaries',
        kind: 'data',
      },
      {
        key: 'bench',
        label: 'Bench Staff Salaries',
        field: 'benchSalaries',
        kind: 'data',
      },
      {
        key: 'support',
        label: 'Support Salaries',
        field: 'supportSalaries',
        kind: 'data',
      },
      {
        key: 'cofounders',
        label: 'Co-Founders Salaries',
        field: 'cofoundersSalaries',
        kind: 'data',
      },
      {
        key: 'senior',
        label: 'Senior Management Salaries',
        field: 'seniorMgmtSalaries',
        kind: 'data',
      },
      { key: 'total', label: 'Total Salary', kind: 'total', computed: 'total' },
      {
        key: 'contrib',
        label: 'Estimated Employer Contributions',
        kind: 'total',
        computed: 'contrib',
      },
      {
        key: 'payroll',
        label: 'Total Payroll Cost',
        kind: 'total',
        computed: 'payroll',
      },
    ];

    return defs.map((row) => {
      const values: Record<string, string | number> = {};
      let fy = 0;
      cols.forEach((col) => {
        let v = 0;
        if (row.field) {
          v = get(col, row.field);
        } else if (row.computed === 'total') {
          v = monthTotal(col);
        } else if (row.computed === 'contrib') {
          v = monthTotal(col) * 0.13;
        } else if (row.computed === 'payroll') {
          const total = monthTotal(col);
          v = total + total * 0.13;
        }
        values[col.key] = formatCurrency(v);
        fy += v;
      });
      values.fyTotal = formatCurrency(fy);
      return {
        key: row.key,
        label: row.label,
        kind: row.kind,
        values,
      } satisfies MetricRow;
    });
  }, [cols, monthMap]);

  return (
    <Table<MetricRow>
      size="small"
      pagination={false}
      scroll={{ x: true }}
      columns={monthColumns(cols, 'Category', true)}
      dataSource={dataSource}
      onRow={onRow}
    />
  );
}

function RevenuePlanSummaryTable({
  cols,
  data,
  customers,
  onRow,
}: {
  cols: FyMonthCol[];
  data: ClientRevenuePlanEntry[];
  customers: CustomerSummary[];
  onRow: (record: MetricRow) => { style?: CSSProperties };
}) {
  const get = useCallback(
    (
      customerId: string,
      col: FyMonthCol,
      field: 'plannedTmRevenue' | 'plannedFixedBidRevenue',
    ) => {
      const entry = data.find(
        (e) =>
          e.customerId === customerId &&
          e.planMonth === col.planMonth &&
          e.planYear === col.planYear,
      );
      return entry ? num(entry[field]) : 0;
    },
    [data],
  );

  const dataSource = useMemo(() => {
    const rows: MetricRow[] = customers.map((cust) => {
      const values: Record<string, string | number> = {};
      const tmValues: Record<string, string | number> = {};
      const fbValues: Record<string, string | number> = {};
      let fyTotal = 0;
      let fyTm = 0;
      let fyFb = 0;
      cols.forEach((col) => {
        const tm = get(cust.id, col, 'plannedTmRevenue');
        const fb = get(cust.id, col, 'plannedFixedBidRevenue');
        values[col.key] = formatCurrency(tm + fb);
        tmValues[col.key] = formatCurrency(tm);
        fbValues[col.key] = formatCurrency(fb);
        fyTotal += tm + fb;
        fyTm += tm;
        fyFb += fb;
      });
      values.fyTotal = formatCurrency(fyTotal);
      tmValues.fyTotal = formatCurrency(fyTm);
      fbValues.fyTotal = formatCurrency(fyFb);
      return {
        key: cust.id,
        label: cust.customerName,
        kind: 'data' as const,
        values,
        children: [
          {
            key: `${cust.id}-tm`,
            label: 'T&M',
            kind: 'data' as const,
            values: tmValues,
          },
          {
            key: `${cust.id}-fb`,
            label: 'Fixed-Bid',
            kind: 'data' as const,
            values: fbValues,
          },
        ],
      };
    });

    const totalValues: Record<string, string | number> = {};
    let fy = 0;
    cols.forEach((col) => {
      const monthSum = customers.reduce((sum, cust) => {
        return (
          sum +
          get(cust.id, col, 'plannedTmRevenue') +
          get(cust.id, col, 'plannedFixedBidRevenue')
        );
      }, 0);
      totalValues[col.key] = formatCurrency(monthSum);
      fy += monthSum;
    });
    totalValues.fyTotal = formatCurrency(fy);
    rows.push({
      key: 'total',
      label: 'Total',
      kind: 'total',
      values: totalValues,
    });
    return rows;
  }, [customers, cols, get]);

  return (
    <Table<MetricRow>
      size="small"
      pagination={false}
      scroll={{ x: true }}
      columns={monthColumns(cols, 'Client Name', true)}
      dataSource={dataSource}
      onRow={onRow}
      expandable={{ defaultExpandAllRows: false }}
    />
  );
}

function OverheadBudgetSummaryTable({
  cols,
  data,
  lineItems,
  onRow,
}: {
  cols: FyMonthCol[];
  data: OverheadBudgetEntry[];
  lineItems: OverheadLineItem[];
  onRow: (record: MetricRow) => { style?: CSSProperties };
}) {
  const get = useCallback(
    (lineCode: string, col: FyMonthCol) => {
      const entry = data.find(
        (e) =>
          e.overheadLine === lineCode &&
          e.planMonth === col.planMonth &&
          e.planYear === col.planYear,
      );
      return entry ? num(entry.amount) : 0;
    },
    [data],
  );

  const categoriesMap = useMemo(() => {
    const map = new Map<string, OverheadLineItem[]>();
    lineItems.forEach((line) => {
      const existing = map.get(line.category) ?? [];
      existing.push(line);
      map.set(line.category, existing);
    });
    map.forEach((lines) => lines.sort((a, b) => a.sortOrder - b.sortOrder));
    return map;
  }, [lineItems]);

  const orderedCategories = useMemo(() => {
    const keys = Array.from(categoriesMap.keys());
    return keys.sort((a, b) => {
      const ai = OVERHEAD_CATEGORY_ORDER.findIndex(
        (c) => c.toLowerCase() === a.toLowerCase(),
      );
      const bi = OVERHEAD_CATEGORY_ORDER.findIndex(
        (c) => c.toLowerCase() === b.toLowerCase(),
      );
      return (ai < 0 ? 999 : ai) - (bi < 0 ? 999 : bi);
    });
  }, [categoriesMap]);

  const dataSource = useMemo(() => {
    const rows: MetricRow[] = [];
    const grand: Record<string, number> = {};
    cols.forEach((col) => {
      grand[col.key] = 0;
    });
    let grandFy = 0;

    orderedCategories.forEach((category) => {
      const lines = categoriesMap.get(category) ?? [];
      rows.push({
        key: `section-${category}`,
        label: category,
        kind: 'section',
        values: Object.fromEntries([
          ...cols.map((c) => [c.key, '']),
          ['fyTotal', ''],
        ]),
      });

      const subtotal: Record<string, number> = {};
      cols.forEach((col) => {
        subtotal[col.key] = 0;
      });
      let subFy = 0;

      lines.forEach((line) => {
        const values: Record<string, string | number> = {};
        let fy = 0;
        cols.forEach((col) => {
          const v = get(line.lineCode, col);
          values[col.key] = formatCurrency(v);
          fy += v;
          subtotal[col.key] += v;
          grand[col.key] += v;
        });
        values.fyTotal = formatCurrency(fy);
        subFy += fy;
        grandFy += fy;
        rows.push({
          key: line.lineCode,
          label: line.displayName,
          kind: 'data',
          values,
        });
      });

      const subValues: Record<string, string | number> = {};
      cols.forEach((col) => {
        subValues[col.key] = formatCurrency(subtotal[col.key]);
      });
      subValues.fyTotal = formatCurrency(subFy);
      rows.push({
        key: `subtotal-${category}`,
        label: `${category} Subtotal`,
        kind: 'total',
        values: subValues,
      });
    });

    const grandValues: Record<string, string | number> = {};
    cols.forEach((col) => {
      grandValues[col.key] = formatCurrency(grand[col.key]);
    });
    grandValues.fyTotal = formatCurrency(grandFy);
    rows.push({
      key: 'grand-total',
      label: 'Grand Total',
      kind: 'total',
      values: grandValues,
    });

    return rows;
  }, [orderedCategories, categoriesMap, cols, get]);

  return (
    <Table<MetricRow>
      size="small"
      pagination={false}
      scroll={{ x: true }}
      columns={monthColumns(cols, 'Line Item', true)}
      dataSource={dataSource}
      onRow={onRow}
      rowClassName={(record) =>
        record.kind === 'section' ? 'plan-summary-section-row' : ''
      }
    />
  );
}
