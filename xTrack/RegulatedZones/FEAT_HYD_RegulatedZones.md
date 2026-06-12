# Hydration: RegulatedZones

**Last Bake:** 2026-06-12 21:49 UTC+2
**State:** Architectural design session complete. All planning docs created and approved by user.

## Summary

Designed multi-source data normalization for regulated zones. Key outcomes:
- **Data format:** 14 ProtoNumber fields with sealed `RegulationClassification` (S101, Catrea, Restrn, InpnMpa, Seed) and `SpeedSource` enum
- **Icon mapping:** 8 `ZoneDisplayCategory` values — SPEED_LIMIT (5/10 red), NO_ANCHOR (⚓ blue+strike), NO_ACCESS (🚤 blue+strike), MOORING (🛥️ blue), NO_DIVING (🤿 blue+strike), SEAPLANE (✈️ grey), ENVIRONMENTAL (🌿 blue), INFORMATION (ℹ️ blue)
- **Data extraction:** SHOM `REGNAV_BDD_WFS:resare` layer on same public INSPIRE endpoint + new `InpnRegulationClient` for INPN Marine Protected Areas
- **Backward compat:** Not required — old .bin regenerated
- **Scope:** 8 files, 1 new, 7 modified

## Target Files

- `RegulatedZone.kt` — Rewrite data model + displayCategories()
- `RegulatedZoneIconProvider.kt` — 2 new categories, fix colours
- `ShomRegulationClient.kt` — Add REGNAV layer, parse CATREA/RESTRN/INFORM/TXTDSC
- `InpnRegulationClient.kt` — NEW
- `RegulationAggregator.kt` — 3-way merge
- `RegulatedZonePrebakeTest.kt` — Wire INPN, expand bbox
- `MapScreen.kt` — Verify new categories
- `*.bin` — Delete and regenerate

## Next Step

`#focus multi-source-normalization` then switch to Code mode for implementation.
