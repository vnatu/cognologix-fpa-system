package com.cognologix.fpa.bankrecon;

import com.cognologix.fpa.bankrecon.domain.TallyLedger;
import com.cognologix.fpa.bankrecon.domain.TallyLedgerHint;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Reads a ledger-code workbook and decides what would change, without writing. */
final class LedgerCodeImport {

    record ParsedRow(
            int rowNumber,
            String ledgerName,
            String ledgerCode,
            String groupName,
            String purpose,
            String keywords,
            String typicalAmount,
            String disambiguationNote) {}

    record Line(int rowNumber, String ledgerName, String ledgerCode, String groupName) {}

    record Issue(int rowNumber, String ledgerName, String message) {}

    record Planned(List<Line> toUpdate, List<Line> toAdd, List<Line> unchanged, List<Issue> errors, List<ParsedRow> rows) {}

    private LedgerCodeImport() {}

    static List<ParsedRow> read(InputStream in) {
        try (Workbook wb = WorkbookFactory.create(in)) {
            Sheet sheet = wb.getNumberOfSheets() == 0 ? null : wb.getSheetAt(0);
            if (sheet == null || sheet.getPhysicalNumberOfRows() == 0) {
                throw new IllegalArgumentException("The workbook has no rows");
            }
            Row header = sheet.getRow(0);
            if (header == null) {
                throw new IllegalArgumentException("The workbook has no header row");
            }
            DataFormatter formatter = new DataFormatter();
            Map<String, Integer> columns = new LinkedHashMap<>();
            for (int i = 0; i < header.getLastCellNum(); i++) {
                String name = formatter.formatCellValue(header.getCell(i)).trim();
                if (!name.isEmpty()) {
                    columns.put(name, i);
                }
            }
            if (!columns.containsKey("Ledger Name") || !columns.containsKey("Ledger Code")) {
                throw new IllegalArgumentException("The workbook must have Ledger Name and Ledger Code columns");
            }
            List<ParsedRow> rows = new ArrayList<>();
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }
                String name = cell(formatter, row, columns.get("Ledger Name"));
                String code = cell(formatter, row, columns.get("Ledger Code"));
                String group = cell(formatter, row, columns.get("Group"));
                String purpose = cell(formatter, row, columns.get("Purpose"));
                String keywords = cell(formatter, row, columns.get("Keywords"));
                String typical = cell(formatter, row, columns.get("Typical Amount"));
                String note = cell(formatter, row, columns.get("Disambiguation Note"));
                if (name.isEmpty() && code.isEmpty() && group.isEmpty()
                        && purpose.isEmpty() && keywords.isEmpty() && typical.isEmpty() && note.isEmpty()) {
                    continue;
                }
                rows.add(new ParsedRow(r + 1, trimEdges(name), code.trim(), group.trim(),
                        purpose, keywords, typical, note));
            }
            return rows;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Unable to read the workbook: " + e.getMessage(), e);
        }
    }

    static Planned plan(
            List<ParsedRow> rows,
            List<TallyLedger> ledgers,
            java.util.Set<String> knownGroups,
            Map<UUID, TallyLedgerHint> hints) {
        Map<String, TallyLedger> exact = new LinkedHashMap<>();
        Map<String, List<TallyLedger>> spaced = new LinkedHashMap<>();
        Map<String, List<TallyLedger>> loose = new LinkedHashMap<>();
        Map<String, TallyLedger> byCode = new LinkedHashMap<>();
        for (TallyLedger ledger : ledgers) {
            exact.put(ledger.getLedgerName(), ledger);
            spaced.computeIfAbsent(spacingKey(ledger.getLedgerName()), key -> new ArrayList<>()).add(ledger);
            loose.computeIfAbsent(looseName(ledger.getLedgerName()), key -> new ArrayList<>()).add(ledger);
            if (ledger.getLedgerCode() != null) {
                byCode.put(ledger.getLedgerCode(), ledger);
            }
        }
        List<Line> toUpdate = new ArrayList<>();
        List<Line> toAdd = new ArrayList<>();
        List<Line> unchanged = new ArrayList<>();
        List<Issue> errors = new ArrayList<>();
        Map<String, Integer> codeRows = new LinkedHashMap<>();
        Map<String, Integer> nameRows = new LinkedHashMap<>();
        for (ParsedRow row : rows) {
            List<String> problems = new ArrayList<>();
            if (row.ledgerName().isEmpty()) {
                problems.add("Ledger name is required");
            }
            String code = LedgerCodeRules.canonical(row.ledgerCode());
            String codeProblem = code == null ? "Ledger code is required" : LedgerCodeRules.rejection(code);
            if (codeProblem != null) {
                problems.add(codeProblem);
            }
            if (!row.ledgerName().isEmpty()) {
                Integer earlier = nameRows.putIfAbsent(row.ledgerName(), row.rowNumber());
                if (earlier != null) {
                    problems.add("Ledger name is duplicated in this file");
                }
            }
            if (code != null && codeProblem == null) {
                Integer earlier = codeRows.putIfAbsent(code, row.rowNumber());
                if (earlier != null) {
                    problems.add("Code " + code + " is duplicated in this file");
                }
            }
            TallyLedger match = row.ledgerName().isEmpty() ? null : exact.get(row.ledgerName());
            if (match == null && !row.ledgerName().isEmpty()) {
                List<TallyLedger> spacedHits = spaced.getOrDefault(spacingKey(row.ledgerName()), List.of());
                if (spacedHits.size() == 1) {
                    match = spacedHits.getFirst();
                } else if (spacedHits.size() > 1) {
                    problems.add("ambiguous: matches " + spacedHits.size() + " ledgers: " + quoteNames(spacedHits));
                } else {
                    List<TallyLedger> possible = loose.getOrDefault(looseName(row.ledgerName()), List.of());
                    if (!possible.isEmpty()) {
                        problems.add("possible match: " + quoteNames(possible));
                    }
                }
            }
            if (code != null && codeProblem == null) {
                TallyLedger owner = byCode.get(code);
                if (owner != null && (match == null || !owner.getId().equals(match.getId()))) {
                    problems.add("Code " + code + " is already used by " + owner.getLedgerName());
                }
            }
            boolean isNew = match == null && problems.stream().noneMatch(p ->
                    p.startsWith("possible match:") || p.startsWith("ambiguous:"));
            if (isNew && problems.stream().noneMatch(p -> p.startsWith("Ledger name"))) {
                if (row.groupName().isEmpty()) {
                    problems.add("Group is required for a new ledger");
                } else if (!knownGroups.contains(row.groupName())) {
                    problems.add("Unknown group: " + row.groupName());
                }
            }
            if (!problems.isEmpty()) {
                for (String problem : problems) {
                    errors.add(new Issue(row.rowNumber(), row.ledgerName(), problem));
                }
                continue;
            }
            String storedName = match == null ? row.ledgerName() : match.getLedgerName();
            Line line = new Line(row.rowNumber(), storedName, code, row.groupName());
            if (match == null) {
                toAdd.add(line);
            } else if (same(match, hints.get(match.getId()), code, row)) {
                unchanged.add(line);
            } else {
                toUpdate.add(line);
            }
        }
        return new Planned(toUpdate, toAdd, unchanged, errors, rows);
    }

    /** Trim, then runs of whitespace or non-breaking spaces become one space. Case is kept. */
    static String spacingKey(String name) {
        if (name == null || name.isEmpty()) {
            return "";
        }
        StringBuilder key = new StringBuilder();
        boolean pendingSpace = false;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (isSpace(c)) {
                pendingSpace = key.length() > 0;
                continue;
            }
            if (pendingSpace) {
                key.append(' ');
                pendingSpace = false;
            }
            key.append(c);
        }
        return key.toString();
    }

    static String looseName(String name) {
        if (name == null) {
            return "";
        }
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim().replaceAll(" +", " ");
    }

    private static String quoteNames(List<TallyLedger> ledgers) {
        return ledgers.stream()
                .map(ledger -> "\"" + ledger.getLedgerName() + "\"")
                .reduce((left, right) -> left + ", " + right)
                .orElse("");
    }

    private static String trimEdges(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        int start = 0;
        int end = value.length();
        while (start < end && isSpace(value.charAt(start))) {
            start++;
        }
        while (end > start && isSpace(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(start, end);
    }

    private static boolean isSpace(char c) {
        return Character.isWhitespace(c) || c == '\u00A0' || c == '\u2007' || c == '\u202F';
    }

    private static boolean same(TallyLedger ledger, TallyLedgerHint hint, String code, ParsedRow row) {
        if (code != null && !code.equals(ledger.getLedgerCode())) {
            return false;
        }
        return !hintChanges(hint, row);
    }

    static boolean hintChanges(TallyLedgerHint hint, ParsedRow row) {
        return differs(row.purpose(), hint == null ? null : hint.getPurpose())
                || differs(row.keywords(), hint == null ? null : hint.getKeywords())
                || differs(row.typicalAmount(), hint == null ? null : hint.getTypicalAmount())
                || differs(row.disambiguationNote(), hint == null ? null : hint.getDisambiguationNote());
    }

    private static boolean differs(String incoming, String current) {
        if (incoming == null || incoming.isEmpty()) {
            return false;
        }
        return !incoming.equals(current == null ? "" : current);
    }

    private static String cell(DataFormatter formatter, Row row, Integer index) {
        if (index == null) {
            return "";
        }
        return formatter.formatCellValue(row.getCell(index)).trim();
    }
}
