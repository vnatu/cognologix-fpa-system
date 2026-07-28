package com.cognologix.fpa.system;

import com.cognologix.fpa.general.BackupSheet;
import com.cognologix.fpa.general.ExcelGrid;
import com.cognologix.fpa.general.GeneralBadRequestException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** ZIP + per-file Excel helpers for full system backup (ADR-044 Tier 2). */
public final class BackupZipIO {

    private static final DataFormatter FORMATTER = new DataFormatter();

    private BackupZipIO() {}

    public static byte[] buildZip(List<BackupSheet> sheetsInOrder) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(bos)) {
            for (BackupSheet sheet : sheetsInOrder) {
                zos.putNextEntry(new ZipEntry(sheet.fileName()));
                zos.write(ExcelGrid.toWorkbookBytes(sheet));
                zos.closeEntry();
            }
            zos.finish();
            return bos.toByteArray();
        } catch (IOException e) {
            throw new GeneralBadRequestException("Failed to build backup ZIP: " + e.getMessage());
        }
    }

    /**
     * @return map of filename → data rows (header row stripped)
     */
    public static Map<String, List<String[]>> readZip(byte[] zipBytes) {
        Map<String, List<String[]>> result = new LinkedHashMap<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName();
                if (name.contains("/")) {
                    name = name.substring(name.lastIndexOf('/') + 1);
                }
                if (!name.endsWith(".xlsx") && !name.endsWith(".xls")) {
                    continue;
                }
                byte[] content = zis.readAllBytes();
                result.put(name, readExcelDataRows(content));
            }
        } catch (IOException e) {
            throw new GeneralBadRequestException("Failed to read backup ZIP: " + e.getMessage());
        }
        return result;
    }

    public static List<String[]> readExcelDataRows(byte[] excelBytes) {
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(excelBytes))) {
            Sheet sheet = workbook.getNumberOfSheets() > 0 ? workbook.getSheetAt(0) : null;
            if (sheet == null) {
                return List.of();
            }
            int headerCols = 0;
            Row header = sheet.getRow(0);
            if (header != null) {
                headerCols = header.getLastCellNum();
            }
            List<String[]> rows = new ArrayList<>();
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null || isBlankRow(row, headerCols)) {
                    continue;
                }
                int cols = Math.max(headerCols, row.getLastCellNum());
                String[] values = new String[cols];
                for (int c = 0; c < cols; c++) {
                    values[c] = cellString(row.getCell(c));
                }
                rows.add(values);
            }
            return rows;
        } catch (IOException e) {
            throw new GeneralBadRequestException("Failed to parse Excel in backup: " + e.getMessage());
        }
    }

    private static boolean isBlankRow(Row row, int cols) {
        int limit = cols > 0 ? cols : row.getLastCellNum();
        for (int c = 0; c < limit; c++) {
            String v = cellString(row.getCell(c));
            if (v != null && !v.isBlank()) {
                return false;
            }
        }
        return true;
    }

    private static String cellString(Cell cell) {
        if (cell == null) {
            return null;
        }
        String formatted = FORMATTER.formatCellValue(cell);
        return formatted == null || formatted.isBlank() ? null : formatted.trim();
    }
}
