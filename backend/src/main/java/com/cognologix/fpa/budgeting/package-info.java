/**
 * Budgeting &amp; Forecasting — OPEN so Reports (ADR-053) and application composition can
 * consume analysis DTOs ({@code budgeting.dto}) without a separate named-interface package
 * (same pattern as Revenue ADR-043 / Expenses ADR-050).
 */
@org.springframework.modulith.ApplicationModule(
        type = org.springframework.modulith.ApplicationModule.Type.OPEN,
        allowedDependencies = {"customer", "people", "revenue", "expenses", "general"}
)
package com.cognologix.fpa.budgeting;
