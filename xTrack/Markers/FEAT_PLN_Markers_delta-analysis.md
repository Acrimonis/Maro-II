# Markers — Plan vs Implementation Delta

> **Reviewed:** 2026-06-22 | **Source plan:** `FEAT_PLN_Markers_user-markers-design.md` + UI review decisions

---

## 🔴 Functional gaps — blocking

### D1. Pin position static during creation (Plan §8.4)

**Plan:** "While the bottom panel is open and marker is unconfirmed: Pin stays locked at map center, User pans the map to position the pin at desired location."

**Reality:** `openCreateDrawer(initialPos)` captures the boat position ONCE. The marker position never updates as the user pans the map. The unconfirmed pin stays where the boat was when "Add Pin" was tapped, not where the user pans to.

**Fix:** The unconfirmed marker position must continuously track the map center while the drawer is open in Creating mode. Capture map center on each frame or on pan gesture, update `CreateFormState.position`. The overlay should use `form.position` (live) not a frozen `marker.geometry.position`.

### D2. Add Pin doesn't auto-show hidden layer (Plan §8.3)

**Plan:** "If layer hidden → auto-show layer" when Add Pin is tapped.

**Reality:** No auto-show. If user has the markers layer toggled off, tapping Add Pin creates a marker that's invisible. The `userMarkersVisible` StateFlow is never set to `true` on Add Pin.

**Fix:** In `openCreateDrawer()`, if `!userMarkersVisible.value` → call `toggleVisibility()` (which sets it to true). Or add a dedicated `showLayer()` method.

### D3. Drawer height hardcoded 300dp — doesn't cover dashboard (Plan §8.5)

**Plan:** "Covers dashboard; map stays visible above." The drawer should match the dashboard height.

**Reality:** `Modifier.height(300.dp)` — hardcoded, never adapts. On small screens it may leave dashboard exposed below; on large screens it may waste space.

**Fix:** Compute drawer height from the dashboard panel's actual height. Pass it as a parameter from `MapScreen` where the dashboard layout is known. Or use `BoxWithConstraints` to fill the dashboard zone dynamically.

### D4. Marker tap goes straight to edit — no view/read mode (Plan §8.7 + UI review G3)

**Plan (discussion):** "Tap on marker should open the drawer on this marker current state. Fow should allow modify." = view mode first, then modify.

**Reality:** `onMarkerTap` calls `openEditDrawer(markerId)` which goes directly to `MarkerDrawerState.Editing` with pre-filled form fields and a Delete button. No read-only view before editing.

**Fix:** Add a `Viewing(markerId)` state to `MarkerDrawerState`. Tap marker → Viewing mode (shows marker info read-only with an "Edit" button). Tap "Edit" → transitions to Editing. Or: keep current flow but add confirmation before changes are saved (revert on cancel). The user's intent was "view first, edit on demand."

### D5. Corridor centerline not rendered (Plan §8.2)

**Plan:** "Two parallel dashed lines at ±width/2, centerline, name label at midpoint." (Name labels removed per R7, but centerline remains.)

**Reality:** `MarkerOverlay.kt` draws the two parallel dashed boundary lines + endpoint dots. No centerline is drawn.

**Fix:** Add a third polyline along the centerline, thinner (1dp) and at reduced alpha (50%) to distinguish from boundary lines.

---

## 🟡 Non-blocking mismatches

### D6. No settings integration (Plan §8.1, §9)

**Plan:** "Settings section: marker list management in Settings" listed as an integration point.

**Reality:** No marker section exists in the settings page. Marker management is only via hamburger → Manage Markers.

**Resolution:** Settings integration is listed but was never detailed in any phase checklist. The hamburger path already provides full management. Defer — add settings entry later if needed.

### D7. Corridor dashed line uses solid polyline (Plan §8.4)

**Plan:** "Temporary dashed line drawn between p1 and p2 (placement guide only)."

**Reality:** `MarkerOverlay.kt` draws the corridor lines with `Stroke` + `PathEffect.dashPathEffect` — so they ARE dashed. But the centerline isn't drawn at all (D5). The temporary placement guide between p1 and p2 during the SET_P2 phase isn't rendered as a separate dashed centerline — only the boundary lines show once p2 is set.

**Resolution:** Add the dashed centerline during corridor SET_P2/CONFIRM phase as a placement guide, distinct from the final boundary lines.

### D8. Unconfirmed pin renders at fixed position (Plan §8.4)

**Plan:** "Pin renders in unconfirmed color" at map center during placement.

**Reality:** Since D1 blocks position updates, the unconfirmed pin stays where the boat was, not where the user pans. Combined with D1.

---

## ✅ Matching plan

| Item | Status |
|------|--------|
| Data model (UserMarker, MarkerGeometry) | ✅ Matches §5 exactly (+ confirmed field, per UI review) |
| JSON persistence (UserMarkerRepository) | ✅ Matches §7 |
| Land-blocking (segmentsIntersectLand, grazing 10m) | ✅ Matches §4 |
| ClosestUnblockedPoint (36 circle, 20 corridor, 1 pin) | ✅ Matches §4.3 |
| Match resolution (Zone→Proximity→NoMatch) | ✅ Matches §6 |
| BBox pre-filter | ✅ Matches discussion Gap 3 |
| Precision sort + spatial nesting | ✅ Matches §6.4 |
| Marker rendering colors (semantic.info/caution) | ✅ Matches UI review R5 |
| No name labels on map | ✅ Matches UI review R7 |
| Proximity range preview (cyan dashed) | ✅ Matches UI review G4 |
| Add Pin button placement (grouped with FanLayout) | ✅ Matches UI review R1 |
| Drawer animation (portrait=bottom, landscape=left) | ✅ Matches §8.5 |
| FanLayout pin toggle (6th child) | ✅ Matches §8.1 |
| Hamburger MARKERS section | ✅ Matches §8.8 |
| Management page swipe-to-delete | ✅ Matches §8.9 + track-list paradigm |
| Empty state on management page | ✅ Matches UI review G1 |
| Marker count in headings | ✅ Matches UI review G2 |
| Boat marker tap → "Where am I?" | ✅ Matches §8.7 |
| Match result tiered display | ✅ Matches §3.2 |
| On-demand only (no 1Hz pipeline) | ✅ Matches §3.1 |
| No per-marker visibility | ✅ Matches §9.1 |
| No export/import | ✅ Matches §9.1 |

---

## Priority fix order

1. **D1** — Pin position tracks map center during creation (core UX broken)
2. **D2** — Add Pin auto-shows layer (invisible marker trap)
3. **D4** — View mode before edit on marker tap
4. **D3** — Drawer matches dashboard height
5. **D5** — Corridor centerline rendering
6. **D7** — Corridor placement guide dashed centerline
