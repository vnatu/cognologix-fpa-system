-- FX Rate (average-per-period, effective-dated, system-wide)
-- btree_gist already enabled in V2; CREATE EXTENSION is idempotent but omitted for clarity
CREATE TABLE fx_rate (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    currency_pair   VARCHAR(10) NOT NULL,               -- e.g. 'USD_INR'
    rate            NUMERIC(10,4) NOT NULL,
    effective_from  DATE NOT NULL,
    effective_to    DATE,                               -- null = currently active
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      VARCHAR(255),                       -- audit: who set this rate
    CONSTRAINT no_overlapping_fx_rates
        EXCLUDE USING gist (
            currency_pair WITH =,
            daterange(effective_from, effective_to, '[)') WITH &&
        )
);
