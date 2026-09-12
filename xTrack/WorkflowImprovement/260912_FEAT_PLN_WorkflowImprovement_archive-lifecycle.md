<!-- scope: feature -->
# Plan Lifecycle & the `#archive` Command

**Feature:** WorkflowImprovement · **Date:** 2026-09-12 · **Branch:** `feature/wrKFl` · **Status:** in implementation
**Migrated from:** Annex B of [`260912_FEAT_PLN_WorkflowImprovement_docs-integrity.md`](260912_FEAT_PLN_WorkflowImprovement_docs-integrity.md:1), carrying review resolutions B1–B12.

## Problem

A plan file does four jobs — intent/rationale, execution scratchpad, provenance pointer, de-facto specification. The fourth is misfiled: a plan has no update path, a `FEAT_DOC_*` does.

**Rule: if a document must stay true, it cannot be a plan.**

## Three tiers

- **Active** — `xTrack/[Feature]/FEAT_PLN_*.md`, attached in `## Docs`, citable by `## Implemented`.
- **Digest** — decisions into `FEAT_DOC_[Feature]_decisions.md`, retained behaviour into `## Rules`, one bare `## Implemented` line with the pointer dropped.
- **Archive** — the file in `xTrack/[Feature]/xxArchive/`, detached, unreferenced.

## Lifecycle

| Exit | Action |
|---|---|
| Shipped | write `## Outcome`, extract digest, archive |
| Superseded | tombstone "superseded by X", archive |
| Promoted | becomes a `FEAT_DOC_*` living reference, then archive |
| Abandoned | **delete**, not archive — a document nobody will act on has no reference value |

## `xxArchive/` rules (B4)

Index at `xxArchive/INDEX.md` — `File | Created | Archived | Status (shipped/superseded/promoted) | Summary | Tags | Superseded-by`. `xxArchive/` is a fixed literal: the `xx` prefix sorts last in a listing and reads as inactive; archived files **keep** their names, since the folder carries the state. Cross-cutting retirements go to `docs/xxArchive/`, which owns its own index. §7a carries the one-line exclusion every consumer references.

## Invocation and read policy (B1)

`#archive` follows `#bake`: it fires only on explicit invocation, and its §7b row carries that clause verbatim. The folder is unreachable by every other command — the feature-summarising set (`#bake`, `#status`, `#doctor`, `#doc list`, `#doc audit`, `#doc update`) excludes it through the shared §7a reference, not six copies. Within an invoked `#archive`, only `INDEX.md` is read; opening one archived body needs a further explicit request naming that file, and no unlock persists to the next request.

## Digest floor (B2)

Archiving is non-destructive, so the floor protects discoverability and currency, not survival.

- **Mandatory** — an `INDEX.md` row plus a written `## Outcome` in the plan (what shipped versus what was planned, two or three lines), knowable only at retirement time.
- **Mandatory when load-bearing** — if anything in the plan still defines current behaviour, promotion is not optional: the retained rule goes to `## Rules` and the decisions to the decisions doc *before* the file moves.
- **Optional otherwise** — decisions-doc extraction may be skipped; the material survives in the body.

`#archive` asks one qualifying question — "does anything here still define current behaviour?" — and derives the mandatory set from the answer.

## `#archive` command (B6, B7, B9)

| Form | Behaviour |
|---|---|
| bare | list the active feature's plans, offer a multi-select |
| `[name]` | move + index row + digest floor + detach from `## Docs` + drop the `## Implemented` pointer |
| `search [terms]` | fuzzy-search the active feature's index only; `search all [terms]` sweeps every feature's index |
| `restore [name]` | move back, re-attach in `## Docs`, restore the pointer, delete the index row (recorded in the next `#bake` hydration) |

**Fourth gate — an unresolved walk.** `#archive` already refuses a missing digest; it refuses an open walk the same way, challenging with the walk's own three exits: close it, resume it, or park it — parking promotes the open points into the digest's `## Outcome`, the promotion rule above applied to state rather than prose. This is the only point where the archive design and the walk design touch: one sentence each, in this plan and in [`260912_FEAT_PLN_WorkflowImprovement_command-flow.md`](260912_FEAT_PLN_WorkflowImprovement_command-flow.md:1), never a third copy.

It stays **top-level** rather than folding into `#doc`, because it covers any retired feature-scoped doc, not only plans.

## Registry footprint (B3)

A §7b row, a `docs/cmd_help_archive.md` page, a line in the Session group of `docs/cmd_help.md`, and an `xxArchive/INDEX.md` template in `templates.md`.

`#doctor` gains **(q)** index↔disk drift and **(r)** an archived file still referenced from a live `## Docs`/`## Rules`/`## Implemented` block (B8) — so the range becomes **a–r**, and the §7b row and the page change in the same write. It also gains the report-only retirement nudge, whose signal (B5) is: a plan still attached in a feature's `## Docs` whose work appears in that feature's `## Implemented`.

## Backlog (B11, B12)

Recount `FEAT_PLN_*` at implementation start — the ~198 figure is a July 2026 count. Triage per feature (~25 decisions), not per plan; uncited plans archive without a human read. Each feature pass delivers, in order: INDEX rows, then digest updates, then moves. `#bake` reports retirement candidates in its summary and never moves them (B10).

## Execution order

1. Migrate this design out of the integrity plan's Annex B (P7).
2. `AGENTS.md` — one write: §7a exclusion line, §7b `#archive` row, `#doctor` row range a–r.
3. `docs/cmd_help_archive.md` — the page.
4. `docs/cmd_help_doctor.md` — checks q–r plus the nudge.
5. `docs/cmd_help.md` — Session-group line and the `#doctor` update.
6. `templates.md` — the `xxArchive/INDEX.md` template.
7. `docs/cmd_help_bake.md` — retirement candidates in the bake summary.
8. Verify: checks k–r, `#doctor` range ↔ page parity, `#help archive` resolves.

## Out of scope

The ~198-file backlog triage — a separate effort, run per feature once the mechanism exists.

## Outcome

Delivered as designed. The design migrated out of the integrity plan (which now keeps only a pointer, so the two cannot drift); `AGENTS.md` gained the §7a `xxArchive/` exclusion and the §7b `#archive` row in a single write; `#doctor` moved to checks **a–r**, adding index↔disk drift, the inverse-leak check and the report-only retirement nudge; `docs/cmd_help_archive.md` was created; `cmd_help.md` and `cmd_help_bake.md` were updated; and `templates.md` gained the `INDEX.md` schema. No scope deviations. Not done, by design: the ~198-file backlog triage.

**Retirement deferred.** By this pass's own lifecycle, a plan whose contract is still load-bearing is promoted rather than archived — and this one defines a mechanism that has not yet been exercised once. It retires only after `#archive` has run for real.

**Amended after review** — an unresolved walk joins the digest floor as a fourth gate (close / resume / park), recorded here and once in the command-flow plan. Design only: not yet implemented.
