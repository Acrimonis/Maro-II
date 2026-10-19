<!-- scope: feature -->
# Route — Functional Decisions Record

> **Purpose:** the Route feature's decisions of record — choices still in force that no rule in
> [`FEAT_DSC_Route.md`](FEAT_DSC_Route.md) states and no state doc already homes.
> **Scope:** whether and how the app draws a route from the boat to a destination.
> **Last updated:** 2026-10-19
> **Promoted by:** the `#archive` sweep of 2026-10-09 that retired the twenty-one 2026-10-03 → 10-06 plans, and the sweep of 2026-10-14 that retired the five landed plans of 2026-10-07 → 10-13.

---

## 1. The stored-route match is removed — every arming acquires afresh

- **Decision:** no arming consults a saved line. Every arming runs a fresh acquisition, the per-door force-fresh flag being what makes it so; the requirements that made an arming read a stored route — **R83 to R87** — were struck in place with the numbering kept, so the gap is the record.
- **Rationale:** the user's word of 2026-10-06 — *do not check the stored routes, always rearm an acquisition*. A return trip, or a re-arming of the same pair, now always searches rather than sometimes answering instantly from a stored line; that visible change is the plain rule's own price and the user's to accept.
- **Left standing:** `routePlanOf` and the track card's follow door — loading a saved route the user **picked** is a different door from matching one behind an arming, and it is untouched; and **R82**'s persisted end ids, whose stated purpose was the match, stay as a record nothing reads.
- **Filed:** retired from `xxArchive/261006_FEAT_PLN_Route_stored-pullback-removal.md`

## 2. Open-water bends — the accepted residue

- **Decision:** two findings of the 2026-10-06 diagnosis are **accepted as known defects** and are not to be re-opened without a new word. The long **off-axis first leg** is the search's own tie-break over genuinely uniform water — with eight neighbours every monotone path is optimal there, so breaking ties toward the aim would move the line on every rung and every pair rather than repair an error. The **41–49 m shore refusals** are a 50 m margin against one real mainland shore (OSM `4212554`), strict but true; loosening it is a policy decision, not a defect repair.
- **Rationale:** the user's word of 2026-10-06 — *we will stick with the first fix and accept this current bug* — after the one fix kept (the EMODnet shallow gate on the routing's depth read, a `## Rules` entry) had landed.
- **What would re-open it:** a new device reading on a pair that shows the leg, printing the search's own cell costs — the one figure that would tell a tie-break from a cost — or the user's word on a value, since every remaining lever is a setting.
- **Filed:** retired from `xxArchive/261006_FEAT_PLN_Route_open-water-bends.md`

## 3. The fine layer's marker price rides the fine cell's base — the accepted line move

