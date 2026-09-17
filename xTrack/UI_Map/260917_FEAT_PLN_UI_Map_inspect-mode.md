<!-- scope: feature -->
# Inspect Mode — drag-to-select on the map

> Host feature: **UI_Map** (map interaction + map chrome). The card, its Prev/Next walk and the close
> rules stay owned by **Ui_General** / **Markers**; this plan adds a new writer of the selection and a
> new `DrawerSource`.
> Decisions arbitrated on 2026-09-17, including a challenge pass and an eight-point review walk. No
> design item remains open; §8 carries the checks still owed.

## Problem

- On the water the screen is small, the boat is moving, and the visible map carries several tracks and markers, so "what is this line near me?" cannot be answered by tapping: a track stroke is a few pixels wide and a marker's tap zone a few metres, and a finger overshoots both.
- An item can today only be opened from the two lists, the menu's chevron, a tap on a marker's proximity zone, or the where-am-i query — a track has no map-tap route at all, so the question is unanswerable from the map itself.
- Inspect mode answers it: drag the map until the target is under the marker, see the range being searched, and open the item without leaving the map or moving the camera.

## 1. Behaviour

- **Toggle** — a square in the map's top-left status row, between the 🌊/🏔️ and 📵 toggles, so the row reads GPS, record, earth/water, inspect, lock (+ the conditional recenter). Glyph ⊕ (`U+2295`, hard-coded like the row's other glyphs), active face when armed, off by default, session-lived (`rememberSaveable`), never persisted.
- **It is disabled while nothing is inspectable** — both filters empty, or both layers off — so a mode that could never pick anything cannot be armed. The gate applies only while disarmed: an armed mode always stays tappable, or a filter change could trap the user in a mode they cannot switch off.
- ⊕ is also the wizard's crosshair glyph; harmless because the wizard disarms the mode, but it will read as "place a marker" to a wizard user.
- **Ring** — while armed, a circle of the pick radius is drawn around the centre marker, so the mode declares its reach before any touch.
- **Sweep** — dragging or pinching re-ranks the inspectable items by distance from the marker point; the nearest one inside the radius wears the gold outline, and the outline clears when nothing is in range.
- **Pick** — the pick is the *opening* of the card, not the highlight: nothing extra is computed while sweeping, and the ladder's origin is snapshotted at that moment (§5).
- **Trigger** — once the map has been quiet for the dwell with a candidate under the anchor, that item is selected and its card opens **without moving the camera**. A gesture yields at most one pick, and a pick made while another card is open replaces it rather than stacking.
- **Navigation is free** — with a card open the map can still be dragged and pinched, and movement never closes the card: its life is tied to Back (gesture or button) and to a change of the referential its walk reads, not to the gesture.
- **Ladder** — with a card open, Prev/Next walks the frozen distance ladder over the inspectable set, index-based so Next then Prev returns to the same item; because the ladder mixes both types, a step landing on the other type swaps the card's surface (§5).
- **Taps** — a tap that lands on a marker or track while armed opens it through the same inspect opener and ladder, so the walk never silently switches to the map world.
- **Panels** — the menu, settings, the two lists and the layer fan leave the mode armed and leave an open card open; the marker wizard disarms the mode (it owns the map centre and puts the crosshair where the ring was drawn).
- **Exit** — Back on an inspect-opened card closes it and disarms; disarming stops the sweep, leaves the selection open as an ordinary one, and puts the map back per §6.
- **Configuration changes are a non-event** — the manifest declares `configChanges`, so rotation never recreates the activity and the mode, its card and its ladder all survive.

## 2. Anchor, radius and ring

- **Anchor** = the geo point under the centre marker, i.e. the map centre including `setMapCenterOffset`. Verify in code that `mapView.mapCenter` already carries the offset; the fallback is `projection.fromPixels(centreX, centreY + offsetPx)`. The anchor is never the finger.
- **The sweep reads the raw centre, never `uiMapCenter`**, which is sampled to ~3 Hz for Compose and would lag a fast drag by up to a third of a second.
- **One derived radius, two consumers** (the drawn ring and the pick gate):
  `radiusDp = clamp(inspectRadiusFactor × BOAT_BASE_DP × 2^(ZOOM_EXPONENT × (zoom − REF_ZOOM)) × distMultiplier, min = inspectRadiusMinDp, max = inspectRadiusMaxViewportPct × min(viewportW, viewportH))`
