---
name: Zone300SpeedBadge
status: done
created: 2026-06-14 15:15
modified: 2026-06-14 17:42
active_subfeature: none
---

**Description:** Speed limit badge for the 300m regulatory band, integrated into the existing regulated zone icon stack and info text panel at bottom-left of the map. The 300m zone's speed limit (5 kn) is injected as the highest-priority SPEED_LIMIT entry, replacing all regulated speed limit icons when the boat is inside the band.

## Subfeatures

### icon-integration  [x]
- Red "5" icon rendered via existing `RegulationZoneCategoryIcon` with SPEED_LIMIT category
- Injected at highest priority (bottom of stack) when `inZone300 == true`
- Regulated SPEED_LIMIT entries suppressed when in 300m zone
- Other regulated categories (anchoring, diving, environmental) unaffected

### info-text-integration  [x]
- Synthetic info line "🔴 300 m Zone — 5 nds" injected into `RegulatedZoneInfoText`
- Same 9 sp / 14 sp lh styling as all other info lines
- Only shown when `regulationInfoVisible` setting is enabled
- Regulated speed limit info lines suppressed when in 300m zone

## Todos
- [x] Add `inZone300` param to `RegulatedZoneWarningStrip`
- [x] Inject 300m zone as SPEED_LIMIT entry, suppress regulated SPEED_LIMIT
- [x] Add `inZone300` param to `RegulatedZoneInfoText`
- [x] Inject "🔴 300 m Zone — {n} nds" info line, suppress regulated SPEED_LIMIT
- [x] Remove standalone `Zone300SpeedBadge` composable
- [x] Remove `suppressSpeedLimit` param (absorbed into `inZone300`)

## Rules
- 300m zone always replaces regulated speed limit icons when boat is inside the band
- Other regulated zone categories continue to render normally

## Key Files
- app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt

## Docs
- `xTrack/Zone300SpeedBadge/FEAT_PLN_Zone300SpeedBadge_design.md` — Zone 300m speed badge design
