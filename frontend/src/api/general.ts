import axios from 'axios';
import type { DateFormatOption } from '@/utils/formatDate';
import type { SimpleImportResult } from '@/components/SimpleExcelImportModal';

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

export const fetchDateFormat = (): Promise<DateFormatOption> =>
  axios
    .get<{ format: DateFormatOption }>('/api/general/config/date-format')
    .then((r) => r.data.format);

export const updateDateFormat = (
  format: DateFormatOption,
): Promise<DateFormatOption> =>
  axios
    .put<{ format: DateFormatOption }>('/api/general/config/date-format', {
      format,
    })
    .then((r) => r.data.format);

export interface SecurityConfig {
  jwtExpiryHours: number;
  inactivityTimeoutMinutes: number;
}

export const fetchSecurityConfig = (): Promise<SecurityConfig> =>
  axios.get<SecurityConfig>('/api/general/config/security').then((r) => r.data);

export const updateSecurityConfig = (
  payload: SecurityConfig,
): Promise<SecurityConfig> =>
  axios
    .put<SecurityConfig>('/api/general/config/security', payload)
    .then((r) => r.data);

export interface FxRate {
  id: string;
  currencyPair: string;
  rate: number;
  effectiveFrom: string;
  effectiveTo: string | null;
  createdBy: string;
}

export interface CreateFxRateRequest {
  currencyPair: string;
  rate: number;
  effectiveFrom: string;
}

export const fetchFxRates = (): Promise<FxRate[]> =>
  axios.get<FxRate[]>('/api/general/fx-rates').then((r) => r.data);

export const createFxRate = (payload: CreateFxRateRequest): Promise<FxRate> =>
  axios.post<FxRate>('/api/general/fx-rates', payload).then((r) => r.data);

export const exportFxRates = (): Promise<void> =>
  downloadBlob('/api/general/fx-rates/export', 'fx_rates_export.xlsx');

export const downloadFxRateImportSample = (): Promise<void> =>
  downloadBlob('/api/general/fx-rates/import/sample', 'fx_rates_import_template.xlsx');

export const importFxRates = (file: File): Promise<SimpleImportResult> => {
  const form = new FormData();
  form.append('file', file);
  return axios
    .post<SimpleImportResult>('/api/general/fx-rates/import', form)
    .then((r) => r.data);
};
