package com.cognologix.fpa.reports;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.PrintSetup;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xddf.usermodel.PresetColor;
import org.apache.poi.xddf.usermodel.XDDFColor;
import org.apache.poi.xddf.usermodel.XDDFLineProperties;
import org.apache.poi.xddf.usermodel.XDDFPresetLineDash;
import org.apache.poi.xddf.usermodel.XDDFShapeProperties;
import org.apache.poi.xddf.usermodel.XDDFSolidFillProperties;
import org.apache.poi.xddf.usermodel.PresetLineDash;
import org.apache.poi.xddf.usermodel.chart.AxisCrossBetween;
import org.apache.poi.xddf.usermodel.chart.AxisPosition;
import org.apache.poi.xddf.usermodel.chart.BarDirection;
import org.apache.poi.xddf.usermodel.chart.BarGrouping;
import org.apache.poi.xddf.usermodel.chart.ChartTypes;
import org.apache.poi.xddf.usermodel.chart.LegendPosition;
import org.apache.poi.xddf.usermodel.chart.MarkerStyle;
import org.apache.poi.xddf.usermodel.chart.XDDFBarChartData;
import org.apache.poi.xddf.usermodel.chart.XDDFCategoryAxis;
import org.apache.poi.xddf.usermodel.chart.XDDFChartData;
import org.apache.poi.xddf.usermodel.chart.XDDFChartLegend;
import org.apache.poi.xddf.usermodel.chart.XDDFDataSourcesFactory;
import org.apache.poi.xddf.usermodel.chart.XDDFLineChartData;
import org.apache.poi.xddf.usermodel.chart.XDDFNumericalDataSource;
import org.apache.poi.xddf.usermodel.chart.XDDFPieChartData;
import org.apache.poi.xddf.usermodel.chart.XDDFValueAxis;
import org.apache.poi.xssf.usermodel.DefaultIndexedColorMap;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFChart;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Consumer;

/**
 * Shared XSSF styles, print/footer helpers, and chart builders for Standard Reports.
 */
final class ReportExcelSupport {

    static final String[] FY_MONTHS = {
            "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec", "Jan", "Feb", "Mar"
    };

    private ReportExcelSupport() {}

    /** Narrow width (characters) for the Sign Convention explanation column. */
    static final int SIGN_CONVENTION_WIDTH_CHARS = 15;

    static final String HIGHER_BETTER = "↑ Higher = Better";
    static final String LOWER_BETTER = "↓ Lower = Better";

    record Styles(
            CellStyle titleStyle,
            CellStyle subtitleStyle,
            CellStyle headerStyle,
            CellStyle dataStyle,
            CellStyle dataPctStyle,
            CellStyle dataIntStyle,
            CellStyle positiveVarianceStyle,
            CellStyle positiveVariancePctStyle,
            CellStyle negativeVarianceStyle,
            CellStyle negativeVariancePctStyle,
            CellStyle totalRowStyle,
            CellStyle totalRowPctStyle,
            CellStyle sectionHeaderStyle,
            CellStyle boldDataStyle,
            CellStyle planOnlyBgStyle,
            CellStyle actualsBgStyle,
            /** Light-grey italic text for the Sign Convention column. */
            CellStyle signConventionStyle,
            /** Normal (non-money) text on total-row grey background — used by legend body rows. */
            CellStyle legendBodyStyle
    ) {}

