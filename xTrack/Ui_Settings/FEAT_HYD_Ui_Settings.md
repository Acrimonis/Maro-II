# Ui_Settings — Hydration (2026-09-06 11:25)

## State
Settings page UI, persistence (SharedPreferences), settings widgets, and UX. Latest work (2026-09-06): the **marker "Belongs to track"** feature was implemented. The shared `MarkerCardContent` composable (used by both the marker list card and the marker detail drawer) now renders a **bottom row** showing the owning track name with a trailing **chevron** when the marker has an associated track; tapping the chevron opens that track's detail drawer. A nullable `onOpenTrack` callback was added to `MarkerCardContent`; the drawer's own standalone "Belongs to track" row was deleted to avoid double render, and both list and drawer callbacks close their current surface before opening the track drawer. Prior session work (Card/Expander/NestedCard structural refactor + guideline consolidation) remains implemented. Header-normalization and properties-normalization plans remain pending (not implemented).

## Target Files
- `app/src/main/java/ykws/android/maro/ui/map/MarkerManagementOverlay.kt` — `MarkerCardContent` bottom track row + chevron, list `onOpenTrack` wiring
- `app/src/main/java/ykws/android/maro/ui/map/MarkerDrawer.kt` — drawer `onOpenTrack` wiring, removed standalone "Belongs to track" row
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — `openTrackDetail` hand-off from marker surfaces

## Next Step
Open the pending `header-normalization` and `properties-normalization` plans (both 260905, not yet implemented), then the `render-tweaks` todo and deferred `settings apply on close` section. BUILD SUCCESSFUL.
