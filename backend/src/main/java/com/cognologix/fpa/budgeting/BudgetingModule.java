package com.cognologix.fpa.budgeting;

/**
 * Budgeting &amp; Forecasting bounded context — Spring Modulith module root.
 *
 * Owns: financial year plans, forecast types/versions, HC / revenue / salary /
 * overhead plan inputs, period actuals snapshots, and Plan vs Actual analysis
 * (ADR-037, ADR-038).
 *
 * Other modules must not import from sub-packages of this package directly.
 * Cross-module access is via this module's exposed service API only (ADR-008).
 */
public final class BudgetingModule {
    private BudgetingModule() {}
}
