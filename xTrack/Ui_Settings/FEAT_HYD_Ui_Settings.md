# Ui_Settings — Hydration (2026-09-10)

## State
C12 OverlayLayer parameter-object collapse in progress on `feature/refact-C12` — the last slice of the
MapScreen orchestration-monolith refactor (step 2, C1–C11 already merged via PR #225). Two of six bundles
landed: `OverlayChrome` (7 — drawer/wizard visibility flags + `wizardStep` + `drawerState`) and
`MenuOverlayData` (12 — GPS mode, menu toggles, first-track/marker ids, track/marker map-referential
filters + counts), both in the new same-package `OverlayLayerParams.kt` and both public `@Immutable`
(public visibility is required — a public `OverlayLayer` cannot expose an `internal` type). `OverlayLayer`
went 89 → 72 params; both bundles are unpacked into 19 same-named locals at the top of the body so the
body and every child call stay unchanged. Builds SUCCESS with zero new warnings; Ask reviews 8/8 and 8/8.
The R10 recomposition metric is waived in writing (plan C12 appendix). Remaining: Tier 1b
(`SettingsOverlayData` 5 + `TrackInfoOverlayData` 4), Tier 1c (`TrackListOverlayData` 3 +
`MarkerListOverlayData` 4 plus the 2 dead `rememberLazyListState` defaults), the citation-drift pass,
doc sync, then `#bake`.

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
Tier 1b: bundle `SettingsOverlayData` (5) + `TrackInfoOverlayData` (4). ⚠ `SettingsOverlayData` carries
four identically-typed `ScrollState` fields — the highest silent cross-wire risk in C12 — so require a
construction↔destructure name diff, not just the destructure check. Also fold in the stale comment reword
near `OverlayLayer.kt:179` and keep the citation-drift pass as its own later commit.
