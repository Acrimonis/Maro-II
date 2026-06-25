# Markers — Hydration Snapshot (2026-06-25 16:08 UTC)

## State
- **Active subfeature:** create-zones-flow
- **Status:** active
- **Last action:** #merge — rebased onto origin/develop (5c9bd9e). 23 commits replayed. Two post-rebase API fixes: CoastlineViewModel→NavigationViewModel, GpsFix→TrackSample.
- **Build:** assembleDebug ✅

## Target Files
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — markers integration (OverlayLayer, MarkersViewModel, WizardDrawer)
- `app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt` — unified drawer/scrim framework
- `app/src/main/java/ykws/android/maro/ui/map/DrawerSlot.kt` — drawer slot abstraction
- `app/src/main/java/ykws/android/maro/ui/map/WizardDrawer.kt` — step-by-step marker creation wizard
- `app/src/main/java/ykws/android/maro/ui/map/MarkerOverlay.kt` — map Canvas overlay rendering
- `app/src/main/java/ykws/android/maro/ui/map/MarkerDrawer.kt` — marker edit drawer
- `app/src/main/java/ykws/android/maro/ui/map/MarkerManagementOverlay.kt` — marker list management
- `app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt` — markers state management

## Next Step
- Continue create-zones-flow wizard implementation
