**Cognologix Technologies**

**Financial Planning & People Analytics System**

**Requirements Specification**

**Module 3: Budgeting & Forecasting (AOP)**

Version 1.0 (Draft — Pending Review) \| July 2026

**Document Control**

| **Field**         | **Detail**                                                                                                                                               |
|-------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------|
| Prepared for      | Vaibhav, Co-Founder, Cognologix Technologies                                                                                                             |
| Prepared by       | Vaibhav Natu                                                                                                                                             |
| Status            | Draft v1.0 — pending sign-off                                                                                                                            |
| Scope             | Annual Operating Plan (AOP) — baseline forecast, rolling forecast, delta, Plan vs Actual across Revenue / HC / Salary / Overhead / Gross Margin / EBITDA |
| Related documents | Module 1 (People & Payroll), Module 2 (Customer Management), ADR log (ADR-022, ADR-037)                                                                  |
| Supersedes        | N/A — first Budgeting & Forecasting spec; replaces manual Excel AOP model (Cognologix_FY2627_v9.xlsx)                                                    |

**Revision History**

| **Rev** | **Date**  | **Change**                                                                                                                                                 |
|---------|-----------|------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 1       | July 2026 | Initial draft — financial year plan structure, three forecast types, versioned baseline, rolling forecast, delta, Plan vs Actual across all six dimensions |

**1. Purpose & Scope**

This document specifies Module 3: Budgeting & Forecasting (AOP) — the original goal of the Cognologix Financial Planning & People Analytics System and the downstream consumer of both People & Payroll (Module 1) and Revenue (future module) actuals. It replaces the manual Excel AOP model with a system-of-record equivalent that tracks Plan vs Actual across all key financial and operational dimensions.

The core philosophy throughout this module is identical to the broader system: Plan vs Actual is the primary value delivered. Every input, every calculated output, and every dashboard view exists to answer one question: how is Cognologix tracking against its plan?

**In scope**

- Financial year plan structure with three forecast types (Normal, Aggressive, Conservative) — Finance can add more

- Versioned baseline per forecast type — one ACTIVE version at a time, prior versions SUPERSEDED and retained permanently

- Plan inputs: HC Plan, Client Revenue Plan (manual), Salary Budget, Overhead Budget (23 line items across 6 categories)

- Rolling Forecast = Actuals (finalised periods) + current Active Normal forecast (future months) — auto-computed

- Delta = Rolling Forecast − current Active Normal Baseline

- Plan vs Actual across all six dimensions: Revenue, HC, Salary Cost, Overhead, Gross Margin, EBITDA

- General Settings additions: working days per month, annual attrition rate, target billable ratio, opening HC per FY

- Actuals consumption via PeriodFinalisedEvent from People & Payroll (ADR-022)

- Scenario comparison: any two forecast types compared side by side

**Out of scope for this module**

- Revenue actuals from Zoho Books — Revenue module not yet specced; Finance enters revenue actuals manually as a placeholder until available

- Overhead actuals from Tally Prime — Finance enters manually for now

- Tier assignment for T&M rate calculation — deferred to a later phase

- Hiring cost detailed model (BGV, screening, agency fees per joiner) — deferred; covered by Overhead Budget Recruitment lines for now

- Employee cost per billable head (layered analysis) — deferred

**2. Background**

Cognologix has maintained a sophisticated 14-sheet Excel AOP model covering headcount movement, revenue by client, cost structure, BU-level gross margin, P&L, scenario comparison, and monthly metrics. This module is the system equivalent of that model — with the critical improvement that actuals flow in automatically from People & Payroll rather than being manually copied from a separate PeopleData workbook each month.

The Excel model distinguishes clearly between input cells (Finance enters), formula cells (auto-calculated), and actual cells (entered monthly). This module preserves that discipline: Finance inputs are explicit, actuals flow from authoritative sources, and calculated outputs are never manually overridden.

*All monetary values in this module are in INR Lakhs unless explicitly noted. USD-denominated revenues are converted at the FX rate from General configuration (ADR-017). The fx_rate_id is stored with every conversion record for historical traceability.*

**3. Financial Year Plan Structure**

**3.1 Financial Year Plan**

