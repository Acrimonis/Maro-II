<!-- scope: feature -->
# Map layer cost — measurement protocol

**Feature:** Performance · **Branch:** `feature/performancE`, cut from `origin/develop` (`9756a47`) · **Status:** shipped 2026-09-20 — §8 and its two follow-ups (the repaint removal and the contour flag gating) are implemented, measured on the device and in the tree; §9 stands unbuilt and waits on numbers · **File committed as of** `39da527`; everything since is uncommitted.

## Purpose

The map feels sluggish when it is dragged or zoomed with layers switched on. The user names the **depth layer** the main culprit, the **tracks layer** second, and reports the two as cumulative. This pass puts a number on what each layer state costs on the phone, so that a later fix targets the chain that actually costs and can be shown to have worked.

Why it exists in this form: the feature already carries two analyses of the same complaint (`260614_FEAT_PLN_Performance_drag-stutter-analysis.md`, `260614_FEAT_PLN_Performance_drag-stutter-event-chain.md`). Both were reasoned from source and never measured, so their figures are estimates — a 150 ms shore sample feeding a 200-step ray-march, ≈188 overlays re-projected per frame. Neither is retired by this pass yet; the pass is what decides. The method therefore inverts the earlier one: measure first on the device, attribute per layer, and only then design a fix.

## Resume here

- **Measured floors, demo mode, Pixel 7:** dragging with nothing switched on **23–28 ms a frame**; pinching **81–85 ms a frame**. The GPU idles at 6–12 ms in both, and the lateness sits on the UI thread and on issuing draw commands.
- **The pinch penalty is layer-independent** — it is present with no layer switched on at all — so the zoom path's own cost is a separate question from the depth question this pass was opened for.
- **Where the pass now stands:** the layer cells were run as an iterative battery instead of the 28-cell grid (§8 holds the readings), the fix in §8 shipped on 2026-09-20, and §9 stands unbuilt as the follow-on. The depth cells (C3, C2) remain unmeasured, and the device pass over the shipped build is what is owed.

## 1. Running one cell

**Before a sitting**

- Device: **Pixel 7, Android 16, on USB** (`35111FDH2002V9`); confirm with `adb devices`. The Wi-Fi address in `docs/SETUP.md` does not answer, so the pass runs on the cable.
- App: **demo mode**, foreground, portrait, one place — Cap d'Antibes and the Îles de Lérins (§2) — with no other app in front.
- Header, recorded once: mode · place · the two levels' cues · display refresh rate (`adb shell dumpsys display | findstr /i refresh`) · build commit · battery and charge state · `dumpsys thermalservice` before and after.

**The cell itself: one command, and it aligns itself to the hand**

```cmd
adb shell "pkg=ykws.android.maro; f() { dumpsys gfxinfo $pkg | grep 'Total frames rendered' | head -1 | tr -dc '0-9'; }; a=$(f); i=0; d=0; while [ $i -lt 60 ]; do sleep 1; b=$(f); d=$((b-a)); if [ $d -gt 40 ]; then break; fi; a=$b; i=$((i+1)); done; echo gate_delta=$d; sleep 1; dumpsys gfxinfo $pkg reset; sleep 10; dumpsys gfxinfo $pkg"
```

- Tell the user the switches and the level **first**, then fire the command; they keep the gesture running until told to stop. The loop polls the frame counter once a second and waits for a delta over **forty** — a developed drag gives sixty to seventy-five, a pinch idles between cycles, an untouched phone gives nothing — then clears the tally and measures **ten seconds**.
- The instruction always precedes the window, and the command is fired only on the user's word; an unannounced window measures an idle phone and is worth nothing.
- Read `gate_delta` (proof the hand was moving) and the block from `Total frames rendered` to `Number Slow issue draw commands`. The dump header also names the process id.
- **Gestures:** drag is one finger sweeping continuously; pinch is two fingers, in and out, one cycle about every two seconds. Ten seconds are measured inside each; the rest of the gesture is discarded.
- Corroboration when a cell looks odd: `adb logcat -c` before and `adb logcat -d -s MaroMapRefresh:D` after, so layer-toggle timestamps arrive beside the numbers. The tag covers the five layer toggles in `NavigationViewModel` and the marker-zones toggle in `MapScreen`, nothing else.

