package com.cognologix.fpa.budgeting;

import com.cognologix.fpa.budgeting.domain.*;
import com.cognologix.fpa.budgeting.dto.BudgetingDtos.*;
import com.cognologix.fpa.budgeting.dto.BudgetingRequests.*;
import com.cognologix.fpa.budgeting.dto.BudgetingResponses.*;
import com.cognologix.fpa.budgeting.dto.PlanInputImportResponse;
import com.cognologix.fpa.customer.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import com.cognologix.fpa.general.AdminOnly;

@RestController
@RequestMapping("/api/budgeting/plans")
@RequiredArgsConstructor
@Tag(name = "Budgeting & Forecasting", description = "AOP plans, rolling forecast, delta, Plan vs Actual")
public class BudgetingController {

    private final BudgetingService budgetingService;
    private final BudgetingReportService budgetingReportService;
    private final CustomerService customerService;

    @AdminOnly
    @PostMapping
    @Operation(summary = "Create financial year plan with NORMAL/AGGRESSIVE/CONSERVATIVE DRAFT v1")
    public ResponseEntity<PlanDetailResponse> createPlan(
            @Valid @RequestBody CreatePlanRequest req,
            Authentication auth) {
        var plan = budgetingService.createFinancialYearPlan(
                req.fiscalYear(), req.openingHc(), actor(auth),
                req.fiscalYearStart(), req.fiscalYearEnd());
        return ResponseEntity.status(HttpStatus.CREATED).body(PlanDetailResponse.from(plan));
    }

    @GetMapping
    @Operation(summary = "List all financial year plans")
    public List<PlanSummaryResponse> listPlans() {
        return budgetingService.listFinancialYearPlans().stream()
                .map(PlanSummaryResponse::from)
                .toList();
    }