One financial_year_plan record per Indian Financial Year (April–March), identified by fiscal_year (e.g. FY2627). Contains the opening headcount for the year and links to all forecast types.

**3.2 Forecast Types**

Three standard types are seeded: NORMAL (primary — drives Rolling Forecast and Delta), AGGRESSIVE (optimistic scenario), CONSERVATIVE (pessimistic scenario). Finance can create additional named types. Each forecast type is independent — its own plan inputs and its own version history. All forecast types share the same actuals.

**3.3 Forecast Versions and Lifecycle**

Each forecast type has exactly one ACTIVE version at any time. Version lifecycle:

| **Status** | **Description**                                                                                                           |
|------------|---------------------------------------------------------------------------------------------------------------------------|
| DRAFT      | Finance is currently editing. Not used in any calculations. Only one DRAFT per forecast type at a time.                   |
| ACTIVE     | The current plan. NORMAL's active version drives Rolling Forecast and Delta. Only one ACTIVE per forecast type at a time. |
| SUPERSEDED | Replaced by a newer version when Finance publishes a revision. Retained permanently for audit and history. Never deleted. |

*The Baseline is always the current ACTIVE version of the NORMAL forecast. When Finance supersedes v1 with v2 in June, the Delta from June onwards becomes Rolling Forecast − v2. This answers 'how are we tracking against our current plan' — more useful than comparing against an April plan that Finance has since revised.*

**4. Plan Inputs**

All plan inputs are entered per forecast version, per month (12 monthly columns: Apr–Mar). Finance enters these when creating or revising a forecast version.

**4.1 HC Plan**

| **Input Line**           | **Description**                                              |
|--------------------------|--------------------------------------------------------------|
| Planned Hires            | Number of new joiners expected this month                    |
| Planned Exits            | Number of attritions/exits expected this month               |
| Planned Billable HC      | Expected billable headcount at month end                     |
| Planned Bench HC         | Expected bench (delivery, undeployed) headcount at month end |
| Planned Support HC       | Expected non-billable support HC (HR, IT, Admin, Finance)    |
| Planned Leadership HC    | Expected senior management headcount                         |
| Planned Management HC    | Expected co-founder/management HC (fixed at 4)               |
| Planned Billable Ratio % | Auto-calculated: Planned Billable HC ÷ Total Planned HC      |

**4.2 Client Revenue Plan**

Per client per month. Finance enters these directly — no auto-calculation from rate cards. Rate cards are used for Revenue actuals reconciliation, not for planning.

| **Input Line**                   | **Description**                                                |
|----------------------------------|----------------------------------------------------------------|
| Planned T&M Revenue (Rs L)       | Finance enters expected T&M revenue per client per month       |
| Planned Fixed-Bid Revenue (Rs L) | Finance enters expected Fixed-Bid revenue per client per month |
| Planned Total Revenue (Rs L)     | Auto-calculated: T&M + Fixed-Bid                               |

**4.3 Salary Budget**

Finance enters total planned monthly salary cost per category. Values can vary month-to-month to reflect planned increments, new hire ramp-up, or attrition assumptions.

| **Category**                  | **Description**                                                          |
|-------------------------------|--------------------------------------------------------------------------|
| Billable Staff Salaries       | Total monthly gross salary for all billable delivery staff               |
| Bench Staff Salaries          | Total monthly gross salary for bench (delivery, undeployed)              |
| Non-Billable Support Salaries | Total monthly gross salary for HR, IT, Admin, Finance                    |
| Co-Founders Salaries          | Combined monthly salary for all co-founders (group total, 4 persons)     |
| Senior Management Salaries    | Combined monthly salary for all senior managers (group total, 6 persons) |

**4.4 Overhead Budget**

23 overhead line items across 6 categories. Finance enters planned monthly amount per line. All values in Rs Lakhs.

