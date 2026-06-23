# Markers — Two-Pass Review

> **Date:** 2026-06-22 | **Files reviewed:** 7 implementation files + MapScreen integration

---

## Pass 1: Code Review

### 🔴 C1. Corridor Confirm button onClick is empty

[`MarkerDrawer.kt:259`](app/src/main/java/ykws/android/maro/ui/map/MarkerDrawer.kt:259) — SET_P2 "Confirm" button:
```kotlin
onClick = { /* p2 is set via map center captured externally */ }
```
The onClick lambda is EMPTY. User taps Confirm → nothing happens. This is the root cause of "actions in panel have no consequence."

**Fix:** The Confirm button needs to capture the current map center and call `viewModel.setCorridorP2(mapCenter)`. Add `onConfirmP2: () -> Unit` parameter to `MarkerDrawer`, pass `mapCenter` from MapScreen.

### 🔴 C2. Save button gate condition wrong

[`MarkerDrawer.kt:363-366`](app/src/main/java/ykws/android/maro/ui/map/MarkerDrawer.kt:363) — Save button check:
```kotlin
if (form.corridorPhase == CorridorPhase.SET_P2 || form.corridorP2 == null) {
    return@Button // can't save without p2
}
```
`form.corridorPhase == CorridorPhase.SET_P2` is wrong — SET_P2 means "waiting to confirm", p2 is NOT set. The gate should be `form.corridorP2 == null` only (simple null check). With the current code, if phase is SET_P2, Save silently does nothing.

**Fix:** Change to: `if (form.corridorP2 == null) return@Button`. Optionally disable the button visually + show "Set Point 2 first" text.

### 🔴 C3. toggleVisibility can hide layer on create

[`MarkersViewModel.kt:155-156`](app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt:155) — `openCreateDrawer`:
```kotlin
if (!userMarkersVisible.value) toggleVisibility()
```
This calls `toggleVisibility()` which FLIPS the current state. If layer is already visible, `toggleVisibility()` would HIDE it. The guard `if (!userMarkersVisible.value)` prevents this, so it's correct for the toggle case. But semantically, this should be a `showLayer()` not a `toggleVisibility()` — cleaner API.

