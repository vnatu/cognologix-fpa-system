package com.cognologix.fpa.general;

/**
 * General Configuration — Spring Modulith module root.
 *
 * Owns system-wide configuration that is not specific to any single bounded context:
 * FX rates, and any future global reference data (e.g. fiscal calendar, global toggles).
 *
 * Public API surface: GeneralConfigService, FxRate (entity exposed for cross-module use).
 * Internal: FxRateRepository (in the repository sub-package).
 *
 * Any module needing FX conversion must call GeneralConfigService — never import
 * the repository directly (ADR-008, ADR-017).
 */
public final class GeneralModule {
    private GeneralModule() {}
}
