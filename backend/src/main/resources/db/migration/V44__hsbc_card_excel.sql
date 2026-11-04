-- ADR-075. HSBC corporate card Excel export. Bank references survive run close and
-- are removed when the run is discarded.

ALTER TABLE recon_run
    ADD COLUMN statement_source VARCHAR(20) NOT NULL DEFAULT 'HDFC_BANK';

ALTER TABLE recon_run
    ADD CONSTRAINT recon_run_statement_source_check
        CHECK (statement_source IN ('HDFC_BANK', 'HSBC_CARD_XLSX'));

ALTER TABLE recon_run ADD COLUMN warnings TEXT;
ALTER TABLE recon_run ADD COLUMN import_summary TEXT;
ALTER TABLE recon_run ADD COLUMN statement_date DATE;

ALTER TABLE recon_transaction ADD COLUMN card_last4 VARCHAR(4);
ALTER TABLE recon_transaction ADD COLUMN statement_ledger VARCHAR(500);
ALTER TABLE recon_transaction ADD COLUMN name_on_card VARCHAR(500);
ALTER TABLE recon_transaction ADD COLUMN merchant_name VARCHAR(500);
ALTER TABLE recon_transaction ADD COLUMN merchant_category VARCHAR(255);
ALTER TABLE recon_transaction ADD COLUMN transaction_type VARCHAR(100);
ALTER TABLE recon_transaction ADD COLUMN tax_amount NUMERIC(14,2);
ALTER TABLE recon_transaction ADD COLUMN cost_center VARCHAR(100);
ALTER TABLE recon_transaction ADD COLUMN employee_id VARCHAR(100);
ALTER TABLE recon_transaction ADD COLUMN import_flag VARCHAR(32);
ALTER TABLE recon_transaction ADD COLUMN card_payment BOOLEAN NOT NULL DEFAULT false;

CREATE TABLE recon_card_reference (
    bank_reference VARCHAR(255) PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES recon_run(id) ON DELETE CASCADE
);

INSERT INTO general_config (config_key, config_value) VALUES
    ('hsbc_card_debit_types', 'Purchase'),
    ('hsbc_card_payment_pattern', 'CREDIT CARD PAYMENT')
ON CONFLICT (config_key) DO NOTHING;
