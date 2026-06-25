# Markers — Comprehensive Fix Plan

> **Based on:** Delta analysis + user feedback ("Add Pin not functional", "drawer at top over map")
> **Date:** 2026-06-22

---

## Root cause analysis

### RC1: Unconfirmed marker never rendered (blocking)

`MarkerOverlay` receives `markers: List<UserMarker>` — the list of **saved** markers from `markersViewModel.markers`. When user taps "Add Pin", the unconfirmed marker exists only in `createForm` state — it's NOT in the `markers` list. `MarkerOverlay` has no way to see it.

**Result:** Add Pin → drawer opens → map shows nothing. User fills form, taps Save → marker gets saved → markers list updates → marker appears. But during creation, there's no visual feedback of what's being created.

### RC2: Pin position frozen at boat location

`openCreateDrawer(mapCenter)` captures position once. While drawer is open, user pans map, but `createForm.position` never updates. The plan says "Pin stays locked at map center" — meaning it should TRACK map center continuously.

**Result:** User pans map to position marker, but marker stays where the boat was. Must cancel and re-tap Add Pin from new location.

### RC3: Drawer covers entire screen including dashboard

`MarkerDrawer` uses `BoxWithConstraints { Modifier.fillMaxSize() }` → scrim + panel fills the entire screen. Panel aligns to `BottomCenter` in portrait — covering the dashboard panel entirely. The plan says "covers dashboard" but the user now says "over the map."

**Result:** Dashboard hidden behind drawer while creating marker.

### RC4: Add Pin doesn't auto-show layer

`openCreateDrawer()` never sets `userMarkersVisible = true`. If user previously toggled the layer off, tapping Add Pin creates a marker that's invisible even after saving.

**Result:** User creates marker, sees nothing on map, confused.

### RC5: No view mode before edit

Per discussion: tap marker → view mode first, then edit. Implementation goes straight to edit.

---

## Fix list

| # | File | Change |
|---|------|--------|
| F1 | MarkerOverlay.kt | Add `unconfirmedMarker: UserMarker?` parameter — render it when non-null (from createForm while Creating/Editing) |
| F2 | MapScreen.kt | Build synthetic unconfirmed `UserMarker` from `createForm` + `createFormState`, pass to MarkerOverlay |
| F3 | MapScreen.kt | During Creating state, continuously update `createForm.position` from map center (LaunchedEffect or snapshotFlow) |
| F4 | MarkersViewModel.kt | `openCreateDrawer()` → if `!userMarkersVisible.value` → call `settingsManager.update { it.copy(userMarkersVisible = true) }` |
| F5 | MarkerDrawer.kt | Remove `BoxWithConstraints` scrim wrapper — draw drawer panel anchored to map area, not full screen. Or: use `Modifier.align(Alignment.TopCenter)` for top positioning. |
| F6 | MarkersViewModel.kt | Add `Viewing(markerId)` state to `MarkerDrawerState`. `openEditDrawer` → Viewing first. Viewing mode shows read-only info with "Edit" button. |
| F7 | MarkerDrawer.kt | Add ViewingContent composable — read-only marker info + Edit/Close buttons |
| F8 | MarkerOverlay.kt | Add corridor centerline rendering (thin, 50% alpha) between p1 and p2 |
| F9 | MarkerOverlay.kt | Add dashed centerline preview during corridor SET_P2 phase |
