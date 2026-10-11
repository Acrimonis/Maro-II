# Context Hydration — RegulatedZones — 2026-10-10

**Last Bake:** 2026-10-10 21:05 UTC — written by `#bake`; absence means never baked

**Directive trace:** Since the last bake, none of the five covered action classes stopped the work — no dependency added, no binary opened (the `.bin` was probed only through the diagnostic), no device touched, no work started without an order, and every code claim had a file read behind it.

## State

The regulated-zone set is now controlled app side by `app/src/main/assets/zones.properties`, a generated asset next to `maro.properties`: the bake seeds one block per zone with both `regulatedZone.speedOverride.*` and `regulatedZone.ignore.*` lines plus a full-info comment block, and `RegulatedZonesRepository.load` applies it — an ignored zone is dropped, a speed override replaces the limit, and a value on a zone with none makes it a speed zone. The seed preserves only a `# name` label the user edits; the values and flags survive across re-bakes. Alongside, the classification was corrected — `parseRestrictionCode` and `parseRestrnAuth` now read one S-57 RESTRN table (`restrictionType`), so restriction-1 zones read `ANCHORING_PROHIBITED` instead of a speed zone — and `cleanText` drops the literal `"null"`. Unit tests, the prebake and `apk-build.bat` are green on `feature/zonetile`.

## Target Files

- `app/src/main/assets/zones.properties` — the generated, app-applied override asset
- `app/src/main/java/ykws/android/maro/data/regulation/RegulationSpeedOverrides.kt` — fold, key, parse, load, apply, seed
- `app/src/main/java/ykws/android/maro/data/regulation/RegulatedZonesRepository.kt` — applies the asset at load
- `app/src/main/java/ykws/android/maro/data/regulation/ShomRegulationClient.kt` — the RESTRN table, `cleanText`, the name/speed/description parsing
- `app/src/test/java/ykws/android/maro/data/regulation/RegulatedZonePrebakeTest.kt` — the bake entry point
- `xTrack/RegulatedZones/261010_FEAT_PLN_RegulatedZones_speed-override-family.md` — the plan

## Next Step

Device pass: edit a value or an ignore in `zones.properties`, rebuild, and confirm the app honours it; then decide whether the runtime `effectiveSpeedLimitKn()` re-map, which still catches the "outside the channel" zone, should be reconciled.
