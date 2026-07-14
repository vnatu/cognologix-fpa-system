-- F&F (Full & Final) payroll sub-type — extends ADR-020 upload pattern
ALTER TABLE snapshot_upload
    DROP CONSTRAINT snapshot_upload_import_type_check,
    ADD CONSTRAINT snapshot_upload_import_type_check
        CHECK (import_type IN ('ZOHO_PEOPLE','ZOHO_PAYROLL','ZOHO_PEOPLE_EXITED','ZOHO_PAYROLL_FNF'));

ALTER TABLE import_column_mapping
    DROP CONSTRAINT import_column_mapping_import_type_check,
    ADD CONSTRAINT import_column_mapping_import_type_check
        CHECK (import_type IN ('ZOHO_PEOPLE','ZOHO_PAYROLL','ZOHO_PEOPLE_EXITED','ZOHO_PAYROLL_FNF'));

ALTER TABLE payroll_snapshot
    ADD COLUMN import_type VARCHAR(30) NOT NULL DEFAULT 'ZOHO_PAYROLL'
        CHECK (import_type IN ('ZOHO_PAYROLL','ZOHO_PAYROLL_FNF'));
