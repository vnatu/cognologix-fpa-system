package com.cognologix.fpa.revenue;

/**
 * Revenue bounded context — Spring Modulith module root (ADR-039, ADR-040).
 *
 * Owns: Zoho Books invoice/credit-note import, invoice-level storage,
 * monthly net-revenue summaries, and the Revenue Dashboard.
 *
 * Other modules must not import from sub-packages of this package directly.
 * Cross-module access is via this module's exposed service API only (ADR-008).
 */
public final class RevenueModule {
    private RevenueModule() {}
}
