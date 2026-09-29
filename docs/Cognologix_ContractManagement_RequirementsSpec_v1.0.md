**Cognologix Technologies**

**Financial Planning & People Analytics System**

**Requirements Specification**

**Module 6: Contract Management**

Version 1.0 (Draft — Pending Review) \| September 2026

**Document Control**

| **Field**         | **Detail**                                                                                        |
|-------------------|---------------------------------------------------------------------------------------------------|
| Prepared for      | Vaibhav, Co-Founder, Cognologix Technologies                                                      |
| Prepared by       | Vaibhav Natu                                                                                      |
| Status            | Draft v1.0 — pending sign-off                                                                     |
| Scope             | Client contract repository — upload, versioning, expiry tracking, notifications, template library |
| Related documents | Module 2 (Customer Management), ADR log                                                           |
| Supersedes        | N/A — first Contract Management spec                                                              |

**Revision History**

| **Rev** | **Date**       | **Change**                                                                                                                                          |
|---------|----------------|-----------------------------------------------------------------------------------------------------------------------------------------------------|
| 1       | September 2026 | Initial draft — contract repository, paper type (Third Party/Own), versioning with manual status, expiry notifications, template library, dashboard |

**1. Purpose & Scope**

This document specifies Module 6: Contract Management — a lightweight contract repository integrated into the FPA system. The goal is to maintain all client contracts (NDAs, MSAs, SOWs, and other agreement types) in one place with version history, expiry tracking, and timely notifications — without the complexity of a full CLM system.

Cognologix signs both Third Party paper (customer's template) and Own paper (Cognologix's template). In both cases, the signed document is uploaded and tracked. For Own paper, multiple draft versions may be uploaded as the contract goes through back-and-forth review before signing. The system maintains all versions without imposing an automated workflow.

**In scope**

- Contract repository: upload, store, and track client contracts

- Paper type: Third Party (customer's template) or Own (Cognologix's template)

- Fully configurable contract types: NDA, MSA, SOW seeded; Finance/Admin can add more

- Loose parent association: SOW can reference a parent MSA

- Document versioning with manual status (Draft, Under Review, Signed, Superseded)

- Primary documents: PDF and Word (.docx). Supporting documents: any file type

- All documents stored as binary blobs in PostgreSQL

- Optional financial terms per contract: contract value, billing currency, payment terms

- Expiry tracking: system default reminder days (configurable) + per-contract override

- Notifications: in-app + email to contract owner + configured recipients

- Template library: upload and download contract templates by type

- Dashboard: expiring soon, recently added, contracts by type breakdown

- Simple search and filter: by client, contract type, status, expiry date range

**Out of scope**

- Automated contract lifecycle workflow (no Draft → Review → Signed progression)

- Signatory tracking (who signed on behalf of each party)

- E-signature integration

- Vendor or internal contracts (employee NDAs, partner agreements)

- Placeholder filling in templates

- OCR or full-text search within document content

- Contract obligation tracking or milestone management

**2. Background**

Cognologix currently maintains contracts in a shared folder with no structured tracking. Contracts approach expiry without advance warning, locating the latest signed version of a specific agreement requires manual searching, and there is no visibility into the overall contract portfolio. This module provides the minimum needed: a searchable repository with expiry alerts.

The design is deliberately lean. Two scenarios cover all current needs: (1) Third Party paper — customer sends their template, Cognologix signs it, upload the signed PDF; (2) Own paper — Cognologix sends its template, customer redlines, back-and-forth until signed. The versioning model handles both without an automated workflow.

**3. Data Model**

**3.1 Contract**

