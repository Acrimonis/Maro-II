# Plan: marker-pin — Pin Toggle on Markers

> **Feature:** Markers | **Subfeature:** marker-pin | **Branch:** feature/marker-pin (fresh from origin/develop)

## Goal

Add a `pinned` boolean toggle to `UserMarker`, exposed as a pin `IconButton` (same pattern as `TrackHistoryOverlay.kt:563-574`) in:
1. The marker list card (`MarkerCardContent`)
2. The marker detail drawer (`ViewingContent`)

Icon: `Icons.Filled.LocationOn` / `Icons.Outlined.LocationOff` — monochrome Material, `ButtonColors.icon` tint.

---

## Step 1 — Data Model

**File:** `app/src/main/java/ykws/android/maro/data/model/markers/UserMarker.kt`

**Line 31** — Change:
```kotlin
    val colorIndex: Int? = null      // null = default colour, 0-15 = 16-colour palette
```
To:
```kotlin
    val colorIndex: Int? = null,      // null = default colour, 0-15 = 16-colour palette
    val pinned: Boolean = false
```

Safe: default `false` + `ignoreUnknownKeys = true` in serializer (`UserMarkerRepository.kt:33`).

---

## Step 2 — ViewModel

**File:** `app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt`

**After line 553** (after `deleteMarker` closing `}`), insert:

```kotlin

    /** Toggle the pinned state of a marker. */
    fun togglePin(markerId: String) {
        val marker = _markers.value.find { it.id == markerId } ?: return
        val updated = marker.copy(pinned = !marker.pinned)
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.update(updated) }
            _markers.value = withContext(Dispatchers.IO) { repo.loadAll() }
        }
    }
```

---

## Step 3 — List Card Pin (5 sub-steps across 3 files)

### 3a — MarkerManagementOverlay param

**File:** `app/src/main/java/ykws/android/maro/ui/map/MarkerManagementOverlay.kt`

**Line 115** (after `onDismiss: () -> Unit,`), insert:
```kotlin
    onTogglePin: (String) -> Unit = {},
```

### 3b — SwipeToDeleteMarkerCard param

**Line 266** (after `onPermanentDelete: () -> Unit`), insert:
```kotlin
    onTogglePin: (String) -> Unit,
```

### 3c — SwipeToDeleteMarkerCard call site wiring

**Lines 227-244** — the `items(markers, ...)` lambda currently calls `SwipeToDeleteMarkerCard` with: `onTap`, `onEdit`, `onDelete`, `onUndo`, `onPermanentDelete`. Add `onTogglePin`:

After line 243 (`onPermanentDelete = { ... }`), add:
```kotlin
                            onTogglePin = { onTogglePin(marker.id) },
```

### 3d — MarkerCardContent param + pin IconButton

**Lines 394-398** — `MarkerCardContent` signature currently takes `marker, onTap, onEdit`. Add `onTogglePin`:

Change:
```kotlin
private fun MarkerCardContent(
    marker: UserMarker,
    onTap: () -> Unit,
    onEdit: () -> Unit
)
```
To:
```kotlin
private fun MarkerCardContent(
    marker: UserMarker,
    onTap: () -> Unit,
    onEdit: () -> Unit,
    onTogglePin: () -> Unit
)
```

**Line 327** — `MarkerCardContent` call inside `SwipeToDeleteMarkerCard` currently:
```kotlin
                MarkerCardContent(marker, onTap = onTap, onEdit = onEdit)
```
Change to:
```kotlin
                MarkerCardContent(marker, onTap = onTap, onEdit = onEdit, onTogglePin = { onTogglePin(marker.id) })
```

**Lines 420-444** — Header row currently has single Edit `IconButton`. Replace the entire header `Row` block:

From current:
```kotlin
            // ── Header: coordinates + edit icon right-aligned ─────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = coordinateHeader(marker),
                    color = Color(AppConfig.uiSettingsTextMuted),
                    fontSize = MARKER_HEADER_FONT_SIZE,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = "Edit",
                        tint = ButtonColors.icon,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
```

To:
```kotlin
            // ── Header: coordinates + pin + edit icons right-aligned ──────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = coordinateHeader(marker),
                    color = Color(AppConfig.uiSettingsTextMuted),
                    fontSize = MARKER_HEADER_FONT_SIZE,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    IconButton(
                        onClick = onTogglePin,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (marker.pinned) Icons.Filled.LocationOn else Icons.Outlined.LocationOff,
                            contentDescription = if (marker.pinned) "Unpin" else "Pin",
                            tint = ButtonColors.icon,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = "Edit",
                            tint = ButtonColors.icon,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
```

