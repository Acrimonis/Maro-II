# BoatTrace — Auto-Created Markers: Functional & Technical Value Evaluation

**Date:** 2026-08-31
**Context:** `#focus track` → discussion on the value of auto-created markers (IDLE BoatMarkers / 🕐 pins) during track recording.

## What they are

- **IDLE BoatMarker** — auto-created at idle-stop entry/exit using the compound idle predicate `(d/Δt < 0.5 m/s) AND (d < 500 m)`.
- **MANUAL BoatMarker** — user-dropped during recording, pre-captured `MarkerSnapshot` names.
- **🕐 pins** — map-overlay visualization of IDLE markers with transparency.
- **populate-track-info** — silent `whereAmI()` calls at idle stops auto-fill track title/description (3-tier priority: 🤿 diving pinned marker > MANUAL > longest IDLE).

## Functional value (user-facing)

1. **Zero-effort stop logging** — every meaningful stop (dive site, anchorage, mooring, fishing spot) is recorded with no interaction.
2. **Auto-generated track names/descriptions** — `[loc1 -> loc2]` instead of a bare timestamp; makes history browsable and GPX exports meaningful.
3. **Spatial trip recall** — 🕐 pins give an at-a-glance map of where the boat stopped, not just a line.
4. **Accurate duration stats** — separates navigating vs idle time so a 2h34m dive stop doesn't inflate speed/distance.

## Technical value (under the hood)

1. **Data-derived idle/navigation classification** — the marker lifecycle is the raw signal for `idleDurationSec` / `navigatingDurationSec`.
2. **Resume/merge integrity** — marker timestamps feed resume-seam GAPs and finalize sweep-close of open IDLE markers.
3. **Semantic enrichment** — `whereAmI()` binds raw coordinates to named zones/landmarks.
4. **Stats recompute (schema 4)** — relies on marker-derived idle segments.

## Costs / risks

1. **Stillness threshold sensitivity** — drift / anchor-swing can produce false or missed idle markers.
2. **Map clutter** — a noisy GPS day yields many 🕐 pins (transparency mitigates but doesn't eliminate).
3. **Semantic mislabeling** — `whereAmI()` zone names can be wrong or ambiguous offshore.
4. **Silent overhead** — periodic `whereAmI()` + full BoatMarker-history recompute during recording costs CPU/IO/battery.
5. **Maintenance coupling** — recorder, ViewModel, overlay, and merger all touch the marker model; changes ripple.

## Verdict

Yes — genuine technical value. The highest-value role is **data-model**: idle/nav time accounting and automatic track titling/description. The 🕐 map pins are the least essential element — a derivative visualization that could be dropped or thinned without losing the core value.
