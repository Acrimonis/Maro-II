<!-- scope: feature -->
# Map layer cost — device measurement protocol

**Branch:** `feature/performancE`, cut from `origin/develop` (`9756a47`) on 2026-09-20. **Status:** finalised 2026-09-20 — amended after its first review, carrying all four scoping answers and the confirmed demo mode; **ready to run**. Nothing has been measured, and no code has changed.

**Who runs what:** the agent drives adb — the per-cell counter clear and dump, the logcat capture — while the gestures are the user's, performed inside the timed window between the clear and the read. One device is attached (the Pixel 7 below), so no command carries a `-s` selector unless a second device appears; the numbers come from `dumpsys gfxinfo` and no file is produced until phase 2.

## 1. Symptom under test

Sluggish map **zoom and drag** when layers are on. The user names the **depth layer** the main culprit, the **tracks layer** the second, and reports the two as cumulative rather than one cause.

- Reported by the user on 2026-09-20. No agent has reproduced it, and no numbers exist for it — every figure below is either a constant in the code or an estimate from the June plans.
- Reported colour, 2026-09-20: **pinch is worse than drag, and both are affected**. The zoom path therefore carries cost the pan path does not, and the G1/G2 pair is what separates them.
- Reported colour, 2026-09-20: **the low-depth warning layer is on in the user's normal set**. Their reported setup carries two banded ground-overlay stacks, not one, which is why C2 and C3 outrank C1 in what they explain.
- The June drag-stutter pair puts the cost elsewhere: [`260614_FEAT_PLN_Performance_drag-stutter-analysis.md`](260614_FEAT_PLN_Performance_drag-stutter-analysis.md) cites `SHORE_SAMPLE_INTERVAL_MS = 150L` (≈6.6 Hz), a ray-march of up to 200 steps plus a binary search, and arithmetic reaching ≈1,386 spatial queries/second; [`260614_FEAT_PLN_Performance_drag-stutter-event-chain.md`](260614_FEAT_PLN_Performance_drag-stutter-event-chain.md) counts ≈188 overlays per frame (GroundOverlay, Polygon, Polyline).
- Those figures are reasoned from source, never measured, and no entry in [`FEAT_DSC_Performance.md`](FEAT_DSC_Performance.md)'s `## Implemented` points at either plan — by the fold criterion they both stand in design, so this pass either extends or retires them.
- What the protocol buys: one frame-cost number per layer, so a later fix targets the chain that actually costs and can be shown to have worked.

## 2. What the code says the per-frame work is

This is what the returned numbers are read against. Counts are stated by the constant that owns them, never copied here.

| Drawn element | Where it is built | Per-frame cost driver | Zoom gate |
|---|---|---|---|
| Depth colour raster | `DepthBitmap.build` → `addBandedOverlay` ([`MapOverlayRenderer.kt:304`](../../app/src/main/java/ykws/android/maro/ui/map/MapOverlayRenderer.kt:304)) | `DEPTH_OVERLAY_BANDS` stacked `GroundOverlay` strips ([`MapOverlayRenderer.kt:28`](../../app/src/main/java/ykws/android/maro/ui/map/MapOverlayRenderer.kt:28)), each stretched by osmdroid; osmdroid repaints overlays every pan frame (the note in [`DepthBitmap.kt:8`](../../app/src/main/java/ykws/android/maro/ui/map/DepthBitmap.kt:8)) | `DepthConstants.DEPTH_MAP_MIN_DRAW_ZOOM` |
| Low-depth warning raster | `LowDepthWarningBitmap.build`, same banded path ([`MapOverlayRenderer.kt:364`](../../app/src/main/java/ykws/android/maro/ui/map/MapOverlayRenderer.kt:364)) | a **second** banded stack, so both depth rasters together double the ground-overlay count | `DepthConstants.DEPTH_MAP_MIN_DRAW_ZOOM` |
| Isobaths | the isobath draw in [`MapOverlayRenderer.kt:384`](../../app/src/main/java/ykws/android/maro/ui/map/MapOverlayRenderer.kt:384) | one `Polyline` per contour, every vertex re-projected per frame; the dense shallow contour joins only above `DepthConstants.SHALLOW_ISOBATH_MIN_ZOOM` | `DepthConstants.ISOBATH_MIN_DRAW_ZOOM` |
| Tracks | [`MapTrackSegments.kt`](../../app/src/main/java/ykws/android/maro/ui/map/MapTrackSegments.kt), [`TrackSpeedHeatmap.kt`](../../app/src/main/java/ykws/android/maro/ui/map/TrackSpeedHeatmap.kt), [`MapTrackOverlayEffects.kt`](../../app/src/main/java/ykws/android/maro/ui/map/MapTrackOverlayEffects.kt) | banded polylines per visible track plus direction chevrons, all vertex-projected | — |
| 300 m band, regulated zones | [`MapOverlayRenderer.kt:205`](../../app/src/main/java/ykws/android/maro/ui/map/MapOverlayRenderer.kt:205) | `Polygon` fills and stroked boundaries, all vertex-projected | `ZONE_MIN_ZOOM` ([`:22`](../../app/src/main/java/ykws/android/maro/ui/map/MapOverlayRenderer.kt:22)), `REGULATED_ZONE_MIN_ZOOM` ([`:25`](../../app/src/main/java/ykws/android/maro/ui/map/MapOverlayRenderer.kt:25)) |

