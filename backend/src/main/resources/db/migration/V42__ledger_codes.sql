ALTER TABLE tally_ledger
    ADD COLUMN ledger_code VARCHAR(10),
    ADD COLUMN added_in_app BOOLEAN NOT NULL DEFAULT false;

ALTER TABLE tally_ledger
    ADD CONSTRAINT tally_ledger_ledger_code_format
        CHECK (ledger_code IS NULL OR ledger_code ~ '^[A-Z0-9]{2,10}$');

CREATE UNIQUE INDEX tally_ledger_ledger_code_uidx
    ON tally_ledger (ledger_code)
    WHERE ledger_code IS NOT NULL;

ALTER TABLE recon_transaction
    DROP CONSTRAINT recon_transaction_mapping_source_check;

ALTER TABLE recon_transaction
    ADD CONSTRAINT recon_transaction_mapping_source_check
        CHECK (mapping_source IN ('LEARNED', 'LLM', 'MANUAL', 'CODE'));

ALTER TABLE recon_transaction
    ADD COLUMN note TEXT;
