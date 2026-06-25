# Drawer Visual Differentiation — Implementation Plan

> **Scope:** Make drawers visually distinct from the dashboard/map they cover.
> **Techniques:** Scrim + visible offset shadow + spring animation + entrance animation.
> **Status:** Iteration 2 — fixes for invisible shadow, masked bounce, Wizard positioning, Settings animation.

---

## Phase 1 — BOM & SDK Migration (Execute First)

> **Note:** After Phase 1 is complete and merged, resume evaluation of Phase 2 starting at Step 3 (shadow approach).

### Migration Scope

Upgrade to May 2026 versions for better Compose shadow rendering and API support.

| Component | Current | Target | File |
|-----------|---------|--------|------|
| Compose BOM | `2024.05.00` | `2026.05.00` | `gradle/libs.versions.toml:8` |
| compileSdk | `34` | `36` | `app/build.gradle.kts:11` |
| targetSdk | `34` | `36` | `app/build.gradle.kts:16` |
| AGP | `8.4.1` | compatible with Gradle | `gradle/libs.versions.toml:2` |
| Gradle | `8.7` | latest stable | `gradle/wrapper/gradle-wrapper.properties:3` |
| Kotlin | `2.0.0` | latest 2.1.x stable | `gradle/libs.versions.toml:3` |

### Migration Branch

- New branch: `feature/gps-migrate` from `origin/develop`
- After migration: build `assembleDebug` must succeed
- Merge into current feature branch before proceeding to Phase 2

---

## Phase 2 — Visual Fixes (Resume After Migration)

### Step 1 — Fix Wizard Positioning

#### Problem
`AnimatedVisibility` at [`MapScreen.kt:991`](../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:991) wraps the Wizard content but the inner `Modifier.align(Alignment.CenterStart)` / `Modifier.align(Alignment.BottomCenter)` reference the wrong parent scope. The Wizard appears at the top of the screen instead of at the dashboard position.

#### Fix
Restructure so the `AnimatedVisibility` wraps the outer Box that holds the alignment + Wizard content:

```kotlin
// Portrait case — Wizard slides up from bottom to dashboard position
AnimatedVisibility(
    visible = showWizard,
    enter = slideInVertically(spring(...)) { it } + fadeIn(tween(80)),
    exit = slideOutVertically(spring(...)) { it } + fadeOut(tween(150))
) {
    Box(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .height(portraitDashboardHeight)
            .offset(y = keyboardOffsetDp)
    ) {
        WizardDrawer(...)
    }
}
```

Key: the alignment Box is INSIDE `AnimatedVisibility`, and `AnimatedVisibility` fills the parent via its content.

#### Affected
- [`MapScreen.kt:989-1052`](../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:989)

---

### Step 2 — Fix Fade Masking Spring Bounce

#### Problem
`fadeIn(tween(200))` runs in parallel with spring slide. Content is invisible during the overshoot phase (first ~150ms).

#### Fix
Shorten fade duration so content is visible during the spring overshoot:

| File | Lines | Change |
|------|-------|--------|
| `MarkerDrawer.kt` | 98-101 | `fadeIn()` default (300ms) → `fadeIn(tween(80))` |
| `TrackDrawerOverlay.kt` | 107-108 | `fadeIn()` default → `fadeIn(tween(80))` |
| `MapScreen.kt` (Wizard) | 999 | `fadeIn(tween(200))` → `fadeIn(tween(80))` |

---

### Step 3 — Replace System Shadow with Visible Offset Shadow (Evaluate After Migration)

#### Problem
`Modifier.shadow(16.dp)` uses Android's RenderNode elevation system which draws a subtle shadow. On a dark-themed UI (drawer on 32% black scrim), the black-on-black shadow has near-zero contrast. The custom `ambientColor`/`spotColor` params require API 29+ and still produce a barely perceptible effect.

**Evaluate after BOM/SDK migration:** Newer Compose BOM may improve shadow rendering. After upgrade, test if `Surface(shadowElevation = 16.dp)` produces a visible shadow. If still invisible, use the offset Box approach below.

#### Fallback Fix (if shadow still invisible after migration)
Replace `Modifier.shadow()` with an explicit offset Box behind each drawer panel:

```kotlin
Box {
    // Visible offset "shadow" — light color on dark background
    Box(
        modifier = Modifier
            .matchParentSize()
            .offset(x = 6.dp, y = 6.dp)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.10f))
    )
    // Actual panel
    Box(
        modifier = Modifier
            .matchParentSize()
            .clip(shape)
            .background(Color(AppConfig.uiSettingsBackground))
    ) { content }
}
```

#### Affected Files
| File | Lines | Current | After |
|------|-------|---------|-------|
| `MarkerDrawer.kt` | 119-130 | `clip() → background() → shadow()` | Outer `Box` with offset shadow layer + panel |
| `TrackDrawerOverlay.kt` | 113-117 | Same | Same pattern |
| `TrackHistoryOverlay.kt` | 230-234 | Same | Same pattern |
| `WizardDrawer.kt` | 129-133 | Same | Same pattern |

---

### Step 4 — Add Settings Overlay Entrance Animation

#### Problem
`SettingsOverlay` at [`MapScreen.kt:1078`](../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1078) appears/disappears instantly.

#### Fix
Wrap in `AnimatedVisibility` with spring fade:

```kotlin
AnimatedVisibility(
    visible = showSettings,
    enter = fadeIn(spring(dampingRatio = 0.6f, stiffness = 300f)),
    exit = fadeOut(tween(150))
) {
    SettingsOverlay(...)
}
```

## Implementation Order (Phase 2)

1. Step 1 — Wizard positioning fix (build test)
2. Step 2 — Fade masking fix (build test)
3. Step 3 — Evaluate shadow; apply offset Box if still invisible (build test)
4. Step 4 — Settings animation (build test)
