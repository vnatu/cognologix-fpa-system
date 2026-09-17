import axios from 'axios';
import type {
  ContraRule,
  Ledger,
  LearnedMapping,
  LlmHint,
  MappingTemplate,
  OllamaConfig,
  OllamaTestResult,
  PageResponse,
  ParseHeadersResponse,
  ReconRun,
  TransactionRow,
  VoucherType,
} from './types';
import { HDFC_IMPORT_TYPE } from './constants';

export const uploadStatement = (file: File, mappingId: string): Promise<ReconRun> => {
  const form = new FormData();
  form.append('file', file);
  return axios
    .post<ReconRun>('/api/bank-recon/runs/upload', form, { params: { mapping_id: mappingId } })
    .then((r) => r.data);
};

export const parseStatementHeaders = (file: File): Promise<ParseHeadersResponse> => {
  const form = new FormData();
  form.append('file', file);
  return axios
    .post<ParseHeadersResponse>('/api/bank-recon/runs/parse-headers', form)
    .then((r) => r.data);
};

export const fetchColumnMapping = (): Promise<MappingTemplate | null> =>
  axios
    .get<MappingTemplate>(`/api/bank-recon/column-mappings/${HDFC_IMPORT_TYPE}`, {
      validateStatus: (s) => s === 200 || s === 204,
    })
    .then((r) => (r.status === 204 ? null : r.data));

export const fetchColumnMappingsByType = (): Promise<
  Partial<Record<string, MappingTemplate[]>>
> =>
  axios
    .get<Partial<Record<string, MappingTemplate[]>>>('/api/bank-recon/column-mappings')
    .then((r) => r.data);

export const saveColumnMapping = (payload: {
  importType: string;
  templateName: string;
  lines: Array<{ excelColumnName: string; systemAttribute: string }>;
}): Promise<MappingTemplate> =>
  axios.post<MappingTemplate>('/api/bank-recon/column-mappings', payload).then((r) => r.data);

export const downloadMappingSample = async (): Promise<void> => {
  const response = await axios.get<Blob>('/api/bank-recon/runs/mapping/sample', {
    responseType: 'blob',
  });
  const url = window.URL.createObjectURL(response.data);
  const link = document.createElement('a');
  link.href = url;
  link.download = 'hdfc-bank-statement-sample.xlsx';
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.URL.revokeObjectURL(url);
};

export const fetchRuns = (page = 0, size = 20): Promise<PageResponse<ReconRun>> =>
  axios.get<PageResponse<ReconRun>>('/api/bank-recon/runs', { params: { page, size } }).then((r) => r.data);

export const fetchRun = (runId: string): Promise<ReconRun> =>
  axios.get<ReconRun>(`/api/bank-recon/runs/${runId}`).then((r) => r.data);

export const updateTransaction = (
  runId: string,
  txId: string,
  payload: { ledgerName?: string | null; voucherType?: VoucherType; excluded?: boolean },
): Promise<TransactionRow> =>
  axios
    .put<TransactionRow>(`/api/bank-recon/runs/${runId}/transactions/${txId}`, payload)
    .then((r) => r.data);

export const exportRun = async (
  runId: string,
  transactionIds: string[],
  filename: string,
): Promise<void> => {
  const response = await axios.post<Blob>(
    `/api/bank-recon/runs/${runId}/export`,
    { transactionIds },
    { responseType: 'blob' },
  );
  const url = window.URL.createObjectURL(response.data);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.URL.revokeObjectURL(url);
};

export const closeRun = (runId: string): Promise<ReconRun> =>
  axios.post<ReconRun>(`/api/bank-recon/runs/${runId}/close`).then((r) => r.data);

export const discardRun = (runId: string): Promise<void> =>
  axios.delete(`/api/bank-recon/runs/${runId}`).then(() => undefined);

export const fetchLedgers = (
  search = '',
  page = 0,
  size = 200,
  voucherType?: VoucherType,
): Promise<PageResponse<Ledger>> =>
  axios
    .get<PageResponse<Ledger>>('/api/bank-recon/ledgers', {
      params: { search, page, size, ...(voucherType ? { voucherType } : {}) },
    })
    .then((r) => r.data);

export const importLedgers = (file: File) => {
  const form = new FormData();
  form.append('file', file);
  return axios.post<{ imported: number; updated: number }>('/api/bank-recon/ledgers/import', form).then((r) => r.data);
};

export const addLedger = (ledgerName: string, groupName: string): Promise<Ledger> =>
  axios.post<Ledger>('/api/bank-recon/ledgers', { ledgerName, groupName }).then((r) => r.data);

export const fetchMappings = (search = '', page = 0, size = 50): Promise<PageResponse<LearnedMapping>> =>
  axios
    .get<PageResponse<LearnedMapping>>('/api/bank-recon/mappings', { params: { search, page, size } })
    .then((r) => r.data);

export const deleteMapping = (id: string): Promise<void> =>
  axios.delete(`/api/bank-recon/mappings/${id}`).then(() => undefined);

export const fetchHints = (): Promise<LlmHint[]> =>
  axios.get<LlmHint[]>('/api/bank-recon/hints').then((r) => r.data);

export const createHint = (payload: {
  hintText: string;
  voucherType: LlmHint['voucherType'];
  active?: boolean;
}): Promise<LlmHint> => axios.post<LlmHint>('/api/bank-recon/hints', payload).then((r) => r.data);

export const updateHint = (id: string, payload: Partial<LlmHint>): Promise<LlmHint> =>
  axios.put<LlmHint>(`/api/bank-recon/hints/${id}`, payload).then((r) => r.data);

export const deleteHint = (id: string): Promise<void> =>
  axios.delete(`/api/bank-recon/hints/${id}`).then(() => undefined);

export const fetchContraRules = (): Promise<ContraRule[]> =>
  axios.get<ContraRule[]>('/api/bank-recon/contra-rules').then((r) => r.data);

export const createContraRule = (patternType: ContraRule['patternType'], patternValue: string) =>
  axios
    .post<ContraRule>('/api/bank-recon/contra-rules', { patternType, patternValue })
    .then((r) => r.data);

export const deleteContraRule = (id: string): Promise<void> =>
  axios.delete(`/api/bank-recon/contra-rules/${id}`).then(() => undefined);

export const fetchOllamaConfig = (): Promise<OllamaConfig> =>
  axios.get<OllamaConfig>('/api/bank-recon/config/ollama').then((r) => r.data);

export const saveOllamaConfig = (payload: OllamaConfig): Promise<OllamaConfig> =>
  axios.put<OllamaConfig>('/api/bank-recon/config/ollama', payload).then((r) => r.data);

export const testOllama = (): Promise<OllamaTestResult> =>
  axios.post<OllamaTestResult>('/api/bank-recon/config/ollama/test').then((r) => r.data);
