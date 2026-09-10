-- Total Payroll Cost = Net Pay + employer contributions excluding VPF (ADR-061).

-- Fix payroll_snapshot total_employer_contributions (exclude VPF)
ALTER TABLE payroll_snapshot DROP COLUMN total_employer_contributions;
ALTER TABLE payroll_snapshot ADD COLUMN total_employer_contributions NUMERIC(12,2)
    GENERATED ALWAYS AS (
        COALESCE(epf_contribution, 0) +
        COALESCE(eps_contribution, 0) +
        COALESCE(edli_contribution, 0) +
        COALESCE(epf_admin_charges, 0) +
        COALESCE(nps_deduction, 0) +
        COALESCE(gratuity, 0)
    ) STORED;

-- Fix total_payroll_cost on payroll_snapshot (use net_pay not gross_pay)
ALTER TABLE payroll_snapshot DROP COLUMN IF EXISTS total_payroll_cost;
ALTER TABLE payroll_snapshot ADD COLUMN total_payroll_cost NUMERIC(12,2)
    GENERATED ALWAYS AS (
        COALESCE(net_pay, 0) +
        COALESCE(epf_contribution, 0) +
        COALESCE(eps_contribution, 0) +
        COALESCE(edli_contribution, 0) +
        COALESCE(epf_admin_charges, 0) +
        COALESCE(nps_deduction, 0) +
        COALESCE(gratuity, 0)
    ) STORED;

-- Add net_pay to master_record
ALTER TABLE master_record ADD COLUMN IF NOT EXISTS net_pay NUMERIC(12,2);

-- Fix total_payroll_cost on master_record
ALTER TABLE master_record DROP COLUMN total_payroll_cost;
ALTER TABLE master_record ADD COLUMN total_payroll_cost NUMERIC(12,2)
    GENERATED ALWAYS AS (
        COALESCE(net_pay, 0) + COALESCE(total_employer_contributions, 0)
    ) STORED;
