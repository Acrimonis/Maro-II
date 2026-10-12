# Context Hydration — RegulatedZones — 2026-10-12

**Last Bake:** 2026-10-12 15:46 UTC — written by `#bake`; absence means never baked

**Directive trace:** Since the last bake, none of the five covered action classes stopped the work — no dependency added, no binary opened (the `.bin` was only read through diagnostics), no device touched, no work started without an order, and every code claim had a file read behind it.

## State

The SHOM INSPIRE bake parses the real shapes — the whole comma-separated `restrn` list with speed-wins typing, ACHARE anchorages from `catach`, `Point` buffering, `MultiPolygon` split, `sourceLayer` and a server-side bbox — and `regulatedZone.speedRoute.<key>` now steers only the route engine while every display surface keeps the override. Each seeded `zones.properties` block carries `# layer`, `# codes` and a `# geometry` WKT outline simplified by adaptive RDP to at most 8 vertices. A read-only schema probe showed the endpoint serves only lowercase `restrn`/`catrea` (the only unread fields are `perend`, `persta`, `status`) and no `vitesse_max`/`TXTDSC`, so the decree stays empty; `ctsare_polygon` and `admare_polygon` yield no zones. Prebake, scoped tests and `assembleDebug` are green on `feature/shom`.

## Target Files

- `app/src/main/java/ykws/android/maro/data/regulation/ShomRegulationClient.kt` — parsing, `classifyZone`, the code tables, the bbox
- `app/src/main/java/ykws/android/maro/data/regulation/RegulatedZone.kt` — `restrictionCodes`, `speedRouteKn`, `displayCategories`
- `app/src/main/java/ykws/android/maro/data/regulation/SpeedZone.kt` + `SpeedZoneBuilder.kt` — the routing-only `routeLimitKn`
- `app/src/main/java/ykws/android/maro/spatial/multipass/RouteGridBuilder.kt`, `RouteFinePass.kt`, `ZoneGeometry.kt` — the route engine's `routeLimitKn` reads
- `app/src/main/java/ykws/android/maro/data/regulation/RegulationSpeedOverrides.kt` — the three key families and the seed's comment block
- `app/src/main/assets/zones.properties`, `maro.properties`, `colors.properties` — the seeded zones, the filter types, the speed red
- `app/src/test/java/ykws/android/maro/data/regulation/ShomWfsSchemaProbeTest.kt` — the schema probe

## Next Step

Device pass: confirm the map polygons and the bottom-left tags read correctly after the speed-red change and that a `speedRoute` value steers the route without moving any displayed limit; then decide whether the two empty layers (`ctsare_polygon`, `admare_polygon`) and the parked RESTRN 9 dredging gap are worth pursuing.
