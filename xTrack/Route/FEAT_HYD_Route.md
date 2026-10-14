# Context Hydration — Route — 2026-10-14

**Last Bake:** 2026-10-14 12:31 UTC — written by `#bake`; absence means never baked

**Directive trace:** Since the last bake (2026-10-13 20:13 UTC) the session opened as an independent review the user ordered — read the sources yourself, my summary is a claim — and the findings were then implemented on the user's own `#impl`, which ran the pipeline Code (implement + build) → Ask (the coverage pass that found the unguarded-override clock) → Architect (the report). No dependency was added, no machine-shaped data file was opened, no work was started without an order, the device was never touched by the agent, and every claim about the code follows a file read.

## State

**A zone's price and its clock are now separate laws.** `regulatedZone.speedRoute.<key>` is a **price only**: the search prices a zone at `routeLimitKn` — the grid's stored `ZoneRing` limits, the guard's point read and the fine crossing price, all untouched — while every **reported** figure times the **displayed** limit. The new [`strictestDisplayLimitKnAt`](../../app/src/main/java/ykws/android/maro/spatial/multipass/ZoneGeometry.kt:92) stands beside [`strictestLimitKnAt`](../../app/src/main/java/ykws/android/maro/spatial/multipass/ZoneGeometry.kt:71), reached through [`MultipassWorld.zoneDisplayLimitKnAt`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassWorld.kt:144), whose `null` default makes an unwired world fail loudly rather than time the price law. Both laws filter and test containment identically, so they can never disagree about **whether** a limit is in force — only about its value.

**One lambda moved.** [`limitAtFor`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RoutePassPrimitives.kt:234) is the only re-pointed read, so the provisional pair, the settled profile clock, the slow-limit attribution and the corner tally follow together while the search does not; the line colour derives from `legTimesSec` and the saved track's speeds from its own durations, so both follow with no change of their own. [`ZoneClockLawTest`](../../app/src/test/java/ykws/android/maro/spatial/multipass/ZoneClockLawTest.kt:1) pins the split in six cases, the six existing world fakes were wired to the new accessor, and the six scoped suites plus `assembleDebug` are green. The four statements were rewritten at their homes — `RegulatedZone.speedRouteKn`, `SpeedZone.routeLimitKn`, `SpeedZoneBuilder` and the `RegulationSpeedOverrides` KDoc plus its generated header, which the `zones.properties` asset is produced from.

**Two readers stay deliberately apart, and open.** [`forcedCrossingNames`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt:792) still filters on the price, so a zone its `speedRoute` prices to nothing is never named as a forced crossing and the doubled-corridor retry keyed on that list never fires for it; and [`RoutePassRanking`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RoutePassRanking.kt:1)'s Best gate and Fun objective move with the clock, so they judge such a zone on the limit the routing never obeyed. The feature file carries this as one cross-listed todo.

**One defect is found and not fixed.** A hand-edited `regulatedZone.speedOverride.<key>=0` or a negative value passes `toDoubleOrNull` with no positivity guard, and the clock — unlike the price path, which guards `interiorLimitKn > 0.0` — divides by it, so [`timeLineWithLimits`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteEta.kt:87) would yield an infinite leg time. The split made that value reachable to the clock, which a set `speedRoute` used to shield. Carried forward from the last bake: the tile key carries the corridor's priced-zone set, so a pre-warm box whose zones differ from an arm's corridor keys a different tile — a rebuild, never a wrong tile.

## Target Files

- `spatial/multipass/ZoneGeometry.kt`, `MultipassWorld.kt`, `RoutePassPrimitives.kt` — the two laws and the one lambda
- `data/regulation/RegulatedZone.kt`, `SpeedZone.kt`, `SpeedZoneBuilder.kt`, `RegulationSpeedOverrides.kt`, `assets/zones.properties` — the statements, rewritten at their homes
- `app/src/test/java/ykws/android/maro/spatial/multipass/ZoneClockLawTest.kt` — the pin, beside the six world fakes wired
- `xTrack/RegulatedZones/261014_FEAT_PLN_RegulatedZones_speedroute-clock-split.md` — the plan of record
- `xTrack/RegulatedZones/FEAT_DSC_RegulatedZones.md`, `xTrack/Route/FEAT_DSC_Route.md` — the landed records

## Next Step

No code task is open on the feature. The one decision the cross-listed todo names — whether the forced-crossing list and the rung ranking should follow the clock as the rest of the reporting now does — is the user's own.
