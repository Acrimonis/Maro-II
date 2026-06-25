# Markers — UI Shortcomings Review

> **Feature:** Markers | **Source plan:** `FEAT_PLN_ZoneTile_user-markers-design.md`
> **Reviewed:** 2026-06-22 | **Discussed:** 2026-06-22

---

## Decisions (post-discussion)

| # | Category | Item | Decision |
|---|----------|------|----------|
| G1 | Gap | No empty state for management page | **Add** empty-state composable: pin icon + "No markers yet" + "Create First Marker" button. Uses `ui.dashboard.text.muted` / `ui.dashboard.card.background` / `ButtonColors`. |
| G2 | Gap | No marker count indication | **Add** live count in hamburger (`Manage Markers (N)`) and management page heading (`Markers · N`). **No hard cap** — unlimited markers. |
| G3 | Gap | No tap-on-marker to edit | **Add** tap on any map marker → opens drawer in view mode showing that marker's state (with Edit button). Boat marker tap still does "where am I?". |
| G4 | Gap | No proximity range preview | **Add** thin dashed proximity-range preview during creation/edit: circle=radius×3, corridor=width×3. Cyan `#4FC3F7` at ~30% alpha. |
| G5 | Gap | Tap conflict (pin vs boat marker) | Boat marker always wins. User naturally pans to separate them before tapping the pin. |
| R1 | Refinement | "Add Pin" button placement | Stacked above zoom controls, grouped with fan layers button at right edge. Both centered as a unit. 48dp round button, `ButtonColors.bg`/`ButtonColors.icon`, filled-pin icon. |
| R2 | Refinement | Drawer interaction gating | Drawer open → hides other controls (standard dialog pattern). Back button + back press dismiss. No cross-overlay gating needed. |
| R3 | Refinement | Corridor 2nd-point UX | Drawer stays open (never minimizes). Three-step: Point 1 set → "Set Point 2" → Confirm. Live dashed preview line from p1 to map center. |
| R4 | Refinement | Mode visual differentiation | **Skip** colored accent bar. Mode content is distinct enough without it. Distinct header text only. |
| R5 | Refinement | Color token normalization | **5 semantic tokens** — `danger` (red), `caution` (amber), `compliant` (green), `info` (blue #1565C0), `inactive` (white-transparent). All existing status/dashboard/icon colors alias to them. Marker unconfirmed → `caution`, marker confirmed → `info`. Collapse `neutral`→`info`, `absent`→`inactive`. |
| R6 | Refinement | Haptic feedback | **Skip** for now. |
| R7 | Refinement | Name labels on map | **Remove** entirely. No labels on map markers. Names only in drawer and management page. |

---

## Implementation order

### Step 0 — Color normalization (prerequisite)

Abstract the 5 semantic tokens as a standalone task before any marker code touches colors. Owned by ColorManagement feature.

**Files touched:**
- `app/src/main/assets/colors.properties` — add `semantic.*` section, re-alias downstream tokens
- `docs/color-scheme.md` — add semantic taxonomy section, update all alias references to reflect new chain
- `app/src/main/java/ykws/android/maro/config/AppConfig.kt` — add convenience accessors (`semanticDanger`, etc.)
- `xTrack/ColorManagement/FEAT_DSC_ColorManagement.md` — update frontmatter, add normalization subfeature

**Semantic tokens:**
```
semantic.danger     = #CCB71C1C   # red — error, delete, blocked
semantic.caution    = #CCEF6C00   # amber — warning, unconfirmed, borderline
semantic.compliant  = #CC4CAF50   # green — OK, safe, inside zone
semantic.info       = #FF1565C0   # blue — normal, navigation, brand primary
semantic.inactive   = #33FFFFFF   # white 20% — disabled, dimmed, no-data
```

All existing `ui.dashboard.status.*`, `status.gps.*`, `status.earthWater.*`, etc. alias to these 5 base tokens. `neutral` → `info`, `absent` → `inactive`.

### Step 1+ — Markers implementation (per original plan)

### Plan changes (FEAT_PLN_ZoneTile_user-markers-design.md)

| Section | Change |
|---------|--------|
| §8.2 Marker rendering | Remove name labels from all three types |
| §8.3 "Add Pin" button | Reposition: grouped with fan layers button above zoom controls |
| §8.4 Placement UX | Add proximity range preview (second dashed circle/line) |
| §8.5 Drawer | Remove "minimize" step for corridor. Add view mode for tap-on-marker. |
| §8.7 "Where am I?" trigger | Add: marker tap → opens drawer in view/edit mode. Boat marker still triggers query. |
| §8.9 Management page | Add empty state + count in heading |
| §9.2 (new) | Add color token section referencing semantic taxonomy |

### colors.properties changes

```properties
# ── Semantic status colours ────────────────────────────
semantic.danger=#CCB71C1C
semantic.caution=#CCEF6C00
semantic.compliant=#CC4CAF50
semantic.info=#FF1565C0
semantic.inactive=#33FFFFFF

# Existing tokens alias to semantics:
ui.dashboard.status.error=${semantic.danger}
ui.dashboard.status.warning=${semantic.caution}
ui.dashboard.status.success=${semantic.compliant}
ui.dashboard.status.neutral=${semantic.info}
ui.dashboard.status.absent=${semantic.inactive}
# ... (all other tokens re-aliased)

# Marker colours:
map.marker.unconfirmed=${semantic.caution}
map.marker.neutral=${semantic.info}
```

### New UI components (not in original plan)

| Component | Purpose |
|-----------|---------|
| `MarkerEmptyState` | Management page when `markers.isEmpty()` |
| Proximity range preview overlay | Thin dashed geometry during creation/edit only |
| Marker tap handler | On existing marker geometries → opens drawer in view mode |
