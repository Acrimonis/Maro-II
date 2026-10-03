# Saved route ends — the pair rides with the track, and the line is re-acquired

## 1. What was asked

- On **save**: when a route's start or destination is a flagged marker, store the marker with the track the route becomes.
- On **acquire**: when a stored route exists whose start and destination are the same two markers, pull the points out of it instead of searching.

## 2. Decisions — the user's word, 2026-09-30

- **Replace the search**: the stored line is the acquisition's answer; no engine call runs.
- The pair is stored **on the track and its summary** at proto 20/21.
- **Only flagged-marker ends count** — `RouteEndSelection.Marker`; `CurrentPosition` and `MarkerPosition` carry no identity and are never stored.
- The match is **directional**: the stored A→B line belongs to A→B and is not swapped for B→A — where the pair admits it the opposite arm **mirrors** the same line instead (§12, the extension of 2026-09-30).
- A re-acquired line's **save door stays shut** — its track already exists.

## 3. The pair's home

- `Track` gains `@ProtoNumber(20) val routeStartMarkerId: String = ""` and `@ProtoNumber(21) val routeDestinationMarkerId: String = ""` in [`Track.kt`](app/src/main/java/ykws/android/maro/data/track/Track.kt:60).
- `TrackSummary` gains the same two fields at 20/21 beside its [`route`](app/src/main/java/ykws/android/maro/data/track/Track.kt:131), so the match runs over the index with no track file loaded.
- **No `SUMMARY_INDEX_VERSION` bump**: an absent string decodes `""`, which is the honest value — no marker recorded — unlike the route bool whose absent `false` was wrong. Old routes therefore simply never match; that is the accepted, stated cost.
- An empty string is the sentinel for "this end was not a marker".
- **The summary projection is one site, and the plan names it**: [`TrackRepository.rebuildIndex()`](app/src/main/java/ykws/android/maro/data/track/TrackRepository.kt:316) copies `routeStartMarkerId = track.routeStartMarkerId` and `routeDestinationMarkerId = track.routeDestinationMarkerId` beside its existing `route = track.route` at [line 340](app/src/main/java/ykws/android/maro/data/track/TrackRepository.kt:340); [`save()`](app/src/main/java/ykws/android/maro/data/track/TrackRepository.kt:71) → [`updateIndex()`](app/src/main/java/ykws/android/maro/data/track/TrackRepository.kt:434) → `rebuildIndex()`, so the one copy site serves every save and read — without it the acquire match sees empty ids and finds nothing.
- **Two KDoc contracts are amended with the fields**: [`TrackFromCourse`](app/src/main/java/ykws/android/maro/data/track/TrackFromCourse.kt:36) "no field on the track points back at the route, deliberately" and [`TrackViewModel`](app/src/main/java/ykws/android/maro/data/track/TrackViewModel.kt:246) "the fields were deliberately left alone" both gain the clause that the two ids are persisted end data, while the live session link stays the only in-memory link.

## 4. The save half

- [`RouteEnds`](app/src/main/java/ykws/android/maro/ui/map/RouteViewModel.kt:194) gains `startMarkerId: String?` and `destinationMarkerId: String?`, null unless the selection is [`Marker`](app/src/main/java/ykws/android/maro/data/route/RouteEndSelection.kt:39).
- [`routeEndsAtTrigger()`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:974) fills the two ids from `routeStartSelection` / `routeDestinationSelection` — the same instant the points are frozen, so a later drawer change cannot re-point the standing line.
- `RouteViewModel` retains the armed pair for the session: set in [`arm()`](app/src/main/java/ykws/android/maro/ui/map/RouteViewModel.kt:288), cleared in [`end()`](app/src/main/java/ykws/android/maro/ui/map/RouteViewModel.kt:404), exposed as `armedMarkerIds()`.
- [`saveRouteTrack()`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1902) reads that pair and hands the two ids into [`TrackFromCourse.build()`](app/src/main/java/ykws/android/maro/data/track/TrackFromCourse.kt:59), which gains two parameters defaulting to `""` and sets them on the built `Track`.

## 5. The acquire half

- [`armRouteMode()`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1803) computes the ends; when **both** ids are non-null, it scans [`TrackViewModel.allSummaries`](app/src/main/java/ykws/android/maro/data/track/TrackViewModel.kt:64) for a summary with `route && routeStartMarkerId == startId && routeDestinationMarkerId == destinationId`, newest `startTimeMs` first.
- On a match it loads the full track through [`loadTrackDetail(id)`](app/src/main/java/ykws/android/maro/data/track/TrackViewModel.kt:361), rebuilds the plan, and calls `arm(ends, storedMatch)`; no `routesToCompute` and no `startLookup` runs.
- The match uses the `ends` already read at the trigger, never a second `routeEndsAtTrigger()`, so the pair and the points cannot drift within one arming.
- The stored-match branch **skips the short-pair guard**: [`routeEndsClearMinimum`](app/src/main/java/ykws/android/maro/ui/map/RouteOverlay.kt:194) stays for the search path, but a stored line is self-validating through `routePlanOf`'s two-point check, so a matching pair re-acquires even where the markers have since moved inside the minimum.
- `arm` gains an optional stored-match parameter (the rebuilt plan and the matched track id). On that branch it seeds one page with the **same plan instance** it registers in the session through [`putSession`](app/src/main/java/ykws/android/maro/ui/map/RouteViewModel.kt:444) — one rebuild, never two — so [`isRouteSaved()`](app/src/main/java/ykws/android/maro/ui/map/RouteViewModel.kt:433) matches by equality and already greys the save door with no new predicate.
- The stored branch enters `Choosing(start = storedPlan.start, plan = storedPlan, searching = false, asked = true)`: the start is the **line's own first point**, the truth of what was saved, not `ends.start` (the marker's current position, which may have moved since the save).
- The matched track id is what the plan is already written as, so the re-acquired line needs no further save.

