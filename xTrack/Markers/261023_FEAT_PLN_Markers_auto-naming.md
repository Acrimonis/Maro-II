# Plan — one auto-name for a marker: the place it names, else the track's own stamp

**Feature:** Markers · **Branch:** `feature/marker-color` · **Created:** 2026-10-23
**Status:** shipped on `feature/marker-color` 2026-10-23 — reviewed twice in design and once by the `#implement` review hop, whose one blocker (edit-after-create re-naming a stored marker) is fixed; `MarkerAutoNameTest` and the 🕐 alignment stay owed.

## 1. Scope

One function names a marker, whatever door created it: the wizard's prefilled title, the save-time
fallback and the map preview all read it, and the two hardcoded literals standing in for it
(`"Marker"`, `"New Marker"`) leave the code.

The rule, in the user's words: *do a where-am-i; if the marker is not in the middle of nowhere, use
that name; otherwise normalise on the track auto-name.*

Out of scope: renaming existing markers, numbering duplicates, and the `TrackRecorder`'s own
first-entry naming (§10, finding).

## 2. The rule and its single home

```kotlin
fun markerAutoName(
    createdAtEpochMs: Long,
    glyph: String,
    geometry: MarkerGeometry?,
    matchesAt: (LatLng) -> List<WhereAmIMatch>
): String
```

- The probes are the **marker's own geometry** — never the vessel's, never the wizard's `mapCenter` — and a null geometry falls straight through to the stamp.
- Pin → its position; circle → its centre; a corridor's midpoint is the same lat/lon average [`MarkerOverlay`](../../app/src/main/java/ykws/android/maro/ui/map/MarkerOverlay.kt:418) already computes.
- **Corridor, two tiers:** if the midpoint yields a naming match, that is the answer outright — p1 and p2 are never consulted; only when the midpoint names nothing are the two endpoints compared against each other.
- That comparison is the app's own: `topNamingMatch` keys on smallest zone, inside before proximity, then `sortScore` (lower better — a `ZoneMatch` scores 0.0–2.0 against a `LineOfSightMatch`'s 1.0–3.0). No new ranking rule, and no three-way pool: the body outranks a tip, and the tips are only ever weighed one against the other.
- The name comes from [`MarkerMatcher.topNamingMatch`](../../app/src/main/java/ykws/android/maro/spatial/MarkerMatcher.kt:554) — the rule the app already settled on (2026-10-18): smallest zone wins, an inside match beats a proximity match on equal size, auto markers never name a place. Its answer is trimmed and dropped when blank.
- "Middle of nowhere" is exactly "no match, or a match whose marker carries no name" — one branch, no second notion of remoteness.
- The fallback is [`trackAutoName(createdAtEpochMs)`](../../app/src/main/java/ykws/android/maro/data/track/Track.kt:86) called as-is, `yyyy-MM-dd HH:mm` US locale. No second formatter, no second pattern.
- **The type glyph leads the name** (settled 2026-10-23): whatever resolved the name — stamp or place — the stored string starts with the glyph the caller passed, `📍 2026-10-23 15:56` or `🎯 Le Village Englouti`. The glyph is an argument, not something the function looks up: the data layer stays free of the UI's `MarkerType`, the wizard passes [`typeIcon(form.type)`](../../app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt:755) and the 🕐 service would pass its own clock.
- **Home:** `app/src/main/java/ykws/android/maro/data/markers/MarkerAutoName.kt`, beside [`AutoMarkerManager`](../../app/src/main/java/ykws/android/maro/data/markers/AutoMarkerManager.kt:22), the data layer that owns marker naming. It gives `data.markers` its first edge onto `spatial`, one-way — `spatial` imports `data.model.markers` and nothing in `data.markers`.
- The function is pure: it takes as many matches as the caller can supply, so no coastline index, no coroutine, no Android type is in its signature.

## 3. Call sites

