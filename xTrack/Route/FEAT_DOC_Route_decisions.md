<!-- scope: feature -->
# Route — Functional Decisions Record

> **Purpose:** the Route feature's decisions of record — choices still in force that no rule in
> [`FEAT_DSC_Route.md`](FEAT_DSC_Route.md) states and no state doc already homes.
> **Scope:** whether and how the app draws a route from the boat to a destination.
> **Last updated:** 2026-10-15
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
