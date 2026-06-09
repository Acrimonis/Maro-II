# Dashboard Redesign Plan

## Objective

Redesign the bottom dashboard panel into visual gauge cards for quick reading of key indicators: distance from coast, distance to 300m zone with speed warning, and depth under the boat with data source.

## Design Decisions (Approved)

| Decision | Choice |
|---|---|
| Layout | Cards (3 visual indicator cards + bottom action row) |
| Depth gradient | Green (shallow/safe) → Red (deep/danger) |
| Zone 300m alert | Pulsing border/animation when INSIDE the zone |
| Validation badge | Separate element, not overlaid on depth card |
| Responsive breakpoint | 240dp width — collapse from row to stacked layout |

## Current Architecture

The existing [`DashboardPanel`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:226) is a private `@Composable` inside `MapScreen.kt`. It takes these parameters:

```kotlin
private fun DashboardPanel(
    state: CoastlineState,
    isWater: Boolean,
    distanceToShore: Double?,
    inZone300: Boolean,
    distanceToZone: Double?,
    depthSample: DepthSample?,
    validation: ValidationReport?,
    onGenerate: () -> Unit,
    onRegenerateBand: () -> Unit,
    modifier: Modifier = Modifier
)
```

All data is already flowing via [`CoastlineViewModel`](app/src/main/java/ykws/android/maro/ui/map/CoastlineViewModel.kt:58) and [`DepthViewModel`](app/src/main/java/ykws/android/maro/ui/map/DepthViewModel.kt:62) `StateFlow`s:

| Data | StateFlow | Provided by |
|---|---|---|
| Distance to coast (m) | `distanceToShore: StateFlow<Double?>` | CoastlineViewModel |
| Inside 300m zone? | `inZone300: StateFlow<Boolean>` | CoastlineViewModel |
| Distance to zone boundary (m) | `distanceToZone: StateFlow<Double?>` | CoastlineViewModel |
| Depth under boat | `depthAtCenter: StateFlow<DepthSample?>` | DepthViewModel |
| Depth metadata | `DepthSample.source`, `.confidence`, `.hasData` | DepthGrid model |

**No data-layer changes are needed** — purely a UI composable refactor.

## Proposed UI Layout

```
┌──────────────────────────────────────────────────┐
│ ┌──────────────┐ ┌──────────────┐ ┌────────────┐ │
│ │   📏         │ │   ⚠️         │ │   🌊       │ │
│ │   850 m      │ │  EN ZONE !   │ │  12.4 m    │ │
│ │   de la côte │ │  300 m       │ │  Fond      │ │
│ │              │ │  5 nœuds max │ │  SHOM 92%  │ │
│ └──────────────┘ └──────────────┘ └────────────┘ │
│              ✓ Données validées (RMSE 1.2 m)     │
│              [ Côte ]  [ 🌍↕️ ]                  │
└──────────────────────────────────────────────────┘
```

### Landscape (dashboard left panel)
- Cards fill the full width, stacked vertically
- Each card spans the full panel width
- Text is larger, more breathing room

### Portrait (dashboard bottom bar)
- 3 cards in a horizontal `Row` with equal `weight(1f)`
- Text scales down proportionally
- If total width < 240dp per card-equivalent, switch to compact layout

## Component Tree

```
MapScreen
 └─ DashboardPanel (extracted to own file)
     ├─ DashboardCard (reusable composable)
     │   ├─ DepthCard        ← green→red gradient
     │   ├─ Zone300Card      ← pulsing animation when inZone
     │   └─ DistanceCard     ← conditional label
     ├─ ValidationBadge      ← separate element
     └─ ActionRow (FlowRow)
         ├─ Generate Button
         └─ Earth/Water Toggle
```

## Implementation Steps

### Step 1: Extract DashboardPanel into standalone file

Create `app/src/main/java/ykws/android/maro/ui/map/DashboardPanel.kt`

- Move the entire `DashboardPanel` composable + its helper functions (`depthReadoutColor`, `depthSourceLabel`, any formatting utils)
- Keep the same function signature so `MapScreen.kt` call sites don't break
- Make it `internal` (package-private) rather than `private`

### Step 2: Create reusable DashboardCard composable

```kotlin
@Composable
fun DashboardCard(
    title: String,
    value: String,
    subtitle: String? = null,
    color: Color,
    modifier: Modifier = Modifier
)
```