| **Category**         | **Line Items**                                                                                                                                                                                      |
|----------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Facilities           | Office Rent, Electricity, Housekeeping Material, Internet, Postage and Courier, Printing and Stationery                                                                                             |
| Technology           | Cloud, Computer Consumables, Subscription and Software                                                                                                                                              |
| People and Welfare   | Staff Medical Insurance and Reimbursement, Staff Welfare, Recruitment, Screening                                                                                                                    |
| Travel and Transport | Travelling Expenses — Domestic, Car Expenses                                                                                                                                                        |
| Finance and Legal    | Audit and Statutory Fees, Bank and Credit Card Charges, Credit Card Expenses, Business Insurance, Professional Fees — Consultancy, Professional Fees — SW Dev (Tooling), Professional Fees — Others |
| Delivery Costs       | Training and Upskilling, Prof Fees — SW Dev (Subcontractors)                                                                                                                                        |

**5. Actuals**

| **Dimension**                      | **Source**              | **Mechanism**                                                      |
|------------------------------------|-------------------------|--------------------------------------------------------------------|
| HC Actuals (per category)          | People & Payroll        | PeriodFinalisedEvent (ADR-022) — @ApplicationModuleListener        |
| Salary Cost Actuals (per category) | People & Payroll        | PeriodFinalisedEvent — aggregated gross pay by classification      |
| Revenue Actuals (per client)       | Revenue module (future) | Placeholder: Finance enters manually until Revenue module is built |
| Overhead Actuals (per line item)   | Finance manual entry    | Finance enters actual spend per overhead line each month           |

*PeriodFinalisedEvent carries: HC counts by classification, gross pay totals by classification, and per-BU billable HC and salary cost. Budgeting & Forecasting stores these in its own period_actuals table — a copy, not a live query. This follows ADR-022's rationale: independence after the event, no ongoing runtime dependency on People & Payroll.*

**6. Calculated Outputs**

**6.1 Rolling Forecast**

Computed on demand, not stored. For each month: if a finalised People & Payroll period exists, use actuals from period_actuals; otherwise use the current ACTIVE Normal forecast version's plan inputs. Revenue follows the same pattern (Zoho Books when available, current plan otherwise). Rolling Forecast is always the most current full-year picture.

**6.2 Delta**

Delta = Rolling Forecast − current ACTIVE Normal Baseline, per month per dimension. Negative Delta = tracking below plan (bad for Revenue, good for Costs). Positive Delta = tracking above plan (good for Revenue, bad for Costs). Always computed against the current ACTIVE Normal version.

**6.3 Financial Lines**

| **Output**                   | **Formula**                                                                                |
|------------------------------|--------------------------------------------------------------------------------------------|
| Total Revenue Plan           | Sum of Planned T&M + Planned Fixed-Bid across all clients                                  |
| Total Salary Cost Plan       | Sum of all five Salary Budget categories                                                   |
| Statutory Benefits Plan      | 13% × Total Salary Cost Plan (employer PF + health insurance)                              |
| Variable Pay Plan            | 10% of Senior Staff + Leadership annual CTC, paid quarterly (Jun/Sep/Dec/Mar)              |
| Total COGS Plan              | Billable + Bench Salaries + Delivery Cost overhead lines                                   |
| Gross Profit Plan            | Total Revenue − Total COGS                                                                 |
| Gross Margin % Plan          | Gross Profit ÷ Total Revenue                                                               |
| Total OpEx Plan              | Support + Leadership Salaries + Statutory Benefits + Variable Pay + Non-Delivery Overheads |
| EBITDA Plan                  | Gross Profit − Total OpEx                                                                  |
| EBITDA Margin % Plan         | EBITDA ÷ Total Revenue                                                                     |
| Gross Margin per Client Plan | Client Revenue Plan − Allocated Salary Cost (Finance inputs per BUMetrics pattern)         |

**7. Plan vs Actual Tracking**

The primary output. For each dimension, three columns per month: Plan / Actual / Variance (Rs L and %). For Revenue and Gross Margin: positive variance = good. For Costs: negative variance = good (under-spend).

| **Dimension**                | **Plan Source**            | **Actual Source**                 | **Variance**            |
|------------------------------|----------------------------|-----------------------------------|-------------------------|
| Revenue (per client + total) | Client Revenue Plan        | Revenue module / manual           | Actual − Plan           |
| HC (per category)            | HC Plan inputs             | PeriodFinalisedEvent              | Actual − Plan (persons) |
| Billable Ratio %             | Planned HC ratio           | Actual HC ratio                   | Actual % − Plan %       |
| Salary Cost (per category)   | Salary Budget              | PeriodFinalisedEvent              | Actual − Plan           |
| Overhead (per line item)     | Overhead Budget            | Finance manual entry              | Actual − Plan           |
| Gross Margin (per client)    | Revenue − Allocated Cost   | Actual Revenue − Actual Cost      | Actual − Plan           |
| EBITDA                       | Gross Profit − OpEx (plan) | Actual Gross Profit − Actual OpEx | Actual − Plan           |

