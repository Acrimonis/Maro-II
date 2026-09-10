# Ui_Settings — Hydration (2026-09-10)

## State
C12 OverlayLayer parameter-object collapse on `feature/refact-C12` — the last slice of the MapScreen
orchestration-monolith refactor (step 2, C1–C11 already merged via PR #225). Four of six bundles landed:
`OverlayChrome` (7 — drawer/wizard visibility flags + `wizardStep` + `drawerState`), `MenuOverlayData`
(12 — GPS mode, menu toggles, first-track/marker ids, track/marker map-referential filters + counts),
`SettingsOverlayData` (5 — settings tab + four scroll positions) and `TrackInfoOverlayData` (4 — track-info
drawer visibility, track, id list, index) — all in the new same-package `OverlayLayerParams.kt`, all public
`@Immutable` (public visibility is required: a public `OverlayLayer` cannot expose an `internal` type).
`OverlayLayer` went 89 → 65 params, unpacking all four bundles into 28 same-named locals at the top of the
body so the body and every child call stay unchanged. Builds SUCCESS with zero new warnings; Ask reviews
8/8 and 8/8 on tiers 1a-i/1a-ii, and tier 1b was verified by a direct pre-image `git diff` (the four
`ScrollState` fields proven paired, call-site expressions byte-identical). The R10 recomposition metric is
waived in writing (plan C12 appendix). Tiers 1a-i + 1a-ii are committed as `ec57458`. Remaining: Tier 1c
(`TrackListOverlayData` 3 + `MarkerListOverlayData` 4 plus the 2 dead `rememberLazyListState` defaults and
their orphan import), the citation-drift pass, doc sync, then `#bake`.

Earlier in the feature (all merged): settings subtree extraction into `MapScreenSettingsOverlay.kt`,
header normalization (PR #217), properties and opacity/transparency normalization, Card/Expander/
NestedCard refactor, drawer content measurement, marker "Belongs to track".

## Target Files
- `app/src/main/java/ykws/android/maro/ui/map/OverlayLayerParams.kt` — new; holds the C12 bundles (2 of 6)
- `app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt` — signature + destructure block
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — the single `OverlayLayer(...)` call site
- `xTrack/Ui_Settings/260909_FEAT_PLN_Ui_Settings_mapScreen-orchestration-monolith-refactor.md` — C12 plan of record
- `app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt` — extracted settings subtree
- `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt` — settings persistence

## Next Step
Tier 1c — the final code tier: bundle `TrackListOverlayData` (`trackSortState`, `trackFilterState`,
`trackListState`) and `MarkerListOverlayData` (`markers`, `markerSortState`, `markerFilterState`,
`markerListState`), then delete the two `rememberLazyListState()` defaults from the signature (a Composable
call cannot be a data-class field default) and remove the now-orphaned `rememberLazyListState` import.
Those two list states are the last fields the call site already supplies, so the defaults are dead code.
Keep the citation-drift pass as its own commit — the signature shrink invalidates the ~30 xTrack/doc
anchors into `OverlayLayer.kt`.
