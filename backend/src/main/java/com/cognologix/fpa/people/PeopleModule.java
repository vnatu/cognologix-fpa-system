package com.cognologix.fpa.people;

/**
 * People & Payroll bounded context — Spring Modulith module root.
 *
 * Owns: employee master data, payroll snapshots, reconciliation,
 * headcount reporting, and all configuration specific to this context
 * (delivery PU list, management BU mapping — Module 1 spec §8.1).
 *
 * Other modules must not import from sub-packages of this package directly.
 * Cross-module access is via this module's exposed service API only (ADR-008).
 */
public final class PeopleModule {
    private PeopleModule() {}
}
