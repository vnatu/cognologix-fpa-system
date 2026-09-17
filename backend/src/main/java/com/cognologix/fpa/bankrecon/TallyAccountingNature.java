package com.cognologix.fpa.bankrecon;

import java.util.Locale;
import java.util.Optional;

/**
 * Maps unknown TallyPrime group names to {@code Asset}/{@code Liability}/{@code Income}/{@code Expense}
 * when they are not among the 28 seeded standard groups.
 */
final class TallyAccountingNature {

    static final String LIABILITY = "Liability";
    static final String ASSET = "Asset";
    static final String INCOME = "Income";
    static final String EXPENSE = "Expense";

    private TallyAccountingNature() {}

    static Optional<String> fromPattern(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.contains("payable")) {
            return Optional.of(LIABILITY);
        }
        if (lower.contains("receivable") || lower.contains("debtor")) {
            return Optional.of(ASSET);
        }
        if (lower.contains("income") || lower.contains("revenue")) {
            return Optional.of(INCOME);
        }
        if (lower.contains("expense") || lower.contains("cost")) {
            return Optional.of(EXPENSE);
        }
        return Optional.empty();
    }

    /** Fallback when a ledger/group parent is not in {@code tally_ledger_group}. */
    static String fromUnknownParent(String parentGroup) {
        return fromPattern(parentGroup).orElse(LIABILITY);
    }
}
