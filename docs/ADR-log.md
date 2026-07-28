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

## ADR-032: Customer Management Export/Import Symmetry
 
**Status:** Accepted — July 2026
 
**Context**
Manual data entry for Customers, Rate Cards, and Project Codes is slow for initial setup and testing. Export/import symmetry needed for bulk load and environment reset.
 
**Decision**
Export endpoints for all three Customer Management entities (GET /api/customers/export, GET /api/customers/rate-cards/export, GET /api/customers/project-codes/export) return Excel in the same format as their corresponding import endpoints. Rate card export sorts by Customer Code ASC + Effective From ASC so historical re-import processes cards in correct chronological order. Customer export includes internal BUs (is_internal=true). Rate card export includes full history (all effective_to values). Project codes import skips existing codes (no conflict resolution needed — always skip). Rate card import updated to process groups sorted by Effective From ASC.
 
**Consequences**
- (+) Finance can export current state, reset DB, and restore via import — full round-trip fidelity.
- (+) Initial data load from existing spreadsheets is self-service.
---
 
## ADR-033: Period Version Re-upload — SUPERSEDED Status + Per-Import-Type Version Bump
 
**Status:** Accepted — July 2026
 
**Context**
Re-uploading a snapshot file against a period version that already has rows for that import type caused a unique constraint violation. Two approaches considered: delete-then-insert (transaction risk) and auto-version-bump.
 
**Decision**
Re-upload triggers an automatic version bump using the existing period_version model. Rules:
- Upload import type X against a version that has NO existing rows of type X → normal insert into current version (first upload for this type).
- Upload import type X against a version that ALREADY HAS rows of type X → current version status set to SUPERSEDED, new version created (version_number + 1, status OPEN), new snapshot rows inserted under the new version.
- Upload against a FINALISED version → rejected with HTTP 400. Finance must use "New Version" button explicitly.
- SUPERSEDED versions retained for full audit trail but excluded from Master build and all operational views.
- This rule applies to ALL current and future upload types (ZOHO_PEOPLE, ZOHO_PAYROLL, ZOHO_PEOPLE_EXITED, ZOHO_PAYROLL_FNF, and any subsequently added types). Documented as a standing contract on the upload method.
Period version status values updated: OPEN / SNAPSHOTS_UPLOADED / MASTER_BUILT / FINALISED / SUPERSEDED (V15 migration).
 
**Consequences**
- (+) No transaction risk — old rows stay, new rows inserted fresh under a new version.
- (+) Full audit trail — SUPERSEDED versions visible in Period Management in muted style.
- (+) Consistent with the versioning model philosophy — never mutate, always append.
- (+) Master always uses the latest non-superseded version.
---
 
## ADR-034: Frontend — pnpm Migration + Customer Management Top-Level Nav
 
**Status:** Accepted — July 2026
 
**Context**
npm was the default package manager from project setup. pnpm offers faster installs, disk efficiency via hard-linking, and stricter dependency resolution. Separately, Customer Management operational screens were in Settings, violating ADR-021's domain-first navigation principle.
 
**Decision**
Two changes applied together:
 
1. **pnpm migration** — `pnpm-lock.yaml` replaces `package-lock.json`. `packageManager: pnpm@11.13.0` in package.json (explicit version for Corepack compatibility — `latest` is invalid). `pnpm-workspace.yaml` with `allowBuilds.esbuild: true` (required by pnpm v11). Dockerfile updated to use `pnpm install --frozen-lockfile` + `pnpm build`. `.gitignore` updated for `.pnpm-store/` and `pnpm-debug.log*`.
2. **Customer Management nav refactor (ADR-021 applied)** — Customer Management moves to top-level nav alongside Dashboard, People & Payroll, and Settings. Three sub-sections: Customers, Rate Cards, Project Codes at routes `/customer-management/customers|rate-cards|project-codes`. Settings → Customer Management tab becomes config-only (Concentration Risk thresholds + Watch Groups). Move note added to Settings tab. Dashboard client BU links updated to `/customer-management/customers`.
Final nav: Dashboard → People & Payroll → Customer Management → Settings.
 
**Consequences**
- (+) pnpm: faster installs, disk-efficient, stricter dependency resolution.
- (+) Bundle note: 2.1MB uncompressed / 657KB gzipped — acceptable for current scale, monitor as modules are added; lazy loading by route is the mitigation if bundle grows past ~3MB.
- (+) ADR-021 now fully applied to Customer Management — operational screens in correct domain-first location.
---

## ADR-035: Project-Scoped Rate Cards Alongside Customer-Level Blended Cards

**Status:** Accepted — July 2026 (revised July 2026 — many-to-many)

**Context**
Module 2 §6 and the original `no_overlapping_rate_cards` exclusion constraint assumed a client has exactly one active rate card at a time. Real contracts often quote different rates per engagement/project, and a single commercial rate card frequently covers several project codes under one customer (e.g. ENGN, CLOUD_OPS, DEV_OPS). Revenue needs to resolve a project-specific card when available, falling back to the customer-level blended card. ADR-015 (single currency per card) and ADR-028 (import skip-when-active) remain in force but must apply per scope.

**Decision**
- **V16 (initial):** nullable `rate_card.project_code_id` — one project per card.
- **V17 (revised):** drop `project_code_id`; introduce `rate_card_project_code` join table (many-to-many). Empty associations = blended (customer-level) card. A project code may belong to at most one *active* rate card per customer — enforced in `CustomerService` (returns HTTP 409), not a DB exclusion, because active/inactive is `rate_card.effective_to`. Soft UUID references on the join entity (no JPA associations to keep joins optional). Manual create does not auto-close existing cards; edit uses versioning. Export Project Code column is semicolon-separated codes; import splits on `;`. Cross-module lookups: `findActiveRateCardForProjectCode` (join + effective dates) and `findActiveBlendedRateCard` (card with no join rows).

