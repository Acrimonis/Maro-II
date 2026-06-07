# Context Hydration — MapDisplay

**Last Bake:** 2026-06-07
**Feature Status:** active (3/4 subfeatures done)
**Active Subfeature:** zone proximity auto-reveal (done ✅)

## State Summary
Layer toggle button (stacked-layers icon) added to map right edge, equidistant between settings and zoom buttons. Toggles `appSettings.zone300Visible` via `viewModel.toggleZone300Visibility()`. Demo pan speed extrapolation implemented: Haversine distance ÷ elapsed time × MPS→knots, throttled to ~10Hz, 500ms stop-detection timeout, divided by 10 for realistic values. Zone proximity auto-reveal state machine: direction-aware (getting closer vs moving away), 400m buffer, auto-reveals on approach, auto-re-hides on exit, no GPS gating.

## Key Files
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — LayerButton composable + demoSpeedKnots wiring
- `app/src/main/java/ykws/android/maro/ui/map/CoastlineViewModel.kt` — demoSpeedKnots, computeDemoSpeed(), toggleZone300Visibility(), zone auto-reveal state machine
- `app/src/main/java/ykws/android/maro/ui/map/DashboardPanel.kt` — SpeedCard (unchanged, merge happens upstream)

## Next Steps
- Subfeature "depth color" still pending: align DepthCard color with DepthColorRamp palette