    static Styles createStyles(XSSFWorkbook wb) {
        XSSFColor red = rgb(wb, 0xF0, 0x57, 0x56);
        XSSFColor dark = rgb(wb, 0x23, 0x23, 0x23);
        XSSFColor orange = rgb(wb, 0xF6, 0x8C, 0x45);
        XSSFColor lightGreen = rgb(wb, 0xE6, 0xF4, 0xEA);
        XSSFColor greenText = rgb(wb, 0x1E, 0x7E, 0x34);
        XSSFColor lightRed = rgb(wb, 0xFD, 0xEC, 0xEA);
        XSSFColor redText = rgb(wb, 0xC6, 0x28, 0x28);
        XSSFColor lightGrey = rgb(wb, 0xF5, 0xF5, 0xF5);
        XSSFColor planOnly = rgb(wb, 0xFA, 0xFA, 0xFA);
        XSSFColor actualsBg = rgb(wb, 0xE8, 0xF0, 0xFE);
        XSSFColor white = rgb(wb, 0xFF, 0xFF, 0xFF);

        Font whiteBold16 = font(wb, true, 16, white);
        Font whiteBold12 = font(wb, true, 12, white);
        Font whiteBold11 = font(wb, true, 11, white);
        Font dataFont = font(wb, false, 10, null);
        Font bold10 = font(wb, true, 10, null);
        Font greenBold = font(wb, true, 10, greenText);
        Font redBold = font(wb, true, 10, redText);
        Font italic10 = font(wb, false, 10, null);
        italic10.setItalic(true);

        CellStyle title = base(wb, whiteBold16, red, HorizontalAlignment.LEFT, false);
        CellStyle subtitle = base(wb, whiteBold12, dark, HorizontalAlignment.LEFT, false);
        CellStyle header = base(wb, whiteBold11, orange, HorizontalAlignment.CENTER, true);
        CellStyle data = moneyStyle(wb, dataFont, white, false);
        CellStyle dataPct = pctStyle(wb, dataFont, white, false);
        CellStyle dataInt = intStyle(wb, dataFont, white, false);
        CellStyle pos = moneyStyle(wb, greenBold, lightGreen, true);
        CellStyle posPct = pctStyle(wb, greenBold, lightGreen, true);
        CellStyle neg = moneyStyle(wb, redBold, lightRed, true);
        CellStyle negPct = pctStyle(wb, redBold, lightRed, true);
        CellStyle total = moneyStyle(wb, bold10, lightGrey, true);
        CellStyle totalPct = pctStyle(wb, bold10, lightGrey, true);
        CellStyle section = base(wb, whiteBold11, dark, HorizontalAlignment.LEFT, true);
        CellStyle boldData = moneyStyle(wb, bold10, white, true);
        CellStyle planOnlyStyle = moneyStyle(wb, dataFont, planOnly, true);
        CellStyle actualsStyle = moneyStyle(wb, dataFont, actualsBg, true);
        CellStyle signConv = base(wb, italic10, lightGrey, HorizontalAlignment.LEFT, true);
        CellStyle legendBody = base(wb, dataFont, lightGrey, HorizontalAlignment.LEFT, true);

        return new Styles(title, subtitle, header, data, dataPct, dataInt,
                pos, posPct, neg, negPct, total, totalPct, section, boldData,
                planOnlyStyle, actualsStyle, signConv, legendBody);
    }