## 6. The rebuild rule — reuse the shipped inverse

- The match calls the existing [`routePlanOf(track)`](app/src/main/java/ykws/android/maro/ui/map/RouteOverlay.kt:170), the inverse of the save already used by the track card's Follow door — no new helper.
- Its semantics are the ones to keep: `start` and `destination` are the first and last vertices, `legTimesSec` comes from the millisecond offsets (sub-second off the original plan, which rounded at save), `distanceM` and `durationSec` are read from the track's own figures, `computedAtMs = track.startTimeMs`, `destinationMoved = false`, `forcedCrossingZoneNames = emptyList()`, and it returns null for fewer than two points.
- **The two doors stay apart**: the acquisition's reuse stays in `Choosing` and must not ride [`followSavedRoute`](app/src/main/java/ykws/android/maro/ui/map/RouteViewModel.kt:408), which jumps Idle → Following with `followedTrackId`; the shared piece is `routePlanOf` alone.

## 7. The page and the panel

- A stored line is always the **sole** page, so the panel's [`PageBlock`](app/src/main/java/ykws/android/maro/ui/map/RouteConfirmPanel.kt:326) and the per-page description are never rendered for it.
- [`RoutePage`](app/src/main/java/ykws/android/maro/ui/map/RouteOverlay.kt:46) makes `lookupId`, `computationId` and `descriptionResId` nullable with null defaults; a stored page sets only `plan`. `disposeLookups` and `onUpdate` need no change — they are keyed by lookup ids a stored page never has.
- `pageRowText` treats a null description as empty, inert because stored pages never sit at index ≥ 1.

## 8. Strings

- No new string is required: the stored page's description is never shown and the save door reuses the existing disabled face. If implementation surfaces any wording (none is expected), it goes through `res/values/strings.xml` and `res/values-fr/strings.xml` by `@StringRes` id.

## 9. Tests

- `routePlanOf` is already shipped and covered by the Follow door's own tests, so no new rebuild test; extend its coverage only if the pair projection needs a pinned case.
- The match predicate: newest-first, `route`-only, the exact pair, and the **reversed** pair answered as the return trip; the no-match cases become one null id, an ordinary track and a legacy route carrying the empty sentinel, and the mirror's own cases are the points and leg times reversed, the fresh arming instant and the open save door.
- `RouteAcquisitionTest`-style coverage: arming with a stored match starts no lookup and lands `Choosing` with the plan already settled and the save door shut.

## 10. Record

- The next R-codes after R81 — the pair on the track and summary (R82), the arming match that replaces the search (R83), the directional flagged-marker rule (R84) and the shut save door (R85) — go to [`FEAT_DSC_Route.md`](xTrack/Route/FEAT_DSC_Route.md)'s `## Requirements`, the live home the archive's index names the retired book's successor. **Not the archived book**: `xTrack/Route/xxArchive/` is excluded from every summarising command and only its `INDEX.md` may be read, so a requirement written into a body there would be reached by nothing.
- [`FEAT_DSC_Route.md`](xTrack/Route/FEAT_DSC_Route.md) key-files and `## Docs` rows point here; the open Level 1 walk gains one trailing item for the bake to carry.

## 11. Out of scope

- `MarkerPosition` and `CurrentPosition` ends are never stored or matched — a point alone is not an identity.
- A fresh search running beside the stored line and a fallback-on-refusal are declined by the decision in §2; **reverse-pair reuse is not** — §12 proposes it, extending that decision rather than contradicting it.
- No migration or backfill of routes saved before the change — they never match, by construction.

## 12. The reverse match — the return trip (proposed and built 2026-09-30)

The decision of §2 matched the pair one way only. This section extends it with the **reverse** case — the everyday out-and-back — on the user's word of 2026-09-30.

