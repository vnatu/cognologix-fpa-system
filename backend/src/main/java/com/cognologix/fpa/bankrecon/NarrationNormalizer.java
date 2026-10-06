package com.cognologix.fpa.bankrecon;

import java.util.Locale;
import java.util.regex.Pattern;

final class NarrationNormalizer {

    private static final Pattern TRAILING_DATE = Pattern.compile(
            "\\b\\d{1,2}[-/]\\d{1,2}[-/]\\d{2,4}\\b");
    /**
     * Bank reference after a hyphen (UTR, IFSC-style codes). A digit is required so a
     * letter-only token such as the company name is kept for contra detection.
     */
    private static final Pattern REF_AFTER_DASH = Pattern.compile("(?<=-)(?=[A-Z0-9]*\\d)[A-Z0-9]{6,}");
    private static final Pattern MULTI_SPACE = Pattern.compile("\\s+");

    private NarrationNormalizer() {}

    static String normalise(String raw) {
        if (raw == null) {
            return "";
        }
        String text = raw.toUpperCase(Locale.ROOT).trim();
        text = TRAILING_DATE.matcher(text).replaceAll(" ");
        text = REF_AFTER_DASH.matcher(text).replaceAll("");
        text = text.replace('-', ' ');
        return MULTI_SPACE.matcher(text).replaceAll(" ").trim();
    }
}
