-- Revenue module (ADR-039, ADR-040) — Zoho Books invoices + credit notes

-- Revenue upload audit trail
CREATE TABLE revenue_upload (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    import_type VARCHAR(30) NOT NULL CHECK (import_type IN ('ZOHO_BOOKS_INVOICES','ZOHO_BOOKS_CREDIT_NOTES')),
    period_month INTEGER NOT NULL CHECK (period_month BETWEEN 1 AND 12),
    period_year INTEGER NOT NULL,
    version_number INTEGER NOT NULL DEFAULT 1,
    status VARCHAR(15) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','SUPERSEDED')),
    uploaded_by VARCHAR(255) NOT NULL,
    uploaded_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    original_filename VARCHAR(500) NOT NULL,
    row_count INTEGER NOT NULL,
    unmapped_columns TEXT,
    missing_columns TEXT,
    unrecognized_customer_codes TEXT,
    UNIQUE (import_type, period_month, period_year, version_number)
);

-- Invoice records
CREATE TABLE revenue_invoice (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    revenue_upload_id UUID NOT NULL REFERENCES revenue_upload(id),
    period_month INTEGER NOT NULL,
    period_year INTEGER NOT NULL,
    invoice_number VARCHAR(100) NOT NULL,
    customer_id VARCHAR(100) NOT NULL,         -- soft ref to customer (cross-module, no FK)
    invoice_date DATE,
    status VARCHAR(30),
    amount NUMERIC(14,2) NOT NULL,
    balance NUMERIC(14,2),
    due_date DATE,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD' CHECK (currency IN ('USD','INR')),
    project_code VARCHAR(100),
    amount_inr NUMERIC(14,2),                  -- converted amount (nullable — set if currency=USD)
    fx_rate_id UUID,                           -- ADR-017: store fx_rate_id used for conversion
    UNIQUE (revenue_upload_id, invoice_number)
);

CREATE INDEX idx_revenue_invoice_period ON revenue_invoice (period_year, period_month);
CREATE INDEX idx_revenue_invoice_customer ON revenue_invoice (customer_id);

-- Credit note records
CREATE TABLE revenue_credit_note (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    revenue_upload_id UUID NOT NULL REFERENCES revenue_upload(id),
    period_month INTEGER NOT NULL,
    period_year INTEGER NOT NULL,
    credit_note_number VARCHAR(100) NOT NULL,
    customer_id VARCHAR(100) NOT NULL,         -- soft ref to customer
    credit_note_date DATE,
    status VARCHAR(30),
    amount NUMERIC(14,2) NOT NULL,             -- stored as positive, treated as negative in calculations
    currency VARCHAR(3) NOT NULL DEFAULT 'USD' CHECK (currency IN ('USD','INR')),
    amount_inr NUMERIC(14,2),
    fx_rate_id UUID,
    UNIQUE (revenue_upload_id, credit_note_number)
);

CREATE INDEX idx_revenue_credit_note_period ON revenue_credit_note (period_year, period_month);
CREATE INDEX idx_revenue_credit_note_customer ON revenue_credit_note (customer_id);

-- Column mapping templates (extend existing import_column_mapping CHECK constraint)
ALTER TABLE import_column_mapping
    DROP CONSTRAINT import_column_mapping_import_type_check,
    ADD CONSTRAINT import_column_mapping_import_type_check
        CHECK (import_type IN (
            'ZOHO_PEOPLE','ZOHO_PAYROLL','ZOHO_PEOPLE_EXITED','ZOHO_PAYROLL_FNF',
            'ZOHO_BOOKS_INVOICES','ZOHO_BOOKS_CREDIT_NOTES'
        ));
