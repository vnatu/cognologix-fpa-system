package com.cognologix.fpa.bankrecon;

import com.cognologix.fpa.bankrecon.domain.TallyLedger;
import com.cognologix.fpa.bankrecon.domain.TallyLedgerHint;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.DefaultIndexedColorMap;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ledger Master workbook. Header colours match {@code ReportExcelSupport} (orange
 * {@code #F68C45}, white bold 11). Bank reconciliation cannot call that class: it is
 * package-private, and this module may depend only on general and people.
 */
final class LedgerMasterExcel {

    static final String SHEET = "Ledger Master";
    private static final String[] HEADERS = {
            "Ledger Name",
            "Group",
            "Accounting Nature",
            "Bank Account",
            "Active",
            "Ledger Code",
            "In Tally XML",
            "Purpose",
            "Keywords",
            "Typical Amount",
            "Disambiguation Note"
    };
    private static final int[] WIDTHS = {40, 28, 20, 16, 12, 14, 14, 40, 36, 22, 40};

    private LedgerMasterExcel() {}

    static byte[] write(List<TallyLedger> ledgers, Map<UUID, TallyLedgerHint> hints) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XSSFSheet sheet = wb.createSheet(SHEET);
            CellStyle header = headerStyle(wb);
            CellStyle text = textStyle(wb);
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(HEADERS[i]);
                cell.setCellStyle(header);
                sheet.setColumnWidth(i, WIDTHS[i] * 256);
            }
            int rowIndex = 1;
            for (TallyLedger ledger : ledgers) {
                TallyLedgerHint hint = hints.get(ledger.getId());
                Row row = sheet.createRow(rowIndex++);
                plainText(row, 0, ledger.getLedgerName(), text);
                plainText(row, 1, ledger.getGroupName(), text);
                plainText(row, 2, ledger.getAccountingNature(), text);
                plainText(row, 3, ledger.isBankAccount() ? "Yes" : "No", text);
                plainText(row, 4, ledger.isActive() ? "Yes" : "No", text);
                plainText(row, 5, ledger.getLedgerCode(), text);
                plainText(row, 6, ledger.isAddedInApp() ? "No" : "Yes", text);
                plainText(row, 7, hint == null ? "" : hint.getPurpose(), text);
                plainText(row, 8, hint == null ? "" : hint.getKeywords(), text);
                plainText(row, 9, hint == null ? "" : hint.getTypicalAmount(), text);
                plainText(row, 10, hint == null ? "" : hint.getDisambiguationNote(), text);
            }
            int lastRow = Math.max(rowIndex - 1, 0);
            sheet.setAutoFilter(new CellRangeAddress(0, lastRow, 0, HEADERS.length - 1));
            sheet.createFreezePane(0, 1);
            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to write ledger master workbook", e);
        }
    }

    /** Shared-string cell with an Excel text format, so a leading = + - or @ stays text. */
    private static void plainText(Row row, int column, String value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellStyle(style);
        cell.setCellValue(value == null ? "" : value);
    }

    private static CellStyle headerStyle(XSSFWorkbook wb) {
        XSSFColor orange = new XSSFColor(new byte[]{(byte) 0xF6, (byte) 0x8C, (byte) 0x45}, new DefaultIndexedColorMap());
        XSSFColor white = new XSSFColor(new byte[]{(byte) 0xFF, (byte) 0xFF, (byte) 0xFF}, new DefaultIndexedColorMap());
        Font font = wb.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 11);
        ((org.apache.poi.xssf.usermodel.XSSFFont) font).setColor(white);
        XSSFCellStyle style = wb.createCellStyle();
        style.setFont(font);
        style.setFillForegroundColor(orange);
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        border(style);
        return style;
    }

    private static CellStyle textStyle(XSSFWorkbook wb) {
        XSSFColor white = new XSSFColor(new byte[]{(byte) 0xFF, (byte) 0xFF, (byte) 0xFF}, new DefaultIndexedColorMap());
        Font font = wb.createFont();
        font.setFontHeightInPoints((short) 10);
        XSSFCellStyle style = wb.createCellStyle();
        style.setFont(font);
        style.setFillForegroundColor(white);
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setDataFormat(wb.createDataFormat().getFormat("@"));
        border(style);
        return style;
    }

    private static void border(CellStyle style) {
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
    }
}
