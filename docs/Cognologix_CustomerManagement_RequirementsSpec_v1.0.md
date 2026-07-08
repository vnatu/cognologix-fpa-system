**Cognologix Technologies**

**Financial Planning & People Analytics System**

**Requirements Specification**

**Module 2: Customer Management**

Version 1.0 (Draft — Pending Review) \| July 2026

**Document Control**

| **Field**         | **Detail**                                                                                                                                        |
|-------------------|---------------------------------------------------------------------------------------------------------------------------------------------------|
| Prepared for      | Vaibhav, Co-Founder, Cognologix Technologies                                                                                                      |
| Prepared by       | Vaibhav Natu                                                                                                                                      |
| Status            | Draft v1.0 — pending sign-off                                                                                                                     |
| Scope             | Customer Master, billing rate cards, commercial terms, concentration risk configuration, client lifecycle status, and configuration UI foundation |
| Related documents | Module 1 Requirements Specification (People & Payroll Master Data Foundation); ADR log (ADR-010, ADR-011)                                         |
| Supersedes        | Module 1 Requirements Specification, Section 9 (Client / Customer Mapping Configuration) — ownership migrated here per ADR-010                    |

**Revision History**

| **Rev** | **Date**  | **Change**                                                                                                        |
|---------|-----------|-------------------------------------------------------------------------------------------------------------------|
| 1       | July 2026 | Initial draft — Customer Master, rate cards, commercial terms, concentration risk configuration, lifecycle status |

**1. Purpose & Scope**

This document specifies Module 2: Customer Management, the second buildable module of Cognologix's Financial Planning & People Analytics platform. It elevates client/customer identity from a narrow mapping utility (originally built as Module 1, Section 9) into a proper Core bounded context, per ADR-010: Customer Management owns the client's identity, billing terms, relationship, risk profile, and lifecycle status — not just a lookup table bridging two external systems.

This module also establishes the Configuration UI foundation (ADR-011): a settings area organized by bounded context, of which this module contributes the first two sections (General, and Customer Management) alongside Module 1's existing People & Payroll configuration.

**In scope**

- Customer Master — the authoritative record for client identity, superseding Module 1 §9's Client/Customer Mapping

- Billing rate cards — both flat (blended) and tiered (by Job Level) structures, effective-dated

- Commercial terms — DSO / payment terms per client

- Concentration risk — threshold configuration only (30% single-client, combined-client flag); percentage calculation deferred (Section 8)

- Client lifecycle status — manually assigned by Finance (active / at-risk / churned / prospect)

- Relationship owner — referencing People & Payroll's Employee Registry by EmployeeID

- Configuration UI foundation — General + Customer Management sections

**Out of scope for this module**

- Zoho Books data ingestion and actual invoice/revenue processing (Revenue module)

- Concentration risk percentage calculation and alert firing — requires Revenue module's per-client revenue actuals

- Automated/derived lifecycle status — Finance assigns this manually for now, per explicit decision

- Migration of Module 1's existing implementation to consume this module's API — a build-phase task, not a new requirement, but noted in Section 5

**2. Background**

The original Module 1 design treated client identity as a narrow mapping problem: aligning Zoho People's Business Unit values with Zoho Books' Customer records, using an existing Customer Code / Project Code list as seed data. On review (ADR-010), this left billing rate, commercial terms, relationship ownership, and concentration risk — all genuine client-relationship domain knowledge already present in the old Excel system — without a proper home.

A further clarification sharpened this: Customer Code and Project Code are currently sourced from Zoho People's export, but conceptually belong to the customer, not to an HR system. Customer Management therefore becomes authoritative for these codes; Zoho People's BU Code / Project Code fields shift from being a source of truth to being validated against Customer Management's own definitions.

*For an IT services firm, client relationships and concentration risk are argued to be Core domain, not merely Supporting infrastructure (ADR-010) — this module is built with that weight, not as a simple reference-data utility.*

