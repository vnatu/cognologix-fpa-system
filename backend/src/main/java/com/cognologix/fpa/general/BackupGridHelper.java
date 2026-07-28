package com.cognologix.fpa.general;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/** Shared row parsing/formatting for module backup/restore grids (ADR-044 Tier 2). */
public final class BackupGridHelper {

    private BackupGridHelper() {}

    public static String cell(String[] row, int col) {
        if (row == null || col >= row.length || row[col] == null) {
            return null;
        }
        String v = row[col].trim();
        return v.isEmpty() ? null : v;
    }

    public static String requireCell(String[] row, int col, String field) {
        String v = cell(row, col);
        if (v == null) {
            throw new GeneralBadRequestException(field + " is required");
        }
        return v;
    }

    public static Integer parseInt(String raw, String field) {
        if (raw == null) {
            return null;
        }
        try {
            return ExcelNumberParser.parseInteger(raw);
        } catch (IllegalArgumentException e) {
            throw new GeneralBadRequestException("Invalid integer for " + field + ": " + raw);
        }
    }

    public static int parseIntRequired(String raw, String field) {
        Integer v = parseInt(raw, field);
        if (v == null) {
            throw new GeneralBadRequestException(field + " is required");
        }
        return v;
    }

    public static BigDecimal parseDecimal(String raw, String field) {
        if (raw == null) {
            return null;
        }
        try {
            return ExcelNumberParser.parseAmount(raw);
        } catch (IllegalArgumentException e) {
            throw new GeneralBadRequestException("Invalid decimal for " + field + ": " + raw);
        }
    }

    public static BigDecimal parseDecimalRequired(String raw, String field) {
        BigDecimal v = parseDecimal(raw, field);
        if (v == null) {
            throw new GeneralBadRequestException(field + " is required");
        }
        return v;
    }

    public static LocalDate parseDate(String raw, String field) {
        if (raw == null) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException e) {
            throw new GeneralBadRequestException("Invalid date for " + field + ": " + raw);
        }
    }

    public static Instant parseInstant(String raw, String field) {
        if (raw == null) {
            return null;
        }
        try {
            return Instant.parse(raw.trim());
        } catch (DateTimeParseException e) {
            throw new GeneralBadRequestException("Invalid instant for " + field + ": " + raw);
        }
    }

    public static boolean parseBoolean(String raw) {
        if (raw == null) {
            return false;
        }
        return "true".equalsIgnoreCase(raw.trim()) || "1".equals(raw.trim()) || "yes".equalsIgnoreCase(raw.trim());
    }

    public static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    public static String[] row(String... cells) {
        return cells;
    }

    public static List<String[]> sortedRows(List<String[]> rows, int... sortCols) {
        List<String[]> copy = new ArrayList<>(rows);
        copy.sort((a, b) -> {
            for (int col : sortCols) {
                String av = cell(a, col);
                String bv = cell(b, col);
                if (av == null && bv == null) {
                    continue;
                }
                if (av == null) {
                    return -1;
                }
                if (bv == null) {
                    return 1;
                }
                int cmp = av.compareToIgnoreCase(bv);
                if (cmp != 0) {
                    return cmp;
                }
            }
            return 0;
        });
        return copy;
    }
}
