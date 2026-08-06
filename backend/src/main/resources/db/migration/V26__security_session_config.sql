-- Session security defaults (ADR-056). Configurable via Settings → Security.
INSERT INTO general_config (config_key, config_value) VALUES
    ('jwt_expiry_hours', '2'),
    ('inactivity_timeout_minutes', '30')
ON CONFLICT (config_key) DO NOTHING;
