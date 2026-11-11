---
name: Ozers
status: active
created: 2026-11-11 15:48
modified: 2026-11-11 16:03
---

# Feature: Ozers

**Description:**
Loose ends that belong to no single feature yet, and the map-temperature topic. Items here are parked until a feature owns them.

## Summary
- The in-band zone fix (`feature/track-dash-arriving-not-updated`) read OK on the device; the false-amber land check followed and awaits its device pass. The map-temperature topic routes here too.

## Todos
- [x] Bottom-left 5 kn tag missing on arrival at the home port: it reads the map centre (`markerInZone300`) — read OK 2026-11-11, closed
- [x] Check whether the home-port basin counts as water (`isOnWater`) — read OK 2026-11-11, closed
- [x] False amber across land: the look-ahead to a SHOM zone now stops at the first land sample (10 m step, `landCrossedBefore`), 1 774 tests and `apk-build` green; device pass owed — a spit narrower than 10 m can slip through, and a boat berthed within 10 m of land may drop a harbour zone
- [x] The entrance heads-up is one straight ray along the GPS bearing, noisy at low speed — read OK 2026-11-11, closed
- [x] Device pass of the in-band zone fix (landward approach shows 5 kn and the speed colouring) — read OK 2026-11-11

## Rules

## Key Files
- `app/src/main/java/ykws/android/maro/ui/map/NavigationViewModel.kt` — shore pipeline, `bandZoneExitUnknown`, the heading rays
- `app/src/main/java/ykws/android/maro/ui/map/DashboardPanel.kt` — distance and speed-limit cards

## Docs

## Implemented
