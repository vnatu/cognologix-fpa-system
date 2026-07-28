import axios from 'axios';

async function downloadBlob(url: string, fallbackFilename: string): Promise<void> {
  const response = await axios.get<Blob>(url, { responseType: 'blob' });
  const disposition = response.headers['content-disposition'] as string | undefined;
  let filename = fallbackFilename;
  if (disposition) {
    const match = /filename="?([^"]+)"?/.exec(disposition);
    if (match?.[1]) filename = match[1];
  }
  const objectUrl = window.URL.createObjectURL(response.data);
  const link = document.createElement('a');
  link.href = objectUrl;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.URL.revokeObjectURL(objectUrl);
}

export interface BackupMeta {
  lastBackupAt: string;
}

export interface RestoreDryRun {
  restoreToken: string;
  expiresAt: string;
  warning: string;
  filesPresent: string[];
  filesMissing: string[];
  recordCounts: Record<string, number>;
}

export interface RestoreConfirmResult {
  recordsRestored: Record<string, number>;
  errors: string[];
  message: string;
}

export const fetchBackupMeta = (): Promise<BackupMeta> =>
  axios.get<BackupMeta>('/api/system/backup/meta').then((r) => r.data);

export const downloadSystemBackup = (): Promise<void> =>
  downloadBlob('/api/system/backup', 'cognologix_backup.zip');

export const prepareSystemRestore = (file: File): Promise<RestoreDryRun> => {
  const form = new FormData();
  form.append('file', file);
  return axios
    .post<RestoreDryRun>('/api/system/restore', form)
    .then((r) => r.data);
};

export const confirmSystemRestore = (restoreToken: string): Promise<RestoreConfirmResult> =>
  axios
    .post<RestoreConfirmResult>('/api/system/restore/confirm', { restoreToken })
    .then((r) => r.data);
