---
name: cognologix-system-governance
description: "Keeps the Cognologix Financial Planning & People Analytics System's documentation permanently in sync with decisions made in conversation. Use whenever discussing, for any module: (1) a design/data-model change (new entity, field, business rule, edge case, dashboard metric) \u2192 update the Requirements Specification docx; (2) an architectural/technology decision (framework, database, hosting, structural pattern, DDD/CQRS/event-sourcing posture, or a refinement/reversal of a prior one) \u2192 capture as an ADR; (3) an API contract change (new/changed endpoint, request/response shape, auth) \u2192 update API documentation; (4) a functional requirement change (scope, ingestion rule, role, access rule) \u2192 update the Requirements Specification. Trigger proactively even if not explicitly asked \u2014 a new rule or decision surfacing in conversation IS the trigger. Also trigger on 'what did we decide about X' recap requests \u2014 check these documents before answering from memory alone."
license: Cognologix Internal Use
---

# Cognologix System Governance — Documentation Sync

This skill exists because this project accumulates real decisions across long conversations, and none of them are allowed to live only in chat history. Every design change, architecture decision, API change, and requirements change gets written to a durable artifact in the same turn it's decided — not batched, not deferred, not left for the user to ask about.

## The four sync rules

| # | Trigger | Artifact to update |
|---|---|---|
| 1 | Design / data-model change (new entity, field, business rule, edge case, dashboard metric) | Requirements Specification (.docx) |
| 2 | Architectural / technology decision (framework, DB, hosting, structural pattern, or a reversal of a prior ADR) | ADR log (ADR-log.md) |
| 3 | API contract change (new/changed endpoint, request/response shape, auth requirement) | API documentation |
| 4 | Functional requirement change (scope, ingestion rule, role, access rule) | Requirements Specification (.docx) |

Rules 1 and 4 land in the same document — "design" and "requirements" changes are both specification content; they're listed separately because they're triggered by different kinds of conversation (a data-model detail vs. a scope/role decision), but both go to the same file.

**Default posture: update first, mention second.** When a triggering change surfaces in conversation, make the documentation update as part of that same response — don't ask permission to update the doc, and don't wait for the user to say "please add that." Only ask a clarifying question first if the change is genuinely ambiguous (e.g. it's unclear whether something is a firm decision or a hypothesis being explored) — the user thinking out loud is not automatically a trigger; a decision being reached is.

## Artifact locations & conventions

### 1 & 4. Requirements Specification (.docx)

- Built via a Node script using the `docx` npm package (`docx` skill applies — read `/mnt/skills/public/docx/SKILL.md` for the general mechanics if unfamiliar).
- Convention established for this project: a single build script (e.g. `build_spec.js`) with reusable styled helpers — `h1()`, `h2()`, `h3()`, `body()`, `bullet()`, `note()`, `table()` — using Cognologix brand colors and fonts (see `cognologix-brand-guidelines` skill: Montserrat headings, Lato body, red/orange accents).
- The document has a **Document Control** block and a **Revision History** table at the top. Every update increments the revision table with a new row (Rev N, date, one-line change summary) — never silently edit without logging the revision.
- Section numbers are sequential and cross-referenced elsewhere in the document (e.g. "see Section 7"). When inserting a new section, renumber subsequent sections AND check for stale cross-references elsewhere in the script — grep for `Section \d+` before regenerating.
- After regenerating: convert to PDF (LibreOffice `soffice.py --headless --convert-to pdf`) and render pages to JPEG (`pdftoppm`) to visually verify tables and layout didn't break, before presenting to the user. Don't skip this check — table/layout regressions are easy to introduce silently when editing a large generation script.
- Copy the output to `/mnt/user-data/outputs/` and use `present_files` — never describe the update without producing the actual updated file.

### 2. ADR log (ADR-log.md)

- Single running markdown file, Michael Nygard format: Title / Status / Context / Decision / Consequences / Alternatives considered.
- Status values: `Proposed` (recorded but not yet confirmed by the user) → `Accepted` (confirmed) → `Superseded by ADR-xxx` (if a later decision reverses it — never delete a superseded ADR, mark it superseded and add the new one, so the history of *why* something changed is preserved) → `Deprecated`.
- When a decision is genuinely uncertain or the user is still weighing it, record it as `Proposed` and say so explicitly — don't mark anything `Accepted` until the user has actually confirmed it, even if it seems obviously correct.
- When an existing ADR is revisited and refined (not reversed) — e.g. a new alternative considered and explicitly deferred rather than adopted — add that reasoning into the existing ADR's "Alternatives considered" section rather than creating a new ADR for the same decision. Create a *new* ADR only for a genuinely new decision.
- Copy to `/mnt/user-data/outputs/ADR-log.md` and `present_files` after every change — same rule as the spec: never describe an ADR update without producing the updated file.

### 3. API documentation

- Not yet created as of this project's current phase (still in requirements/architecture, pre-implementation). Once API implementation begins, establish this artifact (OpenAPI/Swagger spec, or a markdown API reference, matching whatever convention gets set at that time) and apply the same discipline: every endpoint addition or contract change updates the doc in the same turn, with a changelog entry.
- If asked about API behavior before this artifact exists, say so plainly rather than inventing documentation for endpoints that haven't been designed yet.

## What does NOT trigger an update

- Hypothetical "what if we did X instead" exploration that the user hasn't settled on — discuss freely, don't write it down as a decision until it's actually decided.
- Questions about existing decisions (answer from the documents — see below — don't create new entries for a question).
- Formatting/typo fixes to prior conversation text that don't reflect an actual content change.

## Answering "what did we decide" questions

Before answering from conversation memory alone, check the current Requirements Specification and ADR log content (re-read the working files, e.g. `/home/claude/build_spec.js` or the last-generated docx/ADR content) — conversation history is not the source of truth once a decision has been written down; the documents are. If there's ever a discrepancy between what was discussed and what's in the documents, flag it rather than silently trusting one over the other.
