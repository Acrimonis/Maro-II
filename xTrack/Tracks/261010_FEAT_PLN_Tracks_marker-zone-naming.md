<!-- scope: feature -->
# Tracks — naming a recorded trip from the markers it entered, weighted by time

**Status:** design, in discussion — 2026-10-10, branch `feature/rte-track-naming`. Nothing implemented; every rule below is settled except the items under §5.

## 1. The rule

A recorded track's name is built at finalize from the **user markers the boat met on the way** — a Circle or Corridor it entered, or a Pin it came near, whether or not a stop was ever recorded. The markers are ranked by a **time-weighted** version of the app's own match score, and the name keeps the top two. A marked destination always closes the name; a loop back to the same marked place contributes nothing.

This answers the case the current code cannot name: a trip with no dwell long enough to trigger a stop marker, which today falls back to the bare timestamp. [`computeFinalTitle`](../../app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt:1541) reads `BoatMarker`s alone, so a turn-around writes nothing.

**A registered stop keeps an explicit precedence** over a mere pass-by (settled 2026-10-10): the stop's marker takes the first slot by rule, and the timed score orders whatever lies below that rank.

**The name is built live and updated as the trip goes** (settled 2026-10-10): the same pass runs while recording, driven by the existing title poll and the marker-change observer, so the card's name grows with the trip. The poll ticks every three minutes, so the live name moves in coarse steps between marker changes; the finalize pass repeats the work over the whole raw set and stands as the last word.

## 2. The score — `sortTimedScore(trackPoints, markers)`

- **The per-position order already exists** as [`sortScore`](../../app/src/main/java/ykws/android/maro/spatial/MarkerMatcher.kt:514): a containment match scores 0.x and a proximity match 1.x, the fraction of the marker's own size ranks depth or closeness, the smaller zone breaks ties, and user markers precede `IDLE_AUTO`. The timed score **reads that score and never re-derives it** — the ordering keeps one home.
- **Time is duration.** For each marker, sum over the trip's intervals `intervalSeconds × sortScore(marker, position)` for the intervals in which that marker matches; lower wins. Ascending order is the order the markers appear in the name.
- **One number carries the tuning.** The score is not weightless: the per-shape weights (Pin 0.5, Circle 1.0, Corridor 2.0) live in `maro.properties`, so the ranking is tunable from there. The naming adds no constant of its own — duration is measured and the score is the matcher's.
- **The bands overlap.** Containment spans roughly 0–2 and proximity 1–3, so a Pin the boat is almost on can outrank a wide Corridor the boat is only just inside. "Most specific wins" holds within a band, not across them.
- **Cost is a non-issue.** A typical trip is a few hundred points and the longest about a thousand, against at most some fifty markers, so the pass is a few hundred thousand comparisons at worst; the matcher's range pre-filter drops far markers before any land test, and an in-zone match is pure geometry.
- **Walk the raw fixes, not the simplified ones.** The stored points are heavily simplified, so a short visit can fall between two retained points and vanish; the raw list is still in hand at finalize, before simplification replaces it, and its size is trivial.
- **Read the matcher's real order and sample nothing**, since the cost is already bounded — no leaner second implementation, and no sampled subset to keep in step.
- **Named caveat.** The product trades duration against depth, so a marker grazed for an hour can beat one perfectly centred for a minute; forcing the destination last (§3) carves out the exception that matters.

## 3. The name shape