    @GetMapping("/{planId}")
    @Operation(summary = "Get plan detail with forecast types and versions")
    public ResponseEntity<PlanDetailResponse> getPlan(@PathVariable UUID planId) {
        return budgetingService.getFinancialYearPlan(planId)
                .map(plan -> {
                    var latest = budgetingService.findLatestActualsMonth(plan.getId());
                    return PlanDetailResponse.from(
                            plan,
                            latest.map(YearMonth::getMonthValue).orElse(null),
                            latest.map(YearMonth::getYear).orElse(null));
                })
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{planId}/forecast-types")
    @Operation(summary = "List forecast types and their versions for a plan")
    public ResponseEntity<List<ForecastTypeResponse>> listForecastTypes(@PathVariable UUID planId) {
        if (budgetingService.getFinancialYearPlan(planId).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        List<ForecastType> types = budgetingService.listForecastTypes(planId);
        types.forEach(t -> Hibernate.initialize(t.getVersions()));
        return ResponseEntity.ok(types.stream().map(ForecastTypeResponse::from).toList());
    }

    @AdminOnly
    @PostMapping("/{planId}/forecast-types/{typeId}/versions/{versionId}/publish")
    @Operation(summary = "Publish DRAFT version (ACTIVE); prior ACTIVE → SUPERSEDED")
    public ForecastVersionResponse publishVersion(
            @PathVariable UUID planId,
            @PathVariable UUID typeId,
            @PathVariable UUID versionId,
            Authentication auth) {
        return ForecastVersionResponse.from(
                budgetingService.publishForecastVersion(planId, typeId, versionId, actor(auth)));
    }

    @AdminOnly
    @PostMapping("/{planId}/forecast-types/{typeId}/versions")
    @Operation(summary = "Create next DRAFT version as a copy of current ACTIVE inputs")
    public ResponseEntity<ForecastVersionResponse> createDraftVersion(
            @PathVariable UUID planId,
            @PathVariable UUID typeId,
            Authentication auth) {
        var version = budgetingService.createDraftVersion(planId, typeId, actor(auth));
        return ResponseEntity.status(HttpStatus.CREATED).body(ForecastVersionResponse.from(version));
    }

    @GetMapping("/{planId}/forecast-types/{typeId}/versions/{versionId}/hc-plan")
    @Operation(summary = "Get HC plan inputs for a version")
    public List<HcPlanMonthResponse> getHcPlan(
            @PathVariable UUID planId,
            @PathVariable UUID typeId,
            @PathVariable UUID versionId) {
        return budgetingService.getHcPlan(planId, typeId, versionId).stream()
                .map(HcPlanMonthResponse::from)
                .toList();
    }

    @AdminOnly
    @PutMapping("/{planId}/forecast-types/{typeId}/versions/{versionId}/hc-plan")
    @Operation(summary = "Upsert HC plan inputs (DRAFT only)")
    public ResponseEntity<Void> upsertHcPlan(
            @PathVariable UUID planId,
            @PathVariable UUID typeId,
            @PathVariable UUID versionId,
            @Valid @RequestBody UpsertHcPlanRequest req) {
        List<HcPlan> rows = req.months().stream()
                .map(m -> HcPlan.builder()
                        .planMonth(m.planMonth())
                        .planYear(m.planYear())
                        .plannedHires(m.plannedHires())
                        .plannedExits(m.plannedExits())
                        .plannedBillableHc(m.plannedBillableHc())
                        .plannedBenchHc(m.plannedBenchHc())
                        .plannedSupportHc(m.plannedSupportHc())
                        .plannedLeadershipHc(m.plannedLeadershipHc())
                        .plannedManagementHc(m.plannedManagementHc())
                        .build())
                .toList();
        budgetingService.upsertHcPlan(planId, typeId, versionId, rows);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{planId}/forecast-types/{typeId}/versions/{versionId}/salary-budget")
    @Operation(summary = "Get salary budget inputs for a version")
    public List<SalaryBudgetMonthResponse> getSalaryBudget(
            @PathVariable UUID planId,
            @PathVariable UUID typeId,
            @PathVariable UUID versionId) {
        return budgetingService.getSalaryBudget(planId, typeId, versionId).stream()
                .map(SalaryBudgetMonthResponse::from)
                .toList();
    }

    @AdminOnly
    @PutMapping("/{planId}/forecast-types/{typeId}/versions/{versionId}/salary-budget")
    @Operation(summary = "Upsert salary budget inputs (DRAFT only)")
    public ResponseEntity<Void> upsertSalaryBudget(
            @PathVariable UUID planId,
            @PathVariable UUID typeId,
            @PathVariable UUID versionId,
            @Valid @RequestBody UpsertSalaryBudgetRequest req) {
        List<SalaryBudget> rows = req.months().stream()
                .map(m -> SalaryBudget.builder()
                        .planMonth(m.planMonth())
                        .planYear(m.planYear())
                        .billableSalaries(m.billableSalaries())
                        .benchSalaries(m.benchSalaries())
                        .supportSalaries(m.supportSalaries())
                        .cofoundersSalaries(m.cofoundersSalaries())
                        .seniorMgmtSalaries(m.seniorMgmtSalaries())
                        .build())
                .toList();
        budgetingService.upsertSalaryBudget(planId, typeId, versionId, rows);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{planId}/forecast-types/{typeId}/versions/{versionId}/revenue-plan")
    @Operation(summary = "Get client revenue plan for a version")
    public List<ClientRevenuePlanResponse> getRevenuePlan(
            @PathVariable UUID planId,
            @PathVariable UUID typeId,
            @PathVariable UUID versionId) {
        return budgetingService.getRevenuePlan(planId, typeId, versionId).stream()
                .map(ClientRevenuePlanResponse::from)
                .toList();
    }

    @AdminOnly
    @PutMapping("/{planId}/forecast-types/{typeId}/versions/{versionId}/revenue-plan")
    @Operation(summary = "Upsert client revenue plan (DRAFT only)")
    public ResponseEntity<Void> upsertRevenuePlan(
            @PathVariable UUID planId,
            @PathVariable UUID typeId,
            @PathVariable UUID versionId,
            @Valid @RequestBody UpsertRevenuePlanRequest req) {
        for (ClientRevenueMonthRequest entry : req.entries()) {
            if (customerService.findCustomerRef(entry.customerId()).isEmpty()) {
                throw new IllegalArgumentException("Unknown customerId: " + entry.customerId());
            }
        }
        List<ClientRevenuePlan> rows = req.entries().stream()
                .map(e -> ClientRevenuePlan.builder()
                        .customerId(e.customerId())
                        .planMonth(e.planMonth())
                        .planYear(e.planYear())
                        .plannedTmRevenue(e.plannedTmRevenue())
                        .plannedFixedBidRevenue(e.plannedFixedBidRevenue())
                        .build())
                .toList();
        budgetingService.upsertRevenuePlan(planId, typeId, versionId, rows);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{planId}/forecast-types/{typeId}/versions/{versionId}/overhead-budget")
    @Operation(summary = "Get overhead budget for a version")
    public List<OverheadBudgetResponse> getOverheadBudget(
            @PathVariable UUID planId,
            @PathVariable UUID typeId,
            @PathVariable UUID versionId) {
        return budgetingService.getOverheadBudget(planId, typeId, versionId).stream()
                .map(OverheadBudgetResponse::from)
                .toList();
    }

    @AdminOnly
    @PutMapping("/{planId}/forecast-types/{typeId}/versions/{versionId}/overhead-budget")
    @Operation(summary = "Upsert overhead budget (DRAFT only)")
    public ResponseEntity<Void> upsertOverheadBudget(
            @PathVariable UUID planId,
            @PathVariable UUID typeId,
            @PathVariable UUID versionId,
            @Valid @RequestBody UpsertOverheadBudgetRequest req) {
        List<OverheadBudget> rows = req.entries().stream()
                .map(e -> OverheadBudget.builder()
                        .planMonth(e.planMonth())
                        .planYear(e.planYear())
                        .overheadLine(e.overheadLine())
                        .amount(e.amount())
                        .build())
                .toList();
        budgetingService.upsertOverheadBudget(planId, typeId, versionId, rows);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{planId}/forecast-types/{typeId}/versions/{versionId}/hc-plan/export")
    @Operation(summary = "Export HC plan inputs as Excel")
    public ResponseEntity<byte[]> exportHcPlan(
            @PathVariable UUID planId,
            @PathVariable UUID typeId,
            @PathVariable UUID versionId) {
        return excelAttachment(
                budgetingService.exportHcPlan(planId, typeId, versionId), "hc_plan_export.xlsx");
    }

    @GetMapping("/{planId}/forecast-types/{typeId}/versions/{versionId}/hc-plan/import/sample")
    @Operation(summary = "Download HC plan import template (headers only)")
    public ResponseEntity<byte[]> downloadHcPlanImportSample() {
        return excelAttachment(budgetingService.buildHcPlanImportSample(), "hc_plan_import_template.xlsx");
    }

    @AdminOnly
    @PostMapping(value = "/{planId}/forecast-types/{typeId}/versions/{versionId}/hc-plan/import",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Import HC plan inputs from Excel (DRAFT only, merge by month/year)")
    public PlanInputImportResponse importHcPlan(
            @PathVariable UUID planId,
            @PathVariable UUID typeId,
            @PathVariable UUID versionId,
            @RequestPart("file") MultipartFile file) {
        return budgetingService.importHcPlan(planId, typeId, versionId, file);
    }

    @GetMapping("/{planId}/forecast-types/{typeId}/versions/{versionId}/salary-budget/export")
    @Operation(summary = "Export salary budget inputs as Excel")
    public ResponseEntity<byte[]> exportSalaryBudget(
            @PathVariable UUID planId,
            @PathVariable UUID typeId,
            @PathVariable UUID versionId) {
        return excelAttachment(
                budgetingService.exportSalaryBudget(planId, typeId, versionId), "salary_budget_export.xlsx");
    }

    @GetMapping("/{planId}/forecast-types/{typeId}/versions/{versionId}/salary-budget/import/sample")
    @Operation(summary = "Download salary budget import template (headers only)")
    public ResponseEntity<byte[]> downloadSalaryBudgetImportSample() {
        return excelAttachment(
                budgetingService.buildSalaryBudgetImportSample(), "salary_budget_import_template.xlsx");
    }

    @AdminOnly
    @PostMapping(value = "/{planId}/forecast-types/{typeId}/versions/{versionId}/salary-budget/import",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Import salary budget inputs from Excel (DRAFT only, merge by month/year)")
    public PlanInputImportResponse importSalaryBudget(
            @PathVariable UUID planId,
            @PathVariable UUID typeId,
            @PathVariable UUID versionId,
            @RequestPart("file") MultipartFile file) {
        return budgetingService.importSalaryBudget(planId, typeId, versionId, file);
    }

    @GetMapping("/{planId}/forecast-types/{typeId}/versions/{versionId}/revenue-plan/export")
    @Operation(summary = "Export client revenue plan as Excel")
    public ResponseEntity<byte[]> exportRevenuePlan(
            @PathVariable UUID planId,
            @PathVariable UUID typeId,
            @PathVariable UUID versionId) {
        return excelAttachment(
                budgetingService.exportRevenuePlan(planId, typeId, versionId), "client_revenue_plan_export.xlsx");
    }

    @GetMapping("/{planId}/forecast-types/{typeId}/versions/{versionId}/revenue-plan/import/sample")
    @Operation(summary = "Download client revenue plan import template (headers only)")
    public ResponseEntity<byte[]> downloadRevenuePlanImportSample() {
        return excelAttachment(
                budgetingService.buildRevenuePlanImportSample(), "client_revenue_plan_import_template.xlsx");
    }

    @AdminOnly
    @PostMapping(value = "/{planId}/forecast-types/{typeId}/versions/{versionId}/revenue-plan/import",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Import client revenue plan from Excel (DRAFT only, merge by customer/month/year)")
    public PlanInputImportResponse importRevenuePlan(
            @PathVariable UUID planId,
            @PathVariable UUID typeId,
            @PathVariable UUID versionId,
            @RequestPart("file") MultipartFile file) {
        return budgetingService.importRevenuePlan(planId, typeId, versionId, file);
    }

    @GetMapping("/{planId}/forecast-types/{typeId}/versions/{versionId}/overhead-budget/export")
    @Operation(summary = "Export overhead budget as Excel")
    public ResponseEntity<byte[]> exportOverheadBudget(
            @PathVariable UUID planId,
            @PathVariable UUID typeId,
            @PathVariable UUID versionId) {
        return excelAttachment(
                budgetingService.exportOverheadBudget(planId, typeId, versionId), "overhead_budget_export.xlsx");
    }

    @GetMapping("/{planId}/forecast-types/{typeId}/versions/{versionId}/overhead-budget/import/sample")
    @Operation(summary = "Download overhead budget import template (headers only)")
    public ResponseEntity<byte[]> downloadOverheadBudgetImportSample() {
        return excelAttachment(
                budgetingService.buildOverheadBudgetImportSample(), "overhead_budget_import_template.xlsx");
    }

    @AdminOnly
    @PostMapping(value = "/{planId}/forecast-types/{typeId}/versions/{versionId}/overhead-budget/import",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Import overhead budget from Excel (DRAFT only, merge by month/year/line)")
    public PlanInputImportResponse importOverheadBudget(
            @PathVariable UUID planId,
            @PathVariable UUID typeId,
            @PathVariable UUID versionId,
            @RequestPart("file") MultipartFile file) {
        return budgetingService.importOverheadBudget(planId, typeId, versionId, file);
    }

    @GetMapping("/{planId}/forecast-types/{typeId}/versions/{versionId}/export-all")
    @Operation(summary = "Export all plan inputs as a ZIP archive")
    public ResponseEntity<byte[]> exportAllInputs(
            @PathVariable UUID planId,
            @PathVariable UUID typeId,
            @PathVariable UUID versionId) {
        return zipAttachment(
                budgetingService.exportAllInputs(planId, typeId, versionId), "plan_inputs_export.zip");
    }

    @GetMapping("/{planId}/rolling-forecast")
    @Operation(summary = "Rolling Forecast = actuals (finalised) + ACTIVE Normal plan (future)")
    public RollingForecastResult rollingForecast(
            @PathVariable UUID planId,
            @RequestParam(defaultValue = "ANNUAL") PeriodGranularity granularity,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer quarter) {
        return budgetingService.getRollingForecast(planId, granularity, month, year, quarter);
    }

    @GetMapping("/{planId}/delta")
    @Operation(summary = "Delta = Rolling Forecast − ACTIVE Normal Baseline")
    public DeltaResult delta(
            @PathVariable UUID planId,
            @RequestParam(defaultValue = "ANNUAL") PeriodGranularity granularity,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer quarter) {
        return budgetingService.getDelta(planId, granularity, month, year, quarter);
    }

    @GetMapping("/{planId}/plan-vs-actual")
    @Operation(summary = "Plan vs Actual vs Variance with quarterly and FY totals (optional forecastTypeId for Plan side)")
    public PlanVsActualResult planVsActual(
            @PathVariable UUID planId,
            @RequestParam(required = false) UUID forecastTypeId,
            @RequestParam(defaultValue = "ANNUAL") PeriodGranularity granularity,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer quarter) {
        return budgetingService.getPlanVsActual(
                planId, forecastTypeId, granularity, month, year, quarter);
    }

    @GetMapping("/{planId}/cost-per-employee")
    @Operation(summary = "Cost per employee (Full Absorption Layer 1–3) for a period (optional forecastTypeId if no actuals)")
    public CostPerEmployeeResult costPerEmployee(
            @PathVariable UUID planId,
            @RequestParam(defaultValue = "ANNUAL") PeriodGranularity granularity,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer quarter,
            @RequestParam(required = false) UUID forecastTypeId) {
        if (granularity == PeriodGranularity.MONTHLY) {
            if (month == null || year == null) {
                throw new IllegalArgumentException("month and year are required for MONTHLY granularity");
            }
            validateMonthYear(month, year);
        }
        return budgetingService.getCostPerEmployee(
                planId, granularity, month, year, quarter, forecastTypeId);
    }

    @GetMapping("/{planId}/bu-metrics")
    @Operation(summary = "Per-BU profitability metrics for a period (optional forecastTypeId for planned data)")
    public BuMetricsResult buMetrics(
            @PathVariable UUID planId,
            @RequestParam(defaultValue = "ANNUAL") PeriodGranularity granularity,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer quarter,
            @RequestParam(required = false) UUID forecastTypeId) {
        if (granularity == PeriodGranularity.MONTHLY) {
            if (month == null || year == null) {
                throw new IllegalArgumentException("month and year are required for MONTHLY granularity");
            }
            validateMonthYear(month, year);
        }
        return budgetingService.getBuMetrics(
                planId, granularity, month, year, quarter, forecastTypeId);
    }

    @GetMapping("/{planId}/bu-analysis")
    @Operation(summary = "BU Analysis — external/internal BU cost, revenue, and position breakdown")
    public BuAnalysisResult buAnalysis(
            @PathVariable UUID planId,
            @RequestParam(defaultValue = "MONTHLY") PeriodGranularity granularity,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer quarter) {
        if (granularity == PeriodGranularity.MONTHLY) {
            if (month == null || year == null) {
                throw new IllegalArgumentException("month and year are required for MONTHLY granularity");
            }
            validateMonthYear(month, year);
        }
        return budgetingService.getBuAnalysis(planId, granularity, month, year, quarter);
    }

    @GetMapping("/{planId}/reports/{reportType}")
    @Operation(summary = "Download analysis Excel report with How-to-Read sheet and formula comments")
    public ResponseEntity<byte[]> downloadReport(
            @PathVariable UUID planId,
            @PathVariable BudgetingReportService.ReportType reportType,
            @RequestParam(defaultValue = "ANNUAL") PeriodGranularity granularity,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer quarter,
            @RequestParam(required = false) UUID forecastTypeId) {
        if (granularity == PeriodGranularity.MONTHLY) {
            if (month == null || year == null) {
                throw new IllegalArgumentException("month and year are required for MONTHLY granularity");
            }
            validateMonthYear(month, year);
        }
        BudgetingReportService.ReportFile file = budgetingReportService.generate(
                planId, reportType, granularity, month, year, quarter, forecastTypeId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.filename() + "\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(file.bytes());
    }

    @AdminOnly
    @PutMapping("/{planId}/actuals/{month}/{year}/revenue")
    @Operation(summary = "Manual Override — enter revenue actuals when Zoho Books data is unavailable for the period")
    public PeriodActualsResponse upsertRevenueActuals(
            @PathVariable UUID planId,
            @PathVariable int month,
            @PathVariable int year,
            @Valid @RequestBody UpsertRevenueActualsRequest req,
            Authentication auth) {
        validateMonthYear(month, year);
        BigDecimal total = req.totalRevenue();
        List<ClientRevenueActual> byClient = null;
        if (req.byClient() != null && !req.byClient().isEmpty()) {
            byClient = req.byClient().stream()
                    .map(c -> {
                        if (customerService.findCustomerRef(c.customerId()).isEmpty()) {
                            throw new IllegalArgumentException("Unknown customerId: " + c.customerId());
                        }
                        return ClientRevenueActual.builder()
                                .customerId(c.customerId())
                                .actualRevenue(c.actualRevenue())
                                .build();
                    })
                    .toList();
            if (total == null) {
                total = byClient.stream()
                        .map(ClientRevenueActual::getActualRevenue)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
            }
        }
        if (total == null) {
            throw new IllegalArgumentException("totalRevenue or byClient is required");
        }
        var saved = budgetingService.upsertRevenueActuals(planId, month, year, total, byClient, actor(auth));
        return PeriodActualsResponse.from(saved);
    }

    @AdminOnly
    @PutMapping("/{planId}/actuals/{month}/{year}/overhead")
    @Operation(summary = "Enter overhead actuals manually per line item")
    public ResponseEntity<Void> upsertOverheadActuals(
            @PathVariable UUID planId,
            @PathVariable int month,
            @PathVariable int year,
            @Valid @RequestBody UpsertOverheadActualsRequest req,
            Authentication auth) {
        validateMonthYear(month, year);
        List<OverheadActuals> lines = req.lines().stream()
                .map(l -> OverheadActuals.builder()
                        .overheadLine(l.overheadLine())
                        .actualAmount(l.actualAmount())
                        .build())
                .toList();
        budgetingService.upsertOverheadActuals(planId, month, year, lines, actor(auth));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{planId}/actuals/{month}/{year}")
    @Operation(summary = "Get period_actuals snapshot for a month")
    public ResponseEntity<PeriodActualsResponse> getActuals(
            @PathVariable UUID planId,
            @PathVariable int month,
            @PathVariable int year) {
        validateMonthYear(month, year);
        return budgetingService.getPeriodActualsDetail(planId, month, year)
                .map(PeriodActualsResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    private static String actor(Authentication auth) {
        return auth != null ? auth.getName() : "system";
    }

    private static void validateMonthYear(int month, int year) {
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("month must be between 1 and 12");
        }
        if (year < 2000) {
            throw new IllegalArgumentException("year must be >= 2000");
        }
    }

    private static ResponseEntity<byte[]> excelAttachment(byte[] content, String filename) {
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(content);
    }

    private static ResponseEntity<byte[]> zipAttachment(byte[] content, String filename) {
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(content);
    }
}
