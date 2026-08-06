package com.cognologix.fpa.expenses;

import com.cognologix.fpa.expenses.dto.ExpenseDtos.ExpenseImportRowError;
import com.cognologix.fpa.general.ExcelNumberParser;
import com.cognologix.fpa.general.ExcelParserUtils;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.*;

/**
 * Excel export/import for monthly expenses — amounts in Rs Lakhs (system units).
 */
@Component
public class ExpenseExcelIO {

    static final String COL_CATEGORY_CODE = "Category Code";
    static final String COL_CATEGORY_NAME = "Category Name";
    static final String COL_AMOUNT = "Amount";
    static final String COL_NOTES = "Notes";

    private static final String[] HEADERS = {
            COL_CATEGORY_CODE, COL_CATEGORY_NAME, COL_AMOUNT, COL_NOTES
    };

    public byte[] exportMonth(List<ExportRow> rows) {
        try (Workbook workbook = WorkbookFactory.create(true);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Expenses");
            writeHeaderRow(sheet, HEADERS);

            int rowIdx = 1;
            for (ExportRow exportRow : rows) {
                Row row = sheet.createRow(rowIdx++);
                int col = 0;
                row.createCell(col++).setCellValue(exportRow.lineCode());
                row.createCell(col++).setCellValue(exportRow.displayName());
                Cell amountCell = row.createCell(col++);
                amountCell.setCellValue(exportRow.amount() != null
                        ? exportRow.amount().doubleValue() : 0);
                row.createCell(col).setCellValue(exportRow.notes() != null ? exportRow.notes() : "");
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to generate expense export: " + e.getMessage());
        }
    }

    public byte[] buildImportTemplate() {
        return exportMonth(List.of());
    }

    public ParseResult parse(MultipartFile file) {
        validateFileExtension(file);
        try (InputStream in = file.getInputStream(); Workbook workbook = WorkbookFactory.create(in)) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null || sheet.getPhysicalNumberOfRows() == 0) {
                throw new IllegalArgumentException("Excel file has no rows");
            }

            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null) {
                throw new IllegalArgumentException("Excel file has no header row");
            }

            Map<String, Integer> columnIndex = mapHeaders(headerRow);
            requireHeader(columnIndex, COL_CATEGORY_CODE);
            requireHeader(columnIndex, COL_AMOUNT);

            List<ParsedRow> rows = new ArrayList<>();
            List<ExpenseImportRowError> errors = new ArrayList<>();
            int firstDataRow = sheet.getFirstRowNum() + 1;
            int totalRows = 0;

            for (int r = firstDataRow; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null || isBlankRow(row)) {
                    continue;
                }
                totalRows++;
                int rowNumber = r + 1;
                String lineCode = cellValue(row, columnIndex.get(ExcelParserUtils.normalizeHeader(COL_CATEGORY_CODE)));
                String amountRaw = cellValue(row, columnIndex.get(ExcelParserUtils.normalizeHeader(COL_AMOUNT)));
                String notes = optionalCell(row, columnIndex, COL_NOTES);

                if (lineCode == null || lineCode.isBlank()) {
                    errors.add(new ExpenseImportRowError(rowNumber, "Category Code is required"));
                    continue;
                }

                // Finance enters Rs Lakhs directly (same as Budgeting imports) — do NOT call toRsLakhs().
                BigDecimal amount;
                try {
                    amount = ExcelNumberParser.parseAmount(amountRaw);
                    if (amount == null) {
                        amount = BigDecimal.ZERO;
                    }
                } catch (IllegalArgumentException e) {
                    errors.add(new ExpenseImportRowError(rowNumber, "Invalid Amount: " + amountRaw));
                    continue;
                }

                if (amount.compareTo(BigDecimal.ZERO) < 0) {
                    errors.add(new ExpenseImportRowError(rowNumber, "Amount must be >= 0"));
                    continue;
                }

                if (notes != null && notes.length() > 500) {
                    errors.add(new ExpenseImportRowError(rowNumber, "Notes must be at most 500 characters"));
                    continue;
                }

                rows.add(new ParsedRow(rowNumber, lineCode.trim(), amount, notes));
            }

            return new ParseResult(totalRows, rows, errors);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to parse Excel file: " + e.getMessage());
        }
    }

    private void writeHeaderRow(Sheet sheet, String[] headers) {
        Row header = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            header.createCell(i).setCellValue(headers[i]);
        }
    }

    private void validateFileExtension(MultipartFile file) {
        String name = file.getOriginalFilename();
        if (name == null || !(name.toLowerCase(Locale.ROOT).endsWith(".xlsx")
                || name.toLowerCase(Locale.ROOT).endsWith(".xls"))) {
            throw new IllegalArgumentException("File must be .xlsx or .xls");
        }
    }

    private Map<String, Integer> mapHeaders(Row headerRow) {
        Map<String, Integer> map = new HashMap<>();
        for (Cell cell : headerRow) {
            String raw = cellValue(headerRow, cell.getColumnIndex());
            if (raw != null && !raw.isBlank()) {
                map.put(ExcelParserUtils.normalizeHeader(raw), cell.getColumnIndex());
            }
        }
        return map;
    }

    private void requireHeader(Map<String, Integer> columnIndex, String header) {
        if (!columnIndex.containsKey(ExcelParserUtils.normalizeHeader(header))) {
            throw new IllegalArgumentException("Missing required column: " + header);
        }
    }

    private String optionalCell(Row row, Map<String, Integer> columnIndex, String header) {
        Integer idx = columnIndex.get(ExcelParserUtils.normalizeHeader(header));
        if (idx == null) {
            return null;
        }
        String value = cellValue(row, idx);
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String cellValue(Row row, Integer colIndex) {
        if (colIndex == null) {
            return null;
        }
        Cell cell = row.getCell(colIndex);
        if (cell == null) {
            return null;
        }
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getLocalDateTimeCellValue().toLocalDate().toString();
                }
                double n = cell.getNumericCellValue();
                if (n == Math.rint(n) && Math.abs(n) < 1e15) {
                    yield BigDecimal.valueOf((long) n).toPlainString();
                }
                yield BigDecimal.valueOf(n).stripTrailingZeros().toPlainString();
            }
            case BOOLEAN -> Boolean.toString(cell.getBooleanCellValue());
            case FORMULA -> {
                try {
                    yield BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
                } catch (Exception e) {
                    yield cell.getStringCellValue();
                }
            }
            case BLANK -> null;
            default -> null;
        };
    }

    private boolean isBlankRow(Row row) {
        for (Cell cell : row) {
            String v = cellValue(row, cell.getColumnIndex());
            if (v != null && !v.isBlank()) {
                return false;
            }
        }
        return true;
    }

    public record ExportRow(String lineCode, String displayName, BigDecimal amount, String notes) {}

    public record ParsedRow(int rowNumber, String lineCode, BigDecimal amount, String notes) {}

    public record ParseResult(int totalRows, List<ParsedRow> rows, List<ExpenseImportRowError> errors) {}
}
