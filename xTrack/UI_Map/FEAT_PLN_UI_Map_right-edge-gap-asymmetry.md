<!-- scope: feature -->
# Right-Edge Controls Gap Asymmetry — Root Cause Analysis

## Problem

After the immersive ui rework (which enabled `enableEdgeToEdge()` and removed blanket `WindowInsets.systemBars` from the root Box), the gap between the right-edge control stack (Settings gear, Layer fan, Zoom +/-) and the **bottom** of the map is visibly larger than the gap from the controls to the **top** of the map.

The controls should be symmetrically positioned — equal gap top and bottom.

---

## Layout Hierarchy (portrait, simplified)

```
Root Box (MapScreen.kt:503)
  └─ fillMaxSize()
  └─ .padding(bottom = 6.dp)                        ← 🔴 BOTTOM-ONLY PADDING
     │
     BoxWithConstraints (line 539)
     └─ fillMaxSize()                                ← fills root minus 6dp bottom
        │
        MapContent (line 581)
        └─ modifier = Modifier.fillMaxSize()
           .padding(bottom = portraitDashboardHeight) ← space for dashboard
           │
           Box (line 765)
           └─ .clipToBounds()
              │
              ├── CoastlineMapView (line 787)
              │   └─ Modifier.fillMaxSize()           ← MAP fills the entire clip area
              │
              ├── Top-left icons Row (line 815)
              │   └─ .windowInsetsPadding(WindowInsets.statusBars)  ← status bar only
              │
              └── Right-edge control Column (line 938)
                  └─ .align(Alignment.CenterEnd)
                  └─ .fillMaxHeight()
                  └─ .windowInsetsPadding(WindowInsets.systemBars)  ← 🔴 KEY LINE
                  └─ .padding(start = 12.dp, end = 6.dp)
                  └─ verticalArrangement = Arrangement.SpaceBetween
```

---

## Root Cause Analysis

### 1. `WindowInsets.systemBars` is inherently asymmetric

```kotlin
WindowInsets.systemBars = WindowInsets.statusBars + WindowInsets.navigationBars
```

On the right-edge Column at line 942, this single modifier applies:

| Inset | Applied edge | Typical height |
|---|---|---|
| `statusBars` | **Top** of the Column | ~24–48dp (varies with notch/punch-hole) |
| `navigationBars` | **Bottom** of the Column | ~48dp (3-button) or ~0–16dp (gesture) |

With `verticalArrangement = Arrangement.SpaceBetween`:

- The **top control** (Settings gear) sits `statusBarHeight` below the map's top edge
- The **bottom control** (Zoom -) sits `navigationBarHeight` above the map's bottom edge

On a device with 3-button navigation (~48dp nav bar, ~24dp status bar):
- **Top gap** = 24dp
- **Bottom gap** = 48dp → **2× larger**

### 2. `padding(bottom = 6.dp)` on the root Box makes it worse

At line 506, the root Box has `.padding(bottom = 6.dp)`. This was likely added as a hotfix to nudge content upward, but it only affects the bottom. The root Box's `fillMaxSize()` is now 6dp shorter at the bottom, effectively shifting everything up:

- **Bottom gap** = `navigationBarHeight` + 6dp = **54dp** (on 3-button nav)
- **Top gap** = `statusBarHeight` = **~24dp**

The ratio becomes **54:24 = 2.25×** — a very noticeable asymmetry.

### 3. Before the immersive rework

Previously, the root Box had `.windowInsetsPadding(WindowInsets.systemBars)`, so the **entire content area** (map + controls) was uniformly inset from both system bars. The right-edge Column had no additional insets. Both the map top and map bottom were equally padded by `systemBars`, so the controls appeared centered in the available area with no gap asymmetry.

After the rework (per the [`Ui_General`](xTrack/Ui_General/FEAT_DSC_Ui_General.md:74) feature spec):
- Root `systemBars` removed → map fills full screen behind system bars
- Right-edge controls got `systemBars` → controls are pushed away from system bars
- But the map itself has **no insets** → map content extends further at the bottom (nav bar area) than at the top (status bar area), creating the visual gap mismatch

---

## Visual Diagram

```
┌──────────────────────────────────┐
│  Map (fills full screen)         │
│                                  │
│  ╔══════════════════════════╗    │
│  ║  statusBar gap (24dp)    ║    │  ← top gap
│  ║                     ┌──┐ ║    │
│  ║                     │⚙️│ ║    │  Settings gear (TOP)
│  ║                     │  │ ║    │
│  ║                     │🍔│ ║    │  Layer fan (MIDDLE, weight=1)
│  ║                     │  │ ║    │
│  ║                     │➕│ ║    │
│  ║                     │➖│ ║    │  Zoom - (BOTTOM)
│  ║                     └──┘ ║    │
│  ║  navigationBar gap (48dp)║    │  ← bottom gap (2× larger)
│  ║  + root bottom 6dp       ║    │  ← extra gap
│  ╚══════════════════════════╝    │
│                                  │
│  ┌─ Dashboard (portrait) ──────┐ │
│  └─────────────────────────────┘ │
└──────────────────────────────────┘
```

---

## Fix Options

### Option A — Split insets: top gets `statusBars`, bottom gets `navigationBars`

Instead of applying `WindowInsets.systemBars` to the whole Column, split the Column into a top section (with `statusBars`) and a bottom section (with `navigationBars`), with the MIDDLE section (Layer fan) using `Modifier.weight(1f)` in between.

**Pros:** Symmetrical gaps if `statusBars == navigationBars` — the controls sit exactly `statusBarHeight` from top and `navigationBarHeight` from bottom.
**Cons:** May not be perfectly centered if heights differ.

