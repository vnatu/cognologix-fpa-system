import type { FyMonthCol, PeriodGranularity, PlanSummary } from './types';

export const FY_MONTH_LABELS = [
  'Apr',
  'May',
  'Jun',
  'Jul',
  'Aug',
  'Sep',
  'Oct',
  'Nov',
  'Dec',
  'Jan',
  'Feb',
  'Mar',
] as const;

export const FY_MONTH_FULL_LABELS = [
  'April',
  'May',
  'June',
  'July',
  'August',
  'September',
  'October',
  'November',
  'December',
  'January',
  'February',
  'March',
] as const;

/** Build Apr–Mar columns for an Indian FY (e.g. FY2627 → Apr 2026 … Mar 2027). */
export function buildFyMonthCols(
  plan: { fiscalYearStart: string } | null,
): FyMonthCol[] {
  if (!plan) return [];
  const start = new Date(plan.fiscalYearStart);
  const startYear = start.getFullYear();
  const months = [4, 5, 6, 7, 8, 9, 10, 11, 12, 1, 2, 3];
  return months.map((m, i) => {
    const planYear = m >= 4 ? startYear : startYear + 1;
    return {
      key: `${planYear}-${m}`,
      label: FY_MONTH_LABELS[i],
      planMonth: m,
      planYear,
    };
  });
}

export function buildFyMonthOptions(
  plan: { fiscalYearStart: string } | null,
): {
  value: string;
  label: string;
  month: number;
  year: number;
}[] {
  return buildFyMonthCols(plan).map((c, i) => ({
    value: c.key,
    label: `${FY_MONTH_FULL_LABELS[i]} ${c.planYear}`,
    month: c.planMonth,
    year: c.planYear,
  }));
}

export function buildFyQuarterOptions(plan: PlanSummary | null): {
  value: string;
  label: string;
  quarter: number;
  year: number;
}[] {
  if (!plan) return [];
  const startYear = new Date(plan.fiscalYearStart).getFullYear();
  return [
    { value: '1', label: `Q1 ${plan.fiscalYear}`, quarter: 1, year: startYear },
    { value: '2', label: `Q2 ${plan.fiscalYear}`, quarter: 2, year: startYear },
    { value: '3', label: `Q3 ${plan.fiscalYear}`, quarter: 3, year: startYear },
    {
      value: '4',
      label: `Q4 ${plan.fiscalYear}`,
      quarter: 4,
      year: startYear + 1,
    },
  ];
}

export function quarterForMonth(month: number): number {
  if (month >= 4 && month <= 6) return 1;
  if (month >= 7 && month <= 9) return 2;
  if (month >= 10 && month <= 12) return 3;
  return 4;
}

export function defaultDashboardPeriod(plan: {
  fiscalYearStart: string;
  latestActualsMonth?: number | null;
  latestActualsYear?: number | null;
} | null): {
  granularity: PeriodGranularity;
  month: number;
  year: number;
  quarter: number;
} {
  const cols = buildFyMonthCols(plan);
  const fallback = {
    month: cols[0]?.planMonth ?? 4,
    year: cols[0]?.planYear ?? new Date().getFullYear(),
  };
  const month =
    plan?.latestActualsMonth != null ? plan.latestActualsMonth : fallback.month;
  const year =
    plan?.latestActualsYear != null ? plan.latestActualsYear : fallback.year;
  return {
    granularity: 'MONTHLY',
    month,
    year,
    quarter: quarterForMonth(month),
  };
}

export function parseFiscalYearDates(fiscalYear: string): {
  start: string;
  end: string;
} | null {
  const m = /^FY(\d{2})(\d{2})$/i.exec(fiscalYear.trim());
  if (!m) return null;
  const startYear = 2000 + Number(m[1]);
  const endYear = 2000 + Number(m[2]);
  if (endYear !== startYear + 1) return null;
  return {
    start: `${startYear}-04-01`,
    end: `${endYear}-03-31`,
  };
}

export function currentFyMonth(plan: PlanSummary | null): {
  month: number;
  year: number;
} {
  const now = new Date();
  const month = now.getMonth() + 1;
  const year = now.getFullYear();
  if (!plan) return { month, year };
  const cols = buildFyMonthCols(plan);
  const hit = cols.find((c) => c.planMonth === month && c.planYear === year);
  if (hit) return { month: hit.planMonth, year: hit.planYear };
  return { month: cols[0]?.planMonth ?? 4, year: cols[0]?.planYear ?? year };
}

export function pct(
  variance: number | null | undefined,
  plan: number | null | undefined,
): number | null {
  if (variance == null || plan == null || plan === 0) return null;
  return (variance / plan) * 100;
}

export function num(v: unknown): number {
  if (v == null) return 0;
  const n = typeof v === 'number' ? v : Number(v);
  return Number.isFinite(n) ? n : 0;
}

export function billableRatio(billable: number, total: number): number {
  if (total <= 0) return 0;
  return (billable / total) * 100;
}

export const STATUS_COLOR: Record<string, string> = {
  DRAFT: 'default',
  ACTIVE: 'success',
  SUPERSEDED: 'default',
};

export const TYPE_LABELS: Record<string, string> = {
  NORMAL: 'Normal',
  AGGRESSIVE: 'Aggressive',
  CONSERVATIVE: 'Conservative',
};
