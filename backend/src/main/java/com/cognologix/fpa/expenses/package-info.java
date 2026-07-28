/**
 * Expenses module — monthly overhead / actual spend capture (ADR-050).
 * Public API is this root package ({@code ExpenseService}); domain and
 * repositories are internal. OPEN so Budgeting can consume summary maps
 * without a separate named-interface package (same pattern as Revenue ADR-043).
 */
@org.springframework.modulith.ApplicationModule(
        type = org.springframework.modulith.ApplicationModule.Type.OPEN,
        allowedDependencies = {"general"}
)
package com.cognologix.fpa.expenses;
