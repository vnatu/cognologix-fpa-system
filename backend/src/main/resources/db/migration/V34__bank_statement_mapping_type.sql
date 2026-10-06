-- ADR-019 column mapping templates for HDFC bank statements (FinSync).
-- Next sequential Flyway version after V33 (requested name V35).
ALTER TABLE import_column_mapping
    DROP CONSTRAINT import_column_mapping_import_type_check,
    ADD CONSTRAINT import_column_mapping_import_type_check
        CHECK (import_type IN (
            'ZOHO_PEOPLE','ZOHO_PAYROLL','ZOHO_PEOPLE_EXITED','ZOHO_PAYROLL_FNF',
            'ZOHO_BOOKS_INVOICES','ZOHO_BOOKS_CREDIT_NOTES',
            'HDFC_BANK_STATEMENT'
        ));
