package com.cognologix.fpa.customer;

import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * Parses Project Code Excel imports — same column layout as export (ADR-027 extension).
 */
@Component
public class ProjectCodeImportParser {

    static final String COL_CUSTOMER_CODE = "Customer Code";
    static final String COL_PROJECT_CODE = "Project Code";
    static final String COL_DESCRIPTION = "Description";

    private static final List<String> REQUIRED_HEADERS = List.of(COL_CUSTOMER_CODE, COL_PROJECT_CODE);

    public List<ParsedProjectCodeImportRow> parse(MultipartFile file) {
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

            List<ParsedProjectCodeImportRow> rows = new ArrayList<>();
            int firstDataRow = sheet.getFirstRowNum() + 1;
            for (int r = firstDataRow; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null || isBlankRow(row)) {
                    continue;
                }
                rows.add(new ParsedProjectCodeImportRow(
                        r + 1,
                        cellValue(row, columnIndex.get(normalizeHeader(COL_CUSTOMER_CODE))),
                        cellValue(row, columnIndex.get(normalizeHeader(COL_PROJECT_CODE))),
                        optionalCell(row, columnIndex, COL_DESCRIPTION)));
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
            Sheet sheet = workbook.createSheet("Project Codes");
            Row headerRow = sheet.createRow(0);
            String[] headers = {COL_CUSTOMER_CODE, COL_PROJECT_CODE, COL_DESCRIPTION};
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
            String header = CustomerImportParser.cellValueAsString(cell);
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
        return CustomerImportParser.cellValueAsString(row.getCell(columnIndex));
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
            String value = CustomerImportParser.cellValueAsString(cell);
            if (value != null && !value.isBlank()) {
                return false;
            }
        }
        return true;
    }

    public record ParsedProjectCodeImportRow(
            int rowNumber,
            String customerCode,
            String projectCode,
            String description
    ) {}
}
