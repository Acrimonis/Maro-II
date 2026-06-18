# Hydration: BoatTrace

**Last Bake:** 2026-06-18 14:16 UTC
**Status:** active
**Active Subfeature:** adaptive-isstill

## Session Summary (2026-06-18 14:00–14:16)

**What happened:**
- TrackStatusIcon redesigned: 3 visual states with pulsing dot (16dp, top-right quadrant, 6dp padding)
- New `status.tracking.*` color properties in colors.properties (healthy/idle/off/dot colors/alpha)
- AppConfig fields + parsing for all 7 new tracking properties
- TrackStatusIcon dot colors: red when recording, white 80% when idle, no dot when OFF
- Virtual GpsFix flow ticker: 1Hz periodic emission in demo mode only, so AdaptiveGpsPolicy timer advances when map is stationary
- BUILD SUCCESSFUL

**State:**
- tracking-status-n-triggers [x]
- adaptive-isstill [x]
- All icon states functional: OFF dimmed white, ON+moving green+red dot, ON+idle blue+white dot

**Next step:**
- Deploy to device and verify icon states in demo mode
- Continue with verification and track-list subfeatures
