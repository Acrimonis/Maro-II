# Hydration — Ui_General

**Last Bake:** 2026-06-15 14:12 UTC

## State
All 4 subfeatures complete ✅. Feature is fully done.

## Subfeature Completion
- BackToExitConfirm [x] — double-back-to-exit guard with 2s toast
- KeepScreenOn [x] — window flag FLAG_KEEP_SCREEN_ON via settings toggle
- page layout [x] — `enableEdgeToEdge()`, light status bar icons, WindowInsets
- immersive ui rework [x] — nav bar immersion via selective WindowInsets (Option A)

## Target Files
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — all WindowInsets changes
- `app/src/main/java/ykws/android/maro/MainActivity.kt` — unchanged (already had `enableEdgeToEdge()`)

## Git
- Branch: `feature/immersitvity`
- Commit: `47b576d` — `feat(ui): extend immersive edge-to-edge to nav bar with selective WindowInsets`
- Pushed: yes, upstream tracking set

## Next Steps
- Feature is complete — consider `status: done` if no further work planned
- Create PR from `feature/immersitvity` → `develop` when ready
