-- btree_gist required for the daterange overlap exclusion constraints on rate_card and fx_rate
CREATE EXTENSION IF NOT EXISTS btree_gist;

-- Customer Master
CREATE TABLE customer (
    id                              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_code                   VARCHAR(50) NOT NULL UNIQUE,
    customer_name                   VARCHAR(255) NOT NULL,
    zoho_books_customer_ref         VARCHAR(100) UNIQUE,
    relationship_owner_employee_id  VARCHAR(50),        -- soft ref, no FK (cross-module)
    lifecycle_status                VARCHAR(20) NOT NULL
                                    CHECK (lifecycle_status IN
                                    ('ACTIVE','AT_RISK','CHURNED','PROSPECT')),
    created_at                      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Project Codes (nested under customer)
CREATE TABLE customer_project_code (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id     UUID NOT NULL REFERENCES customer(id),
    project_code    VARCHAR(50) NOT NULL,
    description     VARCHAR(500),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (customer_id, project_code)
);

-- Rate Card Header (effective-dated)
CREATE TABLE rate_card (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id         UUID NOT NULL REFERENCES customer(id),
    rate_card_type      VARCHAR(10) NOT NULL CHECK (rate_card_type IN ('FLAT','TIERED')),
    effective_from      DATE NOT NULL,
    effective_to        DATE,                           -- null = currently active
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT no_overlapping_rate_cards
        EXCLUDE USING gist (
            customer_id WITH =,
            daterange(effective_from, effective_to, '[)') WITH &&
        )
);

-- Rate Card Lines (flat = one row, job_level null; tiered = N rows)
CREATE TABLE rate_card_line (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rate_card_id    UUID NOT NULL REFERENCES rate_card(id),
    job_level       VARCHAR(100),                       -- null = FLAT blended rate
    rate_amount     NUMERIC(12,2) NOT NULL,
    currency        VARCHAR(3) NOT NULL CHECK (currency IN ('USD','INR')),
    UNIQUE (rate_card_id, job_level)                    -- enforces one line per job level (tiered)
);

-- Partial unique index: enforces the spec's "flat = one row" rule.
-- PostgreSQL's UNIQUE constraint treats two NULLs as distinct, so UNIQUE(rate_card_id, job_level)
-- alone would allow multiple flat lines per rate card. This index closes that gap.
CREATE UNIQUE INDEX rate_card_line_flat_unique
    ON rate_card_line (rate_card_id)
    WHERE job_level IS NULL;

-- Commercial Terms (current value per client, audit-logged not effective-dated per spec §7.1)
CREATE TABLE commercial_terms (
    customer_id     UUID PRIMARY KEY REFERENCES customer(id),
    dso_days        INTEGER NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Concentration Risk Thresholds (global config, singleton — enforced in service layer)
CREATE TABLE concentration_risk_config (
    id                              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    single_client_threshold_pct     NUMERIC(5,2) NOT NULL DEFAULT 30.00,
    created_at                      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Seed the single global threshold row (30% per spec §8)
INSERT INTO concentration_risk_config (single_client_threshold_pct) VALUES (30.00);

-- Concentration Watch Groups (combined-client tracking e.g. Icertis+Cadent per spec §8)
CREATE TABLE concentration_watch_group (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_name    VARCHAR(255) NOT NULL,
    threshold_pct NUMERIC(5,2) NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE concentration_watch_group_member (
    group_id        UUID NOT NULL REFERENCES concentration_watch_group(id),
    customer_id     UUID NOT NULL REFERENCES customer(id),
    PRIMARY KEY (group_id, customer_id)
);
