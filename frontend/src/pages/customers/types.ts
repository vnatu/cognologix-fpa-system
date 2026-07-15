export type LifecycleStatus = 'ACTIVE' | 'AT_RISK' | 'CHURNED' | 'PROSPECT';
export type RateCardType = 'FLAT' | 'TIERED';
export type RateCurrency = 'USD' | 'INR';

export interface CustomerSummary {
  id: string;
  customerCode: string;
  customerName: string;
  zohoBooksCustomerRef?: string;
  relationshipOwnerEmployeeId?: string;
  lifecycleStatus: LifecycleStatus;
  internal?: boolean;
}

export interface CustomerDetail extends CustomerSummary {
  commercialTerms?: { dsoDays: number };
  projectCodes: ProjectCode[];
}

export interface ProjectCode {
  id: string;
  projectCode: string;
  description?: string;
}

export interface RateCardLine {
  id: string;
  jobLevel?: string;
  rateAmount: number;
}

export interface RateCard {
  id: string;
  name: string;
  rateCardType: RateCardType;
  currency: RateCurrency;
  effectiveFrom: string;
  effectiveTo?: string;
  projectCodes?: ProjectCode[];
  lines: RateCardLine[];
}

export interface ConcentrationRiskConfig {
  id: string;
  singleClientThresholdPct: number;
}

export type ConflictResolution = 'SKIP' | 'REPLACE';

export interface CustomerImportConflicts {
  existingCodes: string[];
  newCodes: string[];
}

export interface CustomerImportRowError {
  rowNumber: number;
  customerCode: string;
  reason: string;
}

export interface CustomerImportResult {
  totalRows: number;
  created: number;
  updated: number;
  skipped: number;
  errors: CustomerImportRowError[];
}

export interface RateCardImportRowError {
  rowNumber: number;
  customerCode: string;
  rateCardName: string;
  reason: string;
}

export interface RateCardImportSkipped {
  customerCode: string;
  rateCardName: string;
  effectiveFrom: string;
}

export interface RateCardImportResult {
  totalRows: number;
  rateCardsCreated: number;
  rateCardsSkipped: number;
  errors: RateCardImportRowError[];
  skipped: RateCardImportSkipped[];
}

export interface ProjectCodeImportRowError {
  rowNumber: number;
  customerCode: string;
  projectCode: string;
  reason: string;
}

export interface ProjectCodeImportResult {
  totalRows: number;
  created: number;
  skipped: number;
  errors: ProjectCodeImportRowError[];
}
