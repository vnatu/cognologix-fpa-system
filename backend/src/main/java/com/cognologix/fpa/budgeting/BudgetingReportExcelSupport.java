package com.cognologix.fpa.budgeting;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.ss.usermodel.Comment;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Drawing;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFRichTextString;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.function.Consumer;

/**
 * Shared Excel helpers for Budgeting analysis reports — How-to-Read sheet and cell comments.
 */
final class BudgetingReportExcelSupport {

    private BudgetingReportExcelSupport() {}

    record Styles(CellStyle title, CellStyle section, CellStyle header, CellStyle body, CellStyle money) {}

    static Styles createStyles(Workbook wb) {
        Font titleFont = wb.createFont();
        titleFont.setBold(true);
        titleFont.setFontHeightInPoints((short) 14);
        CellStyle title = wb.createCellStyle();
        title.setFont(titleFont);

        Font sectionFont = wb.createFont();
        sectionFont.setBold(true);
        sectionFont.setFontHeightInPoints((short) 12);
        CellStyle section = wb.createCellStyle();
        section.setFont(sectionFont);

        Font headerFont = wb.createFont();
        headerFont.setBold(true);
        CellStyle header = wb.createCellStyle();
        header.setFont(headerFont);

        CellStyle body = wb.createCellStyle();

        CellStyle money = wb.createCellStyle();
        money.setDataFormat(wb.createDataFormat().getFormat("#,##0.000"));
        return new Styles(title, section, header, body, money);
    }

    static void writeHowToReadSheet(
            Workbook wb,
            Styles styles,
            String periodDescription,
            int monthsWithActuals,
            int totalMonthsInScope) {
        Sheet sheet = wb.createSheet("How to Read This Report");
        int r = 0;

        Row title = sheet.createRow(r++);
        Cell titleCell = title.createCell(0);
        titleCell.setCellValue("How to Read This Report");
        titleCell.setCellStyle(styles.title());
        r++;

        r = writeSectionHeader(sheet, styles, r, "1. Data Sources");
        r = writeHeaderRow(sheet, styles, r, "Data Type", "Source");
        r = writeBodyRow(sheet, styles, r, "Revenue Actuals",
                "Zoho Books invoices via Revenue module");
        r = writeBodyRow(sheet, styles, r, "Salary Actuals",
                "People & Payroll finalised periods — gross pay + employer contributions");
        r = writeBodyRow(sheet, styles, r, "Overhead Actuals",
                "Expenses module — manually entered by Finance");
        r = writeBodyRow(sheet, styles, r, "Plan figures",
                "Budgeting & Forecasting Plan Setup — entered by Finance");
        r++;

        r = writeSectionHeader(sheet, styles, r, "2. Key Formulas");
        r = writeHeaderRow(sheet, styles, r, "Metric", "Formula", "What's Included", "Notes");
        for (BudgetingFormulaCatalog.FormulaRow row : BudgetingFormulaCatalog.KEY_FORMULAS) {
            r = writeBodyRow(sheet, styles, r, row.metric(), row.formula(),
                    row.components() == null || row.components().isBlank() ? "—" : row.components(),
                    row.notes() == null || row.notes().isBlank() ? "—" : row.notes());
        }
        r++;

        r = writeSectionHeader(sheet, styles, r, "3. Glossary");
        r = writeHeaderRow(sheet, styles, r, "Term", "Definition");
        for (BudgetingFormulaCatalog.GlossaryRow row : BudgetingFormulaCatalog.GLOSSARY) {
            r = writeBodyRow(sheet, styles, r, row.term(), row.definition());
        }
        r++;

        r = writeSectionHeader(sheet, styles, r, "4. Period");
        Row periodRow = sheet.createRow(r++);
        Cell periodCell = periodRow.createCell(0);
        periodCell.setCellValue(periodDescription + ". Actuals available for "
                + monthsWithActuals + " of " + totalMonthsInScope + " months in scope.");
        periodCell.setCellStyle(styles.body());
        r++;

        r = writeSectionHeader(sheet, styles, r, "5. Color Guide");
        r = writeBodyRow(sheet, styles, r, "Green",
                "The actual figure is better than planned for that metric type");
        r = writeBodyRow(sheet, styles, r, "Red",
                "The actual figure is worse than planned for that metric type");
        r = writeBodyRow(sheet, styles, r, "Cost metrics (COGS, OpEx, Overhead)",
                "Spending LESS than planned = green (under-budget is favourable)");
        r = writeBodyRow(sheet, styles, r, "Revenue / profit metrics",
                "Earning MORE than planned = green");
        r = writeBodyRow(sheet, styles, r, "COGS note",
                "COGS reduces Gross Profit (Revenue − COGS). A smaller COGS number means lower costs which is good — "
                        + "negative COGS variance (Actual < Plan) is favourable.");
        r++;

        r = writeSectionHeader(sheet, styles, r, "6. Metric Definitions");
        r = writeHeaderRow(sheet, styles, r, "Metric Name", "What It Measures", "Formula", "Better When");
        r = writeBodyRow(sheet, styles, r, "COGS", "Direct cost of delivering services",
                "Billable Payroll + Bench Payroll + Delivery Overheads", "Lower");
        r = writeBodyRow(sheet, styles, r, "Gross Profit", "Revenue remaining after direct costs",
                "Revenue − COGS", "Higher");
        r = writeBodyRow(sheet, styles, r, "EBITDA", "Operating profitability",
                "Gross Profit − OpEx", "Higher");
        r = writeBodyRow(sheet, styles, r, "Total Payroll Cost", "True cost of employees",
                "Gross Pay + EPF + EPS + EDLI + EPF Admin + VPF + NPS + Gratuity", "Lower");
        r = writeBodyRow(sheet, styles, r, "OpEx", "Operating expenses outside direct delivery",
                "Support/Leadership/Management Payroll + Non-Delivery Overheads + Variable Pay", "Lower");
        r = writeBodyRow(sheet, styles, r, "Billable Ratio %", "Share of workforce that is billable",
                "Billable HC ÷ Total HC × 100", "Higher");

        sheet.setColumnWidth(0, 28 * 256);
        sheet.setColumnWidth(1, 60 * 256);
        sheet.setColumnWidth(2, 70 * 256);
        sheet.setColumnWidth(3, 55 * 256);
    }

