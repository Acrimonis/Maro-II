# Markers — UI Workflow Review & Improvements

> **Date:** 2026-06-23 | **Status:** Design review

---

## Current workflows

### Creation
```
Map → Add Pin btn → Drawer(New Marker) → name/type/geo/prox/desc → Save
                                              ↑ type defaults to Pin
```

### Corridor
```
Map → Add Pin → Drawer → select Corridor → width → "Set Point 2"
→ pan map → Confirm → Save
```

### Match
```
Map → tap boat marker → Drawer(Where Am I?) → tiered text results
```

### Management
```
Hamburger → Manage Markers → list → swipe delete / tap → Viewing → Edit
```

---

## 🔴 Pain points

| # | Issue | Impact |
|---|-------|--------|
| P1 | Add Pin always starts as Pin — no Circle/Corridor shortcut | Extra taps to switch type every time |
| P2 | FanLayout toggle (outlined pin) vs Add Pin (filled pin) too similar | User confusion between "show layer" and "create marker" |
| P3 | Corridor SET_P2: "Pan map → center = Point 2" unclear | User doesn't know center IS p2, no dashed preview feedback |
| P4 | Save button far from form — must scroll past proximity+desc | Annoying on small screens |
| P5 | No undo on creation — must edit after mistaken save | Wasted taps |
| P6 | "Where am I?" results text-only — no map visualization | Can't see which markers matched where |
| P7 | No distance-to-boat shown during placement | Can't position marker relative to current location |
| P8 | Management page closes when tapping marker to edit | Lose list context |
| P9 | No coordinate entry — position purely visual | Can't place at exact lat/lon |
| P10 | No marker tap on map (removed — was blocking drags) | Can't interact with existing markers on map |

---

## 🟡 Proposed improvements

### I1. Three direct-creation buttons instead of one "Add Pin"
Replace the single "Add Pin" filled-pin button with three smaller buttons side by side:
- 📍 Pin
- ⭕ Circle  
- ═ Corridor

Each opens drawer pre-selected to that type. Saves one tap per creation.

### I2. Differentiate FanLayout toggle vs creation buttons
FanLayout toggle: keep outlined pin (toggle semantics).
Creation buttons: use distinct icons — filled pin for Pin, circle outline for Circle, parallel lines for Corridor.

### I3. Corridor SET_P2: show dashed preview line + "Center = P2" label
When in SET_P2, the unconfirmed corridor overlay should show a dashed centerline from p1 to current map center with a text label "P2" at the map center position. Visual feedback that center IS point 2.

### I4. Move Save/Cancel to top of drawer (sticky header)
Put Save/Cancel in the header row (next to title) so it's always visible without scrolling. Only the geometry fields scroll if needed.

### I5. Soft-create with confirmation toast
On Save, show a brief toast "Marker 'X' created" with an "Undo" action (3s). Tapping Undo deletes the marker. Similar to management page undo pattern.

### I6. Highlight matched markers on map
When "Where am I?" results are shown, matched markers render with a highlighted border/glow. Non-matched markers dim. This gives spatial context to the text results.

### I7. Show distance-to-boat in drawer during creation
Below the proximity info line, add: "Distance to boat: X m" (live-updating from current GPS position or map center in demo mode).

### I8. Keep management page open when editing
When user taps a marker in the management list, slide in the edit drawer OVER the management page (not replacing it). The list remains visible behind. On save/cancel, drawer slides out, list is still there.

### I9. Add coordinate fields (lat/lon)
Below the geometry section, add two small text fields for manual lat/lon entry. If user enters values, position snaps to those coordinates. If left empty, position stays at map center.

### I10. Restore marker tap on map via non-intercepting tap detection
Instead of a full-screen `pointerInput` Box, register a tap listener on the MapView directly (Android `OnClickListener` or OSMdroid `MapEventsOverlay`). This doesn't block drag events.

---

## Priority

| Priority | Items |
|----------|-------|
| 🔴 Must fix | P3 (corridor preview), P8 (management stays open), P10 (marker tap) |
| 🟡 Should fix | P1 (type shortcut), P4 (sticky Save), P6 (match highlighting), I7 (distance) |
| 🔵 Nice to have | P5 (undo create), P9 (coordinate entry), P2 (icon differentiation) |
