-- Expense categories (extensible beyond the seeded overhead lines)
CREATE TABLE expense_category (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    line_code VARCHAR(100) NOT NULL UNIQUE,
    category_group VARCHAR(50) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    description VARCHAR(500),
    is_active BOOLEAN NOT NULL DEFAULT true,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Seed the existing overhead line items (aligned with overhead_line_item)
INSERT INTO expense_category (line_code, category_group, display_name, sort_order) VALUES
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

-- Monthly expense actuals (one row per category per month)
CREATE TABLE expense_actual (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    expense_month INTEGER NOT NULL CHECK (expense_month BETWEEN 1 AND 12),
    expense_year INTEGER NOT NULL,
    expense_category_id UUID NOT NULL REFERENCES expense_category(id),
    amount NUMERIC(12,2) NOT NULL DEFAULT 0,
    notes VARCHAR(500),
    is_locked BOOLEAN NOT NULL DEFAULT false,
    updated_by VARCHAR(255),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (expense_month, expense_year, expense_category_id)
);

-- Month lock table (locks all expense entries for a month)
CREATE TABLE expense_month_lock (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    expense_month INTEGER NOT NULL CHECK (expense_month BETWEEN 1 AND 12),
    expense_year INTEGER NOT NULL,
    locked_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    locked_by VARCHAR(255) NOT NULL,
    unlocked_at TIMESTAMPTZ,
    unlocked_by VARCHAR(255),
    unlock_reason TEXT,
    UNIQUE (expense_month, expense_year)
);
