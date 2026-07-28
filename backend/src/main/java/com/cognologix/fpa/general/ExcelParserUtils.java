package com.cognologix.fpa.general;

import java.util.Locale;

/**
 * Shared Excel header helpers for import/export round-trips (ADR-047).
 * Normalizes case, spaces, hyphens, and underscores so parsers tolerate
 * Title Case vs snake_case and minor Zoho header drift.
 */
public final class ExcelParserUtils {

    private ExcelParserUtils() {}

    /**
     * Normalizes a header for comparison: trim, lowercase, collapse spaces/hyphens/underscores
     * to a single underscore, then strip remaining non-alphanumeric characters (except {@code _}).
     * Null → empty string.
     */
    public static String normalizeHeader(String header) {
        if (header == null) {
            return "";
        }
        return header.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\\s_\\-]+", "_")
                .replaceAll("[^a-z0-9_]", "");
    }
}
