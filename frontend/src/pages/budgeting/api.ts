import axios from 'axios';
import type {
  BuMetricsResult,
  ClientRevenuePlanEntry,
  CostPerEmployeeResult,
  DeltaResult,
  ForecastType,
  HcPlanMonth,
  OverheadBudgetEntry,
  OverheadLineItem,
  PlanDetail,
  PlanSummary,
  PlanVsActualResult,
  RollingForecastResult,
  SalaryBudgetMonth,
  ForecastVersion,
} from './types';

const base = (planId: string) => `/api/budgeting/plans/${planId}`;
const versionPath = (planId: string, typeId: string, versionId: string) =>
  `${base(planId)}/forecast-types/${typeId}/versions/${versionId}`;

export const fetchPlans = (): Promise<PlanSummary[]> =>
  axios.get<PlanSummary[]>('/api/budgeting/plans').then((r) => r.data);

export const fetchPlan = (planId: string): Promise<PlanDetail> =>
  axios.get<PlanDetail>(base(planId)).then((r) => r.data);

export const createPlan = (payload: {
  fiscalYear: string;
  openingHc: number;
}): Promise<PlanDetail> =>
  axios.post<PlanDetail>('/api/budgeting/plans', payload).then((r) => r.data);

export const fetchForecastTypes = (planId: string): Promise<ForecastType[]> =>
  axios
    .get<ForecastType[]>(`${base(planId)}/forecast-types`)
    .then((r) => r.data);

export const publishVersion = (
  planId: string,
  typeId: string,
  versionId: string,
): Promise<ForecastVersion> =>
  axios
    .post<ForecastVersion>(`${versionPath(planId, typeId, versionId)}/publish`)
    .then((r) => r.data);

export const createDraftVersion = (
  planId: string,
  typeId: string,
): Promise<ForecastVersion> =>
  axios
    .post<ForecastVersion>(`${base(planId)}/forecast-types/${typeId}/versions`)
    .then((r) => r.data);

export const fetchHcPlan = (
  planId: string,
  typeId: string,
  versionId: string,
): Promise<HcPlanMonth[]> =>
  axios
    .get<HcPlanMonth[]>(`${versionPath(planId, typeId, versionId)}/hc-plan`)
    .then((r) => r.data);

export const saveHcPlan = (
  planId: string,
  typeId: string,
  versionId: string,
  months: HcPlanMonth[],
): Promise<void> =>
  axios.put(`${versionPath(planId, typeId, versionId)}/hc-plan`, { months });

export const fetchSalaryBudget = (
  planId: string,
  typeId: string,
  versionId: string,
): Promise<SalaryBudgetMonth[]> =>
  axios
    .get<SalaryBudgetMonth[]>(
      `${versionPath(planId, typeId, versionId)}/salary-budget`,
    )
    .then((r) => r.data);

export const saveSalaryBudget = (
  planId: string,
  typeId: string,
  versionId: string,
  months: SalaryBudgetMonth[],
): Promise<void> =>
  axios.put(`${versionPath(planId, typeId, versionId)}/salary-budget`, {
    months,
  });

export const fetchRevenuePlan = (
  planId: string,
  typeId: string,
  versionId: string,
): Promise<ClientRevenuePlanEntry[]> =>
  axios
    .get<ClientRevenuePlanEntry[]>(
      `${versionPath(planId, typeId, versionId)}/revenue-plan`,
    )
    .then((r) => r.data);

export const saveRevenuePlan = (
  planId: string,
  typeId: string,
  versionId: string,
  entries: ClientRevenuePlanEntry[],
): Promise<void> =>
  axios.put(`${versionPath(planId, typeId, versionId)}/revenue-plan`, {
    entries,
  });

export const fetchOverheadBudget = (
  planId: string,
  typeId: string,
  versionId: string,
): Promise<OverheadBudgetEntry[]> =>
  axios
    .get<OverheadBudgetEntry[]>(
      `${versionPath(planId, typeId, versionId)}/overhead-budget`,
    )
    .then((r) => r.data);

export const saveOverheadBudget = (
  planId: string,
  typeId: string,
  versionId: string,
  entries: OverheadBudgetEntry[],
): Promise<void> =>
  axios.put(`${versionPath(planId, typeId, versionId)}/overhead-budget`, {
    entries,
  });

export const fetchOverheadLineItems = (): Promise<OverheadLineItem[]> =>
  axios
    .get<OverheadLineItem[]>('/api/budgeting/overhead-line-items')
    .then((r) => r.data);

export const fetchRollingForecast = (
  planId: string,
): Promise<RollingForecastResult> =>
  axios
    .get<RollingForecastResult>(`${base(planId)}/rolling-forecast`)
    .then((r) => r.data);

export const fetchDelta = (planId: string): Promise<DeltaResult> =>
  axios.get<DeltaResult>(`${base(planId)}/delta`).then((r) => r.data);

export const fetchPlanVsActual = (
  planId: string,
  forecastTypeId?: string,
): Promise<PlanVsActualResult> =>
  axios
    .get<PlanVsActualResult>(`${base(planId)}/plan-vs-actual`, {
      params: forecastTypeId ? { forecastTypeId } : undefined,
    })
    .then((r) => r.data);

export const fetchCostPerEmployee = (
  planId: string,
  month: number,
  year: number,
  forecastTypeId?: string,
): Promise<CostPerEmployeeResult> =>
  axios
    .get<CostPerEmployeeResult>(`${base(planId)}/cost-per-employee`, {
      params: { month, year, ...(forecastTypeId ? { forecastTypeId } : {}) },
    })
    .then((r) => r.data);

export const fetchBuMetrics = (
  planId: string,
  month: number,
  year: number,
  forecastTypeId?: string,
): Promise<BuMetricsResult> =>
  axios
    .get<BuMetricsResult>(`${base(planId)}/bu-metrics`, {
      params: { month, year, ...(forecastTypeId ? { forecastTypeId } : {}) },
    })
    .then((r) => r.data);
