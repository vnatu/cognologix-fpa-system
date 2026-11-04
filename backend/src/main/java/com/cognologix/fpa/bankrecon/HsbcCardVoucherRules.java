package com.cognologix.fpa.bankrecon;

import com.cognologix.fpa.bankrecon.domain.DebitCredit;
import com.cognologix.fpa.bankrecon.domain.VoucherType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Voucher sides for an HSBC card Excel export. The card ledger is the statement side.
 * A card payment credits a bank ledger and is Contra.
 */
final class HsbcCardVoucherRules {

    static final String STATEMENT_SOURCE = "HSBC_CARD_XLSX";
    static final String CFG_DEBIT_TYPES = "hsbc_card_debit_types";
    static final String CFG_PAYMENT_PATTERN = "hsbc_card_payment_pattern";
    static final String DEFAULT_DEBIT_TYPES = "Purchase";
    static final String DEFAULT_PAYMENT_PATTERN = "CREDIT CARD PAYMENT,PAYMENT - THANK YOU";
    private static final Pattern CARD_LAST4 = Pattern.compile("\\d{4}$");
    static final String DOUBLE_POST_WARNING =
            "Also appears on the bank statement: post from one side only.";
    static final String CHECK_CURRENCY = "Check currency";
    static final String ALREADY_IMPORTED = "Already imported";
    static final int NARRATION_MAX = 120;

    private HsbcCardVoucherRules() {}

    static boolean debitType(String transactionType, String debitTypes) {
        String type = fold(transactionType);
        if (type.isEmpty()) {
            return false;
        }
        String configured = debitTypes == null || debitTypes.isBlank() ? DEFAULT_DEBIT_TYPES : debitTypes;
        for (String part : configured.split(",")) {
            if (type.equals(fold(part))) {
                return true;
            }
        }
        return false;
    }

    /**
     * A type outside the debit list is a credit. A negative amount is a credit even when the type is a debit type.
     */
    static DebitCredit direction(String transactionType, BigDecimal signedAmount, String debitTypes) {
        boolean negative = signedAmount != null && signedAmount.signum() < 0;
        if (!negative && debitType(transactionType, debitTypes)) {
            return DebitCredit.D;
        }
        return DebitCredit.C;
    }

    /** Comma-separated prefixes. Merchant name or transaction type may match any one of them. */
    static boolean isCardPayment(String merchantName, String transactionType, String pattern) {
        String configured = pattern == null || pattern.isBlank() ? DEFAULT_PAYMENT_PATTERN : pattern;
        for (String part : configured.split(",")) {
            String folded = fold(part);
            if (folded.isEmpty()) {
                continue;
            }
            if (startsWith(merchantName, folded) || startsWith(transactionType, folded)) {
                return true;
            }
        }
        return false;
    }

    static VoucherType voucherType(DebitCredit direction, boolean cardPayment) {
        if (cardPayment && direction == DebitCredit.C) {
            return VoucherType.CONTRA;
        }
        return direction == DebitCredit.D ? VoucherType.PAYMENT : VoucherType.RECEIPT;
    }

    static String learnedKey(String merchantName) {
        return NarrationNormalizer.normalise(merchantName);
    }

    static String narration(
            String merchant, String city, String country, String foreignCurrency, BigDecimal foreignAmount) {
        StringBuilder text = new StringBuilder();
        append(text, merchant);
        append(text, city);
        if (country != null && !country.isBlank() && !fold(country).equals("IND")) {
            append(text, country);
        }
        if (foreignAmount != null && foreignAmount.signum() != 0) {
            String money = foreignAmount.setScale(2, RoundingMode.HALF_UP).toPlainString();
            String ccy = foreignCurrency == null ? "" : foreignCurrency.trim().toUpperCase(Locale.ROOT);
            append(text, (ccy + " " + money).trim());
        }
        String value = text.toString().replaceAll("\\s+", " ").trim();
        if (value.length() > NARRATION_MAX) {
            return value.substring(0, NARRATION_MAX).trim();
        }
        return value;
    }

    /** Hyphens and spaces are removed, then the trailing four digits. Digits hidden by X are not used. */
    static String last4(String masked) {
        if (masked == null) {
            return null;
        }
        String compact = masked
                .replace("-", "")
                .replace(" ", "")
                .replace("\u00A0", "")
                .replace("\u2007", "")
                .replace("\u202F", "");
        Matcher matcher = CARD_LAST4.matcher(compact);
        return matcher.find() ? matcher.group() : null;
    }

    static List<String> unmappedCardWarnings(java.util.Collection<String> last4s, java.util.Set<String> mappedLast4) {
        if (last4s == null || last4s.isEmpty()) {
            return List.of();
        }
        java.util.Set<String> mapped = mappedLast4 == null ? java.util.Set.of() : mappedLast4;
        List<String> warnings = new ArrayList<>();
        for (String last4 : last4s) {
            if (last4 != null && !mapped.contains(last4)) {
                warnings.add("No ledger is mapped for card " + last4
                        + ". Add it in Configuration → Account Mapping.");
            }
        }
        return List.copyOf(warnings);
    }

    static String defaultHdfcLedger(List<String> activeHdfcLedgerNames) {
        if (activeHdfcLedgerNames == null || activeHdfcLedgerNames.isEmpty()) {
            return null;
        }
        List<String> names = new ArrayList<>();
        for (String name : activeHdfcLedgerNames) {
            if (name != null && !name.isBlank()) {
                names.add(name.trim());
            }
        }
        names.sort(Comparator.naturalOrder());
        List<String> hdfc = names.stream()
                .filter(name -> name.toUpperCase(Locale.ROOT).contains("HDFC"))
                .toList();
        List<String> pool = hdfc.isEmpty() ? names : hdfc;
        return pool.isEmpty() ? null : pool.getFirst();
    }

    private static void append(StringBuilder text, String part) {
        if (part == null || part.isBlank()) {
            return;
        }
        if (!text.isEmpty()) {
            text.append(' ');
        }
        text.append(part.trim());
    }

    static String clean(String value) {
        if (value == null) {
            return "";
        }
        String cleaned = value
                .replace('\u00A0', ' ')
                .replace('\u2007', ' ')
                .replace('\u202F', ' ')
                .trim();
        return cleaned;
    }

    private static boolean startsWith(String value, String foldedPrefix) {
        return fold(value).startsWith(foldedPrefix);
    }

    private static String fold(String value) {
        if (value == null) {
            return "";
        }
        return clean(value).toUpperCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}
