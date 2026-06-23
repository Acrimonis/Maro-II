# Hydration: Ui_General

**Session:** Fan tweak — scrim removal + map touch pass-through dismiss.

**State:**
- `fan tweak [x]` — removed transparent scrim, added MapView.setOnTouchListener for pass-through dismiss

**What happened this session:**
- Removed the transparent `Box(Modifier.clickable { onDismissFan() })` scrim from `MapContent()` (was between MapView and overlay Row).
- Added `expandedFanId: ControlId?` and `onDismissFan: () -> Unit` params to `CoastlineMapView()`.
- In `CoastlineMapView.update` block: `mapView.setOnTouchListener` — on `ACTION_DOWN` with fan open, calls `onDismissFan()`, returns `false` so MapView processes the touch for pan/zoom.
- Threaded both params from `MapContent` call site.
- BUILD SUCCESSFUL.

**Key Files:**
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — scrim removed, CoastlineMapView params + touch listener, call site threaded

**Last Bake:** 2026-06-23 15:28 UTC