**Consequences**
- (+) One rate card can cover many project codes; multiple active project-scoped cards allowed when project sets do not overlap.
- (+) Contradicts Module 2 §6 literal “exactly one active rate card” — superseded for project scope; blended uniqueness retained in application layer (DB exclusion for blended removed with V17 column drop).
- (+) Edit-via-versioning preserves point-in-time history (Module 1 §6.1 / rate card effective dating).
- (–) Active project-code exclusivity depends on service validation; concurrent inserts without locking could race (acceptable at Finance-user scale).
---
 
 ## ADR-036: Rate Card to Project Code — Many-to-Many via Join Table
 
**Status:** Accepted — July 2026
 
**Context**
ADR-035/V16 added a single `project_code_id` nullable FK on `rate_card`. In practice, a single rate card often covers multiple project codes for the same customer (e.g. Icertis has 4 project codes — ENGN, CLOUD_OPS, DEV_OPS, AI_ML — all on one rate card). One-to-one is too restrictive.
 
**Decision**
Replace `rate_card.project_code_id` with a `rate_card_project_code` join table (V17 migration). Many-to-many: one rate card → many project codes; one project code → one active rate card per customer at a time (enforced at application layer, not DB constraint, since active state lives on `rate_card.effective_to`). Conflict validation in `CustomerService`: creating/versioning a rate card returns 409 if any requested project code is already assigned to another active rate card for that customer. `findActiveRateCardForProjectCode()` queries via join. Blended rate cards = no join rows. Export/import: project codes as semicolon-separated list per rate card row. Frontend: multi-select for project codes; rate cards grouped by card (not by project code); project codes shown as tags.
 
**Three rate card types:**
- **Multi-project** — associated with 2+ project codes
- **Single-project** — associated with exactly 1 project code
- **Blended** — no project codes (one active per customer)
**Revenue lookup (for future Revenue module):**
`employee.project_code → rate_card_project_code → rate_card (active on period date)`. If no project-scoped card found → fall back to blended rate card for that customer.
 
**Consequences**
- (+) Correctly models real-world billing where one rate applies across multiple projects.
- (+) Revenue module has a clean, unambiguous lookup path.
- (+) 409 conflict detection prevents accidental dual-assignment of a project code.
- (–) Application-layer enforcement (not DB constraint) means concurrent writes could theoretically bypass it — acceptable at current scale and user count.
---
 
## ADR-037: Budgeting & Forecasting — Core Model (Financial Year Plan, Forecast Types, Versioned Baseline, Rolling Forecast, Delta)
 
**Status:** Accepted — July 2026
 
**Context**
Budgeting & Forecasting is the original goal of the system — the downstream consumer of People & Payroll and Revenue actuals, replacing the manual Excel AOP model (Cognologix_FY2627_v9.xlsx, 14 sheets). Requirements were derived from a detailed review of the Excel model and a structured discovery session.
 
**Decision**
One `financial_year_plan` per Indian Financial Year (April–March). Three forecast types seeded: NORMAL (primary), AGGRESSIVE, CONSERVATIVE — Finance can add more. Each forecast type has versioned plan inputs: DRAFT → ACTIVE → SUPERSEDED. Only one ACTIVE version per forecast type at any time.
 
**Baseline** = current ACTIVE version of NORMAL forecast (not "April v1 forever" — when Finance supersedes with a revision, the Delta resets to Rolling Forecast − new active version). This answers "how are we tracking against our current plan" rather than an outdated April commitment.
 
**Rolling Forecast** = Actuals (from PeriodFinalisedEvent for HC/Salary; manual for Revenue/Overhead until respective modules exist) + current ACTIVE Normal forecast for future months. Computed on demand, not stored.
 
**Delta** = Rolling Forecast − Baseline. All six dimensions tracked: Revenue, HC, Salary Cost, Overhead, Gross Margin, EBITDA. Traffic-light color coding on the Delta panel.
 
**Plan inputs per forecast version:** HC Plan (hires, exits, HC by category), Client Revenue Plan (Finance enters T&M and Fixed-Bid manually per client per month — no rate card auto-calculation), Salary Budget (per category, can vary month-to-month), Overhead Budget (23 line items across 6 categories matching Excel Budget sheet).
 
**Actuals:** HC + Salary via PeriodFinalisedEvent (ADR-022, authoritative, not overridable). Revenue actuals: Finance enters manually as placeholder until Revenue module is built. Overhead actuals: Finance enters manually (Tally Prime deferred).
 
**General Settings additions:** Working days per month, annual attrition rate, target billable ratio, opening HC per FY — owned by General config (ADR-012).
 
**Consequences**
- (+) Baseline versioning matches real-world planning — Finance revises mid-year when assumptions change, Delta always reflects current expectations.
- (+) Manual T&M Revenue Plan removes rate card dependency from planning workflow entirely.
- (+) PeriodFinalisedEvent actuals are automatic and authoritative — no monthly manual copy from PeopleData workbook.
- (–) Revenue actuals placeholder (manual entry) until Revenue module is built.
---
 
## ADR-038: Budgeting & Forecasting — BU Analysis, Cost per Employee, Single Dashboard UI
 
**Status:** Accepted — July 2026
 
