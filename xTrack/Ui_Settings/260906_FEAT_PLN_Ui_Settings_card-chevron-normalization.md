<!-- scope: feature -->

# Card Layout Normalization — chevron in header + normalized track-row chevron

## Context

The marker card (and track card) currently render the "open details" chevron at the **bottom-end** of the card
([`MarkerManagementOverlay.kt:481`](app/src/main/java/ykws/android/maro/ui/map/MarkerManagementOverlay.kt:481)). After the
"Belongs to track" feature, a marker with a track shows a **second** chevron on the track row
([`MarkerManagementOverlay.kt:471`](app/src/main/java/ykws/android/maro/ui/map/MarkerManagementOverlay.kt:471)) — different
size (28dp vs 20dp) and color (muted vs accent). The rendering is cluttered and inconsistent.

## Requirement (user-confirmed)

1. **Move the "open details" chevron into the card's header row** (top-right, after the action icons) — for BOTH
   the marker card and the track card.
2. The **track row** (marker card, when a track exists) keeps its chevron at the right, but **normalized to the
   same size** as the header chevron.
3. Update the UI guidelines.

## Target layout

**Marker card** (when it has a track):
```
┌──────────────────────────────────────────────┐
│ █ [coords]          📌 🎨 ✏️   ▸             │  ← header: coords + actions + OPEN-MARKER chevron
│ █ Marker Name                                │
│ █ Description text...                        │
│ ───────────────────────────────────────────  │  ← divider
│ █ TrackName                              ▸   │  ← track row: name + OPEN-TRACK chevron (same size)
└──────────────────────────────────────────────┘
```

**Track card:**
```
┌──────────────────────────────────────────────┐
│ █ [date/time]        📌 ⤴   ▸                │  ← header: date + actions + OPEN-TRACK chevron
│ █ Track Name                                 │
│ █ Comment / stats...                         │
└──────────────────────────────────────────────┘
```

## Canonical chevron token (user-confirmed)
**28dp, muted** (`uiSettingsTextMuted`) — matching the original bottom chevrons. Both the header chevron and the
track-row chevron use this single canonical size/color.

## Implementation steps

### Marker card (`MarkerManagementOverlay.kt` — `MarkerCardContent`)
1. Remove the bottom-end `showChevron` Box ([`MarkerManagementOverlay.kt:481`](app/src/main/java/ykws/android/maro/ui/map/MarkerManagementOverlay.kt:481)).
2. Add the "open marker" chevron to the **header row** (far right, after the pin/icon/edit IconButtons,
   [`MarkerManagementOverlay.kt:309`](app/src/main/java/ykws/android/maro/ui/map/MarkerManagementOverlay.kt:309)), at the
   canonical size/color. Gate it with the existing `showChevron` param (default true; detail drawer already passes
   false at [`MarkerDrawer.kt:197`](app/src/main/java/ykws/android/maro/ui/map/MarkerDrawer.kt:197) — no change needed there).
   Keep it a compact plain `Icon` (not a 36dp IconButton) to limit header crowding.
3. Normalize the **track-row chevron** ([`MarkerManagementOverlay.kt:471`](app/src/main/java/ykws/android/maro/ui/map/MarkerManagementOverlay.kt:471))
   to the canonical size/color. Keep it a non-interactive `Icon` inside the clickable row (no double-fire).

### Track card (`TrackHistoryOverlay.kt` — `TrackCardContent`)
4. **MOVE** the existing unconditional bottom chevron ([`TrackHistoryOverlay.kt:669`](app/src/main/java/ykws/android/maro/ui/map/TrackHistoryOverlay.kt:669))
   into the header row (far right, after the action icons) at the canonical size/color — do NOT add a second one.
5. Add a `showChevron: Boolean = true` param to `TrackCardContent` (mirroring the marker card) and thread `false`
   through the three `onTap = null` call sites in [`OverlayLayer.kt`](app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt:451,538,556)
   (detail drawers + MeasureHeight pass); the list call site ([`TrackHistoryOverlay.kt:374`](app/src/main/java/ykws/android/maro/ui/map/TrackHistoryOverlay.kt:374))
   keeps it true.

### Guidelines
6. Update `docs/ui-drawer-guidelines.md` §9 (list-item card pattern) shell snippet + Shared Tokens to include the
   header chevron and its canonical size/color; reconcile with the §10 Navigation token (28dp muted) so they don't
   conflict. Update `docs/ui-component-guidelines.md` if it references the card chevron.

## Verification
- Build via `apk-build.bat`.
- Manual: marker card shows the open-marker chevron in the header (list) and the track-row chevron (same size);
  track card shows the open-track chevron in the header; NO bottom-end chevron remains on either card.
- Detail-drawer contexts (marker drawer, track info drawer) show NO chevron; the `MeasureHeight`/drawer-height
  path still fits after the bottom chevron removal.

## Files Affected
- `app/src/main/java/ykws/android/maro/ui/map/MarkerManagementOverlay.kt` (MarkerCardContent header chevron + track-row chevron)
- `app/src/main/java/ykws/android/maro/ui/map/TrackHistoryOverlay.kt` (TrackCardContent header chevron)
- `docs/ui-drawer-guidelines.md` / `docs/ui-component-guidelines.md`

## Verification
- Build via `apk-build.bat`.
- Manual: marker card shows the open-marker chevron in the header (list) and the track-row chevron (same size);
  track card shows the open-track chevron in the header; no bottom-end chevron remains.
