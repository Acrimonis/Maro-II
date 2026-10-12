# Context Hydration — Tracks — 2026-10-11

**Last Bake:** 2026-10-11 19:16 UTC — written by `#bake`; the branch moved to `feature/mrkr-imexport` (cut from `origin/develop` by the user's `#new`), on which the session wrote plans only, and the section fold is held by the feature's open `## Walk`. The earlier 17:15 bake on `feature/troute-naming` is folded in below.

**Directive trace:** no covered action stopped and nothing was bent: no dependency was added, no machine-shaped data file was opened, and the device was not touched — the naming was judged on `apk-build.bat` and the full unit suite, so the device pass stays owed. Every claim about the code came from a file read or a command's own output. The one gate asked was a genuine ambiguity — how the origin joins a name, and what "around La Salis" means — put to the user before any edit; the git writes are the user's, and `#commit` / `#push` ran on their word.

## State

**The trip file is planned to carry its markers (2026-10-11, `feature/mrkr-imexport`).** A track's export is designed to gain one payload per owned marker — every marker whose `trackId` is that track's id — in the same two forms the marker file uses, the protobuf blob and a readable JSON mirror, beside one standard `<wpt>` per marker so another app sees the trip's stops; the carriage depends on `xTrack/Markers/261011_FEAT_PLN_Markers_marker-import-export.md` for the codec, and nothing is implemented. Four rules are pinned: the bundled marker's `trackId` is rewritten to the imported track's id (kept on an update, minted on an import-as-new or a foreign file), a marker read from a plain waypoint arrives `confirmed` and `keepable` because the startup cleanup deletes `IDLE_AUTO && !keepable`, a duplicate track skipped on import takes its bundled markers with it, and the marker importer refuses a file carrying a `<trk>`.

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

Implement `xTrack/Tracks/261011_FEAT_PLN_Tracks_export-auto-markers.md` on `feature/mrkr-imexport` once the marker transfer lands, since it reads that plan's payload codec. Take the owed device pass over the naming — a trip's live name with its origin, a route's forms and the home-port exclusion — and settle the plan's residual §5 decisions; the feature file's open walk is the next thing to close.
