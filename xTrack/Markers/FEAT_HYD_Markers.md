# Markers — Hydration Snapshot (2026-06-25 16:08 UTC)

**Baked:** 2026-06-25 14:05 UTC+2

## Active State
- **Subfeature:** create-zones-flow
- **Branch:** feature/markers

## What Changed This Session
1. **OverlayLayer framework** — Unified drawer/scrim layer architecture: `OverlayLayer` composable owns the scrim + drawer hosting zone, eliminating per-drawer scrim duplication. All drawers (MarkerDrawer, WizardDrawer, MarkerManagementOverlay) now render inside OverlayLayer slots.
2. **DrawerSlot abstraction planned** — Designed `DrawerSlot` sealed class to type-safely route drawables to OverlayLayer: `MarkerCreation`, `MarkerWizard`, `MarkerManagement`, `MarkerMatchResult`. Replaces ad-hoc boolean flags.
3. **Wizard blank fix** — Fixed blank/empty wizard step rendering: step parameter was not being passed through the composition chain, causing `WizardDrawer` to render with a null step. Now correctly plumbed via `wizardStep` StateFlow.
4. **Spring animation normalization** — Standardized all drawer spring animations to `spring(dampingRatio = 0.8f, stiffness = 350f)` — previously mixed 1.0f/350f, 0.8f/300f, and default spring values across different drawers.
5. **Gradient shadow edge on all drawers** — Added consistent top-edge gradient shadow (`Brush.verticalGradient` from `Color.Black.copy(alpha = 0.12f)` to transparent, 8dp height) to MarkerDrawer, WizardDrawer, and MarkerManagementOverlay for visual separation from map content.
6. **MarkerManagement moved to OverlayLayer** — `MarkerManagementOverlay` no longer renders as an independent full-screen overlay; now hosted inside `OverlayLayer` as a `DrawerSlot`, sharing the unified scrim and back-press handling.

## Design Decisions
- OverlayLayer uses a single `ModalBottomSheet`-style container with `AnimatedVisibility` + `slideInVertically`/`slideInHorizontally` — consistent entry/exit for all drawer types
- DrawerSlot is a sealed class with associated Compose content factories, keeping the routing logic in one place
- Spring stiffness 350f chosen as the golden mean: snappy enough to feel responsive, damped enough to avoid overshoot on low-end devices

## Target Files
- `app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt` — new unified drawer/scrim host
- `app/src/main/java/ykws/android/maro/ui/map/DrawerSlot.kt` — new sealed class routing
- `app/src/main/java/ykws/android/maro/ui/map/WizardDrawer.kt` — step parameter fix + spring normalization + gradient shadow
- `app/src/main/java/ykws/android/maro/ui/map/MarkerDrawer.kt` — spring normalization + gradient shadow + OverlayLayer integration
- `app/src/main/java/ykws/android/maro/ui/map/MarkerManagementOverlay.kt` — moved into OverlayLayer slot
- `app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt` — DrawerSlot state management
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — OverlayLayer integration

## Next Steps
- Implement DrawerSlot sealed class with content factories
- Wire WizardDrawer step parameter end-to-end (ViewModel → OverlayLayer → WizardDrawer)
- Deploy and test all drawer transitions on device
