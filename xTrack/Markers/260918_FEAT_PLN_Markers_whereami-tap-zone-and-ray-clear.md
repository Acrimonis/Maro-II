<!-- scope: feature -->
# WhereAmI — thumb-sized tap zone and debug-ray clearing

> **Created:** 2026-09-18 | **Feature:** Markers | **Branch:** `feature/where-are-zone-trans` | **Status:** Shipped — items 1 to 4, C1 to C4 and D3 landed 2026-09-18 through the `#implement` pipeline, item 1 with the pointer in `FEAT_DSC_Markers.md`; only the device pass (§5) stays open, and the user owns it.

## 1. Verified findings

| # | Finding | Evidence |
|---|---------|----------|
| F1 | The touch zone is the zoom-scaled marker floored at 48 dp — `touchSizeDp = if (finalSizeDp < 48.dp) 48.dp else finalSizeDp` — so it grows with the boat image and is not a thumb-sized zone. | `MapOverlays.kt:154-161` |
| F2 | The clickable box is centred on the bow and is only as tall as the sprite: the image is offset down by `finalSizeDp / 2` inside a box of that same height, so the hull's lower half falls outside the tappable area at every zoom above the 48 dp floor. Derived by arithmetic over the read code, not observed on device. | `MapOverlays.kt:154-171` |
| F3 | Closing clears nothing: `closeDrawer()` resets the drawer, the wizard step and the three selection fields only, and `_debugSegments` is written solely by the query. | `MarkersViewModel.kt:504-511`, `:1041` |
| F4 | The render effect removes the `wia_debug_*` polylines only by re-running with an empty list. | `MapMarkerEffects.kt:119-141` |
| F5 | The off switch does not clear them either — it swaps in `NoOpWhereAmIDebugger` and leaves the published list intact. | `MapScreenSettingsOverlay.kt:1236-1238` |
| F6 | A query finishing after the close re-publishes the segments and re-opens the dashboard, since closing cancels nothing. | `MarkersViewModel.kt:1035-1043` |
| F7 | The list does scroll when it overflows: `scrollable = true` reaches the non-wrap branch's `verticalScroll` column. | `MarkerDrawer.kt:326`, `DrawerScaffold.kt:268-289` |
| F8 | The debugger is process-global, set from three places: the matcher's own default, `MapMarkerEffects` at composition and the settings toggle. | `MarkerMatcher.kt:53`, `MapMarkerEffects.kt:112`, `MapScreenSettingsOverlay.kt:1238` |
| F9 | The matcher reads the rays flag four times to decide whether to collect, re-deciding what its caller already knows. | `MarkerMatcher.kt:93`, `:94`, `:138`, `:144`, `AppConfig.kt:302` |

## 2. Items

