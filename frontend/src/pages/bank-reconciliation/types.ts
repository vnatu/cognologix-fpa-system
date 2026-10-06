export type VoucherType = 'PAYMENT' | 'RECEIPT' | 'CONTRA';
export type MappingSource = 'LEARNED' | 'LLM' | 'MANUAL' | null;
export type RunStatus = 'OPEN' | 'CLOSED';

export interface TransactionRow {
  id: string;
  transactionDate: string | null;
  description: string;
  normalisedDescription: string;
  amount: number;
  debitCredit: 'D' | 'C';
  referenceNo: string | null;
  valueDate: string | null;
  transactionBranch: string | null;
  runningBalance: number | null;
  voucherType: VoucherType;
  mappedLedger: string | null;
  mappingSource: MappingSource;
  excluded: boolean;
  reviewed: boolean;
  sortOrder: number;
}

export interface ReconExport {
  id: string;
  exportNumber: number;
  filename: string;
  transactionCount: number;
  generatedAt: string;
  generatedBy: string | null;
}

export interface ReconRun {
  id: string;
  runNumber: string;
  statementNumber: string | null;
  accountNumber: string | null;
  bankLedgerName: string | null;
  customerName: string | null;
  statementPeriodStart: string | null;
  statementPeriodEnd: string | null;
  openingBalance: number | null;
  closingBalance: number | null;
  originalFilename: string | null;
  totalTransactions: number;
  mappedCount: number;
  unmappedCount: number;
  excludedCount: number;
  exportCount: number;
  status: RunStatus;
  createdAt: string;
  createdBy: string | null;
  transactions: TransactionRow[];
  exports: ReconExport[];
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  page: number;
  size: number;
}

export interface Ledger {
  id: string;
  ledgerName: string;
  groupName: string;
  accountingNature: string;
  bankAccount: boolean;
  active: boolean;
  hasHint: boolean;
}

export interface LearnedMapping {
  id: string;
  normalisedNarration: string;
  voucherType: VoucherType;
  ledgerName: string;
  useCount: number;
  lastUsedAt: string;
}

export interface LlmHint {
  id: string;
  hintText: string;
  voucherType: 'ALL' | VoucherType;
  active: boolean;
  createdAt: string;
}

export interface ContraRule {
  id: string;
  patternType: 'PREFIX' | 'CONTAINS';
  patternValue: string;
  active: boolean;
}

export type LlmProvider = 'OLLAMA' | 'OMLX';

export interface LlmProviderSettings {
  baseUrl: string;
  chatModel: string;
  embeddingUrl: string;
  embeddingModel: string;
  apiKey: string;
}

export interface OllamaConfig {
  baseUrl: string;
  chatModel: string;
  embeddingUrl: string;
  embeddingModel: string;
  batchSize: number;
  timeoutSeconds: number;
  companyName: string;
  provider: LlmProvider;
  apiKey: string;
  ollamaSettings: LlmProviderSettings;
  omlxSettings: LlmProviderSettings;
}

export interface OllamaTestResult {
  connected: boolean;
  availableModels: string[];
  chatModelPulled: boolean;
  embeddingModelPulled: boolean;
  message: string;
}

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
  headerFields: string[];
  transactionColumns: string[];
  headerFieldValues: Record<string, string>;
  rowCount: number;
}

export type StatementType = 'HDFC_BANK' | 'HSBC_CC';

export interface AccountMapping {
  id: string;
  statementType: StatementType;
  identifier: string;
  ledgerName: string;
  active: boolean;
  createdAt: string;
  createdBy: string | null;
  warning: string | null;
}

export interface LedgerHint {
  ledgerId: string;
  purpose: string | null;
  keywords: string | null;
  typicalAmount: string | null;
  disambiguationNote: string | null;
  present: boolean;
}
