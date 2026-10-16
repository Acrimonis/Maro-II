# Context Hydration — Route — 2026-10-16

**Last Bake:** 2026-10-16 14:54 UTC — written by `#bake`; absence means never baked

**Directive trace:** The session opened on the user's `#focus route` and a read of the feature's backlog, then ran two `#impl` pipelines — the marker price's own arm, and the presence-versus-price split of the forced-crossing report. No dependency was added, no machine-shaped data file was opened, no work was started without an order, the device was never touched, and the only git operations invoked are the `#bake` and `#commit` that close the session; nothing stopped.

## State

Two landings shipped on 2026-10-16, each through `#impl`'s whole pipeline with the unit suite and packaging green at the hop. **The price report names its own arms** — the priced-marker `Soft` source in `markerSources()` declares `armAt = { p -> PriceArm.MARKER to markerPriceSecAt(p) }` off the same local lambda that feeds its `priceSec`, so the arm can never disagree with the seconds the source charges, and the pull's `SWEEP arms` line reads `marker=` where the charge was lumped into `other`. **The report then split in two** — `forcedCrossingNames` keeps filtering on the **price** (the probe, `restrictive` and the doubled-reach retry unmoved) while `crossedSlowZoneNames` names the zones the **drawn line enters** whose **displayed** limit stands below the pace, the two sharing `zonesEnteredBy`, both carried as **records** on `RouteResult.Success` and the plan and printed as `crossed=[…]` beside `forced=[…]` on the engine's answer trace. No drawn line, clock, ranking, string or properties key moved in either hop, and the plan of record, decision 5 of the decisions doc and the feature file's own state carry the record.

## Target Files

- `app/src/main/java/ykws/android/maro/spatial/multipass/RouteMarker.kt` — the priced-marker source's `armAt` and its KDoc
- `app/src/main/java/ykws/android/maro/spatial/multipass/ZoneGeometry.kt` — `zonesEnteredBy`, `forcedCrossingZoneNames` and `crossedSlowZoneNames`
- `app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt` — the answer's two facts, the `crossed=[…]` trace beside `forced=[…]`
- `app/src/main/java/ykws/android/maro/data/model/RouteResult.kt` — the two record fields and their corrected KDoc
- `app/src/main/java/ykws/android/maro/ui/map/RouteViewModel.kt`, `RouteOverlay.kt` — the plan's carriage of both facts and the two saved-route constructions
- `app/src/test/java/ykws/android/maro/spatial/multipass/RouteMarkerCostTest.kt`, `RouteZonePhase4Test.kt`, `app/src/test/java/ykws/android/maro/spatial/RouteAvoidEngineTest.kt`, `app/src/test/java/ykws/android/maro/ui/map/RoutePlanTest.kt` — the witnesses each hop left
- `xTrack/Route/261015_FEAT_PLN_Route_price-meaning.md`, `FEAT_DOC_Route_decisions.md`, `FEAT_DSC_Route.md` — the plan's record, decision 5, and the two closed todo lines

## Next Step

The crown's card figures — the line's minutes in slow water on the card, where the panel does not already print them — is the one code item the feature still fronts; the two report facts wait on a surface and the words for one, which are the user's own to give.