## 2. Fixed conditions

- **Levels by the ground, not by a number** — no zoom readout exists in this build. **Far out** is Cap d'Antibes and the Îles de Lérins in view at once; **close in** is one part of that stretch filling the screen. Set once per cell, reused.
- **Warm-up:** one throwaway window at the start of a sitting (C0, drag, far out), to fill tile and raster caches.
- **Caches and tiles:** every gesture happens on ground already visited; nothing is rebuilt during the pass, the raster caches having been warm for days.
- **Session:** charge state and thermals recorded either side; if the display refresh rate moves mid-pass, the cells it moved in are marked and not compared.
- **Ownership:** the device and the gestures are the user's; adb and the reading are the agent's.

## 3. The conditions

| Id | What is switched on | Levels |
|---|---|---|
| C0 | nothing — the baseline | far out, close in |
| C1 | depth colour layer only | far out, close in |
| C2 | shallow-depth warning only | far out |
| C3 | both depth rasters | far out |
| C4 | tracks only | far out, close in |
| C5 | depth colour + tracks | far out |
| C6 | the user's normal everyday set | far out, close in |
| C7 | everything except depth and tracks — coastline, 300 m band, regulated zones, markers | far out, close in |

- Every condition × level is run twice, once dragging and once pinching: **28 cells**.
- Run order: C0 far out, C6 far out, C7 far out, C3, C1 far out, C2, C4 far out, C5, then C0 far out again as the drift control, then the close-in cells. The depth cells come early on purpose — they answer the question the user actually asked.

| # | Condition | Gesture | Level | | # | Condition | Gesture | Level |
|---|---|---|---|---|---|---|---|---|
| 1 | C0 | drag | far | | 15 | C1 | drag | far |
| 2 | C0 | pinch | far | | 16 | C1 | pinch | far |
| 3 | C6 | drag | far | | 17 | C1 | drag | close |
| 4 | C6 | pinch | far | | 18 | C1 | pinch | close |
| 5 | C7 | drag | far | | 19 | C2 | drag | far |
| 6 | C7 | pinch | far | | 20 | C2 | pinch | far |
| 7 | C3 | drag | far | | 21 | C4 | drag | far |
| 8 | C3 | pinch | far | | 22 | C4 | pinch | far |
| 9 | C1 | drag | far | | 23 | C4 | drag | close |
| 10 | C1 | pinch | far | | 24 | C4 | pinch | close |
| 11 | C7 | drag | close | | 25 | C5 | drag | far |
| 12 | C7 | pinch | close | | 26 | C5 | pinch | far |
| 13 | C6 | drag | close | | 27 | C0 | drag | far — drift control |
| 14 | C6 | pinch | close | | 28 | C0 | pinch | far — drift control |

## 4. Measurement rules

- **Repeat every cell at least once.** Window-to-window spread on this phone reaches 5 ms at the median for the same nominal condition, which is the same scale as the layer effects being hunted; a single sample cannot carry a verdict. An ambiguous cell is **repeated, never lengthened**.
- **A layer earns suspicion only by moving the median or the tail beyond its own gesture's floor band** (23–28 ms dragging, 81–85 ms pinching). Differences inside the band are noise.
- **Percentages compare only within a gesture and a level.** A window that renders few frames turns the same absolute lateness into a larger percentage, which is why the two gestures never share a cell and the run order is kept.
- **A dump's header names the process id.** A change between consecutive cells means the app restarted — cold JIT, cold in-memory state — and both cells are marked accordingly.
- **A failed gate costs one command, never a wrong number.** If `gate_delta` is near zero, nothing was measured and the window is re-run.

## 5. Reading the results

Compare each cell against the floor of its own gesture, and use the three slow counters to say where the cost lives.

