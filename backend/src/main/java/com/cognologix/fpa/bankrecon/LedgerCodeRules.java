package com.cognologix.fpa.bankrecon;

import java.util.Locale;
import java.util.Set;

/** Ledger codes are 2 to 10 letters and digits, stored uppercase. */
final class LedgerCodeRules {

    static final Set<String> RESERVED = Set.of(
            "FT", "NEFT", "RTGS", "IMPS", "UPI", "ATM", "ACH", "NACH", "CBDT", "TDS", "GST", "SALARY", "REFUND");

    private LedgerCodeRules() {}

    /** Trimmed uppercase code, or null when the input is blank. */
    static String canonical(String raw) {
        if (raw == null) {
            return null;
        }
        String code = raw.trim().toUpperCase(Locale.ROOT);
        return code.isEmpty() ? null : code;
    }

    /** Null when the code is acceptable. */
    static String rejection(String code) {
        if (code == null || !code.matches("[A-Z0-9]{2,10}")) {
            return "Code must be 2 to 10 letters and digits";
        }
        if (RESERVED.contains(code)) {
            return "Code " + code + " is reserved";
        }
        return null;
    }
}