    static byte[] writeWorkbook(Consumer<XSSFWorkbook> writer) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            writer.accept(wb);
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write Excel report", e);
        }
    }

    static void applySheetDefaults(XSSFSheet sheet, String generatedDate, int colCount) {
        sheet.createFreezePane(0, 1);
        PrintSetup ps = sheet.getPrintSetup();
        ps.setLandscape(true);
        ps.setFitWidth((short) 1);
        ps.setFitHeight((short) 0);
        ps.setPaperSize(PrintSetup.A4_PAPERSIZE);
        sheet.setFitToPage(true);
        sheet.getFooter().setLeft("Cognologix Technologies — Confidential");
        sheet.getFooter().setRight("Generated: " + generatedDate);
        for (int i = 0; i < colCount; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    static void writeTitle(XSSFSheet sheet, Styles styles, String title, int mergeCols) {
        Row row = sheet.createRow(0);
        Cell cell = row.createCell(0);
        cell.setCellValue(title);
        cell.setCellStyle(styles.titleStyle());
        if (mergeCols > 1) {
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, mergeCols - 1));
        }
    }

    static void writeSubtitle(XSSFSheet sheet, Styles styles, int rowIdx, String text, int mergeCols) {
        Row row = sheet.createRow(rowIdx);
        Cell cell = row.createCell(0);
        cell.setCellValue(text);
        cell.setCellStyle(styles.subtitleStyle());
        if (mergeCols > 1) {
            sheet.addMergedRegion(new CellRangeAddress(rowIdx, rowIdx, 0, mergeCols - 1));
        }
    }

    static void writeHeaders(Row row, Styles styles, String... headers) {
        for (int i = 0; i < headers.length; i++) {
            Cell c = row.createCell(i);
            c.setCellValue(headers[i]);
            c.setCellStyle(styles.headerStyle());
        }
    }

    static void setMoney(Cell cell, BigDecimal value, CellStyle style) {
        cell.setCellStyle(style);
        if (value != null) {
            cell.setCellValue(value.doubleValue());
        }
    }

    static void setPct(Cell cell, BigDecimal pctPoints, CellStyle style) {
        cell.setCellStyle(style);
        if (pctPoints != null) {
            cell.setCellValue(pctPoints.divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP).doubleValue());
        }
    }

    static void setInt(Cell cell, int value, CellStyle style) {
        cell.setCellStyle(style);
        cell.setCellValue(value);
    }

    static void setText(Cell cell, String value, CellStyle style) {
        cell.setCellStyle(style);
        cell.setCellValue(value != null ? value : "");
    }

    /**
     * Variance colour convention (variance = Actual − Plan):
     * <ul>
     *   <li><b>Revenue / profit rows</b> ({@code higherIsBetter=true}): green when variance &gt; 0
     *       (Actual &gt; Plan); red when variance &lt; 0 (Actual &lt; Plan).</li>
     *   <li><b>Cost rows</b> — COGS, OpEx, Payroll, Overhead ({@code higherIsBetter=false}):
     *       green when variance &lt; 0 (Actual &lt; Plan = under-budget = favourable);
     *       red when variance &gt; 0 (Actual &gt; Plan = over-budget = unfavourable).</li>
     * </ul>
     * Under-budget COGS (negative variance) is favourable and must be green — do not invert
     * cost-row polarity because COGS reduces Gross Profit.
     */
    static CellStyle varianceMoneyStyle(Styles styles, BigDecimal variance, boolean higherIsBetter) {
        if (variance == null || variance.compareTo(BigDecimal.ZERO) == 0) {
            return styles.dataStyle();
        }
        boolean favorable = isFavorableVariance(variance, higherIsBetter);
        return favorable ? styles.positiveVarianceStyle() : styles.negativeVarianceStyle();
    }

    /** Same polarity rules as {@link #varianceMoneyStyle} for percentage-formatted variance cells. */
    static CellStyle variancePctStyle(Styles styles, BigDecimal variance, boolean higherIsBetter) {
        if (variance == null || variance.compareTo(BigDecimal.ZERO) == 0) {
            return styles.dataPctStyle();
        }
        boolean favorable = isFavorableVariance(variance, higherIsBetter);
        return favorable ? styles.positiveVariancePctStyle() : styles.negativeVariancePctStyle();
    }

    /**
     * @param higherIsBetter {@code true} for revenue/profit/margin/billable-ratio metrics;
     *                       {@code false} for cost metrics (COGS, OpEx, payroll, overhead)
     */
    static boolean isFavorableVariance(BigDecimal variance, boolean higherIsBetter) {
        return higherIsBetter
                ? variance.compareTo(BigDecimal.ZERO) > 0
                : variance.compareTo(BigDecimal.ZERO) < 0;
    }

    /**
     * Resolves the Sign Convention cell text for a P&amp;L / HC / overhead line label.
     * Cost-like labels → lower is better; revenue / profit / billable → higher is better.
     */
    static String signConventionFor(String lineItem) {
        if (lineItem == null || lineItem.isBlank()) {
            return "";
        }
        String key = lineItem.trim().toLowerCase();
        if (key.contains("cogs") || key.contains("opex") || key.contains("payroll")
                || key.contains("overhead") || key.startsWith("layer")) {
            return LOWER_BETTER;
        }
        if (key.equals("facilities") || key.equals("technology") || key.equals("people & welfare")
                || key.equals("people and welfare") || key.equals("travel & transport")
                || key.equals("travel and transport") || key.equals("finance & legal")
                || key.equals("finance and legal") || key.equals("delivery costs")
                || key.equals("total")) {
            // Expense-summary "Total" and category groups are cost lines
            return LOWER_BETTER;
        }
        if (key.contains("revenue") || key.contains("gross profit") || key.contains("gross margin")
                || key.contains("ebitda") || key.contains("billable hc")
                || key.contains("billable ratio") || key.equals("billable")) {
            return HIGHER_BETTER;
        }
        return "";
    }

    static void writeSignConvention(Cell cell, Styles styles, String lineItem) {
        setText(cell, signConventionFor(lineItem), styles.signConventionStyle());
    }

    static void writeSignConventionText(Cell cell, Styles styles, String text) {
        setText(cell, text != null ? text : "", styles.signConventionStyle());
    }

    /**
     * Compact Color Guide block (3–4 rows) after a blank separator from the data table.
     *
     * @return next free row index after the legend
     */
    static int writeVarianceLegend(XSSFSheet sheet, Styles styles, int startRow, int mergeCols) {
        int r = startRow + 1; // blank separator row
        Row header = sheet.createRow(r++);
        Cell hc = header.createCell(0);
        hc.setCellValue("Color Guide:");
        hc.setCellStyle(styles.sectionHeaderStyle());
        if (mergeCols > 1) {
            sheet.addMergedRegion(new CellRangeAddress(r - 1, r - 1, 0, mergeCols - 1));
        }

        Row row1 = sheet.createRow(r++);
        Cell c1 = row1.createCell(0);
        c1.setCellValue("Green background = Favorable variance    |    Red background = Unfavorable variance");
        c1.setCellStyle(styles.legendBodyStyle());
        if (mergeCols > 1) {
            sheet.addMergedRegion(new CellRangeAddress(r - 1, r - 1, 0, mergeCols - 1));
        }

        Row row2 = sheet.createRow(r++);
        Cell c2 = row2.createCell(0);
        c2.setCellValue("Favorable means: Revenue / Gross Profit / EBITDA / Gross Margin % / Billable Ratio % → Actual HIGHER than Plan is good (green).  "
                + "COGS / OpEx / Total Payroll Cost / Overhead → Actual LOWER than Plan is good (green).");
        c2.setCellStyle(styles.legendBodyStyle());
        if (mergeCols > 1) {
            sheet.addMergedRegion(new CellRangeAddress(r - 1, r - 1, 0, mergeCols - 1));
        }
        return r;
    }

    /** Legend for BU Gross Margin % conditional formatting thresholds. */
    static int writeGmConditionalLegend(XSSFSheet sheet, Styles styles, int startRow, int mergeCols) {
        int r = startRow + 1;
        Row header = sheet.createRow(r++);
        Cell hc = header.createCell(0);
        hc.setCellValue("Color Guide:");
        hc.setCellStyle(styles.sectionHeaderStyle());
        if (mergeCols > 1) {
            sheet.addMergedRegion(new CellRangeAddress(r - 1, r - 1, 0, mergeCols - 1));
        }

        Row row1 = sheet.createRow(r++);
        Cell c1 = row1.createCell(0);
        c1.setCellValue("Green background = Gross Margin % above 30% (strong)    |    Red background = Gross Margin % below 15% (weak)");
        c1.setCellStyle(styles.legendBodyStyle());
        if (mergeCols > 1) {
            sheet.addMergedRegion(new CellRangeAddress(r - 1, r - 1, 0, mergeCols - 1));
        }

        Row row2 = sheet.createRow(r++);
        Cell c2 = row2.createCell(0);
        c2.setCellValue("No colour = Gross Margin % between 15% and 30% (acceptable range).");
        c2.setCellStyle(styles.legendBodyStyle());
        if (mergeCols > 1) {
            sheet.addMergedRegion(new CellRangeAddress(r - 1, r - 1, 0, mergeCols - 1));
        }
        return r;
    }

    static void setSignConventionColumnWidth(XSSFSheet sheet, int colIndex) {
        sheet.setColumnWidth(colIndex, SIGN_CONVENTION_WIDTH_CHARS * 256);
    }

    /**
     * First sheet of every Standard Report workbook — Colour Guide + Metric Definitions
     * plus data-source / formula context.
     */
    static void writeHowToReadSheet(XSSFWorkbook wb, Styles styles, String periodDescription) {
        XSSFSheet sheet = wb.createSheet("How to Read This Report");
        int r = 0;

        Row title = sheet.createRow(r++);
        Cell titleCell = title.createCell(0);
        titleCell.setCellValue("How to Read This Report");
        titleCell.setCellStyle(styles.titleStyle());
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 3));
        r++;

        r = writeHowToSection(sheet, styles, r, "1. Data Sources");
        r = writeHowToHeader(sheet, styles, r, "Data Type", "Source");
        r = writeHowToBody(sheet, styles, r, "Revenue Actuals", "Zoho Books invoices via Revenue module");
        r = writeHowToBody(sheet, styles, r, "Salary / Payroll Actuals",
                "People & Payroll finalised periods — gross pay + employer contributions");
        r = writeHowToBody(sheet, styles, r, "Overhead Actuals", "Expenses module — entered by Finance");
        r = writeHowToBody(sheet, styles, r, "Plan / Budget figures",
                "Budgeting & Forecasting Plan Setup — entered by Finance");
        r++;

        r = writeHowToSection(sheet, styles, r, "2. Key Formulas");
        r = writeHowToHeader(sheet, styles, r, "Metric", "Formula", "Notes");
        r = writeHowToBody(sheet, styles, r, "COGS",
                "Billable Payroll + Bench Payroll + Delivery Overheads", "");
        r = writeHowToBody(sheet, styles, r, "Gross Profit", "Revenue − COGS", "");
        r = writeHowToBody(sheet, styles, r, "OpEx",
                "Support + Leadership + Management Payroll + Non-Delivery Overheads + Variable Pay", "");
        r = writeHowToBody(sheet, styles, r, "EBITDA", "Gross Profit − OpEx", "");
        r = writeHowToBody(sheet, styles, r, "Total Payroll Cost",
                "Gross Pay + EPF + EPS + EDLI + EPF Admin + VPF + NPS + Gratuity",
                "Plan uses 13% estimate; Actuals use Zoho contribution data");
        r = writeHowToBody(sheet, styles, r, "Billable Ratio %", "Billable HC ÷ Total HC × 100", "");
        r++;

        r = writeHowToSection(sheet, styles, r, "3. Variance Sign");
        r = writeHowToBody(sheet, styles, r, "Variance",
                "Always Actual − Plan (or Rolling − Baseline for Delta). See Colour Guide for favourable direction.");
        r++;

        r = writeHowToSection(sheet, styles, r, "4. Period");
        Row periodRow = sheet.createRow(r++);
        Cell periodCell = periodRow.createCell(0);
        periodCell.setCellValue(periodDescription != null ? periodDescription : "");
        periodCell.setCellStyle(styles.legendBodyStyle());
        sheet.addMergedRegion(new CellRangeAddress(r - 1, r - 1, 0, 3));
        r++;

        r = writeHowToSection(sheet, styles, r, "5. Color Guide");
        r = writeHowToBody(sheet, styles, r, "Green",
                "The actual figure is better than planned for that metric type");
        r = writeHowToBody(sheet, styles, r, "Red",
                "The actual figure is worse than planned for that metric type");
        r = writeHowToBody(sheet, styles, r, "Cost metrics (COGS, OpEx, Overhead)",
                "Spending LESS than planned = green (under-budget is favourable)");
        r = writeHowToBody(sheet, styles, r, "Revenue / profit metrics",
                "Earning MORE than planned = green");
        r = writeHowToBody(sheet, styles, r, "COGS note",
                "COGS reduces Gross Profit (Revenue − COGS). A smaller COGS number means lower costs which is good — "
                        + "negative COGS variance (Actual < Plan) is favourable and shown in green.");
        r++;

        r = writeHowToSection(sheet, styles, r, "6. Metric Definitions");
        r = writeHowToHeader(sheet, styles, r, "Metric Name", "What It Measures", "Formula", "Better When");
        for (String[] def : METRIC_DEFINITIONS) {
            r = writeHowToBody(sheet, styles, r, def[0], def[1], def[2], def[3]);
        }

        sheet.setColumnWidth(0, 36 * 256);
        sheet.setColumnWidth(1, 55 * 256);
        sheet.setColumnWidth(2, 70 * 256);
        sheet.setColumnWidth(3, 14 * 256);
    }

    private static final String[][] METRIC_DEFINITIONS = {
            {"Total Revenue", "Income from T&M and fixed-bid work", "T&M Revenue + Fixed-Bid Revenue", "Higher"},
            {"COGS", "Direct cost of delivering services",
                    "Billable Payroll + Bench Payroll + Delivery Overheads", "Lower"},
            {"Gross Profit", "Revenue remaining after direct costs", "Revenue − COGS", "Higher"},
            {"Gross Margin %", "Gross Profit as % of Revenue", "Gross Profit ÷ Revenue × 100", "Higher"},
            {"Total OpEx", "Operating expenses outside direct delivery",
                    "Support/Leadership/Management Payroll + Non-Delivery Overheads + Variable Pay", "Lower"},
            {"EBITDA", "Operating profitability", "Gross Profit − OpEx", "Higher"},
            {"EBITDA Margin %", "EBITDA as % of Revenue", "EBITDA ÷ Revenue × 100", "Higher"},
            {"Total Payroll Cost", "True cost of employees",
                    "Gross Pay + EPF + EPS + EDLI + EPF Admin + VPF + NPS + Gratuity", "Lower"},
            {"Overhead", "Non-payroll operating spend by category", "Sum of expense line items in group", "Lower"},
            {"Billable HC", "Headcount assigned to billable work", "Count of billable master records", "Higher"},
            {"Billable Ratio %", "Share of workforce that is billable", "Billable HC ÷ Total HC × 100", "Higher"},
    };

    private static int writeHowToSection(XSSFSheet sheet, Styles styles, int r, String title) {
        Row row = sheet.createRow(r);
        Cell cell = row.createCell(0);
        cell.setCellValue(title);
        cell.setCellStyle(styles.sectionHeaderStyle());
        sheet.addMergedRegion(new CellRangeAddress(r, r, 0, 3));
        return r + 1;
    }

    private static int writeHowToHeader(XSSFSheet sheet, Styles styles, int r, String... headers) {
        Row row = sheet.createRow(r);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(styles.headerStyle());
        }
        return r + 1;
    }

    private static int writeHowToBody(XSSFSheet sheet, Styles styles, int r, String... values) {
        Row row = sheet.createRow(r);
        for (int i = 0; i < values.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(values[i] != null ? values[i] : "");
            cell.setCellStyle(styles.legendBodyStyle());
        }
        return r + 1;
    }

    static BigDecimal variancePct(BigDecimal variance, BigDecimal plan) {
        if (variance == null || plan == null || plan.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return variance.multiply(new BigDecimal("100")).divide(plan, 2, RoundingMode.HALF_UP);
    }

    static BigDecimal marginPct(BigDecimal part, BigDecimal whole) {
        if (part == null || whole == null || whole.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return part.multiply(new BigDecimal("100")).divide(whole, 2, RoundingMode.HALF_UP);
    }

    static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    static String today() {
        return LocalDate.now().toString();
    }

    /** Clustered column chart. Categories in col 0 of rows [catStart..catEnd]; series values in valueCols. */
    static void addClusteredBarChart(
            XSSFSheet sheet, String title,
            int catCol, int catRowStart, int catRowEnd,
            List<SeriesRef> series,
            int anchorCol1, int anchorRow1, int anchorCol2, int anchorRow2) {
        XSSFDrawing drawing = sheet.createDrawingPatriarch();
        XSSFClientAnchor anchor = drawing.createAnchor(0, 0, 0, 0, anchorCol1, anchorRow1, anchorCol2, anchorRow2);
        XSSFChart chart = drawing.createChart(anchor);
        chart.setTitleText(title);
        chart.setTitleOverlay(false);
        XDDFChartLegend legend = chart.getOrAddLegend();
        legend.setPosition(LegendPosition.BOTTOM);

        XDDFCategoryAxis bottom = chart.createCategoryAxis(AxisPosition.BOTTOM);
        XDDFValueAxis left = chart.createValueAxis(AxisPosition.LEFT);
        left.setCrossBetween(AxisCrossBetween.BETWEEN);

        XDDFChartData data = chart.createData(ChartTypes.BAR, bottom, left);
        var cats = XDDFDataSourcesFactory.fromStringCellRange(sheet,
                new CellRangeAddress(catRowStart, catRowEnd, catCol, catCol));
        for (SeriesRef s : series) {
            XDDFNumericalDataSource<Double> vals = XDDFDataSourcesFactory.fromNumericCellRange(sheet,
                    new CellRangeAddress(s.rowStart(), s.rowEnd(), s.col(), s.col()));
            XDDFChartData.Series ser = data.addSeries(cats, vals);
            ser.setTitle(s.title(), null);
        }
        chart.plot(data);
        XDDFBarChartData bar = (XDDFBarChartData) data;
        bar.setBarDirection(BarDirection.COL);
        bar.setBarGrouping(BarGrouping.CLUSTERED);
    }

    /** Data laid out as header row + value rows where series are columns (more common for P&L). */
    static void addClusteredBarChartByColumns(
            XSSFSheet sheet, String title,
            int catCol, int catRowStart, int catRowEnd,
            int[] valueCols, String[] seriesTitles,
            int anchorCol1, int anchorRow1, int anchorCol2, int anchorRow2) {
        XSSFDrawing drawing = sheet.createDrawingPatriarch();
        XSSFClientAnchor anchor = drawing.createAnchor(0, 0, 0, 0, anchorCol1, anchorRow1, anchorCol2, anchorRow2);
        XSSFChart chart = drawing.createChart(anchor);
        chart.setTitleText(title);
        chart.setTitleOverlay(false);
        XDDFChartLegend legend = chart.getOrAddLegend();
        legend.setPosition(LegendPosition.BOTTOM);

        XDDFCategoryAxis bottom = chart.createCategoryAxis(AxisPosition.BOTTOM);
        XDDFValueAxis left = chart.createValueAxis(AxisPosition.LEFT);
        left.setCrossBetween(AxisCrossBetween.BETWEEN);

        XDDFChartData data = chart.createData(ChartTypes.BAR, bottom, left);
        var cats = XDDFDataSourcesFactory.fromStringCellRange(sheet,
                new CellRangeAddress(catRowStart, catRowEnd, catCol, catCol));
        for (int i = 0; i < valueCols.length; i++) {
            int col = valueCols[i];
            XDDFNumericalDataSource<Double> vals = XDDFDataSourcesFactory.fromNumericCellRange(sheet,
                    new CellRangeAddress(catRowStart, catRowEnd, col, col));
            XDDFChartData.Series ser = data.addSeries(cats, vals);
            ser.setTitle(seriesTitles[i], null);
        }
        chart.plot(data);
        XDDFBarChartData bar = (XDDFBarChartData) data;
        bar.setBarDirection(BarDirection.COL);
        bar.setBarGrouping(BarGrouping.CLUSTERED);
    }

    static void addStackedBarChartByColumns(
            XSSFSheet sheet, String title,
            int catCol, int catRowStart, int catRowEnd,
            int[] valueCols, String[] seriesTitles,
            int anchorCol1, int anchorRow1, int anchorCol2, int anchorRow2) {
        XSSFDrawing drawing = sheet.createDrawingPatriarch();
        XSSFClientAnchor anchor = drawing.createAnchor(0, 0, 0, 0, anchorCol1, anchorRow1, anchorCol2, anchorRow2);
        XSSFChart chart = drawing.createChart(anchor);
        chart.setTitleText(title);
        chart.setTitleOverlay(false);
        XDDFChartLegend legend = chart.getOrAddLegend();
        legend.setPosition(LegendPosition.BOTTOM);

        XDDFCategoryAxis bottom = chart.createCategoryAxis(AxisPosition.BOTTOM);
        XDDFValueAxis left = chart.createValueAxis(AxisPosition.LEFT);
        left.setCrossBetween(AxisCrossBetween.BETWEEN);

        XDDFChartData data = chart.createData(ChartTypes.BAR, bottom, left);
        var cats = XDDFDataSourcesFactory.fromStringCellRange(sheet,
                new CellRangeAddress(catRowStart, catRowEnd, catCol, catCol));
        for (int i = 0; i < valueCols.length; i++) {
            int col = valueCols[i];
            XDDFNumericalDataSource<Double> vals = XDDFDataSourcesFactory.fromNumericCellRange(sheet,
                    new CellRangeAddress(catRowStart, catRowEnd, col, col));
            data.addSeries(cats, vals).setTitle(seriesTitles[i], null);
        }
        chart.plot(data);
        XDDFBarChartData bar = (XDDFBarChartData) data;
        bar.setBarDirection(BarDirection.COL);
        bar.setBarGrouping(BarGrouping.STACKED);
        solidFillSeries(data, 0, PresetColor.FIREBRICK);
        if (valueCols.length > 1) {
            solidFillSeries(data, 1, PresetColor.DARK_ORANGE);
        }
        if (valueCols.length > 2) {
            solidFillSeries(data, 2, PresetColor.STEEL_BLUE);
        }
    }

    static void addPieChart(
            XSSFSheet sheet, String title,
            int catCol, int valCol, int rowStart, int rowEnd,
            int anchorCol1, int anchorRow1, int anchorCol2, int anchorRow2) {
        if (rowEnd < rowStart) {
            return;
        }
        XSSFDrawing drawing = sheet.createDrawingPatriarch();
        XSSFClientAnchor anchor = drawing.createAnchor(0, 0, 0, 0, anchorCol1, anchorRow1, anchorCol2, anchorRow2);
        XSSFChart chart = drawing.createChart(anchor);
        chart.setTitleText(title);
        chart.setTitleOverlay(false);
        XDDFChartLegend legend = chart.getOrAddLegend();
        legend.setPosition(LegendPosition.RIGHT);

        XDDFChartData data = chart.createData(ChartTypes.PIE, null, null);
        var cats = XDDFDataSourcesFactory.fromStringCellRange(sheet,
                new CellRangeAddress(rowStart, rowEnd, catCol, catCol));
        XDDFNumericalDataSource<Double> vals = XDDFDataSourcesFactory.fromNumericCellRange(sheet,
                new CellRangeAddress(rowStart, rowEnd, valCol, valCol));
        XDDFPieChartData.Series series = (XDDFPieChartData.Series) data.addSeries(cats, vals);
        series.setTitle(title, null);
        chart.plot(data);
    }

    static void addLineChart(
            XSSFSheet sheet, String title,
            int catRow, int catColStart, int catColEnd,
            List<LineSeriesRef> series,
            int anchorCol1, int anchorRow1, int anchorCol2, int anchorRow2) {
        XSSFDrawing drawing = sheet.createDrawingPatriarch();
        XSSFClientAnchor anchor = drawing.createAnchor(0, 0, 0, 0, anchorCol1, anchorRow1, anchorCol2, anchorRow2);
        XSSFChart chart = drawing.createChart(anchor);
        chart.setTitleText(title);
        chart.setTitleOverlay(false);
        XDDFChartLegend legend = chart.getOrAddLegend();
        legend.setPosition(LegendPosition.BOTTOM);

        XDDFCategoryAxis bottom = chart.createCategoryAxis(AxisPosition.BOTTOM);
        XDDFValueAxis left = chart.createValueAxis(AxisPosition.LEFT);
        left.setCrossBetween(AxisCrossBetween.BETWEEN);

        XDDFLineChartData data = (XDDFLineChartData) chart.createData(ChartTypes.LINE, bottom, left);
        var cats = XDDFDataSourcesFactory.fromStringCellRange(sheet,
                new CellRangeAddress(catRow, catRow, catColStart, catColEnd));
        int idx = 0;
        for (LineSeriesRef s : series) {
            XDDFNumericalDataSource<Double> vals = XDDFDataSourcesFactory.fromNumericCellRange(sheet,
                    new CellRangeAddress(s.row(), s.row(), s.colStart(), s.colEnd()));
            XDDFLineChartData.Series ser = (XDDFLineChartData.Series) data.addSeries(cats, vals);
            ser.setTitle(s.title(), null);
            ser.setSmooth(false);
            ser.setMarkerStyle(MarkerStyle.CIRCLE);
            if (s.dashed()) {
                XDDFLineProperties lineProps = new XDDFLineProperties();
                lineProps.setWidth(2.0);
                lineProps.setPresetDash(new XDDFPresetLineDash(PresetLineDash.DASH));
                lineProps.setFillProperties(new XDDFSolidFillProperties(XDDFColor.from(PresetColor.GRAY)));
                XDDFShapeProperties props = ser.getShapeProperties();
                if (props == null) {
                    props = new XDDFShapeProperties();
                }
                props.setLineProperties(lineProps);
                ser.setShapeProperties(props);
            } else if (s.color() != null) {
                solidFillSeries(data, idx, s.color());
            }
            idx++;
        }
        chart.plot(data);
    }

    record SeriesRef(String title, int col, int rowStart, int rowEnd) {}

    record LineSeriesRef(String title, int row, int colStart, int colEnd, boolean dashed, PresetColor color) {
        static LineSeriesRef of(String title, int row, int colStart, int colEnd, boolean dashed, PresetColor color) {
            return new LineSeriesRef(title, row, colStart, colEnd, dashed, color);
        }
    }

    private static void solidFillSeries(XDDFChartData data, int index, PresetColor color) {
        XDDFSolidFillProperties fill = new XDDFSolidFillProperties(XDDFColor.from(color));
        XDDFChartData.Series series = data.getSeries(index);
        XDDFShapeProperties props = series.getShapeProperties();
        if (props == null) {
            props = new XDDFShapeProperties();
        }
        props.setFillProperties(fill);
        series.setShapeProperties(props);
    }

    private static XSSFColor rgb(XSSFWorkbook wb, int r, int g, int b) {
        return new XSSFColor(new byte[]{(byte) r, (byte) g, (byte) b}, new DefaultIndexedColorMap());
    }

    private static Font font(XSSFWorkbook wb, boolean bold, int size, XSSFColor color) {
        Font f = wb.createFont();
        f.setBold(bold);
        f.setFontHeightInPoints((short) size);
        if (color != null) {
            ((org.apache.poi.xssf.usermodel.XSSFFont) f).setColor(color);
        }
        return f;
    }

    private static CellStyle base(
            XSSFWorkbook wb, Font font, XSSFColor bg, HorizontalAlignment align, boolean border) {
        XSSFCellStyle style = wb.createCellStyle();
        style.setFont(font);
        style.setFillForegroundColor(bg);
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(align);
        if (border) {
            applyBorder(style);
        }
        return style;
    }

    private static CellStyle moneyStyle(XSSFWorkbook wb, Font font, XSSFColor bg, boolean border) {
        XSSFCellStyle style = (XSSFCellStyle) base(wb, font, bg, HorizontalAlignment.RIGHT, border);
        style.setDataFormat(wb.createDataFormat().getFormat("#,##0.000"));
        return style;
    }

    private static CellStyle pctStyle(XSSFWorkbook wb, Font font, XSSFColor bg, boolean border) {
        XSSFCellStyle style = (XSSFCellStyle) base(wb, font, bg, HorizontalAlignment.RIGHT, border);
        style.setDataFormat(wb.createDataFormat().getFormat("0.00%"));
        return style;
    }

    private static CellStyle intStyle(XSSFWorkbook wb, Font font, XSSFColor bg, boolean border) {
        XSSFCellStyle style = (XSSFCellStyle) base(wb, font, bg, HorizontalAlignment.RIGHT, border);
        style.setDataFormat(wb.createDataFormat().getFormat("#,##0"));
        return style;
    }

    private static void applyBorder(CellStyle style) {
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
    }
}
