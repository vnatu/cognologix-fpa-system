import type { RevenueImportType } from './types';

export const IMPORT_TYPE_LABELS: Record<RevenueImportType, string> = {
  ZOHO_BOOKS_INVOICES: 'Zoho Books Invoices',
  ZOHO_BOOKS_CREDIT_NOTES: 'Zoho Books Credit Notes',
};

export const IMPORT_TYPE_ROUTES: Record<RevenueImportType, string> = {
  ZOHO_BOOKS_INVOICES: '/revenue/imports/zoho-books-invoices',
  ZOHO_BOOKS_CREDIT_NOTES: '/revenue/imports/zoho-books-credit-notes',
};

export const SYSTEM_ATTRIBUTE_LABELS: Record<string, string> = {
  InvoiceNumber: 'Invoice Number',
  CustomerCode: 'Customer Code',
  CustomerName: 'Customer Name',
  InvoiceDate: 'Invoice Date',
  Status: 'Status',
  Amount: 'Amount',
  Balance: 'Balance',
  DueDate: 'Due Date',
  Currency: 'Currency',
  ProjectCode: 'Project Code',
  CreditNoteNumber: 'Credit Note Number',
  CreditNoteDate: 'Credit Note Date',
};

export const ATTRIBUTES_BY_IMPORT_TYPE: Record<RevenueImportType, string[]> = {
  ZOHO_BOOKS_INVOICES: [
    'InvoiceNumber',
    'CustomerCode',
    'CustomerName',
    'InvoiceDate',
    'Status',
    'Amount',
    'Balance',
    'DueDate',
    'Currency',
    'ProjectCode',
  ],
  ZOHO_BOOKS_CREDIT_NOTES: [
    'CreditNoteNumber',
    'CustomerCode',
    'CustomerName',
    'CreditNoteDate',
    'Status',
    'Amount',
    'Currency',
  ],
};

export const REQUIRED_ATTRIBUTES_BY_IMPORT_TYPE: Record<
  RevenueImportType,
  string[]
> = {
  ZOHO_BOOKS_INVOICES: [
    'InvoiceNumber',
    'CustomerCode',
    'InvoiceDate',
    'Status',
    'Amount',
  ],
  ZOHO_BOOKS_CREDIT_NOTES: [
    'CreditNoteNumber',
    'CustomerCode',
    'CreditNoteDate',
    'Status',
    'Amount',
  ],
};

export const MONTH_OPTIONS = [
  { value: 1, label: 'January' },
  { value: 2, label: 'February' },
  { value: 3, label: 'March' },
  { value: 4, label: 'April' },
  { value: 5, label: 'May' },
  { value: 6, label: 'June' },
  { value: 7, label: 'July' },
  { value: 8, label: 'August' },
  { value: 9, label: 'September' },
  { value: 10, label: 'October' },
  { value: 11, label: 'November' },
  { value: 12, label: 'December' },
];

export const INVOICE_STATUS_OPTIONS = [
  'Paid',
  'Partially Paid',
  'Sent',
  'Overdue',
  'Void',
] as const;

export const STATUS_TAG_COLOR: Record<string, string> = {
  Paid: 'green',
  'Partially Paid': 'gold',
  Sent: 'blue',
  Overdue: 'red',
  Void: 'default',
};

/** Convert absolute INR to lakhs for Rs L display columns. */
export function toLakhs(amount: number | null | undefined): number | null {
  if (amount == null || Number.isNaN(amount)) return null;
  return amount / 100_000;
}

export function yearOptions(span = 6): number[] {
  const current = new Date().getFullYear();
  const years: number[] = [];
  for (let y = current + 1; y >= current - span; y -= 1) {
    years.push(y);
  }
  return years;
}
