<!-- scope: feature -->

# Marker "Belongs to track" — show in list + selectable chevron

## Context

A marker can be associated with a track (created while recording). Today the "Belongs to track: [name]"
indicator appears only in the marker **detail drawer** ([`MarkerDrawer.kt:196`](app/src/main/java/ykws/android/maro/ui/map/MarkerDrawer.kt:196)),
display-only (no tap). It does NOT appear in the marker **list card**.

## Requirement (user-confirmed)

1. Show "Belongs to track: [name]" whenever the marker has an associated track, in **both** places a marker is
   shown: the marker **list card** and the marker **detail drawer**.
2. When there is an associated track, add a **chevron** on the right of that row to **select** the track —
   tapping it opens the **track detail drawer** for that track.

## Design

### Shared card
`MarkerCardContent` ([`MarkerManagementOverlay.kt:253`](app/src/main/java/ykws/android/maro/ui/map/MarkerManagementOverlay.kt:253))
is the shared marker card used by both the list and the detail drawer. It already receives `trackTitle`
([`MarkerManagementOverlay.kt:196`](app/src/main/java/ykws/android/maro/ui/map/MarkerManagementOverlay.kt:196)).
Add the "Belongs to track" row inside `MarkerCardContent` (shown when `trackTitle != null`), with a trailing
chevron when a track exists.

### Navigation callback
Add an `onOpenTrack: (() -> Unit)?` (or `onSelectTrack: (String) -> Unit`) parameter to `MarkerCardContent`.
- In the **list** (`MarkerManagementOverlay`), wire it to navigate to the owning track.
- In the **detail drawer** (`MarkerDrawer`), wire it to close the marker drawer and open the owning track's
  detail drawer.

### Drawer hand-off (MapScreen / OverlayLayer)
The track detail drawer is driven by `trackDrawerState` (a `TrackDrawerState` holding `.track`), shown via
`showTrackInfoDrawer` in `OverlayLayer`. To open a specific track from a marker:
- Add a way to open the track drawer for a given track id (e.g. a `TrackViewModel`/state method or a callback
  `onOpenTrackById(trackId)` that loads the track and sets `trackDrawerState`).
- Reuse `closeSelectedItemDashboards()` ([`MapScreen.kt:441`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:441))
  to close the marker drawer before opening the track drawer.

## Layout (user-confirmed)
The track row is a **new row at the bottom** of the marker card, showing just the track name (NO "Belongs to
track:" prefix) with a trailing chevron that opens the track's detail drawer:
```
│ █ Marker Name                          │
│ █ Description text...                  │
│ █ TrackName                        ▸   │  ← bottom row (track name + chevron)
```

## Implementation steps (refined per Ask review)
1. Add a nullable `onOpenTrack: (() -> Unit)? = null` param to `MarkerCardContent`; replace the existing small
   inline `🛰 $trackTitle` line (currently between title and description) with a **bottom row** showing just the
   track name + a chevron, shown only when `onOpenTrack != null` (not merely when `trackTitle != null`). Give the
   track row its own explicit `clickable`/`IconButton` with a distinct content description so its tap doesn't
   bubble to the card's `combinedClickable(onTap)`.
2. **Unconditionally delete** the drawer's own "Belongs to track" row ([`MarkerDrawer.kt:196`](app/src/main/java/ykws/android/maro/ui/map/MarkerDrawer.kt:196)) and pass
   `trackTitle` + `onOpenTrack` into the shared card from the drawer (avoids double render).
3. Wire the **drawer** callback: close the marker drawer + clear `navigateToTarget`, then call the existing
   `openTrackDetail(trackId)` ([`MapScreen.kt:2230`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:2230)).
4. Wire the **list** callback: close `showMarkerManagement` (the marker management overlay), then call
   `openTrackDetail(trackId)`.
5. Build + verify.

## Files Affected
- `app/src/main/java/ykws/android/maro/ui/map/MarkerManagementOverlay.kt` (shared row + list wiring)
- `app/src/main/java/ykws/android/maro/ui/map/MarkerDrawer.kt` (remove own row, pass trackTitle + callback)
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` (two small callback lambdas closing the right surface)
- No TrackViewModel/OverlayLayer plumbing changes needed (`openTrackDetail` already exists).

## Verification
- Build via `apk-build.bat`.
- Manual: a track-associated marker shows "Belongs to track" in both the list and the detail drawer; tapping the
  chevron opens that track's detail drawer.