- Always the boat formula, never the dot branch, so land and water cannot flip the range. The floor keeps the ring finger-sized where the icon is tiny (~9 dp at z8) and the ceiling stops it exceeding the screen where the icon is large (~208 dp at z18).
- `distMultiplier` **is** included — tightening near the coast is wanted precision — and degrades to `1.0` when `distanceToShore` is null. Because that value follows the centre and is itself sampled, the ring breathes as the map is panned and the gate moves with it: accepted behaviour, named here so it is not read as a bug later.
- **Metres conversion** — `radiusMeters = pxPerDp × radiusDp × groundResolutionMetersPerPx(anchorLat, zoom)`, in one pure function beside the ranking, so ring and gate cannot drift.
- **Render** — a Compose `Canvas` circle in the layer-0 cluster (the `Alignment.Center` + `centerOffsetYDp` the centre marker already uses), drawn *behind* the boat image, styled from the existing map surface/accent tokens rather than a new hard-coded colour.

## 3. Candidates, metric and ranking

- **One source for the sweep and the ladder: the map-filtered items whose layer is showing.** The render cap is not a restriction — a filtered track beyond it is a legitimate pick and the mode's own overlay draws it — while a hidden marker layer or hidden tracks contribute nothing, so the mode can never resurrect what the user switched off. The live recording is the only other exclusion, having no card behind it.
- **Distance is measured exactly, from points loaded once.** Arming warms those tracks into the in-memory detail cache, off the UI thread, so a drag frame never reads the file and no second geometry representation exists; at 20–30 tracks of a couple of thousand stored points each, that warm-up is a few hundred milliseconds once and the resident set stays a few megabytes.
- **A simplified copy would not earn its keep at this scale**: thirty tracks at ~2k points is ~60k segment tests per sweep frame, about a third of a millisecond on a worker, so exact and simplified distances cost the same order of magnitude — which is why the plan measures exactly instead of carrying a tolerance and a budget forever.
- **The precision ceiling is upstream.** Stored tracks were already simplified at finalize by the user's own recording setting (ε = 3 m by default, `trackSimplifyEpsilonM`), so inspect is as exact as that data allows and the only way past it is the recording setting, for tracks recorded afterwards.
- **A measured escape hatch, not a first design.** If a device pass ever shows the scan hurting — a filtered set in the hundreds, or tracks in the tens of thousands of points — the fix is one retained simplified copy per track (the app's own Douglas–Peucker, [`TrackSimplifier`](app/src/main/java/ykws/android/maro/data/track/TrackSimplifier.kt:38), at a tolerance under the recording setting with a point budget) shared by the sweep and the ladder.
- **Metric — points and lines only, never areas.** A marker measures to its own point (a pin at its position, a circle at its centre) and a line to its line (a track's polyline, a corridor's centre line), segment-wise and GAP-aware, so one number compares all of them. A corridor's band width is ignored and no shape contributes inside-ness, so being *inside* a circle never yields 0 — that is what stops a zone from owning the ladder merely by containing the boat, and it is a deliberate trade rather than an oversight.
- **Ordering** — ascending distance, tie-break markers before tracks, then id ascending, so it is deterministic and testable.
- **Hysteresis** — the candidate moves only when the new best is closer by `inspectHysteresisPct`, or when the current one has left the radius; it clears outright when nothing is in range, which is what removes the gold.
- **Quiescence** — the sweep re-ranks on every pan and zoom, and the trigger clock resets on every such event rather than only on a candidate change, because a fling moves the map with no touch and would otherwise open a card mid-flight.
- **Two outputs, one metric.** The **sweep** needs only the nearest item, so it is an O(N) minimum scan per frame, off the UI thread and conflated to at most one result per frame; the **ladder** needs the whole order, so it is a full sort once at the pick. Both read the same loaded geometry, so the gold is always the ladder's first entry and the two cannot disagree.
- **Pure class** — `InspectRanking` (candidates + anchor → ranked ids + hysteresis winner) holds the metric, ordering, tie-break and hysteresis, with no Android dependency, and carries the unit tests.

## 4. Painting

- **The mode owns one overlay and paints nothing else.** The candidate is drawn as a single extra line that the mode adds and removes itself, wearing the selection's look from the same style functions the canonical path uses (colour, width, the cased under-stroke), so the track's own drawing is never touched and there is no prior state to restore. That also collapses two cases into one: a candidate beyond the render cap and one already drawn take the same path, and the fork between mutating and adding is gone.
- **Its title must carry a track-band prefix** (`track_hist_` or a new prefix registered in `OverlayZOrder`): `reorder()` buckets by title prefix and anything unrecognised lands in the base band, below every track, breaking the "selection drawn above every other trace" invariant.
- **A rebuild can float other tracks above it**, so a **rebuild generation counter** — bumped when the track effect finishes — re-stacks the overlay after each pass. The counter is the only new state and the re-stack is one list operation, not a repaint.
- The selection's opaque casing hides the candidate's own default stroke underneath, which is the one thing to confirm on device at the widest default width.
- **Markers are not treated this way** — their overlay rebuild is a short list, so the candidate id simply joins their existing inputs.
- **Chevrons are ignored while sweeping** — no `TrackDirectionOverlay` work during the drag; it is the expensive part of a rebuild and is not wanted mid-gesture.
- **On commit the owned overlay is dropped and the canonical rebuild paints the selection** with the same look from the same style functions, so the swap is invisible and the gold has one author once the card is open. The overlay is dropped when the mode is disarmed too.
- **The commit's rebuild must not flash**: the candidate's track is loaded by definition, so build the replacement overlays into a local list and swap them in one non-suspending `removeAll` + `addAll` + `invalidate` block. A suspension between removal and re-add would blank tracks for a frame, so warm geometry comes first and the swapping second.
- A literal double buffer (a second `MapView`, an offscreen overlay set) is not the answer: the artefact is a mid-rebuild suspension, not tearing, and two map views would double tile decoding and memory for a frame the single block already makes indivisible.

## 5. The pick: trigger, ladder, walk, openers

- **One trigger, purely the clock** — a timer reset by every pan and zoom event; when it reaches `inspectDwellMs` with the map quiet and a candidate under the anchor, that candidate is selected and its card opens. A lift opens nothing by itself: it only stops the movement that was resetting the clock, so a resting finger and a lifted one behave identically.
- **One pick per gesture, and no re-fire for the same id** — a gesture may produce at most one pick, which is what stops the fling after a lift from swapping the card with no user action, and the dwell must not re-open the candidate just committed; the last committed id is kept until the candidate changes.
- **The centre marker's tap stops meaning whereAmI while armed** — the mode owns that point, and letting the tap run its query would race the trigger for the same card slot. Taps that land on a map marker keep their route and go through the inspect opener.
- **Release detection** — osmdroid owns the gesture, so the lift signal needs a non-consuming observer (an `OnTouchListener` returning `false`, or the scroll listener plus a `MapEventsReceiver`); confirm the mechanism before wiring it.
- **The ladder is one bounded pass, run at the pick**, over the inspectable candidates and the anchor **snapshotted at that moment**, so the sequence is reproducible and never re-ranks as the boat moves or the map drifts. It keeps only `(id, distance)`, measured through the same warm geometry as the sweep.
- **The card opens on the picked item immediately** and the pass lands almost at once, being a sort over a few dozen warm entries; a Prev/Next button stays dark until the pass knows there is a target in that direction, then lights up, so no press lands on something unknown and the card is never held back by the pass.
- **Quiet openers** — paths beside `openTrackDetail` / `openMarkerDetail` that set the selection and open the drawer with **no camera move, no `whereAmI`, no layer force-show**; the track drawer opens with `mapWasInteracted = true` so closing it never restores a stale camera.
- **The walk is owned by one inspect cursor, not by the drawers.** The drawers each walk their own kind (a marker card steps markers, a track card steps tracks), so a merged ladder needs a cursor above them: `(id, type, distance)` plus an index, held in `MapScreen`, with both cards' Prev/Next routed to it while the card is inspect-opened. The marker side needs `MarkerPrevNext` to take callbacks rather than reading the ViewModel directly (the track side already takes them), and a step landing on the other type closes one card and opens the other through the same quiet openers — R1's one-dashboard-at-a-time rule already makes that transition defined.
- **Two provenance markers tell a drawer which world it is in**: `DrawerSource.INSPECT` for the marker drawer, whose `scopeClosed` answers on the map world (a map-filter change closes the walk, as `MAP` does) and whose `isClampedSource` includes it; and, for the track drawer, an `inspectLadder: List<String>?` (null when opened anywhere else) that decides whether `trackListIds`/`currentTrackIndex` read the ladder or the list world, keeping a list-opened track walking the list even while armed.

## 6. Feed coupling, chrome and configuration

- **Arming** calls `freezeFollow()` (suppress + cancel the resume timer), so `syncCenterFromBoat` no-ops for the whole sweep: the user owns the centre, the marker answers only the map, and the follow effects stand down through their own `autoFollowSuppressed` guard. Demo needs nothing here — no fix drives the centre.
- **Two paths must be gated by the armed flag**, or the freeze is silently reverted: `notifyUserInteraction()` re-arms the resume timer on every pan, and `setDrawerOpen(false)` snaps back with `recenterNow()` whenever follow is suppressed, which would yank the anchor when an inspect card closes.
- **The RecenterButton** keeps its existing rule (`gpsMode && autoFollowSuppressed`) as the manual escape hatch; tapping it hands the centre back to the boat with the mode still armed, and the sweep continues from the new anchor.
- **Exit, GPS mode** — arming captures the follow state (the suppression flag plus whether a resume timer was live) and disarm restores it: a map already panned keeps its position and its delay-return **starts again from that moment** (a full `recenterDelaySeconds`, never the remaining time of the timer the mode cancelled, since a deadline left running underneath the mode would snap the boat back the instant it is disarmed), while a following map recentres in the same frame. Back takes the same path.
- **Exit, demo mode** — arming captures the centre and disarm puts it back, because nothing else can: the demo position is fed *from* the centre, so a swept map has carried the boat across it. Zoom is not restored, mirroring GPS, where a recentre also leaves the user's zoom alone.
- **Chrome follow-through** — the insertion moves the lock square one slot right, so the locked-screen mirror's offset `TOP_TOGGLE_GUTTER + (TOP_TOGGLE_SQUARE + TOP_TOGGLE_GUTTER) * 3` must follow it; better to derive the slot from the row order than repeat the literal.

## 7. Values and keys

| Key (`maro.properties`) | Meaning | Default |
|---|---|---|
| `ui.map.inspect.radius.factor` | Multiplier on the boat icon size | `1.5` |
| `ui.map.inspect.radius.min.dp` | Floor of the ring, so it stays finger-sized when the icon is tiny | `44` |
| `ui.map.inspect.radius.max.viewport.pct` | Ceiling as a fraction of the smaller viewport side | `0.35` |
| `ui.map.inspect.dwell.ms` | Quiet time before a candidate is picked | `400` |
| `ui.map.inspect.hysteresis.pct` | Closeness margin needed to move the candidate | `15` |

Each gets an `AppConfig` accessor. The ring's colour and the ⊕ glyph are not keys: the first reads the palette, the second follows the row's hard-coded glyphs.

## 8. Verification

- **Unit tests** — ranking order and tie-break, hysteresis boundary, clear-on-empty, radius derivation (factor × zoom × shrink), both clamps, dp → metres conversion, ladder ordering against a frozen anchor.
- **Build and chrome** — scoped run for the touched `ui.map` + `config` packages, `apk-build.bat` green; the six-square row fits the narrowest supported portrait width with the conditional recenter present, the locked-screen mirror still lands on the lock square, and ⊕ renders at the shared 22 sp toggle size.
- **Existing callers** — `MarkerPrevNext` gains callbacks for the inspect cursor, so check its other call sites still navigate their own world once the drawer no longer reads the ViewModel directly.
- **Device sweep** — render cap at 20 tracks; a harbour (maximum coast shrink); zoom 8 and 18; the ⊕ square greying out when a filter empties and staying tappable while armed; a candidate beyond the render cap on the owned overlay, asserted to paint above every other trace and to be re-stacked after a rebuild; a rebuild landing mid-sweep; an All-filters map — the far end of the assumed 20–30-track scale and the case that would justify the documented fallback — where the card still opens at once.
- **Device interactions** — dwell-open, a lift followed by a fling that must not swap the card, navigation while a card is open (it must stay), Back closing and disarming, a tap on a marker while armed (inspect ladder, not the map world), entering the marker wizard (disarm), a cross-type step, and Prev/Next past the viewport edge.
- **Device feed checks** — the boat keeps moving while armed and the centre does not follow it; no delay-return fires during a long sweep; closing a card does not recentre; the RecenterButton still escapes mid-sweep; disarm from a following map recentres in the same frame, disarm from a pre-panned map keeps that position with its delay-return restarted, and disarm in demo returns the captured centre with the zoom kept.
- **Two checks owed** — the commit swap's frame time at the 20-track cap (warm versus cold geometry), and the card's slot against the anchor band in portrait and landscape including the offset's upper extreme, which calls for a placement change only if the slot actually covers the anchor.

## Outcome
[Appended once at completion: what actually shipped + deviations from plan.]
