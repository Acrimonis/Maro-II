# Context Hydration — Route — 2026-10-08

**Last Bake:** 2026-10-08 15:44 UTC — written by `#bake`; absence means never baked

**Directive trace:** Since the last bake (2026-10-08 10:31 UTC) the session ran on the user's own words — a `#impl` per phase of the performance plan, a plan review before each, then `#bake` and `#commit`. No dependency was added, no machine-shaped data file was opened, no work was started without an order, the device was not touched (the device passes remain the user's), and every claim about the code follows a file read; the one git write is the commit the user invoked.

## State

**The selective acquisition's cost is measured, cheapened and partly restructured — P0 to P4.1 of the plan landed, P4.2–P5 open.** The device pass of 2026-10-08 fixed the cause on the fine-layer build; a JVM harness then measured and gated the work: an arm now builds its shared fine layer **once** (was three times), the fine build's per-cell radial scan is bounded so `selective`'s build fell **29.3 → 13.0 ms**, and the boxed grid cell is gone — a `ByteArray` tag and a `DoubleArray` cost beside the five limit arrays, read through scalar accessors, with `MultipassCell` deleted. P4.1 anchored the lattice on a corridor-free origin shared by both layers and repaired a latent window-cell defect; the suite reads **1002 tests, 0 failed** and `apk-build.bat` builds.

**Three P4.1 items are owed, one carrying a blocker.** The harness's re-baselined pin records `8217 → 3135`, but 8217 is the P2/P3 recorded figure and a fresh corridor-anchor run reads `7916`, so the KDoc's before-figure is not like-for-like and the 301-read gap is unattributed; one corridor-anchor re-run settles drift from run-sensitivity, and the exact `assertEquals` pins are a latent flake if the harness is sensitive. Nothing guards the window-cell translation at a non-zero offset but that pin. And the anchor as implemented is the corridor's corner floored to a whole degree — stable within a degree, **not** the single region origin P4.2's cache needs.

**P4.2–P4.5 rest in two homes.** A design doc written unasked ([`261008_FEAT_PLN_Route_anchored-tiles.md`](261008_FEAT_PLN_Route_anchored-tiles.md:1)) restates §9's P4 while carrying the zone-stamp spec and the tile key's field list; one of the two must be retired before the tile map is built.

## Target Files

- `xTrack/Route/261008_FEAT_PLN_Route_selective-perf-eval.md` — the assessment, the measured device pass, the five-phase plan and the P0–P4.1 landed notes, in design
- `xTrack/Route/261008_FEAT_PLN_Route_anchored-tiles.md` — the unasked P4.2–P4.5 design doc; its single home is unresolved
- `app/src/main/java/ykws/android/maro/spatial/multipass/WalkLattice.kt`, `MultipassGrid.kt`, `RouteGridBuilder.kt`, `RouteFineWater.kt` and `app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt` — the anchored lattice, the flat grid, the bounded depth scan and the single-flight build
- `app/src/test/java/ykws/android/maro/spatial/RouteSelectivePerfEvalTest.kt` — the P0 harness, its split reading and its re-baselined pin
- `xTrack/Route/FEAT_DSC_Route.md` — the feature state doc

## Next Step

P4.2 — settle the tile design's single home and make the anchor **one region origin** before any tile map is built on it.
