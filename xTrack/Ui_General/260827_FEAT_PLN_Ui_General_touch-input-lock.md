# Touch-input lock — splash-proof screen lock button

**Feature:** Ui_General
**Status:** designed
**Created:** 2026-08-27 12:35 UTC

## Request

Add a toggle button at the top-left of the map that "locks" the screen so the app
ignores **all** touch input — map pan/zoom and every control — except the toggle
itself and the zoom +/− buttons. Use case: navigating in a wet environment where
splashing water registers as ghost taps/drags/pinches and wreaks havoc on the map.
Hardware back stays active. Lock state is transient (not persisted).

## Decisions

| # | Decision |
|---|----------|
| D1 | State `screenLocked: Boolean` hoisted in [`MapScreen()`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:358) via `rememberSaveable` — survives rotation, not persisted, so the app never starts locked. |
| D2 | Lock button = new `LockScreenButton`, 44dp rounded box matching [`GpsStatusIcon()`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:3738), emoji 📱 (unlocked) / 🔒 (locked), inserted right of the Earth/Water icon in the top-left status row (order: GPS → Tracking → Earth/Water → Lock → Recenter). |
| D3 | Lock overlay = full-screen transparent `LockScrim` with a consume-all `pointerInput` loop (swallows tap + drag + pinch), rendered in the [`BoxWithConstraints`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1756) **after** [`OverlayLayer()`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:2233) so it covers map, dashboard, and drawers. |
| D4 | Unlock affordance = top-most duplicate `LockScreenButton` at `TopStart` (`top = lockTopInset`, `start = 6.dp + 3 × (44.dp + 6.dp) = 156.dp`) rendered only when locked, aligned over the row's 4th icon. |
| D5 | Hardware back untouched ([`BackHandler`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1682)) — input lock only. |
| D6 | Feedback = icon colour change + generic bottom banner (exit-toast style) showing "Screen locked"/"Screen unlocked" at the bottom of the map, left of the +/− buttons, auto-dismissing ~2s; **no dim** so the map stays readable. |
| D7 | Icon = emoji 📵 U+1F4F5 in **both** states (state shown by colour: `semantic.inactive` dimmed ↔ `semantic.caution` active); `contentDescription` (`cd_lock_screen` / `cd_unlock_screen`) + EN/FR strings distinguish the state. |
| D8 | Styling = status-icon recipe (44dp / 8dp radius / 22sp emoji) with a new `status.lock.*` token family in `colors.properties` + `AppConfig`: unlocked = `semantic.inactive` (dimmed, contentAlpha 0.50), locked = `semantic.caution` (active, contentAlpha 1f). |
| D9 | Locked exception — zoom controls: extract the +/− pair into a shared `ZoomControls` composable (64dp buttons, 6dp gap, centered). When locked, render `ZoomControls` above the scrim at `Alignment.BottomEnd` + `padding(end = 6.dp, bottom = 6.dp)` with `doubleTap = true` — a single splash tap is ignored, a double-tap zooms one step; the scrim still blocks everything else. |

## Key facts

