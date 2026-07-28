import axios from 'axios';
import type {
  CategoryGroup,
  ExpenseCategory,
  ExpenseImportResult,
  MonthHistory,
  MonthlyExpenses,
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

export const fetchCategoryGroups = (): Promise<CategoryGroup[]> =>
  axios.get<CategoryGroup[]>('/api/expenses/categories').then((r) => r.data);

export const fetchAllCategories = (): Promise<ExpenseCategory[]> =>
  axios.get<ExpenseCategory[]>('/api/expenses/categories/all').then((r) => r.data);

export const addCategory = (payload: {
  lineCode: string;
  categoryGroup: string;
  displayName: string;
  description?: string;
}): Promise<ExpenseCategory> =>
  axios.post<ExpenseCategory>('/api/expenses/categories', payload).then((r) => r.data);

export const deactivateCategory = (id: string): Promise<void> =>
  axios.put(`/api/expenses/categories/${id}/deactivate`).then(() => undefined);

export const fetchMonthlyExpenses = (
  month: number,
  year: number,
): Promise<MonthlyExpenses> =>
  axios.get<MonthlyExpenses>(`/api/expenses/${month}/${year}`).then((r) => r.data);

export const saveMonthlyExpenses = (
  month: number,
  year: number,
  entries: Array<{ categoryId: string; amount: number; notes?: string | null }>,
): Promise<void> =>
  axios.put(`/api/expenses/${month}/${year}`, { entries }).then(() => undefined);

export const lockMonth = (month: number, year: number): Promise<void> =>
  axios.post(`/api/expenses/${month}/${year}/lock`).then(() => undefined);

export const unlockMonth = (
  month: number,
  year: number,
  reason: string,
): Promise<void> =>
  axios
    .post(`/api/expenses/${month}/${year}/unlock`, { reason })
    .then(() => undefined);

export const fetchExpenseHistory = (): Promise<MonthHistory[]> =>
  axios.get<MonthHistory[]>('/api/expenses/history').then((r) => r.data);

export const exportExpenses = (month: number, year: number): Promise<void> =>
  downloadBlob(
    `/api/expenses/${month}/${year}/export`,
    `expenses_${String(month).padStart(2, '0')}_${year}.xlsx`,
  );

export const downloadExpenseSample = (): Promise<void> =>
  downloadBlob('/api/expenses/export/sample', 'expenses_import_template.xlsx');

export const importExpenses = (
  month: number,
  year: number,
  file: File,
): Promise<ExpenseImportResult> => {
  const form = new FormData();
  form.append('file', file);
  return axios
    .post<ExpenseImportResult>(`/api/expenses/${month}/${year}/import`, form)
    .then((r) => r.data);
};
