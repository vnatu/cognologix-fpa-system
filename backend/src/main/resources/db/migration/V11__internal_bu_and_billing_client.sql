-- Internal BU model (Phase 1) — ADR-029
-- Note: numbered V11 because V10 is payroll_snapshot unique constraint.

ALTER TABLE customer
    ADD COLUMN is_internal BOOLEAN NOT NULL DEFAULT false;

INSERT INTO customer (id, customer_code, customer_name, lifecycle_status, is_internal)
VALUES
    (gen_random_uuid(), 'MGMT', 'Management', 'ACTIVE', true),
    (gen_random_uuid(), 'LDSP', 'Leadership', 'ACTIVE', true),
    (gen_random_uuid(), 'POOL', 'Pool', 'ACTIVE', true),
    (gen_random_uuid(), 'LNDT', 'Learning & Development', 'ACTIVE', true),
    (gen_random_uuid(), 'BEFN', 'Business Enabler Function', 'ACTIVE', true);

INSERT INTO commercial_terms (customer_id, dso_days)
SELECT id, 0 FROM customer WHERE is_internal = true;

ALTER TABLE master_record
    ADD COLUMN billing_customer_code VARCHAR(100);
