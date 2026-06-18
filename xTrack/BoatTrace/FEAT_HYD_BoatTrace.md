# Hydration: BoatTrace

**Last Bake:** 2026-06-18 12:44 UTC
**Status:** active
**Active Subfeature:** tracking-status-n-triggers

## Session Summary (2026-06-18)

**What happened:**
- AdaptiveGpsPolicy simplified to pure position-only algorithm — removed wakeSpeedMps, lastPos jump detection, drift logic
- SettingsManager fields replaced: adaptiveWindowSec→stopDetectionTimeSec (default 45, range 10-90), adaptiveDistanceM→stopDetectionDistanceM (default 15), adaptiveIdleIntervalSec removed
- New settings: stopDetectionEnabled (default true), stopDetectionDelayGps (default true)
- MapScreen "Idle saving" section replaced with new "Stop detection" section: enable toggle → conditional thresholds expander → conditional delay toggle. Bottom padding added.
- CoastlineViewModel onFix() call updated, dormant interval = stopDetectionTimeSec * gpsDormantPct / 100
- TrackRecorder onFix() calls fixed (speedMps arg removed)
- Strings updated in EN and FR for new description texts
- BuildConfig added: STOP_DETECTION_GPS_DORMANT_PCT (reads from maro.properties, default 80)
- BUILD SUCCESSFUL

**State:**
- tracking-status-n-triggers [x] — all implementation items done, visual verification remains
- adaptive-isstill [x] — all implementation done, BUILD SUCCESSFUL

**Next step:**
- Verify TrackStatusIcon visually matches GPS and EarthWater icons on device
- Deploy and test demo mode tracking
- Continue with verification and track-list subfeatures
