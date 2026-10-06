-- Statement header-block fields mapped for HDFC_BANK_STATEMENT (ADR-066).
ALTER TABLE recon_run
    ADD COLUMN IF NOT EXISTS customer_name VARCHAR(500),
    ADD COLUMN IF NOT EXISTS opening_balance NUMERIC(14,2),
    ADD COLUMN IF NOT EXISTS closing_balance NUMERIC(14,2);
