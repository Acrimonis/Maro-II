# Hydration: Ui_General

**Session:** Compact track/marker list cards — `TrackCardContent` / `MarkerCardContent`
tightened: description/comment `lineHeight = 14sp` (title 16sp, header 12sp, stats 12/13sp),
reduced description v-padding, asymmetric column padding (top 2dp / bottom 4dp), and the
header→title `HorizontalDivider` removed. Applies to both the list overlays and the detail
drawers over the dashboard (shared card composables). New auto stop-line description format
`HH:mm: Zone for Xmin` in `TrackRecorder.formatStopLine`. BUILD SUCCESSFUL.

**Target files:**
- `TrackHistoryOverlay.kt`, `MarkerManagementOverlay.kt`, `TrackRecorder.kt`

**Last Bake:** 2026-09-03 18:54 UTC
