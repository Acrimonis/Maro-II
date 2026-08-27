# Touch-input lock — splash-proof screen lock button

**Feature:** Ui_General
**Status:** designed
**Created:** 2026-08-27 12:35 UTC

## Request

Add a toggle button at the top-left of the map that "locks" the screen so the app
ignores **all** touch input — map pan/zoom and every control — except the toggle
itself. Use case: navigating in a wet environment where splashing water registers
as ghost taps/drags/pinches and wreaks havoc on the map. Hardware back stays
active. Lock state is transient (not persisted).

## Decisions

| # | Decision |
|---|----------|
| D1 | State `screenLocked: Boolean` hoisted in [`MapScreen()`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:358) via `rememberSaveable` — survives rotation, not persisted, so the app never starts locked. |
| D2 | Lock button = new `LockScreenButton`, 44dp rounded box matching [`GpsStatusIcon()`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:3738), emoji 📱 (unlocked) / 🔒 (locked), inserted right of the Earth/Water icon in the top-left status row (order: GPS → Tracking → Earth/Water → Lock → Recenter). |
| D3 | Lock overlay = full-screen transparent `LockScrim` with a consume-all `pointerInput` loop (swallows tap + drag + pinch), rendered in the [`BoxWithConstraints`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1756) **after** [`OverlayLayer()`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:2233) so it covers map, dashboard, and drawers. |
| D4 | Unlock affordance = top-most duplicate `LockScreenButton` at `TopStart` (`top = lockTopInset`, `start = 6.dp + 3 × (44.dp + 6.dp) = 156.dp`) rendered only when locked, aligned over the row's 4th icon. |
| D5 | Hardware back untouched ([`BackHandler`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1682)) — input lock only. |
| D6 | Feedback = icon state change + brief toast "Screen locked"/"Screen unlocked"; **no dim** so the map stays readable while navigating. |
| D7 | Icon = emoji — 📱 U+1F4F1 when unlocked, 🔒 U+1F512 when locked; `contentDescription` + EN/FR strings. |
| D8 | Styling = status-icon recipe (44dp / 8dp radius / 22sp emoji) with a new `status.lock.*` token family in `colors.properties` + `AppConfig`: unlocked = `semantic.inactive` (dimmed, contentAlpha 0.50), locked = `semantic.caution` (active, contentAlpha 1f). |

## Key facts

- The map is an osmdroid `MapView` embedded via [`AndroidView`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:3290) with multi-touch controls — the scrim must consume all events at the Compose layer before the view receives them.
- [`DashboardPanel`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:2016) and `OverlayLayer` are siblings **above** `MapContent`, so the scrim must live in `MapScreen`'s `BoxWithConstraints`, not inside `MapContent`.
- The existing fan-dismiss scrim ([`MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:2826)) uses `clickable` (taps only) — the lock scrim needs the raw `awaitPointerEventScope` consume loop to also swallow drag/pinch.
- The top-left row uses `topInset = statusBars − 6.dp` (portrait) / full statusBars (landscape); the duplicate button must replicate this inset.

## Files touched

| File | Change |
|------|--------|
| `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` | Add `screenLocked` state + `onToggleScreenLock`; add defaulted `screenLocked` / `onToggleScreenLock` params to `MapContent`; render `LockScrim` + duplicate `LockScreenButton` after `OverlayLayer`; add `LockScreenButton` composable; insert it right of the Earth/Water icon; toast on toggle |
| `app/src/main/res/values/strings.xml` | Add `cd_lock_screen`, `cd_unlock_screen`, `toast_screen_locked`, `toast_screen_unlocked` |
| `app/src/main/res/values-fr/strings.xml` | Add FR equivalents |
| `app/src/main/assets/colors.properties` | Add `status.lock.off` / `status.lock.on` / `status.lock.alpha.active` / `status.lock.alpha.dimmed` |
| `app/src/main/java/ykws/android/maro/config/AppConfig.kt` | Add `statusLockOff` / `statusLockOn` / `statusLockAlphaActive` / `statusLockAlphaDimmed` + parsing |
| `docs/ui-component-guidelines.md` | Document the top-left status-icon pattern incl. the lock button (§5.5) |

## Implementation notes

1. `LockScrim`: `Box(Modifier.fillMaxSize().pointerInput(Unit) { awaitPointerEventScope { while (true) { awaitPointerEvent().changes.forEach { it.consume() } } } })` — may need the pointer-scope import.
2. `LockScreenButton(locked, onClick, modifier)`: 44dp box, 8dp rounded corners, 22sp emoji — 📱 + `AppConfig.statusLockOff` (bgAlpha `statusLockAlphaDimmed`, contentAlpha 0.50) when unlocked; 🔒 + `AppConfig.statusLockOn` (bgAlpha `statusLockAlphaActive`, contentAlpha 1f) when locked.
3. Duplicate alignment: `top = lockTopInset`; `start = 6.dp + 3 * (44.dp + 6.dp)` (= 156.dp) since the lock is the 4th icon (after GPS, Tracking, Earth/Water).
4. Toast: plain `Toast.makeText` for lock/unlock (simpler than the `showExitBanner` banner path). Optional: `stateDescription` semantics on the button so TalkBack announces locked/unlocked.
5. Build: `gradlew assembleDebug` (or `apk-build.bat`) after changes.
6. `colors.properties`: `status.lock.off=${semantic.inactive}`, `status.lock.on=${semantic.caution}`, `status.lock.alpha.active=0.75`, `status.lock.alpha.dimmed=0.50`; mirror the existing `status.gps.*` parsing in `AppConfig`.
