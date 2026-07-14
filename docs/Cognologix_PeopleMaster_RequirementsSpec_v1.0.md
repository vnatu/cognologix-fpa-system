**Cognologix Technologies**

**Financial Planning & People Analytics System**

**Requirements Specification**

**Module 1: People & Payroll Master Data Foundation**

Version 1.0 (Draft — Pending Review) \| July 2026

**Document Control**

| **Field**        | **Detail**                                                                              |
|------------------|-----------------------------------------------------------------------------------------|
| Prepared for     | Vaibhav, Co-Founder, Cognologix Technologies                                            |
| Prepared by      | Vaibhav Natu                                                                            |
| Status           | Draft v1.0 — pending sign-off                                                           |
| Scope            | Pre-requisite data foundation only: Payroll + Zoho People → Master → People Dashboard   |
| Related document | Cognologix System Design Context Document, v1.0, June 2026                              |
| Supersedes       | N/A — first system requirements spec; existing Excel workbooks remain live during build |

**Revision History**

| **Rev** | **Date**  | **Change**                                                                                                                                                                                                                                                                                        |
|---------|-----------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 1       | July 2026 | Initial draft — ingestion, snapshotting, classification, dashboard requirements                                                                                                                                                                                                                   |
| 2       | July 2026 | Reconciliation model extended: persistent employee registry, manual mapping for unmatched payroll, automatic handling of exited-employee settlement lag                                                                                                                                           |
| 3       | July 2026 | Added Client / Customer Mapping Configuration (Section 9) — Zoho People BU ↔ Zoho Books Customer + Project Code mapping, as a foundation for the future Revenue module                                                                                                                            |
| 4       | July 2026 | Exited Employees export confirmed to mirror primary Zoho People format (Section 4.3). Added explicit EmployeeID-only identity key principle and disambiguation requirement for manual mapping (Section 7), since employee names are not unique                                                    |
| 5       | July 2026 | Section 9 (Client / Customer Mapping Configuration) superseded — ownership migrated to the new Customer Management module (Module 2) per ADR-010/ADR-011. Section 9 retained below for historical context only; see the Module 2 Requirements Specification for the current, authoritative design |

**1. Purpose & Scope**

This document specifies the requirements for the first buildable module of Cognologix's new Financial Planning & People Analytics platform: the People & Payroll Master Data Foundation. This module replaces the manual-paste workflow currently performed in the Cognologix_PeopleData.xlsx workbook (ZohoPeople sheet, Payroll sheet, Master sheet, Dashboard sheet) with a persisted, versioned, system-of-record equivalent.

This is deliberately scoped as a pre-requisite layer. It does not cover the AOP/budgeting model, BU-level P&L, contract management, or scenario planning — those are downstream consumers of the Master data this module produces, and will be specified separately once this foundation is agreed.

**In scope**

- Ingestion of monthly Zoho People and Zoho Payroll exports via file upload

- Point-in-time snapshot storage of both sources, retained permanently

- A joined Master record per employee per period, with classification flags

- Reconciliation and exception handling between the two sources

- The People Dashboard — headcount, PU/BU breakdown, salary metrics, trends

- A single flat access role for Phase 1

**Out of scope for this module**

- Direct API integration with Zoho People / Zoho Payroll (Phase 2)

- AOP model integration, BU-level P&L, scenario planning

- Multi-role / granular permissions (deferred until a concrete need arises)

- Contract management, alerts & notifications

- Zoho Books data ingestion and revenue calculation — only the Client Master mapping foundation is built here (Section 9)

**2. Background**

Cognologix currently runs two separate Excel workbooks: an AOP Model and a People & Payroll Dashboard, connected by a monthly manual copy-paste workflow. Both are functionally mature and encode real institutional logic — the classification rules, salary bucketing priority, and reconciliation conventions in this spec are drawn directly from that existing system, not invented fresh. The intent, per the Cognologix System Design Context Document (June 2026), is to migrate toward a proper web-based platform, starting with the data foundation this document covers.

*Design principle carried forward from the Excel system: build only what the current scale needs. This module intentionally avoids introducing complexity (e.g. granular RBAC, real-time API sync) that isn't yet justified by a concrete requirement.*

**3. Users & Access**

This system is an internal tool for the Finance function and Management (Co-Founders). It is not intended for BU Managers or broader staff access at this stage — that was explicitly narrowed during requirements discovery, in contrast to the original wider audience contemplated for the eventual platform.

