package com.cognologix.fpa.budgeting;

/**
 * Shared P&amp;L / metric formula text for Excel reports and comments.
 */
public final class BudgetingFormulaCatalog {

    private BudgetingFormulaCatalog() {}

    public record FormulaRow(String metric, String formula, String components, String notes) {}

    public record GlossaryRow(String term, String definition) {}

    public static final FormulaRow[] KEY_FORMULAS = {
            new FormulaRow("Total Revenue", "T&M Revenue + Fixed-Bid Revenue",
                    "Plan: entered by Finance in Plan Setup. Actual: from Zoho Books invoices via Revenue module.",
                    ""),
            new FormulaRow("COGS", "Billable Payroll Cost + Bench Payroll Cost + Delivery Overheads",
                    "Delivery Overheads = Training & Upskilling + Subcontractor fees",
                    ""),
            new FormulaRow("Gross Profit", "Total Revenue − COGS", "", ""),
            new FormulaRow("Gross Margin %", "Gross Profit ÷ Total Revenue × 100", "", ""),
            new FormulaRow("OpEx",
                    "Support + Leadership + Management Payroll Cost + Non-Delivery Overheads + Variable Pay",
                    "Non-Delivery Overheads = Facilities + Technology + People & Welfare + Travel + Finance & Legal",
                    ""),
            new FormulaRow("EBITDA", "Gross Profit − OpEx", "", ""),
            new FormulaRow("EBITDA Margin %", "EBITDA ÷ Total Revenue × 100", "", ""),
            new FormulaRow("Total Payroll Cost", "Gross Pay + Employer Contributions",
                    "Employer Contributions = EPF + EPS + EDLI + EPF Admin + VPF + NPS + Gratuity",
                    "Plan uses 13% estimate; Actuals use real contribution data from Zoho Payroll."),
            new FormulaRow("Billable Ratio %", "Billable HC ÷ Total HC × 100",
                    "Billable HC from finalised People & Payroll master records",
                    ""),
    };

    public static final GlossaryRow[] GLOSSARY = {
            new GlossaryRow("COGS",
                    "Cost of Goods Sold — direct delivery cost: billable and bench payroll cost plus delivery overheads."),
            new GlossaryRow("OpEx",
                    "Operating Expenses — support, leadership, and management payroll cost plus non-delivery overheads and variable pay."),
            new GlossaryRow("EBITDA",
                    "Earnings Before Interest, Tax, Depreciation and Amortisation — Gross Profit minus OpEx."),
            new GlossaryRow("Gross Margin",
                    "Gross Profit as a percentage of Total Revenue (or BU Revenue − BU Payroll Cost at BU level)."),
            new GlossaryRow("Billable Ratio",
                    "Share of headcount that is billable (Billable HC ÷ Total HC)."),
            new GlossaryRow("Rolling Forecast",
                    "Actuals for finalised months plus the ACTIVE Normal plan for remaining months."),
            new GlossaryRow("Baseline",
                    "Current ACTIVE version of the Normal (primary) forecast type."),
            new GlossaryRow("Delta",
                    "Rolling Forecast minus Baseline — how the current trajectory differs from the plan."),
            new GlossaryRow("Total Payroll Cost",
                    "Gross Pay plus employer contributions (EPF, EPS, EDLI, EPF Admin, VPF, NPS, Gratuity)."),
            new GlossaryRow("Minimum Billing Rate",
                    "Fully loaded cost per billable head (Layer 1 + 2 + 3) — break-even rate for client negotiations."),
    };

    public static final String COMMENT_TOTAL_REVENUE =
            "Total Revenue = T&M Revenue + Fixed-Bid Revenue. Plan from Plan Setup; Actual from Zoho Books via Revenue module.";
    public static final String COMMENT_COGS =
            "COGS = Billable Payroll Cost + Bench Payroll Cost + Delivery Overheads (Training & Upskilling + Subcontractors). Payroll Cost = Gross Pay + Employer Contributions.";
    public static final String COMMENT_GROSS_PROFIT =
            "Gross Profit = Total Revenue − COGS.";
    public static final String COMMENT_GROSS_MARGIN_PCT =
            "Gross Margin % = Gross Profit ÷ Total Revenue × 100.";
    public static final String COMMENT_OPEX =
            "OpEx = Support + Leadership + Management Payroll Cost + Non-Delivery Overheads + Variable Pay.";
    public static final String COMMENT_EBITDA =
            "EBITDA = Gross Profit − OpEx. Gross Profit = Revenue − COGS. COGS = Billable + Bench Payroll Cost + Delivery Overheads.";
    public static final String COMMENT_EBITDA_MARGIN_PCT =
            "EBITDA Margin % = EBITDA ÷ Total Revenue × 100.";
    public static final String COMMENT_TOTAL_PAYROLL =
            "Total Payroll Cost = Gross Pay + Employer Contributions (EPF, EPS, EDLI, EPF Admin, VPF, NPS, Gratuity).";
    public static final String COMMENT_BILLABLE_RATIO =
            "Billable Ratio % = Billable HC ÷ Total HC × 100.";
    public static final String COMMENT_MIN_BILLING_RATE =
            "Minimum Billing Rate = Layer 1 + Layer 2 + Layer 3 per billable head. Shared overhead (Layer 3) is allocated entirely to billable employees — each billable head absorbs their proportional share of company fixed costs.";
    public static final String COMMENT_BU_GROSS_MARGIN =
            "Gross Margin (BU) = BU Revenue − BU Total Payroll Cost. BU Revenue from Zoho Books; BU Payroll Cost = all employees in that BU.";
}
