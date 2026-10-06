package com.cognologix.fpa.contracts;

import com.cognologix.fpa.contracts.dto.DocumentMeta;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class ContractDtos {

    private ContractDtos() {}

    public record CreateContractRequest(
        @NotBlank String title,
        @NotNull UUID contractTypeId,
        @NotNull PaperType paperType,
        String customerId,
        String partyName,
        LocalDate effectiveDate,
        LocalDate expiryDate,
        boolean evergreen,
        ContractStatus status,
        UUID parentContractId,
        BigDecimal contractValue,
        String billingCurrency,
        String paymentTerms,
        List<Integer> reminderDaysOverride,
        String description,
        UUID ownerUserId
) {}

public record UpdateContractRequest(
        @NotBlank String title,
        @NotNull UUID contractTypeId,
        @NotNull PaperType paperType,
        String customerId,
        String partyName,
        LocalDate effectiveDate,
        LocalDate expiryDate,
        boolean evergreen,
        @NotNull ContractStatus status,
        UUID parentContractId,
        BigDecimal contractValue,
        String billingCurrency,
        String paymentTerms,
        List<Integer> reminderDaysOverride,
        String description,
        @NotNull UUID ownerUserId
) {}

public record AddVersionRequest(
        @NotBlank String versionLabel,
        String notes,
        VersionStatus status
) {}

public record UpdateVersionStatusRequest(@NotNull VersionStatus status) {}

public record CreateContractTypeRequest(
        @NotBlank String typeCode,
        @NotBlank String displayName,
        String description
) {}

public record UpdateContractTypeRequest(
        @NotBlank String displayName,
        String description,
        boolean active
) {}

public record CreateTemplateRequest(
        @NotNull UUID contractTypeId,
        @NotBlank String templateName,
        String description
) {}

public record ContractNotificationConfigRequest(
        @NotBlank String reminderDays,
        List<String> recipients
) {}

public record ContractSummary(
        UUID id,
        String contractNumber,
        String title,
        String partyDisplayName,
        String customerId,
        String partyName,
        UUID contractTypeId,
        String contractTypeCode,
        String contractTypeName,
        PaperType paperType,
        ContractStatus status,
        LocalDate effectiveDate,
        LocalDate expiryDate,
        boolean evergreen,
        Integer daysRemaining,
        BigDecimal contractValue,
        String billingCurrency,
        UUID ownerUserId,
        String ownerName,
        Instant createdAt
) {}

public record ContractDetail(
        UUID id,
        String contractNumber,
        String title,
        String partyDisplayName,
        String customerId,
        String partyName,
        UUID contractTypeId,
        String contractTypeCode,
        String contractTypeName,
        PaperType paperType,
        ContractStatus status,
        LocalDate effectiveDate,
        LocalDate expiryDate,
        boolean evergreen,
        Integer daysRemaining,
        UUID parentContractId,
        String parentContractNumber,
        String parentTitle,
        BigDecimal contractValue,
        String billingCurrency,
        String paymentTerms,
        List<Integer> reminderDaysOverride,
        String description,
        UUID ownerUserId,
        String ownerName,
        String ownerEmail,
        String createdBy,
        Instant createdAt,
        Instant updatedAt,
        String updatedBy,
        List<VersionResponse> versions,
        List<UpcomingNotification> upcomingNotifications,
        List<NotificationHistoryEntry> notificationHistory
) {}

public record VersionResponse(
        UUID id,
        UUID contractId,
        int versionNumber,
        String versionLabel,
        VersionStatus status,
        String notes,
        Instant uploadedAt,
        String uploadedBy,
        List<DocumentMeta> documents
) {}

public record ContractDocumentDownload(String filename, String contentType, byte[] fileData) {}

public record ContractDashboard(
        List<ContractSummary> expiringIn30Days,
        List<ContractSummary> expiringIn31To60Days,
        List<ContractSummary> expiringIn61To90Days,
        List<ContractSummary> recentlyAdded,
        List<CountByLabel> countByType,
        List<CountByLabel> countByStatus
) {}

public record CountByLabel(String label, long count) {}

public record ContractTypeResponse(
        UUID id,
        String typeCode,
        String displayName,
        String description,
        boolean active
) {}

public record TemplateSummary(
        UUID id,
        UUID contractTypeId,
        String templateName,
        String description,
        int versionNumber,
        String filename,
        String contentType,
        long fileSizeBytes,
        Instant uploadedAt,
        String uploadedBy
) {}

public record TemplateGroup(
        UUID contractTypeId,
        String typeCode,
        String displayName,
        List<TemplateSummary> templates
) {}

public record ContractNotificationConfig(String reminderDays, List<String> recipients) {}

public record UpcomingNotification(LocalDate notificationDate, int daysBeforeExpiry) {}

public record NotificationHistoryEntry(
        UUID id,
        ContractNotificationType notificationType,
        int daysBeforeExpiry,
        Instant sentAt,
        String recipients
) {}
}