| **Field**              | **Description**                                                | **Required** |
|------------------------|----------------------------------------------------------------|--------------|
| id                     | UUID primary key                                               | Yes          |
| contract_number        | Auto-generated reference (e.g. CON-2026-001)                   | Yes          |
| title                  | Descriptive title (e.g. 'Icertis NDA 2026')                    | Yes          |
| contract_type_id       | FK to contract_type (NDA, MSA, SOW etc.)                       | Yes          |
| paper_type             | THIRD_PARTY or OWN                                             | Yes          |
| customer_id            | Soft reference to FPA customer (nullable)                      | No           |
| party_name             | Free-text party name if customer not in FPA                    | No           |
| effective_date         | Contract start date                                            | No           |
| expiry_date            | Contract end date — drives notifications                       | No           |
| is_evergreen           | No fixed expiry (auto-renewing contracts)                      | No           |
| status                 | DRAFT / ACTIVE / EXPIRED / TERMINATED                          | Yes          |
| parent_contract_id     | Loose association to parent contract (e.g. SOW → MSA)          | No           |
| contract_value         | NUMERIC(14,2) — optional financial term                        | No           |
| billing_currency       | USD or INR — optional                                          | No           |
| payment_terms          | Free text e.g. '30 days net' — optional                        | No           |
| reminder_days_override | JSON array e.g. \[90,30,7\] — overrides system defaults if set | No           |
| description            | Internal notes about the contract                              | No           |
| owner_user_id          | FK to app_user — contract owner, receives notifications        | Yes          |
| created_at             | Creation timestamp                                             | Yes          |
| created_by             | Username of creator                                            | Yes          |

> *Either customer_id (link to FPA Customer Management) or party_name (free text) must be provided — not both. If the client exists in Customer Management, use customer_id for cross-module consistency.*

**3.2 Contract Version**

| **Field**      | **Description**                                        | **Required** |
|----------------|--------------------------------------------------------|--------------|
| id             | UUID primary key                                       | Yes          |
| contract_id    | FK to contract                                         | Yes          |
| version_number | Sequential integer (1, 2, 3...)                        | Yes          |
| version_label  | Display label e.g. 'v1', 'v2-redlined', 'Final Signed' | Yes          |
| status         | DRAFT / UNDER_REVIEW / SIGNED / SUPERSEDED             | Yes          |
| notes          | Version-level notes (what changed, who sent it)        | No           |
| uploaded_at    | Upload timestamp                                       | Yes          |
| uploaded_by    | Username of uploader                                   | Yes          |

**3.3 Contract Document**

| **Field**           | **Description**                                                | **Required** |
|---------------------|----------------------------------------------------------------|--------------|
| id                  | UUID primary key                                               | Yes          |
| contract_version_id | FK to contract_version                                         | Yes          |
| document_type       | PRIMARY or SUPPORTING                                          | Yes          |
| filename            | Original filename with extension                               | Yes          |
| content_type        | MIME type (application/pdf, application/vnd.openxmlformats...) | Yes          |
| file_size_bytes     | File size for display                                          | Yes          |
| file_data           | BYTEA — binary content stored in PostgreSQL                    | Yes          |
| uploaded_at         | Upload timestamp                                               | Yes          |
| uploaded_by         | Username of uploader                                           | Yes          |

**3.4 Contract Type (configurable)**

| **Field**    | **Description**                           |
|--------------|-------------------------------------------|
| id           | UUID primary key                          |
| type_code    | Short code e.g. NDA, MSA, SOW             |
| display_name | Full name e.g. 'Non-Disclosure Agreement' |
| description  | Optional description                      |
| is_active    | Soft delete                               |

Seeded types: NDA (Non-Disclosure Agreement), MSA (Master Services Agreement), SOW (Statement of Work). Finance/Admin can add more via Settings → Contracts.

**3.5 Notification Log**

| **Field**          | **Description**                                |
|--------------------|------------------------------------------------|
| id                 | UUID primary key                               |
| contract_id        | FK to contract                                 |
| notification_type  | EMAIL or IN_APP                                |
| days_before_expiry | How many days before expiry this was triggered |
| sent_at            | When the notification was sent                 |
| recipients         | JSON array of email addresses notified         |

**4. Contract Versioning**

Each contract has one or more versions. Finance uploads a new version whenever a new draft is exchanged or a signed copy is received. Version status is set manually — there is no automated progression.

Auto-supersede on new version upload: When Finance uploads a new version, the system automatically sets the most recent non-SUPERSEDED version to SUPERSEDED. The new version is created with status DRAFT. Finance then manually changes the new version's status to UNDER_REVIEW or SIGNED as appropriate.

| **Version Status** | **When to Use**                                                                                                          |
|--------------------|--------------------------------------------------------------------------------------------------------------------------|
| DRAFT              | Initial version sent to customer, or first upload of a contract Finance is reviewing                                     |
| UNDER_REVIEW       | Customer has returned a redlined version, or contract is with legal for review                                           |
| SIGNED             | Final executed copy — both parties have signed. Only one version per contract should be SIGNED.                          |
| SUPERSEDED         | An older version replaced by a newer one. Finance can mark previous versions as Superseded when uploading a new version. |