**3. Users & Access**

Same audience and access model as Module 1: Finance function and Management (Co-Founders), single flat role for Phase 1 (ADR-005). No new role requirements introduced by this module.

Relationship Owner is a reference to an existing Employee Registry record (Module 1 spec, Section 7.1), not a separate person record — Customer Management does not duplicate people data, it looks up EmployeeID and displays name/role from People & Payroll's registry, consistent with the identity-key principle established there (EmployeeID is the sole unique key; name is display-only).

**4. Customer Master**

The Customer Master is the authoritative record for a client's identity. It supersedes Module 1 §9's Client/Customer Mapping table — that table's fields are retained here, extended with the new attributes below.

| **Field**                     | **Description**                                                                                                       |
|-------------------------------|-----------------------------------------------------------------------------------------------------------------------|
| Customer Code                 | Authoritative identifier, owned by this module (previously sourced from Zoho People — see Section 5)                  |
| Customer Name                 | Client display name                                                                                                   |
| Zoho Books Customer Reference | External join key for the future Revenue module — strictly 1:1 (per original Module 1 §9.2 decision, carried forward) |
| Project Codes                 | One or more, nested under the client — same 1:1 mapping pattern as Module 1 §9.2                                      |
| Relationship Owner            | EmployeeID reference into People & Payroll's Employee Registry                                                        |
| Lifecycle Status              | Active / At-Risk / Churned / Prospect — manually assigned by Finance (Section 7)                                      |

**5. Identity Migration from Module 1**

Module 1 §9 was built with Customer Management's data living inside the People & Payroll module. That ownership now moves here, per ADR-010's resolution: this module becomes authoritative for Customer Code and Project Code, and People & Payroll's validation check (Module 1 §9.4 — flagging a BU value with no matching Client Master entry) is refactored to call this module's exposed lookup rather than own the table directly.

