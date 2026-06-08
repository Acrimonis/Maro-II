# Context Hydration — isOnWaterAgain — 2026-06-08

**Active Subfeature:** none

## State
DONE and validated. `isOnWater` was inverting in thin vertical bands at marinas; the cause was
a vertical counting-ray degenerating against near-vertical coast, then (after a division-free
ray fix) residual tiny coastline **data artifacts**. Final fix: `CoastlineSpatialIndex.isWater`
is now **mainland-primary** — nearest-MAINLAND side test (`signedSide`/`classifyWater`/`cornerWater`)
OR inside a real **CCW island** (`insideRealIsland`, bbox-filtered PNPOLY). Index construction
**drops degenerate rings** (≤3 pts / ~0 area) and **tiny open fragments** (<30 m) and exposes
`usableSegments`. The 300 m band (in-app builders + offline `CoastlinePrebakeTest`) now uses
`index.isWater` + `index.usableSegments`, and `Zone300Builder.capOpenEnds` seals only
region-clipped (lon-extreme) ends — not interior fragment junctions (the band-spike source).
The bundled asset `nice-frejus.bin` was rebaked. All temp diagnostics removed.

## Target Files
- `spatial/CoastlineSpatialIndex.kt` — classifier + index cleaning + `usableSegments`.
- `spatial/SpatialOperations.kt` — `signedSide`.
- `spatial/Zone300Builder.kt` — `capOpenEnds` boundary-only.
- `data/coastline/CoastlineRepository.kt`, `data/prebake/CoastlinePrebakeTest.kt` — feed cleaned segments + `index.isWater`.
- Tests: `CoastlineSpatialIndexWaterTest.kt`, `SpatialOperationsTest.kt`, `BandValidationTest.kt`.

## Next Step
Optional only: clean spikes upstream in the coastline generator; on-structure (<30 m) points
(e.g. #4) stay inherently ambiguous. Branch `feature/isonwater-again` is NOT committed — stage/commit when ready.