- Rounded rectangle shape with background fill
- Title text (small, muted)
- Value text (large, bold, colored)
- Optional subtitle line (small, muted)
- Responsive: in narrow mode (< 240dp per card), omit title and use shorter value format

### Step 3: Build DepthCard

- Uses `DashboardCard` with depth value as primary metric
- Background color: interpolate between `Color(0xFF4CAF50)` (green, 0m) → `Color(0xFFFFEB3B)` (yellow, ~20m) → `Color(0xFFF44336)` (red, 60m+)
- Subtitle line: `"${source.label} · ${confidence}%"`
- Edge case: show `"—"` when `depthSample == null || !depthSample.hasData`

### Step 4: Build Zone300Card

- Uses `DashboardCard`
- When `inZone300 == true`:
  - Background: `Color(0xFFB71C1C)` (dark red)
  - Value: `"EN ZONE !"`
  - Subtitle: `"${distanceToBoundary} — 5 nœuds max"`
  - Pulsing border animation: `infiniteTransition` on border color alpha
- When `inZone300 == false`:
  - Background: muted dark
  - Value: `"${distanceToZone}"`
  - Subtitle: `"de la zone 300m"`
- Edge case: show `"—"` when `distanceToZone == null`

### Step 5: Build DistanceCard

- Uses `DashboardCard`
- Value: formatted distance (m or km)
- Subtitle: `"de la côte"` if `isWater`, `"de la mer"` if `!isWater`
- Edge case: show `"—"` when `distanceToShore == null`

### Step 6: Add ValidationBadge

- Separate composable below the cards row
- Green check + `"Données validées (RMSE %.1f m)"` when `validation.passed`
- Orange warning + `"Validation incomplète (RMSE %.1f m)"` otherwise
- Hidden when `validation == null`

### Step 7: Responsive layout in DashboardPanel

```kotlin
val width = maxWidth

if (width > 240.dp * 3 + spacing) {
    // Wide enough for 3 cards in a row
    Row {
        DepthCard(Modifier.weight(1f))
        Spacer
        Zone300Card(Modifier.weight(1f))
        Spacer
        DistanceCard(Modifier.weight(1f))
    }
} else if (width > 240.dp) {
    // Medium: 2 cards per row, wrap
    FlowRow {
        DepthCard(Modifier.weight(1f))
        Zone300Card(Modifier.weight(1f))
        DistanceCard(Modifier.fillMaxWidth())
    }
} else {
    // Narrow: all cards stacked
    Column {
        DepthCard(Modifier.fillMaxWidth())
        Zone300Card(Modifier.fillMaxWidth())
        DistanceCard(Modifier.fillMaxWidth())
    }
}
```

### Step 8: Wire StateFlow data

No changes needed to `MapScreen.kt` call sites — `DashboardPanel` already receives all required parameters. The extraction into its own file keeps the same signature.

## Color Palette

| Element | Color | Hex |
|---|---|---|
| Dashboard background | Dark navy | `#1A1A2E` |
| Card background (default) | Slightly lighter navy | `#16213E` |
| Depth safe (0m) | Green | `#4CAF50` |
| Depth mid (~20m) | Yellow | `#FFEB3B` |
| Depth danger (60m+) | Red | `#F44336` |
| Zone 300 ALERT | Dark red | `#B71C1C` |
| Zone 300 normal | Muted gray-blue | `#37474F` |
| Text primary | Light gray | `#E0E0E0` |
| Text muted | Blue-gray | `#90A4AE` |
| Validation OK | Green | `#66BB6A` |
| Validation WARN | Orange | `#FFA726` |

## Edge Cases & Error States

| State | Behavior |
|---|---|
| `CoastlineState.Loading` | All cards show shimmer/skeleton, buttons disabled |
| `CoastlineState.Idle` | Dashboard shows muted placeholder text — "Générer la côte" |
| `depthSample == null` | DepthCard shows `"—"` as value |
| `distanceToShore == null` | DistanceCard shows `"—"` as value |
| `distanceToZone == null` | Zone300Card shows `"—"` as value (no band built) |
| `validation == null` | ValidationBadge hidden entirely |
| Very narrow width (< 240dp) | Cards stack vertically, no title, compact value only |
| Landscape (left panel, ⅔ screen height) | Cards fill full width, stacked |