- The depth layer's per-frame cost is therefore a **count of ground overlays**, not a query cost — it would scale with the band constant and stay flat in map complexity, which the June plans do not predict.
- Every layer change re-stacks the whole list: `OverlayZOrder.reorder(mv)` immediately followed by `mv.invalidate()` sits at roughly fifteen call sites ([`CoastlineMapView.kt:314`](../../app/src/main/java/ykws/android/maro/ui/map/CoastlineMapView.kt:314), [`MapTrackOverlayEffects.kt:321`](../../app/src/main/java/ykws/android/maro/ui/map/MapTrackOverlayEffects.kt:321), [`MarkerOverlay.kt:492`](../../app/src/main/java/ykws/android/maro/ui/map/MarkerOverlay.kt:492), [`InspectMode.kt:589`](../../app/src/main/java/ykws/android/maro/ui/map/InspectMode.kt:589), [`MapScreen.kt:1092`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1092) among them), so a toggle pays a full list walk plus a full repaint.
- Both depth bitmaps are built **once per grid, off the main thread and then cached** ([`MapDepthRasterEffects.kt:63`](../../app/src/main/java/ykws/android/maro/ui/map/MapDepthRasterEffects.kt:63) and `:79`, both through `RasterCache`), so a warm cache means the raster *build* must not appear in the numbers; a cold run will show it and must be discarded.
- The map's zoom bounds are `MAP_MIN_ZOOM`..`MAP_MAX_ZOOM` ([`CoastlineMapView.kt:56`](../../app/src/main/java/ykws/android/maro/ui/map/CoastlineMapView.kt:56)); the two levels of §3 are chosen by an observable gate inside those bounds, never by a number typed here.

## 3. Fixed conditions — hold every one of these for every run