**The match.**
- When the exact pair finds nothing, [`storedRouteMatch()`](app/src/main/java/ykws/android/maro/ui/map/RouteOverlay.kt:208) looks again for a summary whose `routeStartMarkerId` is the **armed destination** and whose `routeDestinationMarkerId` is the **armed start**, newest `startTimeMs` first, and answers it with `reversed = true` beside the plan and the matched track id.
- **No new legality gate**: the drawer's [`resolve()`](app/src/main/java/ykws/android/maro/data/route/RouteEndSelection.kt:89) has already dropped a marker not offered for its end, so a pair whose markers do not carry the opposite flags never reaches the trigger.
- **It fires only for double-flagged markers, and that is the feature's limiting fact** — [`eligibleMarkerIds`](app/src/main/java/ykws/android/maro/data/route/RouteEndSelection.kt:108) offers a marker at an end only while that end's flag stands, so the return trip is armable only when **each** of the two markers carries **both** `routeOrigin` and `routeDestination`; the usual one-origin/one-destination setup makes the reverse pair unarmable, and no gate inside this feature can change that. This is the strongest objection to the whole section and the user's call before it is built.
- The short-pair guard is skipped on the reverse match exactly as on the forward one (§5).

**The line is the stored one mirrored.**
- `points.reversed()`; `start` is the mirrored line's first point — the stored destination's — and `destination` its last.
- **The mirror is the forward inverse with its lists reversed**: the same stretch takes the same time either way, so mirrored leg *k* is stored leg *n−1−k* — [`mirroredPlanOf`](app/src/main/java/ykws/android/maro/ui/map/RouteOverlay.kt:208) takes [`routePlanOf`](app/src/main/java/ykws/android/maro/ui/map/RouteOverlay.kt:177)'s points and leg times and reverses both, so the **stored millisecond figures** carry over exactly. No haversine and no pace read (a stored null or zero pace leaves no hole), and each mirrored leg keeps the zone-capped duration the outbound line obeyed.
- **Its helper wraps that inverse**: `mirroredPlanOf(track, nowMs)` sits beside `routePlanOf` and reads it, keeping the distance from the track's own `distanceNm` as in §6, the duration as the mirrored legs' sum, and the arming instant for its date; it returns null under two points.
- **What it deliberately does not do**, and the file is why: the track records paces, never *why* a leg ran at its pace, so "re-time at today's pace" is not derivable — re-timing every leg at one pace would erase the caps and claiming today's pace through a 5 kn zone would be a lie. The mirrored line therefore shows the figures it was saved with, exactly as the forward match does in §6.
- No crossing badge (`forcedCrossingZoneNames = emptyList()`) and `destinationMoved = false`, as in §6; and the mirrored ends stay the stored ones, so a marker moved since the save leaves the line starting a few metres off it — the same accepted cost as the forward match.

**Its identity differs from the forward match.**
- The mirrored line is a **new plan**: `computedAtMs` is the arming instant, not the stored `startTimeMs`, so its name belongs to its own day and cannot collide with the forward track's.
- **Its save door stays open** — R85's shut door belongs to the forward match, whose matched track *is* the line; the return leg is a line no track holds, so the session link is not registered and saving writes a new track.

**Where it lives.**
- [`storedRouteMatch()`](app/src/main/java/ykws/android/maro/ui/map/RouteOverlay.kt:208) gains the second pass and [`StoredRouteMatch`](app/src/main/java/ykws/android/maro/ui/map/RouteViewModel.kt:217) gains `reversed`; `mirroredPlanOf` sits beside `routePlanOf` in the same file; `armRouteMode` mirrors the plan, mints the instant and passes a null track id so `arm`'s stored branch needs no new path.

**Requirements to add** (the epic's `## Requirements`, continuing from R85):
- **R86 — the reverse pair is matched and mirrored** — a stored line whose marker pair is the armed pair reversed is taken at its own mirrored paces rather than searched, an end pair being two markers and the water between them the same water the other way.
- **R87 — a mirrored line is a new plan with an open save door** — it takes the arming instant for its name and registers no session link, because the track that exists holds the other direction.

**Tests.** The predicate's reverse case and its refusal where the flags do not allow the swap; the mirror's points and leg times pinned as the stored figures reversed, tied end to end to a track `TrackFromCourse.build` produced; the fresh instant; and `isRouteSaved` false for a mirrored plan.

**Out of scope.** The two excluded ends of §11 stand — a marker pair only; no crossing badge on a mirrored line; and no current-pace re-timing, which the file cannot support.

**Outcome (2026-09-30).** Built as this section states: `storedRouteMatch` gained the reverse pass answering `StoredRouteHit(summary, reversed)` — the exact pair first, so the forward answer still wins — and [`mirroredPlanOf(track, nowMs)`](app/src/main/java/ykws/android/maro/ui/map/RouteOverlay.kt:208) sits beside `routePlanOf`, reversing its points and leg times and taking the arming instant for its date. `armRouteMode` mirrors on a reverse match and hands a null track id, so `arm`'s stored branch is unchanged and the save door stays open (R87). R86 and R87 are recorded in `FEAT_DSC_Route.md`'s `## Requirements`, R86 naming that it **extends R84's directional rule** rather than contradicting it. **One deviation, settled in the Ask review of 2026-09-30**: a matched track whose file will not load or rebuild falls back to the ordinary search, so the short-pair guard — skipped at arming because the stored line was to be reused — is **re-applied on that fallback**, and the guard is skipped only where the line is actually reused.
