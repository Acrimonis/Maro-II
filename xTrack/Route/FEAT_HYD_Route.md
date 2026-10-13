# Context Hydration — Route — 2026-10-13

**Last Bake:** 2026-10-13 14:18 UTC — written by `#bake`; absence means never baked

**Directive trace:** Since the last bake (2026-10-11 11:20 UTC) the session ran on the user's own words: `#new routing-angles`, then the corner investigation — the read-only listing over the captures, the per-corner tally printed from the pass, seven `adb` fetches from the user's own runs, `#impl` on the three scopes the captures proved, and three mid-session decisions (`keep every point the line was given`; `make the shore test relative`; `settle the guard bend to bend`). No dependency was added, no machine-shaped data file was opened, no work was started without an order, the agent never touched the device, and every claim about the code follows a file read.

## State

**The branch.** `feature/routing-angles`, cut from `origin/develop` by the user's own `#new` at the session's start and unpublished at this bake.

**The corner pass is settled as one law, and it was earned by measurement.** A bend's room is the whole **significant** leg — the straight to the nearest vertex the pass will consider, a kink too flat to be a corner keeping its own drawn point and taking no room. Two bends on one leg **share** it, weighted by their own angles; a close pair turning the same way, within the tangent a pace-speed curve needs for their summed angle, is read as **one turn** over the crossing of its two outer legs; the corner floor is a **target** the room may draw under, down to the pass's own 1 kn bottom; the coast test is **relative to the corner it replaces**, the shallow wall staying absolute; and the overlap guard measures bow to bow, a bow taking back the points it has passed.

**The instrument is the session's other deliverable.** [`RouteAvoidEngine.traceCornerVerdicts`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt) prints, per solve, one `CORNERS` summary — total, pace, `slowed`, outcome counts — and one `CORNER` line per bend that stands, takes a share or carries a slowdown: index, coordinates, the answer, the turn, both legs, `ask`, `emitted`, `shared`, `room`, the speed, the **limit in force** at it, whether it stands **in the 300 m band** and whether it is in a zone. A bend taken at the pace with no share prints nothing. This is what turned seven runs of guesswork into named answers.

**What the device said, run by run.** The captures and the first logs: 18 standing bends, every one refused for room, none for coast, depth or zone. Then `FLOOR_ROOM` at the Salis approach (`ask=12.8 room=12.8 speed=5.00`) and shallow bends capped at 5 kn; then the pair folded into one 51° corner taken at the full pace; then the significant leg reading `ask=1398.8 m` with `room` still 33.9 m — the guard's own cap — and the harbour approach `ROUNDED` at 4.94 kn where it had refused `BOW_COAST`. The last log holds no `BOW_*`, no `NO_ROOM` and no `FLOOR_ROOM` at all.

**The belt is a limit, not a price.** [`limitAtFor()`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RoutePassPrimitives.kt:189) answers the strictest of a zone's limit and the 300 m band's own limit inside the band's width, and both stand whatever their `enabled` switch says — "a switch prices the search, it never suspends the limit". The 5 kn held over the southern water is therefore law, and only `route.avoid.zone300.limitKn` or the band's width changes it.

**What is owed.** The device pass (R97) on the two bends the guard's correction moved — `43.53305,7.11067` and `43.54471,7.15744`, which should now read their 1,398 m and 890 m legs at or near the pace — is the user's own. The one item left of this family is **why the walk places a corner inside the 50 m coast margin at all**, the twin that would correct the line rather than forgive it.

**A second writer worked on this scope.** A variant of the significant-leg rule arrived mid-session, half-wired and with fixtures disagreeing with its own drawing branch; it was joined up and its contradictions settled in favour of keeping every point the line was given, its now-dead `STRAIGHTEN_OFFSET_M` constant and `offsetM` helper removed.

## Target Files

- `app/src/main/java/ykws/android/maro/spatial/multipass/RouteCornerPass.kt` — the whole law: `compoundLine` and `mergeCrossing` (the pair as one turn), `sharedLegReach` (the leg shared by angle), `significantLegM` (the room past a kink), the floor branch and `MIN_CORNER_KN`, the relative `coastFloorM`, the bow-to-bow guard and `RoundedCorner.tangentM`
- `app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt` — `traceCornerVerdicts`, the tally's one home
- `app/src/test/java/ykws/android/maro/spatial/multipass/RouteCornerPassTest.kt` — every rule pinned, each fixture naming the `AppConfig` code default it assumes
- `xTrack/Route/261013_FEAT_PLN_Route_corner-pass-fixes.md` — the measurements, the three scopes, the fixtures and the hops
- `app/src/main/assets/maro.properties`, `app/src/main/java/ykws/android/maro/config/AppConfig.kt` — `route.turn.minSpeedKn`'s new meaning, in its three homes

## Next Step

No code task is open on the feature; the device pass on those two bends is the user's own.