**8. General Settings Additions**

| **Setting**                     | **Description**                                     | **Default**                    |
|---------------------------------|-----------------------------------------------------|--------------------------------|
| Working Days per Month          | Standard working days used for billing calculations | 22                             |
| Annual Attrition Rate (planned) | Expected attrition % per year for HC movement       | 12%                            |
| Target Billable Ratio           | FY exit target for billable ratio                   | 70%                            |
| Opening HC per Financial Year   | Total headcount on April 1 of the financial year    | Finance enters at AOP creation |

**9. BU Analysis & Profitability**

The BU Metrics sub-section in Budgeting & Forecasting shows per-BU aggregated figures for each period, derived from PeriodFinalisedEvent actuals and Client Revenue Plan inputs. The employee-level drill-down links to People & Payroll Master Data (filtered by BU and period) rather than duplicating employee records in this module — consistent with ADR-008's cross-module boundary rule.

**9.1 Per-BU Aggregated Metrics (Plan vs Actual)**

| **Metric**                   | **Source**                                           | **Notes**                                                  |
|------------------------------|------------------------------------------------------|------------------------------------------------------------|
| Revenue                      | Client Revenue Plan (plan) / Revenue module (actual) | Per external client BU only — internal BUs have no revenue |
| Salary Cost                  | PeriodFinalisedEvent per-BU salary aggregates        | Total gross pay of all employees in that BU for the period |
| Gross Margin (Rs L)          | Revenue − Salary Cost                                | Plan vs Actual vs Variance                                 |
| Gross Margin %               | Gross Margin ÷ Revenue                               | Plan vs Actual                                             |
| Average Salary Cost per Head | BU Gross Pay ÷ BU Headcount                          | Indicates average cost of a resource in this BU            |
| Billable HC                  | PeriodFinalisedEvent per-BU billable HC              | Plan vs Actual                                             |

**9.2 Employee-Level Drill-Down**

Clicking any BU row in the BU Metrics view navigates to People & Payroll → Master Data, pre-filtered by that BU and period. This shows individual employee salary costs, classification flags, reconciliation status, and data quality flags for that BU without duplicating any data in this module. A 'Back to BU Metrics' breadcrumb returns Finance to the Budgeting & Forecasting context.

**10. Cost per Employee (Margin Baseline)**

A layered cost-per-employee analysis using Full Absorption Costing (Model 1) — all costs are ultimately allocated to billable employees, giving Finance the true loaded cost per billable head for client rate negotiation. The formula: 'I need to bill at least this much per person per month to break even; my target margin is X% above this.'

Separate cost-per-head figures are produced for each employee category (Billable, Bench, Support, Leadership). Shared overhead is allocated to billable employees only — they are the revenue generators funding all fixed costs.

**10.1 Cost per Billable Employee (the primary negotiation baseline)**

| **Layer**                           | **Components**                                                                                               | **Notes**                                                                                              |
|-------------------------------------|--------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------|
| Layer 1 — Direct Salary             | Avg Gross Pay per billable head + Statutory Benefits (13% — PF + health)                                     | From Salary Budget ÷ Billable HC (plan) or PeriodFinalisedEvent actuals ÷ actual billable HC           |
| Layer 2 — Direct Overhead           | Staff Medical Insurance, Staff Welfare, Computer Consumables, Subscription & Software, Training & Upskilling | Per-head costs: total overhead line ÷ total HC                                                         |
| Layer 3 — Allocated Shared Overhead | Office Rent, Electricity, Housekeeping, Internet, all other fixed overhead lines                             | Total fixed overhead ÷ billable HC only (full absorption — billable employees absorb all shared costs) |
| Total Cost per Billable Head        | Layer 1 + Layer 2 + Layer 3                                                                                  | The minimum billing rate to break even. Add target margin % = minimum client billing rate.             |

