# Cognologix Financial Planning & People Analytics System — Architecture Decision Records

Format: [Michael Nygard ADR style](https://cognitect.com/blog/2011/11/15/documenting-architecture-decisions) — Title / Status / Context / Decision / Consequences.

Status values: `Proposed` · `Accepted` · `Superseded by ADR-xxx` · `Deprecated`

---

## ADR-001: Hosting & Deployment Strategy — Cloud-Native, Cloud-Agnostic

**Status:** Accepted — July 2026

**Context**
Cognologix intends to run the system self-hosted initially, with the explicit option to move to GCP, AWS, or another cloud provider later — without that move requiring a redesign. The architecture must not assume, or quietly depend on, any single provider's proprietary services.

**Decision**
Build cloud-native and cloud-agnostic:
- Application is containerized (Docker), deployable to any Kubernetes-compatible environment or self-hosted via Docker Compose for smaller-scale needs.
- No provider-specific managed services baked into the core design (e.g. no AWS Lambda-specific triggers, no Azure-only PaaS bindings, no GCP-only data services).
- Where a managed convenience is genuinely useful (object storage, secrets management, managed queues), it sits behind an internal abstraction/interface so the underlying provider can be swapped without touching application code.
- Configuration is environment-driven (12-factor app principles) — no hard-coded environment assumptions.

**Consequences**
- (+) Full portability across self-hosted and any major cloud, without redesign.
- (+) Standard, transferable skills required (Docker/Kubernetes, Postgres) rather than proprietary cloud expertise.
- (+) Avoids vendor lock-in and renegotiation leverage loss.
- (–) Forgoes some "serverless magic" and provider-specific convenience/cost optimizations available to a committed single-cloud design.
- (–) Slightly more upfront setup discipline (abstraction layers for storage/secrets/queues) than a fully managed, provider-native build.

**Alternatives considered**
- Azure-native (rejected — creates lock-in inconsistent with the self-hosted-first requirement, despite the natural M365 affinity).
- AWS- or GCP-native (rejected — same reasoning).

---

## ADR-002: Database Technology — PostgreSQL

**Status:** Accepted — July 2026

**Context**
The system's core data (People/Payroll snapshots, Master records, reconciliation state, Client Master, audit trail) is relational, financially sensitive, and requires strong consistency — history must be exactly reproducible (Section 6 of the Module 1 spec), and reconciliation logic depends on reliable joins across periods. Data volume is small (~100–300 employees, monthly cadence). Must also satisfy ADR-001's cloud-agnostic constraint.

**Decision**
Use PostgreSQL as the system of record.

**Consequences**
- (+) ACID transactions — essential for financial correctness and immutable snapshot guarantees.
- (+) Open-source and cloud-agnostic — runs unchanged self-hosted (Docker) or via any provider's managed Postgres (RDS, Cloud SQL, Azure Database for PostgreSQL), satisfying ADR-001 directly.
- (+) JSONB support allows raw snapshot payloads to be retained for audit/rehydration alongside strongly-typed relational tables for everything queried regularly — useful given source export formats (Zoho People / Zoho Payroll columns) may evolve.
- (+) Mature ecosystem, wide hiring pool, well-understood operational characteristics.
- (–) None significant at this data scale — a distributed/NoSQL store would add operational complexity with no corresponding benefit here.

**Alternatives considered**
- MySQL/MariaDB (viable, but weaker JSONB/window-function support for the trend and audit needs).
- NoSQL (MongoDB, DynamoDB, Cosmos DB) — rejected: data is inherently relational, volume doesn't justify it, and provider-native options (DynamoDB, Cosmos DB) would violate ADR-001.
- Specialized event-store databases — rejected as premature; Postgres can model the append-only/immutable snapshot pattern (Section 6) directly via standard tables and constraints without adopting a separate paradigm.
- **TimescaleDB (Postgres extension) — deferred, not rejected.** The monthly, immutable, point-in-time snapshot model (Section 6) is structurally a good fit for Timescale's hypertables/continuous aggregates. However: (a) current data volume (~100–300 employees, monthly cadence) is far below the scale where those optimizations matter — a plain indexed Postgres table performs fine for years; (b) not all managed Postgres offerings support the extension (notably AWS RDS and Azure Database for PostgreSQL do not, without Timescale's own cloud or self-hosting), which would narrow the cloud-agnostic flexibility established in ADR-001. Because Timescale is Postgres-compatible, migrating to it later — if history volume or trend-query performance ever justifies it — is a schema migration, not a rewrite. Revisit if/when that need materializes.

---

## ADR-003: Application Deployment Posture — Standalone Web Application

**Status:** Accepted — July 2026

**Context**
Cognologix runs on M365 heavily (SharePoint, Power Automate) for other operational tooling. For this system, the question was whether it should embed into that environment (SharePoint-hosted, Teams app) or run independently.

**Decision**
Build as a standalone web application, independent of M365/SharePoint.

**Consequences**
- (+) Full architectural freedom — not constrained by SharePoint/Power Platform's data model, extensibility limits, or hosting model.
- (+) Consistent with ADR-001 (cloud-agnostic) — a SharePoint-embedded app would tie the system to Microsoft's ecosystem.
- (+) Simpler to reason about, build, and deploy using standard web application patterns.
- (–) Users access it via its own URL/login rather than through SharePoint navigation they already use daily — a minor discoverability/convenience cost for the small Finance + Management user group (Section 3 of the Module 1 spec).
- (Open) Whether to offer Microsoft SSO (Entra ID) as a login convenience, without embedding the app itself in SharePoint, is a separate, smaller decision — deferred until authentication is designed.

**Alternatives considered**
- SharePoint/Power Platform-native app — rejected: conflicts with cloud-agnostic goal (ADR-001) and would constrain the data model to Microsoft's platform.

---

## ADR-004: Backend Framework — Spring Boot (Java)

**Status:** Accepted — July 2026

**Context**
Backend language is fixed to Java. The system will be built and maintained primarily by Vaibhav, with AI-assisted development (e.g. Claude Code), not a dedicated engineering team. Must satisfy ADR-001 (containerized, cloud-agnostic) and ADR-002 (PostgreSQL).

**Decision**
Spring Boot, with Spring Web (REST), Spring Data JPA, Spring Security, and Flyway for versioned schema migrations. Packaged as a Docker container.

**Consequences**
- (+) Mature, well-documented ecosystem; strong fit for AI-assisted development given how well-represented Spring Boot conventions are.
- (+) Flyway gives auditable, versioned schema migrations — important given the immutable/append-only snapshot model (Module 1 spec, Section 6).
- (+) Spring Security provides a clear path to OAuth2/OIDC later (ADR-005) without a rewrite.
- (–) More boilerplate than Node.js/Python equivalents, mitigated by AI-assisted development and Spring Boot's strong conventions.

**Alternatives considered**
- Quarkus/Micronaut — lighter, faster startup, more "cloud-native" by reputation — but smaller ecosystem and less AI-assistance familiarity; rejected for this build profile.
- Node.js/TypeScript, Python — ruled out; Java is a fixed constraint, not a preference.

**API style:** REST, not GraphQL — single frontend consumer, no need for GraphQL's query flexibility at this scale.

---

## ADR-005: Authentication — Self-Managed (Spring Security), SSO-Ready

**Status:** Accepted — July 2026