1. **A round tap zone on the boat (ruled by the user, 2026-09-18 — replaces the former S1/S2 choice)** — replace the touch box in `CenterMarkerOverlay` with a fixed-diameter circle centred on the sprite's visual centre at any zoom, hit-tested radially rather than as a rectangle, so the zone stops growing with the boat and a press on the boat's middle always lands. The sprite itself does not move: its `finalSizeDp / 2` offset is what pins the bow to the GPS point (`MapOverlays.kt:163`), so the zone's centre follows the sprite while its own size does not, and the tap still calls `onWhereAmI` under the inspect-armed guard, now also reaching assistive tech as the same click action.
2. **The tap's visual feedback (values ruled by the user 2026-09-18, after three device reports)** — an accepted tap pulses a disc of gold, `map.marker.tap.flash.color` at opaque `#FFD700`, at the transparency its own key carries, `map.marker.tap.flash.alpha` at 0.50, the transparency beating once: in to that ceiling, then out to nothing, over `map.marker.tap.flashDurationMs` with the peak at `map.marker.tap.flashPeakRatio`, so a peak can never outlast its duration. All six values are `maro.properties` keys read through `AppConfig` — the user's ruling that this visual effect is a functional setting, which moved colour and transparency out of the palette and withdrew the palette doc's claim on them — with the disc's diameter at `map.marker.tap.flashDiameterDp` twice the zone's `map.marker.tap.zoneDiameterDp`, so neither is ever taken from the sprite and both read the same at every zoom. It is drawn **beneath** the sprite by the user's ruling: the hull stays crisp, the pulse showing as a halo around it and narrowing to the sprite's transparent margins where the sprite is wider than the disc. It may draw past its box — `requiredSize` keeps it at its full diameter where the box would otherwise clamp it, the box carrying no clip while the touch area stays bounded by the radial test. The drawn alpha is the alpha key's value times the beat, its scale stated in that key's own comment, and the config test asserts colour and alpha against their defaults from the file that ships them. It belongs to the map's own UI state, not the view model, the acceptance test already living in the map's lambda. The figures before it were a hairline ring around the sprite's bounding box, then a 25 % disc hidden under the hull, then a 50 % wash over it.
3. **Rays withdrawn whenever the dashboard goes (rewritten twice, by the Ask hop and its findings hop)** — the close funnel alone proved insufficient, since a map marker tap writes `Viewing` straight through `openEditDrawer()`; a private `setDrawerState()` funnel is now the single writer of the drawer state, retiring the open id and clearing the published segments whenever the state leaves `MatchResult`, so no route can leave rays behind and no older run can publish over a newer card. Nothing cancels, and the accepted cost stands: the previous `_matchResult` stays until the boat is tapped again.
4. **Rays withdrawn when the toggle goes off** — the render effect keys on `appSettings.markerDebugRays` as well and draws nothing when off, so the polylines leave on the same path that owns them (F5).
5. **Verification** — `apk-build.bat` BUILD SUCCESSFUL plus the scoped `ui.map` + `config` test run; a VM-level test proved unreachable, `MarkersViewModel` being an `AndroidViewModel` with file-backed IO, so the build, the scoped run and the device checks stand as the evidence.

## 3. Not in scope

- `suppressOverscrollWhenFits = true` for the Where-Am-I body: an overscroll glow appears even when the content fits, the flag defaulting false while `MatchResultContent` passes nothing. Adjacent polish, not requested.

## 4. Target files

- `app/src/main/java/ykws/android/maro/ui/map/MapOverlays.kt` — items 1, 2
- `app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt` — item 3
- `app/src/main/java/ykws/android/maro/ui/map/MapMarkerEffects.kt` — item 4
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — items 1, 2, 3
- `app/src/main/assets/colors.properties` and `app/src/main/java/ykws/android/maro/config/AppConfig.kt` — item 2
- `app/src/test/java/ykws/android/maro/config/MapMarkerTapFlashColorPropertiesTest.kt` — item 2's palette tie, the only test route that held

## 5. Device check owed

Rays on: open the dashboard by tapping the boat, close it, and no ray may remain. Toggle the setting off with rays on screen: they must vanish. Tap the boat's middle and its extremities at low and high zoom: the circle's press must land on the hull, the flash must show once per accepted tap and not when the tap is suppressed, and a pan begun on the boat must be judged for whether the map still moves.

## 6. Review

Self-review, not an independent pass: `#review` resolved this plan in design and the same agent wrote it, so this argues against the work rather than confirming it.

- **R1 — closed by the user's ruling of 2026-09-18.** The S1/S2 choice is gone, replaced by the round zone on the sprite's visual centre; F2's finding still stands as the reason the old box was wrong, and the ruling answers the reach question by putting the whole zone where the hull is drawn rather than by making the zone as big as the sprite.
- **R2 — item 2's funnel is verified, not assumed.** `onMarkerDrawerClose` reaches `closeDrawer()` (`MapScreen.kt:2292`), as do `closeMarkerDashboard()` (`:591`), the auto-close (`:982`) and the map-tap path (`:2642`); the wizard paths that write `_drawerState` directly (`MarkersViewModel.kt:673`, `:744`, `:790`, `:826`) never carry MatchResult, which is why the one hinge is sufficient.
- **R3 — the plan was missing the shared-debugger race (added).** `whereAmISync` (`MarkersViewModel.kt:1013`) and `whereAmI` (`:1034`) clear one global `MarkerMatcher.debugger`, and the tap runs both — the async one on `Dispatchers.Default` — so the published rays can belong to the sync run and the list is mutated off-thread. Item 3 does not touch this; a per-run debugger instance is the remedy, and whether to take it is the user's call.
- **R4 — item 3's cost is now recorded.** A cancelled query leaves the stale `_matchResult` in place, named in item 3 rather than discovered later.
- **R5 — items 2 and 4 do not fight.** Close empties the list and the effect then draws nothing from empty, so the two clear paths agree on the same result.
- **Sweep — the five covered classes.** No dependency was added, no machine-shaped data file was opened and no device was touched; every code claim cites a file read, with F2 named as arithmetic over that read rather than an observation. The single write was this plan file, under the user's plan-and-report order.

