<!-- scope: feature -->
# Limit colour language — making the ramp speak for the 5 and 10 kn edges

Design discussion, opened 2026-09-19. Nothing here is shipped or decided; it records the options and the
questions that choose between them.

## 1. The goal

Two enforcement limits, 5 and 10 kn, and a wanted visual clue for the moment speed goes slightly across
either edge. The ramp currently answers "how fast" continuously; the question is whether it can also answer
"over or under", and at which surface.

## 2. What the shipped ramp does for this goal

- Its loudest steps sit beside the limits rather than on them: green to blue lands at 6–7 kn and blue to
  amber at 12–13, so the line announces a change about a knot *after* a 5 kn crossing and again past 10 —
  a false cue at 6.5 and no cue at either limit.
- Both limits fall inside a gradient family (4–6 and 9–12), whose shades step by a knot, so nothing about
  the colour says "limit" rather than "a bit faster".
- The legend prints 5 and 10 deliberately off their own boundaries (at the 7 and 13 kn rows), because those
  were the ramp's boundaries; a ramp whose edges moved onto the limits would want those rows moved too.

## 3. The two mechanisms

- **A — threshold edges in the ramp.** Move the boundaries so colour changes exactly at 5 and at 10, keep
  each zone flat or gently graded, and leave the rest of the ramp as it is. Cheap: a properties edit, no new
  data path. It answers about *absolute* speed, so it is silent about which limit applied where.
- **B — colour by ratio to the local limit.** The line's colour follows speed ÷ the limit in force at that
  point, so green at or under, amber to 1.4×, red beyond — the rule the Global Todos already state for the
  direction arrow. Limit-agnostic and honest, but it needs a per-point zone lookup the render path does not
  do today, so its cost is a design question rather than a given.

## 4. Channels a banded line has free

- **Hue** — what the ramp uses; the weakest at a glance on a thin line under glare.
- **Lightness** — the strongest and the most robust; it survives sunlight, a 12 dp line, the transparency
  slider and colour-blindness that hue alone does not.
- **Dash** — already spoken for: GAP seams are dashed, so a dashed over-limit stretch would read as a hole.
- **Geometry** — a notch or tick drawn where the line crosses a limit, which marks the *instant* rather than
  the state, and survives any palette. Not proposed by anyone yet; recorded as available.

## 5. Options and their objections

- **Zigzag lightness** (dark safe, light between, dark danger) — the biggest local jumps at both edges,
  which is what edge detection wants; objection: lightness stops ranking the whole ramp, so brightness no
  longer answers "how fast" across the full range.
- **Monotone lightness** — the ramp stays rankable end to end; objection: each edge carries a smaller jump,
  and the cue weakens exactly where it is needed.
- **Hard edges without damping** — a boat holding 4.9–5.1 kn flickers between the two shades, which is the
  jitter the 2026-09-19 step pass removed, and each flicker is another polyline; objection to damping it: a
  short hysteresis band bends the nearest-point colour rule the quantiser deliberately keeps.
- **Ratio colouring (B)** — answers the real question; objection: colouring 10–22 kn red cries wolf on a
  track run offshore where no limit exists, where a normal cruise would read as an alarm.

## 6. Open questions

1. Is the wanted cue **live** (this moment, while driving) or **historical** (where on the track I was over)?
   Live points at the boat sprite or the arrow; historical points at the ramp.
2. **Which limits** — the 5 kn of the 300 m band and the 10 kn of the regulated zones as fixed numbers, or
   whatever limit the boat was actually inside at that point?
3. **Where does normal cruising sit** — if 10–22 kn red would cover the boat's ordinary speed, the danger
   family cannot start at the lower limit without reading as a permanent alarm.
4. **Flicker tolerance at the edge** — is a colour that alternates as you hover on the limit a *cue* or a
   defect?

## 7. Settled in discussion (2026-09-19)

- Colour-blindness is not a constraint, so hue is free to carry the message.
- The thresholds are the fixed numbers 5 and 10 kn, not the limit in force at a point on the track.
- The cue is the ramp's own colour, and it should show a **transition with 25 % tolerance** at each threshold
  rather than an edge — which answers question 4 by construction: a gradient cannot flicker at a point.

## 8. The tolerance-band design

Palette fixed by discussion: green keeps the compliant zone below 5 kn, blue carries the zone between the
limits, and the warm chain above 15 kn is untouched. Each threshold is therefore a **hue change with a 25 %
tolerance band above it**, and the shipped changeover pairs simply move onto the limits they belong to:

