<!-- scope: feature -->
# Tracks — the slow-water time on a card (stops read from the stored track)

**Date:** 2026-11-01 · **Status:** implemented 2026-11-01 (P0–P4; P5 not taken); the Ask hop's findings triaged 2026-11-11 and their fixes shipped the same day (`## Remediation`) · **Order:** the user's word of 2026-11-01 —
*"plan stop detection + plan card display extension (discuss how)"*, then *"g2"* and *"plan, review, update
plan, report"*. Origin: the Route todo *"The crown's card figures"* (the slow-water minutes on a saved route's
card), widened to recorded tracks. Owner: Tracks (the card, the store, the index); Route owns the attribution
the engine fills (`RouteSlowLimit`).

## What is there today

- **A saved route** carries its slow-water time as **text only**: the comment's zone line, composed at save
  from the plan's `slowLimitSeconds` ([`MapScreen.kt:2626-2656`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt)),
  open water as `durationSec − sum` (line 2629), its labels the `OW`/`EL`, `300 m` and `%1$.0f kn` strings
  (`values/strings.xml` ~797). A draft or read-back route has no attribution (lines 2624-2626). The seconds are
  not stored; the list card shows the comment ([`TrackHistoryOverlay.kt:808`](../../app/src/main/java/ykws/android/maro/ui/map/TrackHistoryOverlay.kt)).
- **A recorded track stores no point while the boat is stopped** ([`TrackRecorder.kt:908-909`](../../app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt)):
  a native stop is one long interval between two moving points. The live stop is already a net-distance rule
  ([`AdaptiveGpsPolicy`](../../app/src/main/java/ykws/android/maro/data/location/AdaptiveGpsPolicy.kt), the user's
  `stopDetectionTimeSec` / `stopDetectionDistanceM` settings), and the stored points have their own idle test,
  [`timelineIdleSec`](../../app/src/main/java/ykws/android/maro/data/track/TrackStats.kt) (an interval under
  `IDLE_MAX_SPEED_MPS` and `IDLE_MAX_DRIFT_M`, a `GAP` ending the run), which reconciles idle at finalize.
- **The stats grid** ([`TrackHistoryOverlay.kt:832-858`](../../app/src/main/java/ykws/android/maro/ui/map/TrackHistoryOverlay.kt))
  is `StatCell`s on three equal columns: a recording shows Total · Nav · Avg over Dist · Idle · Max, a route one
  row of Dist · Total (est.) · Avg (est.).
- **The lazy-classification precedent.** `TrackSummary.waterPointCount` / `landPointCount` (biased by one) and
  `TrackRepository.classifyUnclassified`, fed by a classifier the screen attaches
  (`MapScreen` → `TrackViewModel` → `TrackRepository.attachPositionClassifier`).

## The card row — G2 (the user's pick)

A third stats row on both cards, `Open water · 300M · Zones`:

- **On a route:** `300M` and `Zones` are the plan's own seconds, and **Open water = Total (est.) − 300M − Zones**,
  the comment's own remainder rule — so the three sum to the card's Total by construction.
