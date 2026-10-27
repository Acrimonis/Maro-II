<!-- scope: feature -->
# Stage 4 — notification ticker and background display acquisition

## Scope and disposition
Independent branch from upstream develop `b1f40aa211c02c2882bb676514bee753cecacebe`. Retained the stable notification ticker and foreground-only display acquisition. Device performance remains **unverified**; there is no device or battery pass.

The old Compose effect keyed the entire recorder UI snapshot. Every elapsed/statistics update cancelled the five-second delay and immediately sent another service intent. The effect now collects distinct recording, GPS mode, water and movement keys from flows, reads current recorder/navigation values at each tick, and preserves immediate semantic transitions. Its combined water transition replaces the separate water intent; the service still owns the Tasker broadcast. Flow collection does not require a Compose frame to refresh captured values while the Activity is paused. The notification cadence constant is unchanged.

Pause/disposal now disables display acquisition even while recording. Its existing enabled flow gates UI location, compass and the display predictor; disabling also cancels the stale-fix watchdog and lost-fix prediction. A late prediction request checks foreground state. Recording remains owned by TrackRecordingService with its existing GPS listener, cadence and recovery logic. The service publishes an advisory fix stream for background water classification, without another location listener or a suspending send in the recording path. GPS recording notifications use an atomic service-owned motion snapshot; display intents omit movement and speed in this mode. Ready and demo notifications retain their display values. Recorder source is unchanged.

## Proportionate baseline and counters
`NotificationTickerTest` retains the original restart-on-stat-change schedule as the reference and executes both schedules with the existing coroutine virtual-time scheduler:

| Workload | Reference | Candidate |
|---|---:|---:|
| 60-second recording, statistics updated each second | 60 sends | 12 sends |
| Send timestamps, half-open minute | every second | 0, 5, …, 55 seconds |
| Stop after two seconds | initial + final | initial + final |
| Mode/water transition after 1.2 seconds | immediate | immediate |

Each periodic tick reads the then-current statistics. Cancellation produces no later sends. These are deterministic scheduling measurements, **not Android IPC latency or CPU measurements**. They pin the avoided repeated work without pretending a JVM callback measures startService/notification cost. Settings and water transitions intentionally remain immediate and can add sends.

Background display listener/projection counts, resume latency, real notification rendering, task removal, device GPS traces and battery consumption remain unmeasured. Source inspection confirms the service recording listener remains independent with unchanged sampling parameters. No lower GPS rate or reduced visible detail was introduced. No full-app speedup is claimed.

## Validation
The debug APK and 23 targeted notification, display handoff, adaptive GPS and recorder tests pass (0 failures, 0 errors, 0 skipped); device lifecycle/recording continuity requires user-controlled evidence and remains unverified.

The review correction pins stale-display rejection through moving → idle → moving, Ready/demo fallback, live statistics and immediate semantic transitions without recomposition. The handoff fixture retains all seven samples on its reference service consumer and delivers only the three background/GPS/recording samples to water classification, without stale replay. These are deterministic ownership checks, not on-device continuity measurements.
