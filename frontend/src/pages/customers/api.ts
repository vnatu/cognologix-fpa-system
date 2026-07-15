import axios from 'axios';
import type {
  CustomerSummary,
  CustomerDetail,
  RateCard,
  ProjectCode,
  ConcentrationRiskConfig,
  LifecycleStatus,
  RateCardType,
  RateCurrency,
  ConflictResolution,
  CustomerImportConflicts,
  CustomerImportResult,
  RateCardImportResult,
  ProjectCodeImportResult,
} from './types';

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

// ── Customers ────────────────────────────────────────────────────────────────

export const fetchCustomers = (includeInternal = false): Promise<CustomerSummary[]> =>
  axios
    .get<CustomerSummary[]>('/api/customers', { params: { includeInternal } })
    .then((r) => r.data);

export const fetchCustomer = (id: string): Promise<CustomerDetail> =>
  axios.get<CustomerDetail>(`/api/customers/${id}`).then((r) => r.data);

export const createCustomer = (payload: {
  customerCode: string;
  customerName: string;
  zohoBooksCustomerRef?: string;
  relationshipOwnerEmployeeId?: string;
  lifecycleStatus: LifecycleStatus;
  dsoDays?: number;
}): Promise<CustomerSummary> =>
  axios.post<CustomerSummary>('/api/customers', payload).then((r) => r.data);

export const updateCustomer = (
  id: string,
  payload: {
    customerCode?: string;
    customerName?: string;
    lifecycleStatus?: LifecycleStatus;
    relationshipOwnerEmployeeId?: string;
    dsoDays?: number;
  },
): Promise<CustomerDetail> =>
  axios.put<CustomerDetail>(`/api/customers/${id}`, payload)    .then((r) => r.data);

export const exportCustomers = (): Promise<void> =>
  downloadBlob('/api/customers/export', 'customers_export.xlsx');

// ── Customer Import (ADR-027) ─────────────────────────────────────────────────

export const checkCustomerImportConflicts = (
  file: File,
): Promise<CustomerImportConflicts> => {
  const form = new FormData();
  form.append('file', file);
  return axios
    .post<CustomerImportConflicts>('/api/customers/import/conflicts', form)
    .then((r) => r.data);
};

export const importCustomers = (
  file: File,
  conflictResolution: ConflictResolution,
): Promise<CustomerImportResult> => {
  const form = new FormData();
  form.append('file', file);
  return axios
    .post<CustomerImportResult>('/api/customers/import', form, {
      params: { conflictResolution },
    })
    .then((r) => r.data);
};

// ── Rate Card Import ───────────────────────────────────────────────────────────

export const downloadRateCardImportSample = (): Promise<void> =>
  downloadBlob('/api/customers/rate-cards/import/sample', 'rate_card_import_template.xlsx');

export const exportRateCards = (): Promise<void> =>
  downloadBlob('/api/customers/rate-cards/export', 'rate_cards_export.xlsx');

export const importRateCards = (file: File): Promise<RateCardImportResult> => {
  const form = new FormData();
  form.append('file', file);
  return axios
    .post<RateCardImportResult>('/api/customers/rate-cards/import', form)
    .then((r) => r.data);
};

// ── Rate Cards ───────────────────────────────────────────────────────────────

export const fetchRateCards = (customerId: string): Promise<RateCard[]> =>
  axios
    .get<RateCard[]>(`/api/customers/${customerId}/rate-cards`)
    .then((r) => r.data);

export const createRateCard = (
  customerId: string,
  payload: {
    name: string;
    rateCardType: RateCardType;
    currency: RateCurrency;
    effectiveFrom: string;
    lines: Array<{ jobLevel?: string; rateAmount: number }>;
  },
): Promise<RateCard> =>
  axios
    .post<RateCard>(`/api/customers/${customerId}/rate-cards`, payload)
    .then((r) => r.data);

// ── Project Codes ─────────────────────────────────────────────────────────────

export const fetchProjectCodes = (customerId: string): Promise<ProjectCode[]> =>
  axios
    .get<ProjectCode[]>(`/api/customers/${customerId}/project-codes`)
    .then((r) => r.data);

export const addProjectCode = (
  customerId: string,
  payload: { projectCode: string; description?: string },
): Promise<ProjectCode> =>
  axios
    .post<ProjectCode>(`/api/customers/${customerId}/project-codes`, payload)
    .then((r) => r.data);

export const deleteProjectCode = (
  customerId: string,
  codeId: string,
): Promise<void> =>
  axios
    .delete(`/api/customers/${customerId}/project-codes/${codeId}`)
    .then(() => undefined);

export const downloadProjectCodeImportSample = (): Promise<void> =>
  downloadBlob(
    '/api/customers/project-codes/import/sample',
    'project_codes_import_template.xlsx',
  );

export const exportProjectCodes = (): Promise<void> =>
  downloadBlob('/api/customers/project-codes/export', 'project_codes_export.xlsx');

export const importProjectCodes = (file: File): Promise<ProjectCodeImportResult> => {
  const form = new FormData();
  form.append('file', file);
  return axios
    .post<ProjectCodeImportResult>('/api/customers/project-codes/import', form)
    .then((r) => r.data);
};

// ── Concentration Risk ────────────────────────────────────────────────────────

export const fetchConcentrationRiskConfig =
  (): Promise<ConcentrationRiskConfig> =>
    axios
      .get<ConcentrationRiskConfig>('/api/general/concentration-risk-config')
      .then((r) => r.data);

export const updateConcentrationRiskConfig = (
  singleClientThresholdPct: number,
): Promise<ConcentrationRiskConfig> =>
  axios
    .put<ConcentrationRiskConfig>('/api/general/concentration-risk-config', {
      singleClientThresholdPct,
    })
    .then((r) => r.data);