    static void addComment(Sheet sheet, Cell cell, String text) {
        if (cell == null || text == null || text.isBlank()) {
            return;
        }
        CreationHelper factory = sheet.getWorkbook().getCreationHelper();
        Drawing<?> drawing = sheet.createDrawingPatriarch();
        ClientAnchor anchor = factory.createClientAnchor();
        anchor.setCol1(cell.getColumnIndex());
        anchor.setCol2(cell.getColumnIndex() + 3);
        anchor.setRow1(cell.getRowIndex());
        anchor.setRow2(cell.getRowIndex() + 4);
        Comment comment = drawing.createCellComment(anchor);
        comment.setString(new XSSFRichTextString(text));
        comment.setAuthor("Cognologix FP&A");
        cell.setCellComment(comment);
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

    private static int writeSectionHeader(Sheet sheet, Styles styles, int r, String title) {
        Row row = sheet.createRow(r);
        Cell cell = row.createCell(0);
        cell.setCellValue(title);
        cell.setCellStyle(styles.section());
        return r + 1;
    }

    private static int writeHeaderRow(Sheet sheet, Styles styles, int r, String... headers) {
        Row row = sheet.createRow(r);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(styles.header());
        }
        return r + 1;
    }

    private static int writeBodyRow(Sheet sheet, Styles styles, int r, String... values) {
        Row row = sheet.createRow(r);
        for (int i = 0; i < values.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(values[i] != null ? values[i] : "");
            cell.setCellStyle(styles.body());
        }
        return r + 1;
    }
}
