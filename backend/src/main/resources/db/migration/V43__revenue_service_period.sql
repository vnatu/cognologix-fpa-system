-- Effective service period on revenue invoices and credit notes.
-- Existing rows keep their period (the upload tag) and are marked TAG.

ALTER TABLE revenue_invoice
    ADD COLUMN service_month INTEGER,
    ADD COLUMN service_year INTEGER,
    ADD COLUMN month_source VARCHAR(10) NOT NULL DEFAULT 'TAG';

ALTER TABLE revenue_invoice
    ADD CONSTRAINT revenue_invoice_service_month_check
        CHECK (service_month IS NULL OR service_month BETWEEN 1 AND 12),
    ADD CONSTRAINT revenue_invoice_month_source_check
        CHECK (month_source IN ('SERVICE', 'TAG'));

ALTER TABLE revenue_credit_note
    ADD COLUMN service_month INTEGER,
    ADD COLUMN service_year INTEGER,
    ADD COLUMN month_source VARCHAR(10) NOT NULL DEFAULT 'TAG';

ALTER TABLE revenue_credit_note
    ADD CONSTRAINT revenue_credit_note_service_month_check
        CHECK (service_month IS NULL OR service_month BETWEEN 1 AND 12),
    ADD CONSTRAINT revenue_credit_note_month_source_check
        CHECK (month_source IN ('SERVICE', 'TAG'));
