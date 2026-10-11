# Context Hydration — Tracks — 2026-10-11

**Last Bake:** 2026-10-11 17:15 UTC — written by `#bake`; the branch is `feature/troute-naming`, cut from `origin/develop` by the user's `#new` and still without an upstream.

**Directive trace:** no covered action stopped and nothing was bent: no dependency was added, no machine-shaped data file was opened, and the device was not touched — the naming was judged on `apk-build.bat` and the full unit suite, so the device pass stays owed. Every claim about the code came from a file read or a command's own output. The one gate asked was a genuine ambiguity — how the origin joins a name, and what "around La Salis" means — put to the user before any edit; the git writes are the user's, and `#commit` / `#push` ran on their word.

## State

A route and a recorded trip are both named on `feature/troute-naming`. **A name is a chain of segments** — the origin, then the ranked body (top two), then the destination — each empty one dropped and the survivors joined by the locale's connector, so a bodyless pair reads `<origin> to <destination>`, a pair with no origin reads `<body> to <destination>` and a lone segment stands alone. **The boat's home port never names anything**: an end inside the Port Salis geofence, or one whose label carries `salis`, is excluded from both ends of both kinds through the single `TrackNaming.namesHomePort`, and a trip's home-port end keeps its id so the body still drops it as an end. `TrackNaming.select` now carries the origin out of the body into the name, `TrackRecorder.composeTripName` shows an identified origin, and `MapScreen`'s `routeEndName` applies the same exclusion to a saved route. `apk-build.bat` SUCCESSFUL and the full suite green at **1142 tests**, 0 failures, 11 ignored.

**Open and recorded.** The **device pass is owed** over a trip's live name, the route's three forms and the home-port exclusion. The plan's §5 questions stand except the source end, now closed by its §11; the two-marker body cap was left unchanged because the origin is its own segment, and the home-port test reads the geofence regardless of `trackGeofenceEnabled`. **An open `## Walk` stands in the feature file** — the 2026-09-14 / 09-15 / 09-22 levels, some items parked — so `#bake`'s section-fold was reported and skipped.

## Target Files

- `app/src/main/java/ykws/android/maro/data/track/TrackNaming.kt` — the chain `compose`, the origin in `select`, `namesHomePort`
- `app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt` — `composeTripName` and `unlessHomePort`
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — `routeEndName`, `composeRouteName`
- `app/src/test/java/ykws/android/maro/data/track/TrackNamingTest.kt` — the chain shapes and `namesHomePort`
- `xTrack/Tracks/261010_FEAT_PLN_Tracks_marker-zone-naming.md` — the plan, its §11 and `## Implemented`
- `xTrack/Tracks/FEAT_DSC_Tracks.md` — the feature's Rules, Docs and Implemented
- `app/src/main/res/values/strings.xml`, `app/src/main/res/values-fr/strings.xml` — the connector and the From / To forms (read, unchanged)

## Next Step

Take the owed device pass over the naming — a trip's live name with its origin, a route's forms and the home-port exclusion — and settle the plan's residual §5 decisions; the feature file's open walk is the next thing to close.