| **Aspect**                       | **Detail**                                                                                                                                                                      |
|----------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Data migration                   | Existing Customer/Project Code records built under Module 1 §9 are carried over as-is into this module's Customer Master — no data is re-entered                                |
| Module 1 refactor                | People & Payroll's BU validation (§9.4) becomes a call to Customer Management's module API (in-process, per ADR-008's Spring Modulith structure) instead of a direct table read |
| Zoho People's role going forward | BU Code / Project Code in each Zoho People snapshot are validated against this module's Customer Master, not treated as the source of the codes                                 |

**6. Billing Rate Card**

Two rate card types are supported per client, since real contracts vary — some clients are billed a single blended rate, others on a tiered structure by seniority.

| **Type** | **Structure**                                                                                                                                                |
|----------|--------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Flat     | A single blended rate (₹/person/month) applies to the whole client — matches today's Excel Assumptions sheet convention                                      |
| Tiered   | A rate per Job Level, keyed to the same Job Level values Zoho People already classifies employees by (Module 1 spec, Section 4.1) — no new taxonomy invented |

A client has exactly one active rate card at a time, of one type. Rate cards are effective-dated: a rate change creates a new dated entry rather than overwriting the prior one, consistent with the point-in-time principle established in Module 1 (Section 6.1) — a future Revenue module computing March's actual revenue must use March's rate, not whatever rate is current when the calculation runs.

**7. Commercial Terms & Lifecycle Status**

**7.1 Commercial terms**

DSO / payment terms (currently 45–60 days depending on client) are stored as a current editable value per client, tracked through the standard audit log (ADR-007) rather than a dedicated effective-dated structure — unlike the rate card, historical DSO precision hasn't been identified as a concrete requirement yet. If a future need for month-by-month historical DSO emerges (e.g. cash flow modelling in the Budgeting module), this can be elevated to an effective-dated structure at that time.

**7.2 Lifecycle status**

Active / At-Risk / Churned / Prospect, manually assigned by a Finance user — matching today's process. No automatic derivation (e.g. from declining billable headcount) is built in this phase; that was explicitly considered and deferred.

**8. Concentration Risk — Configuration Only**

This module owns the threshold configuration for concentration risk: the single-client percentage threshold (30% today) and the combined-client tracking rule (Icertis + Cadent combined, per the original System Design Context Document). Both are stored as configurable values, not hard-coded, consistent with the configurability principle established in Module 1 (Section 8.1).

*What this module does NOT do: calculate an actual concentration percentage or fire an alert. That requires per-client revenue actuals, which don't exist until the Revenue module is built. This module prepares the threshold configuration so Revenue can consume it directly once it exists, rather than reinventing threshold rules later.*

**9. Configuration UI Foundation**

Per ADR-011, the settings/configuration UI is organized by bounded context. This module contributes:

| **Section**         | **Contents**                                                                                                                           |
|---------------------|----------------------------------------------------------------------------------------------------------------------------------------|
| General             | Cross-cutting settings not owned by a single context (e.g. user accounts, until role-splitting per ADR-005 makes this richer)          |
| Customer Management | Customer Master CRUD, rate card management (flat/tiered), commercial terms, concentration risk thresholds, lifecycle status assignment |

People & Payroll's existing configuration (delivery Practice Unit list, Management/Leadership BU mapping — Module 1 §8.1) becomes a third section in the same settings area, built as part of this module's UI work even though the underlying logic already exists in Module 1.

**10. Non-Functional Requirements**

| **Category**              | **Requirement**                                                                                                                                                  |
|---------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Data volume               | Small — approximately 9 active clients today (Icertis, Cadent, Tarana, Kinesso, Nexxa AI, Uber, Wipro, Variant, Cognologix Inc), low growth rate                 |
| Data sensitivity          | Billing rates and commercial terms are confidential — same access restriction as Module 1 (Finance + Management only)                                            |
| Auditability              | Rate card changes, lifecycle status changes, and threshold configuration changes are logged via the standard audit log (ADR-007)                                 |
| Consistency with Module 1 | Reuses the EmployeeID identity-key principle, the point-in-time/effective-dating principle, and the configurability-over-hard-coding principle established there |

**11. Open Items Deferred to Later Phases**

- Concentration risk percentage calculation and alerting — blocked on Revenue module

- Automated/derived lifecycle status — deferred by explicit decision, revisit if manual assignment proves inadequate

- Revenue module: consumes this module's rate card and Customer Master directly

- Contract Management module: may eventually reference Customer Master for expiry/renewal tracking per client

**12. Key Design Decisions — Summary**

| **Decision**           | **Choice Made**                                                    | **Rationale**                                                                                                                       |
|------------------------|--------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------|
| Context classification | Customer Management promoted to Core domain (ADR-010)              | Client relationships and concentration risk are central to an IT services firm's business, not merely supporting infrastructure     |
| Identity ownership     | Migrated from Module 1 §9 to this module                           | Customer Code/Project Code conceptually belong to the customer, not to Zoho People                                                  |
| Rate card structure    | Both flat and tiered supported per client                          | Real contracts vary; tiered keys off Job Level already used in Zoho People, no new taxonomy                                         |
| Rate card history      | Effective-dated, not overwritten                                   | Matches the point-in-time principle from Module 1 §6.1 — historical revenue calculations must use the rate that applied at the time |
| Lifecycle status       | Manual, Finance-assigned                                           | Matches current process; automated derivation explicitly deferred                                                                   |
| Concentration risk     | Thresholds configured here; calculation deferred to Revenue module | Percentage calculation requires revenue actuals this module doesn't have                                                            |
| Configuration UI       | Organized by bounded context (ADR-011)                             | Mirrors the modular monolith backend structure (ADR-008); avoids an undifferentiated settings dump                                  |

*Prepared by: Vaibhav Natu \| For: Vaibhav, Co-Founder, Cognologix Technologies \| July 2026*

*This document is confidential and intended for internal use only.*
