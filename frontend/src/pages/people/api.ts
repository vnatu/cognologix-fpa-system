import axios from 'axios';
import type {
  ClassificationConfigMap,
  ClassificationConfigType,
  DashboardPeriod,
  DashboardSummary,
  DashboardTrendMetric,
  DashboardTrendPoint,
  EmployeeRegistryEntry,
  ImportType,
  MappingTemplate,
  MasterRecord,
  MasterSummary,
  ParseHeadersResponse,
  PeriodResponse,
  PeriodVersionDetail,
  PeriodVersionSummary,
  SnapshotDetail,
  SnapshotUploadResult,
} from './types';

// ── Periods ──────────────────────────────────────────────────────────────────

export const fetchPeriods = (): Promise<PeriodResponse[]> =>
  axios.get<PeriodResponse[]>('/api/people/periods').then((r) => r.data);

export const createPeriod = (payload: {
  periodMonth: number;
  periodYear: number;
}): Promise<PeriodResponse> =>
  axios
    .post<PeriodResponse>('/api/people/periods', payload)
    .then((r) => r.data);

export const createPeriodVersion = (
  periodId: string,
): Promise<PeriodVersionSummary> =>
  axios
    .post<PeriodVersionSummary>(`/api/people/periods/${periodId}/versions`)
    .then((r) => r.data);

export const buildMaster = (
  periodId: string,
  versionId: string,
): Promise<MasterRecord[]> =>
  axios
    .post<MasterRecord[]>(
      `/api/people/periods/${periodId}/versions/${versionId}/build-master`,
    )
    .then((r) => r.data);

export const finalisePeriod = (
  periodId: string,
  versionId: string,
): Promise<void> =>
  axios
    .post(
      `/api/people/periods/${periodId}/versions/${versionId}/finalise`,
    )
    .then(() => undefined);

export const fetchPeriodVersionDetail = (
  periodId: string,
  versionId: string,
): Promise<PeriodVersionDetail> =>
  axios
    .get<PeriodVersionDetail>(
      `/api/people/periods/${periodId}/versions/${versionId}`,
    )
    .then((r) => r.data);

// ── Imports ──────────────────────────────────────────────────────────────────

export const fetchMappingTemplate = (
  importType: ImportType,
): Promise<MappingTemplate | null> =>
  axios
    .get<MappingTemplate>(`/api/people/imports/mappings/${importType}`)
    .then((r) => r.data ?? null);

/** Active templates grouped by import type — single request for settings UI. */
export const fetchMappingTemplatesByType = (): Promise<
  Partial<Record<ImportType, MappingTemplate[]>>
> =>
  axios
    .get<Partial<Record<ImportType, MappingTemplate[]>>>(
      '/api/people/imports/mappings',
    )
    .then((r) => r.data);

/** Active payroll mapping templates (regular + F&F) for template chooser. */
export const fetchPayrollMappingTemplates = async (): Promise<
  MappingTemplate[]
> => {
  const [regular, fnf] = await Promise.all([
    fetchMappingTemplate('ZOHO_PAYROLL'),
    fetchMappingTemplate('ZOHO_PAYROLL_FNF'),
  ]);
  return [regular, fnf].filter((t): t is MappingTemplate => t != null);
};

export const saveMappingTemplate = (payload: {
  importType: ImportType;
  templateName: string;
  lines: Array<{ excelColumnName: string; systemAttribute: string }>;
}): Promise<MappingTemplate> =>
  axios
    .post<MappingTemplate>('/api/people/imports/mappings', payload)
    .then((r) => r.data);

