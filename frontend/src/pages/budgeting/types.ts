export type ForecastVersionStatus = 'DRAFT' | 'ACTIVE' | 'SUPERSEDED';

export interface PlanSummary {
  id: string;
  fiscalYear: string;
  fiscalYearStart: string;
  fiscalYearEnd: string;
  openingHc: number;
  createdAt: string;
  createdBy: string | null;
}

export interface ForecastVersion {
  id: string;
  versionNumber: number;
  status: ForecastVersionStatus;
  publishedAt: string | null;
  publishedBy: string | null;
  supersededAt: string | null;
  supersededBy: string | null;
  createdAt: string;
  createdBy: string | null;
}

export interface ForecastType {
  id: string;
  typeName: string;
  primary: boolean;
  versions: ForecastVersion[];
}

export interface PlanDetail extends PlanSummary {
  forecastTypes: ForecastType[];
  latestActualsMonth?: number | null;
  latestActualsYear?: number | null;
}

export interface HcPlanMonth {
  planMonth: number;
  planYear: number;
  plannedHires: number;
  plannedExits: number;
  plannedBillableHc: number;
  plannedBenchHc: number;
  plannedSupportHc: number;
  plannedLeadershipHc: number;
  plannedManagementHc: number;
}

export interface SalaryBudgetMonth {
  planMonth: number;
  planYear: number;
  billableSalaries: number;
  benchSalaries: number;
  supportSalaries: number;
  cofoundersSalaries: number;
  seniorMgmtSalaries: number;
}

export interface ClientRevenuePlanEntry {
  customerId: string;
  planMonth: number;
  planYear: number;
  plannedTmRevenue: number;
  plannedFixedBidRevenue: number;
}

export interface OverheadBudgetEntry {
  planMonth: number;
  planYear: number;
  overheadLine: string;
  amount: number;
}

export interface OverheadLineItem {
  lineCode: string;
  category: string;
  displayName: string;
  sortOrder: number;
}

export interface MoneyTriad {
  plan: number;
  actual: number | null;
  variance: number | null;
}

export interface HcFigures {
  billableHc: number;
  benchHc: number;
  supportHc: number;
  leadershipHc: number;
  managementHc: number;
  totalHc: number;
}

export interface SalaryFigures {
  billable: number;
  bench: number;
  support: number;
  cofounders: number;
  seniorMgmt: number;
  total: number;
}

export interface ClientRevenueFigures {
  customerId: string | null;
  customerCode: string;
  customerName: string;
  tmRevenue: number;
  fixedBidRevenue: number;
  totalRevenue: number;
}

export interface OverheadLineFigures {
  lineCode: string;
  amount: number;
}

export interface MonthlyFinancials {
  month: number;
  year: number;
  fromActuals: boolean;
  hc: HcFigures;
  salary: SalaryFigures;
  revenueByClient: ClientRevenueFigures[];
  totalRevenue: number;
  overhead: OverheadLineFigures[];
  totalOverhead: number;
  totalSalaryCost: number;
  statutoryBenefits: number;
  variablePay: number;
  totalCogs: number;
  grossProfit: number;
  totalOpex: number;
  ebitda: number;
  /** Billable Ratio % = (billableHc / totalHc) × 100. Delta: Actual% − Plan%. */
  billableRatioPct: number;
}

export interface TriadHc {
  plan: HcFigures;
  actual: HcFigures;
  variance: HcFigures;
}

export interface TriadSalary {
  plan: SalaryFigures;
  actual: SalaryFigures;
  variance: SalaryFigures;
}

export interface TriadClientRevenue {
  customerId: string;
  customerCode: string;
  tmRevenue: MoneyTriad;
  fixedBidRevenue: MoneyTriad;
  totalRevenue: MoneyTriad;
}

export interface TriadOverhead {
  lineCode: string;
  amount: MoneyTriad;
}

export interface MonthlyPlanVsActual {
  month: number;
  year: number;
  hasActuals: boolean;
  hc: TriadHc;
  salary: TriadSalary;
  revenueByClient: TriadClientRevenue[];
  totalRevenue: MoneyTriad;
  overhead: TriadOverhead[];
  totalOverhead: MoneyTriad;
  totalSalaryCost: MoneyTriad;
  statutoryBenefits: MoneyTriad;
  totalCogs: MoneyTriad;
  grossProfit: MoneyTriad;
  ebitda: MoneyTriad;
}

export type PeriodGranularity = 'MONTHLY' | 'QUARTERLY' | 'ANNUAL';

export interface PeriodQuery {
  granularity: PeriodGranularity;
  month?: number;
  year?: number;
  quarter?: number;
}

