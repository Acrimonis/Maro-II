# RegulatedZones — Hydration Snapshot

**Baked:** 2026-06-11 20:25 UTC

**Status:** data-lookup subfeature **complete**. All 9 implementation steps done. 34 tests pass.

**What was accomplished this session:**
- Public SHOM INSPIRE WFS endpoint discovered: `https://services.data.shom.fr/INSPIRE/wfs` (no auth)
- Real layer names: `REGLEMENTATION_NAVIGATION_BDD_WFS:*` (resare, splare, achare, ctsare, admare)
- EPSG:3857 → WGS84 coordinate conversion with auto-CRS detection
- Speed limit extraction from description text ("speed is limited to X knots")
- Vessel size filtering: `appliesTo()` with structured + text heuristic
- 72 live zones fetched for Nice-Fréjus corridor: speed limits (12), anchoring (12), fishing (5), navigation restrictions (3), access prohibited (1)
- Key zones detected: Golfe Juan 10 kn (827 vertices), Nice 5 kn/10 kn zones, Lerins anchoring prohibition
- `tools/test-regulated-zones.bat` — runs all 34 tests + opens HTML report
- ELI16 practical summary in plans/regulated-zones-readme.md
- Cleaned up `tools/test-regulated-zones.bat` (no BOM, no infinite loop)

**Source files:**
- `app/src/main/java/ykws/android/maro/data/regulation/` — 5 source files
- `app/src/test/java/ykws/android/maro/data/regulation/` — 7 test files
- `tools/bake-regulated-zones.bat`, `tools/test-regulated-zones.bat`
- `plans/regulated-zones-readme.md` (ELI16 + practical guide)

**Next step:** display-layer subfeature — render zones on map with per-type colours/legend.