| **Aspect**           | **Decision**                                                                                                            |
|----------------------|-------------------------------------------------------------------------------------------------------------------------|
| Phase 1 access model | Single flat role — any authenticated user has full read/write access                                                    |
| Expected user count  | Handful of named users (CEO, co-founders, Finance Head)                                                                 |
| Future extensibility | Architecture must allow splitting into Finance (edit) vs Management (view-only) roles later without a data model change |
| Salary visibility    | Full visibility for all users in Phase 1 (no BU Manager audience to restrict from)                                      |

**4. Source Systems & Data Contracts**

**4.1 Zoho People — source of truth for employee existence & organisational attributes**

Zoho People is authoritative for whether an employee exists and what their current organisational assignment is. New employees are onboarded in Zoho People first — they may not appear in Zoho Payroll for one or more months after joining. This asymmetry is a first-class rule in this system, not an edge case to work around (see Section 7).

| **Column**               | **Description**                                          |
|--------------------------|----------------------------------------------------------|
| EmployeeID               | Unique identifier — join key with Payroll                |
| First Name, Last Name    | Employee name                                            |
| Practice Unit            | Product Engineering / DevOps / Data & AI / Emerging Tech |
| Business Unit            | Client name, or Management / Leadership                  |
| Billable Status          | Y = billable to client, N = not billable                 |
| Job Level, Job Sub Level | Seniority classification                                 |
| Title, BU Head, Sub PU   | Role descriptors                                         |
| Date of Joining          | Tenure / attrition analysis                              |
| BU Code, Project Code    | Granular tracking — captured but not used at model level |
| Cognologix Experience    | Tenure at Cognologix                                     |

**4.2 Zoho Payroll — source of truth for compensation actuals**

Zoho Payroll is authoritative for what was actually paid. It is exported monthly as an Excel sheet — this is a manual export today and will remain file-based for this phase, per the Phase 1 ingestion decision in Section 5.

| **Column**               | **Description**                                                   |
|--------------------------|-------------------------------------------------------------------|
| Period                   | Month of payroll, e.g. ‘March 2026’                               |
| Employee No              | Join key — must match EmployeeID in Zoho People                   |
| Employee Name            | Reference only                                                    |
| Department, Designation  | Reference only — Zoho People is authoritative for role data       |
| CTC Amount (Per Annum)   | Annual CTC from salary structure — used for budgeting only        |
| Gross Amount (Per Annum) | Currently ignored                                                 |
| Gross Pay                | Actual monthly gross paid — PRIMARY salary metric used throughout |
| Net Pay                  | Take-home — captured but not shown on dashboard                   |

*Gross Pay, not CTC, is the company-expense figure driving all salary metrics. CTC Per Annum is retained for budgeting comparisons only — this mirrors the existing Excel convention exactly.*

**4.3 Zoho People — Exited Employees Export (supplementary)**

Zoho People also offers an Exited Employees export in the same column structure as the primary export (Section 4.1), filtered to employees who have left. It carries the same EmployeeID join key, with Exit Date / Last Working Day as the field of interest for this module.

This export is a data-quality enrichment, not a replacement for the inference mechanism: absence-based ‘Exited’ flagging remains the primary signal and applies immediately, without waiting on this export to arrive. When available, it backfills the precise exit date on the same Employee Registry record (Section 7.1), upgrading month-level precision to day-level for cost proration and reporting.

**5. Ingestion Requirements**

Phase 1 ingestion is file-based: a finance user uploads the monthly Zoho People export and the monthly Zoho Payroll export as Excel or CSV files through a web form. Direct API integration is explicitly deferred to a later phase (see Section 12) — the data contracts above are written so that a future API adapter can populate the same Master model without a redesign.

| **Requirement**           | **Detail**                                                                                                                                      |
|---------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------|
| Upload target             | Two independent uploads per period: Zoho People export, Zoho Payroll export                                                                     |
| Period tagging            | Each upload is explicitly tagged with its reporting month (not inferred from filename)                                                          |
| Validation on upload      | Column headers must match the expected contract (Section 4); reject with a clear error if columns are missing/renamed                           |
| Duplicate period handling | Re-uploading a period replaces that period's snapshot only — does not touch other periods (see Section 6 for immutability of finalised periods) |
| Row-level validation      | Duplicate EmployeeID / Employee No within a single upload is flagged, not silently deduplicated                                                 |

**6. Historical Data & Point-in-Time Snapshots**

This is the most significant architectural departure from the Excel system, where each month's paste silently overwrote the last. In the new system, every monthly export from both sources is persisted permanently as an immutable snapshot.

**6.1 Why Zoho People must be snapshotted too**

