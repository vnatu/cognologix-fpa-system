-- ADR-077. mapping_source NONE, the code that matched, and why the LLM left a row unmapped.

ALTER TABLE recon_transaction
    DROP CONSTRAINT recon_transaction_mapping_source_check;

ALTER TABLE recon_transaction
    ADD CONSTRAINT recon_transaction_mapping_source_check
        CHECK (mapping_source IN ('LEARNED', 'LLM', 'MANUAL', 'CODE', 'NONE'));

ALTER TABLE recon_transaction ADD COLUMN matched_code VARCHAR(10);
ALTER TABLE recon_transaction ADD COLUMN mapping_reason VARCHAR(32);

ALTER TABLE recon_transaction
    ALTER COLUMN import_flag TYPE VARCHAR(255);

INSERT INTO general_config (config_key, config_value) VALUES
    ('bank_recon_code_min_length', '2'),
    ('bank_recon_code_ignore', '')
ON CONFLICT (config_key) DO NOTHING;
