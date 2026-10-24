<!-- scope: feature -->
# Route — the comment a saved route carries at creation

Branch `feature/route-comment`. Scope: the two-line comment written on the track a route is saved as.
Nothing else about the acquisition or the track pipeline moves.

## What is asked

Upon route creation, the saved track carries the comment, one line then a second:

```text
#[rank] • [type] • [speed] • [pref]
Routed on [date] in [s.s] sec
```

Example: `#1 • Balanced • Now (24 kn, History) • 3x` over `Routed on 2026-10-01 in 3.2 s`.

## Assessment

- No schema change: [`Track.comment`](../../app/src/main/java/ykws/android/maro/data/track/Track.kt:35)
  already exists, is projected into [`TrackSummary.comment`](../../app/src/main/java/ykws/android/maro/data/track/Track.kt:129),
  shows in the track list/card and exports as GPX `<cmt>`.
- The one save site is [`writeRouteTrack`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:2597),
  which calls [`TrackFromCourse.build`](../../app/src/main/java/ykws/android/maro/data/track/TrackFromCourse.kt:69) —
  that builder never sets `comment` today, so it ships as `""`.

## Token map

- `#[rank]` → [`RouteRankView.ranks`](../../app/src/main/java/ykws/android/maro/ui/map/RouteOverlay.kt:402)
  at the saved page's lookup id.
- `[type]` → the rung word ([`route_rung_balanced`](../../app/src/main/res/values/strings.xml:849))
  from the page's `descriptionResId`.
- `[speed]` → the panel's own [`route_header_pace_any_fmt`](../../app/src/main/res/values/strings.xml:721)
  construction — `Now (24 kn, History)`.
- `[pref]` → the Slow-water dial's word (Fastest · 1x … 4x · Ignore time).
- `[date]` → [`RoutePlan.computedAtMs`](../../app/src/main/java/ykws/android/maro/ui/map/RouteViewModel.kt:96)
  as `yyyy-MM-dd`.
- `[s.s]` → the search duration — **not captured anywhere today**; a new measurement is owed.

## Design settled by the review

- The comment is data of the route, written exactly once at the original save; a followed or resumed
  route keeps it and never re-writes — the save door disables once the line is a track (R59), and
  [`isRouteSaved`](../../app/src/main/java/ykws/android/maro/ui/map/RouteViewModel.kt:1037) is the predicate.
- Rank and rung word vanish when the acquisition ends: [`followPage`](../../app/src/main/java/ykws/android/maro/ui/map/RouteViewModel.kt:882)
  clears `_pages`/`_verdict` before [`enterFollowing`](../../app/src/main/java/ykws/android/maro/ui/map/RouteViewModel.kt:935),
  so a followed line saved later has no live ladder. The rank is read **at the time of save**: live from
  [`RouteRankView`](../../app/src/main/java/ykws/android/maro/ui/map/RouteOverlay.kt:402) while the acquisition
  is open, and from the rank the route carried when the search ended once `Following` — the ladder being gone,
  that carried rank cannot move, so it is the rank at save time. The rung word and the session's search
  duration are carried the same way; the pace source and dial word are read at save.
- Carrier: [`RoutePlan`](../../app/src/main/java/ykws/android/maro/ui/map/RouteViewModel.kt:77) — its
  `reversed()` copies every field, so the comment survives the loss mirror (R99) — or a session map beside
  [`noteRouteSaved`](../../app/src/main/java/ykws/android/maro/ui/map/RouteViewModel.kt:1045).

## Decisions taken

- Locale: the scaffold (`Routed on … sec`) and the embedded words are read **at save time**, so the comment
  freezes the locale that was active then — accepted.
- Rank timing: the rank recorded is the rank **at the time of save** — live while the acquisition is open,
  the frozen end-of-search rank once following.
- Fallback: a route saved by a build predating this feature has no comment and is never re-written, so it
  stays commentless — accepted, not back-filled.

## Changes

- [`TrackFromCourse.build`](../../app/src/main/java/ykws/android/maro/data/track/TrackFromCourse.kt:69) gains
  `comment: String = ""` through to the constructor.
- [`writeRouteTrack`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:2597) composes the comment
  at save time from the rank-at-save, the rung word, the pace source, the dial word and `computedAtMs`.
- [`RouteViewModel`](../../app/src/main/java/ykws/android/maro/ui/map/RouteViewModel.kt) records the search
  duration (arm → finalise) for `[s.s] sec`.
- Both locales gain the scaffold keys, read at save time.

## Implemented

Shipped on `feature/route-comment` (2026-10-24): a saved route's track now carries the two-line comment
composed at save time — `#rank • type • speed • pref` over `Routed on date in s sec` — the rank read at the
time of save (live while the acquisition is open, the frozen end-of-search rank once following) and every
word read from the locale active at that instant.

- [`TrackFromCourse.build`](../../app/src/main/java/ykws/android/maro/data/track/TrackFromCourse.kt:69) gained
  a `comment` parameter through to the constructor — no schema change, the field being
  [`Track.comment`](../../app/src/main/java/ykws/android/maro/data/track/Track.kt:35).
- [`RoutePlan`](../../app/src/main/java/ykws/android/maro/ui/map/RouteViewModel.kt:77) gained
  `searchDurationSec` (arm → landing, wall clock), set at
  [`RoutePlan.of`](../../app/src/main/java/ykws/android/maro/ui/map/RouteViewModel.kt:203); the new
  [`commentFacts()`](../../app/src/main/java/ykws/android/maro/ui/map/RouteViewModel.kt:1017) reads the live rank
  while `Choosing` and the rank `captureFollowedFacts()` froze into `Following`.
- [`verdictDialLabel`](../../app/src/main/java/ykws/android/maro/ui/map/RouteOverlay.kt:366) and the panel's pace
  header were given non-composable cores —
  [`verdictDialWord`](../../app/src/main/java/ykws/android/maro/ui/map/RouteOverlay.kt:374) and
  [`paceHeaderWord`](../../app/src/main/java/ykws/android/maro/ui/map/RouteOverlay.kt:386) — so the comment reads the
  same words at save time; [`routeCommentFor()`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:2596)
  composes the two lines and [`writeRouteTrack`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:2631)
  passes them.
- Both locales gained `route_comment_routed_fmt` / `route_comment_routed_no_sec_fmt`.

Deviations from plan: the search-duration stamp uses wall-clock `System.currentTimeMillis()` rather than
monotonic `SystemClock.elapsedRealtime()`, which throws "not mocked" in local JVM unit tests; the seconds
decimal and date stay `Locale.US`, so `3.2 s` keeps a dot in French while the scaffold words follow the
active locale. Review hop: no blocking findings; the flagged pace-header duplication was folded into
`paceHeaderWord`. Build: `:app:compileDebugKotlin` and the scoped `TrackFromCourseTest`, `RoutePlanTest`,
`PartialPlanOfTest`, `PerPointSpeedInvariantTest`, `RouteAcquisitionTest` green (79 tests).
