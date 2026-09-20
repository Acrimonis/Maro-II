<!-- scope: feature -->
# Per-frame cost levers — after the zoom-step fix

**Feature:** Performance · **Branch:** `feature/performancE` · **Status:** partially shipped 2026-09-20 — L1 removed after measuring neutral and L6 (the contour flags) shipped and measured; L2–L5 open, each still one build and one device window · **Written:** 2026-09-20

## 1. Purpose

The zoom-step fix — the pass's §8, shipped 2026-09-20 — removed the per-**step** rebuild: the roughly ten frames at 650–800 ms, one per button tap, are gone from the buttons window. What remains is per-**frame** work, and one item on this list is a repaint the fix itself added. This plan collects the cheap levers against that per-frame cost, in the order the measurements justify, and points at the tiling design ([`260920_FEAT_PLN_Performance_map-layer-cost.md`](260920_FEAT_PLN_Performance_map-layer-cost.md:129) §9) for the one that is not cheap.

## 2. Where the numbers stand

The readings live with the pass that took them — §6 and §8 of the map-layer-cost plan. Two windows matter here, both the zoom buttons at about one tap a second with depth colour alone:

| Window | Build | Frames | Median | 90th | 99th | Janky | Missed vsync |
|---|---|---|---|---|---|---|---|
| buttons | before the fix | 789 | 21 ms | 29 ms | 700 ms | 7.6% | 11 |
| buttons | after the fix | 796 | 16 ms | 61 ms | 101 ms | 8.8% | 20 |

The spike is gone and the middle thickened. On the after run the counters were slow UI 25, slow draw 66 and zero slow bitmap uploads, so what is left sits in drawing and in the frame, not in rebuilding.

## 3. The levers

**L1 — remove the listener's repaint.** [`CoastlineMapView.kt:295`](../../app/src/main/java/ykws/android/maro/ui/map/CoastlineMapView.kt:295) invalidates the view on every zoom event, and the map already repaints a zoom. The line was the belt to the gate-keys braces and is the likeliest cause of the 90th percentile doubling from 29 to 61 ms.
- *Verify:* the buttons window again — the 90th back near 30 ms with the tail still gone.
- *Risk:* if an overlay does not re-project on its own, a layer would lag a step. The coastline layer is the counter-evidence, never zoom-keyed and always correct, so the check is one visual pinch held inside a single band.
- *Measured 2026-09-20:* removed, and neutral — the fifteen-second sweep read 57 ms against 48 ms across windows whose own spread is ±20%, so the repaint cost nothing measurable and stays out as simpler code, not as a fix.

**L2 — publish the zoom less often.** The listener calls `updateZoomLevel` on every zoom event, and that republishes a state read at [`MapScreen.kt:488`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:488) and handed to the markers, the cap arrow and the isobaths — so a pinch recomposes the map subtree frame by frame. Publishing at a tenth of a zoom level, or coalescing it, cuts that rate about tenfold.
- *Verify:* the pinch window — the frame count up, the UI-thread counter down.
- *Risk:* marker scaling becomes stepped. At 0.1 of a level it is imperceptible; coarser is not, so the quantum is a visible choice and therefore the user's.

**L3 — reorder only when the order is wrong.** [`OverlayZOrder.reorder`](../../app/src/main/java/ykws/android/maro/ui/map/OverlayZOrder.kt:62) drops and re-adds every overlay on each rebuild. An early return when the bands are already canonical removes that work.
- *Value today:* small, because gate keys made rebuilds rare.
- *Value later:* high if the tiling design lands, since it multiplies rebuilds.

**L4 — fewer depth bands.** `DEPTH_OVERLAY_BANDS` is 8 strips per raster ([`MapOverlayRenderer.kt:28`](../../app/src/main/java/ykws/android/maro/ui/map/MapOverlayRenderer.kt:28)), each stretched on every frame the map moves; halving it halves that work for both rasters.
- *Risk:* the banding exists to keep the stretched raster's projection error sub-metre, so fewer bands buy speed with a visible offset — a look rather than a number, hence the user's call.

**L6 — gate the contours by flag, not by rebuild (shipped 2026-09-20).** The isobath set was dropped, re-created and re-stacked at every crossing, which one fifteen-second wide sweep paid seven times at roughly 0.7 s each — the pause felt mid-stroke. `drawIsobaths` now attaches every contour, `OverlayTracker` keeps the 2 m subset in its own list, and `applyIsobathGates` writes one boolean per polyline when a floor is crossed. Measured on the shipped build: **145 frames to 410** in the same sweep, with nothing above 150 ms left in the histogram.
- *Residue:* the steady 60–90 ms band is untouched by it, so the sweep is still not smooth — that residue is L2's, or nothing on our side.

**L5 — attach only the contours in view.** Every vertex of every attached polyline is re-projected each frame, so filtering the isobaths to the viewport at rebuild time is the largest per-frame lever — but it needs a rebuild as the viewport moves, which is the tiling design in miniature. Recorded here, planned there.

## 4. Order and measurement

- L6 shipped ahead of L2 because the sweep's histogram pointed at the crossing rather than at the publication: seven discrete 0.7 s frames, not a uniformly slow frame.
- L1 first: it may be a regression the shipped fix introduced, and it costs one line — measured, and it was not.
- L2 second: it targets the UI-thread saturation every pinch window has shown.
- L3 and L4 only if the pinch still hurts after those two, each on its own build and window.
- The pinch window against the shipped build is owed and comes before all of it. If it already returns hundreds of frames, L1 is likely to be the only one that matters.
- Every window is the same shape as the pass's: ten seconds, one gesture, depth colour alone unless the lever says otherwise, the close-in framing, and each cell read twice where the first is ambiguous.

## 5. Verification contract

- Buttons, depth colour alone, about one tap a second: median at or under 21 ms, nothing past about 100 ms, 90th under about 30 ms.
- Pinch, same switches, fast: hundreds of frames against the 14 of the pre-fix build.
- One visual pinch held inside a single band: contours and rasters stay glued to the water.

## 6. Constraints

- No new dependency; the zoom floors keep their one home with the drawing code; each lever stays a one-file change where it can.
- Nothing here is built until a reading justifies it, and each lever names the window that decides its worth.
