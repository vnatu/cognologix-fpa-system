/**
 * Bank Reconciliation (FinSync) — HDFC statement ingest, TallyPrime ledger mapping
 * with Spring AI / Ollama, and Excel export (ADR-065).
 * Public API is this root package ({@code BankReconService}).
 */
@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {"general", "people"}
)
package com.cognologix.fpa.bankrecon;
