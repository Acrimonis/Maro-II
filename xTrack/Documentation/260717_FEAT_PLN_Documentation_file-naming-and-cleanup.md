<!-- scope: feature -->
# Plan: FEAT_PLN_ Date Prefix & /plans/ Cleanup

> **Feature:** Documentation
> **Created:** 2026-07-17
> **Status:** done

## Goal

Two changes to normalize documentation file management:

1. **Date-prefix all `FEAT_PLN_*.md` files** with `YYMMDD_` for chronological sorting
2. **Migrate remaining `/plans/*.md` files** to xTrack feature directories

## Scope

| What | Count | Notes |
|---|---|---|
| `FEAT_PLN_*.md` files to rename | 198 | Across all xTrack feature subdirectories |
| Cross-reference updates | 38 files | In FEAT_DSC_, FEAT_HYD_, FEAT_PLN_, FEAT_DOC_, GLOBAL_CONTEXT.md, templates.md, AGENTS.md, docs/cmd_help_*.md |
| `/plans/*.md` to classify + migrate | 37 | Cross-cutting files stay in `/plans/` |
| Files to delete | 4 | 1 copy + 3 duplicates |

## Phase 1 — Date Sourcing

**Source:** `git log --diff-filter=A --follow --find-renames=40% --format=%ai -- <path>` for each FEAT_PLN_ file.
Also `git log --all --format=%ai -- <path>` → take oldest commit date.
Fallback: file system modification date.

## Phase 2 — Rename FEAT_PLN_ Files

Pattern: `FEAT_PLN_[Feature]_[topic].md` → `YYMMDD_FEAT_PLN_[Feature]_[topic].md`
NOT renamed: `FEAT_DSC_`, `FEAT_HYD_`, `FEAT_DOC_` files.

## Phase 3 — Cross-Reference Updates

Pure string replacement per old-name → new-name pair across all `.md` files.
No concatenation — each file read, string-replaced, written back.

## Phase 4 — /plans/ Cleanup

Classify each file by feature. Cross-cutting files stay in `/plans/`.
For each: date-prefix, `git mv` to `xTrack/[Feature]/`, add to target `## Docs`.

**Cross-cutting (stay):**
- `listable-item-interface-migration.md`
- `tasker-water-state-integration.md`
- `wizard-drawerslot-separation-plan.md`

**Delete:**
- `track-system-simplification-plan - Copy.md` (duplicate)
- 3 duplicates confirmed against existing xTrack equivalents

## Phase 5 — Templates Update

`.claude/skills/xtrack/references/templates.md`: `FEAT_PLN_` → `YYMMDD_FEAT_PLN_`

## Implemented

**Date:** 2026-07-17 | **Branch:** feature/doc | **BUILD:** SUCCESSFUL

- **198** `FEAT_PLN_` files renamed with `YYMMDD_` prefix via `git mv`
- **38** cross-reference files updated via pure string replacement
- **37** `/plans/` files migrated to `xTrack/[Feature]/` — 10 feature ## Docs updated
- **4** files deleted (1 copy + 3 duplicates)
- **3** cross-cutting files retained in `/plans/`
- Templates updated to `YYMMDD_FEAT_PLN_` convention
- Plan attached to `FEAT_DSC_Documentation.md` ## Docs

### Lessons
- First attempt: Phase 3 concatenated content to all 258 .md files (GLOBAL_CONTEXT.md 103→37,896 lines). Reverted.
- Second attempt: pure string replacement per-file — clean.
- Focus pointers reverted during bad-commit revert; manually restored.
