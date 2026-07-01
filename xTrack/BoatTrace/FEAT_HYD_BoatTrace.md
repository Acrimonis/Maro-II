# BoatTrace — Hydration Snapshot

**Baked at:** 2026-07-01 16:02 UTC
**Active Subfeature:** boat-markers (design)

## Session Summary

BoatMarker data model + idle auto-marker design finalised across two plans:

- `FEAT_PLN_BoatTrace_boat-markers.md` — BoatMarker infrastructure: MarkerSnapshot/BoatMarker proto types (Track @ProtoNumber 16), IdleSessionContext per idle period, IdleThresholdCallback abstraction, TrackEvent extensions (IdlePeriodStarted/Completed, DrawerAutoOpen/CloseRequested), two configurable thresholds in maro.properties
- `FEAT_PLN_BoatTrace_idle-auto-marker.md` — Idle auto-marker: 🕐 Pin created on idle entry (confirmed=false, keepable=false), confirmed to permanent if idle ≥ autoMarkerMinDurationSec (120s default), startup cleanup of keepable=false markers, MarkerOrigin.IDLE_AUTO enum, ICON_SET expansion (🐬🐚🏖️🕐, 16 icons, 4×4 grid)

12-step implementation plan defined. Both plans finalised after cross-review.

## Key Files (design only, no code yet)

- `xTrack/BoatTrace/FEAT_PLN_BoatTrace_boat-markers.md`
- `xTrack/BoatTrace/FEAT_PLN_BoatTrace_idle-auto-marker.md`
- `xTrack/GLOBAL_CONTEXT.md` — updated
- `xTrack/BoatTrace/FEAT_DSC_BoatTrace.md` — updated

## Next Steps

- [ ] Implement per 12-step plan
- [ ] Build + E2E verify
