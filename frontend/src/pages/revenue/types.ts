export type RevenueImportType =
  | 'ZOHO_BOOKS_INVOICES'
  | 'ZOHO_BOOKS_CREDIT_NOTES';

export type RevenueUploadStatus = 'ACTIVE' | 'SUPERSEDED';

export type RevenueCurrency = 'USD' | 'INR';

export interface MappingLine {
  id?: string;
  excelColumnName: string;
  systemAttribute: string;
}

export interface MappingTemplate {
  id: string;
  importType: string;
  templateName: string;
  active: boolean;
  createdAt: string;
  updatedAt: string;
  lines: MappingLine[];
}

export interface ParseHeadersResponse {
  headers: string[];
  rowCount: number;
}

export interface UploadResult {
  uploadId: string;
  importType: RevenueImportType;
  periodMonth: number;
  periodYear: number;
  versionNumber: number;
  rowsImported: number;
  unmappedColumns: string[];
  missingColumns: string[];
  unrecognizedCustomerCodes: string[];
  duplicateNumbers: string[];
}

export interface UploadSummary {
  id: string;
  importType: RevenueImportType;
  periodMonth: number;
  periodYear: number;
  versionNumber: number;
  status: RevenueUploadStatus;
  uploadedBy: string;
  uploadedAt: string;
  originalFilename: string;
  rowCount: number;
  unmappedColumns: string[];
  missingColumns: string[];
  unrecognizedCustomerCodes: string[];
}

export interface InvoiceListItem {
  id: string;
  importType: RevenueImportType;
  documentNumber: string;
  customerId: string;
  periodMonth: number;
  periodYear: number;
  documentDate: string | null;
  status: string | null;
  amount: number;
  balance: number | null;
  dueDate: string | null;
  currency: RevenueCurrency;
  projectCode: string | null;
  amountInr: number | null;
  /** Raw USD as invoiced — no conversion. Null for INR / unmapped. */
  amountUsd: number | null;
}

export interface InvoiceListPage {
  content: InvoiceListItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface RevenueVsPlanRow {
  customerId: string;
  customerName: string;
  plannedRevenue: number;
  actualNetRevenue: number;
  actualNetRevenueInr: number;
  variance: number;
  varianceInr: number;
  /** Net raw USD when any AmountUsd present; otherwise null. */
  actualAmountUsd: number | null;
}

export interface InvoiceStatusBucket {
  status: string;
  count: number;
  totalAmount: number;
  totalAmountInr: number;
}

export interface DsoRow {
  customerId: string;
  customerName: string;
  avgDaysOutstanding: number | null;
  oldestOutstandingInvoiceDate: string | null;
  outstandingBalance: number;
  unpaidInvoiceCount: number;
}

export type RevenueDashboardGranularity = 'MONTHLY' | 'QUARTERLY' | 'ANNUAL';

export interface MonthCovered {
  month: number;
  year: number;
  label: string;
}

export interface PeriodWithData {
  month: number;
  year: number;
  label: string;
}

export interface DashboardResponse {
  periodMonth: number;
  periodYear: number;
  granularity: RevenueDashboardGranularity;
  quarter: number | null;
  periodLabel: string;
  monthsCovered: MonthCovered[];
  actualsCoverageNote: string | null;
  revenueVsPlan: RevenueVsPlanRow[];
  invoiceStatusSummary: InvoiceStatusBucket[];
  dso: DsoRow[];
}
