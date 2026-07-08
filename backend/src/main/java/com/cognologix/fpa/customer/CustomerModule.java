package com.cognologix.fpa.customer;

/**
 * Customer Management bounded context — Spring Modulith module root.
 *
 * Owns: customer master records, project/BU code definitions (authoritative
 * per ADR-010), billing rate card, DSO/commercial terms, concentration risk
 * thresholds, and relationship ownership.
 *
 * Other modules must not import from sub-packages of this package directly.
 * Cross-module access is via this module's exposed service API only (ADR-008).
 */
public final class CustomerModule {
    private CustomerModule() {}
}
