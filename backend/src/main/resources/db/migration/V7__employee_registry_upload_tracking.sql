-- ADR-024: link exited-employee registry updates to snapshot_upload for detail screen
ALTER TABLE employee_registry
    ADD COLUMN last_updated_by_upload_id UUID REFERENCES snapshot_upload(id);