export const parseHeaders = (file: File): Promise<ParseHeadersResponse> => {
  const form = new FormData();
  form.append('file', file);
  return axios
    .post<ParseHeadersResponse>('/api/people/imports/parse-headers', form, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
    .then((r) => r.data);
};

export const uploadSnapshot = (
  periodVersionId: string,
  importType: ImportType,
  mappingId: string,
  file: File,
): Promise<SnapshotUploadResult> => {
  const form = new FormData();
  form.append('file', file);
  return axios
    .post<SnapshotUploadResult>(
      `/api/people/imports/${periodVersionId}/upload`,
      form,
      {
        params: { import_type: importType, mapping_id: mappingId },
        headers: { 'Content-Type': 'multipart/form-data' },
      },
    )
    .then((r) => r.data);
};

export const fetchSnapshotDetail = (
  periodVersionId: string,
  importType: ImportType,
): Promise<SnapshotDetail> =>
  axios
    .get<SnapshotDetail>(
      `/api/people/imports/${periodVersionId}/snapshots/${importType}`,
    )
    .then((r) => ({
      ...r.data,
      payrollUploads: r.data.payrollUploads ?? [],
      exitedRegistryRows: r.data.exitedRegistryRows ?? [],
    }));

// ── Master Data ──────────────────────────────────────────────────────────────

export const fetchMasterRecords = (
  periodVersionId: string,
): Promise<MasterRecord[]> =>
  axios
    .get<MasterRecord[]>(`/api/people/master/${periodVersionId}`)
    .then((r) => r.data);

export const fetchMasterSummary = (
  periodVersionId: string,
): Promise<MasterSummary> =>
  axios
    .get<MasterSummary>(`/api/people/master/${periodVersionId}/summary`)
    .then((r) => r.data);

export const reconcileMaster = (
  periodVersionId: string,
  payload: { payrollSnapshotId: string; employeeRegistryId: string },
): Promise<MasterRecord> =>
  axios
    .post<MasterRecord>(
      `/api/people/master/${periodVersionId}/reconcile`,
      payload,
    )
    .then((r) => r.data);

// ── Employee Registry ────────────────────────────────────────────────────────

export const fetchRegistry = (): Promise<EmployeeRegistryEntry[]> =>
  axios
    .get<EmployeeRegistryEntry[]>('/api/people/registry')
    .then((r) => r.data);

// ── Classification Config ──────────────────────────────────────────────────

export const fetchClassificationConfig =
  (): Promise<ClassificationConfigMap> =>
    axios
      .get<ClassificationConfigMap>('/api/people/config/classification')
      .then((r) => r.data);

export const addClassificationConfig = (payload: {
  configType: ClassificationConfigType;
  value: string;
}) =>
  axios
    .post('/api/people/config/classification', payload)
    .then((r) => r.data);

export const deleteClassificationConfig = (id: string): Promise<void> =>
  axios
    .delete(`/api/people/config/classification/${id}`)
    .then(() => undefined);

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

export interface SimpleImportResult {
  totalRows: number;
  created: number;
  skipped: number;
  errors: Array<{ rowNumber: number; reason: string }>;
}

export const exportClassificationConfig = (): Promise<void> =>
  downloadBlob(
    '/api/people/config/classification/export',
    'classification_config_export.xlsx',
  );

export const downloadClassificationImportSample = (): Promise<void> =>
  downloadBlob(
    '/api/people/config/classification/import/sample',
    'classification_config_import_template.xlsx',
  );

export const importClassificationConfig = (
  file: File,
): Promise<SimpleImportResult> => {
  const form = new FormData();
  form.append('file', file);
  return axios
    .post<SimpleImportResult>('/api/people/config/classification/import', form)
    .then((r) => r.data);
};

export const exportMappingTemplates = (): Promise<void> =>
  downloadBlob(
    '/api/people/imports/mappings/export',
    'mapping_templates_export.xlsx',
  );

export const downloadMappingTemplatesImportSample = (): Promise<void> =>
  downloadBlob(
    '/api/people/imports/mappings/import/sample',
    'mapping_templates_import_template.xlsx',
  );

export const importMappingTemplates = (
  file: File,
): Promise<SimpleImportResult> => {
  const form = new FormData();
  form.append('file', file);
  return axios
    .post<SimpleImportResult>('/api/people/imports/mappings/import', form)
    .then((r) => r.data);
};

// ── Dashboard ────────────────────────────────────────────────────────────────

export const fetchDashboardPeriods = (): Promise<DashboardPeriod[]> =>
  axios
    .get<DashboardPeriod[]>('/api/people/dashboard/periods')
    .then((r) => r.data);

export const fetchDashboardSummary = (
  periodVersionId: string,
): Promise<DashboardSummary> =>
  axios
    .get<DashboardSummary>(
      `/api/people/dashboard/${periodVersionId}/summary`,
    )
    .then((r) => r.data);

export const fetchDashboardTrend = (params: {
  metric: DashboardTrendMetric;
  practiceUnit?: string;
  businessUnit?: string;
}): Promise<DashboardTrendPoint[]> =>
  axios
    .get<DashboardTrendPoint[]>('/api/people/dashboard/trend', {
      params: {
        metric: params.metric,
        practiceUnit: params.practiceUnit || undefined,
        businessUnit: params.businessUnit || undefined,
      },
    })
    .then((r) => r.data);
