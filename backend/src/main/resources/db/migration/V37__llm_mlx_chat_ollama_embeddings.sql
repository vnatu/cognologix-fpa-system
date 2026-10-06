-- ADR-065: chat defaults move to an OpenAI-compatible MLX server.
-- Embeddings stay on Ollama and get their own URL.
INSERT INTO general_config (config_key, config_value)
VALUES ('ollama_embedding_url', 'http://localhost:11434')
ON CONFLICT (config_key) DO NOTHING;

UPDATE general_config
SET config_value = 'http://localhost:9000'
WHERE config_key = 'ollama_base_url'
  AND config_value = 'http://localhost:11434';

UPDATE general_config
SET config_value = 'mlx-community/Qwen3.6-35B-A3B-4bit'
WHERE config_key = 'ollama_chat_model'
  AND config_value = 'qwen2.5:32b';
