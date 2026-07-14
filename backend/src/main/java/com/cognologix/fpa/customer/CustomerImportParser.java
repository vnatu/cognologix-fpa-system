package com.cognologix.fpa.customer;

import com.cognologix.fpa.customer.domain.LifecycleStatus;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * Parses Customer Master Excel imports (ADR-027).
 * Expected headers (case-insensitive): Customer Code, Customer Name,
 * Zoho Books Customer Ref, Lifecycle Status, DSO Days, Relationship Owner Employee ID.
 */
@Component
public class CustomerImportParser {

    static final String COL_CUSTOMER_CODE = "Customer Code";
    static final String COL_CUSTOMER_NAME = "Customer Name";
    static final String COL_ZOHO_BOOKS_REF = "Zoho Books Customer Ref";
    static final String COL_LIFECYCLE_STATUS = "Lifecycle Status";
    static final String COL_DSO_DAYS = "DSO Days";
    static final String COL_RELATIONSHIP_OWNER = "Relationship Owner Employee ID";

    private static final List<String> REQUIRED_HEADERS = List.of(COL_CUSTOMER_CODE, COL_CUSTOMER_NAME);

    public List<ParsedCustomerImportRow> parse(MultipartFile file) {
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

            List<ParsedCustomerImportRow> rows = new ArrayList<>();
            int firstDataRow = sheet.getFirstRowNum() + 1;
            for (int r = firstDataRow; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null || isBlankRow(row)) {
                    continue;
                }
                int rowNumber = r + 1;
                rows.add(new ParsedCustomerImportRow(
                        rowNumber,
                        cellValue(row, columnIndex.get(normalizeHeader(COL_CUSTOMER_CODE))),
                        cellValue(row, columnIndex.get(normalizeHeader(COL_CUSTOMER_NAME))),
                        optionalCell(row, columnIndex, COL_ZOHO_BOOKS_REF),
                        optionalCell(row, columnIndex, COL_LIFECYCLE_STATUS),
                        optionalCell(row, columnIndex, COL_DSO_DAYS),
                        optionalCell(row, columnIndex, COL_RELATIONSHIP_OWNER)));
            }
            return rows;
        } catch (CustomerBadRequestException e) {
            throw e;
        } catch (IOException e) {
            throw new CustomerBadRequestException("Failed to parse Excel file: " + e.getMessage());
        }
    }

    static LifecycleStatus parseLifecycleStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return LifecycleStatus.ACTIVE;
        }
        try {
            return LifecycleStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT).replace(' ', '_'));
        } catch (IllegalArgumentException e) {
            return LifecycleStatus.ACTIVE;
        }
    }

    static int parseDsoDays(String raw) {
        if (raw == null || raw.isBlank()) {
            return 30;
        }
        try {
            String normalized = raw.trim().replace(",", "");
            double numeric = Double.parseDouble(normalized);
            int days = (int) Math.round(numeric);
            if (days <= 0) {
                return 30;
            }
            return days;
        } catch (NumberFormatException e) {
            return 30;
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
                    yield String.valueOf((long) cell.getNumericCellValue());
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

    public record ParsedCustomerImportRow(
            int rowNumber,
            String customerCode,
            String customerName,
            String zohoBooksCustomerRef,
            String lifecycleStatusRaw,
            String dsoDaysRaw,
            String relationshipOwnerEmployeeId
    ) {}
}
