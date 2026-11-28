---
name: Ozers
status: active
created: 2026-11-11 15:48
modified: 2026-11-28 16:05
---

# Feature: Ozers

**Description:**
Loose ends that belong to no single feature yet, and the map-temperature topic. Items here are parked until a feature owns them. The air and sea-water temperature tile (Open-Meteo, bottom-left of the map, heading the zone-icon column) lives here, with its plan in this folder.

## Summary
- The false-amber land stop on the zone look-ahead (10 m step, `landCrossedBefore`) still awaits its device pass: a spit narrower than 10 m can slip through, and a boat berthed within 10 m of land may drop a harbour zone.
- The zone-tag hold, the temperature source line, the silent successful refresh and the restored 300 m tag (`feature/zones-temp-info`) pass the unit tests and have not run on a device; the tile's rounds after its first device pass are unseen there too. Only the first round of `feature/zones-temp-info` had a review.
- Open, the user's choice: an outdated source line is drawn lighter than normal and likely hard to read, and with no time on the line that colour is its one sign of age.
- Open in the temperature plan: a water failure ends the lookup at that sample point, and nothing tests the memo guard, the problems stream or the tile text.
- `temperature.air.expiry.min` and `temperature.water.expiry.min` differ from their own `tldr` lines, the `AppConfig` defaults and the temperature plan.
- The temperature plan keeps its `Temperature` filename inside this folder, not the `Ozers` form the placement rule names.

## Sections

### Temperature

#### Key Files
- `app/src/main/java/ykws/android/maro/data/temperature/` — grid, cache, Open-Meteo and Copernicus clients, sea blend, refresh engine (pure Kotlin)
- `app/src/main/java/ykws/android/maro/ui/map/TemperatureViewModel.kt` — owns the engine, cache file, problems stream
- `app/src/main/java/ykws/android/maro/ui/map/TemperatureTile.kt` — the stateless tile and its problem pills
- `app/src/main/java/ykws/android/maro/ui/map/TemperatureSourcesLine.kt` — the info column's source line, following the tile's view
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
- A hold on the zone tags shows or hides the info text, headed by a temperature source line that follows the tile's view; a successful forced refresh raises no pill, and the 300 m tag shows again outside regulated zones; unit tests green, device pass owed → [`261128_FEAT_PLN_Ozers_zone-info-hold-and-water-sources.md`](261128_FEAT_PLN_Ozers_zone-info-hold-and-water-sources.md)
- Air and sea-water temperature tile, built and reviewed → [`261111_FEAT_PLN_Temperature_functional-decisions.md`](261111_FEAT_PLN_Temperature_functional-decisions.md)
- In-band 300 m zone fix and the entrance heads-up (`feature/track-dash-arriving-not-updated`): the 5 kn tag at the home port, the basin-counts-as-water check and the landward approach read OK on the device 2026-11-11; the false-amber land stop shipped with 1 774 tests and `apk-build` green and its device pass still owed (see Summary)
