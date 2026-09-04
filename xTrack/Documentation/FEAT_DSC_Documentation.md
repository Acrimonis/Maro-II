---
name: Documentation
status: active
created: 2026-06-11 06:42
modified: 2026-07-17 13:30
---

# Feature: Documentation

**Description:**
Cross-cutting project documentation — README, FAQs, setup guides, architecture docs, git workflow, code navigation map, and maintenance of docs/ directory. Ensures all plans and reference docs are properly organized and attached to the correct features.

## Implemented

- **maro-code-map** — `docs/maro-code.md` feature-to-code map (package-level + anchor classes, drift detection) → `xTrack/Documentation/260717_FEAT_PLN_Documentation_maro-code-map.md`
- **cleanup** — `plans/*.md` and unattached `docs/*.md` classified + attached to features
- **help** — `docs/cmd_help.md` split into per-command `docs/cmd_help_[cmd].md` files
- **git rules** — `docs/GIT_WORKFLOW.md` compact token-optimized source of truth; upper layers only reference it
- **verif-scattering** — scattered/duplicated instructions audit + consolidation across AGENTS.md / GLOBAL_CONTEXT / cmd_help / adapters
- **readme** — README modernized: bake→build→deploy pipeline, current structure tree, synced docs index
- **planeding** — `plans/*.md` migrated to `xTrack/[Feature]/FEAT_PLN_*` convention

## Rules
- Feature-scoped discussion/plan files go in `xTrack/[Feature]/FEAT_PLN_[Feature]_[topic].md` (not in `plans/`). The `plans/` directory is legacy; new plans use the `FEAT_PLN_` convention under the target feature's xTrack directory.
- `docs/maro-code.md` is the single source of truth for feature-to-code mapping. Feature files reference it; do not duplicate file listings.
## Key Files
- `docs/maro-code.md` — feature-to-code navigation map (primary reference)
- `AGENTS.md` — Lazy-Load Index entry
- `xTrack/GLOBAL_CONTEXT.md` — Cross-Reference Docs + passive drift rule

## Docs
- `docs/maro-code.md` — Feature-to-code navigation map
- `xTrack/Documentation/260717_FEAT_PLN_Documentation_maro-code-map.md` — Design discussion & decisions
- `docs/FAQ.md` — Project FAQ
- `docs/SETUP.md` — Project setup guide
- `xTrack/Documentation/260611_FEAT_PLN_Documentation_git-protection-workflow.md` — Git protection enforcement flow design
- `xTrack/Documentation/260611_FEAT_PLN_Documentation_instruction-consolidation-audit.md` — Scattered/duplicated instructions audit findings
- `xTrack/Documentation/260611_FEAT_PLN_Documentation_readme-update.md` — README update discussion & proposed changes
- `xTrack/Documentation/260610_FEAT_PLN_Documentation_git-merge-command.md` — Git merge command design
- `xTrack/Documentation/260610_FEAT_PLN_Documentation_git-move-command.md` — Git move/rename command design
- `xTrack/Documentation/260612_FEAT_PLN_Documentation_round-1-summary-round-2-plan.md` — Round 1 summary and round 2 plan
- `xTrack/Documentation/260717_FEAT_PLN_Documentation_file-naming-and-cleanup.md` — File naming and cleanup plan
