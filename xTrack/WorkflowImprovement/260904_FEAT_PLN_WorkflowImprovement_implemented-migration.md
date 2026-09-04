# FEAT_PLN — Migrate existing ## Implemented sections to pointer-index

- **Date:** 2026-09-04
- **Branch:** feature/process
- **Status:** planned — implementation pending go-ahead

## Scope

19 `FEAT_DSC_*.md` files carry a `## Implemented` (or section-level `#### Implemented`) in prose form: Coastline, ZoneTile, Ui_General, Navigation, Markers, Ui_Menu, GPS, Ui_Dashboard, BoatTrace, WorkflowImprovement, UI_Map, and 8 more.

## Target format

`## Implemented` = bounded pointer list:

- `- [one-liner of what shipped] → [FEAT_PLN_*/FEAT_DOC_* pointer]`
- planless work = bare one-liner.

## Migration rules

1. **Feature-level `## Implemented`** — each `### heading (date)` + bullets → one pointer entry:
   - pointer = the matching `FEAT_PLN_*` (fuzzy by topic/date) if one exists; else `FEAT_DOC_*_decisions.md`; else bare one-liner.
   - one-liner = a 1-line outcome summary that keeps the key file/outcome.
2. **Section-level `#### Implemented`** — NOT migrated here; folded by `#bake` (C8/C9) into `## Implemented`.
3. **Preserve nothing else** — the detail already lives in the plan/doc files.

## Steps

1. For each of the 19 files: rewrite feature-level `## Implemented` to pointer-index (one bulk pass — cache-bust once).
2. `#bake` each affected feature to fold section-level `#### Implemented` (and legacy slots).
3. Verify: no prose feature-level `## Implemented` remains; every pointer resolves to an existing file.

## Risk / mitigation

- Granular history is compressed into one-liners. Mitigation: the pointer's target (plan/doc) retains the detail; planless work keeps a bare one-liner so nothing vanishes.

## Notes

- This is a per-file semantic pass (pointer matching needs judgment), not a blind script.
