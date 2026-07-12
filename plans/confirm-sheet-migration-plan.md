# ConfirmSheet Migration Plan

**Feature:** Ui_General
**Branch:** feature/multiselect
**Date:** 2026-07-12

## Overview

Replace all confirmation dialogs (centered `AlertDialog`) with a unified MD3 `ModalBottomSheet` component — `ConfirmSheet`. Migrate both existing confirmation call sites. Create a reusable framework mechanism for any future confirmation needs.

## Why Bottom Sheet

- 2026 MD3 best practice for confirmations on mobile
- Thumb-reachable on tall phones
- Less disruptive — slides up from bottom, doesn't fully obscure content
- Consistent with app's existing slide-in patterns (drawers from right, snackbar from bottom)

## Inventory — All Confirmation Dialogs

| Location | Current | Migrate? |
|---|---|---|
| [`MarkerDrawer.kt:364`](app/src/main/java/ykws/android/maro/ui/map/MarkerDrawer.kt:364) — single marker delete | `ConfirmDialog` (AlertDialog) | ✅ |
| [`ListOverlayScaffold.kt`](app/src/main/java/ykws/android/maro/ui/components/ListOverlayScaffold.kt) — batch delete | `ConfirmDialog` (AlertDialog) | ✅ |
| `MapScreen.kt:2006` — recovery track | Inline AlertDialog (Save/Discard) | ❌ — two actions, not confirm/cancel |
| `MapScreen.kt:2027` — bg location | Inline AlertDialog (Open Settings/Not now) | ❌ |
| `MapScreen.kt:2052` — battery optimization | Inline AlertDialog (Open Settings/Not now) | ❌ |
| `ColorPickerDialog` | Custom AlertDialog | ❌ |
| `IconPickerDialog` | Custom AlertDialog | ❌ |

Only 2 call sites to migrate — MarkerDrawer delete + multiselect batch delete.

## New Component: `ConfirmSheet`

```kotlin
// New file: app/src/main/java/ykws/android/maro/ui/components/ConfirmSheet.kt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfirmSheet(
    title: String,
    message: String,
    confirmLabel: String = stringResource(R.string.action_delete),
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    isDestructive: Boolean = true
)
```

### Visual Spec (matches app tokens)

| Element | Token |
|---|---|
| Sheet background | `uiSettingsBackground` |
| Sheet shape | `RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)` — matches list overlay |
| Drag handle | MD3 default (optional, `SheetDefaults.DragHandle()`) |
| Title | `uiSettingsTextPrimary`, 18sp, Bold, centered |
| Message | `uiSettingsTextPrimary`, 14sp, centered |
| Confirm button | `Button` filled, `semanticDanger` bg when destructive |
| Cancel button | `TextButton`, `uiSettingsAccent` text |
| Button layout | `Row`, `Arrangement.spacedBy(12.dp)`, padded 24dp horizontal, 16dp bottom |
| Divider | `HorizontalDivider(uiSettingsDivider)` above buttons |

### Layout

```
┌──────────────────────────────────────┐
│            (drag handle)             │
│                                      │
│            Delete                    │  ← title, 18sp Bold
│                                      │
│    Delete selected tracks?           │  ← message, 14sp
│                                      │
│  ────────────────────────────────    │  ← HorizontalDivider
│                                      │
│   [    Cancel    ] [   Delete   ]    │  ← Row, spacedBy(12.dp), 24dp h-pad
│                                      │
└──────────────────────────────────────┘
```

## API — `ConfirmSheet`

```kotlin
@Composable
fun ConfirmSheet(
    title: String,
    message: String,
    confirmLabel: String = stringResource(R.string.action_delete),
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    isDestructive: Boolean = true
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        containerColor = Color(AppConfig.uiSettingsBackground),
        dragHandle = { SheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, color = Color(AppConfig.uiSettingsTextPrimary), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Text(message, color = Color(AppConfig.uiSettingsTextPrimary), fontSize = 14.sp)
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(thickness = 0.5.dp, color = Color(AppConfig.uiSettingsDivider))
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.action_cancel), color = Color(AppConfig.uiSettingsAccent))
                }
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isDestructive) Color(AppConfig.semanticDanger) else Color(AppConfig.uiSettingsAccent)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(confirmLabel, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
```

## Migration — Call Sites

### 1. Multiselect Scaffold (batch delete)

Replace `ConfirmDialog` import/call with `ConfirmSheet`:

```kotlin
// In ListOverlayScaffold.kt action bar:
if (spec.confirmMessage != null && showConfirmDialog) {
    ConfirmSheet(
        title = stringResource(R.string.confirm_batch_delete_title),
        message = spec.confirmMessage,
        onConfirm = {
            spec.action(selectedIds.toSet())
            showConfirmDialog = false
            exitMultiselect()
        },
        onDismiss = { showConfirmDialog = false }
    )
}
```

Note: `ConfirmSheet` uses `ModalBottomSheet` which manages its own visibility — it renders when called and dismisses via `onDismissRequest`. The `showConfirmDialog` state variable still works (it controls whether the composable is called at all).

### 2. MarkerDrawer (single delete)

Replace `ConfirmDialog` with `ConfirmSheet`:

```kotlin
if (showDeleteConfirm) {
    ConfirmSheet(
        title = stringResource(R.string.marker_delete_title),
        message = stringResource(R.string.marker_delete_confirm, marker?.name ?: stringResource(R.string.marker_unnamed)),
        onConfirm = {
            showDeleteConfirm = false
            currentId?.let { viewModel.deleteMarker(it) }
        },
        onDismiss = { showDeleteConfirm = false }
    )
}
```

## Files Changed

| File | Change |
|---|---|
| `ui/components/ConfirmSheet.kt` | **New** — MD3 ModalBottomSheet confirmation |
| `ui/components/ConfirmDialog.kt` | **Delete** — replaced by ConfirmSheet |
| `ListOverlayScaffold.kt` | `ConfirmDialog` → `ConfirmSheet`, remove `AlertDialog` import |
| `MarkerDrawer.kt` | `ConfirmDialog` → `ConfirmSheet`, remove `ConfirmDialog` import |
| `strings.xml` + `values-fr/` | No new strings (reuse existing) |

## Rollback Safety

`ConfirmDialog.kt` can be deleted after migration is confirmed working. If rollback needed, revert to the commit before migration.

## Open Question

- `ModalBottomSheet` requires `ExperimentalMaterial3Api` opt-in. Already used elsewhere in the app? Check before adding.
