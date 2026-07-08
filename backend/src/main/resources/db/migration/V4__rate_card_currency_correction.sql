-- ADR-020: currency belongs to rate_card (one currency per card, not per line)
-- Also adds a human-readable name to rate_card for display in the UI.

ALTER TABLE rate_card
    ADD COLUMN name     VARCHAR(255) NOT NULL DEFAULT 'Default',
    ADD COLUMN currency VARCHAR(3)   NOT NULL DEFAULT 'USD',
    ADD CONSTRAINT chk_rate_card_currency CHECK (currency IN ('USD', 'INR'));

-- Backfill card-level currency from existing line values before dropping the column
UPDATE rate_card rc
SET currency = sub.currency
FROM (
    SELECT DISTINCT ON (rate_card_id) rate_card_id, currency
    FROM rate_card_line
    ORDER BY rate_card_id, id
) sub
WHERE rc.id = sub.rate_card_id;

ALTER TABLE rate_card_line
    DROP COLUMN currency;