### 3e — MarkerManagementOverlay imports

**After line 41** (`import androidx.compose.material.icons.filled.Edit`), add:
```kotlin
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.outlined.LocationOff
```

### 3f — OverlayLayer param + wiring

**File:** `app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt`

**Line 112** (after `onCreateFirst: () -> Unit`), add:
```kotlin
    onTogglePin: (String) -> Unit,
```

**Lines 317-327** — `MarkerManagementOverlay` call, add `onTogglePin` param:
```kotlin
            MarkerManagementOverlay(
                markers = markers,
                onTapMarker = onTapMarker,
                onEditMarker = onEditMarker,
                onSoftDeleteMarker = onSoftDeleteMarker,
                onUndoDeleteMarker = onUndoDeleteMarker,
                onPermanentDelete = onPermanentDelete,
                onCreateFirst = onCreateFirst,
                onCommitPendingDeletes = onCommitPendingDeletes,
                onDismiss = onDismissMarkerManagement,
                onTogglePin = onTogglePin
            )
```

### 3g — MapScreen wiring

**File:** `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt`

**Line ~1072-1080** — `OverlayLayer` call site (where `onTapMarker`, `onEditMarker` etc. are wired). Add:
```kotlin
            onTogglePin = { markersViewModel.togglePin(it) },
```

(Exact line depends on existing param ordering; insert alongside other marker callbacks.)

---

## Step 4 — Drawer Pin

**File:** `app/src/main/java/ykws/android/maro/ui/map/MarkerDrawer.kt`

### 4a — Imports

**After line 31** (`import androidx.compose.material.icons.filled.Edit`), add:
```kotlin
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.outlined.LocationOff
```

### 4b — Pin IconButton in header

**Lines 166-194** — Header action icons `Row` currently has Edit + Delete. Add pin before Edit:

Current:
```kotlin
            if (marker != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    IconButton(
                        onClick = {
                            viewModel.closeDrawer()
                            viewModel.startWizard(marker.id)
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = "Edit",
                            tint = ButtonColors.icon,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    IconButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Delete",
                            tint = ButtonColors.icon,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
```

To:
```kotlin
            if (marker != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    IconButton(
                        onClick = { viewModel.togglePin(marker.id) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (marker.pinned) Icons.Filled.LocationOn else Icons.Outlined.LocationOff,
                            contentDescription = if (marker.pinned) "Unpin" else "Pin",
                            tint = ButtonColors.icon,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    IconButton(
                        onClick = {
                            viewModel.closeDrawer()
                            viewModel.startWizard(marker.id)
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = "Edit",
                            tint = ButtonColors.icon,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    IconButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Delete",
                            tint = ButtonColors.icon,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
```

Note: `viewModel.togglePin(marker.id)` is direct — no callback threading needed here since `ViewingContent` already has the `viewModel`.

---

## Step 5 — Bake

Create `xTrack/Markers/FEAT_PLN_Markers_marker-pin.md` and update `FEAT_DSC_Markers.md` to add `marker-pin` subfeature.

---

## File Summary

| # | File | Lines | What |
|---|------|-------|------|
| 1 | `UserMarker.kt` | 31 | `pinned: Boolean = false` |
| 2 | `MarkersViewModel.kt` | after 553 | `togglePin(markerId)` method |
| 3a | `MarkerManagementOverlay.kt` | 115 | `onTogglePin` param |
| 3b | `MarkerManagementOverlay.kt` | 266 | `onTogglePin` param on SwipeToDeleteMarkerCard |
| 3c | `MarkerManagementOverlay.kt` | after 243 | wiring at items() call site |
| 3d | `MarkerManagementOverlay.kt` | 327, 394-398, 420-444 | MarkerCardContent param + pin icon + header rewrite |
| 3e | `MarkerManagementOverlay.kt` | after 41 | `LocationOn` + `LocationOff` imports |
| 3f | `OverlayLayer.kt` | 112, 317-327 | `onTogglePin` param + wiring |
| 3g | `MapScreen.kt` | ~1072 | `onTogglePin` wiring |
| 4a | `MarkerDrawer.kt` | after 31 | `LocationOn` + `LocationOff` imports |
| 4b | `MarkerDrawer.kt` | 166-194 | pin IconButton in header Row |
| 5 | xTrack | — | bake subfeature |
