# Hydration: Ui_General

**Session:** Multi-session — fan tweak, toast/progress full-width, overlay styling, icon migration, docs restructure.

**State:**
- `fan tweak [x]` — scrim removed, MapView.setOnTouchListener pass-through dismiss
- `toast and progress dialog [x]` — full-width padding fix (56dp→6dp)
- `overlay styling [x]` — unified navy 80% bg + frost layer + 2dp borders on toast/progress/error
- Material icons migration — 10 Canvas icons → Compose library + 4 standalone ImageVector .kt files

**What happened:**
- Removed fan scrim, added MapView.setOnTouchListener for pass-through dismiss.
- Fixed toast/progress bottom padding from legacy 56dp/76dp to 6dp.
- Unified all 3 bottom overlays with `buttonActionBgColor` + `uiSettingsCardBackground` frost layer + 2dp colored borders.
- Migrated 10 Canvas icons to Material/standalone: added `material-icons-extended`, 4 icons as standalone .kt files (Stacks, Output_circle, Activity_zone, Conversion_path).
- Removed dead `CircleRingIcon`.
- Created `docs/material-icons-standalone-guide.md`.
- Restructured docs: README → MARO_ARCHITECTURE hub, cross-reference table in GLOBAL_CONTEXT.md.

**Key Files:**
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — scrim, padding, overlay styling
- `app/src/main/java/ykws/android/maro/ui/map/FanIconComponents.kt` — all icon replacements
- `app/src/main/java/ykws/android/maro/ui/icons/` — 4 standalone ImageVector files
- `gradle/libs.versions.toml`, `app/build.gradle.kts` — material-icons-extended dependency

**Last Bake:** 2026-06-24 09:13 UTC
