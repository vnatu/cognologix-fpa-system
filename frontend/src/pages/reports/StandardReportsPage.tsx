import { useEffect, useMemo, useState } from 'react';
import {
  Card,
  Col,
  notification,
  Radio,
  Row,
  Select,
  Space,
  Spin,
  Typography,
  Button,
} from 'antd';
import { DownloadOutlined } from '@ant-design/icons';
import { HEADING_FONT } from '@/theme/antdTheme';
import {
  buildFyMonthOptions,
  buildFyQuarterOptions,
  defaultDashboardPeriod,
  quarterForMonth,
} from '@/pages/budgeting/utils';
import type { PeriodGranularity, PeriodQuery, PlanSummary } from '@/pages/budgeting/types';
import { fetchPlans } from '@/pages/budgeting/api';
import { downloadStandardReport, type StandardReportId } from './api';

const { Title, Text, Paragraph } = Typography;

interface ReportCardDef {
  id: StandardReportId;
  name: string;
  description: string;
  periodNote: string;
}

const REPORTS: ReportCardDef[] = [
  {
    id: 'pl',
    name: 'Monthly P&L',
    description:
      'Profit & Loss statement with Plan vs Actual comparison and monthly trend',
    periodNote: 'Selected period + full FY monthly trend',
  },
  {
    id: 'bu-margin',
    name: 'BU Gross Margin',
    description:
      'Revenue, payroll cost and gross margin per client BU with position breakdown',
    periodNote: 'Selected period',
  },
  {
    id: 'headcount',
    name: 'Headcount Summary',
    description: 'Headcount by category and practice unit with monthly trend',
    periodNote: 'Selected period + full FY monthly trend',
  },
  {
    id: 'cost-per-employee',
    name: 'Cost per Employee',
    description:
      'Fully loaded cost per head by category with minimum billing rate',
    periodNote: 'Selected period',
  },
  {
    id: 'rolling-forecast',
    name: 'Rolling Forecast vs Baseline',
    description:
      '12-month forecast trajectory against your published baseline plan',
    periodNote: 'Always full financial year (12 months)',
  },
  {
    id: 'expense-summary',
    name: 'Expense Summary',
    description:
      'Actual vs budgeted overhead expenses by category group and line item',
    periodNote: 'Selected period',
  },
];

