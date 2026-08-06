import axios from 'axios';
import type { PeriodQuery } from '@/pages/budgeting/types';

export type StandardReportId =
  | 'pl'
  | 'bu-margin'
  | 'headcount'
  | 'cost-per-employee'
  | 'rolling-forecast'
  | 'expense-summary';

async function downloadBlob(
  url: string,
  fallbackFilename: string,
  params?: Record<string, string | number>,
): Promise<void> {
  const response = await axios.get<Blob>(url, {
    responseType: 'blob',
    params,
  });
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

export function downloadStandardReport(
  reportId: StandardReportId,
  planId: string,
  period: PeriodQuery,
): Promise<void> {
  const params =
    reportId === 'rolling-forecast'
      ? { planId }
      : { planId, ...periodParams(period) };
  return downloadBlob(
    `/api/reports/${reportId}`,
    `${reportId.replace(/-/g, '_')}_report.xlsx`,
    params,
  );
}
