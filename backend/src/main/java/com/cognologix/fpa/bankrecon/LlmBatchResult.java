package com.cognologix.fpa.bankrecon;

import com.cognologix.fpa.bankrecon.dto.BankReconDtos.LedgerMappingResult;

import java.util.List;

/**
 * One chat completion. {@code truncated} is true when finish_reason is length
 * or the JSON array did not close, so the caller can re-send the missing rows.
 * {@code replied} is false when the call failed before a model reply.
 */
record LlmBatchResult(
        List<LedgerMappingResult> mappings,
        boolean truncated,
        String rawText,
        int returned,
        int parsed,
        boolean replied) {

    LlmBatchResult {
        mappings = mappings == null ? List.of() : List.copyOf(mappings);
        rawText = rawText == null ? "" : rawText;
    }

    LlmBatchResult(List<LedgerMappingResult> mappings, boolean truncated) {
        this(mappings, truncated, "",
                mappings == null ? 0 : mappings.size(),
                mappings == null ? 0 : mappings.size(),
                true);
    }

    static LlmBatchResult complete(List<LedgerMappingResult> mappings) {
        return new LlmBatchResult(mappings, false);
    }

    static LlmBatchResult failed() {
        return new LlmBatchResult(List.of(), false, "", 0, 0, false);
    }
}
