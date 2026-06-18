# Hydration: ArcLayout — scrim-dismiss

**Baked:** 2026-06-18 19:36 UTC

## Micro-State Summary

Added transparent full-screen scrim to `MapContent()` that closes the fan on external tap. Generic `onDismissFan` callback ensures future fans work without changes. Fixed badge dimming — badge now always shows full color regardless of toggle state.

### What changed
- **MapScreen.kt**: Added `onDismissFan: () -> Unit` parameter to `MapContent()`. Added scrim Box between CoastlineMapView and overlay Row: `if (expandedFanId != null) Box(Modifier.fillMaxSize().clickable { onDismissFan() })`. Wired in `MapScreen()` as `onDismissFan = { expandedFanId = null }`.
- **FanLayout.kt**: Updated KDoc — replaced "No scrim" reference with accurate scrim dismiss description. Removed badge alpha dimming based on `activeChildCount`.
- **FEAT_DSC_ArcLayout.md**: Added `scrim-dismiss` subfeature with todos/rules. Marked old fan-migration "No scrim" rule as superseded.

### Key design
- Scrim is invisible (no background) — just catches taps, no visual dimming
- Placed between MapView and overlay Row so fan children, settings, zoom still consume their own taps
- Generic `onDismissFan()` works for any number of future fans
- Badge always uses full color — never dims when children are toggled off

### Next step
- Ready to add second fan button in control stack