| Site | Today | After |
|---|---|---|
| [`MarkersViewModel.startWizard`](../../app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt:771) | `"(dd MMM yy) 📍 <colour name>"` — states a colour the picker then falsifies | `markerAutoName(now, typeIcon(form.type), formGeometry(form), ::matchesAt)`: it opens as `📍 2026-10-23 15:56`, a pin or circle opened from a map point is named by its place at once, a corridor (no p2 yet) keeps the stamp until a geometry step closes |
| Step exit | nothing; `position = mapCenter` is written on every camera move ([`MapScreen.kt`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:3052)) | `wizardNext()` recomputes when Position or PositionP2 is closed, TypeSelect's own exit recomputes so the glyph follows a type change, and `wizardFinish()` recomputes when pressed from any of them — **never** on that per-move write |
| [`saveMarker`](../../app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt:957) | `form.name.ifBlank { "Marker" }` | recompute first while `nameIsAuto`, then `ifBlank { markerAutoName(form.createdAtEpochMs, typeIcon(form.type), formGeometry(form), ::matchesAt) }` — the `"Marker"` literal is deleted |
| [`MapScreen` preview](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:2838) | `createForm.name.ifBlank { "New Marker" }` | `createForm.name` — the preview echoes the form, it never invents text |

`matchesAt` in the ViewModel is `{ pos -> whereAmISync(pos).allMatches }`, the existing synchronous
door ([`MarkersViewModel.kt`](../../app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt:1375)); it
returns an empty result while `coastlineIndex` is null, so the stamp half holds until the coastline lands.

## 4. Ownership — whose name is it

`CreateFormState` gains `nameIsAuto: Boolean = false` — deliberately defaulting **false**, so no path can
re-name a stored marker by omission: only the creation branch of `startWizard` sets it true. It also gains
`createdAtEpochMs: Long`, captured there. The Title step's `onValueChange` clears the flag **only when the
typed text differs from the current value** — a `TextInputStep` that emits its initial value on first
composition would otherwise claim every auto-name as user-written.

- The recompute runs at geometry-step exits, and at Finish pressed from one, only while `nameIsAuto` is true — so a settled placement re-names the marker and typing in the title freezes it.
- The editing flow never recomputes: `startWizard(markerId, door)` prefills from the marker and leaves the flag at its false default, because a stored name is the user's by definition.
- A title the user empties stays empty on screen and falls back to the auto name at save: blank is the absence of a name, not a name.
- `createdAtEpochMs` is also what `saveMarker` writes into `UserMarker.createdAtEpochMs`, so the stamp inside the name and the record's own creation time cannot disagree — a zero (an edit-path form) never reaches the create save.
- The glyph rides inside the field, so a type change re-prefixes it while the name is still auto; the same flag keeps a title the user typed untouched, glyph included.

## 5. The neighbouring names

- **🕐 auto marker:** [`createTemp`](../../app/src/main/java/ykws/android/maro/data/markers/AutoMarkerManager.kt:53) writes `EEE, dd MMM yy` and [`confirm`](../../app/src/main/java/ykws/android/maro/data/markers/AutoMarkerManager.kt:78) rewrites it as `yyyy-MM-dd`. The service owns no coastline index, so it can only ever honour the second half of the rule — `markerAutoName(startTimeMs, "\uD83D\uDD50", null) { emptyList() }`, its own clock as the glyph. See §6 for the decision.
- `markerProximityRangeM` is the precedent for a rule living beside the model rather than in a caller; no such function exists for names.

## 6. Open decisions

1. **Align the 🕐 marker or leave it.** Aligning makes every marker name come from one function, at the cost of losing the `EEE, dd MMM yy` header look and making the temp and confirmed names identical in shape. Recommendation: align, since "one function for all" is the point of this plan.
2. **Duplicates accepted.** A marker created inside "Le Village Englouti" is a second row of that name; two markers in one zone are indistinguishable by name. The alternative is a disambiguating suffix, which this repository has no precedent for.
3. **Inherited stamps.** If the naming match is itself a marker named by an earlier stamp, the new marker inherits that stamp. Distinguishing it would need a stored flag; recommendation: accept in v1 and say so in the feature's decisions doc.
4. **Two tiers, not a pool (settled 2026-10-23).** The midpoint wins outright and the endpoints only break the midpoint's silence, so a pin at p2 names the corridor only when the body names nothing — the body is the thing being named, and its tips are a fallback rather than a competitor.
5. **The glyph leads every auto name (settled 2026-10-23).** It precedes a place name as well as a stamp, so the field never changes shape depending on whether the place had a name; the cost is that a named marker's title still begins with a type glyph the zone name did not ask for. The glyph is supplied by each caller, which is what lets the wizard say 📍 while the 🕐 service says 🕐.

## 7. Risks

