# Markers — Icon/Pin Decoupling & Pin Re-implementation

> **Feature:** Markers | **Branch:** feature/markers-pins-vs-icons
> **Created:** 2026-09-04 | **Status:** Plan — Approved (awaiting implementation)

## Goal

Split the currently-coupled marker `icon` concept into two independent features:

1. **Icon** — a pure POI emoji decoration (`UserMarker.icon`), settable via picker, with no pin semantics.
2. **Pin** — a real persisted boolean (`UserMarker.pinned`), re-implemented from scratch to mirror the tracks pin feature exactly.

## Locked Decisions

- **No backfill** — all existing markers start `pinned=false`; icon no longer implies pin. Users re-pin explicitly.
- **Map layer stays binary** `HIDDEN`/`SHOW_ALL` — no `SHOW_PINNED` state.
- **Rendering** — pinned markers full styling + top z-order; unpinned dimmed. `whereAmI` match highlight takes precedence.

## 1. Icon Feature — Normalize & Decouple

- [`UserMarker.kt`](app/src/main/java/ykws/android/maro/data/model/markers/UserMarker.kt) — `icon` becomes purely decorative; the derived pin coupling (`isPinned get() = icon != null`, `pinned get() = icon != null`) is removed.
- Consolidate geometry-type icons to a single source: `MarkerGeometry.iconFor()`; `MarkersViewModel.typeIcon()` delegates to it or is deleted.
- Wizard wording: "Pin this marker"/"Pinned" → "Set icon"/"Change icon"; drop `cd_pin_marker`, add `cd_change_icon`.
- Marker filter axis renamed `pinned` → `icon` with values `WITH_ICON`/`WITHOUT_ICON`.
- Auto idle markers keep `icon = 🕐` but are no longer implicitly pinned.
- `TrackRecorder.hasDivingPinnedMarker()` / `topLocationIcon()` switch to pure icon semantics (drop the `&& pinned` condition).
- Cleanup: stale "Tri-state"/"SHOW_PINNED" comments, stale `allMarkers` "map pins render from this" comment, dead `WhereToVoteIcon()` + `where_to_vote` ImageVector, `IconPickerDialog` grid doc.

## 2. Pin Feature — Re-implemented (mirroring tracks)

| Layer | Marker implementation (mirrors tracks) |
|---|---|
| Data | `UserMarker.pinned: Boolean = false`; `isPinned get() = pinned`. Safe: repo uses `ignoreUnknownKeys` + default → no data migration. |
| Repo | `UserMarkerRepository.setPinned(id, pinned)` — mirrors `TrackRepository.setPinned`. |
| VM | `MarkersViewModel.setMarkerPinned(id, pinned)` — reload + re-filter/sort, mirrors `TrackViewModel.setPinned`. |
| Card | PushPin filled/outlined toggle in `MarkerCardContent` (`cd_pin`/`cd_unpin`), wired via `onSetPin`. |
| Drawer | PushPin toggle in `MarkerDrawer` viewing header. |
| Multi-select | `Pin` `MultiActionSpec` (pin_all / unpin_all / toggle) — mirrors `TrackHistoryOverlay`. |
| Filter | New `pinned` axis (All / Pinned / Unpinned) alongside the renamed `icon` axis. |
| Sort | None — tracks dropped "group pinned" in migration v4; parity means markers don't get it either. |

## 3. Rendering (binary layer)

- Order confirmed markers `sortedBy { it.pinned }` so pinned render last (top of the marker band; `OverlayZOrder` preserves intra-band order).
- Post-merge behavior: `MapScreen` renders from the **filtered** `markers` list (not `allMarkers`), so the new `pinned`/icon filter axes also gate map visibility — filtering "Pinned" hides unpinned markers from the map. `whereAmI` still matches against `allMarkers` (unfiltered).
- `baseColor` precedence: `highlighted` > `unconfirmed` > `matchResult != null && !isMatched` (dim) > `matchResult != null` (matched full) > `pinned` (full) > `unpinned` (dimmed). Unpinned dimming applies only when no `whereAmI` match result is active.
- `skipDots` (`icon != null && confirmed`) unchanged — icon still suppresses center dots; pin has no dot interaction.

## 4. Migration Surface

- Marker data: **none** (new field defaults false; `ignoreUnknownKeys` + JSON default handles old files).
- Settings: **one version bump** — rewrite persisted `markerListFilter` (`pinned=PINNED` → `icon=WITH_ICON`, `pinned=UNPINNED` → `icon=WITHOUT_ICON`).

## 5. Behavioral Change to Flag

Diving-tier auto-naming in `TrackRecorder` currently keys on `pinned && icon == 🤿`. After decoupling it keys on `icon == 🤿` (icon semantics), otherwise unpinned 🤿 markers would stop feeding the auto-naming tier.

## Files to Change

- `app/src/main/java/ykws/android/maro/data/model/markers/UserMarker.kt`
- `app/src/main/java/ykws/android/maro/data/markers/UserMarkerRepository.kt`
- `app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt`
- `app/src/main/java/ykws/android/maro/ui/map/MarkerManagementOverlay.kt`
- `app/src/main/java/ykws/android/maro/ui/map/MarkerDrawer.kt`
- `app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt`
- `app/src/main/java/ykws/android/maro/ui/map/MarkerOverlay.kt`
- `app/src/main/java/ykws/android/maro/ui/map/FanIconComponents.kt` (+ remove `WhereToVote.kt`)
- `app/src/main/java/ykws/android/maro/ui/map/IconPickerDialog.kt`
- `app/src/main/java/ykws/android/maro/ui/markers/wizard/steps/TypeSelectStep.kt`
- `app/src/main/java/ykws/android/maro/data/model/ListFilter.kt`
- `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt`
- `app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt`
- `app/src/main/res/values/strings.xml`

## Build & Tests

- `gradlew assembleDebug`.
- Unit tests for `UserMarker.matchesFilter` (pinned + icon axes) and the `markerListFilter` settings migration.
