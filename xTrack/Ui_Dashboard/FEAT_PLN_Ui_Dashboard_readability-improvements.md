# Dashboard Readability — Space Management & Readability Improvements

> **Feature:** Dashboard
> **Subfeature:** readability
> **Context:** [`DashboardPanel.kt`](app/src/main/java/ykws/android/maro/ui/map/DashboardPanel.kt) — 2×2 card grid (Distance, Zone300, Depth, Speed)

---

## Current State Analysis

### Layout topology
```
Box (background=#1A1A2E, padding 12.h × 10.v)
  └── Column (weight=1f, spacing=8.dp)
       ├── Row (weight=1f, spacing=8.dp)
       │    ├── DistanceCard  (weight=1f)
       │    └── Zone300Card   (weight=1f)
       ├── Row (weight=1f, spacing=8.dp)
       │    ├── DepthCard     (weight=1f)
       │    └── SpeedCard     (weight=1f)
       └── ValidationBadge (conditional, 10.sp)
```

Each card:
```
CardBox (RoundedCornerShape(10.dp), padding 8.h × 6.v)
  ├── title       → 10.sp, FontWeight.Medium, textMuted (#90A4AE)
  ├── value       → AutoSizeValue (14..64sp clamp), FontWeight.Bold, textPrimary (#E0E0E0)
  └── subtitle    → 9.sp, FontWeight.Normal, textMuted (#90A4AE)
```

### Value sizing (AutoSizeValue)
```kotlin
val byHeight = maxHeight.value * 0.82f
val byWidth  = maxWidth.value * 1.5f / text.length.coerceAtLeast(1)
val fontSize = minOf(byHeight, byWidth).coerceIn(14f, 64f)
```

`byWidth` is inversely proportional to `text.length`. A short string like `5.2 kn` (6 chars) renders larger than `12.5 kn` (7 chars), causing size-jitter on updates.

### Padding waste analysis (360dp screen)

```
|←12→|←8→| CONTENT (148dp) |←8→|←8→| CONTENT (148dp) |←8→|←12→|
```

**41% of screen width** consumed by padding. Only 148dp per card for the value.

---

## Final Format Decisions

| Card | Rule | Format string | Example | Length |
|------|------|:-------------:|---------|:------:|
| Speed | ≤ 99.9 kn | `%4.1f kn` | ` 5.2 kn` / `12.5 kn` | 7 |
| Speed | > 99.9 kn | dash `—` | `—` | 1 |
| Depth | < 100m | `%4.1f m` | ` 1.2 m` / `12.5 m` / `99.9 m` | 6 |
| Depth | ≥ 100m | localized "Deep!" | `Deep!` (EN) / `Fond!` (FR) | 5–6 |
| Distance | < 1000m | `%4.0f m` | ` 250 m` / ` 999 m` | 6 |
| Distance | 1.0–9.9 km | `%.1f km` | `3.3 km` | 6 |
| Distance | ≥ 10 km | `%d km` | `18 km` | 5 |

### Rationale

- **Speed > 99.9 kn** (≈185 km/h) — unrealistic for any recreational vessel. Sanity gate → dash.
- **Depth ≥ 100m** — once past 100m you're in deep open water; the exact value adds little tactical value. Localized tag.
- **Distance ≥ 10 km** — drop decimal, show whole km. `18 km` is cleaner than `18.1 km`.

---

## Padding Reduction

| Element | Current | Proposed | Saving |
|---------|:-------:|:--------:|:------:|
| Outer panel (horizontal) | 12.dp | **4.dp** | 8dp × 2 sides |
| Outer panel (vertical) | 10.dp | **2.dp** | 8dp × 2 sides |
| Card interior (horizontal) | 8.dp | **4.dp** | 4dp × 2 sides × 4 cards |
| Card interior (vertical) | 6.dp | **2.dp** | 4dp × 2 sides × 4 cards |
| Grid row/column spacing | 8.dp | **4.dp** | 4dp |
| Corner radius | 10.dp | **8.dp** | 2dp |

New math: `4+4+4+4+4+4 = 24dp` waste → **168dp per value** (+14%).

---

## Font Weight Bumps

