package com.cognologix.fpa.general;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/** Writes {@link BackupSheet} grids to Excel bytes for backup orchestration (ADR-044 Tier 2). */
public final class ExcelGrid {

    private ExcelGrid() {}

    public static byte[] toWorkbookBytes(BackupSheet sheet) {
        return toWorkbookBytes(List.of(sheet));
    }

    public static byte[] toWorkbookBytes(List<BackupSheet> sheets) {
        try (Workbook workbook = WorkbookFactory.create(true);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (BackupSheet sheet : sheets) {
                String sheetName = sheet.fileName();
                if (sheetName.endsWith(".xlsx")) {
                    sheetName = sheetName.substring(0, sheetName.length() - 5);
                }
                Sheet ws = workbook.createSheet(sheetName);
                writeSheet(ws, sheet.headers(), sheet.rows());
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new GeneralBadRequestException("Failed to write Excel grid: " + e.getMessage());
        }
    }

    private static void writeSheet(Sheet sheet, String[] headers, List<String[]> rows) {
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            headerRow.createCell(i).setCellValue(headers[i]);
        }
        int rowIdx = 1;
        for (String[] data : rows) {
            Row row = sheet.createRow(rowIdx++);
            for (int col = 0; col < headers.length; col++) {
                String value = col < data.length ? data[col] : "";
                row.createCell(col).setCellValue(value != null ? value : "");
            }
        }
    }
}
