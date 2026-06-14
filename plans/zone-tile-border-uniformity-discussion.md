# Zone Tile — State Diff & Uniform Border Rule

## 1. State 4 vs State 6: The Fundamental Diff

Both states trigger when the boat is outside a zone but one is nearby. The axis of difference is **heading-awareness**.

### State 4 — Heading toward a zone

| Property | Detail |
|----------|--------|
| **Condition** | `headingAheadDistance != null` |
| **Distance metric** | **Heading-aware projection** — rays the boat's bearing forward, finds the intersection point with the zone polygon along that vector |
| **Title** | The specific detected zone's name (e.g., "Cap d'Antibes 10 kn") |
| **Primary value** | Distance ahead along the current course (m or km) |
| **Subtitle** | Speed limit + ETA (seconds or min:sec) |
| **Direction arrow** | ✅ Always — ↑ / ↗ → / → |
| **Priority** | **Checked first** (line 468) — takes precedence over State 6 |
| **Real-world meaning** | "If you keep going this way, you'll hit the zone in X meters / Y seconds" |
| **Data type** | `HeadingAheadResult` — struct with `distanceAheadM`, `etaSeconds`, `directionArrow`, `zoneName`, `speedLimitKn` |

### State 6 — Outside, zone ahead (no heading info)

| Property | Detail |
|----------|--------|
| **Condition** | `anyZoneNearby == true` AND `headingAheadDistance == null` |
| **Distance metric** | **Euclidean nearest-edge** — shortest straight-line distance from current position to any zone polygon edge |
| **Title** | Generic "ZONE" (or `activeName` if resolvable) |
| **Primary value** | Straight-line distance to nearest zone boundary |
| **Subtitle** | Zone name only |
| **Direction arrow** | ❌ None |
| **Priority** | **Fallback** — only reached if heading-ahead returned nothing |
| **Real-world meaning** | "There's a zone over there, Y meters away (but you may not be heading toward it)" |
| **Data type** | Raw `Double` (`distanceToSpeedZone` / `distanceToZone`) |

### Why it matters

- **Boat heading parallel to a zone boundary** → `headingAheadDistance` returns null (no intersection along the heading ray) → falls to **State 6**: shows nearby zone with straight-line distance.
- **Boat aiming directly at a zone** → `headingAheadDistance` returns the intersection → **State 4**: shows approach distance, ETA, and direction arrow.

This is why State 4 exists as a separate, higher-priority state: it provides **actionable navigation info** (ETA, direction) that State 6 cannot.

---

## 2. Remove Pulsing Border — Uniform Border Rule

### Current behavior

The pulsing border is a special case exclusive to `SpeedLimitCard`:

```
SpeedLimitCard — inside zone, exceeding speed limit
  → borderColor = DashboardColors.red.copy(alpha = pulseAlpha)
  → borderWidth = 2.dp
  → pulseAlpha animated 0.3 → 1.0, 800ms, infiniteRepeatable, Reverse
```

Lines 440–458 in [`DashboardPanel.kt`](../../app/src/main/java/ykws/android/maro/ui/map/DashboardPanel.kt:440).

No other tile (`DistanceCard`, `DepthCard`, `SpeedCard`) applies any border, animated or otherwise.

### Proposed: Uniform border rule

> **General rule:** When a dashboard tile's `cardColor` changes to an alert/caution/compliance state, the tile's **border** uses the **same color**, at **full opacity** (no transparency, no animation). This applies uniformly to **all four dashboard tiles**.

### What changes per tile

#### SpeedLimitCard (the only tile with border today)

| State | Current | After |
|-------|---------|-------|
| Inside zone, speed compliant | No border | Solid green border matching `zoneCompliant` |
| Inside zone, exceeding limit | Pulsing red animated border | Solid red border matching `zoneDanger` |
| All other states | No border | No border (unchanged) |

**Removed code:** The `rememberInfiniteTransition`, `animateFloat`, `pulseAlpha` — roughly 10 lines of animation infrastructure. The `borderColor` and `borderWidth` params become static:

```kotlin
// Before (pulsing):
val pulseAlpha = ...
borderColor = DashboardColors.red.copy(alpha = pulseAlpha),
borderWidth = 2.dp,

// After (static, same as cardColor):
borderColor = DashboardColors.zoneDanger,
borderWidth = 2.dp,
```

#### SpeedCard (no border today)

| State | Current | After |
|-------|---------|-------|
| Speed ≤ limit (green) | No border | Solid green border matching `speedSafe` |
| Limit < speed ≤ limit×1.4 (orange) | No border | Solid orange border matching `speedCaution` |
| Speed > limit×1.4 (red) | No border | Solid red border matching `speedDanger` |
| No zone / no data | No border | No border (unchanged) |

**Code change:** Pass `borderColor` and `borderWidth` to `DashboardCard` in the colored states.

#### DepthCard (no border today)

| State | Current | After |
|-------|---------|-------|
| Normal depth with color | No border | Solid border matching the `depthColor` at full opacity (same as `cardColor` ramp) |
| Deep water (≥100m, dull) | No border | Solid border matching `zoneNormal` |
| No data / on land | No border | Solid border matching `zoneNormal` |

**Design note:** The `depthColor` ramp already provides the card background at 25% alpha. The border would use the **same ramp color at 100% alpha** — making the border a clean, saturated visual edge while keeping the fill translucent.

#### DistanceCard (no border today)

| State | Current | After |
|-------|---------|-------|
| All states | No border | No border (unchanged — DistanceCard never changes cardColor) |

**Exception:** DistanceCard always uses `DashboardColors.cardBg` — it has no alert states. So it gets no border.

### Summary of removed vs added code

| Metric | Value |
|--------|-------|
| Animation logic removed | `rememberInfiniteTransition`, `animateFloat`, `infiniteRepeatable`, `tween(800ms)`, `pulseAlpha` — ~10 lines |
| `import` lines removable | `rememberInfiniteTransition`, `animateFloat`, `infiniteRepeatable`, `RepeatMode`, `tween`, `LinearEasing` |
| `borderColor`/`borderWidth` params added | ~6 lines across `SpeedCard` and `DepthCard` call sites |
| Net lines | ~12 removed, ~6 added = **~6 lines net reduction** |

### Why this is better

1. **Visual consistency** — all tiles follow the same rule. No special animated treatment for one tile.
2. **Less distraction** — no flashing element during navigation. The solid red background + solid red border already signals violation clearly.
3. **Simpler code** — removes an animation dependency and a special-case render path.
4. **Uniform design language** — border-as-cardColor-reinforcement is a simple, predictable pattern.

### Trade-off acknowledged

The pulsing border drew **extra urgency attention** to speed violations — the animation is inherently attention-grabbing in peripheral vision. Removing it means relying solely on the red background + red border combo, which is visually strong but static. Mitigation: the card background itself already switches to red (`zoneDanger`), and the solid red border reinforces without flicker.
