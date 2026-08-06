/** Shared metric formula definitions for Budgeting dashboards. */

export interface FormulaDef {
  metric: string;
  formula: string;
  components?: string[];
  source?: string;
}

export const FORMULAS = {
  totalRevenue: {
    metric: 'Total Revenue',
    formula: 'T&M Revenue + Fixed-Bid Revenue',
    components: [
      'Plan: entered by Finance in Plan Setup. Actual: from Zoho Books invoices via Revenue module.',
    ],
    source:
      'Plan figures from Budgeting Plan Setup. Actuals from Revenue module.',
  },
  cogs: {
    metric: 'COGS',
    formula: 'Billable Payroll Cost + Bench Payroll Cost + Delivery Overheads',
    components: [
      'Delivery Overheads = Training & Upskilling + Subcontractor fees',
      'Payroll Cost = Gross Pay + Employer Contributions',
    ],
    source:
      'Salary actuals from People & Payroll finalised periods. Overhead actuals from Expenses module.',
  },
  grossProfit: {
    metric: 'Gross Profit',
    formula: 'Total Revenue − COGS',
    components: [
      'COGS = Billable Payroll Cost + Bench Payroll Cost + Delivery Overheads',
    ],
  },
  grossMarginPct: {
    metric: 'Gross Margin %',
    formula: 'Gross Profit ÷ Total Revenue × 100',
  },
  opex: {
    metric: 'OpEx',
    formula:
      'Support + Leadership + Management Payroll Cost + Non-Delivery Overheads + Variable Pay',
    components: [
      'Non-Delivery Overheads = Facilities + Technology + People & Welfare + Travel + Finance & Legal',
      'Payroll Cost = Gross Pay + Employer Contributions',
    ],
    source:
      'Salary actuals from People & Payroll finalised periods. Overhead actuals from Expenses module.',
  },
  ebitda: {
    metric: 'EBITDA',
    formula: 'Gross Profit − OpEx',
    components: [
      'Gross Profit = Total Revenue − COGS',
      'COGS = Billable Payroll Cost + Bench Payroll Cost + Delivery Overheads',
      'OpEx = Support + Leadership + Management Payroll Cost + Non-Delivery Overheads + Variable Pay',
      'Payroll Cost = Gross Pay + Employer Contributions (EPF, EPS, EDLI, EPF Admin, VPF, NPS, Gratuity)',
    ],
    source:
      'Salary actuals from People & Payroll finalised periods. Overhead actuals from Expenses module.',
  },
  ebitdaMarginPct: {
    metric: 'EBITDA Margin %',
    formula: 'EBITDA ÷ Total Revenue × 100',
  },
  totalPayrollCost: {
    metric: 'Total Payroll Cost',
    formula: 'Gross Pay + Employer Contributions',
    components: [
      'Employer Contributions = EPF + EPS + EDLI + EPF Admin + VPF + NPS + Gratuity',
      'Plan: Salary Budget × 1.13 (13% estimate for employer contributions). Actual: Gross Pay + real employer contributions (EPF, EPS, EDLI, EPF Admin, VPF, NPS, Gratuity) from Zoho Payroll.',
    ],
    source: 'Salary actuals from People & Payroll finalised periods.',
  },
  billableRatio: {
    metric: 'Billable Ratio %',
    formula: 'Billable HC ÷ Total HC × 100',
    components: [
      'Billable HC from finalised People & Payroll master records',
    ],
  },
  layer1: {
    metric: 'Layer 1 Total',
    formula: 'Direct Salary + Employer Contributions per head',
    components: [
      'Actuals: employer contributions from Zoho Payroll. Plan: 13% estimate.',
    ],
  },
  layer2: {
    metric: 'Layer 2',
    formula: 'Direct Overhead per head',
    components: [
      'Medical, welfare, consumables, software, training allocated per head.',
    ],
  },
  layer3: {
    metric: 'Layer 3',
    formula: 'Shared Overhead ÷ Billable HC',
    components: [
      'Shared overhead costs (rent, electricity, internet etc.) allocated entirely to billable employees — since billable revenue funds all fixed costs, each billable employee absorbs their proportional share of company overhead.',
    ],
  },
  totalCostPerHead: {
    metric: 'Total Cost per Head',
    formula: 'Layer 1 + Layer 2 + Layer 3',
    components: ['Bench/Support/Leadership carry Layers 1 + 2 only (no Layer 3).'],
  },
  minBillingRate: {
    metric: 'Minimum Billing Rate',
    formula: 'Layer 1 + Layer 2 + Layer 3 per billable head',
    components: [
      'Shared overhead costs (rent, electricity, internet etc.) allocated entirely to billable employees — since billable revenue funds all fixed costs, each billable employee absorbs their proportional share of company overhead.',
    ],
  },
  buGrossMargin: {
    metric: 'Gross Margin',
    formula: 'BU Revenue − BU Total Payroll Cost',
    components: [
      'BU Revenue from Zoho Books invoices. BU Payroll Cost = all employees in that BU',
    ],
  },
  buGrossMarginPct: {
    metric: 'Gross Margin %',
    formula: 'Gross Margin ÷ BU Revenue × 100',
  },
  buCostPct: {
    metric: 'BU Cost % of Total',
    formula: "This BU's Total Payroll Cost ÷ Company Total Payroll Cost × 100",
  },
  buRevenuePct: {
    metric: 'BU Revenue % of Total',
    formula: "This BU's Actual Revenue ÷ Company Total Revenue × 100",
  },
} as const satisfies Record<string, FormulaDef>;
