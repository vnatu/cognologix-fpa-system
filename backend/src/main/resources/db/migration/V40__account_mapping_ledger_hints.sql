-- ADR-068. Statement account → bank ledger, per-ledger hints, last confirmed amount.
-- amount_min / amount_max are intentionally not added.

CREATE TABLE recon_account_mapping (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    statement_type VARCHAR(20) NOT NULL CHECK (statement_type IN ('HDFC_BANK','HSBC_CC')),
    identifier VARCHAR(100) NOT NULL,
    ledger_name VARCHAR(500) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(255),
    UNIQUE (statement_type, identifier)
);

ALTER TABLE recon_run ADD COLUMN IF NOT EXISTS bank_ledger_name VARCHAR(500);

CREATE TABLE tally_ledger_hint (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ledger_id UUID NOT NULL UNIQUE REFERENCES tally_ledger(id) ON DELETE CASCADE,
    purpose VARCHAR(500),
    keywords VARCHAR(500),
    typical_amount VARCHAR(255),
    disambiguation_note VARCHAR(500),
    updated_by VARCHAR(255),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE learned_mapping ADD COLUMN IF NOT EXISTS last_amount NUMERIC(14,2);
