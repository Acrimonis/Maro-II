<!-- scope: feature -->

# Card extraction refactor — MarkerCard.kt + TrackCard.kt

## Context

Both the marker and track list/detail flows share a single card body composable that is currently
buried inside a large host file:

- `MarkerCardContent` lives in [`MarkerManagementOverlay.kt`](app/src/main/java/ykws/android/maro/ui/map/MarkerManagementOverlay.kt:253)
  (host file ~530 lines) — used by the marker **list** and the marker **detail drawer**.
- `TrackCardContent` + `LiveTrackCard` live in [`TrackHistoryOverlay.kt`](app/src/main/java/ykws/android/maro/ui/map/TrackHistoryOverlay.kt:409)
  (host file ~952 lines) — used by the track **history list** and the track **detail drawer**.

This is a pure structural/relocation refactor. No behavior change. It is intentionally a **separate**
delivery from the "marker belongs to track" feature (260906_FEAT_PLN_Ui_Settings_marker-belongs-to-track.md),
which was kept surgical to minimize regression surface.

## Goal

Give each shared card its own file with a single, documented composable contract, mirroring the
existing shared-card pattern already used by both flows.

## Design

### New file: `MarkerCard.kt`
Relocate from `MarkerManagementOverlay.kt`:
- `MarkerCardContent` (public/internal composable)
- its private helpers: `coordinateHeader`, `CenteredPinIcon`
- the `MARKER_*` layout constants (`MARKER_CARD_RADIUS`, `MARKER_ACCENT_BAR_WIDTH`,
  `MARKER_CONTENT_PAD_H`, `MARKER_CONTENT_PAD_V`, `MARKER_HEADER_FONT_SIZE`,
  `MARKER_TITLE_FONT_SIZE`, `MARKER_GEOMETRY_FONT_SIZE`, `MARKER_DESC_FONT_SIZE`)
- move the required imports (icons, `ButtonColors`, `IconPickerDialog`, `MarkerColors`, etc.)

### New file: `TrackCard.kt`
Relocate from `TrackHistoryOverlay.kt`:
- `TrackCardContent`
- `LiveTrackCard`
- their private helpers (`StatCell`, `fmtDuration`, `fmtNm`, `fmtKnFromMps`, `EditingField`, etc.)
- move the required imports

### Host files
- `MarkerManagementOverlay.kt` and `TrackHistoryOverlay.kt` keep their list-scaffold logic and simply
  import the relocated card composables.
- `MarkerDrawer.kt` and `OverlayLayer.kt` already reference the card composables by name — no change
  needed beyond the import resolution (same package `ykws.android.maro.ui.map`).

## Files Affected
- NEW `app/src/main/java/ykws/android/maro/ui/map/MarkerCard.kt`
- NEW `app/src/main/java/ykws/android/maro/ui/map/TrackCard.kt`
- `app/src/main/java/ykws/android/maro/ui/map/MarkerManagementOverlay.kt` (remove relocated code)
- `app/src/main/java/ykws/android/maro/ui/map/TrackHistoryOverlay.kt` (remove relocated code)
- No navigation/ViewModel/OverlayLayer logic changes.

## Verification
- Build via `apk-build.bat`.
- Manual: marker list + marker detail drawer render identically; track history list + track detail
  drawer render identically (incl. live-recording card).
