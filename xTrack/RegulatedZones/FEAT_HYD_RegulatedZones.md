# Context Hydration — RegulatedZones — 2026-10-12

**Last Bake:** 2026-10-12 13:25 UTC — written by `#bake`; absence means never baked

**Directive trace:** Since the last bake, none of the five covered action classes stopped the work — no dependency added, no binary opened (the `.bin` was only read through the diagnostic), no device touched, no work started without an order, and every code claim had a file read behind it.

## State

The SHOM INSPIRE bake now parses the real shapes: `restrn` is a comma-separated S-57 code list kept whole in `RegulatedZone.restrictionCodes`, and `zoneType` resolves speed-wins first (a parsed speed limit → `SPEED_LIMIT`) then `type_reglementation` then the first code; `achare_*` is classified from `restrn`/`catach` (1/8 = positive `MOORING`), `Point` zones buffer into a 100 m ring, `MultiPolygon` features split into one zone per polygon, `sourceLayer` records the typeName, and the fetch sends a server-side EPSG:3857 bbox. Fishing is included (the hard filter is empty), `mouillage` maps to MOORING, and the strip's speed tag uses its own alert red with a uniform `regulatedZone.category.background`; the info text falls back to the category label. The type audit, the re-bake (`132` zones) and `assembleDebug` are green on `feature/shom`.

## Target Files

- `app/src/main/java/ykws/android/maro/data/regulation/ShomRegulationClient.kt` — the `restrn`/RESTRN parsing, `classifyZone`, `restrictionType`, `parseCatrea`, `parseZoneType`, the server-side bbox
- `app/src/main/java/ykws/android/maro/data/regulation/RegulatedZone.kt` — `restrictionCodes` and `displayCategories`
- `app/src/main/java/ykws/android/maro/data/regulation/RegulationFilter.kt` — the empty hard filter
- `app/src/main/java/ykws/android/maro/ui/map/RegulatedZoneIconProvider.kt` + `RegulatedZoneComponents.kt` — the category colours and the info-text label
- `app/src/main/assets/colors.properties`, `maro.properties`, `zones.properties` — the speed red, the strip background, the filteredTypes and the seeded zones
- `xTrack/RegulatedZones/FEAT_DSC_RegulatedZones.md` — the feature record

## Next Step

Device pass: confirm the map polygons and the bottom-left tags read correctly after the speed-red and category changes; then decide whether the parked RESTRN 9 dredging gap is worth a new category.
