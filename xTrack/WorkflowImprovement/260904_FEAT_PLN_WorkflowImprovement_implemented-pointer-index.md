# FEAT_PLN — Implemented-as-pointer-index

- **Date:** 2026-09-04
- **Branch:** feature/process
- **Status:** adopted — implementation pending explicit go-ahead

## Decision

`## Implemented` becomes a bounded pointer index, not a changelog:

- Each entry = one-liner + `→ FEAT_PLN_*` / `→ FEAT_DOC_*` pointer.
- Planless work = bare one-liner (no pointer).
- Detail lives in the plan/doc files — the DSC stays lean.
- **Plans capture intent, not outcome** — so each plan gains an `## Outcome` section, appended once at completion: a short "what actually shipped" summary + deviations.
- PLN lifecycle: write intent first, then append `## Outcome` at completion (append-only, merge-safe).

## Changes (gated)

1. `templates.md` — FEAT_DSC `## Implemented` format → pointer list.
2. `docs/cmd_help_bake.md` — C8 fold-done → write one-liner + pointer.
3. `docs/cmd_help_doc_update.md` — step 2 (plans vs actual) reads `## Outcome` as canonical input.
4. `AGENTS.md` §7a — one-line: Implemented = pointer index (optional).
5. `templates.md` — PLN template gains `## Outcome` section.
6. `docs/cmd_help_bake.md` — fold-done appends `## Outcome` to the plan + writes one-liner/pointer.
7. (deferred) migrate existing `FEAT_DSC_*` Implemented sections to pointer lists.