Zoho People reflects only current state — it has no concept of history. If the system only stored current organisational attributes and re-joined them against historical Payroll amounts, a client move (e.g. an employee moving from Icertis to Cadent) would silently rewrite history: reopening March's dashboard six months later would show the employee under their new client, not the client they were actually billed against in March. For a finance system, this is not acceptable — every historical figure must be reproducible exactly as it was reported at the time.

Therefore, both Zoho People and Zoho Payroll are snapshotted per period. The Master record for any given month is built exclusively from that month's two snapshots — never from “current” data reaching backward.

**6.2 Immutability & correction**

| **Rule**                             | **Detail**                                                                                                  |
|--------------------------------------|-------------------------------------------------------------------------------------------------------------|
| Finalised periods are immutable      | Once a period is marked finalised, its snapshots and Master records cannot be silently overwritten          |
| Correction requires explicit re-open | Correcting a finalised period requires an explicit ‘reopen period’ action, which is logged (who, when, why) |
| Current/in-progress period           | The most recent period can be freely re-uploaded until explicitly finalised                                 |
| Audit trail                          | Every snapshot upload and every period reopen is logged with user, timestamp, and file reference            |

**7. Master Record & Reconciliation**

The Master record is the joined, per-employee, per-period view produced by combining that period's Zoho People snapshot with that period's Zoho Payroll snapshot. Because Zoho People is authoritative for existence and Zoho Payroll is authoritative for compensation, the two sources are not treated symmetrically when they disagree.

*Identity key principle: EmployeeID (= Payroll's Employee No) is the sole unique key for matching, deduplication, and registry lookup, everywhere in the system. Name is a display attribute only and is never used to resolve or infer identity — Cognologix has employees who share names, so name-based matching would silently mismatch records. This applies to auto-match logic (Section 7.2) and, critically, to the manual mapping screen (Section 7.3), which must disambiguate by more than name alone.*

**7.1 Persistent Employee Registry**

Matching is not limited to the current period's Zoho People snapshot. The system maintains a persistent Employee Registry — every EmployeeID ever seen across all Zoho People snapshots, from the first period onward. This registry is what makes exited-employee settlement lag resolvable automatically rather than a dead-end exception: an employee who has left still exists in the registry even after they disappear from the current Zoho People export.

Each registry entry carries an Exit Date field, initially null. It is set to a best-effort month-level value the moment absence-based inference flags the employee 'Exited' (Section 7.2), and upgraded to a precise day-level date if a Zoho People Exited Employees export (Section 4.3) later confirms it.

**7.2 Auto-match logic**

- A Payroll row auto-matches if its Employee No equals an EmployeeID in the current period's Zoho People snapshot — standard case, employee is active.

- A Payroll row auto-matches against the Employee Registry (not just the current snapshot) if its Employee No equals an EmployeeID seen in any earlier period, but the employee is absent from the current period's Zoho People snapshot — this is the exited-employee case (e.g. full-and-final settlement processed a month or two after the employee's last active period, or exit date). The system marks the employee ‘Exited’ as of their last known active period, retains the salary amount attributed to them, but excludes them from current-period headcount.

- A Payroll row that matches neither the current snapshot nor the Employee Registry is Unmatched — this is the new-joiner case, typically because Payroll's Employee No hasn't yet been aligned with Zoho People's EmployeeID for a recent hire, or a genuine data entry mismatch.

**7.3 Manual reconciliation mapping**

For Unmatched Payroll rows, the reconciliation screen lets a Finance user search for and select the correct Zoho People employee record and map the Payroll row to it directly, rather than leaving it as a permanent orphan. Once a mapping is confirmed, it is stored as a persistent alternate-ID link for that employee, so future periods for the same person auto-match without requiring the mapping to be repeated.

Because names are not unique, the search/select UI must always display EmployeeID alongside disambiguating attributes (Practice Unit, Business Unit, Date of Joining) for every candidate match — never a bare name list. The confirmed mapping is stored against EmployeeID, never against name.

