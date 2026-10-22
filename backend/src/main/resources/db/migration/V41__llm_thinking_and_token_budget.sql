-- LLM mapping: thinking off by default, token ceilings, and one batch-size key.
-- ollama_batch_size is no longer read.

INSERT INTO general_config (config_key, config_value) VALUES
    ('llm_thinking_enabled', 'false'),
    ('llm_max_tokens_base', '500'),
    ('llm_max_tokens_per_row', '40'),
    ('llm_max_tokens_base_thinking', '1000'),
    ('llm_max_tokens_per_row_thinking', '400'),
    ('llm_batch_size', '20')
ON CONFLICT (config_key) DO NOTHING;

ALTER TABLE recon_run ADD COLUMN processing_error TEXT;

ALTER TABLE recon_transaction ADD COLUMN llm_name_resolution VARCHAR(16);
ALTER TABLE recon_transaction ADD COLUMN review_tag VARCHAR(32);