| Family | Range (kn) | Colour | Role | Step | Shades |
|--------|-----------|--------|------|------|--------|
| 1 | 0–5 | `#1a6b1a` → `#9ee79e` | green — the compliant zone | 1.0 | 5 |
| 2 | 5–6.25 | `#9ee79e` → `#125b9b` | green to blue — the 5 kn crossing | 0.25 | 5 |
| 3 | 6.25–10 | `#125b9b` → `#b5d8f6` | blue — between the limits | 0.5 | 7 |
| 4 | 10–12.5 | `#b5d8f6` → `#ffd164` | blue to amber — the 10 kn crossing | 0.5 | 5 |
| 5 | 12.5–22 | `#ffd164` → `#EF6C00` | amber to orange, the shipped warmth | 1.0 | 9 |
| 6 | 22–32 | `#EF6C00` → `#751212` | orange to dark red, as shipped | 1.0 | 10 |
| 7 | 32–70 | `#751212` → `#6A1B9A` | dark red to purple, as shipped | 2.0 | 19 |

The two crossing families reuse the shipped pairs — `#9ee79e`→`#125b9b` was the 6–7 kn changeover, and
`#b5d8f6`→`#ffd164` the 12–13 one — so the hue language does not change, only where each lives and on what
grid: a knot and a quarter wide, resolved on a fine step, which is what makes the crossing visible without
flickering. Above the warm zone the colours and the boundaries stay as shipped; the steps above 15 kn are the
2026-09-19 re-cut, not the original grid.

About sixty shades against the forty-seven shipped, the increase sitting in the compliant zone and the two
crossings. The legend's 5 and 10 rows would move onto the limits they name, retiring the off-boundary
placement the tick table was written with.

## 9. Settled shape, and the property set it needs

Settled 2026-09-19: each tolerance band sits **above** its limit, so the limit itself is the crisp moment,
and the band's ceiling is the **integer above the 25 % figure** — 5 × 1.25 = 6.25 becomes 7 kn, and
10 × 1.25 = 12.5 becomes 13 kn, so the two bands span 2 and 3 kn. The rounding is worth recording for what it
does to the proportions: 7 kn is 40 % above the 5 kn limit and 13 kn is 30 % above the 10 kn one, so the 25 %
survives as the rule that generated the ceilings rather than as the tolerance each band ends up holding.

### 9.1 Proposed keys — the grid carries the ladder

| Family | Ceiling (kn) | Meaning | From → to | Step | Shades |
|--------|--------------|---------|-----------|------|--------|
| 1 | 5 | compliant, below limit 1 | `#1a6b1a` → `#228b22` | 1.0 | 6 |
| 2 | 7 | the 5 kn crossing — the tolerance band | `#228b22` → `#125b9b` | 0.25 | 8 |
| 3 | 10 | acceptable, up to limit 2 | `#125b9b` → `#187ad0` | 0.5 | 6 |
| 4 | 13 | the 10 kn crossing — the tolerance band | `#187ad0` → `#ffd164` | 0.5 | 6 |
| 5 | 22 | over, the shipped warmth | `#ffd164` → `#EF6C00` | 1.0 | 9 |
| 6 | 32 | as shipped | `#EF6C00` → `#751212` | 1.0 | 10 |
| 7 | 70 | as shipped | `#751212` → `#6A1B9A` | 2.0 | 19 |

Seven families inside the nine the bound allows, about 64 shades against the 47 shipped — the increase
sitting in the two crossing bands, whose fine grids (0.25 and 0.5 kn) are what make the change read as travel
rather than as a jump. The legend's rows move onto the limits they name,
`scaleTicks=5:5,10:10,22:20,30:30,35:35`, retiring the off-boundary placement; `scaleMinKn=2` stays.

Three files move together, which is why this is a small pass rather than a values edit: the properties block,
`AppConfig`'s default ramp that mirrors it, and the two test classes that assert it.

### 9.2 The alternative, and its cost

The table above buries the two limits among seven ceilings and carries their meaning in comments. Naming
them as keys of their own — `track.heatmap.limit1Kn=5`, `track.heatmap.limit1.tolerancePct=30` and the pair
for 10 kn — would put the ladder where a reader looks for it, but then the ceilings must either be computed
from those keys, which is a parser change that removes `maxKn` from the first four families, or be written
twice, which the one-home-per-fact rule forbids.
