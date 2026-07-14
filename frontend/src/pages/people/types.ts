export type ImportType =
  | 'ZOHO_PEOPLE'
  | 'ZOHO_PAYROLL'
  | 'ZOHO_PAYROLL_FNF'
  | 'ZOHO_PEOPLE_EXITED';

export type PayrollImportType = 'ZOHO_PAYROLL' | 'ZOHO_PAYROLL_FNF';

export type PeriodStatus =
  | 'OPEN'
  | 'SNAPSHOTS_UPLOADED'
  | 'MASTER_BUILT'
  | 'FINALISED'
  | 'SUPERSEDED';

export type ReconciliationStatus =
  | 'MATCHED'
  | 'PAYROLL_PENDING'
  | 'AUTO_MATCHED_EXITED'
  | 'UNMATCHED'
  | 'MANUALLY_MAPPED';

export type ClassificationConfigType =
  | 'DELIVERY_PU'
  | 'MANAGEMENT_BU'
  | 'LEADERSHIP_BU';

export type ExitStatus = 'ACTIVE' | 'EXITED';

export interface PeriodVersionSummary {
  id: string;
  versionNumber: number;
  status: PeriodStatus;
  isLatestFinalised: boolean;
  createdBy: string | null;
  createdAt: string;
  finalisedAt: string | null;
}

export interface PeriodResponse {
  id: string;
  periodMonth: number;
  periodYear: number;
  createdAt: string;
  versions: PeriodVersionSummary[];
}

export interface MappingLine {
  id?: string;
  excelColumnName: string;
  systemAttribute: string;
}

export interface MappingTemplate {
  id: string;
  importType: ImportType;
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

export interface SnapshotUploadResult {
  uploadId: string;
  periodVersionId: string;
  rowsImported: number;
  unmappedColumns: string[];
  missingColumns: string[];
  unrecognizedBuCodes: string[];
  periodVersionStatus: PeriodStatus;
}

export interface MasterRecord {
  id: string;
  employeeRegistryId: string;
  payrollSnapshotId: string | null;
  employeeId: string;
  fullName: string;
  practiceUnit: string | null;
  businessUnit: string | null;
  billableStatus: string | null;
  jobLevel: string | null;
  grossPay: number | null;
  isDeliveryPu: boolean;
  isBillable: boolean;
  isBench: boolean;
  isSupport: boolean;
  isLeadership: boolean;
  isManagement: boolean;
  reconciliationStatus: ReconciliationStatus;
  billingCustomerCode: string | null;
  dataQualityFlags: string | null;
  hasWarnings: boolean;
}

export interface ClassificationTotals {
  headcount: number;
  grossPay: number;
}

export interface MasterSummary {
  billable: ClassificationTotals;
  bench: ClassificationTotals;
  support: ClassificationTotals;
  leadership: ClassificationTotals;
  management: ClassificationTotals;
  byBusinessUnit: Array<{
    businessUnit: string;
    billableHc: number;
    totalGrossPay: number;
  }>;
}

export interface EmployeeRegistryEntry {
  id: string;
  employeeId: string;
  fullName: string;
  dateOfJoining: string | null;
  exitStatus: ExitStatus;
  exitDate: string | null;
}

export interface ClassificationConfigEntry {
  id: string;
  configType: ClassificationConfigType;
  value: string;
}

export type ClassificationConfigMap = Partial<
  Record<ClassificationConfigType, ClassificationConfigEntry[]>
>;

export interface PeriodVersionOption {
  periodId: string;
  periodVersionId: string;
  label: string;
  periodMonth: number;
  periodYear: number;
  versionNumber: number;
  status: PeriodStatus;
}

export interface SnapshotUploadSummary {
  id: string;
  importType: ImportType;
  uploadedBy: string;
  uploadedAt: string;
  originalFilename: string;
  rowCount: number;
}

export interface PeriodVersionDetail {
  id: string;
  periodId: string;
  versionNumber: number;
  status: PeriodStatus;
  uploads: SnapshotUploadSummary[];
}

export interface SnapshotUploadMetadata {
  id: string;
  importType: ImportType;
  uploadedBy: string;
  uploadedAt: string;
  originalFilename: string;
  rowCount: number;
  unmappedColumns: string[];
  missingColumns: string[];
  unrecognizedBuCodes: string[];
}

export type ExitDatePrecision = 'DAY_LEVEL' | 'MONTH_LEVEL';

export interface ExitedRegistryRow {
  id: string;
  employeeId: string;
  fullName: string;
  exitDate: string | null;
  exitDatePrecision: ExitDatePrecision | null;
  exitStatus: ExitStatus;
}

export interface PeopleSnapshotRow {
  id: string;
  employeeId: string;
  fullName: string;
  practiceUnit: string;
  businessUnit: string;
  buCode: string | null;
  projectCode: string | null;
  billableStatus: string;
  jobLevel: string | null;
  jobSubLevel: string | null;
  title: string | null;
  dateOfJoining: string | null;
}

export interface PayrollSnapshotRow {
  id: string;
  importType: PayrollImportType;
  employeeNo: string;
  fullName: string;
  grossPay: number;
  netPay: number;
  ctcPerAnnum: number | null;
}

export interface SnapshotDetail {
  periodVersionId: string;
  periodMonth: number;
  periodYear: number;
  versionNumber: number;
  importType: ImportType;
  upload: SnapshotUploadMetadata;
  payrollUploads: SnapshotUploadMetadata[];
  peopleRows: PeopleSnapshotRow[];
  payrollRows: PayrollSnapshotRow[];
  exitedRegistryRows: ExitedRegistryRow[];
}
