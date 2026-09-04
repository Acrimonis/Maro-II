# FEAT_PLN — Per-feature #bake sweep (fold done sections, C8/C9)

- **Date:** 2026-09-04
- **Branch:** feature/process
- **Status:** planned — implementation pending go-ahead

## Goal

Fold done sections into `## Implemented` as pointer entries, promoting retained rules/key-files/docs to feature level so nothing is lost.

## Fold algorithm (per section)

1. **OPEN** if it has ≥1 `- [ ]` todo → keep as a live section, trimmed:
   a. Keep heading + a **detailed summary** (2–4 sentences retaining the context the open todo needs).
   b. Keep only open `- [ ]` todos (drop done `[x]` todos).
   c. Keep still-valid `#### Rules` + `#### Key Files` + `#### Docs` (dedupe; drop stale rules).
2. **DONE** otherwise → fold:
   a. Emit `- **section** — [one-liner outcome] → [plan pointer]` into `## Implemented` (bare one-liner if no plan/doc exists). The one-liner summarizes the section's outcome.
   b. Drop the section's `#### Todos` (finished checklist — redundant with the one-liner).
   c. Promote only still-valid `#### Rules` → feature `## Rules` (dedupe; drop stale ones).
   d. Promote `#### Key Files` → feature `## Key Files` (dedupe).
   e. Promote `#### Docs` → feature `## Docs` (dedupe).
   f. Delete the `###` section.

## Sweep order

1. WorkflowImprovement (7 retained sections).
2. DepthSafety, DepthMapping, ArcLayout, Performance, ColorManagement, Documentation.
3. GPS, BoatTrace, Markers (large).
4. Ui_General, Ui_Dashboard, Ui_Settings, Ui_Menu, UI_Map.
5. RegulatedZones, ZoneTile, Coastline, Navigation, CheckDev, Health, BakeNormalization.

## Verification

- No done `###` sections remain (only open sections).
- `#doctor` clean; `git diff` reviewable.

## Risk / mitigation

- One-liners compress history; the plan/doc pointers retain the detail. Retained rules/key-files/docs are promoted, not dropped.
