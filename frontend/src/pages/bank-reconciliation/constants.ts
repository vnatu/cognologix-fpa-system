export const HDFC_IMPORT_TYPE = 'HDFC_BANK_STATEMENT' as const;

export const HEADER_ATTRIBUTES = [
  { attr: 'AccountNumber', label: 'Account Number', required: true },
  { attr: 'CustomerName', label: 'Customer Name', required: false },
  { attr: 'FromDate', label: 'From Date', required: true },
  { attr: 'ToDate', label: 'To Date', required: true },
  { attr: 'OpeningBalance', label: 'Opening Balance', required: false },
  { attr: 'ClosingBalance', label: 'Closing Balance', required: false },
] as const;

export const TRANSACTION_ATTRIBUTES = [
  { attr: 'TransactionDate', label: 'Transaction Date', required: true },
  { attr: 'TransactionDescription', label: 'Transaction Description', required: true },
  { attr: 'TransactionAmount', label: 'Transaction Amount', required: true },
  { attr: 'DebitCredit', label: 'Debit/Credit', required: true },
  { attr: 'ReferenceNo', label: 'Reference No', required: false },
  { attr: 'ValueDate', label: 'Value Date', required: false },
  { attr: 'TransactionBranch', label: 'Transaction Branch', required: false },
  { attr: 'RunningBalance', label: 'Running Balance', required: false },
] as const;

export const SYSTEM_ATTRIBUTE_LABELS: Record<string, string> = {
  ...Object.fromEntries(HEADER_ATTRIBUTES.map((a) => [a.attr, a.label])),
  ...Object.fromEntries(TRANSACTION_ATTRIBUTES.map((a) => [a.attr, a.label])),
};

export const REQUIRED_ATTRIBUTES = [
  ...HEADER_ATTRIBUTES.filter((a) => a.required).map((a) => a.attr),
  ...TRANSACTION_ATTRIBUTES.filter((a) => a.required).map((a) => a.attr),
];

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
