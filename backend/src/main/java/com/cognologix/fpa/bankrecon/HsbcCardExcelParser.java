package com.cognologix.fpa.bankrecon;

import com.cognologix.fpa.bankrecon.domain.DebitCredit;
import com.cognologix.fpa.bankrecon.domain.VoucherType;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Reads the HSBC card Excel export by header name. Unknown columns are ignored.
 */
final class HsbcCardExcelParser {

    static final List<String> REQUIRED = List.of(
            "Masked Card Number",
            "Transaction Date",
            "Merchant Name",
            "Amount",
            "Currency",
            "Transaction Type",
            "Bank Reference");

    private static final DateTimeFormatter DAY_FIRST = DateTimeFormatter.ofPattern("d/M/uuuu");
    private static final DateTimeFormatter DAY_FIRST_DASH = DateTimeFormatter.ofPattern("d-M-uuuu");

    record ParsedRow(
            LocalDate transactionDate,
            LocalDate statementDate,
            String narration,
            String merchantName,
            String nameOnCard,
            String merchantCategory,
            String transactionType,
            String bankReference,
            String cardLast4,
            BigDecimal amount,
            DebitCredit debitCredit,
            VoucherType voucherType,
            boolean cardPayment,
            String importFlag,
            BigDecimal taxAmount,
            String costCenter,
            String employeeId) {}

    record ParsedFile(
            List<ParsedRow> rows,
            int inFileDuplicates,
            List<String> transactionTypes,
            LocalDate statementDate) {}