**Context**
Small, fixed set of named users (Section 3 of the Module 1 spec: CEO, co-founders, Finance Head). Standalone application (ADR-003), so no built-in M365 identity integration.

**Decision**
Start with Spring Security's own username/password + JWT-based session, backed by the application database. Structure authentication behind an internal interface so swapping to OAuth2/OIDC SSO (e.g. Microsoft Entra ID, given the existing M365 environment) later is a configuration change, not a rewrite.

**Consequences**
- (+) No premature complexity (standing up an external identity provider) for a handful of users.
- (+) Clear, low-cost upgrade path to SSO if/when convenience or policy demands it.
- (–) Manual user provisioning/password reset until SSO is added.

**Alternatives considered**
- OAuth2/OIDC via Keycloak or Entra ID from day one — rejected as premature for current user count; revisit if user count grows or SSO becomes a stated requirement.

---

## ADR-006: Internal Architecture — Tactical DDD, No Full CQRS/Event Sourcing

**Status:** Accepted — July 2026

**Context**
System is built and maintained solo, with AI assistance, module by module (People & Payroll, Client Master, future Revenue). Data volume is small; read-side (dashboard/trend views) and write-side (ingestion/reconciliation) have different natural shapes, but full CQRS with event sourcing is a significant operational commitment (event store, eventual consistency, replay logic).

**Decision**
Use DDD tactically: bounded contexts and aggregates aligned to the module boundaries already established (People & Payroll context, Client Master context, future Revenue context), implemented as a layered/hexagonal architecture within a single Spring Boot application. Apply "CQRS-lite" — separate read-optimized query services for dashboards/trend views from write-side command handling for ingestion and reconciliation — but both against the same PostgreSQL database, without a separate event store or eventual consistency.

**Consequences**
- (+) Keeps a solo, AI-assisted codebase organized as it grows across modules, without the operational overhead full CQRS/ES would add.
- (+) Bounded context boundaries map directly onto the module-by-module build approach already in use.
- (–) Not a "true" CQRS architecture — read and write models share the same store, so there's no independent scaling of read/write paths. Acceptable given current and foreseeable data volume.

**Alternatives considered**
- Full CQRS with event sourcing — rejected as premature; revisit only if a concrete need emerges (e.g. genuine read/write scaling mismatch, need for full event replay/audit beyond what Flyway-versioned tables + an audit log table already provide).
- No DDD structure at all (transaction-script / CRUD-only) — rejected; the classification, reconciliation, and multi-source-of-truth logic already in the Module 1 spec is genuine domain complexity that benefits from explicit modeling, even at modest scale.

---

## ADR-007: Event Sourcing — Not Adopted System-Wide; Reconsider Selectively for Reconciliation

**Status:** Accepted — July 2026

**Context**
The point-in-time snapshot model (Module 1 spec, Section 6) is immutable and history-preserving, which raises a fair question: does event sourcing fit naturally here? Raised directly during architecture discussion.

**Decision**
Do not adopt event sourcing system-wide. Source data (Zoho People, Zoho Payroll) arrives as complete monthly snapshots, not a stream of fine-grained actions — there is no natural upstream event log to be faithful to, and synthesizing events from snapshot diffs would add event-store machinery (schema versioning, replay/projection logic) to serve a shape of data the domain doesn't actually produce. Instead: immutable snapshot tables per period (already decided, Section 6) plus a proper append-only audit log table (who did what, when — uploads, period reopens, reconciliation mapping actions) provide equivalent auditability without the operational cost.

The Reconciliation workflow specifically (Unmatched → Manually Mapped, Payroll Pending → Resolved, Exited-Auto-Matched → Confirmed) is a legitimate candidate for lightweight, aggregate-scoped event sourcing later, since it has genuine multi-step state transitions. Not adopted now — revisit only if audit granularity at that level becomes a concrete need.

**Consequences**
- (+) Avoids event-store/projection-replay complexity inappropriate for a solo, AI-assisted build (ADR-004 context) and for source data that doesn't naturally arrive as events.
- (+) Snapshot tables + audit log satisfy the actual stated requirement (Section 6: history must be exactly reproducible) without a new paradigm.
- (+) Keeps the door open for scoped, aggregate-level event sourcing on Reconciliation specifically, without committing the whole system to it.
- (–) If audit requirements later demand fine-grained reconstruction of every intermediate state (not just period snapshots and logged actions), this decision would need revisiting.

**Alternatives considered**
- Full event sourcing across the system — rejected per reasoning above.
- Event sourcing scoped to Reconciliation only, from day one — deferred, not rejected; adds complexity not yet justified by a concrete requirement.

---

## ADR-008: Structural Pattern — Modular Monolith via Spring Modulith

**Status:** Accepted — July 2026

**Context**
Bounded contexts are already identified (People & Payroll, Client Master, future Revenue), and the system is built module by module by a solo developer with AI assistance. Needed: a structural pattern that keeps those boundaries real in code, without the operational overhead of microservices (network calls, service discovery, distributed transactions) — inappropriate for one person maintaining an internal tool at this scale.

**Decision**
Modular monolith: a single deployable Spring Boot application, internally organized into modules that map 1:1 to bounded contexts, using Spring Modulith to enforce module boundaries (prevents accidental coupling between modules) and provide in-process domain events for cross-module communication. No network calls between modules; one process, one deployment unit, consistent with ADR-001's containerized deployment.

