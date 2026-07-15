-- Per-BU actuals snapshot from PeriodFinalisedEvent (ADR-022 / ADR-037)
CREATE TABLE period_bu_actuals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    period_actuals_id UUID NOT NULL REFERENCES period_actuals(id) ON DELETE CASCADE,
    business_unit VARCHAR(255) NOT NULL,
    billable_hc INTEGER NOT NULL DEFAULT 0,
    total_gross_pay NUMERIC(12,2) NOT NULL DEFAULT 0,
    UNIQUE (period_actuals_id, business_unit)
);

-- Per-client revenue actuals (manual until Revenue module exists)
CREATE TABLE client_revenue_actual (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    financial_year_plan_id UUID NOT NULL REFERENCES financial_year_plan(id),
    customer_id UUID NOT NULL,
    actuals_month INTEGER NOT NULL CHECK (actuals_month BETWEEN 1 AND 12),
    actuals_year INTEGER NOT NULL,
    actual_revenue NUMERIC(12,2) NOT NULL DEFAULT 0,
    entered_by VARCHAR(255),
    entered_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (financial_year_plan_id, customer_id, actuals_month, actuals_year)
);
