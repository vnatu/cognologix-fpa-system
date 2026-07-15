-- ADR-035 (revised): rate card ↔ project code many-to-many via join table.
-- A rate card may cover multiple project codes; assignment uniqueness for active
-- cards is enforced in CustomerService (effective_to lives on rate_card).

-- Drop V16 project-scoped exclusion (references project_code_id)
ALTER TABLE rate_card
    DROP CONSTRAINT IF EXISTS no_overlapping_project_rate_cards;

-- Blended exclusion also references project_code_id in its WHERE clause — drop before column
ALTER TABLE rate_card
    DROP CONSTRAINT IF EXISTS no_overlapping_blended_rate_cards;

ALTER TABLE rate_card
    DROP COLUMN IF EXISTS project_code_id;

-- Join table: many project codes per rate card
CREATE TABLE rate_card_project_code (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rate_card_id UUID NOT NULL REFERENCES rate_card(id) ON DELETE CASCADE,
    project_code_id UUID NOT NULL REFERENCES customer_project_code(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (rate_card_id, project_code_id)
);

CREATE INDEX idx_rate_card_project_code_project
    ON rate_card_project_code (project_code_id);

CREATE INDEX idx_rate_card_project_code_rate_card
    ON rate_card_project_code (rate_card_id);
