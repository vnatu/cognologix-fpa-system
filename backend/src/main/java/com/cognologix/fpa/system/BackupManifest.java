package com.cognologix.fpa.system;

import java.util.List;

/** Canonical ZIP entry order for full backup/restore (ADR-044 Tier 2). */
public final class BackupManifest {

    private BackupManifest() {}

    public static final List<String> EXPECTED_FILES = List.of(
            "users.xlsx",
            "general_config.xlsx",
            "fx_rates.xlsx",
            "overhead_line_items.xlsx",
            "customers.xlsx",
            "rate_cards.xlsx",
            "project_codes.xlsx",
            "classification_config.xlsx",
            "column_mapping_templates.xlsx",
            "financial_year_plans.xlsx",
            "forecast_types.xlsx",
            "forecast_versions.xlsx",
            "hc_plan.xlsx",
            "salary_budget.xlsx",
            "client_revenue_plan.xlsx",
            "overhead_budget.xlsx",
            "periods.xlsx",
            "period_versions.xlsx",
            "employee_registry.xlsx",
            "alternate_id_links.xlsx",
            "zoho_people_snapshots.xlsx",
            "zoho_payroll_snapshots.xlsx",
            "master_records.xlsx",
            "period_actuals.xlsx",
            "overhead_actuals.xlsx",
            "revenue_invoices.xlsx",
            "revenue_credit_notes.xlsx"
    );

    public static final String TEMP_RESTORE_PASSWORD = "RestoreMe123!";
}