| Pattern in the sheet | Reading |
|---|---|
| C0 already far above its floor | Not a layer problem: the always-on pipeline or the tiles carry it |
| C1 ≈ C0 | The depth colour raster costs nothing per frame; the reported main culprit is wrong or conditional |
| C3 − C1 ≈ C2 − C0 | The second banded stack costs about what the first does — the cost is the ground-overlay count |
| C5 ≈ C1 + C4 − C0 | Additive per-frame cost, so the fix is overlay count and redraw cadence, not the query chain |
| C7 − C0 | What the remainder costs — coastline, 300 m band, regulated zones, markers, with neither depth nor tracks on |
| C6 − C7 | What the depth-plus-tracks pair adds on top of the remainder, at the user's own setting |
| C6 ≫ every single-layer condition | Cumulative as reported; the sheet prices each layer's share |
| Close-in − far-out large | The dense shallow contour and the zoom-gated polygon population, not the raster |
| Pinch ≫ drag beyond the floor gap | A cost the zoom path adds on top of the pen's own; the floors already differ three-fold |
| Slow UI thread dominant | Work above the graphics chip, in the Composable or the spatial query |
| Slow draw commands dominant | The overlay stack and the projection path |
| Slow bitmap uploads non-zero | Image work — raster or tiles — which the floors show as negligible |
| Jank only in the first seconds, then flat | One-shot work — cache miss, tile fetch, bitmap upload — not steady state |

**Falsifiers.** The user's depth-first ranking dies if C1 sits inside the floor band while the floor itself is sound. The June query-cost account dies if C4 (tracks only, no shore pipeline running) is materially above C0. The ground-overlay-count account dies if C3 costs nothing over C1. None of these can tell "slow frame" from "slow touch response": if the numbers are clean while the gesture still feels heavy, the cost is input latency.

## 6. Measured so far — demo mode, Pixel 7, 2026-09-20

- **Idle, no gesture (not a cell):** 529 frames in a minute, 24.76% late, 90th 85 ms, 99th 250 ms — the app misses a quarter of its deadlines while nobody is touching it.
- **C0, drag, far out:** 763 frames, 11.53% late, 50th 28 ms, 90th 36 ms, 99th 61 ms; repeat: 672 frames, 8.04% late, 50th 23 ms, 90th 32 ms, 99th 77 ms. GPU 7–12 ms; slow UI-thread ≈ slow draw-command. **Floor: 23–28 ms a frame**, over the 16.7 ms sixty-hertz budget before any layer is added.
- **C0, pinch, far out:** 218 frames, 93.58% late, 50th 81 ms, 90th 97 ms, 99th 113 ms; repeat: 171 frames, 93.57% late, 50th 85 ms, 90th 97 ms, 99th 109 ms. GPU 6–8 ms; slow UI-thread 160 against slow draw-command 154. **Floor: 81–85 ms a frame.**
- **Inference so far:** the pinch penalty survives with no layer switched on, so the zoom path carries a cost of its own; the layer cells are what remain to be priced.

## 7. What the code says the per-frame work is

| Drawn element | Where it is built | Per-frame cost driver | Zoom gate |
|---|---|---|---|
| Depth colour raster | `DepthBitmap.build` → `addBandedOverlay` ([`MapOverlayRenderer.kt:304`](../../app/src/main/java/ykws/android/maro/ui/map/MapOverlayRenderer.kt:304)) | `DEPTH_OVERLAY_BANDS` stacked `GroundOverlay` strips ([`:28`](../../app/src/main/java/ykws/android/maro/ui/map/MapOverlayRenderer.kt:28)), each stretched by osmdroid, which repaints overlays every pan frame (note in [`DepthBitmap.kt:8`](../../app/src/main/java/ykws/android/maro/ui/map/DepthBitmap.kt:8)) | `DepthConstants.DEPTH_MAP_MIN_DRAW_ZOOM` |
| Low-depth warning raster | `LowDepthWarningBitmap.build`, same banded path ([`MapOverlayRenderer.kt:364`](../../app/src/main/java/ykws/android/maro/ui/map/MapOverlayRenderer.kt:364)) | a **second** banded stack, doubling the ground-overlay count | same gate |
| Isobaths | the isobath draw ([`MapOverlayRenderer.kt:384`](../../app/src/main/java/ykws/android/maro/ui/map/MapOverlayRenderer.kt:384)) | one `Polyline` per contour, every vertex re-projected per frame; the fine 2 m contours join only above `DepthConstants.SHALLOW_ISOBATH_MIN_ZOOM` | `DepthConstants.ISOBATH_MIN_DRAW_ZOOM` |
| Tracks | [`MapTrackSegments.kt`](../../app/src/main/java/ykws/android/maro/ui/map/MapTrackSegments.kt), [`TrackSpeedHeatmap.kt`](../../app/src/main/java/ykws/android/maro/ui/map/TrackSpeedHeatmap.kt), [`MapTrackOverlayEffects.kt`](../../app/src/main/java/ykws/android/maro/ui/map/MapTrackOverlayEffects.kt) | banded polylines per visible track plus direction chevrons, all vertex-projected | — |
| 300 m band, regulated zones | [`MapOverlayRenderer.kt:205`](../../app/src/main/java/ykws/android/maro/ui/map/MapOverlayRenderer.kt:205) | `Polygon` fills and stroked boundaries, all vertex-projected | `ZONE_MIN_ZOOM` ([`:22`](../../app/src/main/java/ykws/android/maro/ui/map/MapOverlayRenderer.kt:22)), `REGULATED_ZONE_MIN_ZOOM` ([`:25`](../../app/src/main/java/ykws/android/maro/ui/map/MapOverlayRenderer.kt:25)) |