- **Decision:** a fine tile's marker price is charged at the **target grid's own base** (the fine lattice's), not the coarse cell's it used to ride. On a world with **priced markers** the drawn line can therefore move; on marker-free water the raster is byte-identical. The user accepted the move on 2026-10-13.
- **Rationale:** the pace-free tile stores a multiplier, so the base has to be applied once and from one grid — pricing the fine layer at the coarse base was a latent inconsistency the factoring could not keep. The alternative, carrying the base's cell in the tile, contradicts the brief's own wording.
- **Left standing:** the coarse interior, the pull and their arithmetic are untouched, so the move is confined to the fine layer's own marker-priced water.
- **Filed:** retired from `xxArchive/261013_FEAT_PLN_Route_pace-free-tiles.md`

## 4. The crown counts the band and the serving ramps — the 2026-09-30 exclusion reversed

- **Decision:** the 300 m band's slow seconds and the ramps that serve a limit now **count in the crown's figure**, weighed with the user's own dial weight (`route.avoid.slowWater.weight`) exactly like a ring's interior. This reverses the 2026-09-30 decision that excluded the band from the ranking gate, and extends the 2026-09-26 ruling that the approach ramps count.
- **Rationale:** the crown is now one arithmetic sum, `trip time + weight × slow seconds`, so the exclusion had no home left. A share-gate could hide a band's cost inside its denominator, but an absolute-seconds sum cannot: every second under a limit is the same second, and a band's seconds are as real as a ring's.
- **Left standing:** the boat's own cornering floor stays **out** — it is the boat's water, not a limit's — so only the band, the rings and the ramps that serve a limit are counted.
- **Supersedes:** the 2026-09-30 exclusion of the band from the ranking gate, itself set over the 2026-09-26 ruling on the ramps.

## 5. The report speaks presence beside the price — the forced-crossing reader kept as it is

- **Decision:** `forcedCrossingNames` keeps filtering on the **price** — a zone its `speedRoute` prices to nothing is still never named as a forced crossing, and the probe, the doubled-reach retry and every drawn line are untouched. The report gains a **second, independent fact** instead: `crossedSlowZoneNames` names the zones the drawn line **enters** whose own limit in force is below the cruise pace, read off the **displayed** limit (`SpeedZone.speedLimitKn`, the field `strictestDisplayLimitKnAt` reads and through it `MultipassWorld.zoneDisplayLimitKnAt`), never off `routeLimitKn` and never off the price. The two facts are **records**, not drawn facts: they reach no proto and no surface reads them.
- **Rationale:** the user's word of 2026-10-16 — the report speaks presence, the search keeps speaking price. A zone the price law calls free (its `speedRoute` override at or above the pace, or `route.avoid.speedZone.enabled` off) still slows the boat and still shows in the line's colour, so a report that named only the priced crossings hid water the clock had to pay. Reading the presence fact off the displayed limit is the same read the clock, the colour and the slow-limit attribution obey, so the two facts cannot disagree about what the water was signed at. A zone at or above the pace costs no second and shows in no colour, so it is named by neither.
- **Left standing:** the price law, the probe, the retry, the ladder, the ranking and the panel's table; the two facts are carried on the answer and on the plan and are left empty at the two saved-route constructions. **Still open:** no composable prints either fact, and the two words for a surface are the user's own to give — deliberately outside the hop that landed this.
- **Recorded in:** [`261015_FEAT_PLN_Route_price-meaning.md`](261015_FEAT_PLN_Route_price-meaning.md), and the feature's own todo, "the zone clock's law and the ranking's own read", is closed by it.

## 6. The `selective` engine is retired — and the depth preference goes with it

- **Decision:** the third engine leaves the tree whole — its row and class, its `SelectiveGridPlan` with the four collars and the `pricesDepthBand` seam member, the depth price (`DepthBandLaw`, its `withDepthBand` door and its `PriceArm.DEPTH` arm), the depth-dilation collar with its radial scan, the local number cache it kept, the seven `route.selective.*` keys with their accessors, and both of its suites. The **depth gate's wall and its margin stay** at the shared level — `costField`/`tileCostField` and the corner pass — so `avoid` and `evolutive` keep every depth behaviour they had.
- **Rationale:** the user's word of 2026-10-16 — *as the selective algo is showing poor performance, plan to remove it completely from the app/code/ui* — on measurements the feature already held: the fine build ≈8× dearer per build on **fewer** fine cells before the single-flight fix, 1.39×–2.12× the adaptive arm's warm cost after it, and the depth source's declared **zero clearance** degrading every priced walk it touched to per-interval reads.
- **Supersedes:** the 2026-10-07 decision that created the engine, whose own words narrowed point 1's scope onto it while keeping `avoid` as the shipped default and `evolutive` as the reference. The engine's going reverses that scoping rather than extending it, and nothing in the code ever forbade the flag on the other engines: `withDepthBand` was a door every `costField` took.
- **Left standing:** the shared wall and margin, the marker price with its `MARKER` arm, the fine-cell-over-coarse-copy rule, and the tile store's versioned codec — a tile written by the retired engine reads as a **miss**, never a wrong tile, and its halo was already zero for the adaptive plan.
- **Still open:** whether a soft depth preference returns on `evolutive` ([`261007_FEAT_PLN_Route_depth-gate-soft-band.md`](261007_FEAT_PLN_Route_depth-gate-soft-band.md) now holds that question), and the health notes the retirement left behind — two code prose lines still describing the collar, and `FineTile.extract`'s production-dead `haloCells`.
- **Recorded in:** [`261016_FEAT_PLN_Route_selective-retirement.md`](xxArchive/261016_FEAT_PLN_Route_selective-retirement.md)

## 7. The pace-free design is recorded, not ordered

- **Decision:** the `Any` stop on the cruising-speed scale ships as the absence of a preference, the search then planning at the boat's own pace; the broader pace-free design — a search that needs no speed at all — is **recorded and not ordered**. Four pieces stand open: the **proposals unranked** (three rows stating distance, added distance and priced water crossed, no rank and no crown), the **cost unit** (effective metres with a per-limit penalty table, or seconds at the limit in force — a re-unit of every price law), the **calibration table** (how dear a 5 kn ring is against a marker, a shipped table whose numbers are the user's) and the **corner radius's own replacement** (drawn from the limit in force at the bend, with one fixed maximum in open water).
- **Rationale:** the user's word of 2026-10-17 — *there is no need to rank it anymore, I would pick it myself* — which the `Any` stop answers only in part, since with a resolved pace the crown still compares rungs by trip time.
- **What would re-open it:** the user's word to order the pace-free design, or a device reading showing the pace setting steering a line the user did not want.
- **Recorded in:** [`xxArchive/261017_FEAT_PLN_Route_no-preferred-speed.md`](xxArchive/261017_FEAT_PLN_Route_no-preferred-speed.md)
