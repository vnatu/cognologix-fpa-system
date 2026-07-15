-- ADR-035: Project-scoped rate cards alongside customer-level blended rate cards.
-- project_code_id NULL = customer-level blended card.

ALTER TABLE rate_card
    ADD COLUMN project_code_id UUID REFERENCES customer_project_code(id);

-- Drop the existing overlap exclusion constraint (customer-wide, single active card)
ALTER TABLE rate_card
    DROP CONSTRAINT no_overlapping_rate_cards;

-- Project-scoped cards: one active per customer+project at a time
ALTER TABLE rate_card
    ADD CONSTRAINT no_overlapping_project_rate_cards
    EXCLUDE USING gist (
        customer_id WITH =,
        project_code_id WITH =,
        daterange(effective_from, effective_to, '[)') WITH &&
    )
    WHERE (project_code_id IS NOT NULL);

-- Blended cards: one active per customer at a time (no project code)
ALTER TABLE rate_card
    ADD CONSTRAINT no_overlapping_blended_rate_cards
    EXCLUDE USING gist (
        customer_id WITH =,
        daterange(effective_from, effective_to, '[)') WITH &&
    )
    WHERE (project_code_id IS NULL);
