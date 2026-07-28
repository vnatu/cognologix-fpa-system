package com.cognologix.fpa.general;

import com.cognologix.fpa.general.dto.FxRateImportRowError;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import com.cognologix.fpa.general.ExcelParserUtils;

/**
 * Excel export/import for FX rates — same column layout for both directions.
 */
@Component
public class FxRateExcelIO {

    static final String COL_CURRENCY_PAIR = "Currency Pair";
    static final String COL_RATE = "Rate";
    static final String COL_EFFECTIVE_FROM = "Effective From";
    static final String COL_EFFECTIVE_TO = "Effective To";
    static final String COL_CREATED_BY = "Created By";

    private static final List<String> REQUIRED_HEADERS = List.of(
            COL_CURRENCY_PAIR, COL_RATE, COL_EFFECTIVE_FROM);

    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    public byte[] exportFxRates(List<FxRate> rates) {
        try (Workbook workbook = WorkbookFactory.create(true);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("FX Rates");
            String[] headers = {
                    COL_CURRENCY_PAIR,
                    COL_RATE,
                    COL_EFFECTIVE_FROM,
                    COL_EFFECTIVE_TO,
                    COL_CREATED_BY
            };
            writeHeaderRow(sheet, headers);

            int rowIdx = 1;
            for (FxRate rate : rates) {
                Row row = sheet.createRow(rowIdx++);
                int col = 0;
                row.createCell(col++).setCellValue(rate.getCurrencyPair());
                setNumericCell(row, col++, rate.getRate());
                row.createCell(col++).setCellValue(rate.getEffectiveFrom().toString());
                setDateCell(row, col++, rate.getEffectiveTo());
                setOptionalString(row, col, rate.getCreatedBy());
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new GeneralBadRequestException("Failed to generate FX rate export: " + e.getMessage());
        }
    }

    public byte[] buildImportTemplate() {
        try (Workbook workbook = WorkbookFactory.create(true);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("FX Rates");
            writeHeaderRow(sheet, new String[]{
                    COL_CURRENCY_PAIR,
                    COL_RATE,
                    COL_EFFECTIVE_FROM,
                    COL_EFFECTIVE_TO,
                    COL_CREATED_BY
            });
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new GeneralBadRequestException("Failed to generate import template: " + e.getMessage());
        }
    }

    public List<ParsedFxRateImportRow> parse(MultipartFile file) {
        validateFileExtension(file);
        try (InputStream in = file.getInputStream(); Workbook workbook = WorkbookFactory.create(in)) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null || sheet.getPhysicalNumberOfRows() == 0) {
                throw new GeneralBadRequestException("Excel file has no rows");
            }

            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null) {
                throw new GeneralBadRequestException("Excel file has no header row");
            }

            Map<String, Integer> columnIndex = mapHeaders(headerRow);
            validateRequiredHeaders(columnIndex);