**10.2 Cost per Bench, Support, Leadership Employee**

| **Category** | **Layers Included**                                                     | **Notes**                                                                |
|--------------|-------------------------------------------------------------------------|--------------------------------------------------------------------------|
| Bench        | Layer 1 (salary + statutory) + Layer 2 (direct overhead)                | No Layer 3 — bench employees do not absorb shared overhead in this model |
| Support      | Layer 1 (salary + statutory) + Layer 2 (direct overhead)                | No Layer 3                                                               |
| Leadership   | Layer 1 (salary + statutory + variable pay) + Layer 2 (direct overhead) | No Layer 3                                                               |

*Variable Pay is included in Leadership Layer 1 (10% CTC quarterly) and allocated pro-rata across months. For Bench and Support, variable pay is minimal and included only if the Overhead Budget carries a variable pay line for those categories.*

**10.3 UI**

A dedicated 'Cost per Employee' sub-section in Budgeting & Forecasting. Period selector at top. Four category tabs (Billable, Bench, Support, Leadership). Each tab shows: the three layers broken out with Rs L per head per month values, a total row, and a Plan vs Actual comparison (plan uses Salary Budget inputs; actual uses PeriodFinalisedEvent salary + manually-entered overhead actuals). For Billable: an additional 'Minimum Billing Rate' row showing Layer 1 + 2 + 3 total, and a 'Target Billing Rate' field where Finance can enter their target margin % to see the resulting rate — useful for rate card validation.

**11. Navigation & UI Structure (ADR-021)**

Per ADR-021 (domain-first navigation), Budgeting & Forecasting is a top-level nav section. The analysis view is a single scrollable Dashboard (Option A) — not separate sub-sections per view. Plan Setup and Scenario Comparison remain as separate pages since they are data entry / comparison workflows, not analysis views.

| **Sub-section**     | **Purpose**                                                                                                                                                         |
|---------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Dashboard           | Single scrollable analysis page with 9 panels. Period selector + forecast type selector at top. Default: current period, Normal forecast.                           |
| Plan Setup          | Data entry workflow — create/edit financial year plan, manage forecast types and versions, enter HC / Revenue / Salary / Overhead plan inputs per forecast version. |
| Scenario Comparison | Compare any two forecast types side by side for any dimension. Separate page — needs its own side-by-side layout.                                                   |

**11.1 Dashboard Panel Order**

All panels respond to the top-level period and forecast type selectors. Panel order reflects how Finance naturally reads through the numbers — to be iterated once tested with real data.

| **Panel**                        | **Content**                                                                                                                                                        |
|----------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 1\. Headline KPIs                | Total Revenue (Plan vs Actual vs Delta), EBITDA (Plan vs Actual vs Delta), Billable Ratio (Plan vs Actual) — large Statistic cards                                 |
| 2\. Rolling Forecast vs Baseline | Revenue and EBITDA 12-month trend line — actuals shown solid, forecast dashed, baseline as reference line                                                          |
| 3\. Plan vs Actual — Revenue     | Per client: T&M + Fixed-Bid, Plan / Actual / Variance Rs L and %                                                                                                   |
| 4\. Plan vs Actual — HC          | Per category (Billable / Bench / Support / Leadership / Management): Plan / Actual / Variance persons                                                              |
| 5\. Plan vs Actual — Costs       | Salary per category + Overhead per line item: Plan / Actual / Variance Rs L                                                                                        |
| 6\. BU Metrics                   | Per client: Revenue, Salary Cost, Gross Margin, Gross Margin %, Avg Salary per Head — Plan vs Actual. Drill-down link to People & Payroll Master Data per BU.      |
| 7\. P&L Summary                  | Revenue → Gross Profit → EBITDA, monthly columns + quarterly totals + FY total, Plan vs Actual                                                                     |
| 8\. Cost per Employee            | Four category tabs (Billable / Bench / Support / Leadership). Layered cost breakdown. Billable tab includes Minimum Billing Rate + Target Billing Rate calculator. |
| 9\. Delta View                   | Rolling Forecast − Baseline for all dimensions. Traffic-light color coding: green = above plan for Revenue/Margin, red = below. Reversed for Costs.                |

