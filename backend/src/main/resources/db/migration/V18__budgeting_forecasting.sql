-- Budgeting & Forecasting core schema (ADR-037)

-- Financial Year Plan
CREATE TABLE financial_year_plan (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    fiscal_year VARCHAR(10) NOT NULL UNIQUE,  -- e.g. 'FY2627'
    fiscal_year_start DATE NOT NULL,           -- April 1
    fiscal_year_end DATE NOT NULL,             -- March 31
    opening_hc INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(255)
);

-- Forecast Types per Financial Year
CREATE TABLE forecast_type (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    financial_year_plan_id UUID NOT NULL REFERENCES financial_year_plan(id),
    type_name VARCHAR(100) NOT NULL,           -- NORMAL, AGGRESSIVE, CONSERVATIVE or custom
    is_primary BOOLEAN NOT NULL DEFAULT false, -- true for NORMAL only
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (financial_year_plan_id, type_name)
);

-- Forecast Versions per Forecast Type
CREATE TABLE forecast_version (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    forecast_type_id UUID NOT NULL REFERENCES forecast_type(id),
    version_number INTEGER NOT NULL,
    status VARCHAR(15) NOT NULL CHECK (status IN ('DRAFT','ACTIVE','SUPERSEDED')),
    published_at TIMESTAMPTZ,
    published_by VARCHAR(255),
    superseded_at TIMESTAMPTZ,
    superseded_by VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(255),
    UNIQUE (forecast_type_id, version_number)
);

-- HC Plan inputs (one row per category per month per forecast version)
CREATE TABLE hc_plan (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    forecast_version_id UUID NOT NULL REFERENCES forecast_version(id),
    plan_month INTEGER NOT NULL CHECK (plan_month BETWEEN 1 AND 12),
    plan_year INTEGER NOT NULL,
    planned_hires INTEGER NOT NULL DEFAULT 0,
    planned_exits INTEGER NOT NULL DEFAULT 0,
    planned_billable_hc INTEGER NOT NULL DEFAULT 0,
    planned_bench_hc INTEGER NOT NULL DEFAULT 0,
    planned_support_hc INTEGER NOT NULL DEFAULT 0,
    planned_leadership_hc INTEGER NOT NULL DEFAULT 0,
    planned_management_hc INTEGER NOT NULL DEFAULT 0,
    UNIQUE (forecast_version_id, plan_month, plan_year)
);

-- Client Revenue Plan (one row per client per month per forecast version)
CREATE TABLE client_revenue_plan (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    forecast_version_id UUID NOT NULL REFERENCES forecast_version(id),
    customer_id UUID NOT NULL,                 -- soft ref to customer (cross-module)
    plan_month INTEGER NOT NULL CHECK (plan_month BETWEEN 1 AND 12),
    plan_year INTEGER NOT NULL,
    planned_tm_revenue NUMERIC(12,2) NOT NULL DEFAULT 0,
    planned_fixed_bid_revenue NUMERIC(12,2) NOT NULL DEFAULT 0,
    UNIQUE (forecast_version_id, customer_id, plan_month, plan_year)
);

-- Salary Budget (one row per category per month per forecast version)
CREATE TABLE salary_budget (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    forecast_version_id UUID NOT NULL REFERENCES forecast_version(id),
    plan_month INTEGER NOT NULL CHECK (plan_month BETWEEN 1 AND 12),
    plan_year INTEGER NOT NULL,
    billable_salaries NUMERIC(12,2) NOT NULL DEFAULT 0,
    bench_salaries NUMERIC(12,2) NOT NULL DEFAULT 0,
    support_salaries NUMERIC(12,2) NOT NULL DEFAULT 0,
    cofounders_salaries NUMERIC(12,2) NOT NULL DEFAULT 0,
    senior_mgmt_salaries NUMERIC(12,2) NOT NULL DEFAULT 0,
    UNIQUE (forecast_version_id, plan_month, plan_year)
);

-- Overhead Budget (one row per line item per month per forecast version)
CREATE TABLE overhead_budget (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    forecast_version_id UUID NOT NULL REFERENCES forecast_version(id),
    plan_month INTEGER NOT NULL CHECK (plan_month BETWEEN 1 AND 12),
    plan_year INTEGER NOT NULL,
    overhead_line VARCHAR(100) NOT NULL,       -- e.g. 'office_rent', 'cloud', 'training'
    amount NUMERIC(12,2) NOT NULL DEFAULT 0,
    UNIQUE (forecast_version_id, plan_month, plan_year, overhead_line)
);

