-- ADR-065: keep Ollama and oMLX settings side by side. llm_provider picks which set is used.
INSERT INTO general_config (config_key, config_value)
SELECT 'mlx_base_url', config_value
FROM general_config
WHERE config_key = 'ollama_base_url'
ON CONFLICT (config_key) DO NOTHING;

INSERT INTO general_config (config_key, config_value)
SELECT 'mlx_chat_model', config_value
FROM general_config
WHERE config_key = 'ollama_chat_model'
ON CONFLICT (config_key) DO NOTHING;

INSERT INTO general_config (config_key, config_value)
SELECT 'mlx_embedding_model', config_value
FROM general_config
WHERE config_key = 'ollama_embedding_model'
ON CONFLICT (config_key) DO NOTHING;

UPDATE general_config
SET config_value = 'http://localhost:11434'
WHERE config_key = 'ollama_base_url'
  AND config_value = 'http://localhost:9000';

UPDATE general_config
SET config_value = 'qwen2.5:32b'
WHERE config_key = 'ollama_chat_model'
  AND config_value = 'mlx-community/Qwen3.6-35B-A3B-4bit';

UPDATE general_config
SET config_value = 'http://localhost:11434'
WHERE config_key = 'ollama_embedding_url'
  AND config_value = 'http://localhost:9000';

UPDATE general_config
SET config_value = 'nomic-embed-text'
WHERE config_key = 'ollama_embedding_model'
  AND config_value = 'mlx-community/nomicai-modernbert-embed-base-4bit';
