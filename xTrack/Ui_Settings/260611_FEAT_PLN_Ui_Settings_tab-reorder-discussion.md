<!-- scope: feature -->
# Settings Tab Reorder — Final Plan

## Layout

```
General ──────────────── Navigation ───────────── System ─────────────────
┌─ DISPLAY ──────────┐  ┌─ Idle saving          ┐  ┌─ LANGUAGE ──────────┐
├─ Layers ───────────┤  │ Z300 alert            │  │ System/EN/FR        │
│ Coastline           │  └───────────────────────┘  ├─ POSITION SOURCE ──┤
│ Zone 300m           │       (thin — revisit later) │ GPS mode toggle     │
│ Low-depth warning   │                              │ ┌ GPS FREQ ──────┐ │
├─ Navigation ────────┤                              │ │ frequency sel  │ │ ← if gpsMode
│ Heading line        │                              │ └────────────────┘ │
│ Cap arrow           │                              │ ┌ Recenter delay ┐ │
└─────────────────────┘                              │ │ slider         │ │ ← if gpsMode
                                                     │ └────────────────┘ │
                                                     ├─ SCREEN ───────────┤
                                                     │ Keep screen on     │
                                                     │ Map FPS slider     │
                                                     ├─ EMODNET SHALLOW ──┤ ← SectionHeader
                                                     │ Cutoff depth slider│
                                                     ├─ REGENERATE LAYERS ┤
                                                     │ checkboxes + btn   │
                                                     └────────────────────┘
```

## Changes needed

| # | Change | Where | Details |
|---|--------|-------|---------|
| 1 | EMODnet → `SectionHeader` | SystemSettings | Change `SubSectionHeader` to `SectionHeader` |
| 2 | Recenter delay → System, GPS-conditional | SystemSettings, under POSITION SOURCE | `if (settings.gpsMode)` wrap around recenter slider |
| 3 | Map FPS → System, in SCREEN section | SystemSettings, after Keep screen on | Move FPS slider code |
| 4 | GPS frequency → System, GPS-conditional | SystemSettings, under POSITION SOURCE | `if (settings.gpsMode)` wrap, keep SubSectionHeader |
| 5 | Remove all 3 from NavigationSettings | NavigationSettings | Strip Recenter, GPS freq, FPS — keep Idle saving + Z300 alert |

