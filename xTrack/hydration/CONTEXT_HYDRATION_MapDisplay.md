# Context Hydration — MapDisplay

**Last Bake:** 2026-06-07
**Feature Status:** active (3/4 subfeatures done)
**Active Subfeature:** zone proximity auto-reveal (done ✅)

## State Summary
Layer toggle button (stacked-layers icon) on map right edge — theme blue, 25% icon alpha when hidden. Demo pan speed extrapolation: Haversine ÷ elapsed time, /10 divisor, 500ms stop timeout. Zone auto-reveal state machine: direction-aware, 400m buffer, auto-reveals on approach, auto-re-hides on exit (no GPS gating). Zone300Card now shows speed compliance colors (green/red) using GPS or demo speed. All dashboard tile emoji icons removed from titles.

## Key Files
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — LayerButton composable + demoSpeedKnots wiring
- `app/src/main/java/ykws/android/maro/ui/map/CoastlineViewModel.kt` — demoSpeedKnots, computeDemoSpeed(), toggleZone300Visibility(), zone auto-reveal state machine
- `app/src/main/java/ykws/android/maro/ui/map/DashboardPanel.kt` — SpeedCard merge, Zone300Card speed compliance colors, no emoji in titles

## Next Steps
- Subfeature "depth color" still pending: align DepthCard color with DepthColorRamp palette
