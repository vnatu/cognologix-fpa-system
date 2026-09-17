-- ADR-065 Bank Reconciliation (FinSync). Requires pgvector.
CREATE EXTENSION IF NOT EXISTS vector;

-- Permanent tables
CREATE TABLE recon_run (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    run_number VARCHAR(20) NOT NULL UNIQUE,
    statement_number VARCHAR(100),
    account_number VARCHAR(50),
    statement_period_start DATE,
    statement_period_end DATE,
    original_filename VARCHAR(500),
    total_transactions INTEGER NOT NULL DEFAULT 0,
    mapped_count INTEGER NOT NULL DEFAULT 0,
    unmapped_count INTEGER NOT NULL DEFAULT 0,
    excluded_count INTEGER NOT NULL DEFAULT 0,
    export_count INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(10) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','CLOSED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(255)
);

CREATE TABLE recon_export (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    run_id UUID NOT NULL REFERENCES recon_run(id),
    export_number INTEGER NOT NULL,
    filename VARCHAR(500) NOT NULL,
    transaction_count INTEGER NOT NULL,
    generated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    generated_by VARCHAR(255)
);

CREATE TABLE tally_ledger_group (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_name VARCHAR(255) NOT NULL UNIQUE,
    accounting_nature VARCHAR(20) NOT NULL CHECK (accounting_nature IN ('Asset','Liability','Income','Expense'))
);

CREATE TABLE tally_ledger (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ledger_name VARCHAR(500) NOT NULL UNIQUE,
    group_name VARCHAR(255) NOT NULL,
    accounting_nature VARCHAR(20) NOT NULL,
    is_bank_account BOOLEAN NOT NULL DEFAULT false,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE learned_mapping (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    normalised_narration TEXT NOT NULL,
    voucher_type VARCHAR(10) NOT NULL CHECK (voucher_type IN ('PAYMENT','RECEIPT','CONTRA')),
    ledger_name VARCHAR(500) NOT NULL,
    use_count INTEGER NOT NULL DEFAULT 1,
    last_used_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(255),
    narration_embedding vector(768),
    UNIQUE (normalised_narration, voucher_type)
);

CREATE INDEX learned_mapping_embedding_idx ON learned_mapping
    USING ivfflat (narration_embedding vector_cosine_ops) WITH (lists = 100);

CREATE TABLE llm_hint (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hint_text TEXT NOT NULL,
    voucher_type VARCHAR(10) NOT NULL DEFAULT 'ALL' CHECK (voucher_type IN ('ALL','PAYMENT','RECEIPT','CONTRA')),
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(255)
);

CREATE TABLE contra_rule (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    pattern_type VARCHAR(10) NOT NULL CHECK (pattern_type IN ('PREFIX','CONTAINS')),
    pattern_value VARCHAR(255) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE recon_audit_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_type VARCHAR(50) NOT NULL,
    entity_id UUID,
    action VARCHAR(20) NOT NULL,
    old_value TEXT,
    new_value TEXT,
    changed_by VARCHAR(255),
    changed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Temporary table (rows purged on run close, table persists)
CREATE TABLE recon_transaction (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    run_id UUID NOT NULL REFERENCES recon_run(id),
    transaction_date TIMESTAMPTZ,
    description TEXT NOT NULL,
    normalised_description TEXT NOT NULL,
    amount NUMERIC(14,2) NOT NULL,
    debit_credit VARCHAR(1) NOT NULL CHECK (debit_credit IN ('D','C')),
    reference_no VARCHAR(255),
    value_date DATE,
    transaction_branch VARCHAR(100),
    running_balance NUMERIC(14,2),
    voucher_type VARCHAR(10) NOT NULL CHECK (voucher_type IN ('PAYMENT','RECEIPT','CONTRA')),
    mapped_ledger VARCHAR(500),
    mapping_source VARCHAR(10) CHECK (mapping_source IN ('LEARNED','LLM','MANUAL')),
    is_excluded BOOLEAN NOT NULL DEFAULT false,
    is_reviewed BOOLEAN NOT NULL DEFAULT false,
    sort_order INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX recon_transaction_run_idx ON recon_transaction (run_id, sort_order);

-- Default contra rules
INSERT INTO contra_rule (pattern_type, pattern_value) VALUES
    ('PREFIX', 'NEFT'),
    ('PREFIX', 'RTGS');

-- Seed 28 TallyPrime ledger groups.
-- Spec brief listed Stock-in-Hand twice (UNIQUE group_name). Second occurrence
-- replaced with Current Assets, the missing standard Tally group.
INSERT INTO tally_ledger_group (group_name, accounting_nature) VALUES
    ('Bank Accounts', 'Asset'),
    ('Cash-in-Hand', 'Asset'),
    ('Deposits (Asset)', 'Asset'),
    ('Loans & Advances (Asset)', 'Asset'),
    ('Stock-in-Hand', 'Asset'),
    ('Sundry Debtors', 'Asset'),
    ('Fixed Assets', 'Asset'),
    ('Investments', 'Asset'),
    ('Misc. Expenses (ASSET)', 'Asset'),
    ('Capital Account', 'Liability'),
    ('Current Liabilities', 'Liability'),
    ('Loans (Liability)', 'Liability'),
    ('Provisions', 'Liability'),
    ('Reserves & Surplus', 'Liability'),
    ('Sundry Creditors', 'Liability'),
    ('Bank OD Accounts', 'Liability'),
    ('Duties & Taxes', 'Liability'),
    ('Indirect Income', 'Income'),
    ('Direct Income', 'Income'),
    ('Sales Accounts', 'Income'),
    ('Indirect Expenses', 'Expense'),
    ('Direct Expenses', 'Expense'),
    ('Purchase Accounts', 'Expense'),
    ('Suspense A/c', 'Liability'),
    ('Branch/Divisions', 'Asset'),
    ('Secured Loans', 'Liability'),
    ('Unsecured Loans', 'Liability'),
    ('Current Assets', 'Asset');

-- Ollama config keys
INSERT INTO general_config (config_key, config_value) VALUES
    ('ollama_base_url', 'http://localhost:11434'),
    ('ollama_chat_model', 'qwen2.5:32b'),
    ('ollama_embedding_model', 'nomic-embed-text'),
    ('ollama_batch_size', '10'),
    ('ollama_request_timeout_seconds', '120'),
    ('ollama_company_name', 'COGNOLOGIX')
ON CONFLICT (config_key) DO NOTHING;
