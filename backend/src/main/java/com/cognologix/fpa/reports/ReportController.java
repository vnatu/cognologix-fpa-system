package com.cognologix.fpa.reports;

import com.cognologix.fpa.budgeting.domain.PeriodGranularity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Standard Reports download endpoints — Admin + Viewer (read-only).
 */
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@Tag(name = "Reports")
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/pl")
    @Operation(summary = "Monthly P&L Excel report")
    public ResponseEntity<byte[]> pl(
            @RequestParam UUID planId,
            @RequestParam(defaultValue = "MONTHLY") PeriodGranularity granularity,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer quarter) {
        return excel(reportService.generate(
                ReportService.ReportKind.PL, planId, granularity, month, year, quarter));
    }

    @GetMapping("/bu-margin")
    @Operation(summary = "BU Gross Margin Excel report")
    public ResponseEntity<byte[]> buMargin(
            @RequestParam UUID planId,
            @RequestParam(defaultValue = "MONTHLY") PeriodGranularity granularity,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer quarter) {
        return excel(reportService.generate(
                ReportService.ReportKind.BU_MARGIN, planId, granularity, month, year, quarter));
    }

    @GetMapping("/headcount")
    @Operation(summary = "Headcount Summary Excel report")
    public ResponseEntity<byte[]> headcount(
            @RequestParam UUID planId,
            @RequestParam(defaultValue = "MONTHLY") PeriodGranularity granularity,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer quarter) {
        return excel(reportService.generate(
                ReportService.ReportKind.HEADCOUNT, planId, granularity, month, year, quarter));
    }

    @GetMapping("/cost-per-employee")
    @Operation(summary = "Cost per Employee Excel report")
    public ResponseEntity<byte[]> costPerEmployee(
            @RequestParam UUID planId,
            @RequestParam(defaultValue = "MONTHLY") PeriodGranularity granularity,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer quarter) {
        return excel(reportService.generate(
                ReportService.ReportKind.COST_PER_EMPLOYEE, planId, granularity, month, year, quarter));
    }

    @GetMapping("/rolling-forecast")
    @Operation(summary = "Rolling Forecast vs Baseline Excel report (always 12 months)")
    public ResponseEntity<byte[]> rollingForecast(@RequestParam UUID planId) {
        return excel(reportService.generate(
                ReportService.ReportKind.ROLLING_FORECAST, planId, PeriodGranularity.ANNUAL, null, null, null));
    }

    @GetMapping("/expense-summary")
    @Operation(summary = "Expense Summary Excel report")
    public ResponseEntity<byte[]> expenseSummary(
            @RequestParam UUID planId,
            @RequestParam(defaultValue = "MONTHLY") PeriodGranularity granularity,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer quarter) {
        return excel(reportService.generate(
                ReportService.ReportKind.EXPENSE_SUMMARY, planId, granularity, month, year, quarter));
    }

    private static ResponseEntity<byte[]> excel(ReportService.ReportFile file) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.filename() + "\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(file.bytes());
    }
}
