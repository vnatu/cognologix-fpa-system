package com.cognologix.fpa.revenue;


import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.Locale;

/**
 * Parses Zoho Books Excel exports into row maps keyed by system attribute (ADR-019, ADR-039).
 */
@Component
public class RevenueExcelParser {

    /** Reads header row and data row count only — no persistence (ADR-019 upload preview). */
    public ParseHeadersResult parseHeaders(MultipartFile file) {
        try (InputStream in = file.getInputStream(); Workbook workbook = new XSSFWorkbook(in)) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null || sheet.getPhysicalNumberOfRows() == 0) {
                throw new RevenueBadRequestException("Excel file has no rows");
            }
            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null) {
                throw new RevenueBadRequestException("Excel file has no header row");
            }
            List<String> headers = new ArrayList<>();
            for (Cell cell : headerRow) {
                String header = cellValueAsString(cell);
                if (header != null && !header.isBlank()) {
                    headers.add(header.trim());
                }
            }
            int rowCount = 0;
            int firstData = sheet.getFirstRowNum() + 1;
            for (int r = firstData; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row != null && !isBlankDataRow(row)) {
                    rowCount++;
                }
            }
            return new ParseHeadersResult(headers, rowCount);
        } catch (IOException e) {
            throw new RevenueBadRequestException("Failed to parse Excel file: " + e.getMessage());
        }
    }

    private static boolean isBlankDataRow(Row row) {
        for (Cell cell : row) {
            String v = cellValueAsString(cell);
            if (v != null && !v.isBlank()) {
                return false;
            }
        }
        return true;
    }

    public ParsedWorkbook parse(MultipartFile file, Map<String, String> excelColumnToAttribute) {
        try (InputStream in = file.getInputStream(); Workbook workbook = new XSSFWorkbook(in)) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null || sheet.getPhysicalNumberOfRows() == 0) {
                throw new RevenueBadRequestException("Excel file has no rows");
            }

            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null) {
                throw new RevenueBadRequestException("Excel file has no header row");
            }

            Map<Integer, String> indexToHeader = new LinkedHashMap<>();
            Set<String> fileHeaders = new LinkedHashSet<>();
            for (Cell cell : headerRow) {
                String header = cellValueAsString(cell);
                if (header == null || header.isBlank()) {
                    continue;
                }
                indexToHeader.put(cell.getColumnIndex(), header.trim());
                fileHeaders.add(header.trim());
            }

            Set<String> mappedHeaders = new LinkedHashSet<>(excelColumnToAttribute.keySet());
            List<String> unmappedColumns = fileHeaders.stream()
                    .filter(h -> !mappedHeaders.contains(h))
                    .sorted()
                    .toList();
            List<String> missingColumns = mappedHeaders.stream()
                    .filter(h -> !fileHeaders.contains(h))
                    .sorted()
                    .toList();

            // Column index → system attribute (only mapped headers present in the file)
            Map<Integer, String> indexToAttribute = new LinkedHashMap<>();
            for (var entry : indexToHeader.entrySet()) {
                String attr = excelColumnToAttribute.get(entry.getValue());
                if (attr != null) {
                    indexToAttribute.put(entry.getKey(), attr);
                }
            }

            List<Map<String, String>> rows = new ArrayList<>();
            int firstData = sheet.getFirstRowNum() + 1;
            for (int r = firstData; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null || isBlankRow(row, indexToAttribute.keySet())) {
                    continue;
                }
                Map<String, String> values = new LinkedHashMap<>();
                for (var entry : indexToAttribute.entrySet()) {
                    Cell cell = row.getCell(entry.getKey());
                    values.put(entry.getValue(), cellValueAsString(cell));
                }
                rows.add(values);
            }

            return new ParsedWorkbook(rows, unmappedColumns, missingColumns);
        } catch (IOException e) {
            throw new RevenueBadRequestException("Failed to parse Excel file: " + e.getMessage());
        }
    }

    private static boolean isBlankRow(Row row, Set<Integer> columns) {
        for (Integer col : columns) {
            String v = cellValueAsString(row.getCell(col));
            if (v != null && !v.isBlank()) {
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
        return BigDecimal.valueOf(n).stripTrailingZeros().toPlainString();
    }

    public static String required(Map<String, String> row, String attribute) {
        String v = row.get(attribute);
        if (v == null || v.isBlank()) {
            throw new RevenueBadRequestException("Missing required mapped value: " + attribute);
        }
        return v.trim();
    }

    public static String optional(Map<String, String> row, String attribute) {
        String v = row.get(attribute);
        return (v == null || v.isBlank()) ? null : v.trim();
    }

    public static BigDecimal requiredDecimal(Map<String, String> row, String attribute) {
        String v = required(row, attribute);
        try {
            return new BigDecimal(v.replace(",", ""));
        } catch (NumberFormatException e) {
            throw new RevenueBadRequestException("Invalid number for " + attribute + ": " + v);
        }
    }

    public static BigDecimal optionalDecimal(Map<String, String> row, String attribute) {
        String v = optional(row, attribute);
        if (v == null) {
            return null;
        }
        try {
            return new BigDecimal(v.replace(",", ""));
        } catch (NumberFormatException e) {
            throw new RevenueBadRequestException("Invalid number for " + attribute + ": " + v);
        }
    }

    public static LocalDate optionalDate(Map<String, String> row, String attribute) {
        String v = optional(row, attribute);
        if (v == null) {
            return null;
        }
        LocalDate parsed = parseFlexibleDate(v);
        if (parsed == null) {
            throw new RevenueBadRequestException("Invalid date for " + attribute + ": " + v);
        }
        return parsed;
    }

    /**
     * Accepts ISO dates, common locale formats (DD/MM/YYYY, etc.), and Excel serial numbers
     * emitted as plain numerics when a cell is not date-formatted.
     */
    static LocalDate parseFlexibleDate(String raw) {
        String value = raw.trim();
        if (value.isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException ignored) {
            // fall through
        }
        try {
            double serial = Double.parseDouble(value.replace(",", ""));
            if (DateUtil.isValidExcelDate(serial)) {
                return DateUtil.getLocalDateTime(serial, false).toLocalDate();
            }
        } catch (NumberFormatException ignored) {
            // fall through
        }
        List<DateTimeFormatter> formatters = List.of(
                DateTimeFormatter.ofPattern("d/M/uuuu"),
                DateTimeFormatter.ofPattern("dd/M/uuuu"),
                DateTimeFormatter.ofPattern("M/d/uuuu"),
                DateTimeFormatter.ofPattern("dd-MM-uuuu"),
                DateTimeFormatter.ofPattern("d-M-uuuu"),
                DateTimeFormatter.ofPattern("dd/MM/uuuu"),
                DateTimeFormatter.ofPattern("d-MMM-uuuu", Locale.ENGLISH),
                DateTimeFormatter.ofPattern("dd-MMM-uuuu", Locale.ENGLISH),
                DateTimeFormatter.ofPattern("dd MMM uuuu", Locale.ENGLISH));
        for (DateTimeFormatter formatter : formatters) {
            try {
                return LocalDate.parse(value, formatter);
            } catch (DateTimeParseException ignored) {
                // try next
            }
        }
        return null;
    }

    public static LocalDate requiredDate(Map<String, String> row, String attribute) {
        LocalDate d = optionalDate(row, attribute);
        if (d == null) {
            throw new RevenueBadRequestException("Missing required mapped value: " + attribute);
        }
        return d;
    }

    public record ParsedWorkbook(
            List<Map<String, String>> rows,
            List<String> unmappedColumns,
            List<String> missingColumns
    ) {}

    public record ParseHeadersResult(List<String> headers, int rowCount) {}
}