- Device: the **Pixel 7 (Android 16) on USB**, serial `35111FDH2002V9` — foreground, screen on, portrait, no other app in front. The Wi-Fi entry `192.168.1.81:5555` in [`docs/SETUP.md`](../../docs/SETUP.md) did not answer on 2026-09-20, so the pass runs on the cable.
- Build: whatever is already installed. Record the commit and change nothing during the pass — **phase 1 needs no build**.
- **Mode: demo, confirmed by the user on 2026-09-20, for the whole pass.** Demo mode is not an interchangeable toggle: [`MapServiceEffects.kt`](../../app/src/main/java/ykws/android/maro/ui/map/MapServiceEffects.kt) carries the demo sample feed, and per the June chain's `computeDemoSpeed` a coroutine is launched per map-centre change, so that path's cost sits inside every number below.
- The consequence is written down rather than assumed away: the sheet describes the **demo** experience, which is the one being reported, and a later GPS-mode pass is a second session — never a comparison against these numbers.
- **Display: hold one refresh rate.** Measured 2026-09-20: the panel offers 90 and 60 Hz and was rendering at 90 with rate switching enabled — so the rate is a live variable, not a constant, and a mid-pass switch would move the vsync budget under the numbers silently. Read it with `adb shell dumpsys display | findstr /i refresh`, record it in the header, and mark any cell during which it moved.
- Place: the **Cap d'Antibes – Îles de Lérins** stretch, one centre chosen once and reused for every condition. No local worst spot is reported — the sluggishness reads as general rather than tied to one place, which is itself a reading and is kept as reported colour.
- That stretch carries the full population a layer test wants: regulated zones from the baked `nice-frejus` and `nice-menton` sources, the 300 m band, the coastline and the isobaths.
- **Levels by ground, not by number — the first cue was wrong and is corrected here.** A cue that reads the depth lines cannot work with the layers off, and half the conditions are the layers off. Level A is the **whole test stretch in view at once** — Cap d'Antibes and the Îles de Lérins together; level B is zoomed in until **one part of that stretch fills the screen**. Both are available in every condition, layer state or not, and a typed zoom number was never reproducible without a readout.
- The depth-contour cue survives as a secondary check where the depth layer is on: the fine 2 m contours join only above `DepthConstants.SHALLOW_ISOBATH_MIN_ZOOM`, so a close view carries far more lines than a far one. Whether those lines belong to the depth layer's own switch or are drawn regardless was **not verified** — a screen with every switch off showing them is worth recording.
- Warm-up: one discarded pass over the conditions in use. It fills the raster caches and the tile cache; a cold first run would otherwise charge the raster build to whatever layer happens to be on.
- Tiles: perform every gesture inside ground already visited at that level. A first visit to a level pays tile fetch and decode, which is not the layer's cost.
- Repetitions: three per cell, the first discarded, the remaining two reported. Deltas smaller than the spread between the two kept reps are noise.
- Record once per session: charge state, battery level, and `adb -s 192.168.1.81:5555 shell dumpsys thermalservice` before and after, since thermal drift over a long pass moves every later number.
- **The device is the user's.** These commands are run by the user; no agent touches the device, and a run that has not been reported is a run that has not happened.

## 4. Run sheet

Conditions are layer states, addressed by the toggle that owns them in [`NavigationViewModel.kt`](../../app/src/main/java/ykws/android/maro/ui/map/NavigationViewModel.kt) — `toggleZone300` at `:1192`, `toggleRegulatedZones` at `:1253`, `toggleLowDepthWarning` at `:1268`, `toggleDepthLayer` at `:1276`, `toggleTracks` at `:1284`. Turn everything else off in every condition.

| Id | Condition | Gestures | Levels |
|---|---|---|---|
| C0 | every layer off — the baseline | drag, pinch | A, B |
| C1 | depth colour layer only | drag, pinch | A, B |
| C2 | low-depth warning only | drag, pinch | A |
| C3 | both depth rasters | drag, pinch | A |
| C4 | tracks only | drag, pinch | A, B |
| C5 | depth colour + tracks | drag, pinch | A |
| C6 | the user's normal full set | drag, pinch | A, B |
| C7 | the remainder — coastline, 300 m band, regulated zones, markers, with neither depth nor tracks on | drag, pinch | A, B |

- **G1 — drag:** ten seconds of continuous one-finger panning, back and forth across the same ground.
- **G2 — pinch:** one full in-and-out cycle every two seconds, ten seconds in all, never leaving ground already visited at either level of the cycle.
- Run order: C0 first as the cool baseline, then C6 (the reported setting), then C7, C3, C1, C2, C4 and C5, closing on C0 at level A as the drift control — any movement in that repeat is thermal or cache rather than layers.
- **The reset comes after everything settles.** Toggle the layer, let the redraw land, and only then run `reset`: the toggle itself calls `OverlayZOrder.reorder` plus `invalidate`, so starting the counter during that redraw charges the toggle to the gesture.
- Per run, three commands, in this order:

```cmd
:: gate — two reads three seconds apart; a real gesture adds hundreds, idle adds none
adb shell "dumpsys gfxinfo ykws.android.maro | grep 'Total frames rendered'; sleep 3; dumpsys gfxinfo ykws.android.maro | grep 'Total frames rendered'"
:: with the gesture still running, the measured ten seconds:
adb shell "dumpsys gfxinfo ykws.android.maro reset; sleep 10; dumpsys gfxinfo ykws.android.maro"
```