> *For Third Party paper, Finance typically uploads one version directly as SIGNED. For Own paper, there may be multiple DRAFT or UNDER_REVIEW versions before the SIGNED copy. The system does not enforce any progression — Finance sets the status manually on each version.*

Each version can have one PRIMARY document (PDF or Word) and multiple SUPPORTING documents (any file type — e.g. annexures, schedules, cover emails). The PRIMARY document is shown prominently; SUPPORTING documents are listed below it.

**5. Expiry Tracking & Notifications**

**5.1 System default reminder days**

Configurable in Settings → Contracts → Notification Settings. Default: 90, 60, 30, 7 days before expiry. Finance can change these defaults. All active contracts without a per-contract override use these defaults.

**5.2 Per-contract override**

When creating or editing a contract, Finance can set custom reminder days that override the system defaults for that specific contract. Example: a critical MSA might have reminders at 180, 90, 30, 7 days.

**5.3 Notification delivery**

A scheduled job runs daily at 8:00 AM IST. For each active contract with an expiry date, it checks if today matches any reminder day (expiry_date − today = reminder days). If yes, it sends:

- In-app notification — shown in a notification bell/badge in the FPA header. Clicking opens the contract detail.

- Email notification — sent to: (1) contract owner (owner_user_id), (2) all addresses in the configured recipient list in Settings → Contracts.

Email content: Contract title, party name, contract type, expiry date, days remaining, direct link to the contract in FPA.

**5.4 Evergreen contracts**

Contracts with is_evergreen = true have no expiry date and never trigger notifications. Finance marks a contract as evergreen when there is no fixed end date (e.g. auto-renewing NDAs).

> *The notification job checks the notification_log before sending to avoid duplicate notifications on the same day. If a notification for contract X at 30 days was already sent today, it skips it.*

**6. Navigation & UI Structure**

Contracts is a new top-level nav section per ADR-021.

| **Sub-section**      | **Purpose**                                                                                                                                                                                                                                                                                 |
|----------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Dashboard            | Summary view: contracts expiring in next 30/60/90 days, recently added contracts, contracts by type (pie chart), contracts by status (bar chart)                                                                                                                                            |
| All Contracts        | Paginated list with search and filter. Search by title or party name. Filters: contract type, paper type, status, expiry date range, client. Each row shows: Contract \#, Title, Party, Type, Paper Type, Status, Expiry Date, Days Remaining (colored: red \<30, orange \<60, green \>60). |
| Templates            | Template library grouped by contract type. Upload, download, and manage contract templates. Each template: type, filename, version, uploaded by, upload date.                                                                                                                               |
| Settings → Contracts | Contract types (add/deactivate), notification recipients list, default reminder days configuration.                                                                                                                                                                                         |

**6.1 Contract Detail page**

Shows all contract metadata. Tabs:

- Overview — all fields, edit button (Admin only)

- Versions — list of all versions with status, upload date, uploader, notes. Each version: download primary document, list supporting documents with download links. 'Add Version' button (Admin only).

- Notifications — history of notifications sent, upcoming notification dates based on expiry and reminder days.

**6.2 Add/Edit Contract form**

Fields: Title, Contract Type, Paper Type, Client (FPA customer select or free-text toggle), Effective Date, Expiry Date, Is Evergreen (checkbox — disables Expiry Date), Status, Parent Contract (optional select from existing contracts), Contract Value, Billing Currency, Payment Terms, Reminder Days Override, Description, Owner (defaults to logged-in user, Admin can change).

**7. Template Library**

Separate from contracts — templates are master documents Finance uses as starting points when preparing Own paper contracts. Each template has a contract type, a display name, and one or more document versions.

Finance uploads a template (PDF or Word), assigns it a contract type and name. Multiple versions of the same template can exist (e.g. NDA Template v2026.1, v2026.2). Finance downloads the latest version when preparing a new contract.

Templates are not linked to specific contracts or clients — they are reusable company-level documents.

**8. Spring Modulith Module**

