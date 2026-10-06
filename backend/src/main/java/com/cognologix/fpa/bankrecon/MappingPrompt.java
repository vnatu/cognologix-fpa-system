package com.cognologix.fpa.bankrecon;

import java.util.List;
import java.util.Set;

/**
 * Static prefix (instructions, hints, ledger catalog) then the per-run transactions,
 * so an unchanged catalog is a byte-identical cached prefix.
 */
final class MappingPrompt {

    static final int SAFE_PROMPT_TOKENS = 12_000;
    static final int EXAMPLE_LIMIT = 3;
    static final int TOKENS_PER_MAPPING_ROW = 40;
    static final int COMPLETION_TOKEN_MARGIN = 500;

    private MappingPrompt() {}

    record HintLine(String voucherType, String text) {}

    record LedgerLine(
            String groupName,
            String ledgerName,
            String keywords,
            String purpose,
            String typicalAmount,
            String note) {}

    record ExampleLine(String narration, String amount, String ledger) {}

    record TransactionLine(
            String id,
            String narration,
            String amount,
            String type,
            List<ExampleLine> examples) {}

    /**
     * Bank-side groups, matched case-insensitively: exact "Bank Accounts", exact "Credit Cards",
     * and any group whose name starts with "Bank OD" (Bank OD, Bank OD A/c, Bank OD Accounts).
     * "Credit Card EMI" is not a bank-side group.
     */
    static int completionTokenBudget(int transactionCount) {
        int rows = Math.max(transactionCount, 0);
        return rows * TOKENS_PER_MAPPING_ROW + COMPLETION_TOKEN_MARGIN;
    }

    static boolean isBankSideGroup(String groupName) {
        if (groupName == null || groupName.isBlank()) {
            return false;
        }
        String name = groupName.trim();
        if (name.equalsIgnoreCase("Bank Accounts") || name.equalsIgnoreCase("Credit Cards")) {
            return true;
        }
        return name.regionMatches(true, 0, "Bank OD", 0, "Bank OD".length());
    }

    static boolean excludedFromCatalog(String groupName, String ledgerName, Set<String> mappedLedgerNames) {
        if (isBankSideGroup(groupName)) {
            return true;
        }
        return ledgerName != null && mappedLedgerNames.contains(ledgerName);
    }

    static String build(
            List<HintLine> hints,
            List<LedgerLine> paymentLedgers,
            List<LedgerLine> receiptLedgers,
            List<TransactionLine> transactions) {
        StringBuilder sb = new StringBuilder();
        sb.append("SYSTEM\n");
        sb.append("Return only a JSON array of objects with transactionId and ledgerName.\n");
        sb.append("Use UNMAPPED when unsure.\n");
        sb.append("Each ledgerName must exactly match a ledger in that transaction's voucher-type catalog.\n\n");
        sb.append("TRANSACTION HINTS\n");
        if (hints == null || hints.isEmpty()) {
            sb.append("(none)\n");
        } else {
            for (HintLine hint : hints) {
                sb.append("- [").append(hint.voucherType()).append("] ").append(hint.text()).append('\n');
            }
        }
        sb.append("\nLEDGER CATALOG\n");
        appendCatalog(sb, "PAYMENT", paymentLedgers);
        appendCatalog(sb, "RECEIPT", receiptLedgers);
        sb.append("TRANSACTIONS\n");
        if (transactions != null) {
            for (TransactionLine tx : transactions) {
                sb.append("- id=").append(tx.id())
                        .append(" narration=\"").append(sanitize(tx.narration())).append('"')
                        .append(" amount=").append(tx.amount() == null ? "" : tx.amount())
                        .append(" type=").append(tx.type()).append('\n');
                List<ExampleLine> examples = tx.examples();
                if (examples == null || examples.isEmpty()) {
                    sb.append("  examples: []\n");
                } else {
                    sb.append("  examples:\n");
                    for (ExampleLine ex : examples) {
                        sb.append("  - narration=\"").append(sanitize(ex.narration())).append('"')
                                .append(" amount=").append(ex.amount() == null ? "" : ex.amount())
                                .append(" ledger=").append(ex.ledger()).append('\n');
                    }
                }
            }
        }
        return sb.toString();
    }

    static String staticPrefix(String prompt) {
        int marker = prompt.indexOf("TRANSACTIONS\n");
        return marker < 0 ? prompt : prompt.substring(0, marker);
    }

    static String catalogSection(String prompt) {
        int start = prompt.indexOf("LEDGER CATALOG\n");
        int end = prompt.indexOf("\nTRANSACTIONS\n");
        if (start < 0) {
            return "";
        }
        int from = start + "LEDGER CATALOG\n".length();
        if (end < 0 || end < from) {
            return prompt.substring(from);
        }
        return prompt.substring(from, end);
    }

    static int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return Math.max(1, text.length() / 4);
    }

    static String formatLedger(LedgerLine line) {
        StringBuilder sb = new StringBuilder(line.ledgerName());
        appendPart(sb, line.keywords());
        appendPart(sb, line.purpose());
        appendPart(sb, line.typicalAmount());
        appendPart(sb, line.note());
        return sb.toString();
    }

    private static void appendCatalog(StringBuilder sb, String voucherType, List<LedgerLine> lines) {
        sb.append(voucherType).append('\n');
        String currentGroup = null;
        if (lines != null) {
            for (LedgerLine line : lines) {
                String group = line.groupName() == null ? "" : line.groupName();
                if (!group.equals(currentGroup)) {
                    currentGroup = group;
                    sb.append("## ").append(group).append('\n');
                }
                sb.append(formatLedger(line)).append('\n');
            }
        }
        sb.append('\n');
    }

    private static void appendPart(StringBuilder sb, String part) {
        if (part == null || part.isBlank()) {
            return;
        }
        sb.append(" | ").append(part.trim());
    }

    private static String sanitize(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\"", "'").replace("\n", " ").replace("\r", " ");
    }
}
