package com.cognologix.fpa.bankrecon;

import java.util.List;
import java.util.Set;

/**
 * System message is instructions, output format, matching rules, the catalog, then hints.
 * Hints are last so editing one does not change the catalog bytes. The user message is
 * only the transactions.
 */
final class MappingPrompt {

    static final int SAFE_PROMPT_TOKENS = 12_000;
    static final int EXAMPLE_LIMIT = 3;
    static final int TOKENS_PER_MAPPING_ROW = 40;
    static final int COMPLETION_TOKEN_MARGIN = 500;

    static final String MATCHING_RULES = """
            Matching rules:
            1. Choose the ledger whose name contains the payee's FULL name as it appears in the narration. A shared surname or first name alone is not enough.
            2. Ignore numbers and codes after a name. "Sky Enterprises 2" refers to the ledger "Sky Enterprises".
            3. Write the ledger name exactly as it appears in the catalog, character for character. Never shorten or reword it ("Private Limited", not "Pvt Ltd").
            4. If no ledger in the catalog contains the payee's name, answer UNMAPPED. Never choose a different person's ledger.
            5. If several ledgers contain the payee's name and the narration does not say which one applies, answer UNMAPPED.
            """;

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
        return completionTokenBudget(transactionCount, COMPLETION_TOKEN_MARGIN, TOKENS_PER_MAPPING_ROW);
    }

    static int completionTokenBudget(int transactionCount, int base, int perRow) {
        int rows = Math.max(transactionCount, 0);
        return rows * perRow + base;
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
            List<LedgerLine> catalog,
            List<TransactionLine> transactions) {
        StringBuilder system = new StringBuilder();
        system.append("Map each transaction to one ledger from the catalog.\n\n");
        system.append("Output format:\n");
        system.append("A JSON array of {\"transactionId\": <int>, \"ledgerName\": \"<exact catalog name or UNMAPPED>\"} with nothing before or after it.\n\n");
        system.append(MATCHING_RULES);
        if (!MATCHING_RULES.endsWith("\n")) {
            system.append('\n');
        }
        system.append('\n');
        system.append("LEDGER CATALOG\n");
        appendCatalog(system, catalog);
        system.append("TRANSACTION HINTS\n");
        if (hints == null || hints.isEmpty()) {
            system.append("(none)\n");
        } else {
            for (HintLine hint : hints) {
                system.append("- [").append(hint.voucherType()).append("] ").append(hint.text()).append('\n');
            }
        }
        StringBuilder user = new StringBuilder();
        user.append("TRANSACTIONS\n");
        if (transactions != null) {
            for (TransactionLine tx : transactions) {
                user.append("- id=").append(tx.id())
                        .append(" narration=\"").append(sanitize(tx.narration())).append('"')
                        .append(" amount=").append(tx.amount() == null ? "" : tx.amount())
                        .append(" type=").append(tx.type()).append('\n');
                List<ExampleLine> examples = tx.examples();
                if (examples == null || examples.isEmpty()) {
                    user.append("  examples: []\n");
                } else {
                    user.append("  examples:\n");
                    for (ExampleLine ex : examples) {
                        user.append("  - narration=\"").append(sanitize(ex.narration())).append('"')
                                .append(" amount=").append(ex.amount() == null ? "" : ex.amount())
                                .append(" ledger=").append(ex.ledger()).append('\n');
                    }
                }
            }
        }
        return "SYSTEM\n" + system + "USER\n" + user;
    }

    static String systemMessage(String prompt) {
        if (prompt == null) {
            return "";
        }
        String body = prompt.startsWith("SYSTEM\n") ? prompt.substring("SYSTEM\n".length()) : prompt;
        int user = body.indexOf("\nUSER\n");
        if (user >= 0) {
            body = body.substring(0, user);
        }
        return body.strip();
    }

    static String userMessage(String prompt) {
        if (prompt == null) {
            return "";
        }
        int user = prompt.indexOf("\nUSER\n");
        if (user >= 0) {
            return prompt.substring(user + "\nUSER\n".length()).strip();
        }
        int transactions = prompt.indexOf("TRANSACTIONS\n");
        return transactions < 0 ? "" : prompt.substring(transactions).strip();
    }

    static String staticPrefix(String prompt) {
        int marker = prompt.indexOf("TRANSACTIONS\n");
        return marker < 0 ? prompt : prompt.substring(0, marker);
    }

    static String catalogSection(String prompt) {
        if (prompt == null) {
            return "";
        }
        int start = prompt.indexOf("LEDGER CATALOG\n");
        if (start < 0) {
            return "";
        }
        int from = start + "LEDGER CATALOG\n".length();
        int end = catalogBodyEnd(prompt, from);
        if (end < 0) {
            return prompt.substring(from);
        }
        return prompt.substring(from, end);
    }

    static int catalogBodyEnd(String prompt, int from) {
        int best = -1;
        for (String marker : List.of("\nTRANSACTION HINTS\n", "\nTRANSACTIONS\n", "\nUSER\n")) {
            int index = prompt.indexOf(marker, from);
            if (index >= 0 && (best < 0 || index < best)) {
                best = index;
            }
        }
        return best;
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

    private static void appendCatalog(StringBuilder sb, List<LedgerLine> lines) {
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
