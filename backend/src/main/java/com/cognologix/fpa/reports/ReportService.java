package com.cognologix.fpa.reports;

import com.cognologix.fpa.budgeting.BudgetingService;
import com.cognologix.fpa.budgeting.domain.OverheadLineItem;
import com.cognologix.fpa.budgeting.domain.PeriodGranularity;
import com.cognologix.fpa.budgeting.dto.BudgetingDtos.*;
import com.cognologix.fpa.people.PeoplePayrollService;
import com.cognologix.fpa.people.PeoplePayrollService.MasterRecordFact;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xddf.usermodel.PresetColor;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Standard Excel report generation — gathers data from Budgeting and People public APIs.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportService {

    public enum ReportKind {
        PL, BU_MARGIN, HEADCOUNT, COST_PER_EMPLOYEE, ROLLING_FORECAST, EXPENSE_SUMMARY
    }

    public record ReportFile(String filename, byte[] bytes) {}

    private final BudgetingService budgetingService;
    private final PeoplePayrollService peoplePayrollService;

    public ReportFile generate(
            ReportKind kind,
            UUID planId,
            PeriodGranularity granularity,
            Integer month,
            Integer year,
            Integer quarter) {
        PeriodGranularity g = granularity != null ? granularity : PeriodGranularity.ANNUAL;
        Integer resolvedQuarter = quarter;
        if (g == PeriodGranularity.QUARTERLY && resolvedQuarter == null && month != null) {
            resolvedQuarter = quarterForMonth(month);
        }
        return switch (kind) {
            case PL -> pl(planId, g, month, year, resolvedQuarter);
            case BU_MARGIN -> buMargin(planId, g, month, year, resolvedQuarter);
            case HEADCOUNT -> headcount(planId, g, month, year, resolvedQuarter);
            case COST_PER_EMPLOYEE -> costPerEmployee(planId, g, month, year, resolvedQuarter);
            case ROLLING_FORECAST -> rollingForecast(planId);
            case EXPENSE_SUMMARY -> expenseSummary(planId, g, month, year, resolvedQuarter);
        };
    }

    // ── Report 1: Monthly P&L ────────────────────────────────────────────────

    private ReportFile pl(UUID planId, PeriodGranularity g, Integer month, Integer year, Integer quarter) {
        PlanVsActualResult pva = budgetingService.getPlanVsActual(planId, null, g, month, year, quarter);
        RollingForecastResult rf = budgetingService.getRollingForecast(planId, g, month, year, quarter);
        String date = ReportExcelSupport.today();
        byte[] bytes = ReportExcelSupport.writeWorkbook(wb -> {
            var styles = ReportExcelSupport.createStyles(wb);
            ReportExcelSupport.writeHowToReadSheet(wb, styles,
                    "Report period: " + pva.periodLabel() + " (" + pva.granularity() + "). Financial Year "
                            + pva.fiscalYear() + ".");
            writePlSummary(wb, styles, pva, date);
            writePlMonthlyTrend(wb, styles, pva, rf, date);
            wb.setSheetOrder("How to Read This Report", 0);
            wb.setSheetOrder("P&L Summary", 1);
            wb.setSheetOrder("Monthly Trend", 2);
        });
        return new ReportFile(filename("pl", pva.periodLabel(), date), bytes);
    }

    private void writePlSummary(XSSFWorkbook wb, ReportExcelSupport.Styles styles,
                                PlanVsActualResult pva, String date) {
        XSSFSheet sheet = wb.createSheet("P&L Summary");
        int cols = 6;
        int signCol = 5;
        ReportExcelSupport.writeTitle(sheet, styles,
                "Cognologix — P&L Report — " + pva.periodLabel(), cols);
        ReportExcelSupport.writeSubtitle(sheet, styles, 1,
                "Generated on " + date + ", Financial Year " + pva.fiscalYear() + ", Forecast Type Normal",
                cols);

        Row header = sheet.createRow(3);
        ReportExcelSupport.writeHeaders(header, styles,
                "Line Item", "Plan", "Actual", "Variance (Rs L)", "Variance %", "Sign Convention");

        PeriodTotals t = pva.selectedPeriod();
        MoneyTriad opex = opexTriad(t);
        int r = 4;
        r = writePlRow(sheet, styles, r, "Total Revenue", t.totalRevenue(), true, true);
        r = writePlRow(sheet, styles, r, "Total COGS", t.totalCogs(), false, false);
        r = writePlRow(sheet, styles, r, "Gross Profit", t.grossProfit(), true, false);
        r = writePlMarginRow(sheet, styles, r, "Gross Margin %",
                ReportExcelSupport.marginPct(t.grossProfit().plan(), t.totalRevenue().plan()),
                t.totalRevenue().actual() != null
                        ? ReportExcelSupport.marginPct(t.grossProfit().actual(), t.totalRevenue().actual()) : null);
        r = writePlRow(sheet, styles, r, "Total OpEx", opex, false, false);
        r = writePlRow(sheet, styles, r, "EBITDA", t.ebitda(), true, true);
        r = writePlMarginRow(sheet, styles, r, "EBITDA Margin %",
                ReportExcelSupport.marginPct(t.ebitda().plan(), t.totalRevenue().plan()),
                t.totalRevenue().actual() != null
                        ? ReportExcelSupport.marginPct(t.ebitda().actual(), t.totalRevenue().actual()) : null);

        r = ReportExcelSupport.writeVarianceLegend(sheet, styles, r, cols);

        // Chart data block below the legend
        int chartDataRow = r + 1;
        Row ch = sheet.createRow(chartDataRow);
        ch.createCell(0).setCellValue("Metric");
        ch.createCell(1).setCellValue("Plan");
        ch.createCell(2).setCellValue("Actual");
        writeChartMetric(sheet, chartDataRow + 1, "Revenue", t.totalRevenue());
        writeChartMetric(sheet, chartDataRow + 2, "Gross Profit", t.grossProfit());
        writeChartMetric(sheet, chartDataRow + 3, "EBITDA", t.ebitda());

        ReportExcelSupport.addClusteredBarChartByColumns(sheet, "Plan vs Actual — Key Metrics",
                0, chartDataRow + 1, chartDataRow + 3,
                new int[]{1, 2}, new String[]{"Plan", "Actual"},
                0, chartDataRow + 5, 8, chartDataRow + 20);

        ReportExcelSupport.applySheetDefaults(sheet, date, cols);
        ReportExcelSupport.setSignConventionColumnWidth(sheet, signCol);
    }

    private void writePlMonthlyTrend(XSSFWorkbook wb, ReportExcelSupport.Styles styles,
                                     PlanVsActualResult pva, RollingForecastResult rf, String date) {
        XSSFSheet sheet = wb.createSheet("Monthly Trend");
        int cols = 14;
        ReportExcelSupport.writeTitle(sheet, styles,
                "Cognologix — P&L Monthly Trend — " + pva.fiscalYear(), cols);

        Row header = sheet.createRow(2);
        Cell h0 = header.createCell(0);
        h0.setCellValue("Line Item");
        h0.setCellStyle(styles.headerStyle());
        for (int i = 0; i < 12; i++) {
            Cell c = header.createCell(i + 1);
            c.setCellValue(ReportExcelSupport.FY_MONTHS[i]);
            c.setCellStyle(styles.headerStyle());
        }
        Cell fy = header.createCell(13);
        fy.setCellValue("FY Total");
        fy.setCellStyle(styles.headerStyle());

        String[] metrics = {
                "Total Revenue", "Total COGS", "Gross Profit", "Gross Margin %",
                "Total OpEx", "EBITDA", "EBITDA Margin %"
        };
        int rowIdx = 3;
        int planStartRow = rowIdx;
        for (String metric : metrics) {
            writeTrendRow(sheet, styles, rowIdx++, metric + " (Plan)", pva, metric, true);
            writeTrendRow(sheet, styles, rowIdx++, metric + " (Actual)", pva, metric, false);
        }

        // Line chart data: Revenue Plan / Actual / RF
        int chartRow = rowIdx + 2;
        Row cat = sheet.createRow(chartRow);
        cat.createCell(0).setCellValue("Series");
        for (int i = 0; i < 12; i++) {
            cat.createCell(i + 1).setCellValue(ReportExcelSupport.FY_MONTHS[i]);
        }
        writeRfSeriesRow(sheet, styles, chartRow + 1, "Revenue Plan", pva, true);
        writeRfSeriesRow(sheet, styles, chartRow + 2, "Revenue Actual", pva, false);
        writeRollingRevenueRow(sheet, styles, chartRow + 3, "Rolling Forecast", rf);

        ReportExcelSupport.addLineChart(sheet, "Revenue Plan vs Actual vs Rolling Forecast",
                chartRow, 1, 12,
                List.of(
                        ReportExcelSupport.LineSeriesRef.of("Revenue Plan", chartRow + 1, 1, 12, true, PresetColor.GRAY),
                        ReportExcelSupport.LineSeriesRef.of("Revenue Actual", chartRow + 2, 1, 12, false, PresetColor.GREEN),
                        ReportExcelSupport.LineSeriesRef.of("Rolling Forecast", chartRow + 3, 1, 12, false, PresetColor.RED)
                ),
                0, chartRow + 5, 10, chartRow + 20);

        ReportExcelSupport.applySheetDefaults(sheet, date, cols);
        // silence unused
        if (planStartRow < 0) {
            throw new IllegalStateException();
        }
    }

    private void writeTrendRow(XSSFSheet sheet, ReportExcelSupport.Styles styles, int rowIdx,
                               String label, PlanVsActualResult pva, String metric, boolean plan) {
        Row row = sheet.createRow(rowIdx);
        ReportExcelSupport.setText(row.createCell(0), label, styles.dataStyle());
        BigDecimal fy = BigDecimal.ZERO;
        boolean isPct = metric.contains("%");
        for (int i = 0; i < 12; i++) {
            MonthlyPlanVsActual m = pva.months().get(i);
            BigDecimal v = extractPvaMetric(m, metric, plan);
            CellStyle bg = m.hasActuals() ? styles.actualsBgStyle() : styles.planOnlyBgStyle();
            Cell cell = row.createCell(i + 1);
            if (isPct) {
                ReportExcelSupport.setPct(cell, v, styles.dataPctStyle());
            } else {
                ReportExcelSupport.setMoney(cell, v, bg);
                fy = fy.add(ReportExcelSupport.nz(v));
            }
        }
        Cell total = row.createCell(13);
        if (isPct) {
            PeriodTotals fyT = pva.fy();
            BigDecimal pct = switch (metric) {
                case "Gross Margin %" -> plan
                        ? ReportExcelSupport.marginPct(fyT.grossProfit().plan(), fyT.totalRevenue().plan())
                        : ReportExcelSupport.marginPct(fyT.grossProfit().actual(), fyT.totalRevenue().actual());
                case "EBITDA Margin %" -> plan
                        ? ReportExcelSupport.marginPct(fyT.ebitda().plan(), fyT.totalRevenue().plan())
                        : ReportExcelSupport.marginPct(fyT.ebitda().actual(), fyT.totalRevenue().actual());
                default -> null;
            };
            ReportExcelSupport.setPct(total, pct, styles.dataPctStyle());
        } else {
            ReportExcelSupport.setMoney(total, fy, styles.totalRowStyle());
        }
    }

    private void writeRfSeriesRow(XSSFSheet sheet, ReportExcelSupport.Styles styles, int rowIdx,
                                  String label, PlanVsActualResult pva, boolean plan) {
        Row row = sheet.createRow(rowIdx);
        row.createCell(0).setCellValue(label);
        for (int i = 0; i < 12; i++) {
            MonthlyPlanVsActual m = pva.months().get(i);
            BigDecimal v = plan ? m.totalRevenue().plan() : m.totalRevenue().actual();
            Cell c = row.createCell(i + 1);
            ReportExcelSupport.setMoney(c, v, styles.dataStyle());
        }
    }

    private void writeRollingRevenueRow(XSSFSheet sheet, ReportExcelSupport.Styles styles, int rowIdx,
                                        String label, RollingForecastResult rf) {
        Row row = sheet.createRow(rowIdx);
        row.createCell(0).setCellValue(label);
        for (int i = 0; i < Math.min(12, rf.months().size()); i++) {
            Cell c = row.createCell(i + 1);
            ReportExcelSupport.setMoney(c, rf.months().get(i).totalRevenue(), styles.dataStyle());
        }
    }

    private BigDecimal extractPvaMetric(MonthlyPlanVsActual m, String metric, boolean plan) {
        return switch (metric) {
            case "Total Revenue" -> plan ? m.totalRevenue().plan() : m.totalRevenue().actual();
            case "Total COGS" -> plan ? m.totalCogs().plan() : m.totalCogs().actual();
            case "Gross Profit" -> plan ? m.grossProfit().plan() : m.grossProfit().actual();
            case "Gross Margin %" -> {
                BigDecimal gp = plan ? m.grossProfit().plan() : m.grossProfit().actual();
                BigDecimal rev = plan ? m.totalRevenue().plan() : m.totalRevenue().actual();
                yield ReportExcelSupport.marginPct(gp, rev);
            }
            case "Total OpEx" -> {
                BigDecimal gp = plan ? m.grossProfit().plan() : m.grossProfit().actual();
                BigDecimal ebitda = plan ? m.ebitda().plan() : m.ebitda().actual();
                yield (gp != null && ebitda != null) ? gp.subtract(ebitda) : null;
            }
            case "EBITDA" -> plan ? m.ebitda().plan() : m.ebitda().actual();
            case "EBITDA Margin %" -> {
                BigDecimal e = plan ? m.ebitda().plan() : m.ebitda().actual();
                BigDecimal rev = plan ? m.totalRevenue().plan() : m.totalRevenue().actual();
                yield ReportExcelSupport.marginPct(e, rev);
            }
            default -> null;
        };
    }

    private int writePlRow(XSSFSheet sheet, ReportExcelSupport.Styles styles, int r,
                           String label, MoneyTriad triad, boolean higherIsBetter, boolean bold) {
        Row row = sheet.createRow(r);
        CellStyle labelStyle = bold ? styles.boldDataStyle() : styles.dataStyle();
        ReportExcelSupport.setText(row.createCell(0), label, labelStyle);
        CellStyle moneyStyle = bold ? styles.boldDataStyle() : styles.dataStyle();
        ReportExcelSupport.setMoney(row.createCell(1), triad.plan(), moneyStyle);
        ReportExcelSupport.setMoney(row.createCell(2), triad.actual(), moneyStyle);
        // Cost rows: higherIsBetter=false → green when Actual < Plan (negative variance)
        ReportExcelSupport.setMoney(row.createCell(3), triad.variance(),
                ReportExcelSupport.varianceMoneyStyle(styles, triad.variance(), higherIsBetter));
        BigDecimal vp = ReportExcelSupport.variancePct(triad.variance(), triad.plan());
        ReportExcelSupport.setPct(row.createCell(4), vp,
                ReportExcelSupport.variancePctStyle(styles, triad.variance(), higherIsBetter));
        ReportExcelSupport.writeSignConvention(row.createCell(5), styles, label);
        return r + 1;
    }

    private int writePlMarginRow(XSSFSheet sheet, ReportExcelSupport.Styles styles, int r,
                                 String label, BigDecimal planPct, BigDecimal actualPct) {
        Row row = sheet.createRow(r);
        ReportExcelSupport.setText(row.createCell(0), label, styles.dataStyle());
        ReportExcelSupport.setPct(row.createCell(1), planPct, styles.dataPctStyle());
        ReportExcelSupport.setPct(row.createCell(2), actualPct, styles.dataPctStyle());
        BigDecimal variance = (planPct != null && actualPct != null) ? actualPct.subtract(planPct) : null;
        ReportExcelSupport.setPct(row.createCell(3), variance,
                ReportExcelSupport.variancePctStyle(styles, variance, true));
        ReportExcelSupport.setPct(row.createCell(4),
                ReportExcelSupport.variancePct(variance, planPct),
                ReportExcelSupport.variancePctStyle(styles, variance, true));
        ReportExcelSupport.writeSignConvention(row.createCell(5), styles, label);
        return r + 1;
    }

    private void writeChartMetric(XSSFSheet sheet, int rowIdx, String label, MoneyTriad triad) {
        Row row = sheet.createRow(rowIdx);
        row.createCell(0).setCellValue(label);
        if (triad.plan() != null) {
            row.createCell(1).setCellValue(triad.plan().doubleValue());
        }
        if (triad.actual() != null) {
            row.createCell(2).setCellValue(triad.actual().doubleValue());
        }
    }

    private static MoneyTriad opexTriad(PeriodTotals t) {
        BigDecimal plan = t.grossProfit().plan().subtract(t.ebitda().plan());
        BigDecimal actual = (t.grossProfit().actual() != null && t.ebitda().actual() != null)
                ? t.grossProfit().actual().subtract(t.ebitda().actual()) : null;
        BigDecimal variance = (t.grossProfit().variance() != null && t.ebitda().variance() != null)
                ? t.grossProfit().variance().subtract(t.ebitda().variance()) : null;
        return new MoneyTriad(plan, actual, variance);
    }

    // ── Report 2: BU Gross Margin ────────────────────────────────────────────

    private ReportFile buMargin(UUID planId, PeriodGranularity g, Integer month, Integer year, Integer quarter) {
        BuAnalysisResult data = budgetingService.getBuAnalysis(planId, g, month, year, quarter);
        String date = ReportExcelSupport.today();
        byte[] bytes = ReportExcelSupport.writeWorkbook(wb -> {
            var styles = ReportExcelSupport.createStyles(wb);
            ReportExcelSupport.writeHowToReadSheet(wb, styles,
                    "Report period: " + data.periodLabel() + " (" + data.granularity() + ").");
            writeBuSummary(wb, styles, data, date);
            writeBuPositionBreakdown(wb, styles, data, date);
            writeInternalBus(wb, styles, data, date);
            wb.setSheetOrder("How to Read This Report", 0);
            wb.setSheetOrder("BU Summary", 1);
            wb.setSheetOrder("Position Breakdown", 2);
            wb.setSheetOrder("Internal BUs", 3);
        });
        return new ReportFile(filename("bu_margin", data.periodLabel(), date), bytes);
    }

    private void writeBuSummary(XSSFWorkbook wb, ReportExcelSupport.Styles styles,
                                BuAnalysisResult data, String date) {
        XSSFSheet sheet = wb.createSheet("BU Summary");
        int cols = 9;
        ReportExcelSupport.writeTitle(sheet, styles,
                "Cognologix — BU Gross Margin — " + data.periodLabel(), cols);

        Row header = sheet.createRow(2);
        ReportExcelSupport.writeHeaders(header, styles,
                "Client", "Billable HC", "Total HC", "Actual Revenue (Rs L)",
                "Total Payroll Cost (Rs L)", "Gross Margin (Rs L)", "Gross Margin %",
                "BU Revenue % of Total", "BU Cost % of Total");

        int r = 3;
        int dataStart = r;
        for (ExternalBuAnalysisRow bu : data.externalBUs()) {
            Row row = sheet.createRow(r++);
            ReportExcelSupport.setText(row.createCell(0), bu.customerName(), styles.dataStyle());
            ReportExcelSupport.setInt(row.createCell(1), bu.billableHc(), styles.dataIntStyle());
            ReportExcelSupport.setInt(row.createCell(2), bu.totalHc(), styles.dataIntStyle());
            ReportExcelSupport.setMoney(row.createCell(3), bu.actualRevenue(), styles.dataStyle());
            ReportExcelSupport.setMoney(row.createCell(4), bu.totalPayrollCost(), styles.dataStyle());
            ReportExcelSupport.setMoney(row.createCell(5), bu.grossMargin(), styles.dataStyle());
            CellStyle gmStyle = gmConditional(styles, bu.grossMarginPct());
            ReportExcelSupport.setPct(row.createCell(6), bu.grossMarginPct(), gmStyle);
            ReportExcelSupport.setPct(row.createCell(7), bu.buRevenuePctOfTotal(), styles.dataPctStyle());
            ReportExcelSupport.setPct(row.createCell(8), bu.buCostPctOfTotal(), styles.dataPctStyle());
        }
        int dataEnd = r - 1;

        r = ReportExcelSupport.writeGmConditionalLegend(sheet, styles, r, cols);

        if (dataEnd >= dataStart) {
            ReportExcelSupport.addClusteredBarChartByColumns(sheet, "Revenue vs Payroll Cost per Client",
                    0, dataStart, dataEnd,
                    new int[]{3, 4}, new String[]{"Actual Revenue", "Payroll Cost"},
                    0, r + 1, 6, r + 16);
            ReportExcelSupport.addPieChart(sheet, "Revenue distribution by client",
                    0, 3, dataStart, dataEnd,
                    7, r + 1, 14, r + 16);
        }
        ReportExcelSupport.applySheetDefaults(sheet, date, cols);
    }

    private CellStyle gmConditional(ReportExcelSupport.Styles styles, BigDecimal gmPct) {
        if (gmPct == null) {
            return styles.dataPctStyle();
        }
        if (gmPct.compareTo(new BigDecimal("30")) > 0) {
            return styles.positiveVariancePctStyle();
        }
        if (gmPct.compareTo(new BigDecimal("15")) < 0) {
            return styles.negativeVariancePctStyle();
        }
        return styles.dataPctStyle();
    }

    private void writeBuPositionBreakdown(XSSFWorkbook wb, ReportExcelSupport.Styles styles,
                                          BuAnalysisResult data, String date) {
        XSSFSheet sheet = wb.createSheet("Position Breakdown");
        int cols = 4;
        ReportExcelSupport.writeTitle(sheet, styles,
                "Cognologix — Position Breakdown — " + data.periodLabel(), cols);
        int r = 2;
        for (ExternalBuAnalysisRow bu : data.externalBUs()) {
            Row section = sheet.createRow(r++);
            Cell sc = section.createCell(0);
            sc.setCellValue(bu.customerName());
            sc.setCellStyle(styles.sectionHeaderStyle());
            sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(r - 1, r - 1, 0, 3));

            Row header = sheet.createRow(r++);
            ReportExcelSupport.writeHeaders(header, styles,
                    "Title", "Headcount", "Avg Payroll Cost (Rs L)", "% of BU HC");
            for (PositionBreakdownRow pos : bu.positionBreakdown()) {
                Row row = sheet.createRow(r++);
                ReportExcelSupport.setText(row.createCell(0), pos.title(), styles.dataStyle());
                ReportExcelSupport.setInt(row.createCell(1), pos.headcount(), styles.dataIntStyle());
                ReportExcelSupport.setMoney(row.createCell(2), pos.avgPayrollCost(), styles.dataStyle());
                ReportExcelSupport.setPct(row.createCell(3), pos.pctOfBuHc(), styles.dataPctStyle());
            }
            r++;
        }
        ReportExcelSupport.applySheetDefaults(sheet, date, cols);
    }

    private void writeInternalBus(XSSFWorkbook wb, ReportExcelSupport.Styles styles,
                                  BuAnalysisResult data, String date) {
        XSSFSheet sheet = wb.createSheet("Internal BUs");
        int cols = 5;
        ReportExcelSupport.writeTitle(sheet, styles,
                "Cognologix — Internal BUs — " + data.periodLabel(), cols);
        Row header = sheet.createRow(2);
        ReportExcelSupport.writeHeaders(header, styles,
                "Internal BU", "Total HC", "Billable HC", "Total Payroll Cost (Rs L)", "Cost % of Total");
        int r = 3;
        int start = r;
        for (InternalBuAnalysisRow bu : data.internalBUs()) {
            Row row = sheet.createRow(r++);
            ReportExcelSupport.setText(row.createCell(0), bu.customerName(), styles.dataStyle());
            ReportExcelSupport.setInt(row.createCell(1), bu.totalHc(), styles.dataIntStyle());
            ReportExcelSupport.setInt(row.createCell(2), bu.billableHc(), styles.dataIntStyle());
            ReportExcelSupport.setMoney(row.createCell(3), bu.totalPayrollCost(), styles.dataStyle());
            ReportExcelSupport.setPct(row.createCell(4), bu.buCostPctOfTotal(), styles.dataPctStyle());
        }
        int end = r - 1;
        if (end >= start) {
            ReportExcelSupport.addPieChart(sheet, "Internal BU cost distribution",
                    0, 3, start, end, 0, r + 1, 8, r + 16);
        }
        ReportExcelSupport.applySheetDefaults(sheet, date, cols);
    }

    // ── Report 3: Headcount Summary ──────────────────────────────────────────

    private ReportFile headcount(UUID planId, PeriodGranularity g, Integer month, Integer year, Integer quarter) {
        PlanVsActualResult pva = budgetingService.getPlanVsActual(planId, null, g, month, year, quarter);
        RollingForecastResult rf = budgetingService.getRollingForecast(planId, g, month, year, quarter);
        String date = ReportExcelSupport.today();
        byte[] bytes = ReportExcelSupport.writeWorkbook(wb -> {
            var styles = ReportExcelSupport.createStyles(wb);
            ReportExcelSupport.writeHowToReadSheet(wb, styles,
                    "Report period: " + pva.periodLabel() + " (" + pva.granularity() + "). Financial Year "
                            + pva.fiscalYear() + ".");
            writeHcByCategory(wb, styles, pva, date);
            writeHcByPu(wb, styles, pva, date);
            writeHcMonthlyTrend(wb, styles, rf, date);
            wb.setSheetOrder("How to Read This Report", 0);
            wb.setSheetOrder("By Category", 1);
            wb.setSheetOrder("By Practice Unit", 2);
            wb.setSheetOrder("Monthly Trend", 3);
        });
        return new ReportFile(filename("headcount", pva.periodLabel(), date), bytes);
    }

    private void writeHcByCategory(XSSFWorkbook wb, ReportExcelSupport.Styles styles,
                                   PlanVsActualResult pva, String date) {
        XSSFSheet sheet = wb.createSheet("By Category");
        int cols = 7;
        int signCol = 6;
        ReportExcelSupport.writeTitle(sheet, styles,
                "Cognologix — Headcount by Category — " + pva.periodLabel(), cols);
        Row header = sheet.createRow(2);
        ReportExcelSupport.writeHeaders(header, styles,
                "Category", "Plan HC", "Actual HC", "Variance",
                "Billable Ratio % Plan", "Billable Ratio % Actual", "Sign Convention");

        HcFigures plan = aggregateHc(pva, true);
        HcFigures actual = aggregateHc(pva, false);
        int r = 3;
        r = writeHcCatRow(sheet, styles, r, "Billable", plan.billableHc(), actual.billableHc(), plan, actual);
        r = writeHcCatRow(sheet, styles, r, "Bench", plan.benchHc(), actual.benchHc(), plan, actual);
        r = writeHcCatRow(sheet, styles, r, "Support", plan.supportHc(), actual.supportHc(), plan, actual);
        r = writeHcCatRow(sheet, styles, r, "Leadership", plan.leadershipHc(), actual.leadershipHc(), plan, actual);
        r = writeHcCatRow(sheet, styles, r, "Management", plan.managementHc(), actual.managementHc(), plan, actual);
        r = writeHcCatRow(sheet, styles, r, "Total", plan.totalHc(), actual.totalHc(), plan, actual);

        r = ReportExcelSupport.writeVarianceLegend(sheet, styles, r, cols);

        ReportExcelSupport.addClusteredBarChartByColumns(sheet, "Plan vs Actual HC per category",
                0, 3, 7, new int[]{1, 2}, new String[]{"Plan HC", "Actual HC"},
                0, r + 1, 8, r + 16);
        ReportExcelSupport.applySheetDefaults(sheet, date, cols);
        ReportExcelSupport.setSignConventionColumnWidth(sheet, signCol);
    }

    private int writeHcCatRow(XSSFSheet sheet, ReportExcelSupport.Styles styles, int r,
                              String cat, int planHc, int actualHc, HcFigures planAll, HcFigures actualAll) {
        Row row = sheet.createRow(r);
        boolean total = "Total".equals(cat);
        CellStyle style = total ? styles.totalRowStyle() : styles.dataStyle();
        ReportExcelSupport.setText(row.createCell(0), cat, style);
        ReportExcelSupport.setInt(row.createCell(1), planHc, styles.dataIntStyle());
        ReportExcelSupport.setInt(row.createCell(2), actualHc, styles.dataIntStyle());
        int variance = actualHc - planHc;
        if ("Billable".equals(cat) && variance != 0) {
            boolean favorable = ReportExcelSupport.isFavorableVariance(BigDecimal.valueOf(variance), true);
            ReportExcelSupport.setInt(row.createCell(3), variance,
                    favorable ? styles.positiveVarianceStyle() : styles.negativeVarianceStyle());
        } else {
            ReportExcelSupport.setInt(row.createCell(3), variance, styles.dataIntStyle());
        }
        BigDecimal planRatio = "Billable".equals(cat) || "Total".equals(cat)
                ? ReportExcelSupport.marginPct(BigDecimal.valueOf(planAll.billableHc()), BigDecimal.valueOf(Math.max(1, planAll.totalHc())))
                : null;
        BigDecimal actualRatio = "Billable".equals(cat) || "Total".equals(cat)
                ? ReportExcelSupport.marginPct(BigDecimal.valueOf(actualAll.billableHc()), BigDecimal.valueOf(Math.max(1, actualAll.totalHc())))
                : null;
        if ("Total".equals(cat) || "Billable".equals(cat)) {
            ReportExcelSupport.setPct(row.createCell(4), planRatio, styles.dataPctStyle());
            ReportExcelSupport.setPct(row.createCell(5), actualRatio, styles.dataPctStyle());
            ReportExcelSupport.writeSignConventionText(row.createCell(6), styles, ReportExcelSupport.HIGHER_BETTER);
        } else {
            ReportExcelSupport.setText(row.createCell(4), "—", styles.dataStyle());
            ReportExcelSupport.setText(row.createCell(5), "—", styles.dataStyle());
            ReportExcelSupport.writeSignConventionText(row.createCell(6), styles, "");
        }
        return r + 1;
    }

    private HcFigures aggregateHc(PlanVsActualResult pva, boolean plan) {
        int billable = 0, bench = 0, support = 0, leadership = 0, management = 0, total = 0;
        int count = 0;
        for (MonthlyPlanVsActual m : pva.months()) {
            if (pva.granularity().equals("MONTHLY") && !m.hasActuals() && !plan) {
                // still include zeros for plan months when aggregating actual
            }
            HcFigures h = plan ? m.hc().plan() : m.hc().actual();
            if (h == null) {
                continue;
            }
            // For period-scoped: use months that fall in selectedPeriod by matching pva months with data
            // Prefer averaging months with data for non-ANNUAL; for simplicity sum then average by months in list
            billable += h.billableHc();
            bench += h.benchHc();
            support += h.supportHc();
            leadership += h.leadershipHc();
            management += h.managementHc();
            total += h.totalHc();
            count++;
        }
        // For MONTHLY/QUARTERLY selectedPeriod — use last month in scope that has values, or average
        if ("MONTHLY".equals(pva.granularity()) && !pva.months().isEmpty()) {
            // Find month matching period label roughly: use first month with matching hasActuals preference
            MonthlyPlanVsActual target = pva.months().stream()
                    .filter(m -> pva.periodLabel() != null && pva.periodLabel().contains(String.valueOf(m.year())))
                    .findFirst()
                    .orElse(pva.months().getFirst());
            // Better: for MONTHLY the selected period is one month — find by checking monthsWithActuals path
            // Use average of months that have plan data (all) — for MONTHLY months() may still be 12.
            // Re-read: PlanVsActual always returns 12 months; selectedPeriod aggregates the selection.
            // HC is not on PeriodTotals — so for MONTHLY we need the matching month.
            for (MonthlyPlanVsActual m : pva.months()) {
                String label = monthLabel(m.month(), m.year());
                if (pva.periodLabel() != null && pva.periodLabel().startsWith(label.split(" ")[0])) {
                    // fragile; use highlight via periodLabel equality with formatMonthLabel
                }
            }
        }
        int divisor = Math.max(1, count);
        if ("MONTHLY".equals(pva.granularity())) {
            MonthlyPlanVsActual match = findMonthForPeriod(pva);
            HcFigures h = plan ? match.hc().plan() : match.hc().actual();
            return h != null ? h : new HcFigures(0, 0, 0, 0, 0, 0);
        }
        if ("QUARTERLY".equals(pva.granularity())) {
            List<MonthlyPlanVsActual> qMonths = monthsForQuarter(pva);
            return averageHc(qMonths, plan);
        }
        // ANNUAL — average across months with data
        return new HcFigures(
                billable / divisor, bench / divisor, support / divisor,
                leadership / divisor, management / divisor, total / divisor);
    }

    private MonthlyPlanVsActual findMonthForPeriod(PlanVsActualResult pva) {
        for (MonthlyPlanVsActual m : pva.months()) {
            if (pva.periodLabel() != null && pva.periodLabel().equalsIgnoreCase(monthLabel(m.month(), m.year()))) {
                return m;
            }
        }
        return pva.months().stream().filter(MonthlyPlanVsActual::hasActuals).findFirst()
                .orElse(pva.months().getFirst());
    }

    private List<MonthlyPlanVsActual> monthsForQuarter(PlanVsActualResult pva) {
        // periodLabel like "Q1 FY2627"
        int q = 1;
        if (pva.periodLabel() != null && pva.periodLabel().startsWith("Q")) {
            try {
                q = Integer.parseInt(pva.periodLabel().substring(1, 2));
            } catch (NumberFormatException ignored) {
                q = 1;
            }
        }
        int[] months = switch (q) {
            case 2 -> new int[]{7, 8, 9};
            case 3 -> new int[]{10, 11, 12};
            case 4 -> new int[]{1, 2, 3};
            default -> new int[]{4, 5, 6};
        };
        List<MonthlyPlanVsActual> result = new ArrayList<>();
        for (int mo : months) {
            for (MonthlyPlanVsActual m : pva.months()) {
                if (m.month() == mo) {
                    result.add(m);
                }
            }
        }
        return result;
    }

    private HcFigures averageHc(List<MonthlyPlanVsActual> months, boolean plan) {
        if (months.isEmpty()) {
            return new HcFigures(0, 0, 0, 0, 0, 0);
        }
        int b = 0, be = 0, s = 0, l = 0, mgt = 0, t = 0;
        for (MonthlyPlanVsActual m : months) {
            HcFigures h = plan ? m.hc().plan() : m.hc().actual();
            if (h == null) {
                continue;
            }
            b += h.billableHc();
            be += h.benchHc();
            s += h.supportHc();
            l += h.leadershipHc();
            mgt += h.managementHc();
            t += h.totalHc();
        }
        int d = months.size();
        return new HcFigures(b / d, be / d, s / d, l / d, mgt / d, t / d);
    }

    private void writeHcByPu(XSSFWorkbook wb, ReportExcelSupport.Styles styles,
                             PlanVsActualResult pva, String date) {
        XSSFSheet sheet = wb.createSheet("By Practice Unit");
        int cols = 6;
        ReportExcelSupport.writeTitle(sheet, styles,
                "Cognologix — Headcount by Practice Unit — " + pva.periodLabel(), cols);
        Row header = sheet.createRow(2);
        ReportExcelSupport.writeHeaders(header, styles,
                "PU", "Total HC", "Billable HC", "Bench HC", "Billable %", "Total Payroll Cost (Rs L)");

        Map<String, PuAgg> byPu = new LinkedHashMap<>();
        List<int[]> monthsToLoad = resolveMonthsForFacts(pva);
        for (int[] my : monthsToLoad) {
            for (MasterRecordFact fact : peoplePayrollService.findActiveMasterRecordFacts(my[0], my[1])) {
                String pu = fact.practiceUnit() != null && !fact.practiceUnit().isBlank()
                        ? fact.practiceUnit() : "(Unassigned)";
                PuAgg agg = byPu.computeIfAbsent(pu, k -> new PuAgg());
                agg.totalHc++;
                if (fact.billable()) {
                    agg.billableHc++;
                }
                if (fact.bench()) {
                    agg.benchHc++;
                }
                agg.payroll = agg.payroll.add(ReportExcelSupport.nz(fact.totalPayrollCost()));
            }
        }
        int divisor = Math.max(1, monthsToLoad.size());
        int r = 3;
        int start = r;
        List<Map.Entry<String, PuAgg>> sorted = byPu.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .toList();
        for (Map.Entry<String, PuAgg> e : sorted) {
            PuAgg a = e.getValue();
            int totalHc = Math.round((float) a.totalHc / divisor);
            int billableHc = Math.round((float) a.billableHc / divisor);
            int benchHc = Math.round((float) a.benchHc / divisor);
            Row row = sheet.createRow(r++);
            ReportExcelSupport.setText(row.createCell(0), e.getKey(), styles.dataStyle());
            ReportExcelSupport.setInt(row.createCell(1), totalHc, styles.dataIntStyle());
            ReportExcelSupport.setInt(row.createCell(2), billableHc, styles.dataIntStyle());
            ReportExcelSupport.setInt(row.createCell(3), benchHc, styles.dataIntStyle());
            ReportExcelSupport.setPct(row.createCell(4),
                    ReportExcelSupport.marginPct(BigDecimal.valueOf(billableHc), BigDecimal.valueOf(Math.max(1, totalHc))),
                    styles.dataPctStyle());
            ReportExcelSupport.setMoney(row.createCell(5),
                    a.payroll.divide(BigDecimal.valueOf(divisor), 2, RoundingMode.HALF_UP), styles.dataStyle());
        }
        int end = r - 1;
        if (end >= start) {
            ReportExcelSupport.addClusteredBarChartByColumns(sheet, "HC per PU",
                    0, start, end, new int[]{1}, new String[]{"Total HC"},
                    0, r + 1, 8, r + 16);
        }
        ReportExcelSupport.applySheetDefaults(sheet, date, cols);
    }

    private List<int[]> resolveMonthsForFacts(PlanVsActualResult pva) {
        List<int[]> result = new ArrayList<>();
        if ("MONTHLY".equals(pva.granularity())) {
            MonthlyPlanVsActual m = findMonthForPeriod(pva);
            result.add(new int[]{m.month(), m.year()});
        } else if ("QUARTERLY".equals(pva.granularity())) {
            for (MonthlyPlanVsActual m : monthsForQuarter(pva)) {
                result.add(new int[]{m.month(), m.year()});
            }
        } else {
            for (MonthlyPlanVsActual m : pva.months()) {
                if (m.hasActuals()) {
                    result.add(new int[]{m.month(), m.year()});
                }
            }
            if (result.isEmpty()) {
                for (MonthlyPlanVsActual m : pva.months()) {
                    result.add(new int[]{m.month(), m.year()});
                }
            }
        }
        return result;
    }

    private static final class PuAgg {
        int totalHc;
        int billableHc;
        int benchHc;
        BigDecimal payroll = BigDecimal.ZERO;
    }

    private void writeHcMonthlyTrend(XSSFWorkbook wb, ReportExcelSupport.Styles styles,
                                     RollingForecastResult rf, String date) {
        XSSFSheet sheet = wb.createSheet("Monthly Trend");
        int cols = 13;
        ReportExcelSupport.writeTitle(sheet, styles,
                "Cognologix — Headcount Monthly Trend — " + rf.fiscalYear(), cols);
        Row header = sheet.createRow(2);
        Cell h0 = header.createCell(0);
        h0.setCellValue("Metric");
        h0.setCellStyle(styles.headerStyle());
        for (int i = 0; i < 12; i++) {
            Cell c = header.createCell(i + 1);
            c.setCellValue(ReportExcelSupport.FY_MONTHS[i]);
            c.setCellStyle(styles.headerStyle());
        }
        Row total = sheet.createRow(3);
        Row billable = sheet.createRow(4);
        Row bench = sheet.createRow(5);
        Row ratio = sheet.createRow(6);
        total.createCell(0).setCellValue("Total HC");
        billable.createCell(0).setCellValue("Billable HC");
        bench.createCell(0).setCellValue("Bench HC");
        ratio.createCell(0).setCellValue("Billable Ratio %");
        for (int i = 0; i < Math.min(12, rf.months().size()); i++) {
            HcFigures hc = rf.months().get(i).hc();
            ReportExcelSupport.setInt(total.createCell(i + 1), hc.totalHc(), styles.dataIntStyle());
            ReportExcelSupport.setInt(billable.createCell(i + 1), hc.billableHc(), styles.dataIntStyle());
            ReportExcelSupport.setInt(bench.createCell(i + 1), hc.benchHc(), styles.dataIntStyle());
            ReportExcelSupport.setPct(ratio.createCell(i + 1),
                    ReportExcelSupport.marginPct(BigDecimal.valueOf(hc.billableHc()),
                            BigDecimal.valueOf(Math.max(1, hc.totalHc()))),
                    styles.dataPctStyle());
        }
        ReportExcelSupport.addLineChart(sheet, "Total HC and Billable HC trend",
                2, 1, 12,
                List.of(
                        ReportExcelSupport.LineSeriesRef.of("Total HC", 3, 1, 12, false, PresetColor.STEEL_BLUE),
                        ReportExcelSupport.LineSeriesRef.of("Billable HC", 4, 1, 12, false, PresetColor.GREEN)
                ),
                0, 8, 10, 23);
        ReportExcelSupport.applySheetDefaults(sheet, date, cols);
    }

    // ── Report 4: Cost per Employee ──────────────────────────────────────────

    private ReportFile costPerEmployee(UUID planId, PeriodGranularity g, Integer month, Integer year, Integer quarter) {
        CostPerEmployeeResult actualOrCurrent = budgetingService.getCostPerEmployee(
                planId, g, month, year, quarter, null);
        CostPerEmployeeResult plan = budgetingService.getCostPerEmployeePlan(
                planId, g, month, year, quarter, null);
        String date = ReportExcelSupport.today();
        byte[] bytes = ReportExcelSupport.writeWorkbook(wb -> {
            var styles = ReportExcelSupport.createStyles(wb);
            ReportExcelSupport.writeHowToReadSheet(wb, styles,
                    "Report period: " + actualOrCurrent.periodLabel() + " (" + actualOrCurrent.granularity() + ").");
            writeCostSheet(wb, styles, "Billable", plan.billable(), actualOrCurrent.billable(),
                    actualOrCurrent.totalCostPerBillableHead(), true, date, actualOrCurrent.periodLabel());
            writeCostSheet(wb, styles, "Bench", plan.bench(), actualOrCurrent.bench(),
                    null, false, date, actualOrCurrent.periodLabel());
            writeCostSheet(wb, styles, "Support", plan.support(), actualOrCurrent.support(),
                    null, false, date, actualOrCurrent.periodLabel());
            writeCostSheet(wb, styles, "Leadership", plan.leadership(), actualOrCurrent.leadership(),
                    null, false, date, actualOrCurrent.periodLabel());
            wb.setSheetOrder("How to Read This Report", 0);
            wb.setSheetOrder("Billable", 1);
            wb.setSheetOrder("Bench", 2);
            wb.setSheetOrder("Support", 3);
            wb.setSheetOrder("Leadership", 4);
        });
        return new ReportFile(filename("cost_per_employee", actualOrCurrent.periodLabel(), date), bytes);
    }

    private void writeCostSheet(XSSFWorkbook wb, ReportExcelSupport.Styles styles, String sheetName,
                                CategoryCost plan, CategoryCost actual, BigDecimal minBillingRate,
                                boolean includeLayer3, String date, String periodLabel) {
        XSSFSheet sheet = wb.createSheet(sheetName);
        int cols = 5;
        int signCol = 4;
        ReportExcelSupport.writeTitle(sheet, styles,
                "Cognologix — Cost per Employee (" + sheetName + ") — " + periodLabel, cols);
        Row header = sheet.createRow(2);
        ReportExcelSupport.writeHeaders(header, styles,
                "Layer", "Components", "Plan (Rs L/head/month)", "Actual (Rs L/head/month)", "Sign Convention");

        int r = 3;
        r = writeCostRow(sheet, styles, r, "Layer 1 Gross Pay", "Gross pay per head",
                plan.grossPayPerHead(), actual.grossPayPerHead(), false);
        r = writeCostRow(sheet, styles, r, "Layer 1 Employer Contributions",
                plan.employerContributionsSource() != null ? plan.employerContributionsSource() : "Contributions",
                plan.employerContributionsPerHead(), actual.employerContributionsPerHead(), false);
        r = writeCostRow(sheet, styles, r, "Layer 1 Total", "Gross + Employer Contributions",
                plan.layer1(), actual.layer1(), true);
        r = writeCostRow(sheet, styles, r, "Layer 2 Direct Overhead",
                "Medical, welfare, consumables, software, training",
                plan.layer2(), actual.layer2(), false);
        if (includeLayer3) {
            r = writeCostRow(sheet, styles, r, "Layer 3 Shared Overhead",
                    "Shared overhead ÷ billable HC",
                    plan.layer3(), actual.layer3(), false);
        }
        r = writeCostRow(sheet, styles, r, "Total Cost per Head", "Sum of layers",
                plan.total(), actual.total(), true);

        if (minBillingRate != null) {
            r = writeCostRow(sheet, styles, r, "Minimum Billing Rate", "Break-even billing rate",
                    plan.total(), minBillingRate, true);
            Row highlight = sheet.createRow(r + 1);
            Cell hc = highlight.createCell(0);
            hc.setCellValue("Minimum Billing Rate: Rs "
                    + ReportExcelSupport.nz(minBillingRate).setScale(2, RoundingMode.HALF_UP)
                    + "L/head/month");
            hc.setCellStyle(styles.titleStyle());
            sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(r + 1, r + 1, 0, 3));
            r += 2;
        }

        // Chart data
        int chartRow = r + 2;
        Row ch = sheet.createRow(chartRow);
        ch.createCell(0).setCellValue("Category");
        ch.createCell(1).setCellValue("Layer 1");
        ch.createCell(2).setCellValue("Layer 2");
        if (includeLayer3) {
            ch.createCell(3).setCellValue("Layer 3");
        }
        Row cv = sheet.createRow(chartRow + 1);
        cv.createCell(0).setCellValue(sheetName);
        cv.createCell(1).setCellValue(ReportExcelSupport.nz(actual.layer1()).doubleValue());
        cv.createCell(2).setCellValue(ReportExcelSupport.nz(actual.layer2()).doubleValue());
        if (includeLayer3) {
            cv.createCell(3).setCellValue(ReportExcelSupport.nz(actual.layer3()).doubleValue());
            ReportExcelSupport.addStackedBarChartByColumns(sheet, "Layer composition of total cost",
                    0, chartRow + 1, chartRow + 1,
                    new int[]{1, 2, 3}, new String[]{"Layer 1", "Layer 2", "Layer 3"},
                    0, chartRow + 3, 8, chartRow + 18);
        } else {
            ReportExcelSupport.addStackedBarChartByColumns(sheet, "Layer composition of total cost",
                    0, chartRow + 1, chartRow + 1,
                    new int[]{1, 2}, new String[]{"Layer 1", "Layer 2"},
                    0, chartRow + 3, 8, chartRow + 18);
        }
        ReportExcelSupport.applySheetDefaults(sheet, date, cols);
        ReportExcelSupport.setSignConventionColumnWidth(sheet, signCol);
    }

    private int writeCostRow(XSSFSheet sheet, ReportExcelSupport.Styles styles, int r,
                             String layer, String components, BigDecimal plan, BigDecimal actual, boolean bold) {
        Row row = sheet.createRow(r);
        CellStyle style = bold ? styles.boldDataStyle() : styles.dataStyle();
        ReportExcelSupport.setText(row.createCell(0), layer, style);
        ReportExcelSupport.setText(row.createCell(1), components, styles.dataStyle());
        ReportExcelSupport.setMoney(row.createCell(2), plan, style);
        ReportExcelSupport.setMoney(row.createCell(3), actual, style);
        String sign = layer.contains("Billing Rate") ? "" : ReportExcelSupport.LOWER_BETTER;
        ReportExcelSupport.writeSignConventionText(row.createCell(4), styles, sign);
        return r + 1;
    }

    // ── Report 5: Rolling Forecast vs Baseline ───────────────────────────────

    private ReportFile rollingForecast(UUID planId) {
        RollingForecastResult rf = budgetingService.getRollingForecast(planId);
        DeltaResult delta = budgetingService.getDelta(planId);
        String date = ReportExcelSupport.today();
        byte[] bytes = ReportExcelSupport.writeWorkbook(wb -> {
            var styles = ReportExcelSupport.createStyles(wb);
            ReportExcelSupport.writeHowToReadSheet(wb, styles,
                    "Report period: full financial year " + rf.fiscalYear()
                            + " (Rolling Forecast vs Baseline).");
            writeForecastSheet(wb, styles, "Revenue Forecast", rf, delta, date,
                    List.of(new ForecastMetric("Revenue",
                            m -> m.totalRevenue(),
                            d -> d.totalRevenue())));
            writeForecastSheet(wb, styles, "HC Forecast", rf, delta, date,
                    List.of(
                            new ForecastMetric("Total HC",
                                    m -> BigDecimal.valueOf(m.hc().totalHc()),
                                    d -> BigDecimal.valueOf(d.hc().totalHc())),
                            new ForecastMetric("Billable HC",
                                    m -> BigDecimal.valueOf(m.hc().billableHc()),
                                    d -> BigDecimal.valueOf(d.hc().billableHc()))
                    ));
            writeForecastSheet(wb, styles, "Cost Forecast", rf, delta, date,
                    List.of(
                            new ForecastMetric("Total Payroll Cost",
                                    m -> m.totalSalaryCost(),
                                    d -> d.totalSalaryCost()),
                            new ForecastMetric("EBITDA",
                                    m -> m.ebitda(),
                                    d -> d.ebitda())
                    ));
            wb.setSheetOrder("How to Read This Report", 0);
            wb.setSheetOrder("Revenue Forecast", 1);
            wb.setSheetOrder("HC Forecast", 2);
            wb.setSheetOrder("Cost Forecast", 3);
        });
        return new ReportFile(filename("rolling_forecast", rf.fiscalYear(), date), bytes);
    }

    @FunctionalInterface
    private interface MetricFn {
        BigDecimal apply(MonthlyFinancials m);
    }

    private record ForecastMetric(String name, MetricFn rolling, MetricFn delta) {}

    private void writeForecastSheet(XSSFWorkbook wb, ReportExcelSupport.Styles styles, String sheetName,
                                    RollingForecastResult rf, DeltaResult delta, String date,
                                    List<ForecastMetric> metrics) {
        XSSFSheet sheet = wb.createSheet(sheetName);
        int cols = 14;
        ReportExcelSupport.writeTitle(sheet, styles,
                "Cognologix — " + sheetName + " — " + rf.fiscalYear(), cols);
        int r = 2;
        for (ForecastMetric metric : metrics) {
            Row section = sheet.createRow(r++);
            Cell sc = section.createCell(0);
            sc.setCellValue(metric.name());
            sc.setCellStyle(styles.sectionHeaderStyle());
            sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(r - 1, r - 1, 0, 13));

            Row header = sheet.createRow(r++);
            Cell h0 = header.createCell(0);
            h0.setCellValue("Series");
            h0.setCellStyle(styles.headerStyle());
            for (int i = 0; i < 12; i++) {
                Cell c = header.createCell(i + 1);
                c.setCellValue(ReportExcelSupport.FY_MONTHS[i]);
                c.setCellStyle(styles.headerStyle());
            }
            Cell fy = header.createCell(13);
            fy.setCellValue("FY Total");
            fy.setCellStyle(styles.headerStyle());

            int baselineRow = r;
            Row baseline = sheet.createRow(r++);
            Row rolling = sheet.createRow(r++);
            Row deltaRow = sheet.createRow(r++);
            Row actuals = sheet.createRow(r++);
            baseline.createCell(0).setCellValue("Baseline");
            rolling.createCell(0).setCellValue("Rolling Forecast");
            deltaRow.createCell(0).setCellValue("Delta");
            actuals.createCell(0).setCellValue("Actuals");

            BigDecimal blTotal = BigDecimal.ZERO;
            BigDecimal rfTotal = BigDecimal.ZERO;
            BigDecimal dTotal = BigDecimal.ZERO;
            BigDecimal aTotal = BigDecimal.ZERO;
            // Revenue / EBITDA / Billable HC: positive delta is favourable.
            // Total Payroll Cost: negative delta (under baseline cost) is favourable.
            boolean higherIsBetter = metric.name().equals("Revenue") || metric.name().equals("EBITDA")
                    || metric.name().contains("HC");

            for (int i = 0; i < 12; i++) {
                MonthlyFinancials rm = rf.months().get(i);
                MonthlyFinancials dm = delta.months().get(i);
                BigDecimal rollingVal = ReportExcelSupport.nz(metric.rolling().apply(rm));
                BigDecimal deltaVal = ReportExcelSupport.nz(metric.delta().apply(dm));
                BigDecimal baselineVal = rollingVal.subtract(deltaVal);
                BigDecimal actualVal = rm.fromActuals() ? rollingVal : null;

                ReportExcelSupport.setMoney(baseline.createCell(i + 1), baselineVal, styles.dataStyle());
                ReportExcelSupport.setMoney(rolling.createCell(i + 1), rollingVal, styles.dataStyle());
                ReportExcelSupport.setMoney(deltaRow.createCell(i + 1), deltaVal,
                        ReportExcelSupport.varianceMoneyStyle(styles, deltaVal, higherIsBetter));
                ReportExcelSupport.setMoney(actuals.createCell(i + 1), actualVal, styles.dataStyle());

                blTotal = blTotal.add(baselineVal);
                rfTotal = rfTotal.add(rollingVal);
                dTotal = dTotal.add(deltaVal);
                if (actualVal != null) {
                    aTotal = aTotal.add(actualVal);
                }
            }
            ReportExcelSupport.setMoney(baseline.createCell(13), blTotal, styles.totalRowStyle());
            ReportExcelSupport.setMoney(rolling.createCell(13), rfTotal, styles.totalRowStyle());
            ReportExcelSupport.setMoney(deltaRow.createCell(13), dTotal,
                    ReportExcelSupport.varianceMoneyStyle(styles, dTotal, higherIsBetter));
            ReportExcelSupport.setMoney(actuals.createCell(13), aTotal, styles.totalRowStyle());

            ReportExcelSupport.addLineChart(sheet, metric.name() + " — Baseline vs Rolling vs Actuals",
                    baselineRow - 1, 1, 12,
                    List.of(
                            ReportExcelSupport.LineSeriesRef.of("Baseline", baselineRow, 1, 12, true, PresetColor.GRAY),
                            ReportExcelSupport.LineSeriesRef.of("Rolling Forecast", baselineRow + 1, 1, 12, false, PresetColor.RED),
                            ReportExcelSupport.LineSeriesRef.of("Actuals", baselineRow + 3, 1, 12, false, PresetColor.GREEN)
                    ),
                    0, r + 1, 10, r + 16);
            r += 18;
        }
        ReportExcelSupport.writeVarianceLegend(sheet, styles, r, cols);
        ReportExcelSupport.applySheetDefaults(sheet, date, cols);
    }

    // ── Report 6: Expense Summary ────────────────────────────────────────────

    private ReportFile expenseSummary(UUID planId, PeriodGranularity g, Integer month, Integer year, Integer quarter) {
        PlanVsActualResult pva = budgetingService.getPlanVsActual(planId, null, g, month, year, quarter);
        List<OverheadLineItem> catalog = budgetingService.listOverheadLineItems();
        String date = ReportExcelSupport.today();
        byte[] bytes = ReportExcelSupport.writeWorkbook(wb -> {
            var styles = ReportExcelSupport.createStyles(wb);
            ReportExcelSupport.writeHowToReadSheet(wb, styles,
                    "Report period: " + pva.periodLabel() + " (" + pva.granularity() + "). Financial Year "
                            + pva.fiscalYear() + ".");
            writeExpenseByGroup(wb, styles, pva, catalog, date);
            writeExpenseLineDetail(wb, styles, pva, catalog, date);
            wb.setSheetOrder("How to Read This Report", 0);
            wb.setSheetOrder("By Category Group", 1);
            wb.setSheetOrder("Line Item Detail", 2);
        });
        return new ReportFile(filename("expense_summary", pva.periodLabel(), date), bytes);
    }

    private void writeExpenseByGroup(XSSFWorkbook wb, ReportExcelSupport.Styles styles,
                                     PlanVsActualResult pva, List<OverheadLineItem> catalog, String date) {
        XSSFSheet sheet = wb.createSheet("By Category Group");
        int cols = 6;
        int signCol = 5;
        ReportExcelSupport.writeTitle(sheet, styles,
                "Cognologix — Expense Summary by Category Group — " + pva.periodLabel(), cols);
        Row header = sheet.createRow(2);
        ReportExcelSupport.writeHeaders(header, styles,
                "Category Group", "Budget (Rs L)", "Actual (Rs L)", "Variance (Rs L)", "Variance %",
                "Sign Convention");

        Map<String, MoneyTriad> byGroup = aggregateOverheadByGroup(pva, catalog);
        // DB catalog uses "People and Welfare" etc.; display labels match the report spec.
        String[][] groups = {
                {"Facilities", "Facilities"},
                {"Technology", "Technology"},
                {"People and Welfare", "People & Welfare"},
                {"Travel and Transport", "Travel & Transport"},
                {"Finance and Legal", "Finance & Legal"},
                {"Delivery Costs", "Delivery Costs"}
        };
        int r = 3;
        int start = r;
        BigDecimal planTotal = BigDecimal.ZERO;
        BigDecimal actualTotal = BigDecimal.ZERO;
        for (String[] group : groups) {
            MoneyTriad t = byGroup.getOrDefault(group[0], new MoneyTriad(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
            r = writeExpenseRow(sheet, styles, r, group[1], t, false);
            planTotal = planTotal.add(ReportExcelSupport.nz(t.plan()));
            actualTotal = actualTotal.add(ReportExcelSupport.nz(t.actual()));
        }
        MoneyTriad total = new MoneyTriad(planTotal, actualTotal, actualTotal.subtract(planTotal));
        r = writeExpenseRow(sheet, styles, r, "Total", total, true);

        r = ReportExcelSupport.writeVarianceLegend(sheet, styles, r, cols);

        ReportExcelSupport.addClusteredBarChartByColumns(sheet, "Budget vs Actual per category group",
                0, start, start + groups.length - 1,
                new int[]{1, 2}, new String[]{"Budget", "Actual"},
                0, r + 1, 8, r + 16);
        ReportExcelSupport.applySheetDefaults(sheet, date, cols);
        ReportExcelSupport.setSignConventionColumnWidth(sheet, signCol);
    }

    private void writeExpenseLineDetail(XSSFWorkbook wb, ReportExcelSupport.Styles styles,
                                        PlanVsActualResult pva, List<OverheadLineItem> catalog, String date) {
        XSSFSheet sheet = wb.createSheet("Line Item Detail");
        int cols = 7;
        int signCol = 6;
        ReportExcelSupport.writeTitle(sheet, styles,
                "Cognologix — Expense Line Item Detail — " + pva.periodLabel(), cols);

        Map<String, MoneyTriad> byLine = aggregateOverheadByLine(pva);
        Map<String, List<OverheadLineItem>> byGroup = catalog.stream()
                .collect(Collectors.groupingBy(OverheadLineItem::getCategory, LinkedHashMap::new, Collectors.toList()));

        int r = 2;
        List<Map.Entry<String, BigDecimal>> actualAmounts = new ArrayList<>();
        for (Map.Entry<String, List<OverheadLineItem>> e : byGroup.entrySet()) {
            Row section = sheet.createRow(r++);
            Cell sc = section.createCell(0);
            sc.setCellValue(e.getKey());
            sc.setCellStyle(styles.sectionHeaderStyle());
            sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(r - 1, r - 1, 0, 6));

            Row header = sheet.createRow(r++);
            ReportExcelSupport.writeHeaders(header, styles,
                    "Category Group", "Line Item", "Budget (Rs L)", "Actual (Rs L)",
                    "Variance (Rs L)", "Variance %", "Sign Convention");

            for (OverheadLineItem item : e.getValue()) {
                MoneyTriad t = byLine.getOrDefault(item.getLineCode(),
                        new MoneyTriad(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
                Row row = sheet.createRow(r++);
                ReportExcelSupport.setText(row.createCell(0), item.getCategory(), styles.dataStyle());
                ReportExcelSupport.setText(row.createCell(1), item.getDisplayName(), styles.dataStyle());
                ReportExcelSupport.setMoney(row.createCell(2), t.plan(), styles.dataStyle());
                ReportExcelSupport.setMoney(row.createCell(3), t.actual(), styles.dataStyle());
                // Overhead cost lines: higherIsBetter=false → green when Actual < Budget
                ReportExcelSupport.setMoney(row.createCell(4), t.variance(),
                        ReportExcelSupport.varianceMoneyStyle(styles, t.variance(), false));
                ReportExcelSupport.setPct(row.createCell(5),
                        ReportExcelSupport.variancePct(t.variance(), t.plan()),
                        ReportExcelSupport.variancePctStyle(styles, t.variance(), false));
                ReportExcelSupport.writeSignConventionText(row.createCell(6), styles, ReportExcelSupport.LOWER_BETTER);
                actualAmounts.add(Map.entry(item.getDisplayName(), ReportExcelSupport.nz(t.actual())));
            }
            r++;
        }

        r = ReportExcelSupport.writeVarianceLegend(sheet, styles, r, cols);

        // Top 10 chart data
        List<Map.Entry<String, BigDecimal>> top10 = actualAmounts.stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .limit(10)
                .toList();
        int chartRow = r + 1;
        Row ch = sheet.createRow(chartRow);
        ch.createCell(0).setCellValue("Line Item");
        ch.createCell(1).setCellValue("Actual");
        for (int i = 0; i < top10.size(); i++) {
            Row row = sheet.createRow(chartRow + 1 + i);
            row.createCell(0).setCellValue(top10.get(i).getKey());
            row.createCell(1).setCellValue(top10.get(i).getValue().doubleValue());
        }
        if (!top10.isEmpty()) {
            ReportExcelSupport.addClusteredBarChartByColumns(sheet, "Top 10 expense line items by actual",
                    0, chartRow + 1, chartRow + top10.size(),
                    new int[]{1}, new String[]{"Actual"},
                    0, chartRow + top10.size() + 2, 8, chartRow + top10.size() + 17);
        }
        ReportExcelSupport.applySheetDefaults(sheet, date, cols);
        ReportExcelSupport.setSignConventionColumnWidth(sheet, signCol);
    }

    private Map<String, MoneyTriad> aggregateOverheadByGroup(
            PlanVsActualResult pva, List<OverheadLineItem> catalog) {
        Map<String, String> lineToGroup = catalog.stream()
                .collect(Collectors.toMap(OverheadLineItem::getLineCode, OverheadLineItem::getCategory));
        Map<String, BigDecimal> plan = new LinkedHashMap<>();
        Map<String, BigDecimal> actual = new LinkedHashMap<>();
        List<MonthlyPlanVsActual> months = monthsInScope(pva);
        for (MonthlyPlanVsActual m : months) {
            for (TriadOverhead o : m.overhead()) {
                String group = lineToGroup.getOrDefault(o.lineCode(), "Other");
                plan.merge(group, ReportExcelSupport.nz(o.amount().plan()), BigDecimal::add);
                actual.merge(group, ReportExcelSupport.nz(o.amount().actual()), BigDecimal::add);
            }
        }
        Map<String, MoneyTriad> result = new LinkedHashMap<>();
        for (String group : plan.keySet()) {
            BigDecimal p = plan.getOrDefault(group, BigDecimal.ZERO);
            BigDecimal a = actual.getOrDefault(group, BigDecimal.ZERO);
            result.put(group, new MoneyTriad(p, a, a.subtract(p)));
        }
        for (String group : actual.keySet()) {
            result.computeIfAbsent(group, g -> {
                BigDecimal a = actual.get(g);
                return new MoneyTriad(BigDecimal.ZERO, a, a);
            });
        }
        return result;
    }

    private Map<String, MoneyTriad> aggregateOverheadByLine(PlanVsActualResult pva) {
        Map<String, BigDecimal> plan = new LinkedHashMap<>();
        Map<String, BigDecimal> actual = new LinkedHashMap<>();
        for (MonthlyPlanVsActual m : monthsInScope(pva)) {
            for (TriadOverhead o : m.overhead()) {
                plan.merge(o.lineCode(), ReportExcelSupport.nz(o.amount().plan()), BigDecimal::add);
                actual.merge(o.lineCode(), ReportExcelSupport.nz(o.amount().actual()), BigDecimal::add);
            }
        }
        Map<String, MoneyTriad> result = new LinkedHashMap<>();
        for (String code : plan.keySet()) {
            BigDecimal p = plan.getOrDefault(code, BigDecimal.ZERO);
            BigDecimal a = actual.getOrDefault(code, BigDecimal.ZERO);
            result.put(code, new MoneyTriad(p, a, a.subtract(p)));
        }
        return result;
    }

    private List<MonthlyPlanVsActual> monthsInScope(PlanVsActualResult pva) {
        if ("MONTHLY".equals(pva.granularity())) {
            return List.of(findMonthForPeriod(pva));
        }
        if ("QUARTERLY".equals(pva.granularity())) {
            return monthsForQuarter(pva);
        }
        return pva.months();
    }

    private int writeExpenseRow(XSSFSheet sheet, ReportExcelSupport.Styles styles, int r,
                                String label, MoneyTriad t, boolean total) {
        Row row = sheet.createRow(r);
        CellStyle style = total ? styles.totalRowStyle() : styles.dataStyle();
        ReportExcelSupport.setText(row.createCell(0), label, style);
        ReportExcelSupport.setMoney(row.createCell(1), t.plan(), style);
        ReportExcelSupport.setMoney(row.createCell(2), t.actual(), style);
        // Overhead / expense groups: higherIsBetter=false → green when Actual < Budget
        ReportExcelSupport.setMoney(row.createCell(3), t.variance(),
                ReportExcelSupport.varianceMoneyStyle(styles, t.variance(), false));
        ReportExcelSupport.setPct(row.createCell(4),
                ReportExcelSupport.variancePct(t.variance(), t.plan()),
                ReportExcelSupport.variancePctStyle(styles, t.variance(), false));
        ReportExcelSupport.writeSignConventionText(row.createCell(5), styles, ReportExcelSupport.LOWER_BETTER);
        return r + 1;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static int quarterForMonth(int month) {
        if (month >= 4 && month <= 6) return 1;
        if (month >= 7 && month <= 9) return 2;
        if (month >= 10 && month <= 12) return 3;
        return 4;
    }

    private static String monthLabel(int month, int year) {
        String[] names = {
                "", "January", "February", "March", "April", "May", "June",
                "July", "August", "September", "October", "November", "December"
        };
        return names[month] + " " + year;
    }

    private static String filename(String reportName, String period, String date) {
        String safePeriod = period == null ? "report"
                : period.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
        return reportName + "_" + safePeriod + "_" + date + ".xlsx";
    }
}