**Fix:** Add `fun showLayer() { if (!userMarkersVisible.value) toggleVisibility() }` and call `showLayer()` from `openCreateDrawer`. Or keep current code (it's functionally correct with the guard).

### 🟡 C4. userMarkersVisible StateFlow double-initialization risk

[`MarkersViewModel.kt:108-117`](app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt:108) — The StateFlow construction reads `flow.value` eagerly then mirrors via collection. If `SettingsManager` hasn't loaded preferences when `flow.value` is read, the initial value may be wrong (default `true` from AppSettings data class). Collection will eventually correct it, but there's a race: first compose might see `true` for one frame, then flip to actual value.

**Fix:** Load settings synchronously before ViewModel creation, or use `stateIn` with `SharingStarted.WhileSubscribed()`.

### 🟡 C5. Corridor p2 uses mapCenter but not captured at Confirm time

The `LaunchedEffect` that tracks mapCenter during Creating state (F3) skips updating `createForm.position` during corridor SET_P2 to keep p1 fixed. But p2 is set by `setCorridorP2(p2)` which needs to be called with the current mapCenter at Confirm time. Currently, `setCorridorP2` is never called because Confirm onClick is empty (C1).

**Fix:** When Confirm is tapped (C1 fixed), call `viewModel.setCorridorP2(mapCenter)`. The LaunchedEffect tracking is correct — it only skips during SET_P2 to preserve p1.

### 🟡 C6. Edit drawer doesn't show corridor 2nd-point flow

When editing a corridor marker, the form is pre-filled and corridorPhase goes to CONFIRM if p2 exists. But the EditContent doesn't have the full corridor flow (P1 → SET_P2 → CONFIRM). If user changes corridor type mid-edit, the corridor fields may not appear.

**Fix:** Add corridor flow to EditContent (same as CreationContent). Currently EditContent only shows width field + proximity for corridors — no "Set Point 2" flow.

---

## Pass 2: Feature Set & Usability

### 🔴 F1. No visual feedback when Save is blocked

When corridor SET_P2 or p2==null, Save button is clickable but does nothing. User taps Save, nothing happens, no explanation.

**Fix:** Disable Save button visually (gray it out) + show red text "Set Point 2 first" when corridor p2 is missing.

### 🔴 F2. Drawer height hardcoded 300dp

[`MarkerDrawer.kt:102`](app/src/main/java/ykws/android/maro/ui/map/MarkerDrawer.kt:102) — `Modifier.height(300.dp)`. On tall screens this wastes space; on short screens this clips fields. The drawer should use `wrapContentHeight` with a max height constraint.

**Fix:** Replace `Modifier.height(300.dp)` with `Modifier.heightIn(max = 400.dp).wrapContentHeight()`.

### 🔴 F3. No "Cancel edit → back to Viewing"

From Viewing mode, tapping Edit opens Editing mode. If user taps Cancel in Editing, drawer closes entirely — loses the Viewing context. Should go back to Viewing, not close.

**Fix:** `EditContent` Cancel button → `viewModel.openEditDrawer(markerId)` (returns to Viewing) instead of `onClose`.

### 🟡 F4. Proximity preview uses AppConfig static values

[`MarkerOverlay.kt:125`](app/src/main/java/ykws/android/maro/ui/map/MarkerOverlay.kt:125) — Proximity preview always uses `proximityZoneMultiplier` parameter (default 3.0). If marker has `proximityOverrideM`, the preview should reflect that override, not the default formula.

**Fix:** Use `marker.proximityOverrideM ?: (radiusM * multiplier)` for preview computation.

### 🟡 F5. Management page doesn't update marker count in real-time

`MarkerManagementOverlay` receives `markers` list via parameter. If a marker is soft-deleted, the list updates reactively (from StateFlow). But the heading "Markers · N" only shows the count at composition time. Since `markers` is a StateFlow-derived value, it should recompose correctly. Verify this.

### 🟡 F6. No "Delete" in Viewing mode

Viewing mode shows marker info + Edit/Close. No Delete option. User must go Edit → scroll down → Delete. Delete should be accessible from Viewing mode too.

**Fix:** Add Delete button to ViewingContent (with confirmation dialog).

### 🟡 F7. Match result doesn't close on back press when drawer is MatchResult

BackHandler in MarkerDrawer calls `onClose` which is `markersViewModel.closeDrawer()`. This should work for all modes. Verify.

---

## Summary

| # | Type | File | Issue | Fix |
|---|------|------|-------|-----|
| C1 | 🔴 Bug | MarkerDrawer.kt:259 | Confirm button onClick empty | Call viewModel.setCorridorP2(mapCenter) |
| C2 | 🔴 Bug | MarkerDrawer.kt:363 | Save gate condition wrong | Use `form.corridorP2 == null` only |
| C3 | 🔴 Logic | MarkersViewModel.kt:155 | toggleVisibility semantic | Already guarded — ok, but add showLayer() |
| C4 | 🟡 Risk | MarkersViewModel.kt:108 | StateFlow double-init | Load settings before ViewModel or use stateIn |
| C5 | 🟡 Missing | MapScreen.kt | p2 never captured | Fixed by C1 |
| C6 | 🟡 Missing | MarkerDrawer.kt | Edit no corridor flow | Add corridor flow to EditContent |
| F1 | 🔴 UX | MarkerDrawer.kt:363 | No save-blocked feedback | Disable button + show reason |
| F2 | 🔴 UX | MarkerDrawer.kt:102 | Hardcoded 300dp | Use wrapContentHeight + max |
| F3 | 🔴 UX | MarkerDrawer.kt:625 | Cancel in Edit closes all | Return to Viewing instead |
| F4 | 🟡 Feature | MarkerOverlay.kt:125 | Proximity preview ignores override | Use marker's proximityOverrideM |
| F5 | 🟡 Verify | MarkerManagementOverlay.kt | Real-time count update | Verify works |
| F6 | 🟡 Missing | MarkerDrawer.kt:455 | No Delete in Viewing | Add Delete button |
| F7 | 🟡 Verify | MarkerDrawer.kt:123 | Back handler | Verify works |
