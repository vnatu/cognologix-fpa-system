package com.cognologix.fpa.people;

import com.cognologix.fpa.people.domain.ClassificationConfig;
import com.cognologix.fpa.people.domain.ImportColumnMapping;
import com.cognologix.fpa.people.domain.ImportColumnMappingLine;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import com.cognologix.fpa.general.ExcelParserUtils;

/**
 * Excel export/import for People &amp; Payroll classification config and column mapping templates (ADR-044).
 */
@Component
public class PeopleExcelIO {

    static final String COL_CONFIG_TYPE = "Config Type";
    static final String COL_VALUE = "Value";
    static final String COL_IMPORT_TYPE = "Import Type";
    static final String COL_TEMPLATE_NAME = "Template Name";
    static final String COL_EXCEL_COLUMN_NAME = "Excel Column Name";
    static final String COL_SYSTEM_ATTRIBUTE = "System Attribute";

    private static final List<String> CLASSIFICATION_REQUIRED_HEADERS =
            List.of(COL_CONFIG_TYPE, COL_VALUE);

    private static final List<String> MAPPING_REQUIRED_HEADERS = List.of(
            COL_IMPORT_TYPE, COL_TEMPLATE_NAME, COL_EXCEL_COLUMN_NAME, COL_SYSTEM_ATTRIBUTE);

    public byte[] exportClassificationConfig(List<ClassificationConfig> configs) {
        List<ClassificationConfig> sorted = configs.stream()
                .sorted(Comparator
                        .comparing((ClassificationConfig c) -> c.getConfigType().name())
                        .thenComparing(ClassificationConfig::getValue, String.CASE_INSENSITIVE_ORDER))
                .toList();

        try (Workbook workbook = WorkbookFactory.create(true);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Classification Config");
            writeHeaderRow(sheet, CLASSIFICATION_REQUIRED_HEADERS);

            int rowIdx = 1;
            for (ClassificationConfig config : sorted) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(config.getConfigType().name());
                row.createCell(1).setCellValue(config.getValue());
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new BadRequestException("Failed to generate classification export: " + e.getMessage());
        }
    }

    public byte[] buildClassificationImportSample() {
        return buildHeadersOnlyWorkbook("Classification Config", CLASSIFICATION_REQUIRED_HEADERS);
    }