- Both depth rasters are built once per grid, off the main thread, and cached ([`MapDepthRasterEffects.kt:63`](../../app/src/main/java/ykws/android/maro/ui/map/MapDepthRasterEffects.kt:63) and `:79`), so with warm caches the build never appears in a cell.
- Every layer change re-stacks the whole overlay list: `OverlayZOrder.reorder(mv)` followed by `mv.invalidate()` at roughly fifteen call sites ([`CoastlineMapView.kt:314`](../../app/src/main/java/ykws/android/maro/ui/map/CoastlineMapView.kt:314), [`MapTrackOverlayEffects.kt:321`](../../app/src/main/java/ykws/android/maro/ui/map/MapTrackOverlayEffects.kt:321), [`MarkerOverlay.kt:492`](../../app/src/main/java/ykws/android/maro/ui/map/MarkerOverlay.kt:492), [`InspectMode.kt:589`](../../app/src/main/java/ykws/android/maro/ui/map/InspectMode.kt:589), [`MapScreen.kt:1092`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1092) among them).

## 8. Fix — gate-keyed rebuilds

**Diagnosis.** The cost is the zoom **step**, not the frame: every zoom event reaches the zoom state and five per-layer effects are keyed on the raw level, so each clears, rebuilds and re-stacks its overlays on the main thread. A continuous pinch emits a zoom event per frame, which is why no pinch frame is ever a steady one.

**Readings behind it** — 2026-09-20, Pixel 7, Android 16 on USB, demo mode, 90 Hz active, close-in framing unless stated. Each window is ten seconds.