New module: \`com.cognologix.fpa.contracts\`. Entities and repositories in sub-packages (internal). \`ContractService\` in root package (public API). Cross-module: \`CustomerService.findCustomerRef()\` for client lookup (soft reference, same pattern as other modules).

| **Endpoint**                                                  | **Description**                                   |
|---------------------------------------------------------------|---------------------------------------------------|
| GET /api/contracts                                            | Paginated contract list with filters              |
| POST /api/contracts                                           | Create contract (Admin only)                      |
| GET /api/contracts/{id}                                       | Contract detail                                   |
| PUT /api/contracts/{id}                                       | Update contract metadata (Admin only)             |
| POST /api/contracts/{id}/versions                             | Add new version with document upload (Admin only) |
| GET /api/contracts/{id}/versions/{versionId}/document/{docId} | Download a document                               |
| PUT /api/contracts/{id}/versions/{versionId}/status           | Update version status (Admin only)                |
| GET /api/contracts/dashboard                                  | Dashboard summary data                            |
| GET /api/contracts/types                                      | List contract types                               |
| POST /api/contracts/types                                     | Add contract type (Admin only)                    |
| GET /api/contracts/templates                                  | List templates                                    |
| POST /api/contracts/templates                                 | Upload template (Admin only)                      |
| GET /api/contracts/templates/{id}/download                    | Download template document                        |
| GET /api/contracts/config/notifications                       | Get notification config                           |
| PUT /api/contracts/config/notifications                       | Update notification config (Admin only)           |

**9. Flyway Migration**

V36\_\_contracts.sql — creates: contract_type (seeded NDA/MSA/SOW), contract, contract_version, contract_document, contract_template, contract_template_document, contract_notification_log, contract_notification_config. Also adds contract notification recipient list to general_config.

**10. Non-Functional Requirements**

| **Category**      | **Requirement**                                                                                                                                          |
|-------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------|
| Document storage  | Binary blobs in PostgreSQL (BYTEA). Max file size: 20MB per document. Enforce via Spring multipart config.                                               |
| Backup            | Contract documents included in the system backup ZIP (as base64-encoded entries). V36 tables added to the backup/restore sequence.                       |
| Authentication    | Admin: create/edit contracts, upload versions, manage templates and types. Viewer: read-only access to contracts, versions, and downloads.               |
| Notifications     | Scheduled job via Spring @Scheduled (daily at 8 AM IST). Email via Spring Mail (SMTP configured in application.yml). Deduplication via notification_log. |
| Modulith boundary | ContractService never accesses foreign repositories. Customer reference via CustomerService.findCustomerRef() soft reference only.                       |

**11. Key Design Decisions — Summary**

| **Decision**     | **Choice**                                  | **Rationale**                                                                                                   |
|------------------|---------------------------------------------|-----------------------------------------------------------------------------------------------------------------|
| Paper type       | Third Party or Own on each contract         | Reflects how Cognologix actually works — most contracts are on customer's paper, a few on Cognologix's template |
| Versioning       | Manual status, no automated workflow        | Keeps it lean — Finance decides when to mark something Signed or Superseded without system enforcement          |
| Document storage | PostgreSQL BYTEA blobs                      | Consistent with existing system architecture — no additional infrastructure (S3, filesystem) needed             |
| Customer link    | Soft reference to FPA customer OR free text | Not all parties are FPA customers — some contracts may be with new clients not yet in Customer Management       |
| Template library | Store and download only                     | No placeholder filling needed — Finance uses Word's own mail merge or manual editing for customization          |
| Notifications    | Daily scheduled job + deduplication log     | Simple and reliable — no message queue or event infrastructure needed at this scale                             |
| Hierarchy        | Loose parent association                    | SOW can reference MSA without enforcing strict hierarchy — Finance occasionally needs flexibility               |

**12. Open Items**

- Email SMTP configuration — application.yml needs Spring Mail settings (host, port, username, password) for email notifications. Confirm SMTP provider with Finance.

- Document size limit — 20MB default. Confirm with Finance if larger contracts/supporting docs are expected.

- Backup ZIP — contract documents as BYTEA blobs will significantly increase backup size. Consider a separate document backup strategy for production.

- Signatory tracking — deferred. Add when Finance needs to track who signed on behalf of each party.

- E-signature integration — deferred. Add when Cognologix adopts DocuSign or similar.

*Prepared by: Vaibhav Natu \| For: Vaibhav, Co-Founder, Cognologix Technologies \| September 2026*

*This document is confidential and intended for internal use only.*
