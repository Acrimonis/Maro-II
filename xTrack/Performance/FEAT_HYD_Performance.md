# Context Hydration — Performance — 2026-09-20

**Last Bake:** 2026-09-20 08:52 UTC — written by `#bake`; absence means never baked

**Directive trace:** the device class was met on the user's own order — adb is driven from here at their word, and the gestures stay theirs — and two wrong statements were corrected in place rather than shipped: a track-file path that did not exist and a zoom cue that cannot be seen with the layers off. No dependency was added, no machine-shaped file was opened, and no work began without an order.

## State

Branch **`feature/performancE`**, cut from `origin/develop` (`9756a47`) and level with it; the tree holds one new plan under `xTrack/Performance/` and no app code has changed.

- **Plan in design:** [`260920_FEAT_PLN_Performance_map-layer-cost.md`](260920_FEAT_PLN_Performance_map-layer-cost.md) — a device measurement protocol for map layer cost, run on a Pixel 7 (Android 16, USB) in demo mode: eight switch states, ten-second `dumpsys gfxinfo` windows opened by a frame-counter motion gate, levels set by the ground rather than by a zoom number, and every cell repeated because window-to-window spread proved as large as the effects being hunted.
- **Floor, dragging at the far level:** 23–28 ms a frame with nothing switched on, GPU 7–12 ms, lateness split evenly between the UI thread and issuing draw commands — the app cannot reach sixty hertz bare.
- **Floor, pinching at the far level:** 81–85 ms a frame, 93.6% of frames late, GPU idle at 6–8 ms, both the UI thread and draw issuance saturated. The penalty is present with no layer at all, so the zoom path's own cost is separate from the layer question the pass was opened for.
- The first attempts — a sixty-second idle window and a pinch measured on a restarted process — are recorded as a false start in the plan's session log; the pass restarted under the gated window.
- `docs/SETUP.md` still names the Wi-Fi device that did not answer; this pass runs on USB.

## Target Files

- `xTrack/Performance/260920_FEAT_PLN_Performance_map-layer-cost.md` — the protocol, the reading table and the running session log
- `app/src/main/java/ykws/android/maro/ui/map/MapOverlayRenderer.kt` — the banded ground-overlay stacks, the isobath draw and their zoom gates
- `app/src/main/java/ykws/android/maro/ui/map/CoastlineMapView.kt` — the per-frame redraw and the `OverlayZOrder.reorder` call sites
- `app/src/main/java/ykws/android/maro/ui/map/MapDepthRasterEffects.kt` — the raster builds and their caches

## Next Step

Continue the pass at the layer cells — depth colour alone, then the shallow warning, then both together, across dragging and pinching, each read against its own gesture's floor.
