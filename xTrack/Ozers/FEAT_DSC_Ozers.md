---
name: Ozers
status: active
created: 2026-11-11 15:48
modified: 2026-11-11 16:29
---

# Feature: Ozers

**Description:**
Loose ends that belong to no single feature yet, and the map-temperature topic. Items here are parked until a feature owns them. The air and sea-water temperature tile (Open-Meteo, bottom-left of the map, heading the zone-icon column) lives here, with its plan in this folder.

## Summary
- The false-amber land stop on the zone look-ahead (10 m step, `landCrossedBefore`) still awaits its device pass: a spit narrower than 10 m can slip through, and a boat berthed within 10 m of land may drop a harbour zone.
- The air and sea-water temperature tile is built, reviewed and tested on `feature/temperature`; the device pass was read fine before the later rounds (error toasts, interval removal, atomic cache, long-press restart), which are unseen on a phone.
- Open in the temperature plan: a failing sea square ends the lookup at that square, and nothing tests the memo guard, the notices stream or the tile text.
- The temperature plan keeps its `Temperature` filename inside this folder, not the `Ozers` form the placement rule names.

## Sections

### Temperature

#### Key Files
- `app/src/main/java/ykws/android/maro/data/temperature/` — grid, cache, Open-Meteo client, refresh engine (pure Kotlin)
- `app/src/main/java/ykws/android/maro/ui/map/TemperatureViewModel.kt` — owns the engine, cache file, notices
- `app/src/main/java/ykws/android/maro/ui/map/TemperatureTile.kt` — the stateless tile and its toasts
- `app/src/main/java/ykws/android/maro/ui/map/TemperatureViewChoice.kt` — the pure tap and view rules
- `app/src/main/assets/maro.properties` — the `temperature.*` block

#### Docs
- `261111_FEAT_PLN_Temperature_functional-decisions.md` — the functional decisions and what shipped

## Todos

## Rules

## Key Files
- `app/src/main/java/ykws/android/maro/ui/map/NavigationViewModel.kt` — shore pipeline, `bandZoneExitUnknown`, the heading rays
- `app/src/main/java/ykws/android/maro/ui/map/DashboardPanel.kt` — distance and speed-limit cards

## Docs

## Implemented
- Air and sea-water temperature tile, built and reviewed → [`261111_FEAT_PLN_Temperature_functional-decisions.md`](261111_FEAT_PLN_Temperature_functional-decisions.md)
- In-band 300 m zone fix and the entrance heads-up (`feature/track-dash-arriving-not-updated`): the 5 kn tag at the home port, the basin-counts-as-water check and the landward approach read OK on the device 2026-11-11; the false-amber land stop shipped with 1 774 tests and `apk-build` green and its device pass still owed (see Summary)
