import axios from 'axios';
import type {
  DashboardResponse,
  InvoiceListPage,
  MappingTemplate,
  ParseHeadersResponse,
  RevenueImportType,
  UploadResult,
  UploadSummary,
} from './types';

async function downloadBlob(url: string, filename: string, params?: Record<string, unknown>): Promise<void> {
  const response = await axios.get<Blob>(url, { responseType: 'blob', params });
  const objectUrl = window.URL.createObjectURL(response.data);
  const link = document.createElement('a');
  link.href = objectUrl;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.URL.revokeObjectURL(objectUrl);
}

export type InvoiceFilterParams = {
  customerId?: string;
  periodMonth?: number;
  periodYear?: number;
  status?: string;
};

export type InvoiceExportParams = InvoiceFilterParams;

export const parseHeaders = (
  importType: RevenueImportType,
  file: File,
): Promise<ParseHeadersResponse> => {
  const form = new FormData();
  form.append('file', file);
  const path =
    importType === 'ZOHO_BOOKS_CREDIT_NOTES'
      ? '/api/revenue/imports/credit-notes/parse-headers'
      : '/api/revenue/imports/invoices/parse-headers';
  return axios
    .post<ParseHeadersResponse>(path, form)
    .then((r) => r.data);
};

export const fetchMappingTemplate = (
  importType: RevenueImportType,
): Promise<MappingTemplate | null> =>
  axios
    .get<MappingTemplate>(`/api/revenue/imports/mappings/${importType}`, {
      validateStatus: (s) => s === 200 || s === 204,
    })
    .then((r) => (r.status === 204 ? null : r.data));

export const fetchMappingTemplatesByType = (): Promise<
  Partial<Record<string, MappingTemplate[]>>
> =>
  axios
    .get<Partial<Record<string, MappingTemplate[]>>>(
      '/api/revenue/imports/mappings',
    )
    .then((r) => r.data);

export const saveMappingTemplate = (payload: {
  importType: RevenueImportType;
  templateName: string;
  lines: Array<{ excelColumnName: string; systemAttribute: string }>;
}): Promise<MappingTemplate> =>
  axios
    .post<MappingTemplate>('/api/revenue/imports/mappings', payload)
    .then((r) => r.data);

export const uploadInvoices = (
  periodMonth: number,
  periodYear: number,
  mappingId: string,
  file: File,
): Promise<UploadResult> => {
  const form = new FormData();
  form.append('file', file);
  return axios
    .post<UploadResult>(
      `/api/revenue/imports/${periodMonth}/${periodYear}/invoices`,
      form,
      { params: { mapping_id: mappingId } },
    )
    .then((r) => r.data);
};

export const uploadCreditNotes = (
  periodMonth: number,
  periodYear: number,
  mappingId: string,
  file: File,
): Promise<UploadResult> => {
  const form = new FormData();
  form.append('file', file);
  return axios
    .post<UploadResult>(
      `/api/revenue/imports/${periodMonth}/${periodYear}/credit-notes`,
      form,
      { params: { mapping_id: mappingId } },
    )
    .then((r) => r.data);
};

export const fetchUploadsForPeriod = (
  periodMonth: number,
  periodYear: number,
): Promise<UploadSummary[]> =>
  axios
    .get<UploadSummary[]>(
      `/api/revenue/imports/${periodMonth}/${periodYear}/uploads`,
    )
    .then((r) => r.data);

export const fetchInvoices = (
  params: InvoiceFilterParams & {
    importType?: RevenueImportType;
    page?: number;
    size?: number;
  },
): Promise<InvoiceListPage> =>
  axios
    .get<InvoiceListPage>('/api/revenue/invoices', { params })
    .then((r) => r.data);

export const exportInvoices = (params: InvoiceExportParams): Promise<void> =>
  downloadBlob('/api/revenue/invoices/export', 'invoices_export.xlsx', params);

export const exportCreditNotes = (params: InvoiceExportParams): Promise<void> =>
  downloadBlob('/api/revenue/credit-notes/export', 'credit_notes_export.xlsx', params);

export const fetchDashboard = (
  periodMonth: number,
  periodYear: number,
): Promise<DashboardResponse> =>
  axios
    .get<DashboardResponse>(
      `/api/revenue/dashboard/${periodMonth}/${periodYear}`,
    )
    .then((r) => r.data);
