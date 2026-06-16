# Map Overlay Layout — Current Inventory & Planned Evolution

## Current Overlays (rendered in [`MapContent`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:730))

| # | Zone | Position | Inset | Content | Fixed/Configurable |
|---|---|---|---|---|---|
| 1 | Map | fills all | none | OSMdroid `MapView` with depth/coastline/zone overlays | always |
| 2 | Top-Start | `TopStart` | `statusBars` + 6dp start | GPS status icon (44×44) + EarthWater icon (44×44) | always |
| 3 | Center | `Center` | none | Boat marker (32dp@z12, zoom-scaled) + cap arrow + direction line | always |
| 4 | Bottom-Center | `BottomCenter` | `navigationBars` + 56dp/76dp h-pad | Loading overlay, Error overlay, Exit toast | conditional |
| 5 | Bottom-Start | `BottomStart` | `navigationBars` + 6dp start | Regulated zone icon stack (44×44 each) + info text | conditional |
| 6 | End Column | `CenterEnd` | `systemBars` + 12dp/6dp h-pad | **Settings** gear (64×64), **Layer fan** (anchor + 4 children), **Zoom** +/- (2× ~44dp) | always |
| 7 | Dashboard | `BottomCenter` (portrait) / `CenterStart` (landscape) | none (sized by formula) | Distance / Speed / Depth / Zone cards | always |
| 8 | Settings overlay | fills all | `statusBars` | Full-screen settings page (3 tabs) | conditional |

## Planned Additions (from feature files)

### Confirmed — will be added soon
| Feature | Addition | Where | Status |
|---|---|---|---|
| [`ArcLayout > fan-migration`](xTrack/ArcLayout/FEAT_DSC_ArcLayout.md:52) | **Second fan button** in the right-edge control stack | End Column, MIDDLE section (replaces the `Spacer(136.dp)` placeholder) | `[ ]` todo: "Add second fan button" |
| [`Ui_Map > layer-zone`](xTrack/UI_Map/FEAT_DSC_UI_Map.md:93) | Depth zone mask (6NM) | Map data layer — no UI addition | `[ ]` last todo: re-bake asset |
| [`Ui_Map > config 300m auto display`](xTrack/UI_Map/FEAT_DSC_UI_Map.md:121) | Per-mode auto-show toggles | Settings page only — no map overlay change | `[ ]` last todo: build |
| [`Ui_Map > layer-lowdepth`](xTrack/UI_Map/FEAT_DSC_UI_Map.md:145) | Pink-bleed fix | Map data layer — no UI addition | `[ ]` low-priority polish |
| [`Ui_Dashboard > readability`](xTrack/Ui_Dashboard/FEAT_DSC_Ui_Dashboard.md:119) | Padding/font/format tweaks | Dashboard cards — no layout change | `[ ]` pending |
| [`Ui_Dashboard > tweak`](xTrack/Ui_Dashboard/FEAT_DSC_Ui_Dashboard.md:66) | ZoneSituation migration | Dashboard + ViewModel — simplifies MapScreen params | `[ ]` pending |

### Possible future additions (discussed/designed)
| Feature | Addition | Where | Notes |
|---|---|---|---|
| [`rotate`](xTrack/UI_Map/FEAT_DSC_UI_Map.md:198) | Two-finger rotate in demo mode | Map orientation — needs a rotation-reset button maybe? | `[ ]` designed |
| [`hide-fix`](xTrack/ArcLayout/FEAT_DSC_ArcLayout.md:82) | Unknown | Unknown | `[ ]` empty shell |

## Key Architectural Observations

### 1. The right-edge control stack is the most volatile area
It currently has 3 controls (Settings, Layer fan, Zoom) but will soon have **4+** when the second fan is added. The `Spacer(136.dp)` at line 1016 is a hardcoded placeholder waiting to be replaced — proof the current system doesn't handle dynamic additions cleanly.

### 2. Three different inset strategies in play
| Strategy | Where | Problem |
|---|---|---|
| `systemBars` = statusBars + navigationBars | Right-edge Column | Different heights → asymmetric gap |
| `statusBars` only | Top-left Row, Settings overlay | Correct — top edge only |
| `navigationBars` only | Bottom overlays | Correct — bottom edge only |
| root `bottom = 6.dp` | Root Box | Band-aid, breaks symmetry |

### 3. The dashboard formula is hardcoded
```kotlin
val portraitDashboardHeight = maxWidth * 3 / 5
val landscapeDashboardWidth = maxHeight * 100 / 100
```
These work but are opaque. Any future dashboard resize means changing magic numbers.

### 4. `MapContent` has 27 parameters
The composable at line 730 passes through almost everything. The ZoneSituation migration (tweak subfeature) aims to reduce this.

---

## What This Means for the Layout Architecture

The layout system needs to handle:

1. **A right-edge edge-column that can grow** — from 3 items to 4+ items, with multiple fan buttons
2. **Correct per-edge insets** — top edge gets `statusBars`, bottom edge gets `navigationBars`
3. **Flexible middle section** — future controls will go in the middle (weight=1) area
4. **Dashboard overlay that may resize** — the 3/5 formula may need adjustment
5. **Consistent, non-magical spacing** — no more `bottom = 6.dp` or `Spacer(136.dp)`
