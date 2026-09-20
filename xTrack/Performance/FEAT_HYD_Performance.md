# Context Hydration — Performance — 2026-09-20

**Last Bake:** 2026-09-20 13:50 UTC — written by `#bake`; absence means never baked

**Directive trace:** the device class was met on the user's own order throughout — adb driven from here at their word, the gestures always theirs, every window fired only after an explicit go, and no deploy run or raised. Two of my own claims were corrected by measurement rather than defended: the repaint I had added to the zoom listener was proven neutral and removed, and the "the zoom path is the floor" reading was split apart from the crossing pause it had been hiding. No dependency was added, no machine-shaped file opened, and nothing was committed or pushed from here.

## State

Branch **`feature/performancE`**, cut from `origin/develop` (`9756a47`), now carrying the pass's three changes in the working tree, uncommitted as this bake was written.

- **In the tree:** the gate-keyed layer rebuilds (plan §8), the zoom listener's repaint removed after measuring it neutral, and the contour polylines attached once and gated by their own `enabled` flag instead of being rebuilt at every crossing.
- **Readings, Pixel 7 / Android 16 on USB, demo mode, 90 Hz, close-in framing:** the zoom-button tail of ~10 frames at 650–800 ms is gone with the median at 12–16 ms against 21 ms before; the depth-colour pinch went from **14 frames at 750 ms to 205 at 53 ms**; the wide in-and-out sweep went from **7 frames at 650–700 ms to none above 150 ms**, rendering 410 frames against 145.
- **The floors that set the ceiling:** a pinch costs 53 ms a frame with nothing switched on and a bare far-out drag 17–28 ms, so a smooth 60 Hz pinch is not on offer and the residual 60–90 ms band in a sweep belongs to the zoom path, not to a layer.
- **Owed:** the warm repeat of the flag-gated sweep; the eye check that the contours still appear and vanish at the 13 and 15 floors; the all-layers pinch (8 frames at 3150 ms before the fix); a bare close-in drag, the reference every close-in delta has lacked; and the split between the colour raster and the contour lines.
- Carry-over: `docs/SETUP.md` still names the Wi-Fi device that does not answer; this pass runs on the cable.

## Target Files

- `app/src/main/java/ykws/android/maro/ui/map/CoastlineMapView.kt` — the gate keys, the effect keys, the isobath build/flag split and `applyIsobathGates`
- `app/src/main/java/ykws/android/maro/ui/map/OverlayTracker.kt` — gate booleans plus the shallow-contour list
- `app/src/main/java/ykws/android/maro/ui/map/MapOverlayRenderer.kt` — `drawIsobaths` attaches every contour and gates nothing
- `app/src/main/java/ykws/android/maro/ui/map/NavigationViewModel.kt` — the throttled zoom persist
- `xTrack/Performance/260920_FEAT_PLN_Performance_map-layer-cost.md` — the protocol, the readings and the fix
- `xTrack/Performance/260920_FEAT_PLN_Performance_per-frame-cost-levers.md` — the levers, and which of them are spent

## Next Step

The warm repeat of the wide sweep, then the eye check on the two contour floors; after those, the all-layers pinch, which is the user's own setting and the last unmeasured case of the original complaint.
