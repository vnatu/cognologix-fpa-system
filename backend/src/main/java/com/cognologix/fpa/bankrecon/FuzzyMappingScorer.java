package com.cognologix.fpa.bankrecon;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

final class FuzzyMappingScorer {

    private FuzzyMappingScorer() {}

    static double score(String left, String right) {
        if (left == null || right == null || left.isBlank() || right.isBlank()) {
            return 0;
        }
        String a = left.toUpperCase(Locale.ROOT).trim();
        String b = right.toUpperCase(Locale.ROOT).trim();
        double lev = normalisedLevenshtein(a, b);
        double overlap = tokenOverlap(a, b);
        return (lev + overlap) / 2.0;
    }

    static double normalisedLevenshtein(String a, String b) {
        int max = Math.max(a.length(), b.length());
        if (max == 0) {
            return 1;
        }
        return 1.0 - (double) levenshtein(a, b) / max;
    }

    static double tokenOverlap(String a, String b) {
        Set<String> left = tokens(a);
        Set<String> right = tokens(b);
        if (left.isEmpty() || right.isEmpty()) {
            return 0;
        }
        Set<String> intersection = new HashSet<>(left);
        intersection.retainAll(right);
        Set<String> union = new HashSet<>(left);
        union.addAll(right);
        return (double) intersection.size() / union.size();
    }

    private static Set<String> tokens(String text) {
        Set<String> out = new HashSet<>();
        for (String part : text.split("\\s+")) {
            if (part.length() >= 2) {
                out.add(part);
            }
        }
        return out;
    }

    private static int levenshtein(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] curr = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            curr[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }
        return prev[b.length()];
    }
}