- The map is an osmdroid `MapView` embedded via [`AndroidView`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:3290) with multi-touch controls — the scrim must consume all events at the Compose layer before the view receives them.
- [`DashboardPanel`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:2016) and `OverlayLayer` are siblings **above** `MapContent`, so the scrim must live in `MapScreen`'s `BoxWithConstraints`, not inside `MapContent`.
- The existing fan-dismiss scrim ([`MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:2826)) uses `clickable` (taps only) — the lock scrim needs the raw `awaitPointerEventScope` consume loop to also swallow drag/pinch.
- The top-left row uses `topInset = statusBars − 6.dp` (portrait) / full statusBars (landscape); the duplicate button must replicate this inset.

## Files touched

| File | Change |
|------|--------|
| `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` | Add `screenLocked` state + `onToggleScreenLock`; add defaulted `screenLocked` / `onToggleScreenLock` params to `MapContent`; render `LockScrim` + duplicate `LockScreenButton` after `OverlayLayer`; add `LockScreenButton` composable; insert it right of the Earth/Water icon; extract shared `ZoomControls` and render a duplicate above the scrim when locked; generic lock banner on toggle |
| `app/src/main/res/values/strings.xml` | Add `cd_lock_screen`, `cd_unlock_screen`, `toast_screen_locked`, `toast_screen_unlocked` |
| `app/src/main/res/values-fr/strings.xml` | Add FR equivalents |
| `app/src/main/assets/colors.properties` | Add `status.lock.off` / `status.lock.on` / `status.lock.alpha.active` / `status.lock.alpha.dimmed` |
| `app/src/main/java/ykws/android/maro/config/AppConfig.kt` | Add `statusLockOff` / `statusLockOn` / `statusLockAlphaActive` / `statusLockAlphaDimmed` + parsing |
| `docs/ui-component-guidelines.md` | Document the top-left status-icon pattern incl. the lock button (§5.5) |

## Implementation notes

1. `LockScrim`: `Box(Modifier.fillMaxSize().pointerInput(Unit) { awaitPointerEventScope { while (true) { awaitPointerEvent().changes.forEach { it.consume() } } } })` — may need the pointer-scope import.
2. `LockScreenButton(locked, onClick, modifier)`: 44dp box, 8dp rounded corners, 22sp emoji 📵 (U+1F4F5) in both states — `AppConfig.statusLockOff` (bgAlpha `statusLockAlphaDimmed`, contentAlpha 0.50) when unlocked; `AppConfig.statusLockOn` (bgAlpha `statusLockAlphaActive`, contentAlpha 1f) when locked.
3. Duplicate alignment: `top = lockTopInset`; `start = 6.dp + 3 * (44.dp + 6.dp)` (= 156.dp) since the lock is the 4th icon (after GPS, Tracking, Earth/Water).
4. Lock banner (replaces `Toast.makeText`): transient `showLockBanner` state with 2s auto-dismiss; render top-most (above the scrim) at `BottomStart` with `end = RIGHT_CONTROL_COLUMN_INSET`, reusing the exit-toast style (14dp `Surface` + 2dp border + `buttonActionBgColor` + `uiCardBackground` + `uiSettingsToastText`). Text from `toast_screen_locked` / `toast_screen_unlocked`.
5. Build: `gradlew assembleDebug` (or `apk-build.bat`) after changes.
6. `colors.properties`: `status.lock.off=${semantic.inactive}`, `status.lock.on=${semantic.caution}`, `status.lock.alpha.active=0.75`, `status.lock.alpha.dimmed=0.50`; mirror the existing `status.gps.*` parsing in `AppConfig`.
7. `ZoomControls(onZoomIn, onZoomOut, doubleTap = false)`: two 64dp buttons (`PlusIcon` / `MinusIcon`) in a centered `Column` with 6dp spacing. Hoist `onZoomIn`/`onZoomOut` in `MapScreen`; the normal right column uses single-tap, the locked duplicate uses `doubleTap = true` (`detectTapGestures(onDoubleTap)` — single splash taps ignored, double-tap zooms one step).

## Implemented

2026-08-27 — touch-input lock shipped (BUILD SUCCESSFUL ×3, `gradlew assembleDebug`):
- `screenLocked` (`rememberSaveable`) + `onToggleScreenLock`, threaded into `MapContent`.
- `LockScreenButton` — 44dp/8dp/22sp, 📵 U+1F4F5 in both states (colour-coded via `status.lock.*`), positioned right of the Earth/Water icon.
- `LockScrim` — full-screen consume-all `pointerInput`; when locked it renders above map/dashboard/drawers, with a top-most duplicate unlock button (start = 156.dp) and a duplicate `ZoomControls` (BottomEnd, 6dp insets).
- `ZoomControls` — shared 64dp +/− pair (6dp gap) reused by the normal right column.
- `LockBanner` — exit-toast-style banner (bottom-left, left of the +/−), 2s auto-dismiss, replacing `Toast.makeText`.
- `status.lock.*` tokens in `colors.properties` + `AppConfig`; EN/FR strings; §5.5 status-icon guideline.
- Fix: locked-overlay controls wrapped in a `Box` mirroring `MapContent`'s dashboard padding (portrait bottom / landscape start) so the duplicate lock button, zoom controls, and banner align in both orientations (BUILD SUCCESSFUL).