### Option B — Use `WindowInsets.statusBars` only, remove root `bottom = 6.dp`

Remove `WindowInsets.navigationBars` from the controls entirely. Only use `WindowInsets.statusBars` at the top. Remove the `bottom = 6.dp` from root Box.

**Pros:** Top and bottom gaps would be equal (both = `statusBarHeight`). Simple change.
**Cons:** On gesture nav, bottom controls may overlap with the gesture handle area.

### Option C — Apply `systemBars` at the `BoxWithConstraints` level instead of the Column

Move `WindowInsets.systemBars` from the right-edge Column up to the `BoxWithConstraints` or the `MapContent` outer Box. Then remove it from the Column entirely.

**Pros:** Returns to the pre-immersive-rework behavior: the entire content area (map + controls) is uniformly inset. Controls are centered within the inset area → symmetric gaps.
**Cons:** Reduces map drawing area; the map wouldn't draw behind nav bar anymore (partially reverting the immersive rework goal).

### Option D — Targeted fix: remove `bottom = 6.dp` and match bottom to top inset

1. Remove `.padding(bottom = 6.dp)` from root Box (line 506)
2. Replace `.windowInsetsPadding(WindowInsets.systemBars)` on the Column (line 942) with a manual approach that applies equal padding at top and bottom

The simplest version: apply only `statusBars` (not `systemBars`) and rely on the bottom overlays (loading/error/exit toast) which already have `.windowInsetsPadding(WindowInsets.navigationBars)` to handle the nav bar. The right-edge controls would then have:

```
.windowInsetsPadding(WindowInsets.statusBars)  // top gap = statusBar height
```

But this would make the bottom gap = 0, which is wrong.

**Better:** Use a layout where top and bottom get the SAME padding. Instead of `systemBars`, use a fixed or derived symmetric value:

```kotlin
// Apply the maximum of statusBar and navBar at both top and bottom
val maxInset = max(statusBarHeightDp, navBarHeightDp)
// ... use as symmetric padding
```

---

## Recommended Approach

**Option D variant** — the cleanest behavioural fix:

1. **Remove** `.padding(bottom = 6.dp)` from root Box (line 506) — this was a band-aid, not a solution
2. **Restructure** the right-edge control Column to apply `statusBar` top-inset and `navigationBar` bottom-inset **independently**, then add a synthetic symmetric padding equal to `min(statusBar, navigationBar)` at the opposite edge to make gaps match

But the simplest correct fix that preserves the immersive feel:

**Replace** the Column's `.windowInsetsPadding(WindowInsets.systemBars)` with **separate** top and bottom padding using a `Box` wrapper:

```kotlin
// Right-edge control stack
Box(
    modifier = Modifier
        .align(Alignment.CenterEnd)
        .fillMaxHeight()
        .windowInsetsPadding(WindowInsets.systemBars)  // keeps controls clear of bars
) {
    // Inner Column uses SpaceBetween WITHOUT additional insets
    Column(
        modifier = Modifier.fillMaxHeight(),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // TOP, MIDDLE, BOTTOM sections
    }
}
```

Wait — this is functionally identical. The issue is that `systemBars` adds different padding at top and bottom.

**The actual fix** — make the gap symmetrical by taking the ***minimum*** of the two insets and applying it equally:

Or even simpler: use `WindowInsets.statusBars` for the top control section and `WindowInsets.navigationBars` for the bottom control section, keeping the MIDDLE section (weight=1) between them. This way:
- Top gap = `statusBarHeight`
- Bottom gap = `navigationBarHeight`
- The middle fan button is centered in the remaining space

This is actually the most natural approach — the controls are pushed apart by different amounts top and bottom, but each control sits at a consistent distance from its respective screen edge. The middle weight section absorbs the difference.

Let me refine this into the actual recommended fix.

---

## Recommended Implementation

**Changes in `MapScreen.kt`:**

### Change 1 — Root Box (line 506)
Remove `.padding(bottom = 6.dp)` — this was a reactive band-aid.

### Change 2 — Right-edge control stack (lines 938–1035)
Replace the single `windowInsetsPadding(WindowInsets.systemBars)` Column structure with a three-part layout where TOP gets `statusBars` padding and BOTTOM gets `navigationBars` padding, with MIDDLE (`weight(1f)`) between them.

```kotlin
// Right-edge control stack with symmetric insets
Column(
    modifier = Modifier
        .align(Alignment.CenterEnd)
        .fillMaxHeight()
        .padding(start = 12.dp, end = 6.dp),  // horizontal padding only
    verticalArrangement = Arrangement.SpaceBetween,
    horizontalAlignment = Alignment.CenterHorizontally
) {
    // ── TOP section: Settings gear ──
    //    Padded below status bar
    Column(
        modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SettingsButton(onClick = onOpenSettings)
    }

    // ── MIDDLE section: Layer fan ──
    //    Centered in remaining vertical space (weight = 1)
    Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ... FanLayout content ...
    }

    // ── BOTTOM section: Zoom +/- ──
    //    Padded above nav bar
    Column(
        modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ... Zoom buttons ...
    }
}
```

This gives:
- **Top gap** between Settings gear and map top edge = `statusBarHeight`
- **Bottom gap** between Zoom - and map bottom edge = `navigationBarHeight`
- Both controls are pushed clear of their respective system bars
- The Layer fan is vertically centered in the remaining space

If the goal is perfect visual symmetry (equal gaps top and bottom), this still won't match if `statusBarHeight != navigationBarHeight`. But it's the *semantically correct* layout — controls are pushed away from system bars by exactly the bar height on each side.

If you want **strict visual symmetry** regardless of bar heights, you'd need to compute the max and apply it equally, which would waste space on the smaller-bar side. That's a design choice.

