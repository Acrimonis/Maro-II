<!-- scope: feature -->
# Navigation — decisions

Decisions that outlive the plans that made them. Promoted from the archived plan
`xxArchive/260916_FEAT_PLN_Navigation_dashboard-position-source.md` when it was retired.

## Which position feeds which surface (decided 2026-09-16)

| Surface | Position | Why |
|---|---|---|
| Dashboard (water flag, distance, band state, speed-zone query, zone situation, ETA, exit) | the boat in GPS mode, the marker in demo or before the first fix | a pan on a stopped boat must not move what the dashboard says about the boat |
| Tag stack and its band sign (`markerInZone300`) | the map centre | the tag answers "what am I looking at" |
| Auto-reveal narrowing when the layer is hidden | the map centre | a user moving the map is looking for zones, so the reveal follows the view |
| Depth readout | its own centre (written by the pan and the follow camera) | it describes the view, not the boat |

The two points are different **on purpose**. Unifying them reintroduces one of the two faults: either a pan moves the dashboard, or the tag stops describing the view.

## Centre ownership

While auto-follow is suppressed the user owns the map centre: no GPS fix may write it until the recenter button or the resume timer hands it back (`syncCenterFromBoat`, `recenterNow`). Live fixes still feed `_gpsPosition`, the recording and, through the rule above, the dashboard.

## Why the pan keeps writing the centre

Removing the pan's write would be the smaller diff, but the tag stack's marker point depends on it. The selection point (`dashboardPositionFor`) is the seam, not the writer.

## Dead reckoning

`_gpsPosition` is the boat's estimate when fixes stop, capped by `DEAD_RECKONING_MAX_MS`; past the cap it stays at the last estimate rather than reverting to the marker — the right bias for a safety readout.
