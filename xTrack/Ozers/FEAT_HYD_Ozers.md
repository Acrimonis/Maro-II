# Context Hydration — Ozers — 2026-11-28

**Last Bake:** 2026-11-28 16:05 UTC — written by `#bake`; absence means never baked

**Directive trace:** Work started only on the user's orders, ambiguous requests being asked first; no dependency was added, no machine-shaped data file was opened, the device was left to the user, and claims about the code came from file reads, gesture behaviour being marked as expected rather than seen.

## State
On `feature/zones-temp-info`, a hold on the zone tags toggles the info text, a source line heads it following the tile (sea sources on the water view, AROME on the air view), a successful forced refresh raises no pill, and the 300 m tag shows again outside regulated zones. `assembleDebug` and 1 902 unit tests pass, 27 skipped. Only the first round had an Ask review. Nothing has run on a device.

## Target Files
- `xTrack/Ozers/261128_FEAT_PLN_Ozers_zone-info-hold-and-water-sources.md` — what shipped, the follow-ups, the open points
- `xTrack/Ozers/261111_FEAT_PLN_Temperature_functional-decisions.md` — the current temperature decisions, the source line included
- `app/src/main/java/ykws/android/maro/ui/map/RegulatedZoneComponents.kt` — the tag stack's hold, the info column, the 300 m entry
- `app/src/main/java/ykws/android/maro/ui/map/TemperatureSourcesLine.kt` — the source line
- `app/src/main/java/ykws/android/maro/data/temperature/TemperatureEngine.kt` — the source values published beside the readings
- `app/src/main/java/ykws/android/maro/ui/map/NavigationViewModel.kt` — the shore pipeline whose land stop awaits the device pass

## Next Step
One phone pass: the hold (fan open, screen locked, dragging near the tags), the source line on land and water in both tile views with its portrait wrapping and outdated colour, the 300 m tag away from regulated zones, and the land stop near a harbour.
