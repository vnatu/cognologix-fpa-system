package com.cognologix.fpa.expenses;

import com.cognologix.fpa.expenses.domain.ExpenseActual;
import com.cognologix.fpa.expenses.domain.ExpenseCategory;
import com.cognologix.fpa.expenses.domain.ExpenseMonthLock;
import com.cognologix.fpa.expenses.dto.ExpenseDtos.*;
import com.cognologix.fpa.expenses.repository.ExpenseActualRepository;
import com.cognologix.fpa.expenses.repository.ExpenseCategoryRepository;
import com.cognologix.fpa.expenses.repository.ExpenseMonthLockRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExpenseService {

    private static final Logger log = LoggerFactory.getLogger(ExpenseService.class);
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final ExpenseCategoryRepository categoryRepository;
    private final ExpenseActualRepository actualRepository;
    private final ExpenseMonthLockRepository monthLockRepository;
    private final ExpenseExcelIO expenseExcelIO;

    public List<CategoryGroupResponse> getCategories() {
        Map<String, List<CategoryResponse>> grouped = new LinkedHashMap<>();
        for (ExpenseCategory category : categoryRepository.findByActiveTrueOrderBySortOrderAsc()) {
            grouped.computeIfAbsent(category.getCategoryGroup(), g -> new ArrayList<>())
                    .add(toCategoryResponse(category));
        }
        return grouped.entrySet().stream()
                .map(e -> new CategoryGroupResponse(e.getKey(), e.getValue()))
                .toList();
    }

    public List<CategoryResponse> listAllCategories() {
        return categoryRepository.findAllByOrderBySortOrderAsc().stream()
                .map(this::toCategoryResponse)
                .toList();
    }

    @Transactional
    public CategoryResponse addCategory(String lineCode, String categoryGroup,
                                        String displayName, String description) {
        if (lineCode == null || lineCode.isBlank()) {
            throw new IllegalArgumentException("Line code is required");
        }
        if (categoryGroup == null || categoryGroup.isBlank()) {
            throw new IllegalArgumentException("Category group is required");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("Display name is required");
        }
        String code = lineCode.trim();
        if (code.length() > 100) {
            throw new IllegalArgumentException("Line code must be at most 100 characters");
        }
        if (categoryRepository.existsByLineCodeIgnoreCase(code)) {
            throw new IllegalArgumentException("Category already exists with line code: " + code);
        }

        ExpenseCategory category = ExpenseCategory.builder()
                .lineCode(code)
                .categoryGroup(categoryGroup.trim())
                .displayName(displayName.trim())
                .description(blankToNull(description))
                .active(true)
                .sortOrder(categoryRepository.findMaxSortOrder() + 1)
                .build();
        return toCategoryResponse(categoryRepository.save(category));
    }

    @Transactional
    public void deactivateCategory(UUID id) {
        ExpenseCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Category not found: " + id));
        category.setActive(false);
        categoryRepository.save(category);
    }

    public MonthlyExpensesResponse getMonthlyExpenses(int month, int year) {
        validateMonthYear(month, year);
        List<ExpenseCategory> categories = categoryRepository.findByActiveTrueOrderBySortOrderAsc();
        Map<UUID, ExpenseActual> existing = actualRepository.findByMonthAndYearWithCategory(month, year)
                .stream()
                .collect(Collectors.toMap(a -> a.getExpenseCategory().getId(), a -> a, (a, b) -> a));

        ExpenseMonthLock lock = monthLockRepository.findByExpenseMonthAndExpenseYear(month, year)
                .orElse(null);
        boolean locked = lock != null && lock.isCurrentlyLocked();

        List<ExpenseEntryResponse> entries = categories.stream()
                .map(cat -> {
                    ExpenseActual actual = existing.get(cat.getId());
                    return new ExpenseEntryResponse(
                            cat.getId(),
                            cat.getLineCode(),
                            cat.getCategoryGroup(),
                            cat.getDisplayName(),
                            cat.getSortOrder(),
                            actual != null ? actual.getAmount() : ZERO,
                            actual != null ? actual.getNotes() : null,
                            actual != null ? actual.getId() : null,
                            actual != null ? actual.getUpdatedAt() : null,
                            actual != null ? actual.getUpdatedBy() : null
                    );
                })
                .toList();

        return new MonthlyExpensesResponse(
                month, year, locked,
                locked ? lock.getLockedAt() : null,
                locked ? lock.getLockedBy() : null,
                entries);
    }

    @Transactional
    public void saveMonthlyExpenses(int month, int year, List<ExpenseEntryRequest> entries, String updatedBy) {
        validateMonthYear(month, year);
        rejectIfLocked(month, year);

        if (entries == null || entries.isEmpty()) {
            throw new IllegalArgumentException("At least one expense entry is required");
        }

        for (ExpenseEntryRequest entry : entries) {
            if (entry.categoryId() == null) {
                throw new IllegalArgumentException("categoryId is required");
            }
            ExpenseCategory category = categoryRepository.findById(entry.categoryId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Category not found: " + entry.categoryId()));
            if (!category.isActive()) {
                throw new IllegalArgumentException(
                        "Cannot save amount for inactive category: " + category.getLineCode());
            }

            BigDecimal amount = entry.amount() != null ? entry.amount() : ZERO;
            if (amount.compareTo(ZERO) < 0) {
                throw new IllegalArgumentException("Amount must be >= 0 for " + category.getLineCode());
            }
            String notes = blankToNull(entry.notes());
            if (notes != null && notes.length() > 500) {
                throw new IllegalArgumentException("Notes must be at most 500 characters");
            }

            ExpenseActual target = actualRepository
                    .findByExpenseMonthAndExpenseYearAndExpenseCategoryId(month, year, category.getId())
                    .orElse(null);
            if (target == null) {
                target = ExpenseActual.builder()
                        .expenseMonth(month)
                        .expenseYear(year)
                        .expenseCategory(category)
                        .amount(amount)
                        .notes(notes)
                        .locked(false)
                        .updatedBy(updatedBy)
                        .build();
            } else {
                target.setAmount(amount);
                target.setNotes(notes);
                target.setUpdatedBy(updatedBy);
            }
            actualRepository.save(target);
        }
    }

    @Transactional
    public void lockMonth(int month, int year, String lockedBy) {
        validateMonthYear(month, year);
        if (lockedBy == null || lockedBy.isBlank()) {
            throw new IllegalArgumentException("lockedBy is required");
        }

        ExpenseMonthLock existing = monthLockRepository.findByExpenseMonthAndExpenseYear(month, year)
                .orElse(null);
        if (existing != null && existing.isCurrentlyLocked()) {
            throw new IllegalStateException("Month " + month + "/" + year + " is already locked");
        }

        if (existing == null) {
            existing = ExpenseMonthLock.builder()
                    .expenseMonth(month)
                    .expenseYear(year)
                    .lockedBy(lockedBy.trim())
                    .lockedAt(Instant.now())
                    .build();
        } else {
            existing.setLockedBy(lockedBy.trim());
            existing.setLockedAt(Instant.now());
            existing.setUnlockedAt(null);
            existing.setUnlockedBy(null);
            existing.setUnlockReason(null);
        }
        monthLockRepository.save(existing);

        for (ExpenseActual actual : actualRepository.findByExpenseMonthAndExpenseYear(month, year)) {
            actual.setLocked(true);
            actualRepository.save(actual);
        }
        log.info("Expenses month locked: {}/{} by {}", month, year, lockedBy);
    }

    @Transactional
    public void unlockMonth(int month, int year, String unlockedBy, String reason) {
        validateMonthYear(month, year);
        if (unlockedBy == null || unlockedBy.isBlank()) {
            throw new IllegalArgumentException("unlockedBy is required");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Unlock reason is required");
        }

        ExpenseMonthLock lock = monthLockRepository.findByExpenseMonthAndExpenseYear(month, year)
                .orElseThrow(() -> new IllegalStateException(
                        "Month " + month + "/" + year + " is not locked"));
        if (!lock.isCurrentlyLocked()) {
            throw new IllegalStateException("Month " + month + "/" + year + " is not locked");
        }

        lock.setUnlockedAt(Instant.now());
        lock.setUnlockedBy(unlockedBy.trim());
        lock.setUnlockReason(reason.trim());
        monthLockRepository.save(lock);

        for (ExpenseActual actual : actualRepository.findByExpenseMonthAndExpenseYear(month, year)) {
            actual.setLocked(false);
            actualRepository.save(actual);
        }
        log.info("Expenses month unlocked: {}/{} by {} reason={}", month, year, unlockedBy, reason);
    }

    /**
     * Public API for Budgeting & Forecasting (ADR-050). Returns line_code → amount for the month.
     * Empty map when no expense_actual rows exist — Budgeting treats that as zero overhead.
     */
    public Map<String, BigDecimal> getMonthlyExpenseActuals(int month, int year) {
        validateMonthYear(month, year);
        List<ExpenseActual> actuals = actualRepository.findByMonthAndYearWithCategory(month, year);
        if (actuals.isEmpty()) {
            return Map.of();
        }
        Map<String, BigDecimal> map = new LinkedHashMap<>();
        for (ExpenseActual actual : actuals) {
            map.put(actual.getExpenseCategory().getLineCode(), nullSafe(actual.getAmount()));
        }
        return map;
    }

    public byte[] exportExpenses(int month, int year) {
        MonthlyExpensesResponse monthly = getMonthlyExpenses(month, year);
        List<ExpenseExcelIO.ExportRow> rows = monthly.entries().stream()
                .map(e -> new ExpenseExcelIO.ExportRow(
                        e.lineCode(), e.displayName(), e.amount(), e.notes()))
                .toList();
        return expenseExcelIO.exportMonth(rows);
    }

    public byte[] buildImportSample() {
        List<ExpenseExcelIO.ExportRow> rows = categoryRepository.findByActiveTrueOrderBySortOrderAsc()
                .stream()
                .map(c -> new ExpenseExcelIO.ExportRow(c.getLineCode(), c.getDisplayName(), ZERO, null))
                .toList();
        return expenseExcelIO.exportMonth(rows);
    }

    @Transactional
    public ExpenseImportResponse importExpenses(MultipartFile file, int month, int year, String updatedBy) {
        validateMonthYear(month, year);
        rejectIfLocked(month, year);

        ExpenseExcelIO.ParseResult parsed = expenseExcelIO.parse(file);
        List<ExpenseImportRowError> errors = new ArrayList<>(parsed.errors());
        int created = 0;
        int updated = 0;
        int skipped = errors.size();

        List<ExpenseEntryRequest> toSave = new ArrayList<>();
        for (ExpenseExcelIO.ParsedRow row : parsed.rows()) {
            Optional<ExpenseCategory> categoryOpt = categoryRepository.findByLineCodeIgnoreCase(row.lineCode());
            if (categoryOpt.isEmpty()) {
                errors.add(new ExpenseImportRowError(row.rowNumber(),
                        "Unknown Category Code: " + row.lineCode()));
                skipped++;
                continue;
            }
            ExpenseCategory category = categoryOpt.get();
            if (!category.isActive()) {
                errors.add(new ExpenseImportRowError(row.rowNumber(),
                        "Category is inactive: " + row.lineCode()));
                skipped++;
                continue;
            }

            boolean exists = actualRepository
                    .findByExpenseMonthAndExpenseYearAndExpenseCategoryId(month, year, category.getId())
                    .isPresent();
            if (exists) {
                updated++;
            } else {
                created++;
            }
            toSave.add(new ExpenseEntryRequest(category.getId(), row.amount(), row.notes()));
        }

        if (!toSave.isEmpty()) {
            saveMonthlyExpenses(month, year, toSave, updatedBy);
        }

        return new ExpenseImportResponse(parsed.totalRows(), created, updated, skipped, errors);
    }

    public List<MonthHistoryResponse> getHistory() {
        List<Object[]> totals = actualRepository.findMonthYearTotals();
        List<MonthHistoryResponse> history = new ArrayList<>();
        for (Object[] row : totals) {
            int year = ((Number) row[0]).intValue();
            int month = ((Number) row[1]).intValue();
            BigDecimal total = row[2] != null ? (BigDecimal) row[2] : ZERO;
            boolean locked = monthLockRepository.findByExpenseMonthAndExpenseYear(month, year)
                    .map(ExpenseMonthLock::isCurrentlyLocked)
                    .orElse(false);
            history.add(new MonthHistoryResponse(month, year, total, locked));
        }
        return history;
    }

    private void rejectIfLocked(int month, int year) {
        monthLockRepository.findByExpenseMonthAndExpenseYear(month, year)
                .filter(ExpenseMonthLock::isCurrentlyLocked)
                .ifPresent(lock -> {
                    throw new IllegalStateException(
                            "Expenses for " + month + "/" + year + " are locked and cannot be modified");
                });
    }

    private void validateMonthYear(int month, int year) {
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("Month must be between 1 and 12");
        }
        if (year < 2000 || year > 2100) {
            throw new IllegalArgumentException("Year is out of range");
        }
    }

    private CategoryResponse toCategoryResponse(ExpenseCategory category) {
        return new CategoryResponse(
                category.getId(),
                category.getLineCode(),
                category.getCategoryGroup(),
                category.getDisplayName(),
                category.getDescription(),
                category.isActive(),
                category.getSortOrder());
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static BigDecimal nullSafe(BigDecimal value) {
        return value != null ? value : ZERO;
    }
}
