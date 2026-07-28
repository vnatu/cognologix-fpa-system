package com.cognologix.fpa.general;

/**
 * General Configuration — Spring Modulith module root.
 *
 * Owns system-wide configuration that is not specific to any single bounded context:
 * FX rates, user accounts (ADR-042), and any future global reference data.
 *
 * Public API surface: GeneralConfigService, UserService, AdminOnly, FxRate, AppUser, BackupSheet
 * (types exposed for cross-module use).
 * Internal: repositories in the repository sub-package.
 *
 * Any module needing FX conversion must call GeneralConfigService — never import
 * the repository directly (ADR-008, ADR-017). Auth infrastructure loads users via
 * UserService (never AppUserRepository).
 */
public final class GeneralModule {
    private GeneralModule() {}
}
