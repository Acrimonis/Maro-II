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
- **Length:** when the whole name exceeds the cap, each token is trimmed in proportion to its own length until the result fits, and an ellipsis marks every token that was cut (settled 2026-10-10). The cap must satisfy both the list card and the hundred-character file name built in [`trackFileBaseName`](../../app/src/main/java/ykws/android/maro/ui/map/TrackSharing.kt:33); the sanitised file name will now carry the accented connector in French, since [`sanitizeFileName`](../../app/src/main/java/ykws/android/maro/ui/map/TrackSharing.kt:14) does not strip it.
- **Icons.** The live title prefixes the matched marker's emoji today (`🤿Cap d'Antibes`); whether each name in the list keeps it is open (§5).

- Examples: `Cap d'Antibes, 🤿Le Village` for a loop, `Cap d'Antibes, 🤿Le Village to Port de La Salis` for a distinct marked destination, and `Port de La Salis` alone when the destination is the only marker.

## 4. The comment fallback

When the title has **no marker at all**, the list of zones traversed is written into the track **comment** instead — the existing `recomputeDescription` lines, so the journey is described even when the name cannot be. With no marker there is also no name: the timestamp from [`trackAutoName`](../../app/src/main/java/ykws/android/maro/data/track/Track.kt:86) stands, as today (settled 2026-10-10). Whether the traversed-zone list is appended only in that case or always is still open (§5).

## 5. Open decisions

- **The source end** — excluded from the body like the destination, or allowed to rank in it.
- **The icon prefix** on each name in the list, and on the destination token.
- **The comment fallback's exact shape** — conditional on a marker-free title, or always appended.
- **Saved routes** — the from-to form is settled (§9); whether the `Route ` prefix survives, whether the connector localises, and how a bare `Current position` fallback is avoided are still open.
- **The geofence as the same-place test** — its meaning when the geofence is disabled, or when the trip is nowhere near Port Salis.
- **Replace or sit beside the old tiers** — whether the new name replaces `computeFinalTitle`'s diving-manual-idle ladder or runs next to it.
- **One cap, not two** — `topZoneNames` already takes its own top two, so one place should own that number.

## 6. Where it lands

- `data/track/` — a new pure builder for the timed score and the name, called from both the live title poll and the finalize hook, the latter repeating it over the whole raw set before simplification.
- `data/track/WhereAmIProvider.kt` — a sibling `MarkerSetProvider` in the same shape: a nullable function the map layer sets once and the recording service reads, rather than a new constructor dependency threaded through the service. The builder still takes the marker list as a parameter, so the provider is production wiring only and the tests pass a list straight in.
- `spatial/MarkerMatcher.kt` — read-only; the ordering's one home.
- `data/track/Track.kt` — `trackAutoName` stays the fallback.
- `ui/map/TrackSharing.kt` — the file-name length cap and the trimming rule.
- Rejected: extending `sortScore` itself, since it is a stateless per-position comparator the live map calls on every fix; and a separate lean containment pass, since the numbers make it unnecessary.

## 7. Tests

Timed ranking order; a shared loop endpoint dropped; the destination appended regardless of dwell; the destination alone as an empty body; the two-marker cap; proportional trimming with its ellipsis; the no-marker fallback into the comment; a route end named by its marker zone and by its dropdown fallback; the `sortScore` reuse; ties.

## 8. Verification

`apk-build.bat` SUCCESS, and the full `gradlew.bat testDebugUnitTest` green — a naming change reaches shared helpers, so a scoped run would not be evidence.

## 9. Route naming (the router's own line)

A route saved as a track is named `[from] to [destination]` rather than the dated `Route <instant>` of [`routeTrackName`](../../app/src/main/java/ykws/android/maro/data/track/TrackFromCourse.kt:169) (settled 2026-10-10).

- **Each end is named by the marker zone containing it**, the same marker-zone source as a recorded trip.
- **Failing that, the end falls back to its source/destination dropdown content** ([`routeEndOptions`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:5144)): a flagged end's own marker name, or the fixed `Current position` / `Marker position` label ([`route_end_current`](../../app/src/main/res/values/strings.xml:614), [`route_end_position`](../../app/src/main/res/values/strings.xml:615)).
- **Named consequence:** dropping the instant means two routes over the same pair carry the same name, where R40's timestamp currently keeps them apart.
- **Open:** whether the `Route ` prefix survives anywhere; whether the connector localises to ` à ` here as it does for a recorded trip; whether the proportional trimming applies; and how a bare `Current position to Marker position` is avoided when nothing identifies either end.

## Implemented

[Appended once at completion: what actually shipped, and deviations from this plan.]
