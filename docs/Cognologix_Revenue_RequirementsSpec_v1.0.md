**Cognologix Technologies**

**Financial Planning & People Analytics System**

**Requirements Specification**

**Module 4: Revenue**

Version 1.0 (Draft — Pending Review) \| July 2026

**Document Control**

| **Field**         | **Detail**                                                                                                                                               |
|-------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------|
| Prepared for      | Vaibhav, Co-Founder, Cognologix Technologies                                                                                                             |
| Prepared by       | Vaibhav Natu                                                                                                                                             |
| Status            | Draft v1.0 — pending sign-off                                                                                                                            |
| Scope             | Zoho Books invoice and credit note import, individual invoice storage, monthly revenue summaries, Revenue Dashboard, Budgeting & Forecasting integration |
| Related documents | Module 2 (Customer Management), Module 3 (Budgeting & Forecasting), ADR log (ADR-017, ADR-019, ADR-039)                                                  |
| Supersedes        | N/A — first Revenue spec; replaces the manual revenue actuals placeholder in Budgeting & Forecasting (Module 3 §11.1)                                    |

**Revision History**

| **Rev** | **Date**  | **Change**                                                                                                                                                                                                    |
|---------|-----------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 1       | July 2026 | Initial draft — two import types (invoices + credit notes), invoice-level storage, client-level aggregation, passive payment status, Revenue Dashboard, direct query integration with Budgeting & Forecasting |

**1. Purpose & Scope**

This document specifies Module 4: Revenue — the system's source of actual invoiced revenue, replacing the manual revenue actuals placeholder currently used in Budgeting & Forecasting (Module 3 §11.1). Revenue data originates in Zoho Books (Cognologix's invoicing system) and is imported into this module via file upload for Phase 1, with direct API integration deferred to Phase 2.

The Revenue module's primary responsibility is to hold the authoritative record of what Cognologix actually invoiced and received, per client per period, so that Budgeting & Forecasting can compute Plan vs Actual revenue variance with real numbers rather than manual estimates.

**In scope**

- Two import types: ZOHO_BOOKS_INVOICES (regular invoices) and ZOHO_BOOKS_CREDIT_NOTES (credit notes that reduce revenue)

- Same column mapping template pattern as People & Payroll (ADR-019) — Finance maps Zoho Books export headers to system attributes; template saved per import type

- Manual period selection at upload time — Finance explicitly tags the period (month/year), same as People & Payroll

- Individual invoice record storage with full audit trail

- Monthly revenue summaries computed from individual records per client per period

- Passive payment status tracking (Status, Balance, Due Date) — informational only, no active collections workflow

- Credit notes stored as separate records, automatically reducing net revenue for the tagged period

- Revenue Dashboard: Revenue vs Plan per client, Invoice Status Summary, DSO informational view

- Direct query API for Budgeting & Forecasting (— no event pattern)

- Navigation per ADR-021: Revenue top-level nav section

**Out of scope**

- Zoho Books API integration — Phase 2 (same phased approach as People & Payroll)

- Project-level revenue breakdown — client-level only; project codes stored for reference but not aggregated

- Active collections/payment workflow — Zoho Books remains the system of record for collections

- T&M revenue calculation from rate cards — this module tracks what was actually invoiced, not what could theoretically be billed

- Fixed-Bid milestone tracking — Finance enters Fixed-Bid revenue in Budgeting & Forecasting plan; actuals come from Zoho Books invoices

**2. Background**

Cognologix invoices clients through Zoho Books using a mix of T&M, Fixed-Bid, and milestone-based billing. Zoho Books already tracks invoice status, payment receipts, and outstanding balances. This module imports that data rather than re-building collections functionality, consistent with the system's philosophy of using authoritative sources rather than duplicating operational workflows.

The pattern mirrors the People & Payroll import design: two import types (regular invoices + credit notes) follow the same upload flow, with credit notes playing the same role as Full & Final settlement payroll — a distinct sub-type that automatically reconciles against the primary records rather than creating noise in the exception queue.

*All monetary values in this module are stored in their original currency (USD or INR as invoiced). Conversion to INR for Budgeting & Forecasting comparisons uses the effective-dated FX rate from General configuration (ADR-017). The fx_rate_id used for any conversion is stored alongside the converted figure for full historical traceability.*

**3. Import Types & Column Mapping**

Two import types, each with its own saved column mapping template. Both follow the same 4-step upload flow established in People & Payroll: (1) period selector + file upload, (2) column mapping with template pre-fill, (3) review and confirm with unmapped/missing column warnings, (4) result summary.

**3.1 ZOHO_BOOKS_INVOICES**

Regular invoices exported from Zoho Books. Finance downloads the invoice list export from Zoho Books and uploads it here, tagging the period (month/year) manually.

