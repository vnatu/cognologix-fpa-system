export type PaperType = 'THIRD_PARTY' | 'OWN';
export type ContractStatus = 'DRAFT' | 'ACTIVE' | 'EXPIRED' | 'TERMINATED';
export type VersionStatus = 'DRAFT' | 'UNDER_REVIEW' | 'SIGNED' | 'SUPERSEDED';
export type DocumentType = 'PRIMARY' | 'SUPPORTING';
export type NotificationChannel = 'EMAIL' | 'IN_APP';

export interface ContractSummary {
  id: string;
  contractNumber: string;
  title: string;
  partyDisplayName: string | null;
  customerId: string | null;
  partyName: string | null;
  contractTypeId: string;
  contractTypeCode: string;
  contractTypeName: string;
  paperType: PaperType;
  status: ContractStatus;
  effectiveDate: string | null;
  expiryDate: string | null;
  evergreen: boolean;
  daysRemaining: number | null;
  contractValue: number | null;
  billingCurrency: string | null;
  ownerUserId: string;
  ownerName: string | null;
  createdAt: string;
}

export interface DocumentMeta {
  id: string;
  contractVersionId: string;
  documentType: DocumentType;
  filename: string;
  contentType: string;
  fileSizeBytes: number;
  uploadedAt: string;
  uploadedBy: string;
}

export interface VersionResponse {
  id: string;
  contractId: string;
  versionNumber: number;
  versionLabel: string;
  status: VersionStatus;
  notes: string | null;
  uploadedAt: string;
  uploadedBy: string;
  documents: DocumentMeta[];
}

export interface UpcomingNotification {
  notificationDate: string;
  daysBeforeExpiry: number;
}

export interface NotificationHistoryEntry {
  id: string;
  notificationType: NotificationChannel;
  daysBeforeExpiry: number;
  sentAt: string;
  recipients: string | null;
}

export interface ContractDetail extends ContractSummary {
  parentContractId: string | null;
  parentContractNumber: string | null;
  parentTitle: string | null;
  paymentTerms: string | null;
  reminderDaysOverride: number[] | null;
  description: string | null;
  ownerEmail: string | null;
  createdBy: string;
  updatedAt: string | null;
  updatedBy: string | null;
  versions: VersionResponse[];
  upcomingNotifications: UpcomingNotification[];
  notificationHistory: NotificationHistoryEntry[];
}

export interface ContractPage {
  content: ContractSummary[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export interface CountByLabel {
  label: string;
  count: number;
}

export interface ContractDashboard {
  expiringIn30Days: ContractSummary[];
  expiringIn31To60Days: ContractSummary[];
  expiringIn61To90Days: ContractSummary[];
  recentlyAdded: ContractSummary[];
  countByType: CountByLabel[];
  countByStatus: CountByLabel[];
}

export interface ContractType {
  id: string;
  typeCode: string;
  displayName: string;
  description: string | null;
  active: boolean;
}

export interface TemplateSummary {
  id: string;
  contractTypeId: string;
  templateName: string;
  description: string | null;
  versionNumber: number;
  filename: string | null;
  contentType: string | null;
  fileSizeBytes: number;
  uploadedAt: string;
  uploadedBy: string;
}

export interface TemplateGroup {
  contractTypeId: string;
  typeCode: string;
  displayName: string;
  templates: TemplateSummary[];
}

export interface ContractNotificationConfig {
  reminderDays: string;
  recipients: string[];
}

export interface ContractPayload {
  title: string;
  contractTypeId: string;
  paperType: PaperType;
  customerId?: string | null;
  partyName?: string | null;
  effectiveDate?: string | null;
  expiryDate?: string | null;
  evergreen: boolean;
  status: ContractStatus;
  parentContractId?: string | null;
  contractValue?: number | null;
  billingCurrency?: string | null;
  paymentTerms?: string | null;
  reminderDaysOverride?: number[] | null;
  description?: string | null;
  ownerUserId?: string | null;
}

export interface AppNotification {
  id: string;
  title: string;
  message: string;
  link: string | null;
  createdAt: string;
}