            List<ParsedFxRateImportRow> rows = new ArrayList<>();
            int firstDataRow = sheet.getFirstRowNum() + 1;
            for (int r = firstDataRow; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null || isBlankRow(row)) {
                    continue;
                }
                rows.add(new ParsedFxRateImportRow(
                        r + 1,
                        cellValue(row, columnIndex.get(ExcelParserUtils.normalizeHeader(COL_CURRENCY_PAIR))),
                        cellValue(row, columnIndex.get(ExcelParserUtils.normalizeHeader(COL_RATE))),
                        cellValue(row, columnIndex.get(ExcelParserUtils.normalizeHeader(COL_EFFECTIVE_FROM))),
                        optionalCell(row, columnIndex, COL_EFFECTIVE_TO),
                        optionalCell(row, columnIndex, COL_CREATED_BY)));
            }
            return rows;
        } catch (GeneralBadRequestException e) {
            throw e;
        } catch (IOException e) {
            throw new GeneralBadRequestException("Failed to parse Excel file: " + e.getMessage());
        }
    }

    public RowValidation validateRow(ParsedFxRateImportRow row) {
        if (row.currencyPair() == null || row.currencyPair().isBlank()) {
            return RowValidation.fail(error(row.rowNumber(), "Currency Pair is required"));
        }
        if (row.currencyPair().trim().length() > 10) {
            return RowValidation.fail(error(row.rowNumber(), "Currency Pair must be at most 10 characters"));
        }
        if (row.rateRaw() == null || row.rateRaw().isBlank()) {
            return RowValidation.fail(error(row.rowNumber(), "Rate is required"));
        }
        BigDecimal rate;
        try {
            rate = ExcelNumberParser.parseAmount(row.rateRaw());
            if (rate == null) {
                return RowValidation.fail(error(row.rowNumber(), "Invalid Rate: " + row.rateRaw()));
            }
        } catch (IllegalArgumentException e) {
            return RowValidation.fail(error(row.rowNumber(), "Invalid Rate: " + row.rateRaw()));
        }
        if (rate.compareTo(BigDecimal.ZERO) <= 0) {
            return RowValidation.fail(error(row.rowNumber(), "Rate must be greater than zero"));
        }
        if (row.effectiveFromRaw() == null || row.effectiveFromRaw().isBlank()) {
            return RowValidation.fail(error(row.rowNumber(), "Effective From is required"));
        }
        LocalDate effectiveFrom;
        try {
            effectiveFrom = parseDate(row.effectiveFromRaw());
        } catch (DateTimeParseException e) {
            return RowValidation.fail(error(row.rowNumber(), "Invalid Effective From: " + row.effectiveFromRaw()));
        }
        LocalDate effectiveTo = null;
        if (row.effectiveToRaw() != null && !row.effectiveToRaw().isBlank()) {
            try {
                effectiveTo = parseDate(row.effectiveToRaw());
            } catch (DateTimeParseException e) {
                return RowValidation.fail(error(row.rowNumber(), "Invalid Effective To: " + row.effectiveToRaw()));
            }
            if (!effectiveTo.isAfter(effectiveFrom)) {
                return RowValidation.fail(error(row.rowNumber(),
                        "Effective To must be after Effective From (exclusive end date)"));
            }
        }
        String createdBy = row.createdByRaw() != null && !row.createdByRaw().isBlank()
                ? row.createdByRaw().trim()
                : null;
        return RowValidation.ok(new ValidatedFxRateImportRow(
                row.rowNumber(),
                row.currencyPair().trim(),
                rate,
                effectiveFrom,
                effectiveTo,
                createdBy));
    }

    public boolean isDuplicate(
            String currencyPair,
            BigDecimal rate,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            FxRate existing) {
        return currencyPair.equals(existing.getCurrencyPair())
                && rate.compareTo(existing.getRate()) == 0
                && effectiveFrom.equals(existing.getEffectiveFrom())
                && Objects.equals(effectiveTo, existing.getEffectiveTo());
    }

    /**
     * Mirrors DB constraint {@code no_overlapping_fx_rates}: daterange [from, to) semantics;
     * null {@code effectiveTo} means open-ended.
     */
    public boolean rangesOverlap(
            LocalDate from1, LocalDate to1,
            LocalDate from2, LocalDate to2) {
        LocalDate end1 = to1 != null ? to1 : LocalDate.MAX;
        LocalDate end2 = to2 != null ? to2 : LocalDate.MAX;
        return from1.isBefore(end2) && from2.isBefore(end1);
    }

    private static FxRateImportRowError error(int rowNumber, String reason) {
        return new FxRateImportRowError(rowNumber, reason);
    }

    private static void validateFileExtension(MultipartFile file) {
        String name = file.getOriginalFilename();
        if (name == null) {
            throw new GeneralBadRequestException("File name is required");
        }
        String lower = name.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".xlsx") && !lower.endsWith(".xls")) {
            throw new GeneralBadRequestException("Only .xlsx and .xls files are supported");
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

    private static void validateRequiredHeaders(Map<String, Integer> columnIndex) {
        List<String> missing = new ArrayList<>();
        for (String header : REQUIRED_HEADERS) {
            if (!columnIndex.containsKey(ExcelParserUtils.normalizeHeader(header))) {
                missing.add(header);
            }
        }
        if (!missing.isEmpty()) {
            throw new GeneralBadRequestException("Missing required columns: " + String.join(", ", missing));
        }
    }


    private static String cellValue(Row row, Integer columnIndex) {
        if (columnIndex == null) {
            return null;
        }
        return cellValueAsString(row.getCell(columnIndex));
    }

    private static String optionalCell(Row row, Map<String, Integer> columnIndex, String headerName) {
        Integer idx = columnIndex.get(ExcelParserUtils.normalizeHeader(headerName));
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

    private static LocalDate parseDate(String raw) {
        String trimmed = raw.trim();
        if (trimmed.matches("\\d+(\\.\\d+)?")) {
            double serial = Double.parseDouble(trimmed);
            return DateUtil.getLocalDateTime(serial).toLocalDate();
        }
        return LocalDate.parse(trimmed, ISO_DATE);
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
                    yield cell.getLocalDateTimeCellValue().toLocalDate().format(ISO_DATE);
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

    private static void writeHeaderRow(Sheet sheet, String[] headers) {
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            headerRow.createCell(i).setCellValue(headers[i]);
        }
    }

    private static void setOptionalString(Row row, int col, String value) {
        if (value != null && !value.isBlank()) {
            row.createCell(col).setCellValue(value);
        } else {
            row.createCell(col).setBlank();
        }
    }

    private static void setNumericCell(Row row, int col, BigDecimal value) {
        if (value == null) {
            row.createCell(col).setBlank();
        } else {
            row.createCell(col).setCellValue(value.doubleValue());
        }
    }

    private static void setDateCell(Row row, int col, LocalDate value) {
        if (value == null) {
            row.createCell(col).setBlank();
        } else {
            row.createCell(col).setCellValue(value.toString());
        }
    }

    public record ParsedFxRateImportRow(
            int rowNumber,
            String currencyPair,
            String rateRaw,
            String effectiveFromRaw,
            String effectiveToRaw,
            String createdByRaw
    ) {}

    public record ValidatedFxRateImportRow(
            int rowNumber,
            String currencyPair,
            BigDecimal rate,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            String createdBy
    ) {}

    public record RowValidation(ValidatedFxRateImportRow validated, FxRateImportRowError error) {
        public static RowValidation ok(ValidatedFxRateImportRow validated) {
            return new RowValidation(validated, null);
        }

        public static RowValidation fail(FxRateImportRowError error) {
            return new RowValidation(null, error);
        }

        public boolean isOk() {
            return validated != null;
        }
    }
}
