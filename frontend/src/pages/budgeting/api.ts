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
  PeriodQuery,
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

async function downloadBlob(url: string, filename: string): Promise<void> {
  const response = await axios.get<Blob>(url, { responseType: 'blob' });
  const objectUrl = window.URL.createObjectURL(response.data);
  const link = document.createElement('a');
  link.href = objectUrl;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.URL.revokeObjectURL(objectUrl);
}

export interface PlanInputImportResult {
  totalRows: number;
  created: number;
  skipped: number;
  errors: Array<{ rowNumber: number; reason: string }>;
}

function importPlanInput(
  url: string,
  file: File,
): Promise<PlanInputImportResult> {
  const form = new FormData();
  form.append('file', file);
  return axios.post<PlanInputImportResult>(url, form).then((r) => r.data);
}

export const fetchPlans = (): Promise<PlanSummary[]> =>
  axios.get<PlanSummary[]>('/api/budgeting/plans').then((r) => r.data);

export const fetchPlan = (planId: string): Promise<PlanDetail> =>
  axios.get<PlanDetail>(base(planId)).then((r) => r.data);

export const createPlan = (payload: {
  fiscalYear: string;
  openingHc: number;
  fiscalYearStart?: string;
  fiscalYearEnd?: string;
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
  period?: PeriodQuery,
): Promise<RollingForecastResult> =>
  axios
    .get<RollingForecastResult>(`${base(planId)}/rolling-forecast`, {
      params: periodParams(period),
    })
    .then((r) => r.data);

export const fetchDelta = (
  planId: string,
  period?: PeriodQuery,
): Promise<DeltaResult> =>
  axios
    .get<DeltaResult>(`${base(planId)}/delta`, {
      params: periodParams(period),
    })
    .then((r) => r.data);

export const fetchPlanVsActual = (
  planId: string,
  forecastTypeId?: string,
  period?: PeriodQuery,
): Promise<PlanVsActualResult> =>
  axios
    .get<PlanVsActualResult>(`${base(planId)}/plan-vs-actual`, {
      params: {
        ...(forecastTypeId ? { forecastTypeId } : {}),
        ...periodParams(period),
      },
    })
    .then((r) => r.data);

export const fetchCostPerEmployee = (
  planId: string,
  period: PeriodQuery,
  forecastTypeId?: string,
): Promise<CostPerEmployeeResult> =>
  axios
    .get<CostPerEmployeeResult>(`${base(planId)}/cost-per-employee`, {
      params: {
        ...periodParams(period),
        ...(forecastTypeId ? { forecastTypeId } : {}),
      },
    })
    .then((r) => r.data);

export const fetchBuMetrics = (
  planId: string,
  period: PeriodQuery,
  forecastTypeId?: string,
): Promise<BuMetricsResult> =>
  axios
    .get<BuMetricsResult>(`${base(planId)}/bu-metrics`, {
      params: {
        ...periodParams(period),
        ...(forecastTypeId ? { forecastTypeId } : {}),
      },
    })
    .then((r) => r.data);

function periodParams(period?: PeriodQuery): Record<string, string | number> {
  if (!period) return {};
  const params: Record<string, string | number> = {
    granularity: period.granularity,
  };
  if (period.month != null) params.month = period.month;
  if (period.year != null) params.year = period.year;
  if (period.quarter != null) params.quarter = period.quarter;
  return params;
}

// ── Plan input Excel export / import ─────────────────────────────────────────

export const exportHcPlan = (
  planId: string,
  typeId: string,
  versionId: string,
): Promise<void> =>
  downloadBlob(
    `${versionPath(planId, typeId, versionId)}/hc-plan/export`,
    'hc_plan_export.xlsx',
  );

export const downloadHcPlanImportSample = (
  planId: string,
  typeId: string,
  versionId: string,
): Promise<void> =>
  downloadBlob(
    `${versionPath(planId, typeId, versionId)}/hc-plan/import/sample`,
    'hc_plan_import_template.xlsx',
  );

export const importHcPlan = (
  planId: string,
  typeId: string,
  versionId: string,
  file: File,
): Promise<PlanInputImportResult> =>
  importPlanInput(
    `${versionPath(planId, typeId, versionId)}/hc-plan/import`,
    file,
  );

export const exportSalaryBudget = (
  planId: string,
  typeId: string,
  versionId: string,
): Promise<void> =>
  downloadBlob(
    `${versionPath(planId, typeId, versionId)}/salary-budget/export`,
    'salary_budget_export.xlsx',
  );

export const downloadSalaryBudgetImportSample = (
  planId: string,
  typeId: string,
  versionId: string,
): Promise<void> =>
  downloadBlob(
    `${versionPath(planId, typeId, versionId)}/salary-budget/import/sample`,
    'salary_budget_import_template.xlsx',
  );

export const importSalaryBudget = (
  planId: string,
  typeId: string,
  versionId: string,
  file: File,
): Promise<PlanInputImportResult> =>
  importPlanInput(
    `${versionPath(planId, typeId, versionId)}/salary-budget/import`,
    file,
  );

export const exportRevenuePlan = (
  planId: string,
  typeId: string,
  versionId: string,
): Promise<void> =>
  downloadBlob(
    `${versionPath(planId, typeId, versionId)}/revenue-plan/export`,
    'client_revenue_plan_export.xlsx',
  );

export const downloadRevenuePlanImportSample = (
  planId: string,
  typeId: string,
  versionId: string,
): Promise<void> =>
  downloadBlob(
    `${versionPath(planId, typeId, versionId)}/revenue-plan/import/sample`,
    'client_revenue_plan_import_template.xlsx',
  );

export const importRevenuePlan = (
  planId: string,
  typeId: string,
  versionId: string,
  file: File,
): Promise<PlanInputImportResult> =>
  importPlanInput(
    `${versionPath(planId, typeId, versionId)}/revenue-plan/import`,
    file,
  );

export const exportOverheadBudget = (
  planId: string,
  typeId: string,
  versionId: string,
): Promise<void> =>
  downloadBlob(
    `${versionPath(planId, typeId, versionId)}/overhead-budget/export`,
    'overhead_budget_export.xlsx',
  );

export const downloadOverheadBudgetImportSample = (
  planId: string,
  typeId: string,
  versionId: string,
): Promise<void> =>
  downloadBlob(
    `${versionPath(planId, typeId, versionId)}/overhead-budget/import/sample`,
    'overhead_budget_import_template.xlsx',
  );

export const importOverheadBudget = (
  planId: string,
  typeId: string,
  versionId: string,
  file: File,
): Promise<PlanInputImportResult> =>
  importPlanInput(
    `${versionPath(planId, typeId, versionId)}/overhead-budget/import`,
    file,
  );

export const exportAllPlanInputs = (
  planId: string,
  typeId: string,
  versionId: string,
): Promise<void> =>
  downloadBlob(
    `${versionPath(planId, typeId, versionId)}/export-all`,
    'plan_inputs_export.zip',
  );

/** Expected entry names inside plan_inputs_export.zip (export-all). */
const PLAN_INPUT_ZIP_ENTRIES = [
  { fileName: 'hc_plan.xlsx', label: 'HC Plan', importFn: importHcPlan },
  {
    fileName: 'salary_budget.xlsx',
    label: 'Salary Budget',
    importFn: importSalaryBudget,
  },
  {
    fileName: 'client_revenue_plan.xlsx',
    label: 'Client Revenue Plan',
    importFn: importRevenuePlan,
  },
  {
    fileName: 'overhead_budget.xlsx',
    label: 'Overhead Budget',
    importFn: importOverheadBudget,
  },
] as const;

export interface PlanInputZipImportPart {
  label: string;
  fileName: string;
  missing?: boolean;
  error?: string;
  result?: PlanInputImportResult;
}

export interface PlanInputZipImportResult {
  parts: PlanInputZipImportPart[];
}

import type JSZipType from 'jszip';
import type { JSZipObject } from 'jszip';

function findZipEntry(
  zip: JSZipType,
  fileName: string,
): JSZipObject | null {
  const direct = zip.file(fileName);
  if (direct) return direct;
  const match = Object.values(zip.files).find(
    (f) => !f.dir && (f.name === fileName || f.name.endsWith(`/${fileName}`)),
  );
  return match ?? null;
}

/** Unzip plan_inputs_export.zip and POST each workbook to its import endpoint in order. */
export async function importAllPlanInputs(
  planId: string,
  typeId: string,
  versionId: string,
  zipFile: File,
): Promise<PlanInputZipImportResult> {
  const JSZip = (await import('jszip')).default;
  const zip = await JSZip.loadAsync(zipFile);
  const parts: PlanInputZipImportPart[] = [];

  for (const entry of PLAN_INPUT_ZIP_ENTRIES) {
    const zipEntry = findZipEntry(zip, entry.fileName);
    if (!zipEntry) {
      parts.push({
        label: entry.label,
        fileName: entry.fileName,
        missing: true,
      });
      continue;
    }
    try {
      const blob = await zipEntry.async('blob');
      const file = new File([blob], entry.fileName, {
        type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
      });
      const result = await entry.importFn(planId, typeId, versionId, file);
      parts.push({ label: entry.label, fileName: entry.fileName, result });
    } catch (error) {
      parts.push({
        label: entry.label,
        fileName: entry.fileName,
        error: error instanceof Error ? error.message : String(error),
      });
    }
  }

  return { parts };
}