export default function StandardReportsPage() {
  const [plans, setPlans] = useState<PlanSummary[]>([]);
  const [planId, setPlanId] = useState<string | null>(null);
  const [granularity, setGranularity] = useState<PeriodGranularity>('MONTHLY');
  const [selectedMonth, setSelectedMonth] = useState(4);
  const [selectedYear, setSelectedYear] = useState(2026);
  const [selectedQuarter, setSelectedQuarter] = useState(1);
  const [loadingId, setLoadingId] = useState<StandardReportId | null>(null);

  const selectedPlan = useMemo(
    () => plans.find((p) => p.id === planId) ?? null,
    [plans, planId],
  );

  const period: PeriodQuery = useMemo(() => {
    if (granularity === 'MONTHLY') {
      return { granularity, month: selectedMonth, year: selectedYear };
    }
    if (granularity === 'QUARTERLY') {
      return { granularity, quarter: selectedQuarter, year: selectedYear };
    }
    return { granularity: 'ANNUAL' };
  }, [granularity, selectedMonth, selectedYear, selectedQuarter]);

  useEffect(() => {
    fetchPlans()
      .then((list) => {
        setPlans(list);
        if (list.length > 0) {
          const first = list[0];
          setPlanId(first.id);
          const defaults = defaultDashboardPeriod(first);
          setGranularity(defaults.granularity);
          setSelectedMonth(defaults.month);
          setSelectedYear(defaults.year);
          setSelectedQuarter(defaults.quarter);
        }
      })
      .catch(() => notification.error({ message: 'Failed to load financial year plans' }));
  }, []);

  const monthOptions = buildFyMonthOptions(selectedPlan);
  const quarterOptions = buildFyQuarterOptions(selectedPlan);

  async function handleDownload(reportId: StandardReportId) {
    if (!planId) {
      notification.warning({ message: 'Select a financial year plan first' });
      return;
    }
    setLoadingId(reportId);
    try {
      await downloadStandardReport(reportId, planId, period);
      notification.success({ message: 'Report downloaded' });
    } catch {
      notification.error({ message: 'Failed to generate report' });
    } finally {
      setLoadingId(null);
    }
  }

  const periodLabel =
    granularity === 'ANNUAL'
      ? selectedPlan?.fiscalYear ?? 'Full year'
      : granularity === 'QUARTERLY'
        ? `Q${selectedQuarter} ${selectedPlan?.fiscalYear ?? ''}`
        : (monthOptions.find((o) => o.month === selectedMonth && o.year === selectedYear)
            ?.label ?? `${selectedMonth}/${selectedYear}`);

  return (
    <div style={{ padding: 24 }}>
      <Title level={3} style={{ fontFamily: HEADING_FONT, marginTop: 0 }}>
        Standard Reports
      </Title>
      <Paragraph type="secondary" style={{ marginBottom: 20 }}>
        Download branded Excel workbooks for the selected financial year and period.
      </Paragraph>

      <Space wrap size="middle" style={{ marginBottom: 24 }}>
        <div>
          <Text type="secondary" style={{ display: 'block', marginBottom: 4 }}>
            Financial Year
          </Text>
          <Select
            style={{ minWidth: 160 }}
            value={planId ?? undefined}
            options={plans.map((p) => ({ value: p.id, label: p.fiscalYear }))}
            onChange={(id) => {
              setPlanId(id);
              const plan = plans.find((p) => p.id === id) ?? null;
              const defaults = defaultDashboardPeriod(plan);
              setSelectedMonth(defaults.month);
              setSelectedYear(defaults.year);
              setSelectedQuarter(defaults.quarter);
            }}
            placeholder="Select FY"
          />
        </div>
        <div>
          <Text type="secondary" style={{ display: 'block', marginBottom: 4 }}>
            Granularity
          </Text>
          <Radio.Group
            value={granularity}
            onChange={(e) => {
              const g = e.target.value as PeriodGranularity;
              setGranularity(g);
              if (g === 'QUARTERLY') {
                setSelectedQuarter(quarterForMonth(selectedMonth));
              }
            }}
            optionType="button"
            options={[
              { value: 'MONTHLY', label: 'Monthly' },
              { value: 'QUARTERLY', label: 'Quarterly' },
              { value: 'ANNUAL', label: 'Annual' },
            ]}
          />
        </div>
        {granularity === 'MONTHLY' && (
          <div>
            <Text type="secondary" style={{ display: 'block', marginBottom: 4 }}>
              Month
            </Text>
            <Select
              style={{ minWidth: 180 }}
              value={`${selectedYear}-${selectedMonth}`}
              options={monthOptions.map((o) => ({ value: o.value, label: o.label }))}
              onChange={(v) => {
                const opt = monthOptions.find((o) => o.value === v);
                if (opt) {
                  setSelectedMonth(opt.month);
                  setSelectedYear(opt.year);
                  setSelectedQuarter(quarterForMonth(opt.month));
                }
              }}
            />
          </div>
        )}
        {granularity === 'QUARTERLY' && (
          <div>
            <Text type="secondary" style={{ display: 'block', marginBottom: 4 }}>
              Quarter
            </Text>
            <Select
              style={{ minWidth: 160 }}
              value={String(selectedQuarter)}
              options={quarterOptions.map((o) => ({ value: o.value, label: o.label }))}
              onChange={(v) => {
                const opt = quarterOptions.find((o) => o.value === v);
                if (opt) {
                  setSelectedQuarter(opt.quarter);
                  setSelectedYear(opt.year);
                }
              }}
            />
          </div>
        )}
      </Space>

      <Row gutter={[16, 16]}>
        {REPORTS.map((report) => (
          <Col xs={24} md={12} key={report.id}>
            <Spin spinning={loadingId === report.id} tip="Generating report…">
              <Card
                title={
                  <span style={{ fontFamily: HEADING_FONT, fontWeight: 700 }}>
                    {report.name}
                  </span>
                }
                extra={
                  <Button
                    type="primary"
                    icon={<DownloadOutlined />}
                    disabled={!planId || loadingId !== null}
                    onClick={() => handleDownload(report.id)}
                  >
                    Download Excel
                  </Button>
                }
              >
                <Paragraph style={{ marginBottom: 8 }}>{report.description}</Paragraph>
                <Text type="secondary">
                  Period: {report.id === 'rolling-forecast'
                    ? selectedPlan?.fiscalYear ?? 'Full FY'
                    : periodLabel}{' '}
                  · {report.periodNote}
                </Text>
              </Card>
            </Spin>
          </Col>
        ))}
      </Row>
    </div>
  );
}
