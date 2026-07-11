<!-- scope: feature -->
# Dashboard Tile Bottom Line — Font Size & Readability

**Feature:** Dashboard
**Active Subfeature:** tile bottom line
**Status:** Implemented (2026-07-11)
**Decision:** Moderate — 11.sp + textMutedBright (#B0BEC5)
**Context:** User wants the 3rd/bottom line (subtitle) of dashboard tiles to display with a bigger/more readable font.

---

## Current State

Each `DashboardCard` is a 3-line `Column`:

```
┌────────────────────┐
│   TITLE (13.sp)    │  ← SemiBold, textPrimary (#E0E0E0)
│                    │
│   VALUE (auto)     │  ← AutoSizeValue, weight(1f), Bold, 14–64.sp
│                    │
│ Subtitle (9.sp)    │  ← Medium, textMuted (#90A4AE)  ← THIS LINE
└────────────────────┘
```

[`DashboardPanel.kt:239-247`](app/src/main/java/ykws/android/maro/ui/map/DashboardPanel.kt:239):

```kotlin
Text(
    text = subtitle ?: "",
    color = subtitleColor,          // defaults to DashboardColors.textMuted
    fontSize = 9.sp,
    fontWeight = subtitleWeight,    // defaults to FontWeight.Medium
    maxLines = 1,
    overflow = TextOverflow.Ellipsis,
    textAlign = TextAlign.Center
)
```

**9.sp** is the smallest text in the entire dashboard — ~12dp line height. On a typical phone at arm's length on a boat in sunlight, this is hard to read.

### Typical subtitle content
| Tile | Subtitle examples |
|------|-------------------|
| Distance | `54 m de la côte`, `1.2 km de la côte`, `→ 320 m to zone` |
| Speed (Zone) | `5 kn · to open sea · 1.8 km`, `3 kn · inside zone`, `Aucune limite` |
| Depth | `Litto3D · 90%`, `EMODnet · 60%`, `—` |
| Speed (boat) | `nœuds`, `demo mode` |

---

## Constraint Analysis

The value (line 2) uses `Modifier.weight(1f)`, so it takes **all remaining vertical space** after title + subtitle are measured. Any increase in subtitle height shrinks the value space.

**Space budget per card (portrait, 360dp-wide screen, 216dp dashboard height):**
- Card height ≈ 100dp (half of dashboard, minus padding/gap)
- Title: ~17dp (13.sp line height + internal leading)
- Subtitle: ~12dp (9.sp)
- Value: ~71dp remaining
- Bumping subtitle to 12.sp → ~16dp → value loses ~4dp (5.6% of value area)

**Landscape** (dashboard = full height, narrower cards): cards are taller (~200dp+), so the impact is even smaller.

The AutoSizeValue formula (`byHeight = maxHeight * 0.82f`) has enough headroom to absorb a 4-5dp reduction without noticeable value shrinkage.

---

## Options

### Font Size

| Option | Size | Line Height | Value Space Loss | Readability gain |
|--------|:----:|:-----------:|:----------------:|:-----------------|
| **A** (minimal) | 10.sp | ~13dp | ~1dp (1.4%) | Barely perceptible |
| **B** (moderate) | 11.sp | ~15dp | ~3dp (4.2%) | Noticeable, still clearly subordinate |
| **C** (bold) | 12.sp | ~16dp | ~4dp (5.6%) | Clear step up, approaches title weight |
| **D** (match title) | 13.sp | ~17dp | ~5dp (7%) | Equal to title — blurs hierarchy |

### Color

| Option | Color | Contrast ratio (on #16213E) | Effect |
|--------|-------|:--------------------------:|--------|
| Keep `textMuted` | `#90A4AE` | ~3.5:1 | Current — grey, low contrast |
| Brighter muted | `#B0BEC5` | ~5:1 | Better contrast, still subordinate |
| `textPrimary` | `#E0E0E0` | ~10:1 | Maximum readability, but competes with value |

### Font Weight

Current: `Medium` (500). Options:
- Keep `Medium` — already appropriate for secondary text
- `SemiBold` (600) — would make subtitle compete with title visually
- `Normal` (400) — step backward

Weight is already at `Medium`; bumping further risks hierarchy confusion.

---

## Recommendation: Option B + Brighter Muted

```
fontSize: 9.sp → 11.sp
color: textMuted (#90A4AE) → #B0BEC5 (or textPrimary if contrast preferred)
fontWeight: Medium (unchanged)
```

**Rationale:**
- **11.sp** is the sweet spot — +2.sp is clearly visible without stealing meaningful value space (~3dp). At 12.sp+ the subtitle starts looking like a second title.
- **Brighter muted** (#B0BEC5) improves contrast from ~3.5:1 to ~5:1 without making the subtitle compete with the bright white value. If maximum-at-a-glance readability is the priority, `textPrimary` (#E0E0E0) is the alternative — but it changes the visual hierarchy.
- **Medium** weight is correct — it's already set; no change needed.
- The title was already bumped to 13.sp + textPrimary — this keeps the 3-tier hierarchy intact: title (13.sp, bright) > value (auto, bold, bright) > subtitle (11.sp, medium-bright).

### Alternative: Option C + textPrimary

If you want the subtitle to be **unambiguously readable at a glance** (sunlight, moving boat), go with 12.sp + `textPrimary`. The visual hierarchy flattens slightly but every line becomes instantly readable.

---

## Implementation

Single-file change in [`DashboardPanel.kt`](app/src/main/java/ykws/android/maro/ui/map/DashboardPanel.kt:242):

```kotlin
// Line 242 — change fontSize and optionally color
fontSize = 11.sp,   // was 9.sp
```

If changing color, also update the default parameter at line 185:
```kotlin
subtitleColor: Color = DashboardColors.textPrimary,  // was DashboardColors.textMuted
```

No string resource changes, no layout changes, no other files touched.
