# Fan Layer — Button Visibility Rules

> **Canonical rules** for which buttons in the right-edge control stack are hidden when the Layer Fan (or any future fan) is expanded.
> **Status:** Agreed after discussion on 2026-06-24.

---

## 1. Core Principle

**When any fan is expanded, all non-fan controls in the right-edge column fade to `α=0`.** Only the fan anchor that opened the fan, and the fan's own children, remain visible.

The unified gate is:

```kotlin
val anyFanOpen = expandedFanId != null
```

---

## 2. Visibility Matrix

| # | Button | Section | When `anyFanOpen == true` | Code location |
|---|--------|---------|--------------------------|---------------|
| 1 | Hamburger / Settings | `ct` (top) | **Fade to α=0** | [`MapScreen.kt:1549`](../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1549) |
| 2 | **Layer fan anchor** (parent button) | `cm` (middle) | **Stay visible α=1** when this fan is expanded; α=0 if another fan opens | [`MapScreen.kt:1576`](../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1576) |
| 3 | **Layer fan children** (6 toggles) | `cm` (middle) | Visible only when fan `isOpen == true` (managed by `FanLayout`) | [`FanLayout.kt:96`](../app/src/main/java/ykws/android/maro/ui/map/FanLayout.kt:96) |
| 4 | **Add Zone** button | `cm` (middle) | **Fade to α=0** (consistency rule — same as ct/cb sections) | [`MapScreen.kt:1629-1634`](../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1629) |
| 5 | Zoom +/- | `cb` (bottom) | **Fade to α=0** | [`MapScreen.kt:1640`](../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1640) |

---

## 3. Alpha Animation

All fade transitions use `animateFloatAsState` with `tween(300)`:

```kotlin
val alpha by animateFloatAsState(
    targetValue = if (anyFanOpen) 0f else 1f,
    animationSpec = tween(300)
)
```

Exception — the fan anchor uses a conditional target so it only fades when **another** fan is expanded:

```kotlin
val cmAlpha by animateFloatAsState(
    targetValue = if (anyFanOpen && !isExpanded) 0f else 1f,
    animationSpec = tween(300)
)
```

---

## 4. Dismiss Mechanisms

When a fan is open, three dismiss paths exist:

| Path | Mechanism | Code |
|------|-----------|------|
| **Tap fan anchor** | Parent click handler toggles `expandedFanId = null` | [`MapScreen.kt:1598`](../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1598) |
| **Tap scrim** (map area outside controls) | Full-screen `Box` with `clickable { onDismissFan() }` | [`MapScreen.kt:1388-1390`](../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1388) |
| **System Back** | `BackHandler { expandedFanId = null }` | [`MapScreen.kt:765-767`](../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:765) |

---

## 5. `ControlId` Enum (Future-Proofing)

```kotlin
private enum class ControlId { SETTINGS, LAYER_FAN, ZOOM, MENU }
```

Defined at [`MapScreen.kt:1267`](../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1267). Currently only `LAYER_FAN` is wired. If future fans are added (e.g., `ZOOM` fan like Garmin), each must:

1. Set `expandedFanId = ControlId.ZOOM` on open.
2. The `cm` section logic will automatically fade other fans' anchors (the `!isExpanded` check).
3. The `ct` and `cb` sections will automatically fade via the existing `anyFanOpen` gate.

---

## 6. Add Zone — Consistency Fix Needed

The **Add Zone** button currently lacks fan-state gating. To apply the consistency rule, it must be wrapped with the same `anyFanOpen` alpha as `ct`/`cb`. This is a one-line change: apply `.alpha(if (anyFanOpen) 0f else 1f)` to the Add Zone button's modifier.