- **Cost:** `whereAmISync` resolves against every marker with land-blocking for line-of-sight. A corridor costs three probes, all paid at step exits, so a whole wizard spends a handful of calls and none per frame.
- **Staleness:** the name is fixed when the geometry steps are left, so a marker moved afterwards keeps the place's name until the user types one. Accepted, and the same trade the tracks make.
- **Provisional start name:** a geometry built at `startWizard` is superseded when the TypeSelect step changes the type, and a wizard finished straight from TypeSelect keeps the stamp because no geometry exists — both are covered by the step-exit recompute, not hidden by it.
- **Probe semantics:** a corridor parked in open sea takes the better-named of its two ends, and a tip inside a zone names the corridor only because the body named nothing — an order that is deliberate, and the reason the tips are never weighed against the midpoint.
- **The glyph is inside the field:** it shows in every list row and rides into export file names through [`markerFileBaseName`](../../app/src/main/java/ykws/android/maro/ui/map/MarkerSharing.kt:26), exactly as today's default already does — unchanged behaviour, now stated instead of inherited.
- **Renaming a zone** does not rename the markers named after it. Accepted.

## 8. Test plan

`app/src/test/java/ykws/android/maro/data/markers/MarkerAutoNameTest.kt`, built on the fakes already
green in [`MarkerMatcherNamingMatchTest`](../../app/src/test/java/ykws/android/maro/spatial/MarkerMatcherNamingMatchTest.kt:15):
inside a zone → its name; a corridor whose midpoint is inside zone A and whose p2 sits in a smaller
zone B → A, the tip never consulted; a corridor with an unnamed midpoint and a pin in range of p2 → the
pin's name; a corridor with an unnamed midpoint and p1 in zone A, p2 in zone B of different sizes → the
smaller zone; no match on any probe → `trackAutoName`; a blank-named match → the stamp; only an auto
marker → the stamp; null geometry → the stamp; the same `createdAtEpochMs` yields the same stamp across
two recomputes; the result always starts with the glyph the caller passed, for a stamp and for a place
alike, and a type change while the name is auto swaps the glyph the wizard supplies.

The step-exit and Finish behaviour is ViewModel state rather than the pure function: it needs the recompute
extracted as a method taking the form plus a matches lambda, which is then JVM-testable on a `CreateFormState`;
without that extraction it is a UI pass, and the plan prefers the extraction.

## 9. Assessment

Verdict: worth doing, and small — one pure function, four call sites, one boolean, two literals deleted.

- It removes a false statement rather than adding an ornament: the current default names a colour the picker immediately invalidates.
- It reuses a rule the app already wrote, so markers stop being the one feature that ignores "what is this place called".
- The strongest objection: two markers in one zone share a name, and the location half drags the spatial engine into the creation path — a dependency the wizard did not have.
- Residual disagreement, not fixed here: [`TrackRecorder.topLocationName`](../../app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt:1372) still reads raw `firstOrNull`, so a track and a marker can name the same spot differently until that reader moves onto `topNamingMatch`.

## 10. Findings, not requests

- `"Marker"` and `"New Marker"` are user-facing literals in Kotlin, contrary to §1; this plan deletes both rather than translating them.
- [`colorName()`](../../app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt:767) exists only for the default name; once the default stops carrying the colour, the helper loses its caller (the picker reads `MarkerColors.nameResOf` directly).
- [`typeIcon()`](../../app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt:755) stays in the ViewModel as the one glyph lookup; the naming function takes the glyph as a string, which is what keeps the data layer from importing the UI's `MarkerType`.
- The form's default description is another inline format, `EEE, dd MMM yy 'at' HH:mm` ([`MarkersViewModel.kt`](../../app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt:781)); this plan leaves it alone rather than half-normalising the pair.
- A corridor's midpoint is spelled inline at [`MarkerOverlay.kt`](../../app/src/main/java/ykws/android/maro/ui/map/MarkerOverlay.kt:418) and would be spelled again in the probes; `MarkerGeometry.midpoint()` is its one home waiting to be written.
- The wizard's geometry is built three times — [`saveMarker`](../../app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt:939), [`updateMarker`](../../app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt:1015) and the preview at [`MapScreen.kt`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:2824) — so naming, saving and the preview should read one `formGeometry(form)`.
- The 🕐 marker's two formats are already an inconsistency independent of this plan.
