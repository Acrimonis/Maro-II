<!-- scope: feature -->
# Portrait Dashboard Bottom Space & Status Bar Immersion

> Discussion — analysis of two UI layout topics raised during `#checkout feature/ui-general` exploration.

---

## 1. Portrait Mode — Lost Space at Dashboard Bottom

### Current Layout (MapScreen.kt:527-608)

The portrait layout uses a `BoxWithConstraints` where:
- **Map** fills the box with `.padding(bottom = portraitDashboardHeight)`
- **Dashboard** is overlaid via `.align(Alignment.BottomCenter)` with `.height(portraitDashboardHeight)`
- `portraitDashboardHeight = maxWidth * 3 / 5` (line 529)

The outer `Box` (line 495) uses `modifier.fillMaxSize()` with **no** insets awareness — no `WindowInsets` consumption, no `imePadding()`, no `navigationBars()` padding.

### Root Cause Analysis

The `Theme.MaroII` parent is `android:Theme.Material.Light.NoActionBar` — this is a **pre-Compose, pre-edge-to-edge** theme. On Android 15+, the system navigation bar (gesture handle area) draws over the app content. Since there's no:
- `enableEdgeToEdge()` call in `MainActivity.onCreate()` (Android 15+ API)
- `WindowCompat.setDecorFitsSystemWindows(window, false)` 
- Any `WindowInsets` handling in the Compose tree

…the dashboard extends into the **navigation bar area**, and the system's grey navigation bar draws on top. What the user perceives as "lost space at the bottom" is likely the dashboard content being **clipped or obscured** by the system navigation bar overlay.

Additionally: `portraitDashboardHeight = maxWidth * 3 / 5` uses `maxWidth` (portrait = narrower dimension). This means the dashboard height is proportional to screen **width**, not height — which may leave excessive or insufficient space depending on aspect ratio (e.g., tall 21:9 screens get a relatively short dashboard with tons of map space above, while squarer screens get a taller dashboard).

### Possible Causes

| Cause | Likelihood | Detail |
|-------|-----------|--------|
| No edge-to-edge | **High** | No `enableEdgeToEdge()` → system bars overlay content |
| No insets padding | **High** | `fillMaxSize()` doesn't account for nav bar / status bar |
| Dashboard height formula | **Medium** | `maxWidth * 3/5` may not fit well across all aspect ratios |
| Map padding vs dashboard alignment | **Low** | Alignment is `BottomCenter` + fixed height, should match |

---

## 2. Grey Status Bar — Immersive Mode

### Current State

The theme `android:Theme.Material.Light.NoActionBar` renders a **light grey status bar** with dark icons (the "light" variant). This is the default Android look when no explicit status bar configuration is applied.

**No immersive/edge-to-edge code exists anywhere in the project** — confirmed by searching for `enableEdgeToEdge`, `WindowCompat`, `SYSTEM_UI_FLAG`, `systemUiVisibility`, `WindowInsetsController`, `statusBarColor`, `navigationBarColor`, `fitsSystemWindows`.

### What "Immersive" Means Here

The user likely wants the **map to extend behind the status bar** (edge-to-edge rendering) with either:
- **Transparent status bar** — map renders underneath, content is visible but system icons overlay it
- **Dark translucent status bar** — map shows through with a semi-transparent dark scrim for readability
- **Fully immersive (hide status bar)** — status bar hidden entirely (only appropriate for full-screen map modes)

### Anatomy of a Fix

| Layer | What to Do |
|-------|-----------|
| **Activity** (`MainActivity.kt`) | Call `enableEdgeToEdge()` in `onCreate()` **before** `setContent()` — this tells the system to draw behind system bars |
| **Theme** (`themes.xml`) | Change parent to `Theme.Material3.Dark.NoActionBar` (dark theme avoids the grey status bar) or keep light but add status bar color attributes. Or better: use `android:statusBarColor="@android:color/transparent"` and `android:navigationBarColor="@android:color/transparent"` |
| **Compose tree** (MapScreen) | Add `ConsumedInsetsModifier` or use `WindowInsets` APIs (`WindowInsets.systemBars.only(...)`) to avoid content being hidden behind the transparent bars |
| **API level handling** | `enableEdgeToEdge()` is Android 15+; for older APIs, fall back to `WindowCompat.setDecorFitsSystemWindows(window, false)` + `window.statusBarColor = Color.TRANSPARENT` |

### Recommended Approach

**Option A: True edge-to-edge (recommended)**
1. Add `enableEdgeToEdge()` in `MainActivity.onCreate()` — requires `androidx.activity:activity-ktx:1.9+`
2. Update theme to `Theme.Material3.Dark.NoActionBar` (dark status bar icons become white, matching the app's dark theme)
3. Add `WindowInsets` consumption in `MapScreen` to properly pad the map content away from the status bar while allowing the map to render underneath
4. The dashboard gets proper bottom insets, fixing the "lost space" problem simultaneously

**Option B: Dark status bar without full edge-to-edge (simpler)**
1. Set `window.statusBarColor = 0xFF1A1A2E` (matching dashboard background) in `MainActivity`
2. Set `window.navigationBarColor = 0xFF1A1A2E`
3. Use `ViewCompat.getWindowInsetsController(window.decorView)?.isAppearanceLightStatusBars = false` for light icons on dark background
4. The status bar becomes dark and visually integrated, but content still doesn't extend behind it

---

## Key Files for Implementation

- `app/src/main/java/ykws/android/maro/MainActivity.kt` — `onCreate()`, add `enableEdgeToEdge()` or window flags
- `app/src/main/res/values/themes.xml` — theme parent and color attributes
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — `BoxWithConstraints` layout (lines 527-608), add insets-aware padding
- `build.gradle.kts` / `libs.versions.toml` — may need `androidx.activity:activity-ktx` version bump

---

> **Next Step:** Decide approach (A or B above), then switch to Code mode to implement.

