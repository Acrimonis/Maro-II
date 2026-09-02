# ZoneTile — Hydration Snapshot

**Baked:** 2026-09-02 21:35 UTC+2

## Active State
- **Subfeature:** none
- **Branch:** feature/zone-info

## What Changed This Session
1. **Zone info text per-line scrim** — `RegulatedZoneInfoText` lines now render on a semi-transparent rounded scrim (`ui.settings.text.scrim`, navy `#16213E` @ 30%) with 4dp corners, 3/1dp padding, and 2dp line spacing so white text stays readable over any map tile.

## Design Decisions
- Per-line scrim (not a single panel) so each line carries its own contrast pocket and the map stays visible between lines.
- Token-driven via `colors.properties` so scrim color/alpha is tunable at runtime without Kotlin changes.

## Target Files
- `app/src/main/java/ykws/android/maro/ui/map/RegulatedZoneComponents.kt`
- `app/src/main/java/ykws/android/maro/config/AppConfig.kt`
- `app/src/main/assets/colors.properties`
