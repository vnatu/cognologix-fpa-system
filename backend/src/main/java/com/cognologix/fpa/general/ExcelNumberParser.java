package com.cognologix.fpa.general;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Shared numeric parsing for Excel/Zoho imports across modules.
 * Strips currency symbols (e.g. ₹) and grouping commas so Indian-format
 * values like {@code ₹1,14,47,529.60} parse correctly.
 *
 * <p>Zoho Books / Zoho Payroll export amounts in full rupees (or full currency units).
 * The FP&amp;A system stores monetary values in Rs Lakhs — use {@link #toRsLakhs(BigDecimal)}
 * after {@link #parseAmount(String)} for those import paths (ADR-046).
 */
public final class ExcelNumberParser {

    private static final BigDecimal ONE_LAKH = new BigDecimal("100000");

    private ExcelNumberParser() {}

    /**
     * Cleans a raw Excel/Zoho numeric cell value: removes ₹, commas, and trims.
     * Returns null when the input is null/blank or cleans to empty.
     */
    public static String cleanNumeric(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String cleaned = raw.replace("₹", "")
                .replace(",", "")
                .trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    /**
     * Parses an amount (decimal) from a Zoho/Excel cell. Null/blank → null.
     *
     * @throws IllegalArgumentException if the cleaned value is not a valid number
     */
    public static BigDecimal parseAmount(String raw) {
        String cleaned = cleanNumeric(raw);
        if (cleaned == null) {
            return null;
        }
        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Cannot parse amount value: '" + raw + "'");
        }
    }

    /**
     * Converts a full-rupee (or full currency-unit) amount from Zoho exports into Rs Lakhs.
     * Null-safe. Scale 2, {@link RoundingMode#HALF_UP}.
     */
    public static BigDecimal toRsLakhs(BigDecimal fullRupeeAmount) {
        if (fullRupeeAmount == null) {
            return null;
        }
        return fullRupeeAmount.divide(ONE_LAKH, 2, RoundingMode.HALF_UP);
    }

    /**
     * Parses an integer from a Zoho/Excel cell (also strips ₹ and commas).
     * Accepts whole numbers and decimals that round cleanly via {@link Math#round}.
     * Null/blank → null.
     *
     * @throws IllegalArgumentException if the cleaned value is not a valid number
     */
    public static Integer parseInteger(String raw) {
        String cleaned = cleanNumeric(raw);
        if (cleaned == null) {
            return null;
        }
        try {
            // Prefer exact integer parse; fall back to decimal→round for Excel numeric cells.
            if (cleaned.indexOf('.') < 0 && cleaned.indexOf('e') < 0 && cleaned.indexOf('E') < 0) {
                return Integer.valueOf(cleaned);
            }
            return (int) Math.round(Double.parseDouble(cleaned));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Cannot parse integer value: '" + raw + "'");
        }
    }
}
