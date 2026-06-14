# UI_Map — Hydration Snapshot

**Baked:** 2026-06-14 19:39 UTC+2

## Active State
- **Subfeature:** boat-center ✅
- **Commit status:** Uncommitted changes on `feature/boat-marker-and-colors`

## What Changed This Session
1. **Boat marker offset** — `CenterMarkerOverlay` in MapScreen.kt: Image and Canvas decoupled; boat marker shifted down by `finalSizeDp/2` so image top-center = map center (GPS antenna position). Cap arrow stays centered. Land dot unchanged.
2. **`BOAT_TIP_OFFSET` constant** — removed; arrow now draws from canvas midpoint (= map center) upward.
3. **Low-depth warning color** — new configurable `lowDepthWarningColor` property in `ZoneConfig.kt`, set to `#FFE53935` (bright red) in `zone.properties`.

## Target Files
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt`
- `app/src/main/java/ykws/android/maro/ui/map/ZoneConfig.kt`
- `app/src/main/java/ykws/android/maro/ui/map/LowDepthWarningBitmap.kt`
- `app/src/main/assets/zone.properties`

## Next Steps
- Deploy and verify boat marker offset on-device
- Verify low-depth warning shows red (not magenta)