-- Period Actuals (written by PeriodFinalisedEvent listener)
CREATE TABLE period_actuals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    financial_year_plan_id UUID NOT NULL REFERENCES financial_year_plan(id),
    actuals_month INTEGER NOT NULL CHECK (actuals_month BETWEEN 1 AND 12),
    actuals_year INTEGER NOT NULL,
    -- HC actuals
    actual_billable_hc INTEGER,
    actual_bench_hc INTEGER,
    actual_support_hc INTEGER,
    actual_leadership_hc INTEGER,
    actual_management_hc INTEGER,
    actual_total_hc INTEGER,
    -- Salary actuals (from PeriodFinalisedEvent)
    actual_billable_salaries NUMERIC(12,2),
    actual_bench_salaries NUMERIC(12,2),
    actual_support_salaries NUMERIC(12,2),
    actual_leadership_salaries NUMERIC(12,2),
    actual_management_salaries NUMERIC(12,2),
    -- Revenue actuals (manual until Revenue module exists)
    actual_revenue_manual NUMERIC(12,2),
    -- Source tracking
    people_period_version_id UUID,             -- which period_version fed this (soft ref)
    fx_rate_id UUID,                           -- ADR-017: store fx_rate_id for any USD conversion
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (financial_year_plan_id, actuals_month, actuals_year)
);

-- Overhead Actuals (Finance enters manually per month)
CREATE TABLE overhead_actuals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    financial_year_plan_id UUID NOT NULL REFERENCES financial_year_plan(id),
    actuals_month INTEGER NOT NULL CHECK (actuals_month BETWEEN 1 AND 12),
    actuals_year INTEGER NOT NULL,
    overhead_line VARCHAR(100) NOT NULL,
    actual_amount NUMERIC(12,2) NOT NULL DEFAULT 0,
    entered_by VARCHAR(255),
    entered_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (financial_year_plan_id, actuals_month, actuals_year, overhead_line)
);

-- Seed standard overhead line items as a reference table
CREATE TABLE overhead_line_item (
    line_code VARCHAR(100) PRIMARY KEY,
    category VARCHAR(50) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    sort_order INTEGER NOT NULL
);

INSERT INTO overhead_line_item (line_code, category, display_name, sort_order) VALUES
    ('office_rent', 'Facilities', 'Office Rent', 1),
    ('electricity', 'Facilities', 'Electricity', 2),
    ('housekeeping', 'Facilities', 'Housekeeping Material', 3),
    ('internet', 'Facilities', 'Internet', 4),
    ('postage_courier', 'Facilities', 'Postage and Courier', 5),
    ('printing_stationery', 'Facilities', 'Printing and Stationery', 6),
    ('cloud', 'Technology', 'Cloud', 7),
    ('computer_consumables', 'Technology', 'Computer Consumables', 8),
    ('subscription_software', 'Technology', 'Subscription and Software', 9),
    ('staff_medical', 'People and Welfare', 'Staff Medical Insurance and Reimbursement', 10),
    ('staff_welfare', 'People and Welfare', 'Staff Welfare', 11),
    ('recruitment', 'People and Welfare', 'Recruitment', 12),
    ('screening', 'People and Welfare', 'Screening', 13),
    ('travel_domestic', 'Travel and Transport', 'Travelling Expenses - Domestic', 14),
    ('car_expenses', 'Travel and Transport', 'Car Expenses', 15),
    ('audit_fees', 'Finance and Legal', 'Audit and Statutory Fees', 16),
    ('bank_charges', 'Finance and Legal', 'Bank and Credit Card Charges', 17),
    ('credit_card_expenses', 'Finance and Legal', 'Credit Card Expenses', 18),
    ('business_insurance', 'Finance and Legal', 'Business Insurance', 19),
    ('prof_fees_consultancy', 'Finance and Legal', 'Professional Fees - Consultancy', 20),
    ('prof_fees_sw_dev', 'Finance and Legal', 'Professional Fees - SW Dev (Tooling)', 21),
    ('prof_fees_others', 'Finance and Legal', 'Professional Fees - Others', 22),
    ('training_upskilling', 'Delivery Costs', 'Training and Upskilling', 23),
    ('subcontractors', 'Delivery Costs', 'Prof Fees - SW Dev (Subcontractors)', 24);

-- General config additions (extend existing general_config table)
INSERT INTO general_config (config_key, config_value) VALUES
    ('working_days_per_month', '22'),
    ('annual_attrition_rate_pct', '12'),
    ('target_billable_ratio_pct', '70')
    ON CONFLICT (config_key) DO NOTHING;
