# Plan: Live Track Card in Track History

## Design

When recording is active (`state == ON`), insert a live track card at position 0 in the track history list. It looks like a regular track card but with:

1. **Pulsing border** in dot color (red when moving, blue/white when idle)
2. **Top line:** date + "-> ..." + pulsing dot (🔴/🔵) right-aligned — no eye/export buttons
3. **Editable name and comment** — same inline edit as saved tracks
4. **Live stats** from `TrackRecorderUiState` instead of `TrackSummary`

### Layout

```
┌── pulsing border (🔴/🔵) ─────────────────┐
│ 2026-06-18  14:32 -> ...              🔴  │
│───────────────────────────────────────────│
│ [Editable name: "2026-06-18 14:32"]       │
│ [Editable comment: "Recording..."]        │
│───────────────────────────────────────────│
│ Total: 32m  Nav: 32m  Avg: 6.5kn         │
│ Dist: 4.2nm  Idle: 0m  Max: 12.0kn       │
└───────────────────────────────────────────┘
```

### Pulsing dot

- Same animation as TrackStatusIcon: `infiniteRepeatable(tween(800ms), RepeatMode.Reverse)`
- Color: `statusTrackingDotRecording` (red) when moving, `statusTrackingDotIdle` (white 80%) when idle
- Positioned in top-right where eye/export buttons normally go, same 36dp icon button area

### Pulsing border

- `Modifier.border()` with animated color
- Same animation: dot color at full alpha → 30% alpha, infinite repeat
- `RoundedCornerShape(12.dp)` to match card shape

### Stats source

| Stat | Source |
|---|---|
| Total | `recorderState.elapsedSeconds` |
| Nav | `recorderState.elapsedSeconds` (no pause) |
| Avg | `recorderState.avgSpeedKn` |
| Dist | `recorderState.distanceNm` |
| Idle | `0` (no pause state) |
| Max | `recorderState.maxSpeedKn` |

## Files to modify

| File | Change |
|---|---|
| `TrackHistoryOverlay.kt` | Add `liveTrackState: TrackRecorderUiState?` param. Insert `LiveTrackCard` at index 0 if non-null. New `LiveTrackCard` composable with pulsing border + dot + editable name/comment + live stats. |
| `MapScreen.kt` (call site ~line 832) | Pass `trackRecorderState` to `TrackHistoryOverlay` |
