# Zone Tile State Normalization

## All Zone-Related States

### Inside Zone (current)

| # | State | Title | Value | Subtitle | Card |
|---|---|---|---|---|---|
| 3 | ✅ Inside — compliant | zone name | `5 kn` | `5 kn max — 120 m` | green |
| 4 | 🔴 Inside — over-speed | zone name | `5 kn` | `5 kn max — 120 m` | red |

### Approaching (current)

| # | State | Title | Value | Subtitle | Card |
|---|---|---|---|---|---|
| 5 | 🟦 Approaching — heading known | zone name | `↑1.2 km` | `5 nœuds · 45 s` | blue |
| 7 | 🟦 Approaching — no heading | ZONE | `800 m` | `BANDE 300M` | blue |

### Exiting (not yet implemented — new)

| # | State | Title | Value | Subtitle | Card |
|---|---|---|---|---|---|
| 8 | ❓ Exiting — just crossed boundary | ? | ? | ? | ? |

## Normalization Goal

The user wants consistent behavior across:
1. **Approaching** (outside, moving toward zone) — states 5 & 7
2. **Inside** — states 3 & 4
3. **Exiting** (outside, moving away from zone) — new state 8

## Questions to decide for each dimension

### Title
- Approaching: zone name (if heading known) or "ZONE" (generic)
- Inside: zone name
- Exiting: zone name? "ZONE"? Leave?

### Value (primary number)
- Approaching: distance to zone boundary (arrow prefix if heading known)
- Inside: speed limit
- Exiting: distance from boundary?

### Subtitle (secondary info)
- Approaching: speed limit + ETA (heading known) or zone name (no heading)
- Inside: exit distance
- Exiting: ? (speed limit? "exited"? "À l'extérieur"?)