| Element | Current | Proposed |
|---------|:-------:|:--------:|
| Value | `Bold` | `Bold` (keep) |
| Title | `Medium` | **`SemiBold`** |
| Subtitle | `Normal` | **`Medium`** |

---

## Implementation Plan

### Files to modify

| File | Change |
|------|--------|
| [`app/src/main/res/values/strings.xml`](app/src/main/res/values/strings.xml) | Add padding to format strings, add new string resources |
| [`app/src/main/res/values-fr/strings.xml`](app/src/main/res/values-fr/strings.xml) | Add French deep-water tag |
| [`app/src/main/java/ykws/android/maro/ui/map/DashboardPanel.kt`](app/src/main/java/ykws/android/maro/ui/map/DashboardPanel.kt) | Padding reductions, font weight bumps, speed gate, depth gate, smart km logic |

### `values/strings.xml` changes

```xml
<!-- Format strings with width padding -->
<string name="dash_value_m">%4.0f m</string>             <!-- was %.0f m -->
<string name="dash_value_depth_m">%4.1f m</string>        <!-- was %.1f m -->
<string name="dash_value_kn">%4.1f kn</string>            <!-- was %.1f kn -->
<!-- dash_value_km left as-is: %.1f km -->

<!-- New string resources -->
<string name="dash_value_km_int">%d km</string>
<string name="dash_value_depth_m_int">%d m</string>
<string name="dash_depth_deep">Deep!</string>
```

### `values-fr/strings.xml` changes

```xml
<string name="dash_value_km_int">%d km</string>
<string name="dash_value_depth_m_int">%d m</string>
<string name="dash_depth_deep">Fond!</string>
```

### `DashboardPanel.kt` changes

1. **Outer panel padding** (line 93): `12.dp` → `4.dp` (h), `10.dp` → `2.dp` (v)

2. **Grid row spacing** (line 103): `8.dp` → `4.dp`
   **Grid column spacing** (line 109): `8.dp` → `4.dp`

3. **Card corner radius** (lines 183, 187): `10.dp` → `8.dp`

4. **Card internal padding** (line 190): `8.dp` → `4.dp` (h), `6.dp` → `2.dp` (v)

5. **Title font weight** (line 201): `FontWeight.Medium` → `FontWeight.SemiBold`

6. **Subtitle font weight** (line 218, default param): `FontWeight.Normal` → `FontWeight.Medium`

7. **`distanceText()` smart km logic** (line 254):
   ```kotlin
   @Composable
   private fun distanceText(distanceM: Double): String {
       val km = distanceM / 1000.0
       return if (distanceM >= 1000.0) {
           if (km >= 10.0) stringResource(R.string.dash_value_km_int, km.toInt())
           else stringResource(R.string.dash_value_km, km)
       } else {
           stringResource(R.string.dash_value_m, distanceM)
       }
   }
   ```

8. **`SpeedCard` > 99.9 kn gate** (line ~438): if `speedKnots > 99.9f`, show dash instead:
   ```kotlin
   if (speedKnots == null || speedKnots > 99.9f) {
       DashboardCard(
           title = stringResource(R.string.dash_speed_title),
           value = stringResource(R.string.dash_empty),
           subtitle = if (speedKnots == null) stringResource(R.string.dash_demo_mode) else null,
           modifier = modifier
       )
       return
   }
   ```

9. **`DepthCard` ≥ 100m gate** (line ~400): insert after the null/no-data check, before the numeric render:
   ```kotlin
   val depthM = depthSample.depthM
   if (depthM >= 100f) {
       DashboardCard(
           title = stringResource(R.string.dash_depth_title),
           value = stringResource(R.string.dash_depth_deep),
           cardColor = DashboardColors.cardBg,
           modifier = modifier
       )
       return
   }
   ```

---

## Expected Outcome

- Values render **14% larger** due to reclaimed padding
- **No size-jitter** — consistent character widths per card type
- **Speed > 99.9 kn** → clean dash instead of garbled overflow
- **Depth ≥ 100m** → localized "Deep!" / "Fond!" tag
- **Distance ≥ 10 km** → clean whole-number `18 km` format
- **Bolder hierarchy** — title easier to scan, subtitle still secondary