| **Case**                                                                            | **Treatment**                                                                                                                                                                                                                                                                            |
|-------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Employee in Zoho People, missing from Payroll                                       | Included in Master and counted in headcount (they exist, per the source of truth). Salary fields are null. Flagged ‘Payroll Pending’. Excluded from salary-average calculations (so a null doesn't distort an average as a false zero) but included in headcount and billability counts. |
| Employee in Payroll, matches Employee Registry but not current Zoho People snapshot | Auto-matched as ‘Exited’. Salary retained and attributed to the employee (so Finance doesn't lose the expense line, including delayed full-and-final settlements). Excluded from current-period headcount. No manual step required.                                                      |
| Employee in Payroll, matches neither current snapshot nor Employee Registry         | Unmatched Payroll exception. Surfaced on the reconciliation screen for manual mapping to the correct employee (Section 7.3). Excluded from headcount until mapped.                                                                                                                       |
| Employee in both, values agree                                                      | Standard Master record — no flag                                                                                                                                                                                                                                                         |

**7.4 Reconciliation screen**

A dedicated exceptions view lists, for the current period: all Payroll Pending employees (informational — resolves itself once next payroll run includes them), all auto-matched Exited employees (informational — confirms settlement was correctly attributed), and all Unmatched Payroll rows requiring manual mapping action. This is where Finance resolves the month's discrepancies rather than having them silently absorbed into totals.

**8. Employee Classification Logic**

Every employee, in every period, is classified into exactly one of five mutually exclusive buckets, computed from that period's Zoho People snapshot. This logic is carried forward unchanged from the existing Excel system.

| **Flag**     | **Rule**                                                               | **Notes**                                      |
|--------------|------------------------------------------------------------------------|------------------------------------------------|
| IsDeliveryPU | PU = Product Engineering OR DevOps OR Data & AI                        | Identifies delivery vs support function        |
| IsBillable   | Billable Status=Y                                                        | Leadership members with Billable Status=Y are correctly classified as both IsLeadership=true and IsBillable=true simultaneously. Salary bucketing priority rule (Leadership bucket wins) remains unchanged. |
| IsBench      | IsDeliveryPU=Y + Billable Status=N + BU ≠ Management + BU ≠ Leadership | Delivery resources without an active client    |
| IsSupport    | IsDeliveryPU=N + BU ≠ Management + BU ≠ Leadership                     | HR, IT, Admin, Finance                         |
| IsLeadership | BU = Leadership                                                        | Senior Management (6 persons)                  |
| IsManagement | BU = Management                                                        | Co-Founders only (4 persons)                   |

Salary bucketing priority rule: Leadership salary always goes into the Leadership bucket even if that person is also billable, preventing double-counting.

**8.1 Configurability**

The lists that drive these rules — which Practice Units count as ‘delivery’, which Business Unit values map to Management/Leadership — should be stored as system configuration (an admin-maintained reference list), not hard-coded, so that organisational changes (e.g. a new Practice Unit) don't require a code change.

**9. Client / Customer Mapping Configuration**

*SUPERSEDED (Rev 5): Ownership of this capability has moved to the Customer Management module (Module 2), per ADR-010 and ADR-011. This section is retained for historical context only — see the Module 2 Requirements Specification for the current, authoritative design, including the expanded scope (billing rate cards, commercial terms, concentration risk configuration, lifecycle status) this section did not cover.*

Cognologix invoices clients through Zoho Books, while Zoho People tracks clients as Business Units (BU = client name). These are two independently maintained identities for the same real-world client, and nothing today guarantees they stay in sync. This module builds the mapping foundation that a future Revenue module (Zoho Books integration) will depend on — it does not ingest Zoho Books data itself; that ingestion is explicitly out of scope here (Section 12).

**9.1 Purpose**

Establish a single Client Master reference that links a Zoho People Business Unit to its corresponding Zoho Books Customer, and each Zoho People Project Code to its corresponding Zoho Books project reference. This gives the future Revenue module a ready-made, trustworthy join key on day one, and gives this module a way to validate that BU values entered in Zoho People are recognised, real clients rather than typos or one-off entries.

**9.2 Structure**

| **Level** | **Mapping**                                                                  | **Cardinality**                                                                     |
|-----------|------------------------------------------------------------------------------|-------------------------------------------------------------------------------------|
| Client    | Zoho People BU (client name + BU Code) ↔ Zoho Books Customer (Customer Code) | Strictly 1:1                                                                        |
| Project   | Zoho People Project Code ↔ Zoho Books project reference                      | Each Project Code maps to one Zoho Books reference, nested under its client mapping |

**9.3 Seed data**

An existing Customer Code / Project Code list is already maintained outside the system today. This is imported once as seed data at build time, then maintained going forward through the system itself (add, edit, deactivate mappings) rather than re-imported on a recurring basis.

**9.4 Data quality tie-in**

Once the Client Master exists, BU values arriving in each period's Zoho People snapshot can be validated against it. A BU value with no corresponding Client Master entry is a configuration gap worth surfacing — the same spirit as the reconciliation exceptions in Section 7, applied to client identity rather than employee identity. This is a natural extension for the reconciliation screen, not a separate feature.

This mapping is deliberately owned as shared reference data — both this module and the future Revenue module read from the same Client Master, rather than each maintaining its own copy of ‘what a client is called.’

**10. People Dashboard Requirements**

The dashboard reproduces and extends the existing PeopleData workbook's Dashboard sheet, now computed per selected period with the ability to view trends across periods.

| **Section**               | **Key Metrics**                                                                                                |
|---------------------------|----------------------------------------------------------------------------------------------------------------|
| Headcount Summary         | Total HC, Billable, Bench, Support, Leadership, Management, Billable Ratio %                                   |
| PU Breakdown              | Per PU: Total HC, Billable HC, Bench HC, Billable %, Bench %, Gross Pay (Total/Billable/Bench)                 |
| BU / Client Breakdown     | Per client: Total HC, Billable HC, Bench HC, Billability %, Non-Billable HC, Total Salary                      |
| Salary Metrics            | Total gross pay split by Billable/Bench/Support/Leadership/Management + avg per head                           |
| Reconciliation Exceptions | Payroll Pending count, Auto-Matched Exited count, Unmatched Payroll count (requiring mapping), with drill-down |
| Trend View (new)          | Any of the above metrics plotted month-over-month using persisted snapshot history                             |
| Period Selector           | Choose any historical period; dashboard recomputes from that period's snapshots only                           |

**11. Non-Functional Requirements**

| **Category**                 | **Requirement**                                                                            |
|------------------------------|--------------------------------------------------------------------------------------------|
| Data volume                  | ~100–300 employee records per period; monthly cadence                                      |
| Data sensitivity             | Salary data is confidential; access restricted to the Phase 1 user group (Section 3)       |
| Auditability                 | Every upload, reconciliation resolution, and period reopen is attributable and timestamped |
| Correctness over convenience | Reconciliation exceptions are surfaced, never silently guessed (Section 7)                 |
| Extensibility                | Data model supports future API ingestion and future role granularity without redesign      |

**12. Open Items Deferred to Later Phases**

- Direct API integration with Zoho People and Zoho Payroll

- Zoho People Exited Employees export — exact column structure to be confirmed once obtained (Section 4.3)

- Revenue module: Zoho Books ingestion and revenue calculation, built on top of the Client Master mapping established in Section 9

- Role split: Finance (edit) vs Management (view-only), if/when needed

- Integration with the AOP Model (feeding Salary Cost per BU, PU splits into budgeting)

- Alerts & notifications (e.g. concentration risk, billability drop)

- Contract management integration

- BU-level P&L with drill-down

**13. Key Design Decisions — Summary**

| **Decision**                  | **Choice Made**                                                                                                                                              | **Rationale**                                                                                                                                                            |
|-------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Ingestion (Phase 1)           | File upload via web form                                                                                                                                     | API integration is a real lift; not required to get a working system                                                                                                     |
| Historical retention          | Full permanent snapshot, every period                                                                                                                        | Enables trend analysis; avoids silent rewriting of history                                                                                                               |
| Zoho People snapshotting      | Snapshotted per period, not just Payroll                                                                                                                     | Zoho People has no native history; org attributes must be point-in-time too                                                                                              |
| Source of truth for existence | Zoho People                                                                                                                                                  | Onboarding happens there first; Payroll lags by design                                                                                                                   |
| Reconciliation treatment      | Asymmetric — Payroll Pending included in HC; exited-employee settlements auto-matched via Employee Registry; genuinely unmatched rows require manual mapping | Matches which system is authoritative for what; avoids permanent orphan exceptions for known real-world scenarios (new joiner ID lag, delayed full-and-final settlement) |
| Access model (Phase 1)        | Single flat role                                                                                                                                             | No concrete need yet for role separation; audience is small and trusted                                                                                                  |
| Audience                      | Finance + Management only                                                                                                                                    | Narrowed from the original broader platform vision during discovery                                                                                                      |
| Client Master mapping         | Strictly 1:1 at client level, Project Code included, seeded from existing Customer/Project Code list                                                         | Prepares the join key the future Revenue module needs, without pulling Zoho Books data into this module                                                                  |

**14. Technical Design — Explicitly Deferred**

This document specifies requirements only — what the system must do, not how it is built. Architectural patterns for the implementation (bounded context boundaries, CQRS, event sourcing for the snapshot/history model, aggregate design) are open for consideration and debate once this requirements spec is signed off, as a separate technical design exercise. Nothing in this document should be read as committing to a specific implementation pattern.

*Prepared by: Claude (Anthropic) \| For: Vaibhav, CEO & Co-Founder, Cognologix Technologies \| July 2026*

*This document is confidential and intended for internal use only.*