**Consequences**
- (+) Bounded context boundaries are enforced by tooling (Spring Modulith's verification), not just convention — matters for a solo build where there's no code review from a second engineer to catch drift.
- (+) In-process domain events give clean cross-module decoupling (e.g. Client Master changes notifying People & Payroll) without the complexity of message brokers or distributed event streams.
- (+) Avoids microservices' operational overhead entirely — no service mesh, no distributed tracing needs, no network-partition failure modes to design around.
- (+) Natural incremental path: if a module ever genuinely needs independent scaling or deployment, Spring Modulith's clean module boundaries make that extraction easier later than un-picking a tangled monolith would be.
- (–) All modules share the same runtime and deployment — a bug in one module can still affect application availability overall (mitigated by the modest scale and low request volume of an internal tool).

**Alternatives considered**
- Microservices per bounded context — rejected: operational overhead (deployment complexity, network calls, distributed transactions) is unjustified for a solo-maintained internal tool at this data volume and user count.
- Unstructured monolith (no enforced module boundaries) — rejected: the domain has genuine complexity (Section 7's reconciliation logic, multi-source classification) that benefits from explicit bounded-context structure, especially without a second engineer to catch architectural drift.

---

## ADR-009: Frontend Framework & UI Library — React + Ant Design

**Status:** Proposed — July 2026

**Context**
Backend is a REST API (ADR-004) behind a standalone web app (ADR-003). The frontend is internal-tool-shaped: dashboards, reconciliation screens with drill-down, period selectors, dense data tables with grouping (PU/BU breakdowns), and forms — not consumer-facing. Built solo, AI-assisted.

**Decision**
React for the framework (confirmed). Ant Design as the component library, over MUI.

**Consequences**
- (+) Ant Design's Table component provides sorting, filtering, grouping, fixed columns, and expandable rows free and out of the box — maps directly onto this system's BU/PU breakdown tables and reconciliation drill-downs (Module 1 spec, Sections 7.4, 9, 10).
- (+) Professional default appearance requires minimal custom theming/CSS work — valuable for a solo, AI-assisted build with no dedicated design effort.
- (+) Purpose-built for enterprise/admin/back-office applications, which is this system's actual profile.
- (–) Less flexible theming than MUI if visual branding beyond "clean and professional" is ever wanted — considered unlikely for an internal Finance/Management tool.
- (–) Smaller ecosystem than MUI for consumer-app-style patterns — not relevant here.

**Alternatives considered**
- MUI — strong general-purpose choice, larger community, more flexible Material Design theming — but its comparable advanced data-grid features (grouping, tree data, export) require the paid MUI X Pro/Premium tier, whereas Ant provides equivalent functionality free. Would still be reasonable if Material Design aesthetic or prior team familiarity mattered — neither is a factor here.

---

## ADR-010: Customer Management as a Core Bounded Context (supersedes narrow "Client Master" framing)

**Status:** Accepted — July 2026

**Context**
The original context map (accompanying this ADR log) scoped "Client Master" narrowly to the identity-mapping problem solved in the Module 1 spec (Section 9): Zoho People BU \u2194 Zoho Books Customer + Project Code. On review, this left no home for client-relationship domain knowledge that already exists but was scattered across the old Excel system: per-client billing rate card (Assumptions sheet), DSO/payment terms and client concentration risk tracking (Section 1.2 of the original System Design Context Document \u2014 Icertis+Cadent 62% combined concentration is an explicitly monitored risk), and relationship ownership.

**Decision**
Rename and expand the context to **Customer Management**, reclassified as a **Core domain** (not Supporting) \u2014 for an IT services firm, client relationships and concentration risk are argued to be as central to the business as cost data, not merely enabling infrastructure. Scope now includes: identity mapping (built, Module 1 \u00a79), billing rate card, DSO/commercial terms, relationship ownership, concentration risk tracking, and client lifecycle status (active/at-risk/churned/prospect) \u2014 the latter four are future scope, not yet specified.

Customer Management is explicitly distinct from Revenue: Customer Management owns master data and relationship (who, what rate, what risk); Revenue owns transactional actuals (invoices, T&M/Fixed-Bid calculations), consuming Customer Management's rate card rather than duplicating it.

**Consequences**
- (+) Concentration risk and DSO tracking \u2014 already identified as a monitored business risk \u2014 now has an explicit domain home instead of being an ungoverned manual Excel check.
- (+) Clean separation from Revenue prevents rate-card data from being duplicated/drifting between the two contexts.
- (+) Module 1's Section 9 scope is unaffected \u2014 it remains the correctly-scoped first slice (identity mapping) of this larger context; no rework needed.
- (\u2013) Reclassifying as Core vs Supporting is a judgment call, not a mechanical one \u2014 revisit if it turns out to behave more like a supporting/reference context in practice once specified.

**Alternatives considered**
- Keep "Client Master" narrowly scoped to identity mapping only, treat rate card/DSO/concentration as part of the Budgeting & Forecasting context instead \u2014 rejected: those attributes describe the *customer*, not the *plan*, and belong with the entity they describe rather than with whoever consumes them first.
- Classify as Supporting rather than Core \u2014 reasonable alternative view; open to revisiting once this context is actually specified and its complexity is better understood.

---

## ADR-011: Configuration UI — Organized by Bounded Context

**Status:** Accepted — July 2026

**Context**
Configuration needs are accumulating per module: People & Payroll already requires admin-maintained reference lists (Module 1 spec, Section 8.1 — which Practice Units count as delivery, which BU values map to Management/Leadership) and Customer Management will require its own (Customer/Project Code definitions, billing rate card, concentration risk thresholds). Left undifferentiated, these settings would become one flat, growing list with no structure.

**Decision**
The settings/configuration UI is organized into a **General** section plus one section per bounded context, mirroring the backend's modular monolith structure (ADR-008) — each module owns and renders its own configuration screen. Known sections so far:

- **General** — cross-cutting settings not owned by a single context (e.g. user accounts, until role-splitting per ADR-005 makes this richer).
- **People & Payroll** — delivery Practice Unit list, Management/Leadership BU mapping (Module 1 spec §8.1).
- **Customer Management** — Customer Code / Project Code definitions (now authoritative here, per clarification below), billing rate card, concentration risk thresholds, relationship owner assignment.
- *(Further sections added as each future module — Revenue, Budgeting, Contracts — is specified.)*

**Consequences**
- (+) Configuration UI structure stays legible as the system grows, rather than becoming an undifferentiated settings page.
- (+) Frontend structure mirrors backend module boundaries, reinforcing (rather than cutting across) the bounded contexts already established.
- (–) A setting that's genuinely cross-context (rare, but possible) needs a deliberate "General" placement decision rather than an obvious home — acceptable trade-off.

**Alternatives considered**
- Single flat configuration list — rejected: doesn't scale as modules are added, and obscures which module actually owns a given setting.

---

## Note on ADR-010: Client identity ownership resolved

Customer Code and Project Code are currently *sourced from* Zoho People's export (Module 1 spec, Section 4.1), but conceptually belong to the Customer, not to an HR system. This resolves the ownership question raised when Customer Management was elevated to Core (ADR-010): **Customer Management becomes authoritative for these codes.** Zoho People's BU Code / Project Code fields shift from being the source of truth to being a value that gets *validated against* Customer Management's own definitions (extending the Section 9.4 data-quality check already designed) — consistent with migrating the Module 1 §9 identity mapping into the Customer Management module rather than leaving it in People & Payroll.

---

## ADR-012: Configuration Storage — Decentralized, Owned per Bounded Context

**Status:** Accepted — July 2026

**Context**
ADR-011 established that the Settings UI is organized by bounded context (General + one tab per module). This left open a separate question: where the underlying configuration *data* actually lives — one shared Configuration module serving all contexts, or each context storing its own.

**Decision**
Each bounded context stores and owns its own configuration data. No centralized Configuration module. The unified Settings UI is a composition concern only: a thin application-layer aggregator calls each module's own config read-API in-process (per ADR-008's Spring Modulith structure) and renders the results together as tabs. Each module retains exclusive write ownership of its own configuration.

Clarification during discussion: configuration in this system is specifically the *rule* an aggregate derives from, not a derived/stored value — e.g. the list of which Practice Unit values count as "delivery" is configuration; `IsDeliveryPU` itself is computed at read-time by applying that rule against each employee's actual Practice Unit value, and is never itself stored.

**Consequences**
- (+) Configuration is treated as domain knowledge belonging to the context that defines its meaning (e.g. the delivery-PU rule belongs to People & Payroll, exactly as Customer Code was determined to belong to Customer Management per ADR-010) — not generic key-value pairs in a disconnected module.
- (+) Avoids the coupling a shared central config module would create across every module (directly counter to ADR-008's modular monolith goals) — and avoids a shared dependency that would complicate extracting any module into its own service later.
- (+) Each context's config shape can be exactly what that domain needs (simple reference lists for People & Payroll; rate-card type + thresholds for Customer Management) without forcing a shared abstraction that doesn't actually fit both.
- (–) The unified Settings page requires a small composition/aggregation layer rather than a single straightforward table read — an acceptable, thin cost.

**Alternatives considered**
- Centralized Configuration module storing all contexts' settings — rejected: creates cross-module coupling that undermines the modular monolith boundary (ADR-008), and forces a generic key-value storage shape that loses type safety and domain-specific validation.

---

## ADR-013: Visual Theme & Login Screen — Frozen Design

**Status:** Accepted — July 2026

**Context**
Phase 1 of the build (Claude Design) iterated through several login screen options to establish the app's visual theme before any implementation. This needs to be recorded as an authoritative reference, not left implicit in chat screenshots, since Claude Code will build against it directly.

**Decision**
Frozen visual theme, based on option 1a as refined:
- **Layout:** split-screen login — left panel dark brand black (#232323, an approved neutral per brand guidelines' "dark backgrounds, header bars, dark panel fills" usage), right panel plain white with the sign-in form.
- **Left panel:** subtle repeating circular pattern texture (low-contrast, referencing the Cognologix glyph), white "cognologix" wordmark logo, white heading/body text. Marketing-style tagline text present but explicitly left open to change or removal during development — not a frozen requirement.
- **Right panel:** email field, password field with show/hide toggle, "Forgot password?" link in Red #f05756, solid Red #f05756 "Sign in" button, "Invite-only access" helper text. No sign-up or social login options (matches ADR-005's self-managed, invite-only auth model).
- **App shell (post-login):** header bar in Black #232323 (not white) — carries brand identity consistently across every screen, reusing the login panel's color as an approved neutral rather than introducing a new one. Logo in the header uses the dark-background variant: gradient glyph + white wordmark.
- **Logo rule, light backgrounds (e.g. inside the Settings page, sidebar):** glyph in the orange/red gradient, wordmark in Grey #525957 — never black. This rule required an explicit Design System correction after two independent screens generated the wordmark in black.
- **Sidebar/content areas:** remain Light BG (#f7f6f4), not dark — the dark treatment is specific to the login panel and the top header bar only.
- **Settings page:** three tabs — General, People & Payroll, Customer Management (per ADR-011) — General fully designed with a Members table (Name, Email, Role — single "Admin" role for all, matching ADR-005's flat-role-for-now decision) and an "Add user" invite modal.

**Consequences**
- (+) Gives Claude Code (and any future design work) an unambiguous, text-based reference for the visual system, rather than relying on screenshots alone.
- (+) Records the specific corrections made during iteration (wordmark color rule, dashboard placeholder scope) so they aren't silently re-introduced during implementation.
- (–) The Dashboard screen was NOT validated as part of this theme freeze — an early Claude Design generation invented unrelated content (cash burn/runway metrics, generic SaaS departments) that must not be treated as any kind of reference; the real People Dashboard design is deferred to its own Phase 4 session against Module 1 Section 10.

**Alternatives considered**
- Full gradient panel fill (original option 1a) — rejected: used Red/Orange as a dominant fill rather than an accent, violating the brief.
- Plain off-white, minimal-color treatment (option 1b) — considered but ultimately not chosen; judged too colorless, losing brand presence entirely rather than striking a balance.

---

## ADR-014: Frontend Theming Centralization & Responsive Design Scope

**Status:** Accepted — July 2026

**Context**
ADR-009 chose Ant Design; ADR-013 froze the specific brand values (colors, fonts). Needed: a decision on *where* those values are implemented in code (to avoid hex codes scattered across component files), and how far responsive design should go, given the audience is under 10 Finance/Management users doing dense data work (reconciliation tables, dashboards) rather than a broad or mobile-first user base.

**Decision**
**Theming:** centralized via Ant Design's `ConfigProvider` `theme` object (`token` + `components`), not per-component styling. Token mapping: `colorPrimary` / `colorLink` = Red #f05756, `colorBgLayout` = Light BG #f7f6f4, `colorBgContainer` = White #ffffff, `fontFamily` = Lato (body); Montserrat for headings via a `Typography` override/class since Ant Design has no separate heading-font token. This is a single config file — brand changes happen in one place, not a codebase-wide search.

**Responsiveness:** desktop-primary for this phase — flexible across laptop-to-large-monitor widths (13" to 27"+), not optimized for phone/tablet. Built using Ant Design's `Layout` components and `Row`/`Col` grid system (with breakpoint props) rather than fixed-pixel widths, specifically so phone/tablet support can be added later by extending existing breakpoints rather than reworking a fixed-width layout. Not building hamburger nav or stacked-table mobile treatments now.

**Consequences**
- (+) Single source of truth for brand values in code, matching the "configuration over hard-coding" principle already established (Module 1 §8.1, ADR-012).
- (+) Responsive scope matches actual usage (desktop, dense data) without spending effort on phone-specific UI patterns nobody currently needs.
- (+) Grid-based layout means extending to phone/tablet later is additive (new breakpoints), not a rework — avoids the common trap of "desktop-only" meaning hardcoded pixel widths that become expensive to retrofit.
- (–) If phone access is ever needed, dense components (large reconciliation/breakdown tables) will still need dedicated small-screen treatment at that time — deferred, not solved in advance.

**Alternatives considered**
- Fully responsive including phone from day one — rejected as premature; no current user need, and the grid-based approach preserves the option without paying the cost now.
- Per-component inline styling instead of centralized theme tokens — rejected: scatters brand values across the codebase, making future changes error-prone.

---

## ADR-015: Rate Card Currency Model & FX Rate as a Separate Effective-Dated Value

**Status:** Accepted — July 2026

**Context**
Cognologix bills global clients in a mix of USD and INR, while payroll and expenses are purely INR-denominated. Revenue calculations need a configurable FX rate to convert USD figures to INR for combination with INR cost data. Initial schema proposal used two fixed columns (`rate_amount_rs`, `rate_amount_dollar`) on each rate card line, assuming every line needed both figures simultaneously — this was corrected on review: a given client's rate is quoted in *one* currency, not both, and different clients use different currencies.

**Decision**
`rate_card_line` carries a single `rate_amount` plus a `currency` field (USD / INR), not two fixed currency columns. A separate `fx_rate` table stores the USD↔INR conversion rate, effective-dated (`effective_from`/`effective_to`) — consistent with the point-in-time principle already established for the rate card itself (Module 2 §6, tracing back to Module 1 §6.1): a March revenue calculation must use March's FX rate, not whatever rate is current when the calculation runs.

**Consequences**
- (+) Scales cleanly if a third currency is ever introduced — a fixed-column model would not.
- (+) FX rate's historical accuracy matches the same reproducibility guarantee already required of the rate card and of Zoho People/Payroll snapshots.
- (–) Open item: whether `fx_rate` is owned by Customer Management or lives in the General configuration section (ADR-011) — not yet decided. Leaning toward General, since it's a system-wide financial utility value that Budgeting & Forecasting will also need, not customer-specific domain knowledge — but this is explicitly left open pending further discussion, not decided.

**Alternatives considered**
- Two fixed currency columns per rate card line — rejected: assumes every line needs both figures, which isn't the actual billing model; doesn't scale to additional currencies.

---

## ADR-016: Concentration Risk Calculation — Deferred to Budgeting & Forecasting, Not Computed via Cross-Module Reach

**Status:** Accepted — July 2026

**Context**
Module 2's original spec deferred concentration risk calculation, reasoning that a percentage requires per-client revenue, which only the future Revenue module (Zoho Books) would supply. During schema design, an alternative was proposed: since Cognologix bills primarily T&M, Customer Management could estimate revenue now by calling People & Payroll's billable-headcount-by-client data directly and multiplying by its own rate card — avoiding the wait for Revenue. This was reconsidered and reversed on review.

**Decision**
Concentration risk calculation remains deferred, and is NOT computed by Customer Management reaching into People & Payroll's data directly. Doing so would violate the modular monolith boundary established in ADR-008 (no module reaches into another's domain data to perform its own cross-cutting aggregation). Cross-context revenue/cost aggregation — including concentration risk, and more broadly the Plan vs. Actual analysis (planned revenue, actual revenue, planned headcount, actual headcount, planned expense, actual expense) that is this system's core analytical philosophy — belongs to the **Budgeting & Forecasting (AOP)** context already identified on the bounded context map, which exists specifically to consume both People & Payroll's and Customer Management's data as a downstream aggregator (via Anti-Corruption Layer, per the context map's existing relationships). Customer Management continues to own only the concentration threshold configuration (Module 2 §8), unchanged.

**Consequences**
- (+) Preserves module boundary integrity — Customer Management's schema and logic stay scoped to customer identity, rate cards, and terms, not revenue estimation.
- (+) Confirms Budgeting & Forecasting's role on the context map is not just for the eventual Revenue-actuals case but for the Plan vs. Actual pattern generally, across every dimension (revenue, headcount, expense) — clarifying scope for when that module is specified.
- (–) Concentration risk (and any T&M-based revenue estimate) remains unavailable until Budgeting & Forecasting is specified and built — no interim estimate is available from Customer Management alone.

**Alternatives considered**
- Customer Management estimates T&M revenue directly via a cross-module call to People & Payroll's billable headcount — rejected: breaks the modular monolith boundary (ADR-008) for a convenience that belongs to a module already scoped for exactly this purpose.

---

## ADR-017: FX Rate — Average-Per-Period Model, General Config Ownership, Snapshot Reference Rule

**Status:** Accepted — July 2026

**Context**
Cognologix bills global clients in USD, while payroll and expenses are purely INR-denominated. Revenue calculations need USD→INR conversion. The question was how to model the FX rate: daily spot rate, a single configurable current value, or something else. Separately: where the rate lives (Customer Management vs. General config), and how historical calculations stay reproducible when the rate changes.

**Decision**
Three sub-decisions, all accepted together:

1. **Average-per-period model.** FX rate is a Finance-maintained average for a meaningful period (typically quarterly or per financial year), not a daily market rate. Updated infrequently — when the agreed average changes, Finance closes the current record and inserts a new one.

2. **General configuration ownership.** `fx_rate` table lives in the General configuration section (ADR-011), not in Customer Management — it is a system-wide financial utility value that Budgeting & Forecasting will also consume, not customer-specific domain knowledge. Managed via the Settings > General tab. This closes ADR-015's open item on FX rate placement.

3. **Snapshot reference rule (cross-cutting).** Any snapshot or computed record that involves a USD→INR conversion stores a `fx_rate_id` (FK → `fx_rate`) alongside the calculated figures — not a copy of the rate value itself. This makes the exact rate used for any historical calculation permanently traceable: rerunning a March report in October reads March's stored `fx_rate_id` and uses March's rate, not whatever rate is current. This rule applies to all future snapshots involving currency conversion — most immediately Budgeting & Forecasting's revenue snapshot — and is established now as a cross-cutting design principle, not something each module rediscovers independently.

**Schema:** `fx_rate` table with `currency_pair`, `rate`, `effective_from`, `effective_to` (null = currently active), `created_by` (audit). Overlap exclusion constraint via PostgreSQL `btree_gist` extension enforces no two overlapping active rates for the same currency pair at the database level. Separate Flyway migration (V3) from Customer Management (V2), reflecting General config ownership.

**Consequences**
- (+) Historical calculations are permanently reproducible — rate changes don't silently rewrite prior period figures, consistent with the point-in-time principle established in Module 1 §6.1 and carried through Module 2 §6.
- (+) FX rate ownership is unambiguous — one table, one owner (General config), consumed by any module that needs it.
- (+) Database-level overlap constraint is stronger than application-layer validation — prevents concurrent-write race conditions.
- (+) `fx_rate_id` reference on snapshots gives full audit traceability (value + who set it + when it was effective) rather than just storing a copied number.
- (–) Slightly more join complexity when querying historical figures — minor, well worth the auditability guarantee.

**Alternatives considered**
- Single configurable "current FX rate" value — rejected: silently rewrites historical figures when updated; no audit trail.
- Daily spot rate — rejected: operational overhead not justified; Cognologix's planning model uses average rates, not daily precision.
- Customer Management ownership — rejected: FX rate is not customer-specific; Budgeting & Forecasting also needs it, so General config is the natural shared home.

---

## ADR-018: People & Payroll — Master Record Lifecycle & Mutability

**Status:** Accepted — July 2026

**Context**
Original Module 1 spec implied master records were immutable once built per period. During schema design walkthrough, the real operational need was clarified: Finance must be able to correct data in open periods without losing the point-in-time guarantee for finalised periods.

**Decision**
Master records are materialised (stored in a table, not computed on the fly) but remain mutable until the period is explicitly finalised by Finance. Period lifecycle: OPEN → SNAPSHOTS_UPLOADED → MASTER_BUILT → FINALISED. A fifth status, SUPERSEDED, marks prior versions that were auto-replaced by a bulk re-upload correction. Corrections to open periods are supported two ways: re-upload the entire snapshot file for a given import type (bulk correction — if that import type was already uploaded on the current version, the current version is marked SUPERSEDED and a new version is auto-created with an incremented version_number; otherwise a normal insert on the current version) or edit individual employee records directly in the UI (targeted correction, triggers recalculation). Master record build is always an explicit Finance action — never triggered automatically. No recalculation log is stored — the audit trail is the snapshot upload history and superseded period versions. Only FINALISED periods are immutable. Dashboard metrics are computed on demand from Master records at query time, not stored separately — acceptable at current data volume (~100-300 employees monthly).

**Consequences**
- (+) Finance can correct data entry errors without losing the point-in-time guarantee for historical periods.
- (+) Dashboard always reflects current Master state for open periods.
- (+) Explicit finalisation gives Finance control over when a period becomes authoritative.
- (+) Re-upload auto-bump preserves a complete audit trail — superseded versions retain their snapshot uploads.
- (–) Dashboard for open periods may show different numbers if Master is rebuilt — Finance must understand that open-period figures are provisional until finalised.
- (–) After a re-upload bump, Finance must re-upload any other import types on the new version before building Master.

---

## ADR-019: People & Payroll — Column Mapping Template for Ingestion

**Status:** Accepted — July 2026

**Context**
Original Module 1 spec assumed fixed column names from Zoho exports ("column headers must match the expected contract"). In practice this creates brittleness — Zoho can change export column names, and different export configurations may vary. A more resilient approach was identified during schema design.

**Decision**
Ingestion uses a column mapping UI rather than fixed column names. On upload, the system reads the Excel headers and presents a mapping screen where Finance maps each Excel column to a pre-defined system attribute (EmployeeID, PracticeUnit, BusinessUnit, BillableStatus, GrossPay, etc.). The mapping is saved as a template per import type (Zoho People / Zoho Payroll / Zoho People Exited) and pre-fills on the next upload — Finance only corrects if Zoho changed their column names. Only mapped fields are stored — the complete raw payload is not retained.

**Consequences**
- (+) Resilient to Zoho export format changes — no code change needed, Finance just re-maps.
- (+) Cleaner storage — only fields the system uses are persisted.
- (–) Adds a mapping step to the first upload of each type — mitigated by template pre-fill on subsequent uploads.

---

## ADR-020: People & Payroll — Three Upload Types Including Exited Employees Export

**Status:** Accepted — July 2026

**Context**
Module 1 §4.3 noted that Zoho People offers a separate Exited Employees export with actual Last Working Day per employee. The question was whether to include this as a third upload type now or defer and rely purely on absence-based exit detection.

**Decision**
Include as a third upload type now alongside Zoho People (active) and Zoho Payroll. All three follow the same upload flow with their own column mapping template. The exited employees upload enriches the Employee Registry directly — finds the registry record by EmployeeID and upgrades the exit date from the absence-inferred month-level value to the precise Last Working Day from the export. Absence-based inference remains the primary detection signal; this export upgrades precision only.

**Consequences**
- (+) Exact exit dates available from day one — useful for prorated salary calculations and precise headcount reporting.
- (+) Consistent upload pattern across all three import types — same UI, same mapping template mechanism.
- (–) Slightly more monthly work for Finance — three upload types instead of two — acceptable given the small team and low employee turnover at current scale.

---

## ADR-021: Navigation Structure — Domain-First, Import as Sub-Section

**Status:** Accepted — July 2026

**Context**
Initial navigation design had a dedicated top-level "Imports" section with sub-sections per module (Imports → People & Payroll, Imports → Books). On review, organising by action type rather than domain was identified as a weaker information architecture — users think in terms of the domain they're working on, not the action they're performing.

**Decision**
Navigation is organised domain-first. Each bounded context with operational screens gets its own top-level nav section. Import is a sub-section within that domain, not a separate top-level concern. Confirmed structure:

- **People & Payroll** — Imports (Zoho People, Zoho Payroll, Zoho People Exited), Period Management (view periods, trigger Master build, finalise/reopen), Master Data (view/edit Master records, trigger recalculation), Dashboard (People Analytics — headcount, PU/BU breakdown, salary metrics, trend view)
- **Customer Management** — moved to top-level (currently only in Settings); operational screens (Customers, Rate Cards, Project Codes) live here; Settings > Customer Management tab becomes config-only (concentration risk thresholds)
- **Revenue** (future) — Imports (Zoho Books), Revenue Data, Revenue Dashboard
- **Budgeting & Forecasting** (future) — Plan vs. Actual, Scenarios, Forecasting
- **Settings** — General, People & Payroll config (classification rules), Customer Management config (thresholds)

**Consequences**
- (+) Users navigate by what they're working on, not what action they're performing — more intuitive for a daily-use tool.
- (+) Each module's full workflow (import → process → view → configure) is self-contained under one nav item.
- (+) Consistent pattern across all future modules — Revenue, Budgeting follow the same structure without redesign.
- (–) Customer Management needs a nav refactor — currently operational screens are under Settings, needs to move to a top-level section.

---

## ADR-022: Budgeting & Forecasting Data Consumption — Event-Driven Snapshot on Period Finalisation

**Status:** Accepted — July 2026

**Context**
Budgeting & Forecasting is the downstream consumer of People & Payroll's Master data (for the "Actual" side of Plan vs. Actual). Two options were considered: (A) query People & Payroll live via its public service API on demand, or (B) receive aggregated data via a domain event when a period is finalised and store its own copy.

**Decision**
Option B — event-driven snapshot on finalisation. When Finance finalises a period in People & Payroll, `PeoplePayrollService.finalisePeriod()` publishes a `PeriodFinalisedEvent` via Spring Modulith's in-process event mechanism. Budgeting & Forecasting listens via `@ApplicationModuleListener` and stores the aggregated People actuals it needs into its own tables — these become the "Actual" side of Plan vs. Actual for that period.

Aggregated figures stored by Budgeting from the event: Billable/Bench/Support/Leadership/Management headcount and salary cost totals, per-BU billable HC and salary cost (for BU-level P&L), per-PU billable HC and salary cost. Period is referenced by month/year value — not a FK into People & Payroll's tables (cross-module boundary preserved per ADR-008).

**Consequences**
- (+) Finalisation is the natural, correct trigger — Budgeting should only work with finalised People data, not provisional open-period figures.
- (+) Budgeting & Forecasting is independent after the event — no ongoing runtime dependency on People & Payroll for historical period queries.
- (+) Dependency is strictly one-directional: People & Payroll fires and forgets; Budgeting listens and stores.
- (+) Uses Spring Modulith's native event mechanism — consistent with ADR-008, no message broker needed.
- (+) Aligns with ADR-007's note that scoped, lightweight domain events were explicitly not ruled out — this is exactly that use case.
- (–) Budgeting stores a copy of aggregated People data — small duplication, justified by the decoupling benefit.
- (–) If a finalised period is reopened and re-finalised with corrected data, Budgeting must handle the re-published event and update its stored actuals — needs explicit handling in the listener.

**Alternatives considered**
- Live query via `PeoplePayrollService.getPeriodSummary(periodId)` — rejected: creates ongoing runtime dependency, doesn't enforce the finalisation boundary, and complicates multi-period Plan vs. Actual queries that span many periods simultaneously.

---

## ADR-023: Snapshot Upload Warning Persistence for Import Preview

**Status:** Accepted — July 2026

**Context**
The People REST API requires `POST /api/people/imports/{periodVersionId}/upload` to return unmapped/missing Excel columns and unrecognised BU codes as informational warnings, and `GET .../preview` to resurface those same warnings after upload. `snapshot_upload` previously stored only audit metadata (who, when, filename, row count) — not the column/BU diagnostics from the parse.

**Decision**
Add nullable TEXT columns on `snapshot_upload`: `unmapped_columns`, `missing_columns`, `unrecognized_bu_codes` (comma-separated values). Written at upload time; preview aggregates distinct values across all uploads for the period version. Warnings remain non-blocking (ADR-019 informational mapping gaps; Module 1 §9.4 BU validation as a surfaced gap, not a gate).

**Consequences**
- (+) Preview can show upload diagnostics without a separate session/cache store.
- (+) Audit trail retains what Finance saw on each upload.
- (–) Comma-separated TEXT is simple but not normalised — acceptable at current volume; can move to JSONB later if needed.

**Alternatives considered**
- In-memory / Redis session store for last-upload warnings — rejected: lost on restart, not attributable per upload.
- Separate `snapshot_upload_warning` child table — deferred as overkill for MVP.

---


## ADR-024: Snapshot Detail Screen — All Three Import Types, No Original File Download
 
**Status:** Accepted — July 2026
 
**Context**
After uploading a snapshot file, Finance had no way to inspect the imported rows before building Master records. A mapping error or a wrong file would only be discovered after the Master build, requiring a re-upload. A snapshot detail screen was identified as a missing piece.
 
**Decision**
Add a `SnapshotDetailPage` accessible from two places: the upload result (Step 4) via "View imported rows" link, and Period Management via "View Snapshots" per import type per version. One shared backend endpoint: `GET /api/people/imports/{periodVersionId}/snapshots/{importType}` returns all rows for that version and import type plus snapshot_upload metadata (uploaded_by, uploaded_at, filename, row_count, warnings). Full table display (all rows loaded client-side, default page size 20, client-side search by Employee ID/Name). Columns differ by import type: Zoho People (Employee ID, Full Name, PU, BU, BU Code, Project Code, Billable Status, Job Level, Title, DOJ), Zoho Payroll (Employee No, Full Name, Gross Pay, Net Pay, CTC), Zoho People Exited (same as Zoho People + Last Working Day). Warning alerts (unmapped/missing columns, unrecognised BU codes) shown non-blocking per ADR-019.
 
No original file download — the system stores only mapped fields (ADR-019), not raw Excel bytes. An export-back-to-Excel option was considered and explicitly deferred.
 
For ZOHO_PEOPLE_EXITED: `employee_registry` needs a `last_updated_by_upload_id` nullable FK (V7 migration) so the detail screen can query which registry rows were updated by a specific upload.
 
**Consequences**
- (+) Finance can sanity-check imported data before committing to Master build.
- (+) Warnings surfaced at detail time, not just at upload time.
- (–) No original file download — acceptable since the stored data is the mapped extract, not the raw file.
---
 
## ADR-025: Date Format Configuration in General Settings
 
**Status:** Accepted — July 2026
 
**Context**
Multiple screens display dates (snapshot upload dates, period labels, DOJ, exit dates, rate card effective dates). Hardcoding a format (e.g. DD MMM YYYY) in individual components creates inconsistency and makes format changes expensive. A configurable date format in General settings was identified as the right solution.
 
**Decision**
Add a date format setting to General configuration (owned by the General module per ADR-012). The setting stores a format string (e.g. `DD MMM YYYY`, `DD/MM/YYYY`, `MM/DD/YYYY`). The frontend reads this setting from `GET /api/general/config/date-format` on app load and stores it in a React context or global state — all date rendering across the app uses this format via a shared utility function `formatDate(date, format)` rather than inline formatting. Additional format types (time, month-only, fiscal year) are added to this config as the need arises — not invented upfront.
 
**Consequences**
- (+) Consistent date display across all screens from a single config point.
- (+) Finance can change the display format without a code change.
- (+) Shared utility function means format changes propagate everywhere automatically.
- (–) Adds one API call on app load — negligible overhead.
**Alternatives considered**
- Hardcode DD MMM YYYY throughout — rejected: creates inconsistency and makes changes expensive.
- Per-screen format configuration — rejected: overkill, no identified need for different formats on different screens.
---

## ADR-026: ZOHO_PAYROLL_FNF — Full & Final Payroll Sub-Type

**Status:** Accepted — July 2026

**Context**
Exited-employee full-and-final settlements arrive as a separate Zoho Payroll export with the same column format as regular payroll but different business meaning. Finance needs to upload both independently while viewing and reconciling them together.

**Decision**
Add `ZOHO_PAYROLL_FNF` as a fourth import type (extends ADR-020). Same upload flow and column mapping mechanism as `ZOHO_PAYROLL`. `payroll_snapshot.import_type` distinguishes regular vs F&F rows. Re-upload of one sub-type replaces only that sub-type's rows for the period version. `SNAPSHOTS_UPLOADED` requires People snapshot plus at least one payroll upload (regular and/or F&F). Master build: unmatched F&F rows reconcile via Employee Registry — `EXITED` → `AUTO_MATCHED_EXITED`, `ACTIVE` or not found → `UNMATCHED`. Snapshot detail for either payroll path returns all payroll rows combined with per-row `importType`; Period Management shows a single "View Payroll" button.

**Consequences**
- (+) F&F settlements tracked separately without a second mapping format.
- (+) Combined payroll detail view gives Finance one place to review all payroll rows.
- (+) Same employee may appear in both regular and F&F uploads for one period version (unique on `period_version_id`, `employee_no`, `import_type` — V10 migration).

**Revision (July 2026):** Initial constraint `(period_version_id, employee_no)` was relaxed to include `import_type` after Finance confirmed employees routinely appear in both regular payroll and F&F settlement for the same period.

---

## ADR-027: Customer Import — Excel Upload with User-Controlled Conflict Resolution
 
**Status:** Accepted — July 2026
 
**Context**
Customers were added manually one by one. For initial data load (migrating from the existing Customer Code/Project Code spreadsheet) and bulk updates, an Excel import was identified as necessary.
 
**Decision**
Add customer import to Settings → Customer Management → Customers section. Two new buttons: "Download Sample File" (client-side SheetJS, no API — generates empty Excel with headers: Customer Code, Customer Name, Zoho Books Customer Ref, Lifecycle Status, DSO Days, Relationship Owner Employee ID) and "Import Customers" (multi-step modal flow). Import scope: Customer Master only — Project Codes import deferred. Conflict resolution is user-controlled: system detects conflicts via a pre-flight endpoint before import, shows conflicting codes, and asks user to choose SKIP or REPLACE. Defaults: Lifecycle Status → ACTIVE if blank/invalid, DSO Days → 30 if blank/invalid. Import response includes created/updated/skipped counts and per-row errors. Scope limited to Customer Master — Project Codes import deferred.
 
**Consequences**
- (+) Initial data load from existing spreadsheet is now self-service for Finance.
- (+) User-controlled conflict resolution avoids silent overwrites.
- (+) Sample file download ensures correct column names without documentation.
- (–) Project Codes not included in this import — deferred to a later prompt.

**Revision (July 2026):** Conflicts pre-flight endpoint implemented as `POST /api/customers/import/conflicts` (not GET) because browsers cannot send multipart file bodies on GET requests.

---

## ADR-028: Rate Card Import — Excel Upload with Active-Card Skip Rule

**Status:** Accepted — July 2026

**Context**
Customer import (ADR-027) covers Customer Master initial load. Rate cards remain manual one-by-one entry. Finance needs bulk import of effective-dated rate cards from spreadsheet, consistent with ADR-015's single-currency-per-card model.

**Decision**
Add rate card import to Settings → Customer Management → Rate Cards section. Excel structure: one row per rate card line; rows grouped by Customer Code + Rate Card Name + Effective From. FLAT = one row (Job Level blank); TIERED = N rows per job level. Import creates rate cards only when the customer has no active card (`effective_to IS NULL`) — otherwise skips the group (does not supersede; unlike manual create). Unknown customer codes are row errors; customers are never created on the fly. Sample template served via `GET /api/customers/rate-cards/import/sample` (Apache POI). Response includes `skipped` detail list for UI filtering.

**Consequences**
- (+) Initial rate card load is self-service for Finance.
- (+) Skip-when-active prevents silent overwrite of live billing terms during bulk import.
- (+) Currency on card header, single `rate_amount` per line — consistent with ADR-015.
- (–) Import cannot supersede an active card; Finance must close/replace manually or clear active card first.

---

## ADR-029: Internal BU Model — Phase 1

**Status:** Accepted — July 2026

**Context**
Zoho People uses Business Unit values for both external clients and internal organisational units (Management, Leadership, Pool, L&D, Business Enabler Function). These internal BUs need to exist in Customer Master for BU validation during People snapshot upload, but must not appear alongside external clients in billing workflows. Billable classification incorrectly required IsDeliveryPU for billable flag, excluding legitimately billable Leadership members.

**Decision**
Add `customer.is_internal` flag; seed five internal BU customers (MGMT, LDSP, POOL, LND, BEF) via Flyway V11 (V10 already taken by payroll snapshot constraint). Add `master_record.billing_customer_code` — derived at master build from project code → customer_project_code lookup when IsBillable=true. Correct IsBillable rule to Billable Status=Y only (IsBench unchanged). `GET /api/customers?includeInternal=false` by default; Customers table shows internal BUs with distinct styling; rate card management disabled for internal BUs in Phase 1.

**Consequences**
- (+) Internal BU codes validate cleanly during People upload — no false unrecognised-BU warnings.
- (+) Billable Leadership members correctly flagged IsBillable=true alongside IsLeadership=true.
- (+) Billing client join key available on master records for future Revenue module.
- (–) Rate cards not supported for internal BUs until a later phase.

---


## ADR-029: Internal BU Model — Phase 1 (is_internal flag on Customer, billing_customer_code on Master)
 
**Status:** Accepted — July 2026
 
**Context**
Certain BUs in Zoho People are internal organisational units (Management, Leadership, Pool, Learning & Development, Business Enabler Function) with no corresponding external client. The existing model had no way to distinguish internal BUs from client BUs, causing: internal BU employees generating unrecognised BU warnings on import; Leadership members who are billable to clients being incorrectly excluded from IsBillable due to the IsDeliveryPU requirement; and no clean way to track internal BU salary % vs overall or derive which client a billable leadership member is billing to.
 
**Decision — Phase 1**
Three changes applied together:
 
1. **`is_internal` flag on `customer` table.** Internal BUs get Customer Master records with `is_internal = true` — seeded: Management (MGMT), Leadership (LDSP), Pool (POOL), Learning & Development (LND), Business Enabler Function (BEF). This keeps every BU consistently mapped in Customer Management while making internal vs external distinction explicit. Internal customers are filtered out of all revenue-facing dropdowns and lists by default, with an optional "Show internal BUs" toggle.
2. **`IsBillable` rule corrected.** Changed from `IsDeliveryPU=Y + Billable Status=Y + BU ≠ Management` to simply `Billable Status=Y`. A Leadership member with Billable Status=Y is genuinely billable to a client. IsDeliveryPU requirement removed from IsBillable specifically — retained for IsBench calculation only. Salary bucketing priority rule unchanged: Leadership bucket always wins for salary attribution regardless of IsBillable.
3. **`billing_customer_code` added to `master_record`.** Derived by: employee.project_code → `customer_project_code.project_code` lookup → parent `customer.customer_code`. Stored as a soft reference (VARCHAR, no FK — cross-module boundary per ADR-008). Null for non-billable employees or where project code has no match. For single-product clients where BU Code = Project Code (e.g. Nexxa AI: NXAI = NXAI), the derivation works identically — no special handling needed.
**Billing client derivation logic (confirmed):**
`employee.project_code → customer_project_code → customer.customer_code` — not BU-based. BU is the organisational home; Project Code determines the billing client.
 
**Phase 2 (deferred):**
Correct the underlying model more explicitly — separating org-home from billing assignment more formally. May involve Zoho People model changes. Not yet specified.
 
**Consequences**
- (+) All three use cases resolved: internal BU salary % tracking, headcount including internal BUs, Leadership members correctly billable to clients.
- (+) No Zoho People model changes needed — existing data (Billable Status + Project Code) is already correct.
- (+) BU validation no longer generates false warnings for internal BU values.
- (+) `billing_customer_code` on Master records enables Budgeting & Forecasting to correctly attribute billable leadership cost to the right client for BU-level P&L (Phase 2 consumer).
- (–) Internal BUs as Customer records is a pragmatic Phase 1 compromise — not the cleanest long-term model (hence Phase 2).
**Alternatives considered**
- Separate `INTERNAL_BU` classification config type — rejected in favour of `is_internal` on Customer to keep all BU references in one place.
- Using BU to derive billing client instead of Project Code — rejected: BU is org home, not billing assignment. Project Code is the correct join key into Customer Management.

---

## ADR-030: Master Record Data Quality Flags

**Status:** Accepted — July 2026

**Context**
After ADR-029 introduced `billing_customer_code` derivation from project code, Finance needs visibility into master records where billing client cannot be resolved — missing project codes on external BUs, unregistered project codes, or billable employees with no derived billing client. These are data-quality issues in Zoho People or Customer Management, not reconciliation gaps.

**Decision**
Add nullable `master_record.data_quality_flags` (VARCHAR 500, comma-separated) populated at master build time. Flag values: `MISSING_PROJECT_CODE`, `PROJECT_CODE_NOT_FOUND`, `BILLING_CLIENT_UNRESOLVED`. External BU employees (customer `is_internal = false`) with blank project code get `MISSING_PROJECT_CODE`; external BU with unknown project code get `PROJECT_CODE_NOT_FOUND`; billable employees with null `billing_customer_code` get `BILLING_CLIENT_UNRESOLVED`. Internal BU employees with blank project code are normal — no flag. Exposed on `MasterRecordResponse` with derived `hasWarnings` boolean. Master Data UI shows warning indicator, filter, and summary card.

**Consequences**
- (+) Finance can quickly identify employees needing Zoho People corrections before period finalisation.
- (+) Flags are persisted on master records for audit and downstream reporting.
- (–) Flags are point-in-time at master build — rebuilding master refreshes them.

---

## ADR-031: Internal BU Customer Code Corrections

**Status:** Accepted — July 2026

**Context**
V11 internal BU seeds had incorrect codes for some units (Leadership mis-coded as LND on early deployments; Business Enabler Function as BEF with singular name). Finance also needs to edit internal BU customer records (code, name, lifecycle status) from Customer Management settings.

**Decision**
Flyway V13 corrects `LND` → `LNDT` where LDSP does not already exist, and `BEF` → `BEFN` with name "Business Enabler Functions". `PUT /api/customers/{id}` allows `customerCode` updates for `is_internal = true` customers only. Frontend Customers table enables edit modal for internal BUs with simplified fields and an "Internal BU" tag.

**Consequences**
- (+) Internal BU codes align with Zoho People BU values.
- (+) Finance can correct internal BU metadata without DB access.
- (–) External client `customer_code` remains immutable after creation (spec §7).

---

## ADR-032: Customer Management Export and Project Code Import

**Status:** Accepted — July 2026

**Context**
ADR-027 and ADR-028 added Excel import for customers and rate cards. Finance needs round-trip export in the same column layout for backup, migration, and re-import. Project Codes import was deferred in ADR-027.

**Decision**
Add Apache POI Excel exports: `GET /api/customers/export`, `GET /api/customers/rate-cards/export`, `GET /api/customers/project-codes/export` — column layouts match import templates. Add project code import via `POST /api/customers/project-codes/import` with skip-existing behaviour (no conflict resolution). Rate card import sorts groups by Customer Code + Effective From ASC before processing so full-history re-import creates cards chronologically.

**Consequences**
- (+) Finance can export and re-import customer master data without manual reconstruction.
- (+) Project code bulk load completes ADR-027 deferred scope.
- (+) Historical rate card export + sorted re-import supports migration workflows.

---

*(Further ADRs to be added as decisions are finalized.)*
