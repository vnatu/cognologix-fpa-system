package com.cognologix.fpa.bankrecon;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Turns a model ledger name into one catalog entry.
 * Exact match wins. Otherwise one normalised match. Otherwise one close candidate.
 * Several matches stay unresolved so the row can be tagged AMBIGUOUS.
 */
final class LedgerNameResolver {

    enum Kind {
        EXACT,
        RESOLVED,
        AMBIGUOUS,
        REJECTED
    }

    record Resolution(Kind kind, String catalogName) {
        static Resolution exact(String name) {
            return new Resolution(Kind.EXACT, name);
        }

        static Resolution resolved(String name) {
            return new Resolution(Kind.RESOLVED, name);
        }

        static Resolution ambiguous() {
            return new Resolution(Kind.AMBIGUOUS, null);
        }

        static Resolution rejected() {
            return new Resolution(Kind.REJECTED, null);
        }
    }

    private LedgerNameResolver() {}

    static Resolution resolve(String returned, Collection<String> catalog) {
        if (returned == null || returned.isBlank() || catalog == null || catalog.isEmpty()) {
            return Resolution.rejected();
        }
        for (String name : catalog) {
            if (name != null && name.equals(returned)) {
                return Resolution.exact(name);
            }
        }
        String norm = normalize(returned);
        if (norm.isEmpty()) {
            return Resolution.rejected();
        }
        List<String> normalisedHits = new ArrayList<>();
        for (String name : catalog) {
            if (name != null && norm.equals(normalize(name))) {
                normalisedHits.add(name);
            }
        }
        if (normalisedHits.size() == 1) {
            return Resolution.resolved(normalisedHits.getFirst());
        }
        if (normalisedHits.size() > 1) {
            return Resolution.ambiguous();
        }
        List<String> closest = new ArrayList<>();
        int best = Integer.MAX_VALUE;
        for (String name : catalog) {
            if (name == null) {
                continue;
            }
            int distance = distanceAtMost(norm, normalize(name), 2);
            if (distance < 0 || distance > 2) {
                continue;
            }
            if (distance < best) {
                best = distance;
                closest.clear();
                closest.add(name);
            } else if (distance == best) {
                closest.add(name);
            }
        }
        if (closest.size() == 1) {
            return Resolution.resolved(closest.getFirst());
        }
        if (closest.size() > 1) {
            return Resolution.ambiguous();
        }
        return Resolution.rejected();
    }

    /**
     * Lower case, punctuation to spaces, collapsed spaces, and Ltd/Limited plus Pvt/Private
     * folded to the short form.
     */
    static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String spaced = value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim()
                .replaceAll(" +", " ");
        if (spaced.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String word : spaced.split(" ")) {
            if (word.isEmpty()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(switch (word) {
                case "limited" -> "ltd";
                case "private" -> "pvt";
                default -> word;
            });
        }
        return sb.toString();
    }

    /** Returns the edit distance, or -1 when it already exceeds {@code max}. */
    private static int distanceAtMost(String left, String right, int max) {
        if (left.equals(right)) {
            return 0;
        }
        if (Math.abs(left.length() - right.length()) > max) {
            return -1;
        }
        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];
        for (int j = 0; j <= right.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= left.length(); i++) {
            current[0] = i;
            int rowBest = current[0];
            for (int j = 1; j <= right.length(); j++) {
                int cost = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(
                        Math.min(current[j - 1] + 1, previous[j] + 1),
                        previous[j - 1] + cost);
                rowBest = Math.min(rowBest, current[j]);
            }
            if (rowBest > max) {
                return -1;
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[right.length()];
    }
}
