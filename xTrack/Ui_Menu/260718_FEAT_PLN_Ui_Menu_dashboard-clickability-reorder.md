# Menu Dashboard — Clickability + Reorder

> **Feature:** Ui_Menu | **Branch:** feature/ya-manu
> **Created:** 2026-07-18 | **Status:** Implemented

## Problem

1. **Clickability gaps** — Import/Export row has only two small 40dp IconButtons, no row-level click target. Divider spacers (6dp) create dead zones inside cards.
2. **"Show Zones on Map" ordering** — toggle sits above "Manage Markers" in the MARKERS card. "Manage Markers" is the primary action and should come first.

## Current vs Proposed

```
CURRENT MARKERS                    PROPOSED MARKERS
├── Show Zones on Map [Switch]     ├── Manage Markers  [chevron →]   ← primary action first
├── ─── divider ───                ├── ─── divider ───
└── Manage Markers  [chevron →]    └── Show Zones on Map [Switch]    ← toggle below
```

**Rationale:** Primary action ("Manage Markers") should be the first item users see when scanning the card. The toggle is a secondary control.

## Clickability Audit (unchanged from original)

| Row | Touch Target | Issue |
|-----|-------------|-------|
| GPS toggle | Full Row + Switch | ✅ |
| Manage Track | `.clickable` on Row, 48dp min | ⚠️ Spacer(6dp) gaps around divider |
| Import / Export | Two 40dp IconButtons only | 🔴 No row-level click — dead between icons |
| Show Zones | Full Row + Switch | ✅ |
| Manage Markers | `.clickable` on Row, 48dp min | ⚠️ Same spacer gaps |

## Proposed Changes

### 1. Reorder MARKERS card: "Manage Markers" above "Show Zones on Map"

Swap the two Row blocks in `MenuDrawerOverlay.kt` lines 346-396. Divider stays between them.

### 2. Clickability: taller rows, smaller separators

**Principle:** Increase row touch targets while tightening visual gaps between them.

| Element | Current | Proposed | Reason |
|---------|---------|----------|--------|
| Clickable row `heightIn(min =)` | 48.dp | **56.dp** | Larger tap zone (48dp is Android minimum; 56dp is comfortable) |
| Divider spacer (`Spacer` above/below `HorizontalDivider`) | 6.dp | **2.dp** | Tighten gaps; card padding already provides breathing room |
| Divider thickness | 0.5.dp | 0.5.dp | Unchanged |
| Import/Export icons | 40.dp | **48.dp** | Larger tap targets; no row-level click — keep icon-only |

Net effect per card section:
- **Before:** 48dp row + 6dp gap + 0.5dp divider + 6dp gap = 60.5dp vertical per item pair, with dead zones
- **After:** 56dp row + 2dp gap + 0.5dp divider + 2dp gap = 60.5dp vertical — same height, larger tap zones, tighter gaps

### 3. Hardcoded string → resource

`"Import / Export"` (line 250) → `R.string.menu_import_export`

## Files

- `app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt` — all changes
- `app/src/main/res/values/strings.xml` — add `menu_import_export` string if missing

## Implemented

| # | Change | Detail |
|---|--------|--------|
| 1 | Reorder MARKERS | "Manage Markers" row above divider, "Show Zones on Map" toggle below |
| 2 | Taller rows | 4× `heightIn(min = 48.dp)` → `56.dp` (Track List, Import/Export, Manage Markers, Show Zones) |
| 3 | Tighter divider gaps | 6× `Spacer(6.dp)` → `Spacer(2.dp)` above/below each HorizontalDivider |
| 4 | Larger icons | 2× Import/Export `IconButton` `Modifier.size(40.dp)` → `48.dp` |
| 5 | String resource | Hardcoded `"Import / Export"` → `stringResource(R.string.menu_import_export)`; added to `strings.xml` |

**Build:** `gradlew clean assembleDebug` — BUILD SUCCESSFUL, zero new errors.

**Ask Review:** All 5 changes verified against plan. No regressions, no deviations, no spaghetti. One pre-existing inconsistency noted (TRACKS vs MARKERS header-card spacer asymmetry: 2dp vs 8dp) — out of scope.
