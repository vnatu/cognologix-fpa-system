import { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
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
  Tag,
  Typography,
  theme,
  notification,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { HEADING_FONT } from '@/theme/antdTheme';
import { formatCurrency } from '@/utils/formatDate';
import {
  fetchDashboardPeriods,
  fetchDashboardSummary,
  fetchDashboardTrend,
} from '../api';
import { MONTH_NAMES, PERIOD_STATUS_LABELS } from '../constants';
import type {
  DashboardPeriod,
  DashboardSummary,
  DashboardTrendMetric,
  DashboardTrendPoint,
  PeriodStatus,
  ReconciliationStatus,
} from '../types';
import { formatPeriodLabel } from '../utils';

const { Title, Text } = Typography;

const TREND_METRIC_OPTIONS: { value: DashboardTrendMetric; label: string }[] = [
  { value: 'TOTAL_HC', label: 'Total HC' },
  { value: 'BILLABLE_HC', label: 'Billable HC' },
  { value: 'BENCH_HC', label: 'Bench HC' },
  { value: 'BILLABLE_RATIO_PCT', label: 'Billable Ratio %' },
  { value: 'TOTAL_GROSS_PAY', label: 'Total Gross Pay' },
  { value: 'BILLABLE_GROSS_PAY', label: 'Billable Gross Pay' },
];

type VersionSelection = {
  periodId: string;
  periodVersionId: string;
  periodMonth: number;
  periodYear: number;
  versionNumber: number;
  status: PeriodStatus;
};

function pickDefaultVersion(periods: DashboardPeriod[]): VersionSelection | null {
  const options: VersionSelection[] = [];
  for (const p of periods) {
    for (const v of p.versions) {
      if (v.status === 'SUPERSEDED') continue;
      options.push({
        periodId: p.id,
        periodVersionId: v.id,
        periodMonth: p.periodMonth,
        periodYear: p.periodYear,
        versionNumber: v.versionNumber,
        status: v.status,
      });
    }
  }
  options.sort((a, b) => {
    if (a.periodYear !== b.periodYear) return b.periodYear - a.periodYear;
    if (a.periodMonth !== b.periodMonth) return b.periodMonth - a.periodMonth;
    return b.versionNumber - a.versionNumber;
  });

  const finalised = options.filter((o) => o.status === 'FINALISED');
  if (finalised.length > 0) return finalised[0];

  const masterBuilt = options.filter((o) => o.status === 'MASTER_BUILT');
  if (masterBuilt.length > 0) return masterBuilt[0];

  return options[0] ?? null;
}

function shortPeriodLabel(month: number, year: number): string {
  return `${MONTH_NAMES[month - 1].slice(0, 3)} ${year}`;
}

function billableRatioColor(
  pct: number,
  success: string,
  warning: string,
  error: string,
): string {
  if (pct >= 72) return success;
  if (pct >= 60) return warning;
  return error;
}

export default function PeopleDashboardPage() {
  const { token } = theme.useToken();
  const navigate = useNavigate();

  const [periodsLoading, setPeriodsLoading] = useState(true);
  const [summaryLoading, setSummaryLoading] = useState(false);
  const [periods, setPeriods] = useState<DashboardPeriod[]>([]);
  const [selected, setSelected] = useState<VersionSelection | null>(null);
  const [summary, setSummary] = useState<DashboardSummary | null>(null);

  const [trendMetric, setTrendMetric] =
    useState<DashboardTrendMetric>('TOTAL_HC');
  const [trendFilter, setTrendFilter] = useState<string>('ALL');
  const [trendLoading, setTrendLoading] = useState(false);
  const [trendPoints, setTrendPoints] = useState<DashboardTrendPoint[]>([]);

  useEffect(() => {
    (async () => {
      setPeriodsLoading(true);
      try {
        const loaded = await fetchDashboardPeriods();
        setPeriods(loaded);
        setSelected(pickDefaultVersion(loaded));
      } catch {
        notification.error({ message: 'Failed to load dashboard periods' });
      } finally {
        setPeriodsLoading(false);
      }
    })();
  }, []);

  useEffect(() => {
    if (!selected) {
      setSummary(null);
      return;
    }
    (async () => {
      setSummaryLoading(true);
      try {
        setSummary(await fetchDashboardSummary(selected.periodVersionId));
      } catch {
        notification.error({ message: 'Failed to load dashboard summary' });
        setSummary(null);
      } finally {
        setSummaryLoading(false);
      }
    })();
  }, [selected]);

  useEffect(() => {
    (async () => {
      setTrendLoading(true);
      try {
        const params: {
          metric: DashboardTrendMetric;
          practiceUnit?: string;
          businessUnit?: string;
        } = { metric: trendMetric };
        if (trendFilter.startsWith('PU:')) {
          params.practiceUnit = trendFilter.slice(3);
        } else if (trendFilter.startsWith('BU:')) {
          params.businessUnit = trendFilter.slice(3);
        }
        setTrendPoints(await fetchDashboardTrend(params));
      } catch {
        notification.error({ message: 'Failed to load trend data' });
        setTrendPoints([]);
      } finally {
        setTrendLoading(false);
      }
    })();
  }, [trendMetric, trendFilter]);

  const versionSelectOptions = useMemo(
    () =>
      periods.map((p) => ({
        label: formatPeriodLabel(p.periodMonth, p.periodYear),
        options: p.versions
          .filter((v) => v.status !== 'SUPERSEDED')
          .map((v) => ({
            value: v.id,
            label: `v${v.versionNumber} — ${PERIOD_STATUS_LABELS[v.status] ?? v.status}`,
          })),
      })),
    [periods],
  );

  const trendFilterOptions = useMemo(() => {
    const options: { value: string; label: string }[] = [
      { value: 'ALL', label: 'All' },
    ];
    if (!summary) return options;
    for (const pu of summary.puBreakdown) {
      options.push({
        value: `PU:${pu.practiceUnit}`,
        label: `PU: ${pu.practiceUnit}`,
      });
    }
    for (const c of summary.clientBreakdown) {
      options.push({
        value: `BU:${c.businessUnit}`,
        label: `Client: ${c.businessUnit}`,
      });
    }
    for (const bu of summary.internalBuBreakdown) {
      options.push({
        value: `BU:${bu.businessUnit}`,
        label: `Internal: ${bu.businessUnit}`,
      });
    }
    return options;
  }, [summary]);

  const chartData = useMemo(
    () =>
      trendPoints.map((p) => ({
        ...p,
        label: shortPeriodLabel(p.periodMonth, p.periodYear),
      })),
    [trendPoints],
  );

  const provisional =
    selected != null &&
    selected.status !== 'FINALISED' &&
    selected.status === 'MASTER_BUILT';

  const goToMaster = (opts: {
    reconciliationStatus?: ReconciliationStatus;
    hasWarnings?: boolean;
  }) => {
    const params = new URLSearchParams();
    if (opts.reconciliationStatus) {
      params.set('reconciliationStatus', opts.reconciliationStatus);
    }
    if (opts.hasWarnings) {
      params.set('hasWarnings', 'true');
    }
    navigate(`/people-payroll/master?${params.toString()}`);
  };

  const onVersionChange = (versionId: string) => {
    for (const p of periods) {
      const v = p.versions.find((x) => x.id === versionId);
      if (v) {
        setSelected({
          periodId: p.id,
          periodVersionId: v.id,
          periodMonth: p.periodMonth,
          periodYear: p.periodYear,
          versionNumber: v.versionNumber,
          status: v.status,
        });
        return;
      }
    }
  };

  if (periodsLoading) {
    return (
      <div style={{ padding: 24 }}>
        <Skeleton active paragraph={{ rows: 8 }} />
      </div>
    );
  }

  if (!selected) {
    return (
      <div style={{ padding: 24 }}>
        <Empty description="No period versions available yet. Build a Master from Period Management to populate the dashboard." />
      </div>
    );
  }

  const hc = summary?.headcount;
  const salary = summary?.salaryMetrics;
  const recon = summary?.reconciliationSummary;
  const dq = summary?.dataQualitySummary;

  const puColumns: ColumnsType<DashboardSummary['puBreakdown'][number]> = [
    { title: 'Practice Unit', dataIndex: 'practiceUnit', key: 'practiceUnit' },
    { title: 'Total HC', dataIndex: 'totalHc', key: 'totalHc', align: 'right' },
    {
      title: 'Billable HC',
      dataIndex: 'billableHc',
      key: 'billableHc',
      align: 'right',
    },
    { title: 'Bench HC', dataIndex: 'benchHc', key: 'benchHc', align: 'right' },
    {
      title: 'Billable %',
      dataIndex: 'billablePct',
      key: 'billablePct',
      align: 'right',
      render: (v: number) => `${Number(v).toFixed(2)}%`,
    },
    {
      title: 'Bench %',
      dataIndex: 'benchPct',
      key: 'benchPct',
      align: 'right',
      render: (v: number) => `${Number(v).toFixed(2)}%`,
    },
    {
      title: 'Total Gross Pay',
      dataIndex: 'totalGrossPay',
      key: 'totalGrossPay',
      align: 'right',
      render: formatCurrency,
    },
    {
      title: 'Billable Gross Pay',
      dataIndex: 'billableGrossPay',
      key: 'billableGrossPay',
      align: 'right',
      render: formatCurrency,
    },
    {
      title: 'Bench Gross Pay',
      dataIndex: 'benchGrossPay',
      key: 'benchGrossPay',
      align: 'right',
      render: formatCurrency,
    },
  ];

  const clientColumns: ColumnsType<
    DashboardSummary['clientBreakdown'][number]
  > = [
    {
      title: 'Client',
      dataIndex: 'businessUnit',
      key: 'businessUnit',
      render: (name: string, row) =>
        row.customerCode ? (
          <Link to="/customer-management/customers">{name}</Link>
        ) : (
          name
        ),
    },
    { title: 'Total HC', dataIndex: 'totalHc', key: 'totalHc', align: 'right' },
    {
      title: 'Billable HC',
      dataIndex: 'billableHc',
      key: 'billableHc',
      align: 'right',
    },
    {
      title: 'Non-Billable HC',
      dataIndex: 'nonBillableHc',
      key: 'nonBillableHc',
      align: 'right',
    },
    {
      title: 'Billability %',
      dataIndex: 'billabilityPct',
      key: 'billabilityPct',
      align: 'right',
      render: (v: number) => `${Number(v).toFixed(2)}%`,
    },
    {
      title: 'Total Gross Pay',
      dataIndex: 'totalGrossPay',
      key: 'totalGrossPay',
      align: 'right',
      render: formatCurrency,
    },
  ];

  const internalColumns: ColumnsType<
    DashboardSummary['internalBuBreakdown'][number]
  > = [
    { title: 'Business Unit', dataIndex: 'businessUnit', key: 'businessUnit' },
    { title: 'Total HC', dataIndex: 'totalHc', key: 'totalHc', align: 'right' },
    {
      title: 'Billable HC',
      dataIndex: 'billableHc',
      key: 'billableHc',
      align: 'right',
    },
    {
      title: 'Non-Billable HC',
      dataIndex: 'nonBillableHc',
      key: 'nonBillableHc',
      align: 'right',
    },
    {
      title: 'Total Gross Pay',
      dataIndex: 'totalGrossPay',
      key: 'totalGrossPay',
      align: 'right',
      render: formatCurrency,
    },
    {
      title: 'Gross Pay % of Total',
      dataIndex: 'grossPayPct',
      key: 'grossPayPct',
      align: 'right',
      render: (v: number) => `${Number(v).toFixed(2)}%`,
    },
  ];

  const salaryCards: {
    title: string;
    gross: number | undefined;
    avg: number | undefined;
  }[] = [
    {
      title: 'Billable',
      gross: salary?.billableGrossPay,
      avg: salary?.avgPerHeadBillable,
    },
    {
      title: 'Bench',
      gross: salary?.benchGrossPay,
      avg: salary?.avgPerHeadBench,
    },
    {
      title: 'Support',
      gross: salary?.supportGrossPay,
      avg: salary?.avgPerHeadSupport,
    },
    {
      title: 'Leadership',
      gross: salary?.leadershipGrossPay,
      avg: salary?.avgPerHeadLeadership,
    },
    {
      title: 'Management',
      gross: salary?.managementGrossPay,
      avg: salary?.avgPerHeadManagement,
    },
  ];

  return (
    <div style={{ padding: 24 }}>
      <Row justify="space-between" align="middle" style={{ marginBottom: 16 }}>
        <Col>
          <Title
            level={3}
            style={{ fontFamily: HEADING_FONT, margin: 0, fontWeight: 700 }}
          >
            People Analytics Dashboard
          </Title>
          {selected && (
            <Text type="secondary">
              {formatPeriodLabel(selected.periodMonth, selected.periodYear)} — v
              {selected.versionNumber}
            </Text>
          )}
        </Col>
        <Col>
          <Select
            style={{ minWidth: 280 }}
            value={selected.periodVersionId}
            onChange={onVersionChange}
            options={versionSelectOptions}
            placeholder="Select period / version"
          />
        </Col>
      </Row>

      {provisional && (
        <Alert
          type="warning"
          showIcon
          style={{ marginBottom: 16 }}
          message="This period is not yet finalised — figures are provisional."
        />
      )}

      {summaryLoading || !summary ? (
        <Skeleton active paragraph={{ rows: 12 }} />
      ) : (
        <>
          <Title
            level={5}
            style={{ fontFamily: HEADING_FONT, marginTop: 8 }}
          >
            Headcount Summary
          </Title>
          <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
            {[
              { title: 'Total HC', value: hc?.total },
              { title: 'Billable HC', value: hc?.billable },
              { title: 'Bench HC', value: hc?.bench },
              { title: 'Support HC', value: hc?.support },
              { title: 'Leadership HC', value: hc?.leadership },
              { title: 'Management HC', value: hc?.management },
            ].map((item) => (
              <Col xs={24} sm={12} md={8} lg={4} key={item.title}>
                <Card size="small">
                  <Statistic title={item.title} value={item.value ?? 0} />
                </Card>
              </Col>
            ))}
            <Col xs={24} sm={12} md={8} lg={4}>
              <Card size="small">
                <Statistic
                  title="Billable Ratio %"
                  value={hc?.billableRatioPct ?? 0}
                  precision={2}
                  suffix="%"
                  valueStyle={{
                    color: billableRatioColor(
                      hc?.billableRatioPct ?? 0,
                      token.colorSuccess,
                      token.colorWarning,
                      token.colorError,
                    ),
                  }}
                />
              </Card>
            </Col>
          </Row>

          <Title level={5} style={{ fontFamily: HEADING_FONT }}>
            Salary Metrics
          </Title>
          <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
            {salaryCards.map((card) => (
              <Col xs={24} sm={12} md={8} lg={4} xl={4} key={card.title}>
                <Card size="small" title={card.title}>
                  <Statistic
                    title="Gross Pay"
                    value={card.gross ?? 0}
                    formatter={(v) => formatCurrency(Number(v))}
                  />
                  <div style={{ marginTop: 12 }}>
                    <Text type="secondary">Avg per Head</Text>
                    <div style={{ fontSize: 16, fontWeight: 600 }}>
                      {formatCurrency(card.avg)}
                    </div>
                  </div>
                </Card>
              </Col>
            ))}
          </Row>

          <Title level={5} style={{ fontFamily: HEADING_FONT }}>
            PU Breakdown
          </Title>
          <Table
            size="small"
            pagination={false}
            rowKey="practiceUnit"
            columns={puColumns}
            dataSource={summary.puBreakdown}
            style={{ marginBottom: 24 }}
            scroll={{ x: true }}
          />

          <Title level={5} style={{ fontFamily: HEADING_FONT }}>
            Client Breakdown
          </Title>
          <Table
            size="small"
            pagination={false}
            rowKey="businessUnit"
            columns={clientColumns}
            dataSource={summary.clientBreakdown}
            style={{ marginBottom: 24 }}
            scroll={{ x: true }}
          />

          <Title level={5} style={{ fontFamily: HEADING_FONT }}>
            Internal BU Breakdown
          </Title>
          <Table
            size="small"
            pagination={false}
            rowKey="businessUnit"
            columns={internalColumns}
            dataSource={summary.internalBuBreakdown}
            scroll={{ x: true }}
          />
          <Text
            type="secondary"
            style={{ display: 'block', marginBottom: 24, marginTop: 8 }}
          >
            Gross Pay % shows each internal BU&apos;s salary cost as a proportion
            of total company salary.
          </Text>

          <Title level={5} style={{ fontFamily: HEADING_FONT }}>
            Reconciliation Summary
          </Title>
          <Space wrap style={{ marginBottom: 24 }}>
            <Tag
              color="gold"
              style={{ cursor: 'pointer', padding: '4px 10px' }}
              onClick={() =>
                goToMaster({ reconciliationStatus: 'PAYROLL_PENDING' })
              }
            >
              Payroll Pending: {recon?.payrollPending ?? 0}
            </Tag>
            <Tag
              color="default"
              style={{ cursor: 'pointer', padding: '4px 10px' }}
              onClick={() =>
                goToMaster({ reconciliationStatus: 'AUTO_MATCHED_EXITED' })
              }
            >
              Auto-Matched Exited: {recon?.autoMatchedExited ?? 0}
            </Tag>
            <Tag
              color="error"
              style={{ cursor: 'pointer', padding: '4px 10px' }}
              onClick={() => goToMaster({ reconciliationStatus: 'UNMATCHED' })}
            >
              Unmatched: {recon?.unmatched ?? 0}
            </Tag>
            <Tag
              color="blue"
              style={{ cursor: 'pointer', padding: '4px 10px' }}
              onClick={() =>
                goToMaster({ reconciliationStatus: 'MANUALLY_MAPPED' })
              }
            >
              Manually Mapped: {recon?.manuallyMapped ?? 0}
            </Tag>
          </Space>

          {dq && dq.totalWarnings > 0 && (
            <Alert
              type="warning"
              showIcon
              style={{ marginBottom: 24 }}
              message={`${dq.totalWarnings} data quality warning${dq.totalWarnings === 1 ? '' : 's'}`}
              description={
                <span>
                  Missing project code: {dq.missingProjectCode} · Project code
                  not found: {dq.projectCodeNotFound} · Billing client
                  unresolved: {dq.billingClientUnresolved}.{' '}
                  <Link
                    to="/people-payroll/master?hasWarnings=true"
                    onClick={(e) => {
                      e.preventDefault();
                      goToMaster({ hasWarnings: true });
                    }}
                  >
                    View in Master Data
                  </Link>
                </span>
              }
            />
          )}

          <Title level={5} style={{ fontFamily: HEADING_FONT }}>
            Trend View
          </Title>
          <Space wrap style={{ marginBottom: 16 }}>
            <Select
              style={{ minWidth: 200 }}
              value={trendMetric}
              onChange={setTrendMetric}
              options={TREND_METRIC_OPTIONS}
            />
            <Select
              style={{ minWidth: 220 }}
              value={trendFilter}
              onChange={setTrendFilter}
              options={trendFilterOptions}
            />
          </Space>
          {trendLoading ? (
            <Skeleton active paragraph={{ rows: 6 }} />
          ) : chartData.length < 2 ? (
            <Empty description="Trend data will appear once at least 2 periods are finalised." />
          ) : (
            <div style={{ width: '100%', height: 320 }}>
              <ResponsiveContainer>
                <LineChart data={chartData}>
                  <CartesianGrid strokeDasharray="3 3" />
                  <XAxis dataKey="label" />
                  <YAxis />
                  <Tooltip
                    formatter={(value) => {
                      const n = Number(value ?? 0);
                      if (
                        trendMetric === 'TOTAL_GROSS_PAY' ||
                        trendMetric === 'BILLABLE_GROSS_PAY'
                      ) {
                        return formatCurrency(n);
                      }
                      if (trendMetric === 'BILLABLE_RATIO_PCT') {
                        return `${n.toFixed(2)}%`;
                      }
                      return String(n);
                    }}
                  />
                  <Line
                    type="monotone"
                    dataKey="value"
                    stroke={token.colorPrimary}
                    strokeWidth={2}
                    dot={{ r: 4 }}
                  />
                </LineChart>
              </ResponsiveContainer>
            </div>
          )}
        </>
      )}
    </div>
  );
}