## 7. Consolidation — code health

The pair is one engine behind two thin bodies, so the consolidation removes the repeated body and the shared mutable debugger, not the two entry points: the recorder calls the bridge inside non-suspend lambdas (`TrackRecorder.kt:1437`, `:1552`) and keeps its synchronous door.

- **C1 — the debugger becomes a parameter.** `MarkerMatcher.debugger` and its three writers go; `resolveAllMarkers`, `resolveMatch` and the sample loop take a `WhereAmIDebugger` per call, so two concurrent resolutions can no longer overwrite each other's capture. This is R3's root fix, and it makes F9's four flag reads redundant, a no-op collector costing nothing to call.
- **C2 — one private body.** `MarkersViewModel` gains `private fun resolveAt(boatPos: LatLng): Resolution`, holding the coastline-index guard, the marker snapshot, the empty case, the per-run debugger choice and the resolver call, and returning the result with its segments; `whereAmISync` returns it and `whereAmI` publishes it, so the guards and the clear exist once.
- **C3 — one query per tap.** The tap's second, synchronous resolution ([`MapScreen.kt:1865`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1865)) goes, and the single run's result feeds the recording as well as the dashboard. Decision **D4**: whether that snapshot arrives from a launched continuation, a frame later than the click, or from a completion callback the view model invokes.
- **C4 — the debugger interface shrinks to what is used.** Under per-run instances `beginCapture`, `endCapture(keep)` — whose `keep` is already ignored — and `clear()` are dead, leaving `onSegmentTested` plus a read of the collected segments, with the no-op kept for the disabled path.
- **D3 settled — retired (2026-09-18).** The hook went whole: the `AppConfig` property, its loader block, the assignment in the settings toggle, the empty effect that synced it, and the `marker.debug.rays.enabled` key with its comments, leaving `AppSettings.markerDebugRays` as the single carrier. It had already been overridden by that setting at composition, so nothing a user could reach changed.
- Files: `MarkerMatcher.kt` (C1, C4) · `MarkersViewModel.kt` (C2) · `MapScreen.kt` (C3) · `MapScreenSettingsOverlay.kt` and `MapMarkerEffects.kt` (C1's writers) · `AppConfig.kt` and `maro.properties` under D3.
- Ownership crosses features: the matcher and the view model are Markers' own key files, while `MapScreen.kt`, `MapMarkerEffects.kt` and the toggle sit with other features whose key-file lists I have not re-read, so each owner's `## Implemented` pointer lands at its next bake.

## 8. Review — consolidation

- **R7 — C1 leaves a dangling hook (open, D3).** The matcher stops reading the flag, so the properties key and its `AppConfig` var lose their reader; retiring them is the clean end and keeping them is dead surface, so the plan refuses to settle it silently.
- **R8 — C4 is only safe with C1.** Shrinking the interface while the global survives would strand an instance nothing reads; the two items move together or neither moves.
- **R9 — this is not one public function.** The recorder's non-suspend call sites keep the synchronous door, so the claim the plan can carry is one body, one debugger per run and one query per tap.
- **R10 — C3 changes recording timing (open, D4).** Today the snapshot lands inside the click; a launched continuation defers it a frame, and the recorder's own ordering is not something this plan has read.
- **R11 — C3 removes R3's tap-path cause only.** The idle capture and the service bridge still resolve while a user query may be running, which is why C1 is the root fix and C3 a reduction.
- **R12 — cost accepted.** A `VisualWhereAmIDebugger` per query allocates a few hundred small objects with rays on; with rays off the no-op singleton allocates nothing.
- **R13 — verification widens (unverified).** The engine itself changes, so the scoped run must cover whatever tests guard `spatial/`; I have not read the test tree, so that scope is named rather than assumed.

## 9. Challenge — no feature regression

A challenge, so it is self-argument rather than independent confirmation, and it reads every item against one test: can this land without a behaviour anyone relies on changing?

- **CH1 — superseded by the user's ruling.** The challenge argued that a fixed zone must not shrink what is reachable and preferred sprite-aligned bounds; the ruling takes the other route deliberately, a fixed circle on the sprite's centre, accepting that the sprite's extremities fall outside it at high zoom. The part that still binds is the one the challenge found: the sprite must not move, its offset being what keeps the bow on the GPS point (`MapOverlays.kt:163`).
- **CH2 — item 3 collides with C3 (settle first).** Once the tap's snapshot comes from the single run, cancelling that run on close can drop a MANUAL recording marker; either the snapshot is taken before publication or cancellation must not reach a run the recording awaits. The ordering is decided before either is written.
- **CH3 — item 4's condition must be the flag *and* a non-empty list.** A flag change re-runs the effect, so with a stale non-empty list it would redraw exactly the rays it is meant to withdraw.
- **CH4 — C1 changes who pays for capture.** `whereAmISync` serves the recorder and the service on their own threads, so it must pass the no-op collector; otherwise idle and service resolutions allocate segments nobody reads.
- **CH5 — C2 must keep the two empties apart.** A missing coastline index returns without opening the dashboard, an empty marker list opens it empty (`MarkersViewModel.kt:1025`, `:1027`); folding one body over both regresses the other.
- **CH6 — C3 defers the recording snapshot (D4).** Today it lands inside the click; a continuation opens a window a fast stop or exit could lose, and the non-empty guard is all that keeps an empty snapshot from being written.
- **CH7 — C4 removes dead code, verified.** `endCapture` has no caller anywhere in the tree and its `keep` is ignored, so `_capturing` never returns to false and the list only ever grew until a clear; per-run instances retire the whole state rather than merely leaving it unused.
- **CH8 — a bookkeeping regression.** The plan edits `MapScreenSettingsOverlay.kt` and `MapMarkerEffects.kt`, owned outside Markers, so those features need their `## Implemented` pointers at the next bake or the record drifts.
- **No regression in the matching itself.** C1, C2 and C4 are structural, the resolver's output unchanged; the only behavioural changes are item 1's zone and item 3's cancellation, both named above and both carrying a decision.

## 10. Resolution — the note never rides on a cancellable run

CH2 is answered by that sentence, and it reshapes item 3 rather than item 2.

- A close stops the presentation, never the work: the state change clears the published segments and retires the open id, and an in-flight run is left to finish, its completion publishing to the UI only while the id it carries is still current — a publish-guard, which cures the reopen defect the cancellation was meant to cure. The guard reads at publication, not at capture, and it now covers every writer of the drawer state rather than the close alone.
- The note is delivered at completion whatever the drawer did, so a tap followed by an instant close still records where the boat was.
- Rapid taps stop cancelling each other: each run carries a monotonically increasing id and only the newest publishes, while every run still delivers its own note, so two quick taps record two notes exactly as the two calls do today (`MapScreen.kt:1863-1868`).
- The exemption is bounded by the recording itself: `TrackRecorder.addManualBoatMarker` returns immediately on a null current track (`TrackRecorder.kt:256`), so a run owes a note only while a recording is live, and outside that window the superseded-run cancellation stays as blunt as it is now.
- The tap already knows which case it is in, since `trackRecorderState` is in the screen's scope at the `onWhereAmI` lambda (`MapScreen.kt:1858`, `:1878`), so the owed-note flag needs no new plumbing.
- D4 is settled by the same rule: the snapshot comes from the run's completion — the callback form, not a launched continuation that could outlive the tap.

## 11. Review — the resolution

- **R14 — the newest-wins check must read at publication, not at capture.** A late older run comparing a stamp it captured earlier would still win.
- **R15 — the publish-guard needs an owner per open.** A monotonic open id, stamped where the tap opens the dashboard and cleared by `closeDrawer()`, or a late run sees a `MatchResult` state belonging to a newer open and publishes over it.
- **R16 — moot twice over.** The completion callback delivers the note unconditionally and the recorder ignores it without a live track, so the flag was never needed; and the funnel that replaced the close path retires the open id instead, so no derivation of recording state is required at all.
- **R17 — cost accepted, and bounded.** A superseded run owing a note finishes its resolution anyway, which is CPU spent only while recording — the case where the data is the point.
- **R18 — the device check widens.** Tap the boat and close instantly while recording, then read the track: the note must be there, and no ray may remain on the map.
- **CH2 is closed as a question.** What remains of item 3 is smaller than what it replaced: a publish-guard, an open id and a flag the screen already holds.

## 12. Shipped — the behaviour-neutral items

Items 2, 3, C1, C2, C3 and C4 are implemented, `apk-build.bat` BUILD SUCCESSFUL, scoped `ui.map` + `config` + `spatial` run at 258 tests with the five pre-existing `maro.properties`-versus-defaults failures and every `spatial/` test green. Item 1's tap zone and item 8's properties hook stay open, both being the user's calls.

What the implementation corrected in this plan, reported by the Code hop and not re-read here:

- The `whereAmIJob` field was deleted rather than left uncancelled, being write-only once nothing cancels.
- **F9 and C1 are wrong as written.** The no-op collector is not free: the cost was the boundary sampling the old flag gate skipped, so the sink short-circuits it through a `collecting` flag. Calling it unconditionally would have added roughly forty land tests per marker per poll on the recorder's `whereAmISync`.
- **R16 is unnecessary.** The completion callback always delivers the note and `TrackRecorder.addManualBoatMarker` already returns on a null current track, so no recording-state plumbing was added.
- The empty-marker-list open now publishes one dispatch later through the same resolve path; its content and its still-opening behaviour are unchanged, and it stamps the id so a pending older run cannot publish over it.
- `AppConfig.markerDebugRaysEnabled` now has writers and no reader — the dead hook R7 predicted, which sharpens item 8's decision to a retirement rather than a preference.
- No view-model test was added: `MarkersViewModel` is an `AndroidViewModel` with file-backed IO, so item 5's conditional test route does not hold and the build plus the scoped run are the evidence.
- **Item 8 and D3 — the hook is retired (2026-09-18).** `AppConfig.markerDebugRaysEnabled` with its KDoc and loader block, the empty `LaunchedEffect` that synced it, the assignment in the settings overlay, and the `marker.debug.rays.enabled` key are gone; `AppSettings.markerDebugRays` is the single carrier. `apk-build.bat` BUILD SUCCESSFUL and the scoped `ui.map` + `config` run at 184 tests with the same five pre-existing failures.
- **The Ask hop's blocking finding closed by a third Code hop (2026-09-18).** A marker-card swap bypassed the close funnel, so rays survived it and a late run could repaint `MatchResult` over the card: `MarkersViewModel.setDrawerState()` is now the single writer of the drawer state, all eight former direct writers route through it, and any move off `MatchResult` clears the segments and retires the id. The id comparison at publication stays.
- The same hop closed four secondary items: the stale `ProximityMatch` KDoc and its "sea-path distance ≤ range" step, the per-match log on the disabled debug path, the uneven `collecting` guard, and a redundant empty-list branch in `resolveAt`. `apk-build.bat` BUILD SUCCESSFUL; scoped `ui.map` + `config` at 184 tests with the same five reds.
- Plan drift this hop reported and this edit fixes: the Status line above, §2 item 3 and §10's first bullet all described the funnel as `closeDrawer`'s, which the code has outgrown.
- A tap that starts a new run while a card is up still publishes `MatchResult` over it: that run belongs to the tap the user just made, and taking the dashboard slot is what the tap does.
- **Item 1 rewritten to the user's ruling (2026-09-18), and the flash given its own item.** What was written as a choice between two rectangles — the former S1/S2 and the R1 gate they carried — is now the round zone on the sprite's visual centre plus the gold tap flash, so CH1's preference for S2 is retired rather than awaiting an answer. Open numbers: **D5** the circle's diameter (48 dp suggested), **D6** whether anything caps it where the sprite is huge, **D7** the flash's alpha and duration. The walk's item 1 already carries the same wording.

## 13. Review — the rewritten item 1

A second review, on the two new items alone; the earlier sections were reconciled in the same pass, their R1 and CH1 now marked closed and superseded.

- **R19 — the accepted cost is unproven for a radial test.** The plan says a pan begun on the boat is consumed, which was true of `clickable` on a box; a `pointerInput` tap detector that does not consume its down may leave the map panning, and whether an unconsumed event even reaches `MapView` is interop behaviour this plan has not verified. The device check now asks the question instead of assuming the answer.
- **R20 — the zone's centre depends on the visual scale while its size must not.** `finalSizeDp / 2` is recomputed per frame from zoom and distance-to-coast, so the implementer needs the offset and the diameter kept apart in the code; a single scaled size would silently restore the old growing box.
- **R21 — the flash's trigger needs pinning to acceptance.** The plan must mean the tap that actually runs `onWhereAmI`: not a touch while inspect mode is armed, and not a touch that drags into a pan. Written as "an accepted tap" it can be read either way.
- **R22 — the flash's ending is unspecified.** A run closed mid-fade, or a second tap inside the fade, has no stated behaviour; both are harmless at a fifth of a second, and the plan should say so rather than leave it open.
- **Sweep — the five covered classes.** No dependency added, no machine-shaped data file opened, no device touched, every write this session ordered — the plan, the walk, the bake files and `AGENTS.md` — and each claim citing a read or a hop, with the hops' build and test figures attributed rather than adopted.
- **What the review would still change:** nothing in items 1 and 2 beyond R19 to R22; the plan is now self-consistent on item 1, which it was not before this pass.

## 14. Shipped — the tap zone and its flash

Item 1 landed 2026-09-18 through the `#implement` pipeline: `apk-build.bat` BUILD SUCCESSFUL, scoped `ui.map` + `config` at 184 tests with the same five pre-existing `maro.properties`-versus-defaults failures and no new one.

- **The numbers used.** The zone's diameter is a fixed 48 dp and its centre is the sprite's own offset — `finalSizeDp / 2` in water, zero on land — so the diameter and the offset are two separate things and no single value can restore the old growing box (R20). The ring is 2 dp wide, `#40FFD700` — the `#FFD700` shape `MarkerAppearance.COLOR_SELECTED` already carries, at its 25 % share of the file's own ARGB convention — and fades 1 → 0 over 200 ms, restarting on a second accepted tap and finishing through a close (R22).
- **What the code does about R19.** Inside the circle the down is consumed, so the map sees none of that gesture — exactly what the old box's `clickable` did, over 48 dp instead of the whole sprite; outside the circle nothing is consumed, so the gesture falls through to the fan scrim and MapView, the mechanism `LockScrim`'s KDoc relies on. Panning is therefore no worse than before and better around the sprite, while whether an unconsumed touch truly reaches osmdroid stays the device's question, with the rest of §5.
- **Deviations from the plan, named.** `onClick` returns the acceptance (`Boolean`) rather than being a `Unit` callback, the map's lambda being where the acceptance lives; the layout box grew to `finalSizeDp + 48 dp` because it has to contain the circle it hit-tests, the sprite itself unmoved (the bow stays on the map centre at any box height); the 25 % alpha rides in the token's ARGB rather than a code constant; and `clickable`'s ripple plus the node's accessibility click action went with the raw `pointerInput`.
- **Files.** `MapOverlays.kt` (zone, gesture, flash) · `MapScreen.kt` (the lambda's acceptance return) · `AppConfig.kt` (`mapMarkerTapFlashColor` and its loader line) · `colors.properties` (`map.marker.tap.flash.color`). `MapScreen.kt`, `AppConfig.kt` and `colors.properties` sit outside Markers, so their owners' pointers land at the next bake (walk item 11).
- **Left open by the Ask hop, unowned.** No test ties the new token to the shipped `colors.properties`, and the code default is the same gold, so a one-sided typo would be invisible; the `pointerInput` is keyed on `finalSizeDp`, so a mid-gesture recomposition cancels an in-flight tap the user must retry; and the boat marker is no longer an accessible click target, nothing having replaced the semantics `clickable` carried.