    ParsedFile parse(byte[] xlsx, String debitTypes, String paymentPattern) {
        if (xlsx == null || xlsx.length == 0) {
            throw new IllegalArgumentException("Statement file is empty");
        }
        try (InputStream in = new ByteArrayInputStream(xlsx); Workbook workbook = new XSSFWorkbook(in)) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new IllegalArgumentException("The workbook has no sheets");
            }
            return parseSheet(workbook.getSheetAt(0), debitTypes, paymentPattern);
        } catch (IOException ex) {
            throw new IllegalArgumentException("Could not read the HSBC card workbook", ex);
        }
    }

    ParsedFile parseSheet(Sheet sheet, String debitTypes, String paymentPattern) {
        if (sheet == null) {
            throw new IllegalArgumentException("The workbook has no sheets");
        }
        DataFormatter formatter = new DataFormatter();
        Row header = sheet.getRow(sheet.getFirstRowNum());
        if (header == null) {
            throw new IllegalArgumentException("The workbook has no header row");
        }
        Map<String, Integer> columns = headerIndex(header, formatter);
        List<String> missing = new ArrayList<>();
        for (String name : REQUIRED) {
            if (!columns.containsKey(normalize(name))) {
                missing.add(name);
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("Missing required column: " + String.join(", ", missing));
        }
        List<ParsedRow> rows = new ArrayList<>();
        Set<String> seenReferences = new LinkedHashSet<>();
        Set<String> types = new LinkedHashSet<>();
        int duplicates = 0;
        LocalDate statementDate = null;
        int first = sheet.getFirstRowNum() + 1;
        for (int i = first; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null || blankRow(row, columns, formatter)) {
                continue;
            }
            String type = text(row, columns, formatter, "Transaction Type");
            if (type != null && !type.isBlank()) {
                types.add(type.trim());
            }
            String reference = text(row, columns, formatter, "Bank Reference");
            if (reference != null && !reference.isBlank() && !seenReferences.add(reference.trim())) {
                duplicates++;
                continue;
            }
            ParsedRow parsed = readRow(row, columns, formatter, debitTypes, paymentPattern);
            if (parsed == null) {
                continue;
            }
            rows.add(parsed);
            if (parsed.statementDate() != null
                    && (statementDate == null || parsed.statementDate().isAfter(statementDate))) {
                statementDate = parsed.statementDate();
            }
        }
        return new ParsedFile(List.copyOf(rows), duplicates, List.copyOf(types), statementDate);
    }

    static List<ParsedRow> markAlreadyImported(List<ParsedRow> rows, Set<String> existingReferences) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        Set<String> existing = existingReferences == null ? Set.of() : existingReferences;
        List<ParsedRow> marked = new ArrayList<>();
        for (ParsedRow row : rows) {
            if (row.bankReference() != null && existing.contains(row.bankReference())) {
                marked.add(copyFlag(row, HsbcCardVoucherRules.ALREADY_IMPORTED));
            } else {
                marked.add(row);
            }
        }
        return List.copyOf(marked);
    }

    private static ParsedRow copyFlag(ParsedRow row, String flag) {
        return new ParsedRow(
                row.transactionDate(), row.statementDate(), row.narration(), row.merchantName(),
                row.nameOnCard(), row.merchantCategory(), row.transactionType(), row.bankReference(),
                row.cardLast4(), row.amount(), row.debitCredit(), row.voucherType(), row.cardPayment(),
                flag, row.taxAmount(), row.costCenter(), row.employeeId());
    }

    private ParsedRow readRow(
            Row row, Map<String, Integer> columns, DataFormatter formatter,
            String debitTypes, String paymentPattern) {
        LocalDate transactionDate = date(row, columns, formatter, "Transaction Date");
        if (transactionDate == null) {
            transactionDate = date(row, columns, formatter, "Posting Date");
        }
        if (transactionDate == null) {
            return null;
        }
        BigDecimal signed = money(row, columns, formatter, "Amount");
        if (signed == null) {
            return null;
        }
        String currency = text(row, columns, formatter, "Currency");
        String flag = null;
        BigDecimal inr = signed;
        boolean inrCurrency = currency == null || currency.isBlank() || currency.trim().equalsIgnoreCase("INR");
        if (!inrCurrency) {
            BigDecimal consolidated = money(row, columns, formatter, "Consolidated Amount");
            if (consolidated != null) {
                inr = consolidated;
            } else {
                flag = HsbcCardVoucherRules.CHECK_CURRENCY;
            }
        }
        BigDecimal amount = inr.abs().setScale(2, RoundingMode.HALF_UP);
        String merchant = text(row, columns, formatter, "Merchant Name");
        String transactionType = text(row, columns, formatter, "Transaction Type");
        DebitCredit direction = HsbcCardVoucherRules.direction(transactionType, signed, debitTypes);
        boolean cardPayment = direction == DebitCredit.C
                && HsbcCardVoucherRules.isCardPayment(merchant, transactionType, paymentPattern);
        String narration = HsbcCardVoucherRules.narration(
                merchant,
                text(row, columns, formatter, "Merchant City"),
                text(row, columns, formatter, "Merchant Country"),
                text(row, columns, formatter, "Foreign Currency"),
                money(row, columns, formatter, "Foreign Amount"));
        if (narration.isBlank()) {
            narration = "(no merchant)";
        }
        return new ParsedRow(
                transactionDate,
                date(row, columns, formatter, "Statement Date"),
                narration,
                merchant == null ? "" : merchant.trim(),
                text(row, columns, formatter, "Name On Card"),
                text(row, columns, formatter, "Merchant Category"),
                transactionType == null ? "" : transactionType.trim(),
                blankToNull(text(row, columns, formatter, "Bank Reference")),
                HsbcCardVoucherRules.last4(text(row, columns, formatter, "Masked Card Number")),
                amount,
                direction,
                HsbcCardVoucherRules.voucherType(direction, cardPayment),
                cardPayment,
                flag,
                money(row, columns, formatter, "Tax Amount"),
                text(row, columns, formatter, "Cost Center"),
                text(row, columns, formatter, "Employee ID"));
    }

    private static boolean blankRow(Row row, Map<String, Integer> columns, DataFormatter formatter) {
        for (String name : REQUIRED) {
            String value = text(row, columns, formatter, name);
            if (value != null && !value.isBlank()) {
                return false;
            }
        }
        return true;
    }

    private static Map<String, Integer> headerIndex(Row header, DataFormatter formatter) {
        Map<String, Integer> columns = new LinkedHashMap<>();
        for (int i = header.getFirstCellNum(); i < header.getLastCellNum(); i++) {
            Cell cell = header.getCell(i);
            if (cell == null) {
                continue;
            }
            String name = HsbcCardVoucherRules.clean(formatter.formatCellValue(cell));
            if (name.isBlank()) {
                continue;
            }
            columns.putIfAbsent(normalize(name), i);
        }
        return columns;
    }

    private static String normalize(String header) {
        return header.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private static String text(Row row, Map<String, Integer> columns, DataFormatter formatter, String header) {
        Integer index = columns.get(normalize(header));
        if (index == null) {
            return null;
        }
        Cell cell = row.getCell(index);
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return null;
        }
        String value = HsbcCardVoucherRules.clean(formatter.formatCellValue(cell));
        return value.isBlank() ? null : value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static LocalDate date(Row row, Map<String, Integer> columns, DataFormatter formatter, String header) {
        Integer index = columns.get(normalize(header));
        if (index == null) {
            return null;
        }
        Cell cell = row.getCell(index);
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalDate();
        }
        String value = HsbcCardVoucherRules.clean(formatter.formatCellValue(cell));
        if (value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value, DAY_FIRST);
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDate.parse(value, DAY_FIRST_DASH);
            } catch (DateTimeParseException again) {
                return null;
            }
        }
    }

    private static BigDecimal money(Row row, Map<String, Integer> columns, DataFormatter formatter, String header) {
        Integer index = columns.get(normalize(header));
        if (index == null) {
            return null;
        }
        Cell cell = row.getCell(index);
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC && !DateUtil.isCellDateFormatted(cell)) {
            return BigDecimal.valueOf(cell.getNumericCellValue()).setScale(2, RoundingMode.HALF_UP);
        }
        String text = HsbcCardVoucherRules.clean(formatter.formatCellValue(cell));
        if (text.isBlank()) {
            return null;
        }
        String cleaned = text.replace(",", "").replace(" ", "");
        if (cleaned.startsWith("(") && cleaned.endsWith(")") && cleaned.length() > 2) {
            cleaned = "-" + cleaned.substring(1, cleaned.length() - 1);
        }
        try {
            return new BigDecimal(cleaned).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
