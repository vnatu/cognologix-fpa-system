import type { ImportType } from './types';

export const MONTH_NAMES = [
  'January',
  'February',
  'March',
  'April',
  'May',
  'June',
  'July',
  'August',
  'September',
  'October',
  'November',
  'December',
] as const;

export const IMPORT_TYPE_LABELS: Record<ImportType, string> = {
  ZOHO_PEOPLE: 'Zoho People',
  ZOHO_PAYROLL: 'Zoho Payroll',
  ZOHO_PAYROLL_FNF: 'Zoho Payroll — F&F',
  ZOHO_PEOPLE_EXITED: 'Zoho People Exited',
};

/** Shorter labels for mapping template chooser */
export const PAYROLL_TEMPLATE_LABELS: Record<
  'ZOHO_PAYROLL' | 'ZOHO_PAYROLL_FNF',
  string
> = {
  ZOHO_PAYROLL: 'Zoho Payroll',
  ZOHO_PAYROLL_FNF: 'Zoho Payroll F&F',
};

export const IMPORT_TYPE_ROUTES: Record<ImportType, string> = {
  ZOHO_PEOPLE: '/people-payroll/imports/zoho-people',
  ZOHO_PAYROLL: '/people-payroll/imports/zoho-payroll',
  ZOHO_PAYROLL_FNF: '/people-payroll/imports/zoho-payroll-fnf',
  ZOHO_PEOPLE_EXITED: '/people-payroll/imports/zoho-people-exited',
};

export function isPayrollImportType(importType: ImportType): boolean {
  return importType === 'ZOHO_PAYROLL' || importType === 'ZOHO_PAYROLL_FNF';
}

/** Combined payroll snapshot detail route (regular + F&F rows). */
export function payrollSnapshotDetailPath(periodVersionId: string): string {
  return snapshotDetailPath(periodVersionId, 'ZOHO_PAYROLL');
}

export function payrollTemplateLabel(importType: ImportType): string {
  if (importType === 'ZOHO_PAYROLL' || importType === 'ZOHO_PAYROLL_FNF') {
    return PAYROLL_TEMPLATE_LABELS[importType];
  }
  return IMPORT_TYPE_LABELS[importType];
}

export function snapshotDetailPath(
  periodVersionId: string,
  importType: ImportType,
): string {
  return `/people-payroll/imports/snapshots/${periodVersionId}/${importType}`;
}

export const SYSTEM_ATTRIBUTE_LABELS: Record<string, string> = {
  EmployeeID: 'Employee ID',
  EmployeeNo: 'Employee No',
  FullName: 'Full Name',
  PracticeUnit: 'Practice Unit',
  BusinessUnit: 'Business Unit',
  BUCode: 'BU Code',
  ProjectCode: 'Project Code',
  BillableStatus: 'Billable Status',
  JobLevel: 'Job Level',
  JobSubLevel: 'Job Sub Level',
  Title: 'Title',
  DateOfJoining: 'Date of Joining',
  GrossPay: 'Gross Pay',
  NetPay: 'Net Pay',
  CtcPerAnnum: 'CTC Per Annum',
  LastWorkingDay: 'Last Working Day',
};

const PAYROLL_ATTRIBUTES = [
  'EmployeeNo',
  'FullName',
  'GrossPay',
  'NetPay',
  'CtcPerAnnum',
] as const;

/** Core attributes that must be mapped for a successful import. */
export const REQUIRED_ATTRIBUTES_BY_IMPORT_TYPE: Record<ImportType, string[]> = {
  ZOHO_PEOPLE: ['EmployeeID'],
  ZOHO_PAYROLL: ['EmployeeNo', 'GrossPay'],
  ZOHO_PAYROLL_FNF: ['EmployeeNo', 'GrossPay'],
  ZOHO_PEOPLE_EXITED: ['EmployeeID', 'LastWorkingDay'],
};

/** System attributes relevant per import type (ADR-019 / ADR-020). */
export const ATTRIBUTES_BY_IMPORT_TYPE: Record<ImportType, string[]> = {
  ZOHO_PEOPLE: [
    'EmployeeID',
    'FullName',
    'PracticeUnit',
    'BusinessUnit',
    'BUCode',
    'ProjectCode',
    'BillableStatus',
    'JobLevel',
    'JobSubLevel',
    'Title',
    'DateOfJoining',
  ],
  ZOHO_PAYROLL: [...PAYROLL_ATTRIBUTES],
  ZOHO_PAYROLL_FNF: [...PAYROLL_ATTRIBUTES],
  ZOHO_PEOPLE_EXITED: [
    'EmployeeID',
    'FullName',
    'PracticeUnit',
    'BusinessUnit',
    'BUCode',
    'ProjectCode',
    'BillableStatus',
    'JobLevel',
    'JobSubLevel',
    'Title',
    'DateOfJoining',
    'LastWorkingDay',
  ],
};

export const PERIOD_STATUS_LABELS: Record<string, string> = {
  OPEN: 'Open',
  SNAPSHOTS_UPLOADED: 'Snapshots Uploaded',
  MASTER_BUILT: 'Master Built',
  FINALISED: 'Finalised',
  SUPERSEDED: 'Superseded',
};

export const RECONCILIATION_STATUS_LABELS: Record<string, string> = {
  MATCHED: 'Matched',
  PAYROLL_PENDING: 'Payroll Pending',
  AUTO_MATCHED_EXITED: 'Auto-matched Exited',
  UNMATCHED: 'Unmatched',
  MANUALLY_MAPPED: 'Manually Mapped',
};

export const CLASSIFICATION_CONFIG_LABELS: Record<string, string> = {
  DELIVERY_PU: 'Delivery Practice Units',
  MANAGEMENT_BU: 'Management BUs',
  LEADERSHIP_BU: 'Leadership BUs',
};

export const DATA_QUALITY_FLAG_LABELS: Record<string, string> = {
  MISSING_PROJECT_CODE:
    'External BU employee has no Project Code in Zoho People — billing client cannot be determined',
  PROJECT_CODE_NOT_FOUND:
    'Project Code not registered in Customer Management — billing client cannot be determined',
  BILLING_CLIENT_UNRESOLVED:
    'Employee is marked billable but no billing client could be derived',
};
