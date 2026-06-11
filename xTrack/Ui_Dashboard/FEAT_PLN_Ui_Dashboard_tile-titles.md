# Dashboard Tile Titles — Sizing & Prominence

**Feature:** Dashboard  
**Active Subfeature:** tile titles  
**Context:** User wants the first line (title/subtitle) of each dashboard card to be bigger and more prominent, without stealing space from the data value display.

## Current Layout

Each `DashboardCard` is a Column with three rows:

```
┌────────────────────┐
│   Title (10.sp)    │  ← small, subdued, SemiBold, textMuted (#90A4AE)
│                    │
│                    │
│   VALUE (auto)     │  ← AutoSizeValue, weight(1f), Bold, 14–64.sp
│                    │
│                    │
│ Subtitle (9.sp)    │  ← very small, Medium weight
└────────────────────┘
```

- Title: `fontSize = 10.sp`, `fontWeight = SemiBold`, `color = textMuted`
- Value: `weight(1f)` — fills remaining vertical space, auto-sizes from 14–64.sp
- Subtitle: `fontSize = 9.sp`, `fontWeight = Medium`, `color = textMuted`

## Constraint Analysis

Because the value uses `Modifier.weight(1f)`, the value takes **all remaining vertical space** after the title and subtitle are measured. Any increase in title height shrinks the value's available space proportionally.

**However:** the title is only 10.sp now (~13 dp line height). A bump to 12.sp (~16 dp) adds ~3 dp of height, which at worst reduces the value font size by ~1-2 sp in small card configurations. In practice, the `byHeight = maxHeight * 0.82f` formula in AutoSizeValue has enough headroom to absorb this.

## Options

| Option | Title Size | Title Weight | Title Color | Space Impact | Effect |
|--------|-----------|-------------|-------------|-------------|--------|
| **A** (light) | 11.sp | SemiBold | textMuted | negligible | Slightly larger, same styling |
| **B** (medium) | 12.sp | SemiBold | textPrimary | ~3 dp from value area | Noticeably larger, brighter color |
| **C** (bold) | 10.sp | **Bold** | textPrimary | none | Same size but bolder + brighter |
| **D** (combo) | 11.sp | SemiBold | textPrimary | ~1.5 dp from value area | Balanced — modest size bump + color lift |

## Recommendation: Option D

```
fontSize: 10.sp → 11.sp
color: textMuted → textPrimary
fontWeight: SemiBold (unchanged)
```

**Rationale:**
- +1.sp is barely perceptible in absolute terms but makes the title feel more present
- Changing from `textMuted` (grey) to `textPrimary` (white) has the biggest perceptual impact — the title stops being "ghost text"
- SemiBold is already appropriate for a label; Bold would compete with the value
- The ~1.5 dp of space taken from the value area is absorbed by AutoSizeValue's 14-64.sp range without noticeable change

## Note

The `subtitle` (third line, 9.sp) is left untouched — it's intentionally the quietest element.
