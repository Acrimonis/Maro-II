# Zone Info Text — Discussion

## Layout

Insert a column/panel between the warning-strip icons (bottom-left) and the zoom +/- buttons (bottom-right):

```
┌──────────────────────────────────────────┐
│                                          │
│                                          │
│                                          │
│                                          │
│                        [+]              │
│                        [-]              │
│ ⚓ 🚤 5                                   │
│ ⚡ 10 kn — Cap d'Antibes                 │
│ ⚓ No anchoring — Baie de Cannes         │
│ 🛬 Seaplane — forest fire water drawing  │
└──────────────────────────────────────────┘
```

The new text panel sits under the icon strip row, left-aligned, very small text (e.g. `fontSize=10.sp` or `11.sp`).

## Data Source

Derived from the same `regulatedZones.zones` list, filtered by `it.contains(boatPosition)` (same geo-fence as the warning strip). Each zone that contains the boat gets one line.

## Line Format Options

### Option A — Type + Name + Key info
```
{emoji} {name or short desc} — {key info}
```
Examples:
- `⚡ Cap d'Antibes — 10 nds`
- `⚓ No anchoring — Baie de Cannes`
- `🛬 Seaplane activity`
- `🚫 Accès interdit — Port de Cannes`
- `🚤 Mooring — Nice port`

### Option B — Type + speed/restriction only
```
{emoji} {speed} {zone type label}
```
Examples:
- `⚡ 10 nds — Zone de vitesse`
- `⚓ Mouillage interdit`
- `🛬 Zone d'hydravion`

### Option C — Full name + description (SHOM text)
```
{emoji} {name}: {short description}
```
Examples:
- `⚡ Cap d'Antibes: speed limited to 10 knots`
- `⚓ Cannes bay: anchoring prohibited`

## What to show per zone

| Field | Source | Notes |
|---|---|---|
| Emoji | `emojiForType(zone.zoneType)` | Same mapping as map overlay |
| Name | `zone.name` | SHOM "objnam" or seed name (may be empty) |
| Speed | `zone.speedLimitKn` | e.g. "10 nds" or "5 kn" |
| Description | `zone.description` | SHOM "inform" text — may be very long |
| Type label | `zone.zoneType` human-readable | e.g. "Speed limit", "No anchoring" |

## Decision points

1. **Show one line per display category or per zone?** The warning strip deduplicates by category+speed. For text, per-zone is more informative (you see all overlapping zones).

2. **Truncation?** Descriptions can be 200+ chars. Truncate at ~40 chars with `…`? Or show only name+speed?

3. **Visibility gate?** Show always when `regulatedZonesVisible && zones.isNotEmpty()`? Or only when boat is inside a zone?

4. **Icon row + text overlap?** On small screens, 4+ zone lines might overflow. Make the text panel scrollable vertically?

## Recommended approach

**Per-zone, one line per zone containing boat, format:**
```
{emoji} {name or «Réglementation»} — {speed if speed zone, else type label}
```

At La Salis (after fixes, only zones #18 and #24 remain):
```
🛬 Seaplane — zone de remplissage
⚡ 5 nds — limitation de vitesse
```

At Cap d'Antibes (zone #13 ANCHORING_PROHIBITED):
```
⚓ Mouillage interdit — Cap d'Antibes
```

Text: `fontSize=10.sp`, `color=ComposeColor.White` on semi-transparent dark background, left-aligned in a scrollable Column if >4 lines.