    public List<ParsedClassificationImportRow> parseClassificationImport(MultipartFile file) {
        validateFileExtension(file);
        try (InputStream in = file.getInputStream(); Workbook workbook = WorkbookFactory.create(in)) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null || sheet.getPhysicalNumberOfRows() == 0) {
                throw new BadRequestException("Excel file has no rows");
            }

            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null) {
                throw new BadRequestException("Excel file has no header row");
            }

            Map<String, Integer> columnIndex = mapHeaders(headerRow);
            validateRequiredHeaders(columnIndex, CLASSIFICATION_REQUIRED_HEADERS);

            List<ParsedClassificationImportRow> rows = new ArrayList<>();
            int firstDataRow = sheet.getFirstRowNum() + 1;
            for (int r = firstDataRow; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null || isBlankRow(row)) {
                    continue;
                }
                int rowNumber = r + 1;
                rows.add(new ParsedClassificationImportRow(
                        rowNumber,
                        cellValue(row, columnIndex.get(ExcelParserUtils.normalizeHeader(COL_CONFIG_TYPE))),
                        cellValue(row, columnIndex.get(ExcelParserUtils.normalizeHeader(COL_VALUE)))));
            }
            return rows;
        } catch (BadRequestException e) {
            throw e;
        } catch (IOException e) {
            throw new BadRequestException("Failed to parse Excel file: " + e.getMessage());
        }
    }

    public byte[] exportMappingTemplates(List<ImportColumnMapping> mappings) {
        List<ImportColumnMapping> sorted = mappings.stream()
                .sorted(Comparator
                        .comparing((ImportColumnMapping m) -> m.getImportType().name())
                        .thenComparing(ImportColumnMapping::getTemplateName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        try (Workbook workbook = WorkbookFactory.create(true);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Mapping Templates");
            writeHeaderRow(sheet, MAPPING_REQUIRED_HEADERS);

            int rowIdx = 1;
            for (ImportColumnMapping mapping : sorted) {
                List<ImportColumnMappingLine> lines = mapping.getLines().stream()
                        .sorted(Comparator.comparing(
                                ImportColumnMappingLine::getExcelColumnName, String.CASE_INSENSITIVE_ORDER))
                        .toList();
                for (ImportColumnMappingLine line : lines) {
                    Row row = sheet.createRow(rowIdx++);
                    row.createCell(0).setCellValue(mapping.getImportType().name());
                    row.createCell(1).setCellValue(mapping.getTemplateName());
                    row.createCell(2).setCellValue(line.getExcelColumnName());
                    row.createCell(3).setCellValue(line.getSystemAttribute());
                }
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new BadRequestException("Failed to generate mapping template export: " + e.getMessage());
        }
    }

    public byte[] buildMappingImportSample() {
        return buildHeadersOnlyWorkbook("Mapping Templates", MAPPING_REQUIRED_HEADERS);
    }

    public List<ParsedMappingImportRow> parseMappingImport(MultipartFile file) {
        validateFileExtension(file);
        try (InputStream in = file.getInputStream(); Workbook workbook = WorkbookFactory.create(in)) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null || sheet.getPhysicalNumberOfRows() == 0) {
                throw new BadRequestException("Excel file has no rows");
            }

            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null) {
                throw new BadRequestException("Excel file has no header row");
            }

            Map<String, Integer> columnIndex = mapHeaders(headerRow);
            validateRequiredHeaders(columnIndex, MAPPING_REQUIRED_HEADERS);

            List<ParsedMappingImportRow> rows = new ArrayList<>();
            int firstDataRow = sheet.getFirstRowNum() + 1;
            for (int r = firstDataRow; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null || isBlankRow(row)) {
                    continue;
                }
                int rowNumber = r + 1;
                rows.add(new ParsedMappingImportRow(
                        rowNumber,
                        cellValue(row, columnIndex.get(ExcelParserUtils.normalizeHeader(COL_IMPORT_TYPE))),
                        cellValue(row, columnIndex.get(ExcelParserUtils.normalizeHeader(COL_TEMPLATE_NAME))),
                        cellValue(row, columnIndex.get(ExcelParserUtils.normalizeHeader(COL_EXCEL_COLUMN_NAME))),
                        cellValue(row, columnIndex.get(ExcelParserUtils.normalizeHeader(COL_SYSTEM_ATTRIBUTE)))));
            }
            return rows;
        } catch (BadRequestException e) {
            throw e;
        } catch (IOException e) {
            throw new BadRequestException("Failed to parse Excel file: " + e.getMessage());
        }
    }

    private static byte[] buildHeadersOnlyWorkbook(String sheetName, List<String> headers) {
        try (Workbook workbook = WorkbookFactory.create(true);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(sheetName);
            writeHeaderRow(sheet, headers);
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new BadRequestException("Failed to generate sample file: " + e.getMessage());
        }
    }

    private static void validateFileExtension(MultipartFile file) {
        String name = file.getOriginalFilename();
        if (name == null) {
            throw new BadRequestException("File name is required");
        }
        String lower = name.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".xlsx") && !lower.endsWith(".xls")) {
            throw new BadRequestException("Only .xlsx and .xls files are supported");
        }
    }

    private static Map<String, Integer> mapHeaders(Row headerRow) {
        Map<String, Integer> columnIndex = new LinkedHashMap<>();
        for (Cell cell : headerRow) {
            String header = cellValueAsString(cell);
            if (header == null || header.isBlank()) {
                continue;
            }
            columnIndex.put(ExcelParserUtils.normalizeHeader(header), cell.getColumnIndex());
        }
        return columnIndex;
    }

    private static void validateRequiredHeaders(Map<String, Integer> columnIndex, List<String> requiredHeaders) {
        List<String> missing = new ArrayList<>();
        for (String header : requiredHeaders) {
            if (!columnIndex.containsKey(ExcelParserUtils.normalizeHeader(header))) {
                missing.add(header);
            }
        }
        if (!missing.isEmpty()) {
            throw new BadRequestException("Missing required columns: " + String.join(", ", missing));
        }
    }


    private static void writeHeaderRow(Sheet sheet, List<String> headers) {
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.size(); i++) {
            headerRow.createCell(i).setCellValue(headers.get(i));
        }
    }

    private static String cellValue(Row row, Integer columnIndex) {
        if (columnIndex == null) {
            return null;
        }
        return cellValueAsString(row.getCell(columnIndex));
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

    public record ParsedClassificationImportRow(int rowNumber, String configTypeRaw, String valueRaw) {}

    public record ParsedMappingImportRow(
            int rowNumber,
            String importTypeRaw,
            String templateNameRaw,
            String excelColumnNameRaw,
            String systemAttributeRaw
    ) {}
}
