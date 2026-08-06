-- Monetary values stored in Rs Lakhs: increase scale from 2 to 3 decimal places (ADR-055).
-- Payroll snapshot / rate_card_line / fx_rate stay unchanged (full rupees or other units).

-- Revenue
ALTER TABLE revenue_invoice ALTER COLUMN amount TYPE NUMERIC(14,3);
ALTER TABLE revenue_invoice ALTER COLUMN balance TYPE NUMERIC(14,3);
ALTER TABLE revenue_invoice ALTER COLUMN amount_inr TYPE NUMERIC(14,3);
ALTER TABLE revenue_credit_note ALTER COLUMN amount TYPE NUMERIC(14,3);
ALTER TABLE revenue_credit_note ALTER COLUMN amount_inr TYPE NUMERIC(14,3);

-- Expense actuals
ALTER TABLE expense_actual ALTER COLUMN amount TYPE NUMERIC(12,3);

-- Budgeting plan inputs
ALTER TABLE salary_budget ALTER COLUMN billable_salaries TYPE NUMERIC(12,3);
ALTER TABLE salary_budget ALTER COLUMN bench_salaries TYPE NUMERIC(12,3);
ALTER TABLE salary_budget ALTER COLUMN support_salaries TYPE NUMERIC(12,3);
ALTER TABLE salary_budget ALTER COLUMN cofounders_salaries TYPE NUMERIC(12,3);
ALTER TABLE salary_budget ALTER COLUMN senior_mgmt_salaries TYPE NUMERIC(12,3);
ALTER TABLE client_revenue_plan ALTER COLUMN planned_tm_revenue TYPE NUMERIC(12,3);
ALTER TABLE client_revenue_plan ALTER COLUMN planned_fixed_bid_revenue TYPE NUMERIC(12,3);
ALTER TABLE overhead_budget ALTER COLUMN amount TYPE NUMERIC(12,3);

-- Period actuals (salary / payroll cost in Lakhs)
ALTER TABLE period_actuals ALTER COLUMN actual_billable_salaries TYPE NUMERIC(12,3);
ALTER TABLE period_actuals ALTER COLUMN actual_bench_salaries TYPE NUMERIC(12,3);
ALTER TABLE period_actuals ALTER COLUMN actual_support_salaries TYPE NUMERIC(12,3);
ALTER TABLE period_actuals ALTER COLUMN actual_leadership_salaries TYPE NUMERIC(12,3);
ALTER TABLE period_actuals ALTER COLUMN actual_management_salaries TYPE NUMERIC(12,3);
ALTER TABLE period_actuals ALTER COLUMN actual_total_payroll_cost TYPE NUMERIC(12,3);
ALTER TABLE period_actuals ALTER COLUMN actual_billable_employer_contributions TYPE NUMERIC(12,3);
ALTER TABLE period_actuals ALTER COLUMN actual_bench_employer_contributions TYPE NUMERIC(12,3);
ALTER TABLE period_actuals ALTER COLUMN actual_support_employer_contributions TYPE NUMERIC(12,3);
ALTER TABLE period_actuals ALTER COLUMN actual_leadership_employer_contributions TYPE NUMERIC(12,3);
ALTER TABLE period_actuals ALTER COLUMN actual_management_employer_contributions TYPE NUMERIC(12,3);
ALTER TABLE period_actuals ALTER COLUMN actual_total_employer_contributions TYPE NUMERIC(12,3);

-- Overhead actuals (legacy / backup table)
ALTER TABLE overhead_actuals ALTER COLUMN actual_amount TYPE NUMERIC(12,3);
