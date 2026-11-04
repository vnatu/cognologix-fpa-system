package com.cognologix.fpa.bankrecon;

import com.cognologix.fpa.bankrecon.domain.TallyLedger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Whole-token, case-insensitive ledger-code match on the raw narration.
 * Zero or several distinct ledgers do not decide the row.
 */
final class LedgerCodeMatch {

    enum Kind { MATCH, NONE, CONFLICT }

    record Outcome(Kind kind, TallyLedger ledger, String matchedCode, String note, String conflictCodes) {

        static Outcome none() {
            return new Outcome(Kind.NONE, null, null, null, null);
        }
    }

    private LedgerCodeMatch() {}

    static Outcome match(String description, List<TallyLedger> ledgers, int minLength, Set<String> ignore) {
        int floor = minLength < 1 ? 2 : minLength;
        Set<String> skipped = ignore == null ? Set.of() : ignore;
        Map<String, List<TallyLedger>> byCode = new LinkedHashMap<>();
        if (ledgers != null) {
            for (TallyLedger ledger : ledgers) {
                String code = fold(ledger == null ? null : ledger.getLedgerCode());
                if (code.length() < floor || skipped.contains(code)) {
                    continue;
                }
                byCode.computeIfAbsent(code, key -> new ArrayList<>()).add(ledger);
            }
        }
        Map<String, TallyLedger> matched = new LinkedHashMap<>();
        LedgerCodeScan.Word first = null;
        TreeSet<String> conflictCodes = new TreeSet<>();
        for (LedgerCodeScan.Word word : LedgerCodeScan.words(description)) {
            String token = fold(word.text());
            if (token.length() < floor || skipped.contains(token)) {
                continue;
            }
            List<TallyLedger> hits = byCode.get(token);
            if (hits == null || hits.isEmpty()) {
                continue;
            }
            if (first == null) {
                first = word;
            }
            for (TallyLedger hit : hits) {
                matched.putIfAbsent(hit.getId() == null ? hit.getLedgerName() : hit.getId().toString(), hit);
                conflictCodes.add(fold(hit.getLedgerCode()));
            }
        }
        if (matched.size() == 1 && first != null) {
            TallyLedger ledger = matched.values().iterator().next();
            String code = fold(ledger.getLedgerCode());
            return new Outcome(
                    Kind.MATCH,
                    ledger,
                    code,
                    code + " at position " + first.position(),
                    null);
        }
        if (matched.size() > 1) {
            return new Outcome(Kind.CONFLICT, null, null, null, String.join(", ", conflictCodes));
        }
        return Outcome.none();
    }

    private static String fold(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
