# ListableItem Interface Migration Plan

**Created:** 2026-07-01 19:43 UTC
**Branch:** feature/lists-features
**Feature:** Ui_General / list
**Status:** planning

## Goal

Normalize `UserMarker` and `TrackSummary` under a common `ListableItem` interface to enable:
- Shared sort strategies across marker and track lists
- Batch actions (select multiple items, pin/unpin, delete)
- Consistent card rendering with common title/description/date fields

## Interface

```kotlin
// data/model/ListableItem.kt
interface ListableItem {
    val id: String
    val title: String
    val description: String
    val createdAtEpochMs: Long
    val updatedAtEpochMs: Long
    val isPinned: Boolean
}
```

## Migration Steps

### 1. Create interface
- **File:** `app/src/main/java/ykws/android/maro/data/model/ListableItem.kt`
- Pure Kotlin, no Android dependencies, no serialization

### 2. UserMarker → implement ListableItem
- Add `: ListableItem` to class declaration
- `title` → `name` (override)
- `isPinned` → `pinned` (override)
- `description`, `createdAtEpochMs` → direct match
- **New:** `updatedAtEpochMs: Long = createdAtEpochMs` (non-proto, JSON field)

### 3. Track + TrackSummary → implement ListableItem
- **Track:** add `@ProtoNumber(17) val updatedAtEpochMs: Long = 0L` (mirrored to TrackSummary so `rebuildIndex()` preserves it)
- **TrackSummary:** add `@ProtoNumber(15) val updatedAtEpochMs: Long = 0L`
- Both implement `: ListableItem`
- `title` → `name` (override), `description` → `comment` (override), `createdAtEpochMs` → `startTimeMs` (override), `isPinned` → `pinned` (override)

### 4. UserMarkerRepository — set updatedAtEpochMs
- `add(marker)`: set `updatedAtEpochMs = System.currentTimeMillis()` before save
- `update(marker)`: set `updatedAtEpochMs = System.currentTimeMillis()` before save

### 5. TrackRepository + TrackRecorder — set updatedAtEpochMs
- `TrackRepository.save(track)`: `.copy(updatedAtEpochMs = now)` before proto encode
- `TrackRepository.finalizeOrphanedCheckpoint()`: add `updatedAtEpochMs = now` to `.copy()`
- `TrackRecorder.finalizeTrack()`: add `updatedAtEpochMs = now` to `track.copy(...)` call
- `rebuildIndex()`: add `updatedAtEpochMs = track.updatedAtEpochMs` to TrackSummary constructor
- `updateMetadata()` + `setPinned()` go through `save()` → timestamp set automatically
- `saveCheckpoint()`: no change (mid-recording snapshot, not a final save)

### 6. MarkersViewModel — set updatedAtEpochMs
- `saveMarker()`: set `updatedAtEpochMs = System.currentTimeMillis()`
- `updateMarker()`: set `updatedAtEpochMs = System.currentTimeMillis()`
- `confirmAutoMarker()`: set `updatedAtEpochMs = System.currentTimeMillis()`
- `togglePin()`: set `updatedAtEpochMs = System.currentTimeMillis()`
- `setMarkerIcon()`: set `updatedAtEpochMs = System.currentTimeMillis()`

### 7. Field renames — UserMarker
Rename `name` → `title`, `pinned` → `isPinned`. Use `@SerialName` for JSON backward compat:
```kotlin
@SerialName("name") val title: String
@SerialName("pinned") val isPinned: Boolean
```
**Call sites (~15 files):** MarkerMatcher, MapScreen, MarkerDrawer, MarkerManagementOverlay, MarkerOverlay, MarkersViewModel, toMarkerSnapshot extension.

### 8. Field renames — Track + TrackSummary
Rename `name` → `title`, `comment` → `description`, `pinned` → `isPinned`. Proto uses `@ProtoNumber` — field name irrelevant for serialization, no annotation needed.
**Call sites (~6 files):** TrackRecorder, TrackRepository, MapScreen, TrackHistoryOverlay.

### 9. Build + verify
- `apk-build.bat` — ensure all proto/JSON serialization works
- Verify marker and track lists render correctly

## Backward Compatibility

| Concern | Handling |
|---------|----------|
| Legacy markers (JSON, no `updatedAtEpochMs`) | Default = `createdAtEpochMs` |
| Legacy tracks (proto, no field 15) | Protobuf default = `0L` |
| Existing proto files | New field with default, no migration needed |
| Existing JSON files | New field with default, no migration needed |
