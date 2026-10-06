# Context Hydration — Route — 2026-10-06

**Last Bake:** 2026-10-06 13:18 UTC — written by `#bake`; absence means never baked

**Directive trace:** Since the last bake (2026-10-06 07:33 UTC) every change ran on an order — the fine-walk
plan and its Phase 1, the stored-route removal on the user's own words re-asked and confirmed, the EMODnet
filter on *Yes same filter*, five device passes the user took themselves, three rounds of instrumentation on
*instrumentalize*, and the removal of every instrument on *remove all instrumentation and dead code*. No
dependency was added, no machine-shaped data file was opened, no work was started without an order, the device
was touched only by the user, and every claim about the code follows a file read.

## State

**One fix kept, one defect accepted, and a session of diagnosis closed by decision.** The routing's depth read
now passes the chart's own **EMODnet shallow filter** at `emodnetShallowCutoffM`, so a coarse EMODnet cell below
the cutoff no longer walls a chord while a shallow fine-source cell still does. The **stored-route pull-back is
gone** — R83 to R87 struck, one acquiring case in its place, every arming running a fresh acquisition — and the
**off-axis first leg is accepted as a known defect**: at λ = 0 on open water every monotone staircase is
equally optimal, so the search's tie-break picks the lattice's axes and the pull then tautens that staircase
into one long leg at its own bearing before turning.

**The diagnosis's conclusions, kept for the record** — the step walls were real water (Litto3D, confidence 90,
2.2 to 2.9 m), the coast walls one real **mainland** OSM segment at 41 to 49 m against the 50 m margin, and the
long leg needed no wall at all: its cells were free, its chords all clear, and the straight alternative priced
at zero. **Every instrument that found this was removed on the same word**, so those figures live in
`route-phase9` to `route-phase13` at the repo root and in the two plans, never in the code.

**Gates green, with the three known reds and no fourth** — the suite stands at **937 / 3 / 11** (the parked
`route.avoid.fine.cellRatio` test and the two `TrackOutlineTest` dash reds) and `apk-build.bat` is green.

## Target Files

- `app/src/main/java/ykws/android/maro/spatial/multipass/MultipassWorld.kt`, `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt`, `app/src/test/java/ykws/android/maro/spatial/multipass/RouteEmodnetShallowGateTest.kt` — the kept fix and its guard
- `app/src/main/java/ykws/android/maro/ui/map/RouteOverlay.kt`, `RouteViewModel.kt`, `MapScreen.kt`, `app/src/test/java/ykws/android/maro/ui/map/RouteAcquisitionTest.kt` — the removal, and the case that pins it
- `xTrack/Route/261006_FEAT_PLN_Route_stored-pullback-removal.md` — **landed**, its Outcome written
- `xTrack/Route/261006_FEAT_PLN_Route_open-water-bends.md` — **closed by decision**, the accepted defect and what would re-open it
- `xTrack/Route/FEAT_DSC_Route.md`, `xTrack/GLOBAL_CONTEXT.md` — this bake's record

## Next Step

**Nothing on this line waits.** The fix is in, the defect is accepted by decision, and both plans carry the
reasoning, so the next session opens from the record rather than from this code. The one gap the bake could not
close is stated rather than hidden: the Route row in `GLOBAL_CONTEXT.md`'s summary table is a single
4 000-character line, so its one-liner and its Modified date were left as they stand while the feature's own
front matter and the Focus History carry this session's chapter.
