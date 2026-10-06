package com.cognologix.fpa.bankrecon;

import com.cognologix.fpa.bankrecon.domain.DebitCredit;
import com.cognologix.fpa.general.ExcelParserUtils;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class HdfcStatementParser {

    private static final Logger log = LoggerFactory.getLogger(HdfcStatementParser.class);
    private static final DataFormatter FORMATTER = new DataFormatter();
    private static final List<DateTimeFormatter> DATE_TIMES = List.of(
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm")
    );
    private static final List<DateTimeFormatter> DATES = List.of(
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yy"),
            DateTimeFormatter.ofPattern("dd-MM-yy"),
            DateTimeFormatter.ofPattern("d/M/yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd")
    );

    /**
     * Snapshot of a spreadsheet/CSV cell taken before the POI workbook is closed.
     * Excel date serials are NUMERIC, not STRING — keep type + numeric/date values.
     */
    record GridCell(String display, CellType type, Double numeric, LocalDateTime dateTime) {
        static GridCell blank() {
            return new GridCell("", CellType.BLANK, null, null);
        }

        static GridCell text(String value) {
            String v = value == null ? "" : value;
            return new GridCell(v, CellType.STRING, null, null);
        }

        boolean isBlank() {
            return (display == null || display.isBlank()) && numeric == null && dateTime == null;
        }
    }

    record ParseHeadersResult(
            List<String> headers,
            List<String> headerFields,
            List<String> transactionColumns,
            Map<String, String> headerFieldValues,
            int rowCount
    ) {}

    record ParsedStatement(
            String accountNumber,
            String customerName,
            LocalDate periodStart,
            LocalDate periodEnd,
            BigDecimal openingBalance,
            BigDecimal closingBalance,
            String statementNumber,
            List<ParsedRow> rows
    ) {}

    record ParsedRow(
            Instant transactionDate,
            String description,
            BigDecimal amount,
            DebitCredit debitCredit,
            String referenceNo,
            LocalDate valueDate,
            String branch,
            BigDecimal runningBalance
    ) {}

    ParseHeadersResult parseHeaders(MultipartFile file) {
        List<List<GridCell>> grid = readGrid(file);
        int headerIdx = findWideRow(grid);
        if (headerIdx < 0) {
            throw new IllegalArgumentException("Could not detect a transaction column header row");
        }
        List<String> headerFields = new ArrayList<>();
        Map<String, String> headerFieldValues = new LinkedHashMap<>();
        for (int i = 0; i < headerIdx; i++) {
            String label = headerLabel(grid.get(i));
            if (label != null && !label.isBlank()) {
                headerFields.add(label);
                GridCell value = matchHeaderCell(grid.get(i), ExcelParserUtils.normalizeHeader(label));
                if (value != null && !value.isBlank()) {
                    headerFieldValues.put(label, display(value));
                }
            }
        }
        List<String> transactionColumns = new ArrayList<>();
        for (GridCell cell : grid.get(headerIdx)) {
            if (!cell.isBlank()) {
                transactionColumns.add(display(cell).trim());
            }
        }
        LinkedHashSet<String> flat = new LinkedHashSet<>();
        flat.addAll(headerFields);
        flat.addAll(transactionColumns);
        int rowCount = 0;
        for (int i = headerIdx + 1; i < grid.size(); i++) {
            if (!isBlankRow(grid.get(i))) {
                rowCount++;
            }
        }
        return new ParseHeadersResult(
                new ArrayList<>(flat), headerFields, transactionColumns, headerFieldValues, rowCount);
    }

    ParsedStatement parse(MultipartFile file, Map<String, String> excelColumnToAttribute) {
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "statement";
        String statementNumber = stripExtension(filename);
        List<List<GridCell>> grid = readGrid(file);
        Map<String, String> attrToExcel = invert(excelColumnToAttribute);
        int headerIdx = findMappedHeaderRow(grid, attrToExcel);
        if (headerIdx < 0) {
            throw new IllegalArgumentException(
                    "Could not find a transaction header row matching the column mapping template");
        }
        Map<String, Integer> attrToIndex = indexTransactionColumns(grid.get(headerIdx), excelColumnToAttribute);
        Map<String, GridCell> headerValues = extractHeaderFields(grid.subList(0, headerIdx), attrToExcel);

        List<ParsedRow> rows = new ArrayList<>();
        for (int i = headerIdx + 1; i < grid.size(); i++) {
            List<GridCell> row = grid.get(i);
            if (isBlankRow(row)) {
                continue;
            }
            ParsedRow parsed = parseTransaction(row, attrToIndex);
            if (parsed != null) {
                rows.add(parsed);
            }
        }
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("No transactions found in statement");
        }
        return new ParsedStatement(
                blankToNull(display(headerValues.get(BankStatementSystemAttribute.ACCOUNT_NUMBER))),
                blankToNull(display(headerValues.get(BankStatementSystemAttribute.CUSTOMER_NAME))),
                toLocalDate(parseDateTimeCell(headerValues.get(BankStatementSystemAttribute.FROM_DATE), false)),
                toLocalDate(parseDateTimeCell(headerValues.get(BankStatementSystemAttribute.TO_DATE), false)),
                parseBalance(headerValues.get(BankStatementSystemAttribute.OPENING_BALANCE)),
                parseBalance(headerValues.get(BankStatementSystemAttribute.CLOSING_BALANCE)),
                statementNumber,
                rows);
    }

    byte[] sampleWorkbook(Map<String, String> excelColumnToAttribute) {
        List<String> headerLabels = new ArrayList<>();
        List<String> txnHeaders = new ArrayList<>();
        if (excelColumnToAttribute == null || excelColumnToAttribute.isEmpty()) {
            headerLabels.addAll(List.of(BankStatementSystemAttribute.DEFAULT_HEADER_LABELS));
            txnHeaders.addAll(List.of(BankStatementSystemAttribute.DEFAULT_TRANSACTION_HEADERS));
        } else {
            for (var e : excelColumnToAttribute.entrySet()) {
                if (BankStatementSystemAttribute.HEADER_ATTRIBUTES.contains(e.getValue())) {
                    headerLabels.add(e.getKey());
                } else if (BankStatementSystemAttribute.TRANSACTION_ATTRIBUTES.contains(e.getValue())) {
                    txnHeaders.add(e.getKey());
                }
            }
            if (headerLabels.isEmpty()) {
                headerLabels.addAll(List.of(BankStatementSystemAttribute.DEFAULT_HEADER_LABELS));
            }
            if (txnHeaders.isEmpty()) {
                txnHeaders.addAll(List.of(BankStatementSystemAttribute.DEFAULT_TRANSACTION_HEADERS));
            }
        }
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("HDFC Statement");
            int r = 0;
            for (String label : headerLabels) {
                Row row = sheet.createRow(r++);
                row.createCell(0).setCellValue(label);
            }
            r++;
            Row header = sheet.createRow(r);
            for (int i = 0; i < txnHeaders.size(); i++) {
                header.createCell(i).setCellValue(txnHeaders.get(i));
            }
            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build sample mapping template", e);
        }
    }

    private List<List<GridCell>> readGrid(MultipartFile file) {
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "";
        try {
            if (filename.toLowerCase(Locale.ROOT).endsWith(".xlsx")
                    || filename.toLowerCase(Locale.ROOT).endsWith(".xls")) {
                return parseExcel(file.getBytes());
            }
            return parseCsv(file.getBytes());
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Unable to parse bank statement: " + e.getMessage(), e);
        }
    }

    private List<List<GridCell>> parseCsv(byte[] bytes) {
        List<List<GridCell>> grid = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    grid.add(List.of());
                } else {
                    grid.add(parseCsvLine(line).stream().map(GridCell::text).toList());
                }
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Unable to read CSV statement", e);
        }
        return grid;
    }

    private List<List<GridCell>> parseExcel(byte[] bytes) throws Exception {
        List<List<GridCell>> grid = new ArrayList<>();
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            Sheet sheet = wb.getSheetAt(0);
            int last = sheet.getLastRowNum();
            for (int i = 0; i <= last; i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    grid.add(List.of());
                    continue;
                }
                List<GridCell> cells = new ArrayList<>();
                short lastCell = row.getLastCellNum();
                for (int c = 0; c < lastCell; c++) {
                    cells.add(fromPoi(row.getCell(c)));
                }
                grid.add(cells);
            }
        }
        return grid;
    }

    private static GridCell fromPoi(Cell cell) {
        if (cell == null) {
            return GridCell.blank();
        }
        CellType type = cell.getCellType();
        if (type == CellType.FORMULA) {
            type = cell.getCachedFormulaResultType();
        }
        String display = FORMATTER.formatCellValue(cell).trim();
        return switch (type) {
            case NUMERIC -> {
                double n = cell.getNumericCellValue();
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield new GridCell(display, CellType.NUMERIC, n, cell.getLocalDateTimeCellValue());
                }
                yield new GridCell(display, CellType.NUMERIC, n, null);
            }
            case STRING -> GridCell.text(cell.getStringCellValue() == null ? "" : cell.getStringCellValue().trim());
            case BOOLEAN -> GridCell.text(Boolean.toString(cell.getBooleanCellValue()));
            default -> display.isBlank() ? GridCell.blank() : GridCell.text(display);
        };
    }

    private static int findWideRow(List<List<GridCell>> grid) {
        for (int i = 0; i < grid.size(); i++) {
            long named = grid.get(i).stream().filter(c -> !c.isBlank()).count();
            if (named >= 4) {
                return i;
            }
        }
        return -1;
    }

    private static int findMappedHeaderRow(List<List<GridCell>> grid, Map<String, String> attrToExcel) {
        Set<String> wanted = new LinkedHashSet<>();
        for (String attr : BankStatementSystemAttribute.TRANSACTION_ATTRIBUTES) {
            String excel = attrToExcel.get(attr);
            if (excel != null) {
                wanted.add(ExcelParserUtils.normalizeHeader(excel));
            }
        }
        if (wanted.isEmpty()) {
            return findWideRow(grid);
        }
        int bestIdx = -1;
        int bestScore = 0;
        for (int i = 0; i < grid.size(); i++) {
            int score = 0;
            for (GridCell cell : grid.get(i)) {
                if (wanted.contains(ExcelParserUtils.normalizeHeader(display(cell)))) {
                    score++;
                }
            }
            if (score > bestScore) {
                bestScore = score;
                bestIdx = i;
            }
        }
        return bestScore >= 2 ? bestIdx : -1;
    }

    private static Map<String, Integer> indexTransactionColumns(
            List<GridCell> headerRow, Map<String, String> excelColumnToAttribute) {
        Map<String, Integer> attrToIndex = new LinkedHashMap<>();
        for (int i = 0; i < headerRow.size(); i++) {
            String cell = display(headerRow.get(i));
            if (cell.isBlank()) {
                continue;
            }
            String norm = ExcelParserUtils.normalizeHeader(cell);
            for (var e : excelColumnToAttribute.entrySet()) {
                if (!BankStatementSystemAttribute.TRANSACTION_ATTRIBUTES.contains(e.getValue())) {
                    continue;
                }
                if (norm.equals(ExcelParserUtils.normalizeHeader(e.getKey()))) {
                    attrToIndex.putIfAbsent(e.getValue(), i);
                }
            }
        }
        return attrToIndex;
    }

    private static Map<String, GridCell> extractHeaderFields(
            List<List<GridCell>> preamble, Map<String, String> attrToExcel) {
        Map<String, GridCell> values = new LinkedHashMap<>();
        for (String attr : BankStatementSystemAttribute.HEADER_ATTRIBUTES) {
            String excel = attrToExcel.get(attr);
            if (excel == null) {
                continue;
            }
            String wanted = ExcelParserUtils.normalizeHeader(excel);
            for (List<GridCell> row : preamble) {
                GridCell found = matchHeaderCell(row, wanted);
                if (found != null && !found.isBlank()) {
                    values.put(attr, found);
                    break;
                }
            }
        }
        return values;
    }

    private static GridCell matchHeaderCell(List<GridCell> row, String wantedNorm) {
        for (int i = 0; i < row.size(); i++) {
            GridCell cell = row.get(i);
            if (cell.isBlank()) {
                continue;
            }
            String text = display(cell);
            String[] parts = text.split(":", 2);
            if (ExcelParserUtils.normalizeHeader(parts[0]).equals(wantedNorm)) {
                if (parts.length > 1 && !parts[1].isBlank()) {
                    return GridCell.text(parts[1].trim());
                }
                for (int j = i + 1; j < row.size(); j++) {
                    if (!row.get(j).isBlank()) {
                        return row.get(j);
                    }
                }
            }
        }
        return null;
    }

    private static String headerLabel(List<GridCell> row) {
        for (GridCell cell : row) {
            if (!cell.isBlank()) {
                String[] parts = display(cell).split(":", 2);
                return parts[0].trim();
            }
        }
        return null;
    }

    private ParsedRow parseTransaction(List<GridCell> row, Map<String, Integer> attrToIndex) {
        String description = display(mapped(row, attrToIndex, BankStatementSystemAttribute.TRANSACTION_DESCRIPTION));
        if (description.isBlank()) {
            return null;
        }
        BigDecimal amount = parseBalance(mapped(row, attrToIndex, BankStatementSystemAttribute.TRANSACTION_AMOUNT));
        DebitCredit dc = parseDebitCredit(display(mapped(row, attrToIndex, BankStatementSystemAttribute.DEBIT_CREDIT)));
        if (amount == null || amount.compareTo(BigDecimal.ZERO) == 0 || dc == null) {
            return null;
        }
        GridCell dateCell = mapped(row, attrToIndex, BankStatementSystemAttribute.TRANSACTION_DATE);
        LocalDateTime txnDate = parseTransactionDate(dateCell);
        return new ParsedRow(
                txnDate == null ? null : txnDate.toInstant(ZoneOffset.UTC),
                description,
                amount.abs(),
                dc,
                blankToNull(display(mapped(row, attrToIndex, BankStatementSystemAttribute.REFERENCE_NO))),
                toLocalDate(parseDateTimeCell(mapped(row, attrToIndex, BankStatementSystemAttribute.VALUE_DATE), false)),
                blankToNull(display(mapped(row, attrToIndex, BankStatementSystemAttribute.TRANSACTION_BRANCH))),
                parseBalance(mapped(row, attrToIndex, BankStatementSystemAttribute.RUNNING_BALANCE)));
    }

    /**
     * HDFC .xlsx Transaction Date cells are Excel date serials (NUMERIC), not strings.
     */
    LocalDateTime parseTransactionDate(GridCell cell) {
        return parseDateTimeCell(cell, true);
    }

    private LocalDateTime parseDateTimeCell(GridCell cell, boolean logRaw) {
        if (cell == null || cell.type() == CellType.BLANK) {
            return null;
        }
        if (logRaw) {
            log.info("TransactionDate raw cell display='{}' type={} numeric={} dateTime={}",
                    cell.display(), cell.type(), cell.numeric(), cell.dateTime());
        }
        return switch (cell.type()) {
            case NUMERIC -> {
                if (cell.dateTime() != null) {
                    yield cell.dateTime();
                }
                yield parseExcelSerial(cell.numeric());
            }
            case STRING -> parseDateTimeString(cell.display());
            default -> cell.dateTime() != null ? cell.dateTime() : parseDateTimeString(cell.display());
        };
    }

    private static LocalDateTime parseExcelSerial(Double numeric) {
        if (numeric == null) {
            return null;
        }
        try {
            return DateUtil.getLocalDateTime(numeric);
        } catch (Exception e) {
            return null;
        }
    }

    private static LocalDateTime parseDateTimeString(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String v = raw.trim();
        for (DateTimeFormatter fmt : DATE_TIMES) {
            try {
                return LocalDateTime.parse(v, fmt);
            } catch (DateTimeParseException ignored) {
                // next
            }
        }
        LocalDate date = parseDate(v);
        if (date != null) {
            return date.atStartOfDay();
        }
        try {
            double serial = Double.parseDouble(v.replace(",", ""));
            if (serial > 20_000 && serial < 80_000) {
                return DateUtil.getLocalDateTime(serial);
            }
        } catch (Exception ignored) {
            // not a serial
        }
        return null;
    }

    private static LocalDate toLocalDate(LocalDateTime value) {
        return value == null ? null : value.toLocalDate();
    }

    private static DebitCredit parseDebitCredit(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String v = raw.trim().toUpperCase(Locale.ROOT);
        if (v.startsWith("C") || v.contains("CR")) {
            return DebitCredit.C;
        }
        if (v.startsWith("D")) {
            return DebitCredit.D;
        }
        return null;
    }

    private static GridCell mapped(List<GridCell> row, Map<String, Integer> attrToIndex, String attr) {
        Integer idx = attrToIndex.get(attr);
        if (idx == null || idx < 0 || idx >= row.size() || row.get(idx) == null) {
            return GridCell.blank();
        }
        return row.get(idx);
    }

    private static Map<String, String> invert(Map<String, String> excelColumnToAttribute) {
        Map<String, String> out = new LinkedHashMap<>();
        if (excelColumnToAttribute == null) {
            return out;
        }
        for (var e : excelColumnToAttribute.entrySet()) {
            out.putIfAbsent(e.getValue(), e.getKey());
        }
        return out;
    }

    private static boolean isBlankRow(List<GridCell> row) {
        return row.stream().allMatch(GridCell::isBlank);
    }

    private static String display(GridCell cell) {
        return cell == null || cell.display() == null ? "" : cell.display();
    }

    private static LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String v = raw.trim();
        for (DateTimeFormatter fmt : DATES) {
            try {
                return LocalDate.parse(v, fmt);
            } catch (DateTimeParseException ignored) {
                // next
            }
        }
        return null;
    }

    private static BigDecimal parseBalance(GridCell cell) {
        if (cell == null || cell.type() == CellType.BLANK) {
            return null;
        }
        if (cell.type() == CellType.NUMERIC && cell.numeric() != null && cell.dateTime() == null) {
            return BigDecimal.valueOf(cell.numeric()).setScale(2, RoundingMode.HALF_UP);
        }
        return parseDecimal(display(cell));
    }

    private static BigDecimal parseDecimal(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String cleaned = raw.replace(",", "").replace("\"", "").trim();
        if (cleaned.isEmpty() || cleaned.equals("-")) {
            return null;
        }
        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v;
    }

    private static String stripExtension(String filename) {
        int slash = Math.max(filename.lastIndexOf('/'), filename.lastIndexOf('\\'));
        String base = slash >= 0 ? filename.substring(slash + 1) : filename;
        int dot = base.lastIndexOf('.');
        return dot > 0 ? base.substring(0, dot) : base;
    }

    static List<String> parseCsvLine(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        cur.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    cur.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                out.add(cur.toString().trim());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString().trim());
        return out;
    }
}
