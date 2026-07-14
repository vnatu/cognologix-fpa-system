ALTER TABLE period_version
    DROP CONSTRAINT period_version_status_check,
    ADD CONSTRAINT period_version_status_check
        CHECK (status IN ('OPEN','SNAPSHOTS_UPLOADED','MASTER_BUILT','FINALISED','SUPERSEDED'));
