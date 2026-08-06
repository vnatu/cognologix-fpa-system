package com.cognologix.fpa.budgeting;

import com.cognologix.fpa.budgeting.domain.*;
import com.cognologix.fpa.budgeting.dto.PlanInputImportRowError;
import com.cognologix.fpa.customer.CustomerService;
import com.cognologix.fpa.customer.CustomerService.CustomerRef;
import com.cognologix.fpa.general.ExcelNumberParser;
import com.cognologix.fpa.general.ExcelParserUtils;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Excel export/import for Budgeting plan inputs (ADR-044 Tier 1).
 *
 * <p>Per-section export and import sample templates share the same Title Case headers
 * (no fiscal_year / type_name / version_number). Those context columns belong only in
 * the system backup ZIP ({@link BudgetingModuleBackup}). Header matching uses
 * {@link ExcelParserUtils#normalizeHeader} (ADR-047).
 */
@Component
@RequiredArgsConstructor
public class BudgetingExcelIO {

    static final String COL_MONTH = "Month";
    static final String COL_YEAR = "Year";
    static final String COL_PLANNED_HIRES = "Planned Hires";
    static final String COL_PLANNED_EXITS = "Planned Exits";
    static final String COL_PLANNED_BILLABLE_HC = "Planned Billable HC";
    static final String COL_PLANNED_BENCH_HC = "Planned Bench HC";
    static final String COL_PLANNED_SUPPORT_HC = "Planned Support HC";
    static final String COL_PLANNED_LEADERSHIP_HC = "Planned Leadership HC";
    static final String COL_PLANNED_MANAGEMENT_HC = "Planned Management HC";
    static final String COL_BILLABLE_SALARIES = "Billable Salaries";
    static final String COL_BENCH_SALARIES = "Bench Salaries";
    static final String COL_SUPPORT_SALARIES = "Support Salaries";
    static final String COL_COFOUNDERS_SALARIES = "Cofounders Salaries";
    static final String COL_SENIOR_MGMT_SALARIES = "Senior Mgmt Salaries";
    static final String COL_CUSTOMER_CODE = "Customer Code";
    static final String COL_CUSTOMER_NAME = "Customer Name";
    static final String COL_PLANNED_TM_REVENUE = "Planned TM Revenue";
    static final String COL_PLANNED_FIXED_BID_REVENUE = "Planned Fixed Bid Revenue";
    static final String COL_OVERHEAD_LINE = "Overhead Line";
    static final String COL_AMOUNT = "Amount";

    private static final String[] HC_PLAN_HEADERS = {
            COL_MONTH, COL_YEAR, COL_PLANNED_HIRES, COL_PLANNED_EXITS,
            COL_PLANNED_BILLABLE_HC, COL_PLANNED_BENCH_HC, COL_PLANNED_SUPPORT_HC,
            COL_PLANNED_LEADERSHIP_HC, COL_PLANNED_MANAGEMENT_HC
    };
    private static final String[] SALARY_BUDGET_HEADERS = {
            COL_MONTH, COL_YEAR, COL_BILLABLE_SALARIES, COL_BENCH_SALARIES,
            COL_SUPPORT_SALARIES, COL_COFOUNDERS_SALARIES, COL_SENIOR_MGMT_SALARIES
    };
    private static final String[] REVENUE_PLAN_HEADERS = {
            COL_MONTH, COL_YEAR, COL_CUSTOMER_CODE, COL_CUSTOMER_NAME,
            COL_PLANNED_TM_REVENUE, COL_PLANNED_FIXED_BID_REVENUE
    };
    private static final String[] OVERHEAD_BUDGET_HEADERS = {
            COL_MONTH, COL_YEAR, COL_OVERHEAD_LINE, COL_AMOUNT
    };

    private final CustomerService customerService;

    public byte[] exportHcPlan(List<HcPlan> rows) {
        List<HcPlan> sorted = rows.stream()
                .sorted(Comparator.comparingInt(HcPlan::getPlanYear).thenComparingInt(HcPlan::getPlanMonth))
                .toList();
        return writeWorkbook("HC Plan", HC_PLAN_HEADERS, sheet -> {
            int rowIdx = 1;
            for (HcPlan plan : sorted) {
                Row row = sheet.createRow(rowIdx++);
                int col = 0;
                row.createCell(col++).setCellValue(plan.getPlanMonth());
                row.createCell(col++).setCellValue(plan.getPlanYear());
                row.createCell(col++).setCellValue(plan.getPlannedHires());
                row.createCell(col++).setCellValue(plan.getPlannedExits());
                row.createCell(col++).setCellValue(plan.getPlannedBillableHc());
                row.createCell(col++).setCellValue(plan.getPlannedBenchHc());
                row.createCell(col++).setCellValue(plan.getPlannedSupportHc());
                row.createCell(col++).setCellValue(plan.getPlannedLeadershipHc());
                row.createCell(col).setCellValue(plan.getPlannedManagementHc());
            }
        });
    }

    public byte[] exportSalaryBudget(List<SalaryBudget> rows) {
        List<SalaryBudget> sorted = rows.stream()
                .sorted(Comparator.comparingInt(SalaryBudget::getPlanYear)
                        .thenComparingInt(SalaryBudget::getPlanMonth))
                .toList();
        return writeWorkbook("Salary Budget", SALARY_BUDGET_HEADERS, sheet -> {
            int rowIdx = 1;
            for (SalaryBudget budget : sorted) {
                Row row = sheet.createRow(rowIdx++);
                int col = 0;
                row.createCell(col++).setCellValue(budget.getPlanMonth());
                row.createCell(col++).setCellValue(budget.getPlanYear());
                setNumericCell(row, col++, budget.getBillableSalaries());
                setNumericCell(row, col++, budget.getBenchSalaries());
                setNumericCell(row, col++, budget.getSupportSalaries());
                setNumericCell(row, col++, budget.getCofoundersSalaries());
                setNumericCell(row, col, budget.getSeniorMgmtSalaries());
            }
        });
    }

    public byte[] exportRevenuePlan(List<ClientRevenuePlan> rows) {
        List<ClientRevenuePlan> sorted = rows.stream()
                .sorted(Comparator
                        .comparing((ClientRevenuePlan r) -> customerCode(r.getCustomerId()))
                        .thenComparingInt(ClientRevenuePlan::getPlanYear)
                        .thenComparingInt(ClientRevenuePlan::getPlanMonth))
                .toList();
        return writeWorkbook("Client Revenue Plan", REVENUE_PLAN_HEADERS, sheet -> {
            int rowIdx = 1;
            for (ClientRevenuePlan plan : sorted) {
                Row row = sheet.createRow(rowIdx++);
                int col = 0;
                CustomerRef ref = customerService.findCustomerRef(plan.getCustomerId()).orElse(null);
                row.createCell(col++).setCellValue(plan.getPlanMonth());
                row.createCell(col++).setCellValue(plan.getPlanYear());
                row.createCell(col++).setCellValue(ref != null ? ref.customerCode() : "");
                row.createCell(col++).setCellValue(ref != null ? ref.customerName() : "");
                setNumericCell(row, col++, plan.getPlannedTmRevenue());
                setNumericCell(row, col, plan.getPlannedFixedBidRevenue());
            }
        });
    }

    public byte[] exportOverheadBudget(List<OverheadBudget> rows) {
        List<OverheadBudget> sorted = rows.stream()
                .sorted(Comparator.comparingInt(OverheadBudget::getPlanYear)
                        .thenComparingInt(OverheadBudget::getPlanMonth)
                        .thenComparing(OverheadBudget::getOverheadLine))
                .toList();
        return writeWorkbook("Overhead Budget", OVERHEAD_BUDGET_HEADERS, sheet -> {
            int rowIdx = 1;
            for (OverheadBudget budget : sorted) {
                Row row = sheet.createRow(rowIdx++);
                int col = 0;
                row.createCell(col++).setCellValue(budget.getPlanMonth());
                row.createCell(col++).setCellValue(budget.getPlanYear());
                row.createCell(col++).setCellValue(budget.getOverheadLine());
                setNumericCell(row, col, budget.getAmount());
            }
        });
    }

    public byte[] buildHcPlanSample() {
        return buildHeadersOnly("HC Plan", HC_PLAN_HEADERS);
    }

    public byte[] buildSalaryBudgetSample() {
        return buildHeadersOnly("Salary Budget", SALARY_BUDGET_HEADERS);
    }

    public byte[] buildRevenuePlanSample() {
        return buildHeadersOnly("Client Revenue Plan", REVENUE_PLAN_HEADERS);
    }

    public byte[] buildOverheadBudgetSample() {
        return buildHeadersOnly("Overhead Budget", OVERHEAD_BUDGET_HEADERS);
    }

    public byte[] zipPlanInputs(byte[] hcPlan, byte[] salaryBudget, byte[] revenuePlan, byte[] overheadBudget) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(out)) {
            addZipEntry(zip, "hc_plan.xlsx", hcPlan);
            addZipEntry(zip, "salary_budget.xlsx", salaryBudget);
            addZipEntry(zip, "client_revenue_plan.xlsx", revenuePlan);
            addZipEntry(zip, "overhead_budget.xlsx", overheadBudget);
            zip.finish();
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to generate ZIP export: " + e.getMessage());
        }
    }

    public HcPlanParseResult parseHcPlan(MultipartFile file) {
        validateFileExtension(file);
        try (InputStream in = file.getInputStream(); Workbook workbook = WorkbookFactory.create(in)) {
            SheetContext ctx = sheetContext(workbook, HC_PLAN_HEADERS);
            List<HcPlan> rows = new ArrayList<>();
            List<PlanInputImportRowError> errors = new ArrayList<>();
            int totalRows = 0;
            for (int r = ctx.firstDataRow(); r <= ctx.sheet().getLastRowNum(); r++) {
                Row row = ctx.sheet().getRow(r);
                if (row == null || isBlankRow(row)) {
                    continue;
                }
                int rowNumber = r + 1;
                totalRows++;
                Integer month = parseMonth(row, ctx.columnIndex(), rowNumber, errors);
                Integer year = parseYear(row, ctx.columnIndex(), rowNumber, errors);
                if (month == null || year == null) {
                    continue;
                }
                Integer hires = parseNonNegativeInt(row, ctx.columnIndex(), COL_PLANNED_HIRES, rowNumber, errors);
                Integer exits = parseNonNegativeInt(row, ctx.columnIndex(), COL_PLANNED_EXITS, rowNumber, errors);
                Integer billable = parseNonNegativeInt(row, ctx.columnIndex(), COL_PLANNED_BILLABLE_HC, rowNumber, errors);
                Integer bench = parseNonNegativeInt(row, ctx.columnIndex(), COL_PLANNED_BENCH_HC, rowNumber, errors);
                Integer support = parseNonNegativeInt(row, ctx.columnIndex(), COL_PLANNED_SUPPORT_HC, rowNumber, errors);
                Integer leadership = parseNonNegativeInt(row, ctx.columnIndex(), COL_PLANNED_LEADERSHIP_HC, rowNumber, errors);
                Integer management = parseNonNegativeInt(row, ctx.columnIndex(), COL_PLANNED_MANAGEMENT_HC, rowNumber, errors);
                if (hires == null || exits == null || billable == null || bench == null
                        || support == null || leadership == null || management == null) {
                    continue;
                }
                rows.add(HcPlan.builder()
                        .planMonth(month)
                        .planYear(year)
                        .plannedHires(hires)
                        .plannedExits(exits)
                        .plannedBillableHc(billable)
                        .plannedBenchHc(bench)
                        .plannedSupportHc(support)
                        .plannedLeadershipHc(leadership)
                        .plannedManagementHc(management)
                        .build());
            }
            return new HcPlanParseResult(totalRows, rows, errors);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to parse Excel file: " + e.getMessage());
        }
    }

    public SalaryBudgetParseResult parseSalaryBudget(MultipartFile file) {
        validateFileExtension(file);
        try (InputStream in = file.getInputStream(); Workbook workbook = WorkbookFactory.create(in)) {
            SheetContext ctx = sheetContext(workbook, SALARY_BUDGET_HEADERS);
            List<SalaryBudget> rows = new ArrayList<>();
            List<PlanInputImportRowError> errors = new ArrayList<>();
            int totalRows = 0;
            for (int r = ctx.firstDataRow(); r <= ctx.sheet().getLastRowNum(); r++) {
                Row row = ctx.sheet().getRow(r);
                if (row == null || isBlankRow(row)) {
                    continue;
                }
                int rowNumber = r + 1;
                totalRows++;
                Integer month = parseMonth(row, ctx.columnIndex(), rowNumber, errors);
                Integer year = parseYear(row, ctx.columnIndex(), rowNumber, errors);
                if (month == null || year == null) {
                    continue;
                }
                BigDecimal billable = parseAmount(row, ctx.columnIndex(), COL_BILLABLE_SALARIES, rowNumber, errors);
                BigDecimal bench = parseAmount(row, ctx.columnIndex(), COL_BENCH_SALARIES, rowNumber, errors);
                BigDecimal support = parseAmount(row, ctx.columnIndex(), COL_SUPPORT_SALARIES, rowNumber, errors);
                BigDecimal cofounders = parseAmount(row, ctx.columnIndex(), COL_COFOUNDERS_SALARIES, rowNumber, errors);
                BigDecimal seniorMgmt = parseAmount(row, ctx.columnIndex(), COL_SENIOR_MGMT_SALARIES, rowNumber, errors);
                if (billable == null || bench == null || support == null || cofounders == null || seniorMgmt == null) {
                    continue;
                }
                rows.add(SalaryBudget.builder()
                        .planMonth(month)
                        .planYear(year)
                        .billableSalaries(billable)
                        .benchSalaries(bench)
                        .supportSalaries(support)
                        .cofoundersSalaries(cofounders)
                        .seniorMgmtSalaries(seniorMgmt)
                        .build());
            }
            return new SalaryBudgetParseResult(totalRows, rows, errors);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to parse Excel file: " + e.getMessage());
        }
    }

    public RevenuePlanParseResult parseRevenuePlan(MultipartFile file) {
        validateFileExtension(file);
        try (InputStream in = file.getInputStream(); Workbook workbook = WorkbookFactory.create(in)) {
            SheetContext ctx = sheetContext(workbook, REVENUE_PLAN_HEADERS);
            List<ClientRevenuePlan> rows = new ArrayList<>();
            List<PlanInputImportRowError> errors = new ArrayList<>();
            int totalRows = 0;
            for (int r = ctx.firstDataRow(); r <= ctx.sheet().getLastRowNum(); r++) {
                Row row = ctx.sheet().getRow(r);
                if (row == null || isBlankRow(row)) {
                    continue;
                }
                int rowNumber = r + 1;
                totalRows++;
                Integer month = parseMonth(row, ctx.columnIndex(), rowNumber, errors);
                Integer year = parseYear(row, ctx.columnIndex(), rowNumber, errors);
                String customerCode = cellValue(row, resolveColumn(ctx.columnIndex(), COL_CUSTOMER_CODE));
                if (month == null || year == null) {
                    continue;
                }
                if (customerCode == null || customerCode.isBlank()) {
                    errors.add(new PlanInputImportRowError(rowNumber, "Customer Code is required"));
                    continue;
                }
                var customerOpt = customerService.resolveBuCustomer(customerCode.trim());
                if (customerOpt.isEmpty()) {
                    errors.add(new PlanInputImportRowError(rowNumber, "Customer Code not found: " + customerCode));
                    continue;
                }
                BigDecimal tmRevenue = parseAmount(row, ctx.columnIndex(), COL_PLANNED_TM_REVENUE, rowNumber, errors);
                BigDecimal fixedBid = parseAmount(row, ctx.columnIndex(), COL_PLANNED_FIXED_BID_REVENUE, rowNumber, errors);
                if (tmRevenue == null || fixedBid == null) {
                    continue;
                }
                rows.add(ClientRevenuePlan.builder()
                        .customerId(customerOpt.get().id())
                        .planMonth(month)
                        .planYear(year)
                        .plannedTmRevenue(tmRevenue)
                        .plannedFixedBidRevenue(fixedBid)
                        .build());
            }
            return new RevenuePlanParseResult(totalRows, rows, errors);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to parse Excel file: " + e.getMessage());
        }
    }

    public OverheadBudgetParseResult parseOverheadBudget(MultipartFile file) {
        validateFileExtension(file);
        try (InputStream in = file.getInputStream(); Workbook workbook = WorkbookFactory.create(in)) {
            SheetContext ctx = sheetContext(workbook, OVERHEAD_BUDGET_HEADERS);
            List<OverheadBudget> rows = new ArrayList<>();
            List<PlanInputImportRowError> errors = new ArrayList<>();
            int totalRows = 0;
            for (int r = ctx.firstDataRow(); r <= ctx.sheet().getLastRowNum(); r++) {
                Row row = ctx.sheet().getRow(r);
                if (row == null || isBlankRow(row)) {
                    continue;
                }
                int rowNumber = r + 1;
                totalRows++;
                Integer month = parseMonth(row, ctx.columnIndex(), rowNumber, errors);
                Integer year = parseYear(row, ctx.columnIndex(), rowNumber, errors);
                String overheadLine = cellValue(row, resolveColumn(ctx.columnIndex(), COL_OVERHEAD_LINE));
                if (month == null || year == null) {
                    continue;
                }
                if (overheadLine == null || overheadLine.isBlank()) {
                    errors.add(new PlanInputImportRowError(rowNumber, "Overhead Line is required"));
                    continue;
                }
                if (overheadLine.length() > 100) {
                    errors.add(new PlanInputImportRowError(rowNumber, "Overhead Line must be at most 100 characters"));
                    continue;
                }
                BigDecimal amount = parseAmount(row, ctx.columnIndex(), COL_AMOUNT, rowNumber, errors);
                if (amount == null) {
                    continue;
                }
                rows.add(OverheadBudget.builder()
                        .planMonth(month)
                        .planYear(year)
                        .overheadLine(overheadLine.trim())
                        .amount(amount)
                        .build());
            }
            return new OverheadBudgetParseResult(totalRows, rows, errors);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to parse Excel file: " + e.getMessage());
        }
    }

    public record HcPlanParseResult(int totalRows, List<HcPlan> rows, List<PlanInputImportRowError> errors) {}
    public record SalaryBudgetParseResult(int totalRows, List<SalaryBudget> rows, List<PlanInputImportRowError> errors) {}
    public record RevenuePlanParseResult(int totalRows, List<ClientRevenuePlan> rows, List<PlanInputImportRowError> errors) {}
    public record OverheadBudgetParseResult(int totalRows, List<OverheadBudget> rows, List<PlanInputImportRowError> errors) {}

    private String customerCode(UUID customerId) {
        return customerService.findCustomerRef(customerId)
                .map(CustomerRef::customerCode)
                .orElse("");
    }

    private static void addZipEntry(ZipOutputStream zip, String name, byte[] content) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        zip.putNextEntry(entry);
        zip.write(content);
        zip.closeEntry();
    }

    private static byte[] buildHeadersOnly(String sheetName, String[] headers) {
        return writeWorkbook(sheetName, headers, sheet -> {});
    }

    @FunctionalInterface
    private interface SheetWriter {
        void write(Sheet sheet);
    }

    private static byte[] writeWorkbook(String sheetName, String[] headers, SheetWriter writer) {
        try (Workbook workbook = WorkbookFactory.create(true);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(sheetName);
            writeHeaderRow(sheet, headers);
            writer.write(sheet);
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to generate Excel file: " + e.getMessage());
        }
    }

    private static void writeHeaderRow(Sheet sheet, String[] headers) {
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            headerRow.createCell(i).setCellValue(headers[i]);
        }
    }

    private static void setNumericCell(Row row, int col, BigDecimal value) {
        if (value == null) {
            row.createCell(col).setBlank();
        } else {
            row.createCell(col).setCellValue(value.doubleValue());
        }
    }

    private static SheetContext sheetContext(Workbook workbook, String[] requiredHeaders) {
        Sheet sheet = workbook.getSheetAt(0);
        if (sheet == null || sheet.getPhysicalNumberOfRows() == 0) {
            throw new IllegalArgumentException("Excel file has no rows");
        }
        Row headerRow = sheet.getRow(sheet.getFirstRowNum());
        if (headerRow == null) {
            throw new IllegalArgumentException("Excel file has no header row");
        }
        Map<String, Integer> columnIndex = mapHeaders(headerRow);
        validateRequiredHeaders(columnIndex, requiredHeaders);
        return new SheetContext(sheet, columnIndex, sheet.getFirstRowNum() + 1);
    }

    private record SheetContext(Sheet sheet, Map<String, Integer> columnIndex, int firstDataRow) {}

    private static void validateFileExtension(MultipartFile file) {
        String name = file.getOriginalFilename();
        if (name == null) {
            throw new IllegalArgumentException("File name is required");
        }
        String lower = name.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".xlsx") && !lower.endsWith(".xls")) {
            throw new IllegalArgumentException("Only .xlsx and .xls files are supported");
        }
    }

    private static Map<String, Integer> mapHeaders(Row headerRow) {
        Map<String, Integer> columnIndex = new LinkedHashMap<>();
        for (Cell cell : headerRow) {
            String header = cellValueAsString(cell);
            if (header == null || header.isBlank()) {
                continue;
            }
            String normalized = ExcelParserUtils.normalizeHeader(header);
            columnIndex.putIfAbsent(normalized, cell.getColumnIndex());
            // Accept backup-style plan_month / plan_year as Month / Year (ADR-047).
            if ("plan_month".equals(normalized)) {
                columnIndex.putIfAbsent(ExcelParserUtils.normalizeHeader(COL_MONTH), cell.getColumnIndex());
            }
            if ("plan_year".equals(normalized)) {
                columnIndex.putIfAbsent(ExcelParserUtils.normalizeHeader(COL_YEAR), cell.getColumnIndex());
            }
        }
        return columnIndex;
    }

    private static void validateRequiredHeaders(Map<String, Integer> columnIndex, String[] requiredHeaders) {
        List<String> missing = new ArrayList<>();
        for (String header : requiredHeaders) {
            if (resolveColumn(columnIndex, header) == null) {
                missing.add(header);
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("Missing required columns: " + String.join(", ", missing));
        }
    }

    /** Resolve a canonical Title Case header via {@link ExcelParserUtils#normalizeHeader}. */
    private static Integer resolveColumn(Map<String, Integer> columnIndex, String header) {
        return columnIndex.get(ExcelParserUtils.normalizeHeader(header));
    }

    private static String cellValue(Row row, Integer columnIndex) {
        if (columnIndex == null) {
            return null;
        }
        return cellValueAsString(row.getCell(columnIndex));
    }

    private static Integer parseMonth(Row row, Map<String, Integer> columnIndex, int rowNumber,
                                      List<PlanInputImportRowError> errors) {
        String raw = cellValue(row, resolveColumn(columnIndex, COL_MONTH));
        Integer month = parsePositiveInt(raw);
        if (month == null || month < 1 || month > 12) {
            errors.add(new PlanInputImportRowError(rowNumber, "Month must be between 1 and 12"));
            return null;
        }
        return month;
    }

    private static Integer parseYear(Row row, Map<String, Integer> columnIndex, int rowNumber,
                                     List<PlanInputImportRowError> errors) {
        String raw = cellValue(row, resolveColumn(columnIndex, COL_YEAR));
        Integer year = parsePositiveInt(raw);
        if (year == null || year < 2000) {
            errors.add(new PlanInputImportRowError(rowNumber, "Year must be >= 2000"));
            return null;
        }
        return year;
    }

    private static Integer parseNonNegativeInt(Row row, Map<String, Integer> columnIndex, String header,
                                               int rowNumber, List<PlanInputImportRowError> errors) {
        String raw = cellValue(row, resolveColumn(columnIndex, header));
        if (raw == null || raw.isBlank()) {
            return 0;
        }
        try {
            Integer value = ExcelNumberParser.parseInteger(raw);
            if (value == null) {
                return 0;
            }
            if (value < 0) {
                errors.add(new PlanInputImportRowError(rowNumber, header + " must be >= 0"));
                return null;
            }
            return value;
        } catch (IllegalArgumentException e) {
            errors.add(new PlanInputImportRowError(rowNumber, "Invalid " + header + ": " + raw));
            return null;
        }
    }

    private static BigDecimal parseAmount(Row row, Map<String, Integer> columnIndex, String header,
                                          int rowNumber, List<PlanInputImportRowError> errors) {
        String raw = cellValue(row, resolveColumn(columnIndex, header));
        if (raw == null || raw.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            BigDecimal value = ExcelNumberParser.parseAmount(raw);
            if (value == null) {
                return BigDecimal.ZERO;
            }
            value = value.setScale(3, RoundingMode.HALF_UP);
            if (value.signum() < 0) {
                errors.add(new PlanInputImportRowError(rowNumber, header + " must be >= 0"));
                return null;
            }
            return value;
        } catch (IllegalArgumentException e) {
            errors.add(new PlanInputImportRowError(rowNumber, "Invalid " + header + ": " + raw));
            return null;
        }
    }

    private static Integer parsePositiveInt(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return ExcelNumberParser.parseInteger(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static boolean isBlankRow(Row row) {
        for (Cell cell : row) {
            String value = cellValueAsString(cell);
            if (value != null && !value.isBlank()) {
                return false;
            }
        }
        return true;
    }

    private static String cellValueAsString(Cell cell) {
        if (cell == null) {
            return null;
        }
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try {
                    yield cell.getStringCellValue().trim();
                } catch (IllegalStateException ex) {
                    yield formatNumeric(cell);
                }
            }
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield String.valueOf((long) cell.getNumericCellValue());
                }
                yield formatNumeric(cell);
            }
            case BLANK, _NONE -> null;
            default -> null;
        };
    }

    private static String formatNumeric(Cell cell) {
        double n = cell.getNumericCellValue();
        if (n == Math.rint(n) && !Double.isInfinite(n)) {
            return String.valueOf((long) n);
        }
        return String.valueOf(n);
    }
}
