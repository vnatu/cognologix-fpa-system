-- Allow the same employee in both regular and F&F payroll uploads per period version (ADR-026)
ALTER TABLE payroll_snapshot
    DROP CONSTRAINT payroll_snapshot_period_version_id_employee_no_key;

ALTER TABLE payroll_snapshot
    ADD CONSTRAINT payroll_snapshot_period_version_employee_import_type_key
        UNIQUE (period_version_id, employee_no, import_type);
