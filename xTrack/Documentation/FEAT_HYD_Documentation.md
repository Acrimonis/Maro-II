# Context Hydration — Documentation — 2026-10-10

**Last Bake:** 2026-10-10 11:53 UTC — written by `#bake`

**Directive trace:** No covered action class was met — the session read source files and rewrote one reference doc: no dependency added, no machine-shaped data file opened, no work started without an order, no device touched, and every claim written was read from a file first.

## State
`docs/maro-code.md`, the feature-to-code map, was re-verified row by row against `app/src/main/java/ykws/android/maro`. Every Package Layout row was corrected to the tree — `data/model` 10→27 files, `data/track` 9→22, `spatial/multipass` 6→25, and `data/depth`, `data/regulation`, `data/markers`, `data/power`, `spatial`, `ui/components`, `ui/markers/wizard`, `ui/icons` and `config` each aligned — with a new `data/route/` row and a new `data/depth/validation/` row. The `RouteEngine.kt` anchor now names its four engines and `RouteEngineChoice.kt`, and the volatile MapScreen line-range and OverlayLayer param-count anchors were dropped. Three dead file names came out (`WizardTopBar.kt`, `WizardButtonRow.kt`, `ui/icons/WhereToVote.kt`). Two brief claims contradicted the tree and the tree won — `ui/components/` still holds `DrawerScaffold.kt`, `ListOverlayScaffold.kt` and `ConfirmDialog.kt`, and `OverlayLayerParams.kt` declares nine `@Immutable` bundles, not six — both reported rather than silently followed.

## Target Files
- `docs/maro-code.md` — the single source of truth for feature-to-code mapping, refreshed whole
- `xTrack/Documentation/FEAT_DSC_Documentation.md` — the feature file, this session recorded under `## Implemented`
- `xTrack/GLOBAL_CONTEXT.md` — the feature summary and focus history rows refreshed

## Next Step
Settle the ownership of the two git plans and the RegulatedZones plan sitting under `Documentation/` — moving a file is `#doc`'s or `#archive`'s call, not a refresh's.
