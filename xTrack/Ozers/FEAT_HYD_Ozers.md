# Context Hydration — Ozers — 2026-11-11

**Last Bake:** 2026-11-11 16:10 UTC — written by `#bake`; absence means never baked

**Directive trace:** None of the five covered action classes stopped anything this session: no dependency was added, no machine-shaped data file was opened, work started only on the user's `#impl` orders, the device was left to the user (who ran it), and the one unverifiable claim about the sea API's replies was flagged as unverified rather than asserted.

## State
The air and sea-water temperature tile is implemented and committed on `feature/temperature`, not pushed. It builds (`assembleDebug`) and its 56 unit tests pass: grid, cache, Open-Meteo client, engine, and the pure tap rules. The settings section is the on/off switch and the Open-Meteo credit; the fetch interval and the settings "Refresh now" were removed. Two independent reviews of the last rounds found no loop risk.

## Target Files
- `xTrack/Ozers/261111_FEAT_PLN_Temperature_functional-decisions.md` — the decisions, what shipped, the open findings
- `app/src/main/java/ykws/android/maro/data/temperature/TemperatureEngine.kt` — refresh rules, back-off, water lookup, memo
- `app/src/main/java/ykws/android/maro/ui/map/TemperatureTile.kt` — the tile, its toasts, its text fit
- `app/src/main/assets/maro.properties` — the `temperature.*` block

## Next Step
Look at the whole feature on the phone once with the latest rounds; if a water 4xx toast ever shows, its reason decides whether a refused sea square should be skipped and remembered.
