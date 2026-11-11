# Context Hydration — Ozers — 2026-11-11

**Last Bake:** 2026-11-11 16:29 UTC — written by `#bake`; absence means never baked

**Directive trace:** The merge from develop stopped at its confirmation on a non-trivial overlap and ran only on the user's yes; no dependency was added, no machine-shaped data file was opened, work started only on the user's orders, and the device was left to the user.

## State
The air and sea-water temperature tile is built and committed on `feature/temperature`, rebased onto develop at PR #401, and the feature is folded into `Ozers` beside develop's loose ends. The temperature code builds (`assembleDebug`) and its 56 unit tests pass, as do develop's three incoming test classes; the full suite was not run. The parked in-band zone work is done except the device pass of the false-amber land stop.

## Target Files
- `xTrack/Ozers/261111_FEAT_PLN_Temperature_functional-decisions.md` — the decisions, what shipped, the open findings
- `app/src/main/java/ykws/android/maro/data/temperature/TemperatureEngine.kt` — refresh rules, back-off, water lookup, memo
- `app/src/main/java/ykws/android/maro/ui/map/TemperatureTile.kt` — the tile, its toasts, its text fit
- `app/src/main/java/ykws/android/maro/ui/map/NavigationViewModel.kt` — the shore pipeline whose land stop awaits the device pass

## Next Step
Take one phone pass over both: the temperature tile with the latest rounds, and the land stop near a harbour; if a water 4xx toast ever shows, its reason decides whether a refused sea square should be skipped and remembered.