**12. Non-Functional Requirements**

| **Category**       | **Requirement**                                                                                                                                                  |
|--------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Financial year     | Indian Financial Year (April–March). All period references use this convention.                                                                                  |
| Currency           | INR Lakhs throughout. USD revenue converted at FX rate from General config (ADR-017). fx_rate_id stored with every conversion record.                            |
| Immutability       | SUPERSEDED forecast versions are permanent records — never deleted. ACTIVE version is editable while in DRAFT state.                                             |
| Auditability       | Every version publish action logged (who, when). Every manual actual entry logged.                                                                               |
| Actuals precedence | PeriodFinalisedEvent actuals are authoritative for HC and Salary — Finance cannot manually override them.                                                        |
| Consistency        | Same point-in-time principle as Module 1 §6.1: period_actuals snapshot written at PeriodFinalisedEvent time ensures historical figures are exactly reproducible. |

**13. Open Items**

- Revenue actuals from Zoho Books — Revenue module not yet specced. Will consume via RevenueActualsEvent (same pattern as PeriodFinalisedEvent) once built. Finance enters manually as placeholder.

- Overhead actuals from Tally Prime — Finance enters manually for now.

- Cost per employee — Phase 2 refinement: split overhead allocation by Practice Unit (Data & AI carries higher compute costs than Product Engineering). Not needed for Phase 1.

- Tier assignment for T&M revenue calculation — schema and UI deferred to implementation phase.

- Hiring cost detailed model (BGV, screening, agency fees per joiner) — deferred.

- Employee cost per billable head layered analysis — deferred to a later phase.

- Overhead actuals from Tally Prime — Finance enters manually for now.

- Tier assignment for T&M revenue calculation — rate card × tier × headcount lives here (planning side). Schema and UI deferred to implementation phase.

- Hiring cost detailed model (BGV, screening, agency fees per joiner) — deferred to a later phase.

- Employee cost per billable head layered analysis — deferred to a later phase.

**14. Key Design Decisions — Summary**

| **Decision**            | **Choice Made**                                                                          | **Rationale**                                                                                                                                           |
|-------------------------|------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------|
| Forecast types          | NORMAL + AGGRESSIVE + CONSERVATIVE seeded; Finance can add more                          | Matches Excel Scenarios sheet; extensible without schema change                                                                                         |
| Baseline definition     | Current ACTIVE version of NORMAL forecast                                                | Delta answers 'vs our current plan' — more useful than 'vs April v1 forever' when Finance revises mid-year                                              |
| Version lifecycle       | DRAFT → ACTIVE → SUPERSEDED; one ACTIVE per forecast type at a time                      | Consistent with SUPERSEDED pattern in People & Payroll (ADR-033)                                                                                        |
| T&M Revenue Plan        | Finance enters manually per client per month                                             | Removes rate card dependency from planning; rate cards are for Revenue actuals reconciliation                                                           |
| Actuals for HC + Salary | PeriodFinalisedEvent from People & Payroll (ADR-022)                                     | Authoritative, automatic, consistent with event-driven pattern already established                                                                      |
| Overhead actuals        | Finance manual entry for now                                                             | Tally Prime integration deferred; Finance already does this in Excel                                                                                    |
| Planning assumptions    | General Settings (ADR-012)                                                               | Cross-cutting values not owned by Budgeting alone — FX rate already lives there                                                                         |
| Scenario comparison     | Any two forecast types compared side by side                                             | More flexible than Excel's fixed Base/Stress/Upside structure                                                                                           |
| BU employee drill-down  | Links to People & Payroll Master Data (Option A — no data duplication)                   | Consistent with ADR-008 cross-module boundary rule; People & Payroll already has this view                                                              |
| Cost per employee model | Full Absorption Costing (Model 1) — shared overhead allocated to billable employees only | Standard IT services pricing model; gives the true loaded cost per billable head for rate negotiation. Bench/Support/Leadership carry Layer 1 + 2 only. |

*Prepared by: Vaibhav Natu \| For: Vaibhav, Co-Founder, Cognologix Technologies \| July 2026*

*This document is confidential and intended for internal use only.*
