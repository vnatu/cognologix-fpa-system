package com.cognologix.fpa.expenses;

import com.cognologix.fpa.expenses.dto.ExpenseDtos.*;
import com.cognologix.fpa.general.AdminOnly;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/expenses")
@RequiredArgsConstructor
@Tag(name = "Expenses", description = "Monthly overhead expense actuals")
public class ExpenseController {

    private final ExpenseService expenseService;

    @GetMapping("/categories")
    @Operation(summary = "List active expense categories grouped by category_group")
    public List<CategoryGroupResponse> getCategories() {
        return expenseService.getCategories();
    }

    @GetMapping("/categories/all")
    @Operation(summary = "List all expense categories including inactive (Settings)")
    public List<CategoryResponse> listAllCategories() {
        return expenseService.listAllCategories();
    }

    @AdminOnly
    @PostMapping("/categories")
    @Operation(summary = "Add a new expense category")
    public ResponseEntity<CategoryResponse> addCategory(@RequestBody AddCategoryRequest req) {
        CategoryResponse created = expenseService.addCategory(
                req.lineCode(), req.categoryGroup(), req.displayName(), req.description());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @AdminOnly
    @PutMapping("/categories/{id}/deactivate")
    @Operation(summary = "Deactivate an expense category (soft delete)")
    public ResponseEntity<Void> deactivateCategory(@PathVariable UUID id) {
        expenseService.deactivateCategory(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/history")
    @Operation(summary = "List months with expense entries, totals, and lock status")
    public List<MonthHistoryResponse> getHistory() {
        return expenseService.getHistory();
    }

    @GetMapping("/export/sample")
    @Operation(summary = "Download sample import template with active categories")
    public ResponseEntity<byte[]> downloadSample() {
        return excelAttachment(expenseService.buildImportSample(), "expenses_import_template.xlsx");
    }

    @GetMapping("/{month}/{year}")
    @Operation(summary = "Get all expense entries for a month (zeros for missing categories)")
    public MonthlyExpensesResponse getMonthlyExpenses(
            @PathVariable int month, @PathVariable int year) {
        return expenseService.getMonthlyExpenses(month, year);
    }

    @AdminOnly
    @PutMapping("/{month}/{year}")
    @Operation(summary = "Save/upsert expense entries for a month (rejected if locked)")
    public ResponseEntity<Void> saveMonthlyExpenses(
            @PathVariable int month,
            @PathVariable int year,
            @RequestBody SaveMonthlyExpensesRequest req,
            Authentication auth) {
        expenseService.saveMonthlyExpenses(month, year, req.entries(), actor(auth));
        return ResponseEntity.noContent().build();
    }

    @AdminOnly
    @PostMapping("/{month}/{year}/lock")
    @Operation(summary = "Lock a month so entries cannot be edited")
    public ResponseEntity<Void> lockMonth(
            @PathVariable int month, @PathVariable int year, Authentication auth) {
        expenseService.lockMonth(month, year, actor(auth));
        return ResponseEntity.noContent().build();
    }

    @AdminOnly
    @PostMapping("/{month}/{year}/unlock")
    @Operation(summary = "Unlock a month (requires reason)")
    public ResponseEntity<Void> unlockMonth(
            @PathVariable int month,
            @PathVariable int year,
            @Valid @RequestBody UnlockMonthRequest req,
            Authentication auth) {
        expenseService.unlockMonth(month, year, actor(auth), req.reason());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{month}/{year}/export")
    @Operation(summary = "Export month's expenses as Excel")
    public ResponseEntity<byte[]> exportExpenses(
            @PathVariable int month, @PathVariable int year) {
        String filename = String.format("expenses_%02d_%d.xlsx", month, year);
        return excelAttachment(expenseService.exportExpenses(month, year), filename);
    }

    @AdminOnly
    @PostMapping(value = "/{month}/{year}/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Import expenses from Excel (rejected if locked)")
    public ExpenseImportResponse importExpenses(
            @PathVariable int month,
            @PathVariable int year,
            @RequestPart("file") MultipartFile file,
            Authentication auth) {
        return expenseService.importExpenses(file, month, year, actor(auth));
    }

    private static String actor(Authentication auth) {
        return auth != null ? auth.getName() : "system";
    }

    private static ResponseEntity<byte[]> excelAttachment(byte[] content, String filename) {
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(content);
    }
}
