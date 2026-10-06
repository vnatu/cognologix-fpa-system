package com.cognologix.fpa.bankrecon.dto;

import com.cognologix.fpa.bankrecon.domain.DebitCredit;
import com.cognologix.fpa.bankrecon.domain.HintVoucherScope;
import com.cognologix.fpa.bankrecon.domain.MappingSource;
import com.cognologix.fpa.bankrecon.domain.PatternType;
import com.cognologix.fpa.bankrecon.domain.ReconRunStatus;
import com.cognologix.fpa.bankrecon.domain.VoucherType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class BankReconDtos {

    private BankReconDtos() {}

    public record LedgerMappingResult(String transactionId, String ledgerName) {}

    public record BatchMappingResponse(List<LedgerMappingResult> mappings) {}

    public record TransactionResponse(
            UUID id,
            Instant transactionDate,
            String description,
            String normalisedDescription,
            BigDecimal amount,
            DebitCredit debitCredit,
            String referenceNo,
            LocalDate valueDate,
            String transactionBranch,
            BigDecimal runningBalance,
            VoucherType voucherType,
            String mappedLedger,
            MappingSource mappingSource,
            boolean excluded,
            boolean reviewed,
            int sortOrder
    ) {}

    public record RunResponse(
            UUID id,
            String runNumber,
            String statementNumber,
            String accountNumber,
            String bankLedgerName,
            String customerName,
            LocalDate statementPeriodStart,
            LocalDate statementPeriodEnd,
            BigDecimal openingBalance,
            BigDecimal closingBalance,
            String originalFilename,
            int totalTransactions,
            int mappedCount,
            int unmappedCount,
            int excludedCount,
            int exportCount,
            ReconRunStatus status,
            Instant createdAt,
            String createdBy,
            List<TransactionResponse> transactions,
            List<ExportResponse> exports
    ) {}

    public record ExportResponse(
            UUID id,
            int exportNumber,
            String filename,
            int transactionCount,
            Instant generatedAt,
            String generatedBy
    ) {}

    public record UpdateTransactionRequest(
            String ledgerName,
            VoucherType voucherType,
            Boolean excluded
    ) {}

    public record ExportRequest(List<UUID> transactionIds) {}

    public record LedgerResponse(
            UUID id,
            String ledgerName,
            String groupName,
            String accountingNature,
            boolean bankAccount,
            boolean active,
            boolean hasHint
    ) {}

    public record CreateLedgerRequest(
            String ledgerName,
            String groupName
    ) {}

    public record ImportCountResponse(int imported, int updated) {}

    public record AccountMappingRequest(
            String statementType,
            String identifier,
            String ledgerName,
            Boolean active
    ) {}

    public record AccountMappingResponse(
            UUID id,
            String statementType,
            String identifier,
            String ledgerName,
            boolean active,
            Instant createdAt,
            String createdBy,
            String warning
    ) {}

    public record LedgerHintRequest(
            String purpose,
            String keywords,
            String typicalAmount,
            String disambiguationNote
    ) {}

    public record LedgerHintResponse(
            UUID ledgerId,
            String purpose,
            String keywords,
            String typicalAmount,
            String disambiguationNote,
            boolean present
    ) {}

    public record MappingResponse(
            UUID id,
            String normalisedNarration,
            VoucherType voucherType,
            String ledgerName,
            int useCount,
            Instant lastUsedAt
    ) {}

    public record HintResponse(
            UUID id,
            String hintText,
            HintVoucherScope voucherType,
            boolean active,
            Instant createdAt
    ) {}

    public record HintRequest(String hintText, HintVoucherScope voucherType, Boolean active) {}

    public record ContraRuleResponse(
            UUID id,
            PatternType patternType,
            String patternValue,
            boolean active
    ) {}

    public record ContraRuleRequest(PatternType patternType, String patternValue) {}

    public record LlmProviderSettings(
            String baseUrl,
            String chatModel,
            String embeddingUrl,
            String embeddingModel,
            String apiKey
    ) {}

    public record OllamaConfigResponse(
            String baseUrl,
            String chatModel,
            String embeddingUrl,
            String embeddingModel,
            int batchSize,
            int timeoutSeconds,
            String companyName,
            String provider,
            String apiKey,
            LlmProviderSettings ollamaSettings,
            LlmProviderSettings omlxSettings
    ) {}

    public record OllamaConfigRequest(
            String baseUrl,
            String chatModel,
            String embeddingUrl,
            String embeddingModel,
            Integer batchSize,
            Integer timeoutSeconds,
            String companyName,
            String provider,
            String apiKey
    ) {}

    public record OllamaTestResponse(
            boolean connected,
            List<String> availableModels,
            boolean chatModelPulled,
            boolean embeddingModelPulled,
            String message
    ) {}

    public record MigrateResponse(int migrated, int skipped) {}

    public record PageResponse<T>(List<T> content, long totalElements, int page, int size) {}

    public record ParseHeadersResponse(
            List<String> headers,
            List<String> headerFields,
            List<String> transactionColumns,
            java.util.Map<String, String> headerFieldValues,
            int rowCount
    ) {}

    public record MappingLineRequest(String excelColumnName, String systemAttribute) {}

    public record CreateColumnMappingRequest(
            String importType,
            String templateName,
            List<MappingLineRequest> lines
    ) {}

    public record MappingLineResponse(UUID id, String excelColumnName, String systemAttribute) {}

    public record ColumnMappingTemplateResponse(
            UUID id,
            String importType,
            String templateName,
            boolean active,
            Instant createdAt,
            Instant updatedAt,
            List<MappingLineResponse> lines
    ) {
        public static ColumnMappingTemplateResponse from(com.cognologix.fpa.people.MappingTemplateApi api) {
            return new ColumnMappingTemplateResponse(
                    api.id(),
                    api.importType(),
                    api.templateName(),
                    api.active(),
                    api.createdAt(),
                    api.updatedAt(),
                    api.lines().stream()
                            .map(l -> new MappingLineResponse(l.id(), l.excelColumnName(), l.systemAttribute()))
                            .toList());
        }
    }
}