| **System Attribute** | **Typical Zoho Books Header** | **Required** | **Notes**                                                           |
|----------------------|-------------------------------|--------------|---------------------------------------------------------------------|
| InvoiceNumber        | Invoice#                      | Yes          | Unique identifier — used for deduplication on re-upload             |
| CustomerCode         | Customer Code                 | Yes          | Join key to Customer Management's customer table                    |
| CustomerName         | Customer Name                 | No           | Reference only — CustomerCode is authoritative                      |
| InvoiceDate          | Invoice Date                  | Yes          | Stored for audit and DSO calculation                                |
| Status               | Status                        | Yes          | Paid / Partially Paid / Sent / Overdue / Void                       |
| Amount               | Total                         | Yes          | Invoice amount in original currency                                 |
| Balance              | Balance                       | No           | Outstanding amount — used for DSO informational display             |
| DueDate              | Due Date                      | No           | Used for overdue flag calculation                                   |
| Currency             | Currency                      | No           | USD or INR — defaults to customer's billing currency if not present |
| ProjectCode          | Project-Code                  | No           | Stored for reference only — not used in revenue aggregation         |

*Invoice Month / Service Month / Service Year fields in Zoho Books exports are unreliable (Invoice Month is deprecated; Service Month/Year are custom fields not always present). Finance tags the period manually at upload time — consistent with People & Payroll's period selection pattern.*

**3.2 ZOHO_BOOKS_CREDIT_NOTES**

Credit notes exported from Zoho Books as a separate export. Credit notes reduce net revenue for the tagged period. Same upload flow as invoices. Finance tags the period manually.

| **System Attribute** | **Typical Zoho Books Header** | **Required** | **Notes**                                                                               |
|----------------------|-------------------------------|--------------|-----------------------------------------------------------------------------------------|
| CreditNoteNumber     | Credit Note#                  | Yes          | Unique identifier                                                                       |
| CustomerCode         | Customer Code                 | Yes          | Join key to Customer Management                                                         |
| CustomerName         | Customer Name                 | No           | Reference only                                                                          |
| CreditNoteDate       | Credit Note Date              | Yes          | Stored for audit                                                                        |
| Status               | Status                        | Yes          | Open / Closed / Void                                                                    |
| Amount               | Total                         | Yes          | Credit note amount — stored as positive, treated as negative in net revenue calculation |
| Currency             | Currency                      | No           | USD or INR                                                                              |

Credit notes are stored as separate records (not merged with invoices) so Finance can see the full audit trail of what was invoiced and what was credited independently. Net Revenue per client per period = Sum of Invoice Amounts − Sum of Credit Note Amounts for that customer and period.

**4. Data Model**

**4.1 Revenue Import Upload (audit trail)**