- **On a track:** `300M` and `Zones` are the moving seconds spent there, and **Open water = Nav − 300M − Zones**
  — the same remainder rule on the card's own Nav, so the row always sums to Nav and no figure the card already
  shows is recomputed. (This settles v1's open question on Nav.)
- **One pure function** answers the three cells from integer seconds, so no cell drifts by a rounding.
- **Hidden until computed**, kept (zeros) once computed, so a list's cards keep one height; hidden for good
  where no figure can exist (a draft route, a route saved before this plan, a track with no time span).
- **No per-limit split anywhere** (the user's word, 2026-11-01): `Zones` stays one total; a saved route's
  comment and the search panel keep their own per-limit lines.

## Part A — the stops: the stored track's own idle test, shared

- **No new detector and no new keys.** The interval test `timelineIdleSec` already applies is extracted into one
  shared predicate (`isIdleStretch(prev, point)` in `TrackStats.kt`); `timelineIdleSec` and the slow-water walk
  both read it. On a native track it is the net-distance rule in effect, since a stop is one interval.
- **Its known limit, accepted:** a foreign GPX, or a track recorded with stop detection off, keeps stationary
  points at 1 Hz whose jitter can read as moving. A net-distance detector on the user's
  `stopDetectionTimeSec` / `stopDetectionDistanceM` (never new keys) is the follow-up if that case matters.

## Part B — the slow-water walk, behind a classifier seam

- **The classifier.** `NavigationViewModel` exposes a pure `(lat, lon) -> SlowWater?` — the band, a zone with its
  limit, or `null` for open water — from `SpeedZoneQuery.mostRestrictiveSpeedKn` and the coast distance;
  `isRouteRestricted` becomes `classifier(...) != null`, so the pace's seeder and this walk read one test.
- **One precedence.** A point in the band and a zone counts **once, the zone over the band**, as the route's
  attribution already decides (`RouteEta.kt` `slowTimeByLimit`); that rule is extracted into one shared
  function both walks call.
- **The walk.** `TrackSlowWater.of(points, classifier)`, pure, in `data/track/`: for each interval between two
  consecutive non-`GAP` points that is not an idle stretch, its seconds go to the bucket of the water at its
  start point. No 120 s cap — stops are removed explicitly and simplification leaves long straight intervals —
  a `GAP` alone ends a run, a negative interval (a GPX point with no time) is clamped to zero, and a track with
  no time span is not computed.
- **Where it runs** — the `attachPositionClassifier` seam, never Compose: the screen attaches the classifier
  once `RegulatedZonesRepository.loaded` and the coastline are both ready (so "no data yet" never bakes in as
  zero), and the repository computes in `summarize` and in a catch-up pass shaped like `classifyUnclassified`,
  under its write mutex on IO.

## Part C — storage

- **A track's figure lives on the summary only** — derived and rebuildable, like the point counts; writing old
  track files would reset `updatedAtEpochMs` and reorder lists. `TrackSummary` gains the entries (proto 24, a list
  of a new `@Serializable` entry with integer seconds — `RouteSlowLimit` is not serializable) and a stamp
  (proto 25, absent = not computed). A merge builds a new track, so its summary is computed afresh.
- **The stamp** is `RegulationMetadata.fetchTimestampMs`: a regulation bake re-marks every track uncomputed.
  **Named gap:** the band has no content stamp (`ZONE_DISTANCE_M` is a constant), so a coastline change does not
  re-mark.
- **A route's figure is a source fact** — the plan's seconds cannot be re-derived later — so `Track` gains the
  same two fields, written at save in `MapScreen.writeRouteTrack` through a new `TrackFromCourse` parameter,
  **only when `searchDurationSec != null`**, projected by the index pass, and never re-marked by a bake.
- **No index version bump:** absent fields decode as "not computed" (the `routeStartMarkerId` precedent), and the
  catch-up does the work.

## Strings

- New `track_stat_open_water` and `track_stat_zones` in `values` and `values-fr` (the French label must fit a
  third of the card); the band cell reuses the existing `300 m` word.
- **Contradiction, named not fixed:** `fmtDuration` already writes `h` / `m` / `s` as literals
  (`TrackHistoryOverlay.kt:1088-1097`), against AGENTS.md §1; the new row would read through it.

## Phases

1. **P0 — the shared predicates**, no behaviour change: `isIdleStretch` out of `timelineIdleSec`, the
   zone-over-band precedence out of `slowTimeByLimit`; the existing suites unchanged.
2. **P1 — the classifier seam and the walk**, with fixtures: a native stop (one long interval), a band-and-zone
   overlap, a `GAP`, a timeless GPX, a long simplified interval.
3. **P1b — the route's attribution aligned:** `slowTimeByLimit` counts every second on water with a limit
   through the shared precedence; `SlowTimeByLimitTest` re-read, the panel's column and the comment checked.
4. **P2 — the summary fields**, the stamp, the attach gate and the catch-up.
5. **P3 — routes:** the save writes the plan's seconds; drafts and old routes stay uncomputed.
6. **P4 — the G2 row** on both cards, both locales, its pure three-cell function tested.
7. **P5 (optional) — the route comment's zone line** composed from the stored field.
8. **The device pass (the user's):** a dive trip's card, the row summing to Nav, a route card summing to Total.

## Argued against

- **The value is modest** (assessed 2026-11-01): a route card already shows its zone times in the comment, and
  forecast against actual only reads on tracks driven from a saved route.
- **Cost:** two summary fields, two route fields, a seam and a catch-up — a change of the store, not of the
  display.
- **The aligned meaning moves the route panel's Speed limits column** — it now lists zones crossed at or under their limit too (Decisions).

## Review — 2026-11-01 (the `ask` hop), folded into v2

- **Blockers taken:** v1's net-distance detector and its two keys would have been a fourth stop notion in the
  code for a case the stored data mostly does not have → Part A reuses the stored idle test. The 120 s cap would
  undercount simplified tracks → dropped. A draft route would show false zeros → written only on a settled search.
- **Should-fix taken:** the compute moved out of Compose onto the classifier seam; the attach gated on the zones
  and the coastline; a track's figure kept on the summary only, keyed on the regulation stamp, no version bump;
  the route's save site corrected to `MapScreen.writeRouteTrack`; open water as the remainder on both cards.
- **Corrections taken:** the comment's real labels, the version constant (`SUMMARY_INDEX_VERSION`), two proto
  numbers for a list plus its marker, IDLE boat markers do store their session's start and end (only near a
  marker).
- **Noted, not taken:** domain orchestration already sits in `MapScreen` composables (the history seeding,
  `routeCommentFor`, `writeRouteTrack`); the stop predicate has three homes (`AdaptiveGpsPolicy`,
  `timelineIdleSec`, `TrackMerger`) — P0 joins two of them, the live one stays apart.

## Decisions — the user's, 2026-11-01

- **One meaning on both cards: time on water with a limit, the zone over the band.** Today a route charges only
  legs slower than the pace and only priced zones (`RouteEta.kt:357`, `RoutePassModels.kt:80-86`), so a zone at
  or above the pace, or every zone with pricing off, reads as open water. The route's attribution
  (`slowTimeByLimit`) is redefined to the track's meaning — every second on water with a limit, whatever the
  pace and the pricing switch — read through the shared precedence of Part B. **What moves with it:** the
  panel's Speed limits column and the comment's zone line, which print the same attribution, now also show a
  zone the route crossed at or under its limit. **What does not:** the crown, the drawing and the clock — R98
  keeps the crown's figure the line's own excess under its limits, apart from this published table. A phase of
  its own (P1b), with `SlowTimeByLimitTest` re-read.
- **Routes saved before this plan keep the row hidden for good** — accepted.
- **Foreign GPX / stop detection off:** the jitter limit is accepted; no net-distance follow-up.
- **Demo tracks get the row.**

- **No per-limit split on any surface** — the selected route gets nothing beyond its card.

## Open

None: the definition, the old routes, the GPX jitter, the demo tracks and the split are decided above.

## Outcome — `#implement`, 2026-11-01 on `feature/rooouting`

- **Shipped P0–P4.** `SlowWater.of` (`data/model/SlowWater.kt`) holds the zone-over-band precedence for both readings; `isIdleStretch` is shared by `timelineIdleSec` and `TrackSlowWater.of`; the route table counts every second on limited water over every corridor ring, the crown keeping its priced slow-legs-only walk (`slowLegsOnly`), pinned by `anUnpricedRingStandsInTheTableAndLeavesTheCostUnmoved`; `Track` and `TrackSummary` fields 24/25; the repository's `attachSlowWaterClassifier`, the summary projection and the `attributeSlowWater` catch-up; the `Open water · 300M · Zones` row on both card kinds, both locales.
- **Deviations.** The attach waits on `NavigationViewModel.slowWaterStamp` (zones loaded and their index published), not `loaded` alone; the map's dashboard card reads the figure off the stored summary too; the crown and the table are two walks per rung; a track with no time span is retried once per attachment rather than marked.
- **Verification.** `apk-build.bat` SUCCESS; full unit suite 1 717 tests, 27 skipped, 0 failures; the device pass is the user's.
- **Ask hop's findings, open (none blocking):**
  1. should-fix — the catch-up walks the library under `writeMutex`, blocking the recorder's checkpoint on a first run or after a regulation bake.
  2. should-fix — `slowWaterAt` runs the full `SpeedZoneIndex.query` (nearest edge included) per track point; a containment-only read would do.
  3. should-fix — the dashboard looks the stored summary up in the filtered lists, so a recording hidden by the list filter loses its row there.
  4. should-fix — `zones.properties` overrides change no stamp, so a recording is not re-marked when they change (a second named gap beside the coastline's).
  5. should-fix — the table's second `LegWater` re-reads the band per leg; the `===` shortcut never fires in production.
  6. should-fix — three docs still state the old slow-only meaning (`RouteAvoidEngine.kt` rung note, `RouteOverlay.kt` `routeSlowLimitEntries`, `SlowTimeByLimitTest` class doc).
  7. nit — `inAnyZone` identity check fragile; `slowWaterPassDone` can mark a newer source done; a metadata edit before attach drops a valid figure; `ROUTE_STAMP` and `NO_REGULATION_STAMP` share `1L`.
  8. nit — a track's row overshoots Nav when the walk's idle differs from the recorder's (open water clamps at 0); excluded zones count on a track but not on a route; a zone in the band is keyed at the band's limit, now more visible; the dashboard's two hand-built summaries grew.

## Remediation — 2026-11-11 on `feature/tracks-slow-water`

**Order:** the user's word of 2026-11-11 — the triage (*fix 1, 2, 3, 5, 6 and 7's race; drop 4, the rest of 7, and 8*), then *"plan all fixes, review plan, update, report"*, then `#impl`. Implemented the same day (see `### Outcome`). Every claim below comes from a file read on this branch.

### Triage — the verdicts

| # | Verdict | Phase |
|---|---|---|
| 1 | fix — the catch-up's walks leave the write lock | R1 |
| 2 | fix — a box-rejected containment read replaces the full point query | R2 |
| 3 | fix — the dashboard reads the unfiltered summaries | R3 |
| 4 | **dropped, named** — `zones.properties` ships in the APK and changes only with a rebuild; it stands beside the coastline as the second named gap (Part C) | — |
| 5 | fix — the table's legs read the band once | R4 |
| 6 | fix — the stale comments | R5 |
| 7a | fix — `slowWaterPassDone` marks only the source the pass ran under | R1 |
| 7b–d | **dropped** — the metadata-edit-before-attach loss self-heals at the next catch-up; `ROUTE_STAMP` and `NO_REGULATION_STAMP` sharing `1L` is harmless because the catch-up skips routes; the `inAnyZone` identity check is deleted by R4 | — |
| 8 | **dropped** — the overshoot is clamped by design (`slowWaterCells`); the band key and the hand-built summaries are cosmetic; "excluded zones count on a track" stands as the plan's accepted difference between a route's corridor set and the whole zone list | — |

### R1 — the catch-up off the write lock, and the pass marked for its own source (fixes 1, 7a)

- **The defect.** `listTracks` holds `writeMutex` across `attributeSlowWater` ([`TrackRepository.kt:158-160`](../../app/src/main/java/ykws/android/maro/data/track/TrackRepository.kt)), which loads and walks every stale recording. `saveCheckpoint` and `deleteCheckpoint` take the same mutex (lines 251-259), so a live recording's 30 s checkpoint queues behind a first run or a post-bake run.
- **The shape.** `listTracks` takes a new `slowWaterPassMutex` around the sequence below. It serialises concurrent list calls as `writeMutex` did, but the recorder never takes it:
  1. under `writeMutex`: `classifyUnclassified(readIndex())`, unchanged;
  2. when a pass is owed (`!slowWaterPassDone` and a source attached), **unlocked**: for each recording whose stamp differs from the source's, `load` (lock-free; every file is replaced atomically) and `TrackSlowWater.of`, keeping `(id, track.updatedAtEpochMs, entries)`;
  3. under `writeMutex` again: `readIndex()` afresh and apply each result as a **compare-and-set**: only where that id's summary still carries both the `updatedAtEpochMs` and the `slowWaterStamp` the walk saw. A track saved meanwhile already took a figure from `summarize`, and a track deleted meanwhile is absent from the fresh index, so both are skipped. The index is written once if anything changed, and the merged list returned.
- **Fix 7a, by reference rather than flag** (review blocker). `slowWaterPassDone: Boolean` becomes `@Volatile slowWaterPassDoneFor: SlowWaterSource?`. Attach writes `slowWaterSource` alone, a pass is owed when `slowWaterPassDoneFor !== slowWaterSource`, and the pass ends by setting `slowWaterPassDoneFor` to the source it captured. An attach that lands at any instant, the end of a pass included, leaves the two references different, so the refresh it queues sweeps under the newer stamp. No check-then-act window is left.
- **Left alone, named:** `classifyUnclassified` still samples under `writeMutex`. The **rebuild path** still walks under it too: `readIndex` rebuilds a missing, corrupt or older-version index through `summarize` (`rebuildIndexLocked`, `updateIndexLocked`'s unknown files), which runs `TrackSlowWater.of` whenever a source is attached. Neither is in the findings, and a rebuild is rare, so both keep their lock.
- **Tests** (beside `theRepositoryComputesARecordingAndProjectsARouteAsSaved` in `TrackSlowWaterTest`):
  - a classifier that blocks on a latch: `saveCheckpoint` completes, under a timeout, while `listTracks` is parked mid-walk;
  - a track re-saved during the pass keeps the figure its save computed, and the stale result is not merged over it;
  - a re-attach mid-pass leaves the next `listTracks` computing under the new stamp.

### R2 — a containment-only zone read for the walk (fix 2)

- **The defect.** `slowWaterAt` ([`NavigationViewModel.kt:1568-1572`](../../app/src/main/java/ykws/android/maro/ui/map/NavigationViewModel.kt)) calls `SpeedZoneIndex.query`, whose `PolygonIndexBase.status` scans the cell's candidate edges for the nearest boundary. It then ray-casts **every zone's polygon with no box reject** (`zones.indices.filter { pointInsidePolygon(...) }`, [`PolygonIndexBase.kt:178`](../../app/src/main/java/ykws/android/maro/spatial/PolygonIndexBase.kt)). That runs for every point of every recording, and the walk reads only `mostRestrictiveSpeedKn`.
- **The read that exists.** `ZoneSet.strictestDisplayLimitKnAt` ([`ZoneGeometry.kt:217`](../../app/src/main/java/ykws/android/maro/spatial/multipass/ZoneGeometry.kt)) returns the least `speedLimitKn` over the zones that contain the point. Holes are excluded, and each zone is rejected by a box computed once per set. That is the same quantity, and the route clock already reads it.
- **The shape.** The publication at `NavigationViewModel.kt:1146-1172` builds `ZoneSet(zones)` beside `SpeedZoneIndex(zones)` from the same `SpeedZoneBuilder.build` list, and carries the set inside `IndexedZones` (line 282).
  - `slowWaterAt` reads `_indexedZones.value?.limits?.strictestDisplayLimitKnAt(lat, lon)`, and `isRouteRestricted` follows unchanged.
  - **The attached classifier is bound to its stamp's publication** (review nit, tightened by the code review). `NavigationViewModel.slowWaterAttachment` builds the stamp and a lambda bound to that publication's `IndexedZones.limits` in the same `combine`, and `MapScreen` attaches the pair as one value. A first design read `_indexedZones` live when the effect ran, which left a window of one dispatch where a stale stamp met a newer zone set.
  - **Publication order** (review nit). `_indexedZones` is written before `_speedZoneIndex` (lines 1170-1171 swapped). The speed-history seed, which waits on the index (`MapScreen.kt:1664`), then never reads `isRouteRestricted` against the previous set.
  - The dashboard's zone readouts keep `SpeedZoneIndex`.
- **Equivalence.** Both ray casts are even–odd with the same half-open vertex rule, `(a > y) != (b > y)` ⇔ `a ≤ y < b ∨ b ≤ y < a`. They compute the crossing from opposite ends of the edge, so they can differ only on a boundary at floating-point precision.
- **Test:** a new `SlowWaterZoneReadTest` with overlapping zones of two limits, a zone with a hole, and points inside, in the hole, outside and inside a box but outside its ring. It asserts `ZoneSet(zones).strictestDisplayLimitKnAt == SpeedZoneIndex(zones).query(...).mostRestrictiveSpeedKn` at every point.
- **Not touched:** the band's `distanceToCoastMeters`, already read only where no zone answers (`SlowWater.of`'s lazy `inBand`).

### R3 — the dashboard's figure read off the unfiltered summaries (fix 3)

- **The defect.** `storedSummaryOf` ([`OverlayLayer.kt:299-305`](../../app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt)) searches `summaries` and `routeSummaries`. Both are filtered (`TrackViewModel.recomputeListViews`), so a recording hidden by the list filter loses its row on the map's card.
- **The fix.** Collect `trackViewModel.allSummaries` (the unfiltered source, both kinds) and look the id up there alone. The comment is corrected with it.
- **Test:** none of its own, because the lookup is one line of Compose state. The device pass covers it: a filtered-out recording opened from the map keeps its row.

### R4 — the table's legs read the band once (fix 5)

- **The defect.** `inZone(...)` returns a new lambda on every call ([`RoutePassPrimitives.kt:519`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RoutePassPrimitives.kt)), so `inAnyZone === inZone` ([`RouteAvoidEngine.kt:1467`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt)) never holds in production. The second `LegWater.of` re-reads the band at every leg's midpoint on every rung.
- **The fix.** `LegWater` gains `withZone(timed, inZone)`, which keeps `midInBand` and re-reads only the zone predicate. `rungSlowWater` builds `anyWater = water.withZone(timed, inAnyZone)`, and the identity check is deleted. Every figure is unchanged by construction.
- **What it saves, exactly** (review nit): one band read per leg midpoint per rung. `insideSeconds` still samples the band along each band leg in both walks (`RouteEta.kt:383-384`, `477-481`), and that stays as it is.
- **The `LegWater` class doc** (`RouteEta.kt:91-95`, "a priced ring", "every slow leg") is corrected in the same edit: the table reads every leg under `inAnyZone`.
- **Test** (review should-fix: a figures-only test passes before and after): one `SlowWaterSecondsTest` case on an all-open-water line counts the `inBand` calls, `legs` after the fix against `2 × legs` today. The existing 5 `rungSlowWater` cases stay green unchanged.

### R5 — the stale comments (fix 6)

- [`RouteOverlay.kt:125-129`](../../app/src/main/java/ykws/android/maro/ui/map/RouteOverlay.kt) — "Empty when nothing slowed the route" becomes "empty when the route meets no limit".
- [`SlowTimeByLimitTest.kt:10-16`](../../app/src/test/java/ykws/android/maro/spatial/multipass/SlowTimeByLimitTest.kt) — the class doc speaks of "each slow leg" and of "the full seconds its own legs take". It is rewritten to every leg on limited water, slow or not, a straddling leg paying only its inside fraction, with `slowLegsOnly` named as the crown's reading.
- [`values/strings.xml:938-940`](../../app/src/main/res/values/strings.xml) — the Speed limits comment ("the time the route spends slowed … the ramp into and out of each counted in") becomes the time on each limit's water, ramps counted in none.
- **Correction to finding 6:** the `RouteAvoidEngine.kt` rung note it names (lines 1426-1446) already reads the new meaning. The third stale text is the `strings.xml` comment above. Comment only, so no translation moves.

### Verification

- The scoped suites first (`TrackSlowWaterTest`, `SlowWaterZoneReadTest`, `SlowWaterSecondsTest`, `SlowTimeByLimitTest`, `SpeedZoneIndexTest`), then the full unit suite and `apk-build.bat`.
- The device pass is the user's: a filtered-out recording's card on the map, and a first launch after a regulation bake while recording.

### Argued against

- **R1 is the only fix with risk:** a lock-free walk plus a merge can lose an update. The merge rule (same `updatedAtEpochMs`, stamp still stale) is what guards it. The cheaper alternative, the lock taken per track, keeps the whole loop inside the repository's lock discipline but still holds the lock through each walk.
- **The value is modest:** the checkpoint delay only bites on the first run or after a bake, and the dashboard row only on a filtered list.
- **Against dropping 4** (the reviewer's objection): an APK update with edited overrides is exactly when the figures go stale, and folding an overrides hash into the stamp would close it cheaply. The drop stands as the user's call.

### Review — 2026-11-11 (the `ask` hop), folded

- **Blocker taken:** R1's `slowWaterPassDone = (slowWaterSource === source)` kept a check-then-act window, so an attach between the compare and the write would still be lost. It is replaced by the `slowWaterPassDoneFor` reference.
- **Should-fix taken:** the merge is a compare-and-set on the walked `updatedAtEpochMs` and stamp; R4's test counts band reads rather than figures; the rebuild path's lock-held walks are named beside `classifyUnclassified`.
- **Nits taken:** the classifier attached as a zone-set snapshot; `_indexedZones` published before the index; R4's saving stated exactly; the `LegWater` doc and the test doc's "full seconds" folded into R4 and R5.
- **Confirmed:**
  - R2's equivalence holds: the same build list, `speedLimitKn` on both sides, degenerate rings, holes and an empty set alike.
  - R3's `allSummaries` holds both kinds unfiltered.
  - R5's three targets are stale, and the rung note already reads the new meaning.
  - R1 has no deadlock (`listTracks` alone takes the new mutex, from `TrackViewModel.kt:342`).
  - The drops 7b–d and 8 hold.
- **Noted, not taken:** an unlocked `load` can quarantine a file a save has just replaced, an edge that predates this plan. A second `listTracks` now waits on the whole pass, so a rename shows when the pass ends; a `tryLock` fallback is not taken.

### Outcome — `#impl`, 2026-11-11 on `feature/tracks-slow-water`

- **Shipped R1–R5; the original findings 1, 2, 3, 5, 6 and 7a are closed.** 4, 7b–d and 8 stand dropped as triaged.
  - R1: the catch-up walks off the write lock and merges by compare-and-set; `slowWaterPassDoneFor` replaces the flag.
  - R2: `ZoneSet.strictestDisplayLimitKnAt` replaces the full point query, with `slowWaterAttachment` pairing the stamp and the classifier.
  - R3: the dashboard reads `allSummaries`.
  - R4: `LegWater.withZone`.
  - R5: three comments.
- **Deviations.**
  - The classifier is handed over with its stamp as one `slowWaterAttachment` value (the plan had a snapshot function beside the stamp flow); `slowWaterStamp` and `slowWaterClassifier()` are gone.
  - The pass exits early when nothing is stale, without the lock or an index decode, and marks itself done after the index write.
- **Verification.** `apk-build.bat` SUCCESS; full unit suite 1 770 tests, 27 skipped, 0 failures (6 new: three repository tests, the zone-read equivalence pair, the band-read count). The device pass is the user's.
- **Ask hop's findings (one pass).** No blocker. The pairing window, the early exit and the done-after-write order were taken; open:
  1. nit — the `updatedAtEpochMs` half of the merge's compare-and-set is unpinned: the re-save test passes on the stamp half alone, since two saves can share a millisecond on Windows.
  2. nit — `ZoneSet(zones)` for an empty list could be `ZoneSet.EMPTY`.
  3. noted — the zone-read equivalence test pins the reads, not the `IndexedZones` wiring.