- **The window is gated on observed motion, not on slack — the user's own correction.** The frame counter *is* the movement signal: idle, the app drew **zero** frames in a four-second calibration, while a dragging gesture adds about sixty a second, so a three-second delta separates the two with room to spare. The two-clear slack pair existed only to guess when the hand had started, and the guess cost the first window; the delta removes the guess.
- If the gate reads idle, nothing is measured and the user is told to keep moving — a gate that fails costs one wasted command, never a wrong number.
- Every dump's own header carries the **process id**, so a restart is visible without a second command: a pid that differs from the previous cell means cold JIT and cold in-memory state, with the on-disk raster caches surviving, and the cell is recorded as measured on a fresh process.
- **Method as being run (a recorded deviation from §3's repetition rule):** one window per cell, **ten measured seconds** of one continuous gesture — dragging, or pinching — with the gesture kept running from the user's word until they are told to stop, so the gate always has something to see. Ten seconds is the user's ceiling on this channel: a sixty-second window was rejected as too long to hold, and the earlier three-repetition rule would have meant eighty-four windows.
- A cell that comes back ambiguous is **repeated, never lengthened**, and the sheet carries one row per window.
- The instruction always precedes the window, in numbered form, and the window waits for the user's word. A window fired before the instruction is a wasted window.

- Paste back the whole block from `Total frames rendered` to `Number Slow issue draw commands`. Those lines carry the janky-frame percentage, the 50th/90th/95th/99th percentile frame times, the missed-vsync count and the three "slow" counters that separate CPU work from the render pipeline.
- Corroboration when a cell looks odd: `adb logcat -c` with the clear, then `adb logcat -d -s MaroMapRefresh:D` with the read, so the toggle and rebuild timestamps arrive beside the frame numbers. The tag is written by the five toggles above and by the marker-zones toggle in [`MapScreen.kt:2415`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:2415); nothing else was checked, so its timestamps say when the overlay stack was rebuilt, not what it cost.

## 5. Sheet to fill

**Header, filled once:** place and centre · mode (GPS or demo) · what level A and level B showed · display refresh rate · build commit · battery and charge state · `thermalservice` before and after.

**Run list — the 28 cells in execution order.** Each cell is one window, covering three repeats of its gesture; drag and pinch never share a cell, so the two remain comparable. Levels A and B are the two ground framings of §3, and the first cell — C0 with drag — is the throwaway warm-up. Set the cell's layers, let the redraw land, then gesture inside the window.

| # | Condition | Gesture | Level |
|---|---|---|---|
| 1 | C0 | drag | A |
| 2 | C0 | pinch | A |
| 3 | C0 | drag | B |
| 4 | C0 | pinch | B |
| 5 | C6 | drag | A |
| 6 | C6 | pinch | A |
| 7 | C6 | drag | B |
| 8 | C6 | pinch | B |
| 9 | C7 | drag | A |
| 10 | C7 | pinch | A |
| 11 | C7 | drag | B |
| 12 | C7 | pinch | B |
| 13 | C3 | drag | A |
| 14 | C3 | pinch | A |
| 15 | C1 | drag | A |
| 16 | C1 | pinch | A |
| 17 | C1 | drag | B |
| 18 | C1 | pinch | B |
| 19 | C2 | drag | A |
| 20 | C2 | pinch | A |
| 21 | C4 | drag | A |
| 22 | C4 | pinch | A |
| 23 | C4 | drag | B |
| 24 | C4 | pinch | B |
| 25 | C5 | drag | A |
| 26 | C5 | pinch | A |
| 27 | C0 | drag | A — drift control |
| 28 | C0 | pinch | A — drift control |

| Run | Cond | Gesture | Level | Rep | Total frames | Janky % | 50th | 90th | 95th | 99th | Missed vsync | Slow UI thread | Slow bitmap uploads | Slow draw commands | Notes |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | | | | | | | | | | | | | | | |

## 6. Phase 2 — only if phase 1 is ambiguous

One trace per extreme (C0 and C6), drag only, at the level where the gap is widest:

```cmd
adb shell atrace -t 10 -b 8000 -o /data/local/tmp/maro_C6_drag gfx view sched freq
adb pull /data/local/tmp/maro_C6_drag perf/
```

- Read it in the Perfetto UI and paste the **top slices by self time for the worst frame**, plus the frame timeline for the ten seconds. A trace is machine-shaped: the agent does not open the file, it reads the pasted summary.
- This is the step that separates Compose recomposition from `onDraw` projection from background query cost, which `gfxinfo` cannot do on its own.
- Whether this build emits Compose slices of its own was **not checked**. If it does not, the trace names osmdroid and the view layer, and the Compose share is read from the gap between the frame slice and its children rather than from a labelled slice.

## 7. How the numbers will be read

| Pattern in the sheet | Reading |
|---|---|
| C0 already janky | Not a layer problem: the always-on pipeline or the tiles carry it, and every layer hypothesis is dead until C0 is clean |
| C1 ≈ C0 | The depth colour raster costs nothing per frame at that level; the reported main culprit is wrong or conditional (cold cache, or the warning layer rather than the colour one) |
| C3 − C1 ≈ C2 − C0 | The second banded stack costs about what the first does — the cost is the ground-overlay count |
| C5 ≈ C1 + C4 − C0 | Additive per-frame cost, so the fix is overlay count and redraw cadence, not the query chain |
| C7 − C0 | What the remainder costs on its own — coastline, 300 m band, regulated zones and markers, with neither depth nor tracks on |
| C6 − C7 | What the depth-plus-tracks pair adds on top of the remainder, at the user's own setting |
| C6 ≫ every single-layer condition | Cumulative as reported; the sheet quantifies how much each layer contributes |
| Level B − level A large | The dense shallow contour and the zoom-gated polygon population, not the raster |
| G2 ≫ G1 | The re-projection path per zoom step; G2 ≈ G1 means per-frame overlay redraw |
| No place reads worse than another | The cost does not follow map complexity between the places the user visits, which favours per-frame fixed work over geometry- or query-bound cost |
| Jank only in the first seconds, then flat | One-shot work — cache miss, tile fetch, bitmap upload — not steady state |

## 8. What would falsify the working hypotheses

- The user's depth-first account is falsified if C1 sits at C0 within noise while C0 is itself clean.
- The June plans' query-cost account is falsified if C4's jank is materially above C0 with no depth layer on, since nothing in the track path runs the shore pipeline.
- The ground-overlay-count account is falsified if C3 costs nothing over C1 — the second banded stack would then be free, which would point at vertex count rather than overlay count.
- None of these can distinguish "slow frame" from "slow touch response": if the sheet shows clean frame times while the gesture still feels sluggish, the cost is input latency, and phase 2's input slices become the first thing to read.

## 9. Risks and limits

- `gfxinfo` measures the whole application window, never a single layer. Every attribution above rests on the conditions differing **only** by the layer, so a stray layer left on invalidates the differential rather than losing precision.
- The matrix stands at **28 cells**, each one window of ten measured seconds after the throwaway warm-up — one window per cell rather than §3's three repetitions, a deviation §4 records with its reason. The cheapest trim for a first sitting is C7 at level A alone, which takes the total to 26 cells.
- A ten-second window carries roughly six or seven hundred frames against the warm-up's 4020, so a marginal cell is decided by a second window, not by trusting a small sample.
- The overlay population is derived from code, never observed on the device. A one-line debug log of the overlay list size after a reorder would pin it, and that needs a build, so it belongs to the phase after this one.
- One device, one mode, one place, one level pair: a fix validated here is validated for this case alone. Thermal throttling mid-pass will make later conditions look worse for reasons that are not the layers.

## 10. Questions settled before the pass

- **Answered 2026-09-20 — pinch is worse than drag, and both are affected.** The reading table's `G2 ≫ G1` row is therefore the expected one, while a pure per-frame redraw hypothesis stays alive beside it.
- **Answered 2026-09-20 — the low-depth warning layer is on in the normal set.** Two banded stacks are on during the reported complaint, so C3 is the depth state to compare against C0.
- **Answered 2026-09-20 — no local worst spot; the test ground is Cap d'Antibes – Îles de Lérins.** One centre serves every run, so a difference between conditions is the layers and not the ground.
- **Answered 2026-09-20 — nothing has been rebuilt recently, so the caches are assumed warm.** Recorded as the user's assumption rather than a measurement; it makes the complaint a steady-state one that the sheet should reproduce, and leaves the warm-up pass a safety net rather than a precondition.
- That assumption is testable for free: if the first repetitions of C0 and C6 are the worst and the later ones settle, a cold cache was in play after all — the discarded first repetition already absorbs it.
- **Answered 2026-09-20 — the pass runs in demo mode**, so the demo path's cost is inside every cell and the header names it.
- Still a header field rather than a decision: whether the session can hold one display refresh rate, since a rate that silently changes mid-pass breaks the comparison the sheet is for.

## 11. Cross-references

| Feature | What it owns that this must not contradict |
|---|---|
| [`UI_Map`](../UI_Map/FEAT_DSC_UI_Map.md) | the render cadence (`mapRefreshFps`), the overlay z-order and the map's paint lengths |
| [`DepthMapping`](../DepthMapping/FEAT_DSC_DepthMapping.md) | the raster pipeline, the grid and the banded-overlay decision |
| [`Tracks`](../Tracks/FEAT_DSC_Tracks.md) | the two render axes, banded strokes and the speed heatmap |
| [`GPS`](../GPS/FEAT_DSC_GPS.md) | the fix cadence and demo mode, which feed the map-centre pipeline |

## 12. Session log — one line per window

- **Warm-up window, 2026-09-20, idle — not a cell:** 529 frames in sixty seconds, 24.76% late, 50th 18 ms, 90th 85 ms, 95th 109 ms, 99th 250 ms, 129 slow UI-thread frames, 110 slow draw-command frames and only 3 slow bitmap uploads. The user did not gesture — the instruction arrived after the window had closed — so this reads as an **idle baseline** rather than C0, and it says the app misses a quarter of its deadlines while nobody is touching it.
- That idle reading is what forced the slack-then-clear window design above. Cell results begin below this line.
- **Cell 1 — C0, drag, position A:** 4020 frames over the sixty measured seconds (≈67 fps), 231 janky (5.75%), legacy 28.76%, 50th 17 ms, 90th 25 ms, 95th 28 ms, 99th 44 ms, 1 missed vsync, 7318 high-input-latency frames, 230 slow UI-thread frames, 218 slow draw-command frames, 0 slow bitmap uploads; GPU 50th 8 ms, 90th 10 ms.
- Reading Cell 1: the GPU has headroom while 2179 of the 4020 frames land at exactly 17 ms — a hair over the 16.7 ms a 60 Hz budget allows — so with nothing switched on the app already spends about one frame's worth of work per frame, split evenly between the UI thread and issuing draw commands.
- A methodological note the idle window forced: a window that renders few frames turns the same absolute lateness into a larger percentage, so percentages are comparable only between cells of the same gesture at the same position — the reason the run order is kept.
- **Adaptation, 2026-09-20:** the measured window drops from sixty seconds to **ten**, on the user's word that seventy seconds of continuous gesture is too long to hold. The slack-then-clear pair stays, so a window costs about twenty-five seconds of gesture and one read.
- Cell 1 was measured under the sixty-second window and is marked as the larger sample; its percentages stand, but its denominator is six times that of the cells that follow.
- **Motion gate, 2026-09-20 (calibration, before Cell 2):** the app drew **zero** frames in a four-second idle sample against roughly sixty a second while dragging in Cell 1 — so the gate is unambiguous, and the two-clear window is replaced by a delta check followed by one clear and ten measured seconds.
- **Cell 2 — C0, pinch, position A:** 293 frames over the ten measured seconds (≈29 fps, less than half the drag cell's rate), 45 janky (15.36%, legacy 20.48%), 50th 14 ms, 90th 101 ms, 95th 117 ms, 99th 150 ms, 41 missed vsync, 492 high-input-latency frames, 45 slow UI-thread frames, 4 slow draw-command frames, 0 slow bitmap uploads; GPU 50th 7 ms, 90th 12 ms.
- Reading Cell 2: the pinch cell is worse than the drag cell on every tail measure while drawing fewer frames, and its lateness sits almost entirely on the UI thread — 45 slow UI-thread frames against 4 slow draw-command frames, the opposite split from Cell 1's even 230/218. That is the first agreement with the user's report that pinching hurts more than dragging, and it points at work on the UI thread rather than at drawing.
- **Process caveat on Cell 2:** its dump names pid 18223 against Cell 1's 7383, so the app was restarted between the two cells — cold JIT and cold in-memory state, the on-disk raster caches surviving. The two cells are therefore not measured on the same process, and a relaunch during the rest of the pass marks the cells either side of it.
- **Restart, 2026-09-20 — Cells 1 and 2 are retired as a false start.** Cell 1 predates the ten-second gated window, and Cell 2 was measured on a different process, so neither is comparable with what follows. The pass restarts at Cell 1 under the motion-gated window, and nothing measured above this line enters the reading table.
- **Cell 1 (restart) — C0, drag, far out:** gate caught motion (75 frames in the gating second); 763 frames over the ten measured seconds (≈76 fps), 88 janky (11.53%), 50th 28 ms, 90th 36 ms, 95th 42 ms, 99th 61 ms, 15 missed vsync, 1334 high-input-latency frames, 88 slow UI-thread frames, 88 slow draw-command frames, 0 slow bitmap uploads; GPU 50th 7 ms, 90th 11 ms, 99th 13 ms. Process pid 18223, unchanged from the retired Cell 2.
- Reading it: the tail is mild and the median is the story — 28 ms a frame is roughly 36 frames a second with nothing switched on, split evenly between the UI thread and issuing draw commands, with the GPU idle. Legacy jank reads 99.87%, so essentially every frame misses the sixty-hertz deadline even at the floor.
- **Spread warning, and it changes the method.** The same nominal condition read 17 ms at the median in the retired Cell 1 and 28 ms here, which puts window-to-window noise on this phone on the same scale as the layer effects being hunted. Every cell therefore takes at least one repeated window, and the reading table is applied to the repeats rather than to a single sample.
- **Cell 1 (restart), repeat window:** gate caught motion (62 frames in the gating second); 672 frames, 54 janky (8.04%), 50th 23 ms, 90th 32 ms, 95th 44 ms, 99th 77 ms, 10 missed vsync, 54 slow UI-thread frames, 53 slow draw-command frames, 0 slow bitmap uploads; GPU 50th 7 ms, 90th 12 ms. Same pid.
- The two windows of Cell 1 agree within five milliseconds at the median and four at the 90th, so the cell's floor stands as **23–28 ms a frame with nothing switched on** — over the 16.7 ms sixty-hertz budget before a single layer is added, and split evenly between the UI thread and issuing draws. The retired 17 ms sample stays retired as the outlier.
- Note for every later reading: a layer earns suspicion only by pushing the median or the tail beyond that band, which is wider than any single-frame difference the sheet can resolve.
- **Cell 2 (restart) — C0, pinch, far out:** the gate tripped at only 21 frames in the gating second, marginal for a pinch; 218 frames over the ten measured seconds (≈22 fps), 204 janky (93.58%), 50th 81 ms, 90th 97 ms, 95th 101 ms, 99th 113 ms, 194 missed vsync, 23 high-input-latency frames, 204 slow UI-thread frames, 110 slow draw-command frames, 0 slow bitmap uploads; GPU 50th 6 ms, 90th 8 ms, 99th 9 ms.
- Reading it, held tentatively: with nothing switched on a pinch runs at about 81 ms a frame — five times the sixty-hertz budget — while the GPU never performed better all pass, so the whole cost sits above the graphics chip, on the UI thread and on issuing draws. That is three times the dragging floor and the first solid agreement with the user's own ranking of the two gestures.
- **Why it is not yet trusted:** a gating second of 21 frames is barely a gesture against the 60–75 a developed one gives, so the window may have caught the pinch while it was still starting up. The cell is repeated with the gate raised to 40 frames a second, and the stricter threshold becomes the standard from here.
- Older evidence it agrees with: the retired pinch cell measured 90th 101 ms and 99th 150 ms at 15.36% janky, so the tail is reproducible even though its median was not.
- **Cell 2 (restart), repeat window at the stricter gate (42 frames in the gating second):** 171 frames, 160 janky (93.57%), 50th 85 ms, 90th 97 ms, 95th 101 ms, 99th 109 ms, 160 slow UI-thread frames, 154 slow draw-command frames, 0 slow bitmap uploads; GPU 50th 7 ms, 90th 8 ms, 99th 10 ms. The gating dump before it read the same way — 90.41% janky, median 81 ms.
- **Cell 2 verdict — confirmed, not a half-started gesture.** Pinching with nothing switched on runs at **81–85 ms a frame**, five sixty-hertz budgets, GPU idle, both the UI thread and draw issuance saturated. The two floors now stand as the reference: **23–28 ms dragging, 81–85 ms pinching**, and each layer cell is read against its own gesture's floor.
- First inference, held for testing rather than belief: the pinch penalty is **layer-independent**, since the baseline carries no layer at all — so a layer fix and the zoom path's own cost must be judged separately.
- **Run-order deviation, recorded:** the pass takes the depth cells at the far distance before measuring the close-in baseline, so the user's own question is answered earliest; the close-in cells follow afterwards.
- The gate threshold is raised to 40 frames a second for every later window, so a half-developed gesture no longer opens a cell.
