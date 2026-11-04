package com.cognologix.fpa.revenue;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;

/**
 * Parses optional Zoho Books Service Month / Service Year cells.
 * A blank or unreadable value is a result, never an exception.
 */
public final class ServicePeriodParser {

    /** Same window as the upload period tag. */
    public static final int MIN_YEAR = 2000;
    public static final int MAX_YEAR = 2100;

    private static final Map<String, Integer> MONTH_NAMES = Map.ofEntries(
            Map.entry("january", 1), Map.entry("jan", 1),
            Map.entry("february", 2), Map.entry("feb", 2),
            Map.entry("march", 3), Map.entry("mar", 3),
            Map.entry("april", 4), Map.entry("apr", 4),
            Map.entry("may", 5),
            Map.entry("june", 6), Map.entry("jun", 6),
            Map.entry("july", 7), Map.entry("jul", 7),
            Map.entry("august", 8), Map.entry("aug", 8),
            Map.entry("september", 9), Map.entry("sep", 9), Map.entry("sept", 9),
            Map.entry("october", 10), Map.entry("oct", 10),
            Map.entry("november", 11), Map.entry("nov", 11),
            Map.entry("december", 12), Map.entry("dec", 12));

    private ServicePeriodParser() {}

    public record MonthParse(Integer month, boolean blank, boolean unreadable) {
        static MonthParse blankMonth() {
            return new MonthParse(null, true, false);
        }

        static MonthParse validMonth(int month) {
            return new MonthParse(month, false, false);
        }

        static MonthParse unreadableMonth() {
            return new MonthParse(null, false, true);
        }
    }

    public record YearParse(Integer year, boolean blank, boolean unreadable) {
        static YearParse blankYear() {
            return new YearParse(null, true, false);
        }

        static YearParse validYear(int year) {
            return new YearParse(year, false, false);
        }

        static YearParse unreadableYear() {
            return new YearParse(null, false, true);
        }
    }

    public static MonthParse parseMonth(String raw) {
        if (raw == null || raw.isBlank()) {
            return MonthParse.blankMonth();
        }
        String value = stripTrailingDot(raw.trim());
        Integer numeric = wholeNumber(value);
        if (numeric != null) {
            return numeric >= 1 && numeric <= 12 ? MonthParse.validMonth(numeric) : MonthParse.unreadableMonth();
        }
        Integer named = MONTH_NAMES.get(value.toLowerCase(Locale.ROOT));
        return named != null ? MonthParse.validMonth(named) : MonthParse.unreadableMonth();
    }

    public static YearParse parseYear(String raw) {
        if (raw == null || raw.isBlank()) {
            return YearParse.blankYear();
        }
        Integer numeric = wholeNumber(stripTrailingDot(raw.trim()));
        if (numeric != null && numeric >= MIN_YEAR && numeric <= MAX_YEAR) {
            return YearParse.validYear(numeric);
        }
        return YearParse.unreadableYear();
    }

    private static String stripTrailingDot(String value) {
        return value.endsWith(".") ? value.substring(0, value.length() - 1).trim() : value;
    }

    /** Whole numbers only, including {@code 8}, {@code 08}, and {@code 8.0}. */
    private static Integer wholeNumber(String value) {
        try {
            BigDecimal stripped = new BigDecimal(value).stripTrailingZeros();
            if (stripped.scale() > 0) {
                return null;
            }
            return stripped.intValueExact();
        } catch (NumberFormatException | ArithmeticException ex) {
            return null;
        }
    }
}
