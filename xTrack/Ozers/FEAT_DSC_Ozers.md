---
name: Ozers
status: active
created: 2026-11-11 15:48
modified: 2026-11-11 16:25
---

# Feature: Ozers

**Description:**
Loose ends that belong to no single feature yet, and the map-temperature topic. Items here are parked until a feature owns them. The air and sea-water temperature tile (Open-Meteo, bottom-left of the map, heading the zone-icon column) lives here, with its plan in this folder.

## Summary
- The in-band zone fix (`feature/track-dash-arriving-not-updated`) read OK on the device; the false-amber land check followed and awaits its device pass. The map-temperature topic routes here too.
- The air and sea-water temperature tile is built, reviewed and tested on `feature/temperature`, committed; the device pass was read fine before the later rounds (error toasts, interval removal, atomic cache, long-press restart), which are unseen on a phone.
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
- [x] Bottom-left 5 kn tag missing on arrival at the home port: it reads the map centre (`markerInZone300`) — read OK 2026-11-11, closed
- [x] Check whether the home-port basin counts as water (`isOnWater`) — read OK 2026-11-11, closed
- [x] False amber across land: the look-ahead to a SHOM zone now stops at the first land sample (10 m step, `landCrossedBefore`), 1 774 tests and `apk-build` green; device pass owed — a spit narrower than 10 m can slip through, and a boat berthed within 10 m of land may drop a harbour zone
- [x] The entrance heads-up is one straight ray along the GPS bearing, noisy at low speed — read OK 2026-11-11, closed
- [x] Device pass of the in-band zone fix (landward approach shows 5 kn and the speed colouring) — read OK 2026-11-11

## Rules

## Key Files
- `app/src/main/java/ykws/android/maro/ui/map/NavigationViewModel.kt` — shore pipeline, `bandZoneExitUnknown`, the heading rays
- `app/src/main/java/ykws/android/maro/ui/map/DashboardPanel.kt` — distance and speed-limit cards

## Docs

## Implemented
- Air and sea-water temperature tile, built and reviewed → [`261111_FEAT_PLN_Temperature_functional-decisions.md`](261111_FEAT_PLN_Temperature_functional-decisions.md)
