package com.cognologix.fpa.budgeting;

import com.cognologix.fpa.budgeting.domain.PeriodGranularity;
import com.cognologix.fpa.budgeting.dto.BudgetingDtos.*;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Analysis Excel report generation with formula transparency.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BudgetingReportService {

    public enum ReportType {
        ROLLING_FORECAST,
        PLAN_VS_ACTUAL,
        DELTA,
        PL_SUMMARY,
        COST_PER_EMPLOYEE,
        BU_ANALYSIS
    }

    private final BudgetingService budgetingService;

    public record ReportFile(String filename, byte[] bytes) {}

    public ReportFile generate(
            UUID planId,
            ReportType type,
            PeriodGranularity granularity,
            Integer month,
            Integer year,
            Integer quarter,
            UUID forecastTypeId) {
        PeriodGranularity g = granularity != null ? granularity : PeriodGranularity.ANNUAL;
        return switch (type) {
            case ROLLING_FORECAST -> rollingForecast(planId, g, month, year, quarter);
            case PLAN_VS_ACTUAL -> planVsActual(planId, g, month, year, quarter, forecastTypeId);
            case DELTA -> delta(planId, g, month, year, quarter);
            case PL_SUMMARY -> plSummary(planId, g, month, year, quarter, forecastTypeId);
            case COST_PER_EMPLOYEE -> costPerEmployee(planId, g, month, year, quarter, forecastTypeId);
            case BU_ANALYSIS -> buAnalysis(planId, g, month, year, quarter);
        };
    }

    private ReportFile rollingForecast(
            UUID planId, PeriodGranularity g, Integer month, Integer year, Integer quarter) {
        RollingForecastResult data = budgetingService.getRollingForecast(planId, g, month, year, quarter);
        int monthsWithActuals = (int) data.months().stream().filter(MonthlyFinancials::fromActuals).count();
        byte[] bytes = BudgetingReportExcelSupport.writeWorkbook(wb -> {
            var styles = BudgetingReportExcelSupport.createStyles(wb);
            BudgetingReportExcelSupport.writeHowToReadSheet(
                    wb, styles, periodLabel(data.periodLabel(), g), monthsWithActuals, data.months().size());
            Sheet sheet = wb.createSheet("Rolling Forecast");
            writeMonthlyFinancialsSheet(sheet, styles, data.months(), true);
        });
        return new ReportFile(filename("rolling_forecast", data.periodLabel()), bytes);
    }

    private ReportFile planVsActual(
            UUID planId, PeriodGranularity g, Integer month, Integer year, Integer quarter, UUID forecastTypeId) {
        PlanVsActualResult data = budgetingService.getPlanVsActual(
                planId, forecastTypeId, g, month, year, quarter);
        int monthsWithActuals = data.monthsWithActuals() != null
                ? data.monthsWithActuals()
                : (int) data.months().stream().filter(MonthlyPlanVsActual::hasActuals).count();
        int totalMonths = data.totalMonthsInFy() != null ? data.totalMonthsInFy() : data.months().size();
        byte[] bytes = BudgetingReportExcelSupport.writeWorkbook(wb -> {
            var styles = BudgetingReportExcelSupport.createStyles(wb);
            BudgetingReportExcelSupport.writeHowToReadSheet(
                    wb, styles, periodLabel(data.periodLabel(), g), monthsWithActuals, totalMonths);
            Sheet sheet = wb.createSheet("Plan vs Actual");
            writePlanVsActualSheet(sheet, styles, data);
        });
        return new ReportFile(filename("plan_vs_actual", data.periodLabel()), bytes);
    }

    private ReportFile delta(
            UUID planId, PeriodGranularity g, Integer month, Integer year, Integer quarter) {
        DeltaResult data = budgetingService.getDelta(planId, g, month, year, quarter);
        int monthsWithActuals = (int) data.months().stream().filter(MonthlyFinancials::fromActuals).count();
        byte[] bytes = BudgetingReportExcelSupport.writeWorkbook(wb -> {
            var styles = BudgetingReportExcelSupport.createStyles(wb);
            BudgetingReportExcelSupport.writeHowToReadSheet(
                    wb, styles, periodLabel(data.periodLabel(), g), monthsWithActuals, data.months().size());
            Sheet sheet = wb.createSheet("Delta");
            writeMonthlyFinancialsSheet(sheet, styles, data.months(), false);
            if (data.periodTotal() != null) {
                writePeriodTotalRow(sheet, styles, data.periodTotal(), data.months().size() + 2);
            }
        });
        return new ReportFile(filename("delta", data.periodLabel()), bytes);
    }

    private ReportFile plSummary(
            UUID planId, PeriodGranularity g, Integer month, Integer year, Integer quarter, UUID forecastTypeId) {
        PlanVsActualResult data = budgetingService.getPlanVsActual(
                planId, forecastTypeId, g, month, year, quarter);
        int monthsWithActuals = data.monthsWithActuals() != null
                ? data.monthsWithActuals()
                : (int) data.months().stream().filter(MonthlyPlanVsActual::hasActuals).count();
        int totalMonths = data.totalMonthsInFy() != null ? data.totalMonthsInFy() : data.months().size();
        byte[] bytes = BudgetingReportExcelSupport.writeWorkbook(wb -> {
            var styles = BudgetingReportExcelSupport.createStyles(wb);
            BudgetingReportExcelSupport.writeHowToReadSheet(
                    wb, styles, periodLabel(data.periodLabel(), g), monthsWithActuals, totalMonths);
            Sheet sheet = wb.createSheet("P&L Summary");
            writePlSummarySheet(sheet, styles, data);
        });
        return new ReportFile(filename("pl_summary", data.periodLabel()), bytes);
    }

    private ReportFile costPerEmployee(
            UUID planId, PeriodGranularity g, Integer month, Integer year, Integer quarter, UUID forecastTypeId) {
        CostPerEmployeeResult data = budgetingService.getCostPerEmployee(
                planId, g, month, year, quarter, forecastTypeId);
        byte[] bytes = BudgetingReportExcelSupport.writeWorkbook(wb -> {
            var styles = BudgetingReportExcelSupport.createStyles(wb);
            BudgetingReportExcelSupport.writeHowToReadSheet(
                    wb, styles, periodLabel(data.periodLabel(), g), data.fromActuals() ? 1 : 0, 1);
            Sheet sheet = wb.createSheet("Cost per Employee");
            writeCostPerEmployeeSheet(sheet, styles, data);
        });
        return new ReportFile(filename("cost_per_employee", data.periodLabel()), bytes);
    }

    private ReportFile buAnalysis(
            UUID planId, PeriodGranularity g, Integer month, Integer year, Integer quarter) {
        BuAnalysisResult data = budgetingService.getBuAnalysis(planId, g, month, year, quarter);
        byte[] bytes = BudgetingReportExcelSupport.writeWorkbook(wb -> {
            var styles = BudgetingReportExcelSupport.createStyles(wb);
            BudgetingReportExcelSupport.writeHowToReadSheet(
                    wb, styles, periodLabel(data.periodLabel(), g),
                    data.totalCompanyHc() > 0 ? 1 : 0, 1);
            Sheet external = wb.createSheet("External BUs");
            writeExternalBuSheet(external, styles, data);
            Sheet internal = wb.createSheet("Internal BUs");
            writeInternalBuSheet(internal, styles, data);
        });
        return new ReportFile(filename("bu_analysis", data.periodLabel()), bytes);
    }

    private void writeMonthlyFinancialsSheet(
            Sheet sheet, BudgetingReportExcelSupport.Styles styles,
            List<MonthlyFinancials> months, boolean rolling) {
        String[] headers = {
                "Month", "Year", "From Actuals", "Total Revenue", "Total Payroll Cost",
                "COGS", "Gross Profit", "Gross Margin %", "OpEx", "EBITDA", "EBITDA Margin %"
        };
        Row header = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell c = header.createCell(i);
            c.setCellValue(headers[i]);
            c.setCellStyle(styles.header());
        }
        int r = 1;
        for (MonthlyFinancials m : months) {
            Row row = sheet.createRow(r++);
            row.createCell(0).setCellValue(m.month());
            row.createCell(1).setCellValue(m.year());
            row.createCell(2).setCellValue(m.fromActuals() ? "Y" : (rolling ? "Plan" : "N"));
            setMoneyWithComment(row, 3, m.totalRevenue(), BudgetingFormulaCatalog.COMMENT_TOTAL_REVENUE, sheet, styles);
            setMoneyWithComment(row, 4, m.totalSalaryCost(), BudgetingFormulaCatalog.COMMENT_TOTAL_PAYROLL, sheet, styles);
            setMoneyWithComment(row, 5, m.totalCogs(), BudgetingFormulaCatalog.COMMENT_COGS, sheet, styles);
            setMoneyWithComment(row, 6, m.grossProfit(), BudgetingFormulaCatalog.COMMENT_GROSS_PROFIT, sheet, styles);
            setPctWithComment(row, 7, marginPct(m.grossProfit(), m.totalRevenue()),
                    BudgetingFormulaCatalog.COMMENT_GROSS_MARGIN_PCT, sheet);
            setMoneyWithComment(row, 8, m.totalOpex(), BudgetingFormulaCatalog.COMMENT_OPEX, sheet, styles);
            setMoneyWithComment(row, 9, m.ebitda(), BudgetingFormulaCatalog.COMMENT_EBITDA, sheet, styles);
            setPctWithComment(row, 10, marginPct(m.ebitda(), m.totalRevenue()),
                    BudgetingFormulaCatalog.COMMENT_EBITDA_MARGIN_PCT, sheet);
        }
        autosize(sheet, headers.length);
    }

    private void writePeriodTotalRow(
            Sheet sheet, BudgetingReportExcelSupport.Styles styles, MonthlyFinancials total, int rowIdx) {
        Row row = sheet.createRow(rowIdx);
        Cell label = row.createCell(0);
        label.setCellValue("Period Total");
        label.setCellStyle(styles.header());
        setMoneyWithComment(row, 3, total.totalRevenue(), BudgetingFormulaCatalog.COMMENT_TOTAL_REVENUE, sheet, styles);
        setMoneyWithComment(row, 4, total.totalSalaryCost(), BudgetingFormulaCatalog.COMMENT_TOTAL_PAYROLL, sheet, styles);
        setMoneyWithComment(row, 5, total.totalCogs(), BudgetingFormulaCatalog.COMMENT_COGS, sheet, styles);
        setMoneyWithComment(row, 6, total.grossProfit(), BudgetingFormulaCatalog.COMMENT_GROSS_PROFIT, sheet, styles);
        setMoneyWithComment(row, 8, total.totalOpex(), BudgetingFormulaCatalog.COMMENT_OPEX, sheet, styles);
        setMoneyWithComment(row, 9, total.ebitda(), BudgetingFormulaCatalog.COMMENT_EBITDA, sheet, styles);
    }

    private void writePlanVsActualSheet(
            Sheet sheet, BudgetingReportExcelSupport.Styles styles, PlanVsActualResult data) {
        String[] headers = {
                "Month", "Year", "Has Actuals",
                "Revenue Plan", "Revenue Actual",
                "Payroll Plan", "Payroll Actual",
                "COGS Plan", "COGS Actual",
                "Gross Profit Plan", "Gross Profit Actual",
                "EBITDA Plan", "EBITDA Actual"
        };
        Row header = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell c = header.createCell(i);
            c.setCellValue(headers[i]);
            c.setCellStyle(styles.header());
        }
        int r = 1;
        for (MonthlyPlanVsActual m : data.months()) {
            Row row = sheet.createRow(r++);
            row.createCell(0).setCellValue(m.month());
            row.createCell(1).setCellValue(m.year());
            row.createCell(2).setCellValue(m.hasActuals() ? "Y" : "N");
            setMoneyWithComment(row, 3, m.totalRevenue().plan(), BudgetingFormulaCatalog.COMMENT_TOTAL_REVENUE, sheet, styles);
            setMoneyWithComment(row, 4, m.totalRevenue().actual(), BudgetingFormulaCatalog.COMMENT_TOTAL_REVENUE, sheet, styles);
            setMoneyWithComment(row, 5, m.totalSalaryCost().plan(), BudgetingFormulaCatalog.COMMENT_TOTAL_PAYROLL, sheet, styles);
            setMoneyWithComment(row, 6, m.totalSalaryCost().actual(), BudgetingFormulaCatalog.COMMENT_TOTAL_PAYROLL, sheet, styles);
            setMoneyWithComment(row, 7, m.totalCogs().plan(), BudgetingFormulaCatalog.COMMENT_COGS, sheet, styles);
            setMoneyWithComment(row, 8, m.totalCogs().actual(), BudgetingFormulaCatalog.COMMENT_COGS, sheet, styles);
            setMoneyWithComment(row, 9, m.grossProfit().plan(), BudgetingFormulaCatalog.COMMENT_GROSS_PROFIT, sheet, styles);
            setMoneyWithComment(row, 10, m.grossProfit().actual(), BudgetingFormulaCatalog.COMMENT_GROSS_PROFIT, sheet, styles);
            setMoneyWithComment(row, 11, m.ebitda().plan(), BudgetingFormulaCatalog.COMMENT_EBITDA, sheet, styles);
            setMoneyWithComment(row, 12, m.ebitda().actual(), BudgetingFormulaCatalog.COMMENT_EBITDA, sheet, styles);
        }
        autosize(sheet, headers.length);
    }

    private void writePlSummarySheet(
            Sheet sheet, BudgetingReportExcelSupport.Styles styles, PlanVsActualResult data) {
        PeriodTotals t = data.selectedPeriod();
        String[] headers = {"Metric", "Plan", "Actual", "Variance"};
        Row header = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell c = header.createCell(i);
            c.setCellValue(headers[i]);
            c.setCellStyle(styles.header());
        }
        writePlRow(sheet, styles, 1, "Total Revenue", t.totalRevenue(), BudgetingFormulaCatalog.COMMENT_TOTAL_REVENUE);
        writePlRow(sheet, styles, 2, "Total Payroll Cost", t.totalSalaryCost(), BudgetingFormulaCatalog.COMMENT_TOTAL_PAYROLL);
        writePlRow(sheet, styles, 3, "COGS", t.totalCogs(), BudgetingFormulaCatalog.COMMENT_COGS);
        writePlRow(sheet, styles, 4, "Gross Profit", t.grossProfit(), BudgetingFormulaCatalog.COMMENT_GROSS_PROFIT);

        Row gm = sheet.createRow(5);
        gm.createCell(0).setCellValue("Gross Margin %");
        BigDecimal gmPlan = marginPct(t.grossProfit().plan(), t.totalRevenue().plan());
        BigDecimal gmActual = t.totalRevenue().actual() != null
                ? marginPct(t.grossProfit().actual(), t.totalRevenue().actual()) : null;
        setPctWithComment(gm, 1, gmPlan, BudgetingFormulaCatalog.COMMENT_GROSS_MARGIN_PCT, sheet);
        setPctWithComment(gm, 2, gmActual, BudgetingFormulaCatalog.COMMENT_GROSS_MARGIN_PCT, sheet);

        // OpEx = GP − EBITDA
        MoneyTriad opex = new MoneyTriad(
                t.grossProfit().plan().subtract(t.ebitda().plan()),
                t.grossProfit().actual() != null && t.ebitda().actual() != null
                        ? t.grossProfit().actual().subtract(t.ebitda().actual()) : null,
                t.grossProfit().variance() != null && t.ebitda().variance() != null
                        ? t.grossProfit().variance().subtract(t.ebitda().variance()) : null);
        writePlRow(sheet, styles, 6, "OpEx", opex, BudgetingFormulaCatalog.COMMENT_OPEX);
        writePlRow(sheet, styles, 7, "EBITDA", t.ebitda(), BudgetingFormulaCatalog.COMMENT_EBITDA);

        Row em = sheet.createRow(8);
        em.createCell(0).setCellValue("EBITDA Margin %");
        setPctWithComment(em, 1, marginPct(t.ebitda().plan(), t.totalRevenue().plan()),
                BudgetingFormulaCatalog.COMMENT_EBITDA_MARGIN_PCT, sheet);
        setPctWithComment(em, 2,
                t.totalRevenue().actual() != null
                        ? marginPct(t.ebitda().actual(), t.totalRevenue().actual()) : null,
                BudgetingFormulaCatalog.COMMENT_EBITDA_MARGIN_PCT, sheet);
        autosize(sheet, 4);
    }

    private void writePlRow(
            Sheet sheet, BudgetingReportExcelSupport.Styles styles, int r, String label, MoneyTriad triad, String comment) {
        Row row = sheet.createRow(r);
        row.createCell(0).setCellValue(label);
        setMoneyWithComment(row, 1, triad.plan(), comment, sheet, styles);
        setMoneyWithComment(row, 2, triad.actual(), comment, sheet, styles);
        setMoneyWithComment(row, 3, triad.variance(), comment, sheet, styles);
    }

    private void writeCostPerEmployeeSheet(
            Sheet sheet, BudgetingReportExcelSupport.Styles styles, CostPerEmployeeResult data) {
        String[] headers = {
                "Category", "Headcount", "Gross Pay / Head", "Employer Contrib / Head",
                "Layer 1", "Layer 2", "Layer 3", "Total Cost / Head"
        };
        Row header = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell c = header.createCell(i);
            c.setCellValue(headers[i]);
            c.setCellStyle(styles.header());
        }
        writeCategoryRow(sheet, styles, 1, data.billable());
        writeCategoryRow(sheet, styles, 2, data.bench());
        writeCategoryRow(sheet, styles, 3, data.support());
        writeCategoryRow(sheet, styles, 4, data.leadership());

        Row min = sheet.createRow(6);
        min.createCell(0).setCellValue("Minimum Billing Rate");
        setMoneyWithComment(min, 1, data.totalCostPerBillableHead(),
                BudgetingFormulaCatalog.COMMENT_MIN_BILLING_RATE, sheet, styles);
        autosize(sheet, headers.length);
    }

    private void writeCategoryRow(Sheet sheet, BudgetingReportExcelSupport.Styles styles, int r, CategoryCost cat) {
        Row row = sheet.createRow(r);
        row.createCell(0).setCellValue(cat.category());
        row.createCell(1).setCellValue(cat.headcount());
        setMoney(row, 2, cat.grossPayPerHead(), styles);
        setMoney(row, 3, cat.employerContributionsPerHead(), styles);
        setMoneyWithComment(row, 4, cat.layer1(),
                "Layer 1 = Direct salary + employer contributions (or 13% estimate on plan months) per head.",
                sheet, styles);
        setMoneyWithComment(row, 5, cat.layer2(),
                "Layer 2 = Direct overhead per head (medical, welfare, consumables, software, training).",
                sheet, styles);
        setMoneyWithComment(row, 6, cat.layer3(),
                "Layer 3 = Shared overhead costs (rent, electricity, internet etc.) allocated entirely to billable employees — since billable revenue funds all fixed costs, each billable employee absorbs their proportional share of company overhead.",
                sheet, styles);
        setMoneyWithComment(row, 7, cat.total(),
                BudgetingFormulaCatalog.COMMENT_MIN_BILLING_RATE, sheet, styles);
    }

    private void writeExternalBuSheet(
            Sheet sheet, BudgetingReportExcelSupport.Styles styles, BuAnalysisResult data) {
        String[] headers = {
                "Client", "Total HC", "Billable HC", "Non-Billable HC",
                "Total Payroll Cost", "Actual Revenue", "Gross Margin", "Gross Margin %",
                "BU Cost % of Total", "BU Revenue % of Total"
        };
        Row header = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell c = header.createCell(i);
            c.setCellValue(headers[i]);
            c.setCellStyle(styles.header());
        }
        int r = 1;
        for (ExternalBuAnalysisRow bu : data.externalBUs()) {
            Row row = sheet.createRow(r++);
            row.createCell(0).setCellValue(bu.customerName());
            row.createCell(1).setCellValue(bu.totalHc());
            row.createCell(2).setCellValue(bu.billableHc());
            row.createCell(3).setCellValue(bu.nonBillableHc());
            setMoneyWithComment(row, 4, bu.totalPayrollCost(), BudgetingFormulaCatalog.COMMENT_TOTAL_PAYROLL, sheet, styles);
            setMoneyWithComment(row, 5, bu.actualRevenue(), BudgetingFormulaCatalog.COMMENT_TOTAL_REVENUE, sheet, styles);
            setMoneyWithComment(row, 6, bu.grossMargin(), BudgetingFormulaCatalog.COMMENT_BU_GROSS_MARGIN, sheet, styles);
            setPctWithComment(row, 7, bu.grossMarginPct(), BudgetingFormulaCatalog.COMMENT_GROSS_MARGIN_PCT, sheet);
            setPctWithComment(row, 8, bu.buCostPctOfTotal(),
                    "BU Cost % of Total = this BU's Total Payroll Cost ÷ company Total Payroll Cost × 100.",
                    sheet);
            setPctWithComment(row, 9, bu.buRevenuePctOfTotal(),
                    "BU Revenue % of Total = this BU's Actual Revenue ÷ company Total Revenue × 100.",
                    sheet);
        }
        autosize(sheet, headers.length);
    }

    private void writeInternalBuSheet(
            Sheet sheet, BudgetingReportExcelSupport.Styles styles, BuAnalysisResult data) {
        String[] headers = {
                "Business Unit", "Total HC", "Billable HC", "Non-Billable HC",
                "Total Payroll Cost", "Cost % of Total"
        };
        Row header = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell c = header.createCell(i);
            c.setCellValue(headers[i]);
            c.setCellStyle(styles.header());
        }
        int r = 1;
        for (InternalBuAnalysisRow bu : data.internalBUs()) {
            Row row = sheet.createRow(r++);
            row.createCell(0).setCellValue(bu.customerName());
            row.createCell(1).setCellValue(bu.totalHc());
            row.createCell(2).setCellValue(bu.billableHc());
            row.createCell(3).setCellValue(bu.nonBillableHc());
            setMoneyWithComment(row, 4, bu.totalPayrollCost(), BudgetingFormulaCatalog.COMMENT_TOTAL_PAYROLL, sheet, styles);
            setPctWithComment(row, 5, bu.buCostPctOfTotal(),
                    "BU Cost % of Total = this internal BU's cost ÷ company Total Payroll Cost × 100.",
                    sheet);
        }
        autosize(sheet, headers.length);
    }

    private static void setMoney(Row row, int col, BigDecimal value, BudgetingReportExcelSupport.Styles styles) {
        Cell cell = row.createCell(col);
        if (value != null) {
            cell.setCellValue(value.doubleValue());
        }
        cell.setCellStyle(styles.money());
    }

    private static void setMoneyWithComment(
            Row row, int col, BigDecimal value, String comment, Sheet sheet,
            BudgetingReportExcelSupport.Styles styles) {
        Cell cell = row.createCell(col);
        if (value != null) {
            cell.setCellValue(value.doubleValue());
        }
        cell.setCellStyle(styles.money());
        BudgetingReportExcelSupport.addComment(sheet, cell, comment);
    }

    private static void setPctWithComment(Row row, int col, BigDecimal value, String comment, Sheet sheet) {
        Cell cell = row.createCell(col);
        if (value != null) {
            cell.setCellValue(value.doubleValue());
        }
        BudgetingReportExcelSupport.addComment(sheet, cell, comment);
    }

    private static BigDecimal marginPct(BigDecimal part, BigDecimal whole) {
        if (part == null || whole == null || whole.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return part.multiply(new BigDecimal("100")).divide(whole, 2, RoundingMode.HALF_UP);
    }

    private static void autosize(Sheet sheet, int cols) {
        for (int i = 0; i < cols; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private static String periodLabel(String label, PeriodGranularity g) {
        return "Report period: " + label + " (" + g.name() + ")";
    }

    private static String filename(String prefix, String periodLabel) {
        String safe = periodLabel == null ? "report"
                : periodLabel.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
        return prefix + "_" + safe + ".xlsx";
    }
}
