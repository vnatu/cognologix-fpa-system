package com.cognologix.fpa.people.domain;

public enum ImportType {
    ZOHO_PEOPLE,
    ZOHO_PAYROLL,
    ZOHO_PAYROLL_FNF,
    ZOHO_PEOPLE_EXITED,
    /** Revenue module — Zoho Books invoices (ADR-039, ADR-040). Stored in shared import_column_mapping. */
    ZOHO_BOOKS_INVOICES,
    /** Revenue module — Zoho Books credit notes (ADR-040). Stored in shared import_column_mapping. */
    ZOHO_BOOKS_CREDIT_NOTES
}
