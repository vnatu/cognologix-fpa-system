package com.cognologix.fpa.customer;

import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * Parses rate card Excel imports (spec §6, ADR-015).
 * One row per rate card line; headers grouped by Customer Code + Rate Card Name + Effective From.
 */
@Component
public class RateCardImportParser {

    static final String COL_CUSTOMER_CODE = "Customer Code";
    static final String COL_RATE_CARD_NAME = "Rate Card Name";
    static final String COL_RATE_CARD_TYPE = "Rate Card Type";
    static final String COL_CURRENCY = "Currency";
    static final String COL_EFFECTIVE_FROM = "Effective From";
    static final String COL_JOB_LEVEL = "Job Level";
    static final String COL_RATE_AMOUNT = "Rate Amount";

    private static final List<String> REQUIRED_HEADERS = List.of(
            COL_CUSTOMER_CODE,
            COL_RATE_CARD_NAME,
            COL_RATE_CARD_TYPE,
            COL_CURRENCY,
            COL_EFFECTIVE_FROM,
            COL_RATE_AMOUNT);

    public List<ParsedRateCardImportRow> parse(MultipartFile file) {
        validateFileExtension(file);
        try (InputStream in = file.getInputStream(); Workbook workbook = WorkbookFactory.create(in)) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null || sheet.getPhysicalNumberOfRows() == 0) {
                throw new CustomerBadRequestException("Excel file has no rows");
            }

            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null) {
                throw new CustomerBadRequestException("Excel file has no header row");
            }

            Map<String, Integer> columnIndex = mapHeaders(headerRow);
            validateRequiredHeaders(columnIndex);

            List<ParsedRateCardImportRow> rows = new ArrayList<>();
            int firstDataRow = sheet.getFirstRowNum() + 1;
            for (int r = firstDataRow; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null || isBlankRow(row)) {
                    continue;
                }
                rows.add(new ParsedRateCardImportRow(
                        r + 1,
                        cellValue(row, columnIndex.get(normalizeHeader(COL_CUSTOMER_CODE))),
                        cellValue(row, columnIndex.get(normalizeHeader(COL_RATE_CARD_NAME))),
                        cellValue(row, columnIndex.get(normalizeHeader(COL_RATE_CARD_TYPE))),
                        cellValue(row, columnIndex.get(normalizeHeader(COL_CURRENCY))),
                        cellValue(row, columnIndex.get(normalizeHeader(COL_EFFECTIVE_FROM))),
                        optionalCell(row, columnIndex, COL_JOB_LEVEL),
                        cellValue(row, columnIndex.get(normalizeHeader(COL_RATE_AMOUNT)))));
            }
            return rows;
        } catch (CustomerBadRequestException e) {
            throw e;
        } catch (IOException e) {
            throw new CustomerBadRequestException("Failed to parse Excel file: " + e.getMessage());
        }
    }

    public byte[] buildSampleWorkbook() {
        try (Workbook workbook = WorkbookFactory.create(true);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Rate Cards");
            Row headerRow = sheet.createRow(0);
            String[] headers = {
                    COL_CUSTOMER_CODE,
                    COL_RATE_CARD_NAME,
                    COL_RATE_CARD_TYPE,
                    COL_CURRENCY,
                    COL_EFFECTIVE_FROM,
                    COL_JOB_LEVEL,
                    COL_RATE_AMOUNT
            };
            for (int i = 0; i < headers.length; i++) {
                headerRow.createCell(i).setCellValue(headers[i]);
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new CustomerBadRequestException("Failed to generate sample file: " + e.getMessage());
        }
    }

    private static void validateFileExtension(MultipartFile file) {
        String name = file.getOriginalFilename();
        if (name == null) {
            throw new CustomerBadRequestException("File name is required");
        }
        String lower = name.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".xlsx") && !lower.endsWith(".xls")) {
            throw new CustomerBadRequestException("Only .xlsx and .xls files are supported");
        }
    }

    private static Map<String, Integer> mapHeaders(Row headerRow) {
        Map<String, Integer> columnIndex = new LinkedHashMap<>();
        for (Cell cell : headerRow) {
            String header = cellValueAsString(cell);
            if (header == null || header.isBlank()) {
                continue;
            }
            columnIndex.put(normalizeHeader(header), cell.getColumnIndex());
        }
        return columnIndex;
    }

    private static void validateRequiredHeaders(Map<String, Integer> columnIndex) {
        List<String> missing = new ArrayList<>();
        for (String header : REQUIRED_HEADERS) {
            if (!columnIndex.containsKey(normalizeHeader(header))) {
                missing.add(header);
            }
        }
        if (!missing.isEmpty()) {
            throw new CustomerBadRequestException("Missing required columns: " + String.join(", ", missing));
        }
    }

    private static String normalizeHeader(String header) {
        return header.trim().toLowerCase(Locale.ROOT);
    }

    private static String cellValue(Row row, Integer columnIndex) {
        if (columnIndex == null) {
            return null;
        }
        return cellValueAsString(row.getCell(columnIndex));
    }

    private static String optionalCell(Row row, Map<String, Integer> columnIndex, String headerName) {
        Integer idx = columnIndex.get(normalizeHeader(headerName));
        if (idx == null) {
            return null;
        }
        return cellValue(row, idx);
    }

    private static boolean isBlankRow(Row row) {
        for (Cell cell : row) {
            String value = cellValueAsString(cell);
            if (value != null && !value.isBlank()) {
                return false;
            }
        }
        return true;
    }

    static String cellValueAsString(Cell cell) {
        if (cell == null) {
            return null;
        }
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try {
                    yield cell.getStringCellValue().trim();
                } catch (IllegalStateException ex) {
                    yield formatNumeric(cell);
                }
            }
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getLocalDateTimeCellValue().toLocalDate().toString();
                }
                yield formatNumeric(cell);
            }
            case BLANK, _NONE -> null;
            default -> null;
        };
    }

    private static String formatNumeric(Cell cell) {
        double n = cell.getNumericCellValue();
        if (n == Math.rint(n) && !Double.isInfinite(n)) {
            return String.valueOf((long) n);
        }
        return String.valueOf(n);
    }

    public record ParsedRateCardImportRow(
            int rowNumber,
            String customerCode,
            String rateCardName,
            String rateCardType,
            String currency,
            String effectiveFrom,
            String jobLevel,
            String rateAmount
    ) {}
}
