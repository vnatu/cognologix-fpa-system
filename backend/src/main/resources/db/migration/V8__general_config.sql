-- ADR-025: system-wide configuration (date format, etc.)
CREATE TABLE general_config (
    config_key   VARCHAR(100) PRIMARY KEY,
    config_value VARCHAR(255) NOT NULL
);

INSERT INTO general_config (config_key, config_value)
VALUES ('date_format', 'DD MMM YYYY');
