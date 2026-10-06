import axios from 'axios';
import type {
  AppNotification,
  ContractDashboard,
  ContractDetail,
  ContractNotificationConfig,
  ContractPage,
  ContractPayload,
  ContractStatus,
  ContractType,
  PaperType,
  TemplateGroup,
  TemplateSummary,
  VersionResponse,
  VersionStatus,
} from './types';

export function apiError(error: unknown, fallback: string): string {
  if (axios.isAxiosError(error)) {
    const message = error.response?.data?.error;
    if (typeof message === 'string' && message.trim()) return message;
  }
  return fallback;
}

function saveBlob(blob: Blob, filename: string) {
  const objectUrl = window.URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = objectUrl;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.URL.revokeObjectURL(objectUrl);
}

function filenameFromDisposition(header: string | undefined, fallback: string): string {
  if (!header) return fallback;
  const match = /filename="([^"]+)"/.exec(header);
  return match?.[1] || fallback;
}

export interface ContractListQuery {
  page?: number;
  size?: number;
  typeId?: string;
  paperType?: PaperType;
  status?: ContractStatus;
  customerId?: string;
  expiryFrom?: string;
  expiryTo?: string;
  search?: string;
}

export const fetchContracts = (query: ContractListQuery): Promise<ContractPage> =>
  axios.get<ContractPage>('/api/contracts', { params: query }).then((r) => r.data);

export const fetchContract = (id: string): Promise<ContractDetail> =>
  axios.get<ContractDetail>(`/api/contracts/${id}`).then((r) => r.data);

export const createContract = (payload: ContractPayload): Promise<ContractDetail> =>
  axios.post<ContractDetail>('/api/contracts', payload).then((r) => r.data);

export const updateContract = (id: string, payload: ContractPayload): Promise<ContractDetail> =>
  axios.put<ContractDetail>(`/api/contracts/${id}`, payload).then((r) => r.data);

export const fetchContractDashboard = (): Promise<ContractDashboard> =>
  axios.get<ContractDashboard>('/api/contracts/dashboard').then((r) => r.data);

export const fetchContractTypes = (includeInactive = false): Promise<ContractType[]> =>
  axios
    .get<ContractType[]>('/api/contracts/types', { params: { includeInactive } })
    .then((r) => r.data);

export const createContractType = (payload: {
  typeCode: string;
  displayName: string;
  description?: string;
}): Promise<ContractType> =>
  axios.post<ContractType>('/api/contracts/types', payload).then((r) => r.data);

export const updateContractType = (
  id: string,
  payload: { displayName: string; description?: string | null; active: boolean },
): Promise<ContractType> =>
  axios.put<ContractType>(`/api/contracts/types/${id}`, payload).then((r) => r.data);

export const fetchTemplates = (): Promise<TemplateGroup[]> =>
  axios.get<TemplateGroup[]>('/api/contracts/templates').then((r) => r.data);

export const createTemplate = (payload: {
  contractTypeId: string;
  templateName: string;
  description?: string;
  file: File;
}): Promise<TemplateSummary> => {
  const form = new FormData();
  form.append(
    'metadata',
    new Blob(
      [JSON.stringify({
        contractTypeId: payload.contractTypeId,
        templateName: payload.templateName,
        description: payload.description,
      })],
      { type: 'application/json' },
    ),
    'metadata.json',
  );
  form.append('file', payload.file);
  return axios.post<TemplateSummary>('/api/contracts/templates', form).then((r) => r.data);
};

export const downloadTemplate = async (id: string, fallbackName: string): Promise<void> => {
  const response = await axios.get<Blob>(`/api/contracts/templates/${id}/download`, {
    responseType: 'blob',
  });
  saveBlob(
    response.data,
    filenameFromDisposition(response.headers['content-disposition'], fallbackName),
  );
};

export const addContractVersion = (payload: {
  contractId: string;
  versionLabel: string;
  notes?: string;
  status?: VersionStatus;
  primaryFile: File;
  supportingFiles: File[];
}): Promise<VersionResponse> => {
  const form = new FormData();
  form.append(
    'metadata',
    new Blob(
      [JSON.stringify({
        versionLabel: payload.versionLabel,
        notes: payload.notes,
        status: payload.status ?? 'DRAFT',
      })],
      { type: 'application/json' },
    ),
    'metadata.json',
  );
  form.append('primaryFile', payload.primaryFile);
  payload.supportingFiles.forEach((file) => form.append('supportingFiles', file));
  return axios
    .post<VersionResponse>(`/api/contracts/${payload.contractId}/versions`, form)
    .then((r) => r.data);
};

export const updateVersionStatus = (
  contractId: string,
  versionId: string,
  status: VersionStatus,
): Promise<VersionResponse> =>
  axios
    .put<VersionResponse>(`/api/contracts/${contractId}/versions/${versionId}/status`, { status })
    .then((r) => r.data);

export const downloadContractDocument = async (
  contractId: string,
  versionId: string,
  documentId: string,
  fallbackName: string,
): Promise<void> => {
  const response = await axios.get<Blob>(
    `/api/contracts/${contractId}/versions/${versionId}/documents/${documentId}/download`,
    { responseType: 'blob' },
  );
  saveBlob(
    response.data,
    filenameFromDisposition(response.headers['content-disposition'], fallbackName),
  );
};

export const fetchContractConfig = (): Promise<ContractNotificationConfig> =>
  axios.get<ContractNotificationConfig>('/api/contracts/config').then((r) => r.data);

export const updateContractConfig = (payload: {
  reminderDays: string;
  recipients: string[];
}): Promise<ContractNotificationConfig> =>
  axios.put<ContractNotificationConfig>('/api/contracts/config', payload).then((r) => r.data);

export const fetchNotifications = (): Promise<AppNotification[]> =>
  axios.get<AppNotification[]>('/api/notifications').then((r) => r.data);

export const markNotificationRead = (id: string): Promise<void> =>
  axios.put(`/api/notifications/${id}/read`).then(() => undefined);

export const markAllNotificationsRead = (): Promise<void> =>
  axios.put('/api/notifications/read-all').then(() => undefined);