export interface PeriodTotals {
  label: string;
  totalRevenue: MoneyTriad;
  totalSalaryCost: MoneyTriad;
  totalOverhead: MoneyTriad;
  totalCogs: MoneyTriad;
  grossProfit: MoneyTriad;
  ebitda: MoneyTriad;
}

export interface PlanVsActualResult {
  financialYearPlanId: string;
  fiscalYear: string;
  baselineVersionId: string;
  months: MonthlyPlanVsActual[];
  q1: PeriodTotals;
  q2: PeriodTotals;
  q3: PeriodTotals;
  q4: PeriodTotals;
  fy: PeriodTotals;
  granularity: PeriodGranularity;
  periodLabel: string;
  selectedPeriod: PeriodTotals;
  actualsCoverageNote: string | null;
  monthsWithActuals: number;
  totalMonthsInFy: number;
}

export interface RollingForecastResult {
  financialYearPlanId: string;
  fiscalYear: string;
  baselineVersionId: string;
  months: MonthlyFinancials[];
  granularity: PeriodGranularity;
  periodLabel: string;
  highlightMonth: number | null;
  highlightYear: number | null;
  highlightQuarter: number | null;
}

export interface DeltaResult {
  financialYearPlanId: string;
  fiscalYear: string;
  baselineVersionId: string;
  months: MonthlyFinancials[];
  granularity: PeriodGranularity;
  periodLabel: string;
  periodTotal: MonthlyFinancials;
}

export interface CategoryCost {
  category: string;
  headcount: number;
  grossPayPerHead: number;
  employerContributionsPerHead: number;
  employerContributionsSource: 'ACTUAL' | 'ESTIMATE_13PCT';
  layer1: number;
  layer2: number;
  layer3: number;
  total: number;
}

export interface CostPerEmployeeResult {
  financialYearPlanId: string;
  month: number | null;
  year: number | null;
  quarter: number | null;
  granularity: PeriodGranularity;
  periodLabel: string;
  fromActuals: boolean;
  billable: CategoryCost;
  bench: CategoryCost;
  support: CategoryCost;
  leadership: CategoryCost;
  totalCostPerBillableHead: number;
}

export interface BuMetricRow {
  customerId: string;
  customerCode: string;
  customerName: string;
  internal: boolean;
  plannedRevenue: number;
  actualRevenue: number | null;
  plannedSalaryCost: number;
  actualSalaryCost: number | null;
  plannedBillableHc: number | null;
  actualBillableHc: number | null;
  plannedGrossMargin: number;
  actualGrossMargin: number | null;
  plannedGrossMarginPct: number;
  actualGrossMarginPct: number | null;
  avgSalaryPerHead: number | null;
}

export interface BuMetricsResult {
  financialYearPlanId: string;
  month: number | null;
  year: number | null;
  quarter: number | null;
  granularity: PeriodGranularity;
  periodLabel: string;
  rows: BuMetricRow[];
}

export interface PositionBreakdownRow {
  title: string;
  headcount: number;
  avgPayrollCost: number;
  pctOfBuHc: number;
}

export interface ExternalBuAnalysisRow {
  customerCode: string;
  customerName: string;
  totalHc: number;
  billableHc: number;
  nonBillableHc: number;
  totalGrossPay: number;
  billableGrossPay: number;
  nonBillableGrossPay: number;
  totalPayrollCost: number;
  billablePayrollCost: number;
  nonBillablePayrollCost: number;
  avgPayrollCostPerHead: number;
  buCostPctOfTotal: number;
  buRevenuePctOfTotal: number;
  actualRevenue: number;
  grossMargin: number;
  grossMarginPct: number;
  positionBreakdown: PositionBreakdownRow[];
}

export interface InternalBuAnalysisRow {
  customerCode: string;
  customerName: string;
  totalHc: number;
  billableHc: number;
  nonBillableHc: number;
  totalGrossPay: number;
  totalPayrollCost: number;
  avgPayrollCostPerHead: number;
  buCostPctOfTotal: number;
  positionBreakdown: PositionBreakdownRow[];
}

export interface BuAnalysisResult {
  financialYearPlanId: string;
  month: number | null;
  year: number | null;
  quarter: number | null;
  granularity: PeriodGranularity;
  periodLabel: string;
  totalCompanyPayrollCost: number;
  totalCompanyRevenue: number;
  totalCompanyHc: number;
  externalBUs: ExternalBuAnalysisRow[];
  internalBUs: InternalBuAnalysisRow[];
}

export interface FyMonthCol {
  key: string;
  label: string;
  planMonth: number;
  planYear: number;
}
