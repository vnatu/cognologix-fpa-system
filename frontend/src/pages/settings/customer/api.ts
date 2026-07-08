import axios from 'axios';
import type {
  CustomerSummary,
  CustomerDetail,
  RateCard,
  ProjectCode,
  ConcentrationRiskConfig,
  LifecycleStatus,
  RateCardType,
  RateCurrency,
} from './types';

// ── Customers ────────────────────────────────────────────────────────────────

export const fetchCustomers = (): Promise<CustomerSummary[]> =>
  axios.get<CustomerSummary[]>('/api/customers').then((r) => r.data);

export const fetchCustomer = (id: string): Promise<CustomerDetail> =>
  axios.get<CustomerDetail>(`/api/customers/${id}`).then((r) => r.data);

export const createCustomer = (payload: {
  customerCode: string;
  customerName: string;
  zohoBooksCustomerRef?: string;
  relationshipOwnerEmployeeId?: string;
  lifecycleStatus: LifecycleStatus;
  dsoDays?: number;
}): Promise<CustomerSummary> =>
  axios.post<CustomerSummary>('/api/customers', payload).then((r) => r.data);

export const updateCustomer = (
  id: string,
  payload: {
    customerName?: string;
    lifecycleStatus?: LifecycleStatus;
    relationshipOwnerEmployeeId?: string;
    dsoDays?: number;
  },
): Promise<CustomerDetail> =>
  axios.put<CustomerDetail>(`/api/customers/${id}`, payload).then((r) => r.data);

// ── Rate Cards ───────────────────────────────────────────────────────────────

export const fetchRateCards = (customerId: string): Promise<RateCard[]> =>
  axios
    .get<RateCard[]>(`/api/customers/${customerId}/rate-cards`)
    .then((r) => r.data);

export const createRateCard = (
  customerId: string,
  payload: {
    name: string;
    rateCardType: RateCardType;
    currency: RateCurrency;
    effectiveFrom: string;
    lines: Array<{ jobLevel?: string; rateAmount: number }>;
  },
): Promise<RateCard> =>
  axios
    .post<RateCard>(`/api/customers/${customerId}/rate-cards`, payload)
    .then((r) => r.data);

// ── Project Codes ─────────────────────────────────────────────────────────────

export const fetchProjectCodes = (customerId: string): Promise<ProjectCode[]> =>
  axios
    .get<ProjectCode[]>(`/api/customers/${customerId}/project-codes`)
    .then((r) => r.data);

export const addProjectCode = (
  customerId: string,
  payload: { projectCode: string; description?: string },
): Promise<ProjectCode> =>
  axios
    .post<ProjectCode>(`/api/customers/${customerId}/project-codes`, payload)
    .then((r) => r.data);

export const deleteProjectCode = (
  customerId: string,
  codeId: string,
): Promise<void> =>
  axios
    .delete(`/api/customers/${customerId}/project-codes/${codeId}`)
    .then(() => undefined);

// ── Concentration Risk ────────────────────────────────────────────────────────

export const fetchConcentrationRiskConfig =
  (): Promise<ConcentrationRiskConfig> =>
    axios
      .get<ConcentrationRiskConfig>('/api/general/concentration-risk-config')
      .then((r) => r.data);

export const updateConcentrationRiskConfig = (
  singleClientThresholdPct: number,
): Promise<ConcentrationRiskConfig> =>
  axios
    .put<ConcentrationRiskConfig>('/api/general/concentration-risk-config', {
      singleClientThresholdPct,
    })
    .then((r) => r.data);
