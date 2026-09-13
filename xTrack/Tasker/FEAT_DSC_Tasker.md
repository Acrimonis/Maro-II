---
name: Tasker
status: active
created: 2026-09-12 08:50
modified: 2026-09-12 08:50
---

# Feature: Tasker

**Description:**
Automation bridge that exposes the boat's water state to Tasker, so external profiles can react to
it. Maro derives water state from the GPS fix (`isOnWater`) into a StateFlow; `TrackRecordingService`
holds `lastKnownOnWater` and publishes it two ways — a push broadcast on every toggle
(`WATER_STATE_CHANGED`) and an on-demand query/response pair (`QUERY_WATER_STATE` →
`WATER_STATE_RESULT`). Architecture approved; implementation not started.

## Docs
- `xTrack/Tasker/260628_FEAT_PLN_Tasker_tasker-water-state-integration.md` — architecture and implementation plan
