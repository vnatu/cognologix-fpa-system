package com.cognologix.fpa.bankrecon;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Per-run correlation for chat, follow-up, and embedding calls on this thread.
 * A call id looks like {@code R-005#2}.
 */
final class LlmCallContext {

    private static final ThreadLocal<Scope> CURRENT = new ThreadLocal<>();
    private static final AtomicInteger STANDALONE = new AtomicInteger();

    private LlmCallContext() {}

    static boolean openIfAbsent(String runNumber) {
        if (CURRENT.get() != null) {
            return false;
        }
        String number = runNumber == null || runNumber.isBlank() ? "unknown" : runNumber.trim();
        CURRENT.set(new Scope(number));
        return true;
    }

    static void close() {
        CURRENT.remove();
    }

    static String nextCallId() {
        Scope scope = CURRENT.get();
        if (scope == null) {
            return "no-run#" + STANDALONE.incrementAndGet();
        }
        String callId = scope.runNumber + "#" + scope.sequence.incrementAndGet();
        scope.activeCallId = callId;
        return callId;
    }

    static String activeCallId() {
        Scope scope = CURRENT.get();
        if (scope == null) {
            return "no-run";
        }
        return scope.activeCallId == null ? scope.runNumber : scope.activeCallId;
    }

    static LlmTrace.CatalogMemory catalogMemory() {
        Scope scope = CURRENT.get();
        if (scope == null) {
            return new LlmTrace.CatalogMemory();
        }
        return scope.catalog;
    }

    private static final class Scope {
        private final String runNumber;
        private final AtomicInteger sequence = new AtomicInteger();
        private final LlmTrace.CatalogMemory catalog = new LlmTrace.CatalogMemory();
        private String activeCallId;

        private Scope(String runNumber) {
            this.runNumber = runNumber;
        }
    }
}
