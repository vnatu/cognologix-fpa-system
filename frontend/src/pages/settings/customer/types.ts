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
  lines: RateCardLine[];
}

export interface ConcentrationRiskConfig {
  id: string;
  singleClientThresholdPct: number;
}
