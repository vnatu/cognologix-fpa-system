package com.cognologix.fpa.expenses.dto;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ExpenseDtos {

    private ExpenseDtos() {}

    public record CategoryResponse(
            UUID id,
            String lineCode,
            String categoryGroup,
            String displayName,
            String description,
            boolean active,
            int sortOrder
    ) {}

    public record CategoryGroupResponse(
            String categoryGroup,
            List<CategoryResponse> categories
    ) {}

    public record AddCategoryRequest(
            String lineCode,
            String categoryGroup,
            String displayName,
            String description
    ) {}

    public record ExpenseEntryResponse(
            UUID categoryId,
            String lineCode,
            String categoryGroup,
            String displayName,
            int sortOrder,
            BigDecimal amount,
            String notes,
            UUID actualId,
            Instant updatedAt,
            String updatedBy
    ) {}

    public record MonthlyExpensesResponse(
            int month,
            int year,
            boolean locked,
            Instant lockedAt,
            String lockedBy,
            List<ExpenseEntryResponse> entries
    ) {}

    public record ExpenseEntryRequest(
            UUID categoryId,
            BigDecimal amount,
            String notes
    ) {}

    public record SaveMonthlyExpensesRequest(
            List<ExpenseEntryRequest> entries
    ) {}

    public record UnlockMonthRequest(
            @NotBlank String reason
    ) {}

    public record MonthHistoryResponse(
            int month,
            int year,
            BigDecimal totalAmount,
            boolean locked
    ) {}

    public record ExpenseImportResponse(
            int totalRows,
            int created,
            int updated,
            int skipped,
            List<ExpenseImportRowError> errors
    ) {}

    public record ExpenseImportRowError(
            int rowNumber,
            String reason
    ) {}
}
