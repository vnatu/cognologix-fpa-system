-- ADR-060: employee_status on people_snapshot and master_record so master build
-- can match active + exited Zoho People rows against payroll (incl. F&F).

ALTER TABLE people_snapshot
    ADD COLUMN employee_status VARCHAR(10) NOT NULL DEFAULT 'ACTIVE'
    CHECK (employee_status IN ('ACTIVE', 'EXITED'));

ALTER TABLE master_record
    ADD COLUMN employee_status VARCHAR(10) NOT NULL DEFAULT 'ACTIVE'
    CHECK (employee_status IN ('ACTIVE', 'EXITED'));

UPDATE people_snapshot ps
SET employee_status = 'EXITED'
WHERE EXISTS (
    SELECT 1 FROM snapshot_upload su
    WHERE su.id = ps.snapshot_upload_id
      AND su.import_type = 'ZOHO_PEOPLE_EXITED'
);
