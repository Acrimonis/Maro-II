<!-- scope: feature -->
# Tracks — the slow-water time on a card (stops read from the stored track)

**Date:** 2026-11-01 · **Status:** in design, reviewed once (v2), every decision taken · **Order:** the user's word of 2026-11-01 —
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
