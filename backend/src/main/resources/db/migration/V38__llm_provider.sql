-- ADR-065: Finance selects OLLAMA or OMLX. Default is OMLX.
-- nomic-ai/modernbert-embed-base (the source of mlx-community/nomicai-modernbert-embed-base-4bit)
-- has hidden_size 768, matching learned_mapping.narration_embedding vector(768).
-- Confirm on the live server before switching embeddings in production:
--   curl -s "$BASE/v1/embeddings" -H "Authorization: Bearer $KEY" -H "Content-Type: application/json" \
--     -d '{"model":"mlx-community/nomicai-modernbert-embed-base-4bit","input":"ping"}'
-- If the embedding array length is not 768, change the vector column before use.
INSERT INTO general_config (config_key, config_value) VALUES
    ('llm_provider', 'OMLX'),
    ('mlx_api_key', '')
ON CONFLICT (config_key) DO NOTHING;

UPDATE general_config
SET config_value = 'mlx-community/nomicai-modernbert-embed-base-4bit'
WHERE config_key = 'ollama_embedding_model'
  AND config_value = 'nomic-embed-text';

UPDATE general_config
SET config_value = 'http://localhost:9000'
WHERE config_key = 'ollama_embedding_url'
  AND config_value = 'http://localhost:11434';
