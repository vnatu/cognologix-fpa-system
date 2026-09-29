-- Contract types (configurable)
CREATE TABLE contract_type (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type_code VARCHAR(20) NOT NULL UNIQUE,
    display_name VARCHAR(255) NOT NULL,
    description VARCHAR(500),
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO contract_type (type_code, display_name) VALUES
    ('NDA', 'Non-Disclosure Agreement'),
    ('MSA', 'Master Services Agreement'),
    ('SOW', 'Statement of Work');

-- Contracts
CREATE TABLE contract (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    contract_number VARCHAR(20) NOT NULL UNIQUE,
    title VARCHAR(500) NOT NULL,
    contract_type_id UUID NOT NULL REFERENCES contract_type(id),
    paper_type VARCHAR(15) NOT NULL CHECK (paper_type IN ('THIRD_PARTY','OWN')),
    customer_id VARCHAR(100),                    -- soft ref to FPA customer
    party_name VARCHAR(500),                     -- free text if customer not in FPA
    effective_date DATE,
    expiry_date DATE,
    is_evergreen BOOLEAN NOT NULL DEFAULT false,
    status VARCHAR(15) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('DRAFT','ACTIVE','EXPIRED','TERMINATED')),
    parent_contract_id UUID REFERENCES contract(id),
    contract_value NUMERIC(14,2),
    billing_currency VARCHAR(3),
    payment_terms VARCHAR(255),
    reminder_days_override INTEGER[],            -- overrides system defaults if set
    description TEXT,
    owner_user_id UUID NOT NULL REFERENCES app_user(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(255) NOT NULL,
    updated_at TIMESTAMPTZ,
    updated_by VARCHAR(255),
    CONSTRAINT contract_party_xor CHECK (
        (customer_id IS NOT NULL AND btrim(customer_id) <> '' AND party_name IS NULL)
        OR
        (party_name IS NOT NULL AND btrim(party_name) <> '' AND customer_id IS NULL)
    )
);

-- Contract versions
CREATE TABLE contract_version (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    contract_id UUID NOT NULL REFERENCES contract(id),
    version_number INTEGER NOT NULL,
    version_label VARCHAR(100) NOT NULL,
    status VARCHAR(15) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT','UNDER_REVIEW','SIGNED','SUPERSEDED')),
    notes TEXT,
    uploaded_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    uploaded_by VARCHAR(255) NOT NULL,
    UNIQUE (contract_id, version_number)
);

-- Contract documents (primary + supporting)
CREATE TABLE contract_document (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    contract_version_id UUID NOT NULL REFERENCES contract_version(id),
    document_type VARCHAR(10) NOT NULL CHECK (document_type IN ('PRIMARY','SUPPORTING')),
    filename VARCHAR(500) NOT NULL,
    content_type VARCHAR(255) NOT NULL,
    file_size_bytes BIGINT NOT NULL,
    file_data BYTEA NOT NULL,
    uploaded_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    uploaded_by VARCHAR(255) NOT NULL
);

-- Contract templates
CREATE TABLE contract_template (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    contract_type_id UUID NOT NULL REFERENCES contract_type(id),
    template_name VARCHAR(255) NOT NULL,
    description VARCHAR(500),
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(255) NOT NULL
);

CREATE TABLE contract_template_document (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    template_id UUID NOT NULL REFERENCES contract_template(id),
    version_number INTEGER NOT NULL DEFAULT 1,
    filename VARCHAR(500) NOT NULL,
    content_type VARCHAR(255) NOT NULL,
    file_size_bytes BIGINT NOT NULL,
    file_data BYTEA NOT NULL,
    uploaded_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    uploaded_by VARCHAR(255) NOT NULL,
    UNIQUE (template_id, version_number)
);

-- Notification log (deduplication)
CREATE TABLE contract_notification_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    contract_id UUID NOT NULL REFERENCES contract(id),
    notification_type VARCHAR(10) NOT NULL CHECK (notification_type IN ('EMAIL','IN_APP')),
    days_before_expiry INTEGER NOT NULL,
    sent_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    recipients TEXT
);

-- In-app notifications (contracts first; other modules can reuse)
CREATE TABLE app_notification (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES app_user(id),
    title VARCHAR(255) NOT NULL,
    message TEXT NOT NULL,
    link VARCHAR(500),           -- deep link e.g. /contracts/{id}
    is_read BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_app_notification_user_unread
    ON app_notification (user_id, created_at DESC)
    WHERE is_read = false;

-- Notification config (system defaults).
-- config_value widened so a recipient list can exceed the original VARCHAR(255).
ALTER TABLE general_config ALTER COLUMN config_value TYPE TEXT;

INSERT INTO general_config (config_key, config_value) VALUES
    ('contract_reminder_days', '90,60,30,7'),
    ('contract_notification_recipients', '')
ON CONFLICT (config_key) DO NOTHING;

-- Auto-generate contract numbers sequence
CREATE SEQUENCE contract_number_seq START 1;