- **Tokens:** the separator is `, `, and the connector is ` to ` in English and ` à ` in French (settled 2026-10-10). The connector is the one place the name carries literal UI text, so a track named under one locale keeps that locale's connector.
- **Body:** the top **two** markers by ascending timed score.
- **Destination:** when the last point matches a user marker distinct from the source, the destination is appended as ` to <Dest>` **regardless of the time spent there** — `marker#1, marker#2 to Dest`.
- **Empty body:** when the destination is the only marker, the name is the destination alone — `Dest`, with no leading connector (settled 2026-10-10).
- **Loop:** when the source and the destination are the same place within the **geofence radius** and that place is marked, the shared endpoint is dropped from the name, leaving the ranked body alone — `marker#1, marker#2`.
- **Marked at an end** means the match-list winner at the exact first or last point, a Pin or a zone, `IDLE_AUTO` excluded.
- **A registered stop takes the first slot by rule**, not by score, with the existing tiers — diving beats manual beats idle — ordering the stops among themselves and the timed score filling the remaining slot.
- **Length:** the cap is `track.name.maxLength`, raised to `254` (settled 2026-10-10); beyond it each token is cut in proportion to its own length, an ellipsis marking every cut. The exported file name is capped separately, at 100 characters, by [`sanitizeFileName`](../../app/src/main/java/ykws/android/maro/ui/map/TrackSharing.kt:14) — which also lets the accented French connector through, so a long name reaches the list and the export differently.
- **Icons:** a marker's manually-set icon leads its own name with one space between them, `🤿 Cap d'Antibes`, and a marker without one shows its bare name (settled 2026-10-10). One pure helper, `markerLabel`, is the single home of that rule.

- Examples: `Cap d'Antibes, 🤿Le Village` for a loop, `Cap d'Antibes, 🤿Le Village to Port de La Salis` for a distinct marked destination, and `Port de La Salis` alone when the destination is the only marker.

## 4. The comment fallback

When the title has **no marker at all**, the list of zones traversed is written into the track **comment** instead — the existing `recomputeDescription` lines, so the journey is described even when the name cannot be. With no marker there is also no name: the timestamp from [`trackAutoName`](../../app/src/main/java/ykws/android/maro/data/track/Track.kt:86) stands, as today (settled 2026-10-10). Whether the traversed-zone list is appended only in that case or always is still open (§5).

## 5. Open decisions

- **The source end** — excluded from the body like the destination, or allowed to rank in it.
- **The comment fallback's exact shape** — conditional on a marker-free title, or always appended.
- **The geofence as the same-place test** — its meaning when the geofence is disabled, or when the trip is nowhere near Port Salis.
- **Replace or sit beside the old tiers** — whether the new name replaces `computeFinalTitle`'s diving-manual-idle ladder or runs next to it.
- **One cap, not two** — `topZoneNames` already takes its own top two, so one place should own that number.
- **The `Route ` prefix** — whether it survives anywhere, now that the from-to forms have replaced it for a save while a draft still wears it.

## 6. Where it lands

- `data/track/` — a new pure builder for the timed score and the name, called from both the live title poll and the finalize hook, the latter repeating it over the whole raw set before simplification.
- **No new provider was needed**, contrary to this plan's first draft: the per-point matches come from the `whereAmI` seam the recorder already holds, so the whole `MarkerSetProvider` idea was dropped in the build.
- `spatial/MarkerMatcher.kt` — read-only; the ordering's one home.
- `data/track/Track.kt` — `trackAutoName` stays the fallback.
- `ui/map/TrackSharing.kt` — the file-name length cap and the trimming rule.
- Rejected: extending `sortScore` itself, since it is a stateless per-position comparator the live map calls on every fix; and a separate lean containment pass, since the numbers make it unnecessary.

## 7. Tests

Timed ranking order; a shared loop endpoint dropped; the destination appended regardless of dwell; the destination alone as an empty body; the two-marker cap; proportional trimming with its ellipsis; the no-marker fallback into the comment; a marker's icon leading its own name; the `sortScore` reuse; ties. The route's own name is built in `MapScreen`, so its three forms are device-tested rather than unit-tested.

## 8. Verification

`apk-build.bat` SUCCESS, and the full `gradlew.bat testDebugUnitTest` green — a naming change reaches shared helpers, so a scoped run would not be evidence.

## 9. Route naming (the router's own line)

A route saved as a track is named from its two ends rather than the dated `Route <instant>` of [`routeTrackName`](../../app/src/main/java/ykws/android/maro/data/track/TrackFromCourse.kt:169) (settled 2026-10-10).

- **Each end is named by the marker zone containing it**, else by the flagged marker that end stands on, each wearing its own icon (settled 2026-10-10).
- **The drawer's fixed entries are entries, not names**, so an unnamed end contributes nothing: both ends named read `<origin> to <destination>`, one named end reads `From <origin>` or `To <destination>` ([`route_name_from_fmt`](../../app/src/main/res/values/strings.xml:250)), and neither named leaves the route to its own dated `Route <instant>` name (all settled 2026-10-10).
- **Named consequence:** the instant survives only for a route whose ends nothing identifies, so two *named* routes over the same pair still carry the same name.

## 10. The save toast (settled 2026-10-10)

A track or a route just written names itself in a two-second toast — `"%s" saved` / `"%s" enregistré` — through the app's own [`MapStatusBanner`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:5174) idiom, the same one the route refusal already wears, rather than a platform Toast. The recording's save rides the recorder's `Finalized` event, which now carries the saved name; the route's save rides `writeRouteTrack`.

## Implemented

- **Shipped** (2026-10-10, `feature/rte-track-naming`): [`TrackNaming`](../../app/src/main/java/ykws/android/maro/data/track/TrackNaming.kt) computes the ascending `Σ seconds × score` fusion, selects the body and the destination, and trims in proportion with an ellipsis; [`TrackRecorder`](../../app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt:1488) names the trip live on the title poll and again at finalize over the raw points, with the comment falling back to the traversed markers when no name forms; [`MapScreen`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:2311) names a saved route `from to destination` through `routeEndLabel`; `snackbar_saved` toasts the written track or route; and `track.name.maxLength` stands at 254 against the file name's separate 100.
- **Deviations:** no `MarkerSetProvider` was added — the recorder already holds `whereAmI`, so the per-point matches come from the seam that exists; `MarkerMatcher.markerOf`/`sortScore` were widened from private to `internal` instead of the plan's read-only promise; and the icon prefix, the geofence-as-same-place test and the `Route ` prefix shipped as defaults rather than as decided rules.
- **Second pass, same day:** a marker's own icon now leads its name with one space through `markerLabel`, the route ends return no name rather than the drawer's fixed labels so an unnamed pair keeps the dated `Route <instant>`, and a single named end reads `From <origin>` or `To <destination>`.
- **Still inconsistent:** `pollTitle`'s stop tiers and `computeFinalTitle` still spell that icon without the space, so the same marker renders two ways — one rule, two homes — until both read `markerLabel`.
- **Verification:** `apk-build.bat` SUCCESS, and the full `gradlew.bat testDebugUnitTest` reports 1099 tests with two failures in `MapPulseDotTest` — pre-existing, reading `colors.properties`, which this change never touches.
- **Left open:** the §5 decisions above, and the Tracks epic requirements are not yet updated.
