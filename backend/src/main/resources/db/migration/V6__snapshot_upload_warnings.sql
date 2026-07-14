-- Persist column-mapping / BU validation warnings on snapshot_upload so
-- GET /api/people/imports/{periodVersionId}/preview can resurface them after upload.
ALTER TABLE snapshot_upload
    ADD COLUMN unmapped_columns TEXT,
    ADD COLUMN missing_columns TEXT,
    ADD COLUMN unrecognized_bu_codes TEXT;
