# Zone300SpeedBadge — Hydration Snapshot

**State:** All changes implemented and tested. Build: green.

## Summary
- Injected 300m zone speed limit into `RegulatedZoneWarningStrip` and `RegulatedZoneInfoText` as highest-priority SPEED_LIMIT entry
- Removed standalone `Zone300SpeedBadge` composable
- Regulated SPEED_LIMIT entries suppressed when in 300m zone
- All other regulated zone categories unaffected

## Target Files
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt`

## Next Step
- Commit and push to `feature/300m-speed-badge`
