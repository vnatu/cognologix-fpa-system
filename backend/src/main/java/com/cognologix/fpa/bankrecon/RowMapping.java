package com.cognologix.fpa.bankrecon;

import com.cognologix.fpa.bankrecon.domain.MappingSource;
import com.cognologix.fpa.bankrecon.domain.ReconTransaction;

/**
 * The only writer of {@code mapping_source}. A stage sets the source when it decides the ledger.
 */
final class RowMapping {

    static final String LLM_UNAVAILABLE = "LLM_UNAVAILABLE";
    static final String LLM_UNMAPPED = "LLM_UNMAPPED";
    static final String LLM_INVALID = "LLM_INVALID";

    private RowMapping() {}

    static void reset(ReconTransaction tx) {
        tx.setMappedLedger(null);
        tx.setMappingSource(MappingSource.NONE);
        tx.setMatchedCode(null);
        tx.setMappingReason(null);
        tx.setLlmNameResolution(null);
        tx.setReviewTag(null);
        tx.setNote(null);
        if (tx.getImportFlag() != null && tx.getImportFlag().startsWith("code conflict:")) {
            tx.setImportFlag(null);
        }
    }

    static void code(ReconTransaction tx, String ledgerName, String matchedCode, String note) {
        tx.setMappedLedger(ledgerName);
        tx.setMappingSource(MappingSource.CODE);
        tx.setMatchedCode(matchedCode);
        tx.setMappingReason(null);
        tx.setLlmNameResolution(null);
        tx.setReviewTag(null);
        tx.setNote(note);
    }

    static void learned(ReconTransaction tx, String ledgerName) {
        tx.setMappedLedger(ledgerName);
        tx.setMappingSource(MappingSource.LEARNED);
        tx.setMatchedCode(null);
        tx.setMappingReason(null);
    }

    static void llm(ReconTransaction tx, String ledgerName, String resolution) {
        tx.setMappedLedger(ledgerName);
        tx.setMappingSource(MappingSource.LLM);
        tx.setMatchedCode(null);
        tx.setMappingReason(null);
        tx.setLlmNameResolution(resolution);
        tx.setReviewTag(null);
    }

    static void none(ReconTransaction tx, String reason) {
        tx.setMappedLedger(null);
        tx.setMappingSource(MappingSource.NONE);
        tx.setMatchedCode(null);
        tx.setMappingReason(reason);
        tx.setLlmNameResolution(null);
    }

    static void manual(ReconTransaction tx, String ledgerName) {
        tx.setMappedLedger(ledgerName);
        tx.setMappingSource(MappingSource.MANUAL);
        tx.setMatchedCode(null);
        tx.setMappingReason(null);
        tx.setNote(null);
    }
}
