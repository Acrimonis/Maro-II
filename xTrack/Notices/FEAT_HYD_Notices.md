# Context Hydration — Notices — 2026-11-11

**Last Bake:** 2026-11-11 17:25 UTC — written by `#bake`; absence means never baked

**Directive trace:** All five covered action classes were met — no dependency was added, no machine-shaped data file was opened, `#new` ran on the user's own order and the other git writes follow their `#bake`/`#commit` invocation, the device was not touched, and every claim about the code rests on a file read.

## State

On `feature/notices-toasts`, cut from `origin/develop` at PR #404 with `--no-track`. The plan `261111_FEAT_PLN_Notices_notice-host.md` is complete as a design: D1–D9 are decided, the regression guards and ranked risks are written, and the migration order runs from the properties move (step 0) through the arbiter and host (step 1) to the system Toasts (step 5). No code has been written.

## Target Files

- `xTrack/Notices/261111_FEAT_PLN_Notices_notice-host.md` — the plan of record
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — pill states, dwells and the z-order of the snack host
- `app/src/main/java/ykws/android/maro/config/AppConfig.kt` — the load order the properties move depends on

## Next Step

Step 0: move the notice family's values into `maro.properties`, with the duplicate-key test landing before any key moves.
