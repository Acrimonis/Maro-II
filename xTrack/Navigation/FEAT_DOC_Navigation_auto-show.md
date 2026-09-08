---
feature: Navigation
scope: auto-show — re-display of 300 m band + regulated zones on approach (GPS and demo)
status: active
created: 2026-09-08 16:28
---

# Navigation — Auto-Show Zones (consolidated reference)

Single authoritative reference for the **auto-show / re-display of the 300 m band and regulated
zones** on approach. Consolidates legacy knowledge that previously lived in other features. The
engine code is in `NavigationViewModel.kt`; this doc maps concepts to code, settings, and the
UI/gate side, and records history/decisions.

## Scope

- **300 m band** (the shoreline regulatory band) — auto-show and auto-hide around the approach.
- **Regulated zones** = **speed zones** (speed-limit subset, e.g. SHOM) + **non-speed regulated
  zones** (anchoring / access / environmental categories).
- Auto-show is **proximity driven**: approaching within the reveal distance (or reveal time at SOG)
  brings the overlay up; manual user state is never overwritten (decoupled overlay flows).

## Engine (code)

| Concept | Location |
|---|---|
| Memory-only overlay flows, separate from the user setting | `NavigationViewModel.kt:241-246` (`_zone300OverlayVisible`, `_regulatedZoneOverlayVisible`) |
| Shore recompute pipeline on `_mapCenter.sample(...)` | `NavigationViewModel.kt:461-464` |
| Position source per mode — demo uses the map centre (pan) | `feedDemoPosition` `NavigationViewModel.kt:1195`, callers `MapScreen.kt:1181-1183`, `:1672-1674` |
| SOG per mode — GPS `speedKnots`; demo `demoSpeedKnots ?: (inZone ? 0f : null)` | `NavigationViewModel.kt:604-608` |
| Heading per mode — GPS `bearingDeg`; demo `demoBearingDeg ?: 0` (**`demoBearingDeg` is never set** — demo heading is always 0° north; two-finger rotation + `demoHeadingUp` only rotates the map via `bearingDeg`, it does not feed the auto-show cone) | `NavigationViewModel.kt:496-497`, `:1209`, `MapScreen.kt:806-818` |
| Global gate `(gpsMode ? approachAutoShowGps : approachAutoShowDemo) && autoShowMasterOverride` | `NavigationViewModel.kt:610-611` |
| 300 m band auto-show block | `NavigationViewModel.kt:613-644` |
| Regulated speed + non-speed blocks (independent, OR'd) | `NavigationViewModel.kt:647-673` |
| Demo pan-speed lifecycle — set on scroll, cleared after `PAN_STOP_DELAY_MS` (500 ms) idle | `NavigationViewModel.kt:675-680`, `:1144`, `:1173`, `:1576` |
| Pure decision `zoneAutoShowDecision` (armed / approaching / reveal / hide) | `NavigationViewModel.kt:1732-1802` |
| Zone proximity source for non-speed reveal | `zoneStatus` / `boundaryInCone` used at `NavigationViewModel.kt:570-575` |

### Decision semantics (pure function)

- **Reveal (outside)** — requires `armed` (zone was manually hidden), boat **approaching**
  (`dist < prevDist`), still outside, and within `revealDistM` **or** `timeToZone <= revealTimeS`.
- **300 m band hide** (`hideOnCompliantInside=true`) — hide when compliant inside, exited seaward,
  or retreated past the reveal margin; re-show when inside and non-compliant.
- **Speed zone** (`hideOnCompliantInside=false`) — stays visible while inside; reveal immediately when
  inside even if the outside window was missed; hide when stopped+idle, retreated, exited past margin,
  or location unknown.

## Settings (owner: Ui_Settings — referenced, not duplicated)

The data model and gate originate in the Ui_Settings redisplay design and the overlay decoupling design:

- [`260902_FEAT_PLN_Ui_Settings_approach-redisplay.md`](../Ui_Settings/260902_FEAT_PLN_Ui_Settings_approach-redisplay.md)
  — settings model: `approachAutoShowGps` / `approachAutoShowDemo`, per-type switches
  (`zone300AutoShow`, `speedZoneAutoShow`, `regulatedZoneAutoShow`), `autoShowMasterOverride`,
  `zoneAutoRevealDistanceM`/`S`; drawer "Auto-show zones" master switch; prefs migration v5→6.
- [`260617_FEAT_PLN_Ui_Settings_auto-show-settings-decoupling-design.md`](../Ui_Settings/260617_FEAT_PLN_Ui_Settings_auto-show-settings-decoupling-design.md)
  — decoupling design (implemented): auto-show writes memory-only overlay flows, never the persisted
  user setting; render OR = user setting || overlay flow.

### Behaviour matrix (from decoupling design)

| User setting | Auto-show state | Visual result | Fan shows |
|---|---|---|---|
| OFF | OFF | Hidden | Off |
| OFF | ON (approaching) | Shown | Off |
| ON | OFF | Shown | On |
| ON | ON (approaching) | Shown | On |

## UI / render / overlay lifecycle (owners: ZoneTile, UI_Map, Coastline — referenced)

- **Render OR gate** — map reads user setting OR the overlay flow to draw the band/regulated polygons
  and the warning strip. Overlay diffing/lifecycle details:
  [`Coastline/260704 per-layer update`](../Coastline/260704_FEAT_PLN_Coastline_coastline-mapview-per-layer-update.md)
  and `UI_Map` map-refresh / overlay docs.
- **300 m auto-show overlay churn** — visibility-only toggles and full-rebuild behaviour:
  [`ZoneTile/260617 zone300-auto-show-stutter-fix`](../ZoneTile/260617_FEAT_PLN_ZoneTile_zone300-auto-show-stutter-fix.md)
  (stays in ZoneTile; overlay-render scope).

## History / decisions (consolidated)

Superseded-but-relevant decisions from the legacy regulated-zone overlay auto-show plan
([`ZoneTile/260617 speed-enforcement-zone-auto-show-plan`](../ZoneTile/260617_FEAT_PLN_ZoneTile_speed-enforcement-zone-auto-show-plan.md),
now stubbed to this doc):

- Auto-show targets the regulated-zone overlay through the same proximity decision used for the band.
- Original scope decision: speed-enforcement zones only; non-speed categories excluded at that time.
  Later superseded by the redisplay rework, which adds independent speed + non-speed switches.
- Warning strip stays coupled to the overlay (not independently auto-shown); `regulationInfoVisible`
  text panel stays manual.
- Manual-hide override (`ManuallyHidden` / `AutoRevealed` flags) prevents auto-show from re-revealing
  right after a user hides a layer; the band block still relies on the `armed` flag.
- Legacy `speedZonesVisible` was a dead overlay flag (no UI reader); removed in the redisplay rework.

## Current task

Demo-mode non-reveal validation and fix — see
[`260908_FEAT_PLN_Navigation_auto-show-demo-mode.md`](260908_FEAT_PLN_Navigation_auto-show-demo-mode.md).

## Provenance (no-duplication mapping)

| Legacy file | Disposition |
|---|---|
| `ZoneTile/260617 speed-enforcement-zone-auto-show-plan` | Folded into this doc; original stubbed to a pointer |
| `ZoneTile/260617 zone300-auto-show-stutter-fix` | Stays ZoneTile (overlay-render); cross-referenced here |
| `ZoneTile/260614 speed-zones-design` + `speed-zones-heading-distance` | Remain in ZoneTile (zone engine/distance scope); auto-show portions superseded by this doc + Ui_Settings redisplay |
| `Ui_Settings/260902 approach-redisplay` | Settings owner, referenced above |
| `Ui_Settings/260617 auto-show-settings-decoupling-design` | Settings/decoupling owner, referenced above |