**Context**
Two analytical features were added during requirements discovery: BU-level profitability drill-down (including employee-level salary visibility), and a cost-per-employee calculation Finance uses as the baseline for client rate negotiation. Additionally, the UI structure was decided: single scrollable dashboard vs separate sub-section pages.
 
**Decision**
**BU Analysis (Option A — link, not duplicate):** Budgeting & Forecasting BU Metrics panel shows per-BU aggregated figures (Revenue, Salary Cost, Gross Margin, Avg Salary per Head, Billable HC — Plan vs Actual). Employee-level drill-down navigates to People & Payroll → Master Data filtered by BU and period, rather than duplicating employee records in this module. Consistent with ADR-008 cross-module boundary rule.
 
**Cost per Employee — Full Absorption Costing (Model 1):** All shared overhead allocated to billable employees only (they fund all fixed costs via revenue). Three layers: Layer 1 = Direct Salary + Statutory Benefits (13%); Layer 2 = Direct Overhead per head (medical, welfare, consumables, software, training); Layer 3 = Allocated Shared Overhead ÷ billable HC only. Bench/Support/Leadership carry Layers 1 + 2 only (no Layer 3). Total Cost per Billable Head = the minimum billing rate Finance needs to break even. A "Target Billing Rate" calculator (enter margin % → see resulting rate) aids rate card validation. Model 1 chosen over ABC (too complex at this scale) and Marginal Costing (doesn't give the negotiation baseline needed). Phase 2 refinement: split overhead allocation by Practice Unit if needed.
 
**Dashboard UI (Option A — single scrollable page):** Analysis views consolidated into one scrollable Dashboard page with 9 panels (Headline KPIs, Rolling Forecast trend, Plan vs Actual Revenue/HC/Costs, BU Metrics, P&L Summary, Cost per Employee, Delta View). Plan Setup and Scenario Comparison remain as separate pages (data entry / side-by-side layout respectively). Panel order to be iterated once tested with real data.
 
**Consequences**
- (+) BU drill-down via link keeps module boundaries clean — no data duplication.
- (+) Full Absorption gives Finance a single defensible number for rate negotiation — "true loaded cost per billable head."
- (+) Single dashboard reduces navigation overhead for Finance's monthly review workflow.
- (–) Dashboard panel order is a first-pass assumption — real usage patterns may suggest reordering.
---

## ADR-039: Revenue Module — Core Model (Zoho Books Import, Invoice Level, Client-Level Aggregation, Direct Query)
 
**Status:** Accepted — July 2026
 
**Context**
Revenue is the last major module needed to replace the manual revenue actuals placeholder in Budgeting & Forecasting. Requirements discovered through a structured discovery session covering billing models, Zoho Books integration approach, data granularity, payment tracking, revenue recognition, and Budgeting & Forecasting consumption pattern.
 
**Decision**
**Ingestion:** File upload (Zoho Books export) for Phase 1, API later — same phased approach as People & Payroll (ADR-019). One import type: `ZOHO_BOOKS_INVOICES`. Same column mapping template pattern as People & Payroll — Finance maps Zoho Books export headers to system attributes; template saved per import type, pre-fills on subsequent uploads; unmapped/missing columns surfaced as warnings, non-blocking.
 
**Period attribution:** Finance explicitly selects the period (month/year) at upload time — same as People & Payroll. `Invoice Month` field in Zoho Books export is deprecated (replaced by Service Month/Service Year custom fields in newer exports) — not reliable for auto-attribution. Manual period selection is consistent and explicit.
 
**Data granularity:** Individual invoice records stored (Invoice Number, Customer Code, Invoice Date, Status, Amount, Balance, Due Date, Project Code — optional) + monthly summaries computed from individual records. Client-level only — multiple project codes under one client can share one invoice; project-level revenue breakdown deferred to Phase 2.
 
**Credit notes:** Stored as negative-amount invoice records (same import, negative Total value in Zoho Books export) — reduce net revenue for the tagged period automatically. If credit notes appear as a separate export, a second import sub-type `ZOHO_BOOKS_CREDIT_NOTES` handles them with the same flow.
 
**Payment status:** Imported passively from Zoho Books Status column (Paid/Partially Paid/Sent/Overdue/Void) and Balance column (outstanding amount). No active collections workflow built — Zoho Books remains the system of record for collections. DSO informational only — displayed on Revenue Dashboard, not tracked as a workflow.
 
**Revenue recognition:** Net Revenue per client per period = Sum of invoice Amounts (positive) + credit note Amounts (negative) for that customer_code and tagged period.
 
**Budgeting & Forecasting consumption:** Direct query via `RevenueService.getMonthlyRevenueSummary(customerId, month, year)` — no event (ADR-022 event pattern not used here because revenue actuals aren't "finalised" the way People & Payroll periods are; invoices can be corrected until Finance explicitly closes the period). Budgeting & Forecasting replaces its `actual_revenue_manual` placeholder with this query.
 
**System attributes for column mapping template:**
InvoiceNumber (required), CustomerName (reference), CustomerCode (required — join key), InvoiceDate (required), Status (required), Amount (required), Balance (optional — DSO), DueDate (optional), ProjectCode (optional — stored, not aggregated), ServiceMonth/ServiceYear (optional — ignored if Finance uses manual period).
 
**Navigation (ADR-021):** Revenue top-level nav → Imports (Zoho Books Invoices) → Invoice List → Revenue Dashboard (Revenue vs Plan per client, Invoice Status Summary, DSO informational).
 
**Consequences**
- (+) Same import/template pattern as People & Payroll — Finance already knows the workflow.
- (+) Individual invoice storage gives full audit trail and enables future project-level analysis.
- (+) Direct query (no event) correctly handles mid-month corrections without re-publishing.
- (+) Passive payment status import removes need for a collections workflow while still surfacing DSO.
- (–) Revenue period must be manually tagged — minor operational overhead, offset by consistency and reliability.
- (–) Project-level revenue breakdown deferred — acceptable since invoices often span multiple projects.
---
 
 ## ADR-040: Revenue — Separate Credit Notes Import Type (ZOHO_BOOKS_CREDIT_NOTES)
 
**Status:** Accepted — July 2026
 
**Context**
Credit notes in Zoho Books reduce invoiced revenue for a period. Two options considered: (1) include credit notes in the same ZOHO_BOOKS_INVOICES export as negative-amount records, or (2) treat credit notes as a separate import type with its own export and column mapping template.
 
**Decision**
Separate import type: `ZOHO_BOOKS_CREDIT_NOTES`. Credit notes are exported from Zoho Books as a separate report and uploaded via a distinct import flow, identical in structure to ZOHO_BOOKS_INVOICES but with credit-note-specific column names (Credit Note#, Credit Note Date, etc.). Credit note amounts stored as positive values; treated as negative in all net revenue calculations. Net Revenue per client per period = Sum(invoice amounts) − Sum(credit note amounts).
 
This mirrors the ZOHO_PAYROLL vs ZOHO_PAYROLL_FNF pattern established in ADR-026 — a distinct sub-type with different semantics handled cleanly rather than mixed into the primary import with sign-based disambiguation.
 
**Consequences**
- (+) Clear separation of invoices and credit notes in the audit trail — Finance can see what was invoiced and what was credited independently.
- (+) Consistent with the established pattern of separate import types for related-but-distinct record types (ADR-026).
- (+) Each import type has its own saved column mapping template — Finance maps once, reuses every month.
- (−) Finance must run two separate Zoho Books exports and two uploads per period rather than one — minor operational overhead, offset by audit clarity.
**Alternatives considered**
- Single import with negative Total values for credit notes — rejected: mixes record types in one upload, makes the audit trail less clear, and requires Finance to know to look for negative values in the same file.
---

## ADR-041: Revenue Module Implementation — Shared Mapping Table + Direct Plan Lookup

**Status:** Accepted — July 2026

**Context**
Implementing Module 4 (Revenue) required deciding how to reuse the existing `import_column_mapping` table (ADR-019) from a new Spring Modulith module without violating package boundaries, and how the Revenue Dashboard obtains planned figures from Budgeting & Forecasting.

**Decision**
1. **Shared mapping table, people-owned JPA, revenue via public API.** V20 extends `import_column_mapping.import_type` CHECK with `ZOHO_BOOKS_INVOICES` / `ZOHO_BOOKS_CREDIT_NOTES`. People `ImportType` enum gains the same values so Hibernate can deserialize shared rows. People keeps JPA ownership of the table; Revenue calls `PeoplePayrollService` mapping methods that return `MappingTemplateApi` (people root package) — never `people.domain` types. People `findActiveMappings()` excludes books types so Settings → People is not polluted.

2. **Revenue upload versioning on `revenue_upload`.** SUPERSEDED version-bump (ADR-033) applies per `import_type` + period on `revenue_upload` itself (not people `period_version`) — mark prior ACTIVE as SUPERSEDED, insert version_number+1.

3. **`BudgetingService.getClientRevenuePlan(customerId, month, year)`.** New public method resolves the financial year covering that calendar month, loads the active primary baseline, and returns planned TM + fixed-bid total (or empty). Revenue Dashboard uses this for revenue-vs-plan variance (ADR-039), replacing reliance on `actual_revenue_manual` for the plan side.

**Consequences**
- (+) No duplicate mapping schema; Finance reuse of ADR-019 templates.
- (+) Modulith boundaries preserved (revenue → people/budgeting/customer/general root APIs only).
- (–) Revenue mapping storage remains people-owned at the DB/JPA layer — acceptable reuse; extract to general only if a third consumer appears.

**Clarification (frontend, July 2026):** Revenue Dashboard DSO informational panel uses average of `(today − Invoice Date)` for unpaid / non-void invoices and surfaces the oldest outstanding invoice date per client — operational DSO age, not contractual due-window length. Spec §7 / ADR-039 wording on Due Date − Invoice Date remains the conceptual payment-window view; UI follows the operational aging definition used by Finance for collections visibility.

---


## ADR-042: Authentication — Database Users with ADMIN / VIEWER Roles

**Status:** Accepted — July 2026

**Context**
ADR-005 established self-managed JWT auth with a single flat in-memory ADMIN role. Named users now need invite/deactivate/reset flows, and Finance needs a read-only VIEWER role for dashboards and list screens without write access.

**Decision**
1. **Store:** `app_user` (email, BCrypt `password_hash`, role `ADMIN|VIEWER`, `is_active`, `must_change_password`) plus `login_attempt` for rate limiting — Flyway V21. Seed one admin (`admin@cognologix.com`) with temporary password requiring change on first login.
2. **Auth:** Keep JWT (ADR-005). Replace `InMemoryUserDetailsManager` with a DB-backed `UserDetailsService` loading from `app_user`. JWT claims include `role` and `mustChangePassword` so the UI can gate without an extra round-trip.
3. **Authorization:** Method security via `@AdminOnly` in the General module (`@PreAuthorize("hasRole('ADMIN')")`) on all write endpoints (POST/PUT/DELETE) except `/api/auth/**` and `PUT /api/users/me/password`. GET remains available to both roles. Annotation lives in General (not Security) to avoid a Modulith cycle — Security depends on General's `UserService`, not the reverse.
4. **Rate limit:** ≥5 `login_attempt` rows for an email in the last 15 minutes → HTTP 429 before authentication.
5. **User management API:** Owned by the General module (`UserController` / `UserService`) — Settings → General Members + `/account` profile.

**Consequences**
- (+) Real invite/reset/deactivate without changing the JWT session model.
- (+) VIEWER can use all read surfaces; write UI and APIs are Admin-only.
- (–) Manual provisioning remains until SSO (still deferred per ADR-005).

**Alternatives considered**
- Keep single ADMIN flat role — rejected; Finance needs viewers for dashboards without mutation risk.
- OAuth2/OIDC now — still premature; revisit when SSO is a stated requirement.

---

## ADR-043: Budgeting Actual Revenue — Prefer Revenue Module, Manual Override Fallback

**Status:** Accepted — July 2026

**Context**
ADR-039 directed Budgeting & Forecasting to replace `period_actuals.actual_revenue_manual` with a direct query to Revenue. Finance still needs a path for periods where Zoho Books uploads are not yet available. Revenue already called Budgeting for plan figures on the Dashboard (ADR-041), so a naive `BudgetingService → RevenueService` call would form a Modulith cycle.

**Decision**
1. **Actuals resolution in Budgeting:** `getRollingForecast`, `getPlanVsActual`, and `getBuMetrics` call `RevenueService.getAllClientsMonthlyRevenue(month, year)`. That method returns `null` when no active invoice/credit-note upload exists for the period; otherwise the per-client list. Non-null → use Revenue (INR net). Null → fall back to `client_revenue_actual` rows, then `actual_revenue_manual`. The column stays in schema as a **Manual Override**.
2. **Plan vs Actual:** each month includes `revenueSource` = `REVENUE_MODULE` | `MANUAL_OVERRIDE` | null.
3. **Manual Override API:** `PUT .../actuals/{month}/{year}/revenue` returns `PeriodActualsResponse` with `actualRevenueManualLabel = "Manual Override"`.
4. **Cycle break:** Revenue Dashboard composition (actuals + planned) lives in the application composition module (`com.cognologix.fpa.application.RevenueDashboardController`) that injects both services. `RevenueService` no longer depends on `BudgetingService`. Budgeting declares `allowedDependencies` including `revenue`; Revenue module is OPEN for DTO access.

**Consequences**
- (+) Finance sees which source drives each month's actual revenue.
- (+) Manual entry remains for gaps; Revenue is primary when uploads exist.
- (+) Modulith DAG preserved: budgeting → revenue; revenue no longer → budgeting.

---
## ADR-044: Export/Import Completeness + Full Backup/Restore
 
**Status:** Accepted — July 2026
 
**Context**
Export/import was built for Customers, Rate Cards, and Project Codes (ADR-027, ADR-028, ADR-032) but not consistently across all sections. Additionally, a full backup/restore capability is needed for environment resets, testing, and data safety.
 
**Decision**
Two tiers of export/import:
 
**Tier 1 — Per-section export/import (targeted updates)**
Every section that has data Finance can configure or import gets its own export and import, in the same Excel format as its import template. Sections still missing export/import to be completed:
- People & Payroll: classification config export/import (Delivery PU list, Management/Leadership BU list), column mapping template export/import
- Budgeting & Forecasting: HC Plan export/import, Salary Budget export/import, Client Revenue Plan export/import, Overhead Budget export/import — all per forecast version
- Revenue: invoice export (import already exists), credit note export (import already exists)
- General: FX rate export/import, users export (no import — users managed via invite flow)
**Tier 2 — Full backup/restore**
Format: ZIP archive containing one Excel file per data type. Single download, single upload for full system backup/restore. Restore behaviour: full replace — system resets all data then reloads from the ZIP in dependency order (users → general config → customers → rate cards → project codes → classification config → FX rates → financial year plans → forecast versions → plan inputs → periods → snapshots → master records → invoices → credit notes). Restore requires ADMIN role and explicit confirmation ("This will delete all existing data and replace it with the backup. This cannot be undone.").
 
Accessible from a new "Backup & Restore" section in Settings → General (Admin only).
 
**ZIP contents (one Excel per file):**
users.xlsx, general_config.xlsx, fx_rates.xlsx, customers.xlsx, rate_cards.xlsx, project_codes.xlsx, classification_config.xlsx, column_mapping_templates.xlsx, financial_year_plans.xlsx, forecast_versions.xlsx, hc_plan.xlsx, salary_budget.xlsx, client_revenue_plan.xlsx, overhead_budget.xlsx, period_actuals.xlsx, overhead_actuals.xlsx, zoho_people_snapshots.xlsx, zoho_payroll_snapshots.xlsx, employee_registry.xlsx, alternate_id_links.xlsx, master_records.xlsx, revenue_invoices.xlsx, revenue_credit_notes.xlsx.
 
**Consequences**
- (+) Finance can reset the test DB, restore from backup, and continue testing without manual re-entry.
- (+) Per-section export/import allows targeted updates without touching unrelated data.
- (+) ZIP format keeps backup as a single portable artifact while keeping individual files inspectable.
- (−) Full restore with "everything including transactional snapshots" will be large for production use — acceptable at Cognologix's current scale (~100 employees, monthly cadence).
- (−) Full replace restore is destructive — mitigated by the explicit confirmation step and Admin-only access.
**Phase approach:** Per-section exports for missing sections first (quick wins, consistent with existing pattern). Full backup/restore second (more complex due to dependency ordering and volume).

**Implementation note (July 2026):** ADR-044 Tier 1 per-section exports implemented:

- **People:** `GET/POST /api/people/config/classification/export|import` (+ sample); `GET/POST /api/people/imports/mappings/export|import` (+ sample). Settings UI export/import on Classification Rules and Column Mapping Templates.
- **Budgeting:** per-version Excel export/import for HC plan, salary budget, client revenue plan, overhead budget; sample downloads; `GET .../export-all` ZIP (`plan_inputs_export.zip`). Import merges by natural key then call existing upsert; DRAFT-only (409 if not DRAFT). Plan Setup panel toolbars + Export All Inputs.
- **Revenue:** `GET /api/revenue/invoices/export` and `GET /api/revenue/credit-notes/export` (same filters as list, unpaginated). Invoice List Export uses server download.
- **General:** `GET/POST /api/general/fx-rates/export|import` (+ sample); overlap/`exact-duplicate` validation. FX Rates section in Settings → General.

**Tier 2 implementation (July 2026):**
- Module portals expose `exportBackupSheets` / `wipeForRestore` / `restoreBackupSheets` (ADR-008: orchestrator never touches foreign repositories).
- New OPEN Modulith module `com.cognologix.fpa.system` — `SystemBackupService` + `SystemBackupController`:
  - `GET /api/system/backup` → ZIP of Excel files in dependency order; updates `general_config.last_backup_at`
  - `POST /api/system/restore` → dry-run summary + time-limited token (no mutation)
  - `POST /api/system/restore/confirm` → wipe in reverse dependency order (preserve logged-in admin), insert forward; restored users get temporary password `RestoreMe123!` + `must_change_password=true`
- Settings → General → Backup & Restore (Admin only)

**Implementation note (July 2026 — Tier 2 module portals):** Each bounded-context service exposes backup/restore portal methods (ADR-044 Tier 2) without the system orchestrator yet:
- Shared types: `BackupSheet`, `BackupGridHelper`, `ExcelGrid` in `com.cognologix.fpa.general`.
- **General:** `UserService.exportUsersBackupSheet()`, `wipeUsersExcept()`, `restoreUsers()`; `GeneralConfigService.exportBackupSheets()`, `wipeForRestore()`, `restoreBackupSheets()`, `getConfigValue()`, `setConfigValue()`, `LAST_BACKUP_AT_KEY`.
- **Customer:** `CustomerService.exportBackupSheets()`, `wipeForRestore()`, `restoreBackupSheets()` — customers, rate_cards, project_codes sheets.
- **People:** `PeoplePayrollService` — nine sheets (classification through master_records).
- **Budgeting:** `BudgetingService` — ten sheets (overhead_line_items through overhead_actuals).
- **Revenue:** `RevenueService` — revenue_invoices, revenue_credit_notes sheets.
Orchestrator ZIP download/upload and Settings UI deferred.
 
---

## ADR-045: Payroll Cost = Gross Pay + Employer Benefit Contributions

**Status:** Accepted — July 2026

**Context**
Module 1 §4.2 and §10 treat Gross Pay as the primary company-expense salary metric. In practice Zoho Payroll also exports employer contributions (EPF, EPS, EDLI, EPF Admin, VPF, NPS, Gratuity). Using Gross Pay alone understates true payroll cost for dashboards, Cost per Employee Layer 1, and BU Gross Margin. Budgeting previously applied a flat 13% statutory estimate on top of Gross Pay for Layer 1 / OpEx, which diverges from actual contribution amounts when available.

**Decision**
1. Persist optional employer contribution columns on `payroll_snapshot`; DB-generated `total_employer_contributions` sums them (null → 0).
2. On Master build, copy `total_employer_contributions` to `master_record`; DB-generated `total_payroll_cost` = Gross Pay + employer contributions.
3. Salary Metrics and Cost-per-Employee actuals use **Total Payroll Cost** as the primary figure, with Gross Pay and Employer Contributions shown as a breakdown.
4. `PeriodFinalisedEvent` carries per-classification employer contributions and total payroll cost; Budgeting stores them on `period_actuals` / `period_bu_actuals`.
5. Cost per Employee Layer 1: for months with actuals, use actual employer contributions ÷ HC; for plan months, retain the 13% estimate as a forward-looking proxy. Return gross and contributions per head separately.
6. BU Gross Margin = Revenue − totalPayrollCost (not Gross Pay alone).
7. ZOHO_PAYROLL mapping attributes for contributions are optional — unmapped columns remain null.

**Consequences**
- (+) Finance sees true employer cost when mapped from Zoho; plan months keep a simple proxy.
- (+) Spec §4.2/§10 Gross-Pay-primary wording is superseded for operational cost metrics (flagged deviation).
- (–) Historical periods without contribution mappings continue to show contributions as zero (total = gross) until re-imported with mappings.
- (–) OpEx `statutoryBenefits` in monthly financials still uses the 13% estimate (unchanged in this ADR); may be aligned later.

**Alternatives considered**
- Keep Gross Pay primary and show contributions only as informational — rejected: understates cost in margin and CPE calculations.
- Always use 13% even when actuals exist — rejected: ignores available Zoho data.

---

## ADR-046: Zoho Import Amounts — Full Rupees → Rs Lakhs on Parse

**Status:** Accepted — July 2026

**Context**
Zoho Books (invoices / credit notes) and Zoho Payroll export monetary columns in full rupees (or full currency units for non-INR invoices). Cognologix FP&A dashboards and budgeting treat all monetary figures as **Rs Lakhs** (People & Payroll salary metrics, Cost per Employee, BU Metrics, Budgeting overhead/salaries). Storing Zoho amounts without conversion made imported figures ~100,000× too large relative to plan and display conventions.

**Decision**
1. After `ExcelNumberParser.parseAmount()`, convert with `toRsLakhs(amount) = amount ÷ 100_000` (scale 2, `HALF_UP`) before persistence.
2. **Revenue** (`RevenueExcelParser` required/optional decimal helpers): apply to Amount and Balance for `ZOHO_BOOKS_INVOICES` and `ZOHO_BOOKS_CREDIT_NOTES`. USD amounts are divided the same way so `amount_inr` (FX × stored amount) is also in Rs Lakhs.
3. **People & Payroll** (`ExcelSnapshotParser` decimal helpers used by ZOHO_PAYROLL / ZOHO_PAYROLL_FNF): apply to Gross Pay, Net Pay, CTC Per Annum, and all employer contribution columns (EPF, EPS, EDLI, EPF Admin, VPF, NPS, Gratuity).
4. Budgeting Excel I/O and manual entry already use Rs Lakhs — no change. Backup/restore reads already-stored values — no re-conversion.
5. Existing imported data in full rupees is wrong until Finance **re-uploads** Zoho Payroll and Zoho Books files; re-upload SUPERSEDEs prior versions (ADR-033).

**Consequences**
- (+) Stored amounts align with dashboard/plan units (Rs Lakhs).
- (–) Historical imports must be re-uploaded; superseded rows remain for audit but must not be used for metrics.
- (–) Spec wording that described Amount as “in original currency” without naming the Lakhs storage unit is clarified by this ADR (flagged alignment).

**Alternatives considered**
- Convert only at display time — rejected: plan vs actual and cross-module events would still mix units.
- Convert only INR currency rows — rejected: USD→INR path must produce Lakhs `amount_inr` consistently.

---

## ADR-047: Shared Excel Header Normalization (Import / Export / Mapping Templates)

**Status:** Accepted — July 2026

**Context**
Export files and Zoho exports often use different header spellings than import parsers expect (Title Case vs snake_case, spaces vs underscores/hyphens, case drift). Fixed-column parsers each had a private `trim`+lowercase helper; People/Revenue Zoho imports and frontend mapping pre-fill used exact string equality — so a saved column-mapping template failed to pre-fill when Zoho renamed a header slightly (e.g. `Invoice #` vs `Invoice#`).

**Decision**
1. Shared `ExcelParserUtils.normalizeHeader` (backend) and `normalizeHeader` (frontend): trim → lowercase → collapse spaces/hyphens/underscores to `_` → strip other non-alphanumeric chars.
2. Apply normalization to **both** sides of every header comparison in all import parsers (customers, rate cards, project codes, FX rates, classification config, mapping-template IO, budgeting plan inputs, Zoho People/Payroll, Zoho Books).
3. Column-mapping template pre-fill (frontend `buildInitialMappings` and backend Zoho parse mapping lookup) normalizes saved `excel_column_name` and incoming file headers before match; UI/DB retain original header strings.
4. User-facing Excel exports and import sample templates use consistent **Title Case** headers matching the import column constants (customers include `Is Internal`; order aligned with the sample). System backup ZIP grids remain snake_case + positional (ADR-044) and are out of scope.

**Consequences**
- (+) Export→re-import and Zoho header drift are tolerant without changing stored template display names.
- (+) Single normalization contract across modules.
- (–) Aggressive stripping means `Invoice#` and `Invoice` normalize identically — acceptable for Zoho; avoid two distinct columns that differ only by punctuation.

**Alternatives considered**
- Exact match only — rejected: brittle against Zoho/export formatting.
- Fuzzy / Levenshtein match — rejected: unpredictable false positives.

---

## ADR-048: Revenue Import — FX Conversion Only for Explicit USD; Unmapped Currency Defaults to INR

**Status:** Accepted — July 2026

**Context**
`RevenueService.parseCurrency` previously defaulted null/blank Currency (unmapped column) to **USD**, so every invoice without a Currency mapping received `amount_inr = amount × FX_rate`. INR invoices with Currency=INR were already correct (`amount_inr = amount`), but missing/unmapped Currency incorrectly applied FX. Spec originally said “defaults to customer's billing currency” — that was never implemented and caused the USD default bug.

**Decision**
1. Unmapped/blank Currency → **INR** (no FX; `amount_inr = amount`; `fx_rate_id` null).
2. Explicit **INR** → same (`amount_inr = amount`).
3. Explicit **USD** → `amount_inr = amount × USD_INR rate` (ADR-017); store `fx_rate_id`.
4. Applies to both `ZOHO_BOOKS_INVOICES` and `ZOHO_BOOKS_CREDIT_NOTES`. Amounts remain Rs Lakhs after ÷100,000 (ADR-046).
5. Entity defaults for `RevenueInvoice` / `RevenueCreditNote` currency = INR. Backup restore uses the same blank→INR default.

**Consequences**
- (+) INR and unmapped-currency imports no longer inflate `amount_inr` by FX.
- (–) Finance must re-upload affected periods after deploy so SUPERSEDED versions replace wrong INR equivalents.
- Spec Currency rows updated to document INR default (replacing unimplemented “customer billing currency” wording).

**Alternatives considered**
- Default to customer billing currency — deferred: Customer Management has no single billing-currency field on the customer today; INR default is the safe no-FX path.
- Leave null currency as USD — rejected: caused incorrect FX on domestic invoices.

---

## ADR-049: Budgeting Dashboard — Period Granularity Selector (Monthly / Quarterly / Annual)

**Status:** Accepted — July 2026

**Context**
ADR-038's single scrollable dashboard showed full-FY totals in Headline KPIs and related panels. When only a few months of `period_actuals` exist, FY Actual is diluted by zero-filled future months and variance vs full-year Plan is misleading. Finance reviews the year monthly and quarterly as well as YTD.

**Decision**
1. **Dashboard period controls:** Granularity Segmented (Monthly / Quarterly / Annual) + Period Select (month or quarter). Default on load: Monthly + most recent month with `period_actuals` for the FY (else April = FY start).
2. **API:** Optional query params on dashboard endpoints — `granularity` (`MONTHLY` | `QUARTERLY` | `ANNUAL`, default `ANNUAL` for backward compatibility), `month`+`year` (MONTHLY), `quarter`+`year` (QUARTERLY; Q1=Apr–Jun … Q4=Jan–Mar, Indian FY). `year` is the calendar year of the period's first month.
3. **Aggregation:**
   - Plan vs Actual / Headline / P&L selected-period card: MONTHLY = one month; QUARTERLY = sum of 3 months; ANNUAL = YTD only (months with actuals — Plan and Actual both scoped to those months), plus coverage note e.g. `Actuals: Apr–Jun 2026 (3 of 12 months)`.
   - Rolling Forecast chart: always 12 monthly points; granularity only highlights the selected month/quarter.
   - Delta: MONTHLY = that month; QUARTERLY = sum of 3 months; ANNUAL = sum of all 12 months.
   - Cost per Employee: MONTHLY = one month; QUARTERLY/ANNUAL = average of per-head costs across months in scope (plan fallback when no actuals).
   - BU Metrics: MONTHLY = one month; QUARTERLY = sum of 3 months; ANNUAL = sum of months with actuals only.
4. **P&L column layout (UI):** Monthly → 12 months + highlight + summary card; Quarterly → Q1–Q4 + highlight + summary card; Annual → single FY/YTD column + coverage note.

**Consequences**
- (+) Variance is like-for-like for the period Finance is reviewing.
- (+) Default Monthly + latest actuals matches the monthly close workflow.
- (–) ANNUAL Headline/BU use YTD while Delta ANNUAL sums all 12 months — intentional (Delta vs full baseline year).

**Alternatives considered**
- Frontend-only filtering of full-FY payloads — rejected: Cost per Employee / BU Metrics need server-side multi-month average/sum; YTD Plan scoping must be consistent.
- Default Annual — rejected for UX; API default remains ANNUAL for backward compatibility only.

---

## ADR-050: Expenses Module — Monthly Overhead Actuals Feed Budgeting

**Status:** Accepted — July 2026

**Context**
ADR-037 treated overhead actuals as manual entry into Budgeting (`overhead_actuals`) until a dedicated module existed. Finance needs a first-class place to capture monthly overhead spend by the 24 seeded line items (extensible), with month lock for close, Excel import/export, and Settings-managed categories. Budgeting & Forecasting (ADR-038 Cost per Employee, P&L, Plan vs Actual) must consume those actuals the same way Revenue feeds net revenue (ADR-043) — but without a manual override path: expenses always come from the Expenses module.

**Decision**
1. **New bounded context `expenses`** (`com.cognologix.fpa.expenses`), Spring Modulith OPEN module depending on `general` only. Public API: `ExpenseService` in the root package.
2. **Schema:** `expense_category` (seeded from the same line codes as `overhead_line_item`), `expense_actual` (one row per category per month), `expense_month_lock` (unlock requires reason, audited).
3. **Budgeting consumption:** `BudgetingService` calls `ExpenseService.getMonthlyExpenseActuals(month, year)` wherever it previously read `overhead_actuals` for actuals months. Empty/null map → zero overhead (no fallback to `overhead_actuals` / period manual entry).
4. **Navigation (ADR-021):** top-level Expenses → Expense Entry + Expense History; Settings → Expenses tab for category admin.
5. **Roles (ADR-042):** writes / lock / unlock / import / add-deactivate category are Admin-only; Viewers can read and export.

**Consequences**
- (+) Overhead actuals live in their domain; Budgeting stays a consumer.
- (+) Month lock matches Finance close discipline.
- (–) Legacy `overhead_actuals` table / upsert API remain for backup compatibility but are no longer used in Plan vs Actual calculations.

---

# ADR-051: BU Analysis — Dedicated Sub-Section Under Budgeting & Forecasting
 
**Status:** Accepted — July 2026
 
**Context**
BU Metrics was a single panel on the Budgeting & Forecasting Dashboard. Finance needs deeper BU analysis including position-level salary breakdown, billable vs non-billable splits, and cost/revenue as % of total.
 
**Decision**
New BU Analysis sub-section under Budgeting & Forecasting nav alongside Dashboard, Plan Setup, Scenario Comparison. Two tabs: External BUs (client BUs) and Internal BUs (Management, Leadership, Pool, L&D, BEF). Per BU: billable vs non-billable HC, salary cost split, avg salary cost per title (from Zoho People `title` field — not job_level), position-wise HC as % of total BU HC, BU cost as % of overall salary cost (pie chart), BU revenue as % of overall revenue (pie chart, external only). Current BU Metrics dashboard panel (Revenue, Gross Margin, Avg salary per head) moved into this section. Data from master_record per period.
 
**Consequences**
- (+) Deeper BU analysis for cost management and client profitability decisions.
- (+) Internal BUs tracked separately from external clients — no mixing.
- (−) Panel 6 (BU Metrics) removed from dashboard — Finance accesses via dedicated sub-section.
---
 
*(Further ADRs to be added as decisions are finalized.)*