| Switches on | Input | Frames in 10 s | Median | What it says |
|---|---|---|---|---|
| depth colour | drag, far out (the sitting's first window) | 1067 | 17 ms | the wide-out anchor, one sample |
| nothing | pinch | 322 | 53 ms | the bare pinch floor under today's conditions |
| depth colour | drag | 320 | 69 ms | steady state with the depth raster on |
| everything but depth | drag | 272 | 89 ms | tracks included |
| everything but depth and tracks | drag | 270 | 89 ms | tracks cost nothing measurable |
| all layers | drag | 163 | 150 ms | the user's own setting |
| depth colour | pinch, fast | 14 | 750 ms | |
| depth colour | pinch, slow | 12 | 1250 ms | gesture speed is not the lever |
| all layers | pinch | 8 | 3150 ms | the reported complaint, reproduced |
| depth colour | zoom buttons, ~1 tap/s | 789 | 21 ms | a tail of ~10 frames at 650–800 ms, one per tap |

- **After the fix** (build installed 2026-09-20 14:56 local, fresh pid 25686), the same buttons window read 796 frames at a **16 ms median, 90th 61 ms, 99th 101 ms**, 8.8% janky and 20 missed vsyncs: the 650–800 ms tail is gone, the middle of the distribution is thicker, and the counters moved to 25 slow UI frames against 66 slow draw commands with no slow bitmap uploads. The levers that attack what is left are planned in [`260920_FEAT_PLN_Performance_per-frame-cost-levers.md`](260920_FEAT_PLN_Performance_per-frame-cost-levers.md).
- **The pass then ran to the end of the session.** The buttons window settled at a 12–16 ms median with the tail absent in three windows; the pinch with depth colour alone went from **14 frames at 750 ms to 205 at 53 ms**, and a bare pinch in the same framing also reads 53 ms, so the layer's share became noise. Removing the listener's repaint measured **neutral** and it is gone. What remained was a pause mid-sweep: a fifteen-second wide in-and-out held **seven frames at 650–700 ms**, one per crossing of the fine-mesh floor — visible only in the histogram of a single window, which is why the earlier window averages had hidden it.
- **The contour flag gating, shipped 2026-09-20.** The isobath set was dropped, re-created and re-stacked at each crossing; it is now attached once and switched by each polyline's own `enabled` flag (`drawIsobaths` attaches everything, `applyIsobathGates` writes the two floors). The same sweep went from 145 frames to **410, with nothing above 150 ms in the histogram** — the residual is a broad 60–90 ms band that no layer change has moved.
- **Owed, and each is a device window rather than a design:** the warm repeat of the flag-gated sweep; the eye check that the contours still appear and vanish at the 13 and 15 floors; the all-layers pinch (8 frames at 3150 ms before the fix); a bare close-in drag, the reference every close-in delta lacks; and the split between the colour raster and the contour lines.

**Where the cost lives.** A zoom event reports the level ([`CoastlineMapView.kt:277`](../../app/src/main/java/ykws/android/maro/ui/map/CoastlineMapView.kt:277)) into the zoom state ([`MapScreen.kt:1893`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1893) → [`NavigationViewModel.kt:1388`](../../app/src/main/java/ykws/android/maro/ui/map/NavigationViewModel.kt:1388)), and five effects are keyed on it — the 300 m band ([`:296`](../../app/src/main/java/ykws/android/maro/ui/map/CoastlineMapView.kt:296)), the regulated zones ([`:319`](../../app/src/main/java/ykws/android/maro/ui/map/CoastlineMapView.kt:319)), the depth colour raster ([`:343`](../../app/src/main/java/ykws/android/maro/ui/map/CoastlineMapView.kt:343)), the low-depth warning ([`:360`](../../app/src/main/java/ykws/android/maro/ui/map/CoastlineMapView.kt:360)) and the isobaths ([`:375`](../../app/src/main/java/ykws/android/maro/ui/map/CoastlineMapView.kt:375)). Each clears its own overlays, rebuilds them, calls `OverlayZOrder.reorder(mv)` and invalidates. The zoom is read inside those draws only as a **gate** ([`MapOverlayRenderer.kt:360`](../../app/src/main/java/ykws/android/maro/ui/map/MapOverlayRenderer.kt:360), [`:395`](../../app/src/main/java/ykws/android/maro/ui/map/MapOverlayRenderer.kt:395), [`:222`](../../app/src/main/java/ykws/android/maro/ui/map/MapOverlayRenderer.kt:222)); the widths are dp constants and the raster bitmap is cached per grid ([`MapDepthRasterEffects.kt:63`](../../app/src/main/java/ykws/android/maro/ui/map/MapDepthRasterEffects.kt:63)), so the geometry does not depend on the level at all.

**The change.** Key each effect on its gate instead of the raw level:

| Layer | Effect | Key today | Proposed key |
|---|---|---|---|
| 300 m band | [`CoastlineMapView.kt:296`](../../app/src/main/java/ykws/android/maro/ui/map/CoastlineMapView.kt:296) | `zoomLevel` | `zoomLevel >= ZONE_MIN_ZOOM` |
| Regulated zones | [`:319`](../../app/src/main/java/ykws/android/maro/ui/map/CoastlineMapView.kt:319) | `zoomLevel` | `zoomLevel >= REGULATED_ZONE_MIN_ZOOM` |
| Depth colour raster | [`:343`](../../app/src/main/java/ykws/android/maro/ui/map/CoastlineMapView.kt:343) | `zoomLevel` | `zoomLevel >= DepthConstants.DEPTH_MAP_MIN_DRAW_ZOOM` |
| Low-depth warning | [`:360`](../../app/src/main/java/ykws/android/maro/ui/map/CoastlineMapView.kt:360) | `zoomLevel` | same gate as the raster |
| Isobaths | [`:375`](../../app/src/main/java/ykws/android/maro/ui/map/CoastlineMapView.kt:375) | `zoomLevel` | `zoomLevel >= ISOBATH_MIN_DRAW_ZOOM`, plus `zoomLevel >= SHALLOW_ISOBATH_MIN_ZOOM` for the 2 m net |

The guard inside each effect compares the tracker's `last…Zoom` against the level; it becomes a comparison of the gate in force, with the tracker holding the gate rather than the double.

**Open question that gates the change.** Whether osmdroid re-projects an overlay in its own draw pass after a zoom. If it does, the rebuild was redundant and the gate keys remove it outright; if it does not, a gate-keyed layer keeps its old projection until a crossing, and the remedy is one `mv.invalidate()` on the step — cheap — with the rebuild left to the crossing.

**Verification.** Repeat the zoom-button window (~1 tap/s, depth colour alone): the 650–800 ms tail should disappear while the 21 ms median holds. Then the pinch window should return hundreds of frames instead of 14, at the same two levels.

**Risks and open cells.**
- The gates are coarse (11, 13, 15), so a step inside a band stops rebuilding while each crossing still pays a full rebuild: three crossings per layer per direction, and the pinch crosses them repeatedly.
- The change removes the rebuild, not the per-frame draw — five overlay sets stay in the list and are drawn every frame.
- Still unmeasured: a bare drag at the close-in framing, the level's own floor, and a second far-out sample. The 17 ms anchor and the 53 ms bare pinch are single windows.
- The 23–28 ms drag and 81–85 ms pinch bands in §6 come from an earlier sitting whose refresh rate was never recorded; today's 90 Hz line puts the two sittings' comparability in doubt.

## 9. Next step if the gate keys do not suffice

Viewport-bounded, coalesced tile rebuilds — the shape to reach for only after §8 has been measured:

- **Tiles.** Cut each layer's geometry into tiles keyed `(layer, dataVersion, zoom, tileX, tileY)`, one overlay per tile, attached when the tile enters the viewport and detached when it leaves. The raster half is already cached per grid, so tiling pays for itself on the vector layers first: isobaths, the 300 m band, the regulated zones and the coastline.
- **Queue.** One `MutableStateFlow<TileRequest>` holding the current viewport and level; a `collectLatest` consumer cancels the in-flight build when a newer request arrives and drops every queued request it supersedes — the "stack the calls but discard the stale ones" behaviour, for free from the coroutine machinery.
- **Cancellation.** A tile build must be cooperative (`ensureActive` inside the projection loop) or a cancel does not stop the CPU; a tile close to completion is better finished and cached than thrown away.
- **Threading.** Build on `Dispatchers.Default`; touch the osmdroid overlay list only on the main thread.
- **Cache.** Tile keys cover a re-zoom to a visited level as a hit, which is what turns a pinch from "rebuild per step" into "hit per step".
- **Costs to weigh.** Many more overlay objects are drawn each frame; a spatial query runs per step; tiles churn while panning. Each of those can cost what the change saves, which is why §8 is measured first.
- **The measurement that decides it:** the button window again, read for a tail of one slow frame per crossing. If crossings are then the whole cost, this section is the route; if the tail is gone, it is not needed.

## 10. Cross-references

| Feature | What it owns that this must not contradict |
|---|---|
| [`UI_Map`](../UI_Map/FEAT_DSC_UI_Map.md) | the render cadence (`mapRefreshFps`), the overlay z-order, the map's paint lengths |
| [`DepthMapping`](../DepthMapping/FEAT_DSC_DepthMapping.md) | the raster pipeline, the grid, the banded-overlay decision |
| [`Tracks`](../Tracks/FEAT_DSC_Tracks.md) | the two render axes, banded strokes, the speed heatmap |
| [`GPS`](../GPS/FEAT_DSC_GPS.md) | the fix cadence and demo mode, which feed the map-centre pipeline |
| [`260614_FEAT_PLN_Performance_drag-stutter-analysis.md`](260614_FEAT_PLN_Performance_drag-stutter-analysis.md), [`…-event-chain.md`](260614_FEAT_PLN_Performance_drag-stutter-event-chain.md) | the source-reasoned figures this pass is meant to confirm or retire |
