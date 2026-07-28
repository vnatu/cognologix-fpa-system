-- Employer benefit contributions on payroll snapshots and master records.
-- Total Payroll Cost = Gross Pay + Employer Contributions (ADR-045).

ALTER TABLE payroll_snapshot
    ADD COLUMN epf_contribution NUMERIC(12,2),
    ADD COLUMN eps_contribution NUMERIC(12,2),
    ADD COLUMN edli_contribution NUMERIC(12,2),
    ADD COLUMN epf_admin_charges NUMERIC(12,2),
    ADD COLUMN vpf NUMERIC(12,2),
    ADD COLUMN nps_deduction NUMERIC(12,2),
    ADD COLUMN gratuity NUMERIC(12,2),
    ADD COLUMN total_employer_contributions NUMERIC(12,2)
        GENERATED ALWAYS AS (
            COALESCE(epf_contribution, 0) +
            COALESCE(eps_contribution, 0) +
            COALESCE(edli_contribution, 0) +
            COALESCE(epf_admin_charges, 0) +
            COALESCE(vpf, 0) +
            COALESCE(nps_deduction, 0) +
            COALESCE(gratuity, 0)
        ) STORED;

ALTER TABLE master_record
    ADD COLUMN total_employer_contributions NUMERIC(12,2),
    ADD COLUMN total_payroll_cost NUMERIC(12,2)
        GENERATED ALWAYS AS (
            COALESCE(gross_pay, 0) + COALESCE(total_employer_contributions, 0)
        ) STORED;

-- Update period_actuals to carry total payroll cost per classification
ALTER TABLE period_actuals
    ADD COLUMN actual_billable_employer_contributions NUMERIC(12,2),
    ADD COLUMN actual_bench_employer_contributions NUMERIC(12,2),
    ADD COLUMN actual_support_employer_contributions NUMERIC(12,2),
    ADD COLUMN actual_leadership_employer_contributions NUMERIC(12,2),
    ADD COLUMN actual_management_employer_contributions NUMERIC(12,2),
    ADD COLUMN actual_total_employer_contributions NUMERIC(12,2),
    ADD COLUMN actual_total_payroll_cost NUMERIC(12,2);

-- BU-level actuals: store total payroll cost for Gross Margin (Revenue − totalPayrollCost)
ALTER TABLE period_bu_actuals
    ADD COLUMN total_employer_contributions NUMERIC(12,2),
    ADD COLUMN total_payroll_cost NUMERIC(12,2);