One record per file upload. Same pattern as \`snapshot_upload\` in People & Payroll — tracks who uploaded what, when, for which period. Re-uploading the same import type for the same period replaces existing records (same SUPERSEDED version-bump pattern as People & Payroll — see ADR-033).

**4.2 Invoice Record**

| **Field**                 | **Notes**                                                        |
|---------------------------|------------------------------------------------------------------|
| id                        | UUID PK                                                          |
| revenue_upload_id         | FK to upload audit record                                        |
| period_month, period_year | Finance-tagged period — not derived from invoice date            |
| invoice_number            | Unique within a period upload                                    |
| customer_id               | Soft reference to customer (cross-module, no FK)                 |
| invoice_date              | Date on the invoice                                              |
| status                    | Paid / Partially Paid / Sent / Overdue / Void                    |
| amount                    | Invoice amount in original currency                              |
| balance                   | Outstanding amount (nullable)                                    |
| due_date                  | Due date (nullable)                                              |
| currency                  | USD or INR                                                       |
| project_code              | Reference only (nullable)                                        |
| fx_rate_id                | FK to fx_rate — set when amount is converted to INR (ADR-017)    |
| amount_inr                | Converted amount in INR (nullable — populated if currency = USD) |

**4.3 Credit Note Record**

Same structure as Invoice Record but with \`credit_note_number\` instead of \`invoice_number\`, \`credit_note_date\` instead of \`invoice_date\`. Amount stored as positive; treated as negative in all net revenue calculations.

**4.4 Revenue Period Summary (computed, not stored)**

Computed on demand from invoice and credit note records. Not stored as a separate table — always reflects the current state of uploaded records for that period. Formula: Net Revenue = Sum(invoice.amount) − Sum(credit_note.amount) per customer per period. INR equivalent: same formula using amount_inr fields.

**5. Re-upload Behaviour**

Same SUPERSEDED version-bump pattern as People & Payroll (ADR-033): re-uploading the same import type for the same period creates a new upload version, marks the prior version SUPERSEDED. Net Revenue summary always computed from the latest non-superseded upload per import type per period. Finance can view superseded upload history for audit purposes.

**6. Budgeting & Forecasting Integration**

Direct query pattern — no event (ADR-039 rationale: revenue actuals are not 'finalised' the same way People & Payroll periods are; invoices can be corrected after upload without needing to republish an event).

RevenueService exposes: \`getMonthlyRevenueSummary(customerId, month, year)\` — returns net invoiced revenue (invoices − credit notes) for that client and period, in both original currency and INR equivalent. Budgeting & Forecasting calls this in-process (Spring Modulith — ADR-008) when computing Plan vs Actual revenue, replacing the \`actual_revenue_manual\` placeholder.

RevenueService also exposes: \`getAllClientsMonthlyRevenue(month, year)\` — returns net revenue for all clients for a period, used by Budgeting & Forecasting's BU Metrics panel to populate actual revenue per client row without making N separate calls.

**7. Revenue Dashboard**

Per ADR-021, Revenue is a top-level nav section with three sub-sections:

| **Sub-section**   | **Purpose**                                                                                                                                                                                                                                                                                                                                                                        |
|-------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Imports           | Upload Zoho Books invoice and credit note files. Same 4-step flow as People & Payroll imports. Column mapping template management (inline + Settings canonical home).                                                                                                                                                                                                              |
| Invoice List      | Paginated, searchable, filterable list of all invoice and credit note records. Filters: client, period, status, import type. Columns: Invoice#, Client, Period, Invoice Date, Amount, Currency, INR Equivalent, Status, Balance, Due Date. Export to Excel.                                                                                                                        |
| Revenue Dashboard | Three panels: (1) Revenue vs Plan per client per period — calls Budgeting & Forecasting’s Plan vs Actual API and Revenue module’s actual summary; (2) Invoice Status Summary — Paid/Partially Paid/Sent/Overdue counts and amounts for selected period; (3) DSO Informational — avg days outstanding per client (Due Date − Invoice Date for unpaid invoices), informational only. |

**8. Settings — Revenue Configuration**

Settings → Revenue tab (per ADR-011): column mapping template management for ZOHO_BOOKS_INVOICES and ZOHO_BOOKS_CREDIT_NOTES. Same dual-surface pattern as People & Payroll — canonical home in Settings, surfaced contextually in the Import screen.

**9. Non-Functional Requirements**

| **Category**          | **Requirement**                                                                                                                                                                                         |
|-----------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Currency              | Invoice amounts stored in original currency (USD or INR). INR equivalent computed at upload time using the effective FX rate from General config (ADR-017). fx_rate_id stored on each converted record. |
| Auditability          | Every upload logged (who, when, file, period, record count). SUPERSEDED uploads retained permanently.                                                                                                   |
| Deduplication         | Invoice Number used for deduplication within a period upload. Duplicate Invoice Numbers in the same upload flagged as warnings.                                                                         |
| Cross-module boundary | No FK references into Customer Management tables. CustomerCode stored as VARCHAR soft reference. CustomerService.isKnownCustomer() called at upload for validation.                                     |
| Phase 2               | Zoho Books API integration replaces file upload. Same data model — API adapter populates the same invoice and credit note tables without schema changes.                                                |

**10. Key Design Decisions — Summary**

| **Decision**          | **Choice Made**                                          | **Rationale**                                                                                                                                                   |
|-----------------------|----------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Two import types      | ZOHO_BOOKS_INVOICES + ZOHO_BOOKS_CREDIT_NOTES (separate) | Mirrors ZOHO_PAYROLL + ZOHO_PAYROLL_FNF pattern — credit notes are a distinct record type with different semantics, not just negative invoices in the same file |
| Period attribution    | Finance tags period manually at upload                   | Invoice Month deprecated in Zoho Books; Service Month/Year custom fields not always present; manual tagging is reliable and consistent with People & Payroll    |
| Revenue granularity   | Invoice level stored + monthly summaries computed        | Full audit trail; summaries always reflect current state without stale stored aggregates                                                                        |
| Project code          | Stored for reference, not aggregated                     | Multiple projects share one invoice; project-level revenue breakdown deferred to Phase 2                                                                        |
| Payment tracking      | Passive import of Status + Balance — no active workflow  | Zoho Books is the system of record for collections; passive import gives DSO visibility without duplicating operational workflow                                |
| Budgeting integration | Direct query, no event                                   | Revenue actuals can be corrected after upload; events would require republishing on every correction                                                            |
| Re-upload             | SUPERSEDED version-bump (ADR-033)                        | Consistent with People & Payroll re-upload behaviour; prior uploads retained for audit                                                                          |

*Prepared by: Vaibhav Natu \| For: Vaibhav, Co-Founder, Cognologix Technologies \| July 2026*

*This document is confidential and intended for internal use only.*
