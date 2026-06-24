# Hydration: Ui_General

**Session:** Multi-session — fan tweak, toast/progress full-width, overlay styling, icon migration, docs restructure, point count in track cards.

**State:**
- `fan tweak [x]` — scrim removed, MapView.setOnTouchListener pass-through dismiss
- `toast and progress dialog [x]` — full-width padding fix (56dp→6dp)
- `overlay styling [x]` — unified navy 80% bg + frost layer + 2dp borders on toast/progress/error
- Material icons migration — 10 Canvas icons → Compose library + 4 standalone ImageVector .kt files
- `manage tracks + point count [x]` — "Track List..." → "Manage Tracks..." in drawer; added `pointCount` to TrackSummary proto (@13) + TrackRepository population + "xxx pts" display in track cards left of pin/share icons

**What happened:**
- Removed fan scrim, added MapView.setOnTouchListener for pass-through dismiss.
- Fixed toast/progress bottom padding from legacy 56dp/76dp to 6dp.
- Unified all 3 bottom overlays with `buttonActionBgColor` + `uiSettingsCardBackground` frost layer + 2dp colored borders.
- Migrated 10 Canvas icons to Material/standalone: added `material-icons-extended`, 4 icons as standalone .kt files (Stacks, Output_circle, Activity_zone, Conversion_path).
- Removed dead `CircleRingIcon`.
- Created `docs/material-icons-standalone-guide.md`.
- Renamed "Track List..." → "Manage Tracks..." in TrackDrawerOverlay.
- Added `pointCount: Int = 0` (@ProtoNumber 13) to TrackSummary data class, populated from `track.trackPoints.size` in TrackRepository.rebuildIndex().
- Displayed "${summary.pointCount} pts" between date/time and pin/share icons in TrackHistoryOverlay track cards.
- Restructured docs: README → MARO_ARCHITECTURE hub, cross-reference table in GLOBAL_CONTEXT.md.

**Key Files:**
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — scrim, padding, overlay styling
- `app/src/main/java/ykws/android/maro/ui/map/FanIconComponents.kt` — all icon replacements
- `app/src/main/java/ykws/android/maro/ui/icons/` — 4 standalone ImageVector files
- `app/src/main/java/ykws/android/maro/data/track/Track.kt` — added pointCount to TrackSummary
- `app/src/main/java/ykws/android/maro/data/track/TrackRepository.kt` — populate pointCount in rebuildIndex
- `app/src/main/java/ykws/android/maro/ui/map/TrackDrawerOverlay.kt` — "Manage Tracks..." rename
- `app/src/main/java/ykws/android/maro/ui/map/TrackHistoryOverlay.kt` — "xxx pts" display
- `gradle/libs.versions.toml`, `app/build.gradle.kts` — material-icons-extended dependency

**Last Bake:** 2026-06-24 11:24 UTC
