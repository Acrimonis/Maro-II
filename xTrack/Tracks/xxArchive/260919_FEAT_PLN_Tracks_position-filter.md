<!-- scope: feature -->
# Track position filter — All / On water / On land

Plan opened 2026-09-19, from a request to filter the track list by where a track was recorded, with a track
holding both sides categorised by which side owns more points. All four semantics were decided the same day,
the review that followed folded seven findings in, and a question about export and import folded two more —
that question also moved the sampling into the pass the repository already runs.

## 1. Requirement

A third filter axis on the tracks list — **All** (default), **On water**, **On land** — where a track that
holds both is classified by the larger number of points. No "mixed" bucket is asked for.

## 2. The mechanism it plugs into

- `ListFilter` (`data/model/ListFilter.kt`) holds axes as a `key=value` map, persisted in that form, and
  `trackFilterAxes()` declares the dropdown the filter sheet draws.
- `TrackSummary.matchesFilter()` evaluates each axis in a `when (key)`, its `else -> true` leaving an unknown
  axis inert, and the date axis already shows the pattern this one needs: `isLive || dateInRange`.
- The track filter is linked to the map's by default (`trackFilterLinked = true`), so the axis filters the
  map's stored tracks as well as the list — **choosing On land removes the water tracks from the map too**,
  the intended reading of a linked filter and reversible by the decoupling control.
- Both paths work from `TrackSummary`, and the list never loads points, so the classification has to be two
  integers on the summary rather than a pass over a track's points at render time.

## 3. Where the classification comes from

Nothing stores a point's water state today, and the healthiest reading is that nothing should: a per-point
field would add bytes to every point, couple the recorder to the coastline, and carry protobuf's
default-`false` trap, where "never classified" and "land" decode alike. A track is therefore classified
**once, by sampling**, and the result is cached on its summary.

- The sample walks the track's points on an even stride — a bounded number of them, GAP markers skipped — and
  asks a water test per sample.
- Two integers come back: the water and the land counts. `water >= land` (the tie rule) gives the track its
  side, and a track with no classified sample at all counts as water.
- Being cached, the answer is stable: a track cannot change side between two renders.
- The trade is exactness — the stored share is sampled, not counted, so a track within a few points of an even
  split may be filed either way. At that margin the answer is arbitrary whatever the method.
- **The trap to avoid:** `CoastlineRepository.isOnWater` answers false beyond 6 NM from the coast and false
  with no index loaded, so the predicate the boat sprite uses would file every offshore passage as land. The
  classification needs the containment test alone, which is not reachable through a public accessor today: a
  narrow one is added, documented as the classification's primitive and distinct from the navigable-zone
  predicate.
- **The region check:** the containment test answers water when no data is loaded, so "outside the baked
  region" needs its own answer — `CoastlineData.boundingBox` is the source, already serialized with the
  coastline, and a sample outside it answers unclassifiable rather than water.

## 4. The semantics — decided 2026-09-19

1. **Tie — water wins**, three options only, no "Mixed" value: the app's world is water.
2. **Unknown — counts as water.** Open sea beyond the baked region is water in fact, and every track stays
   visible under at least one value, where excluding it from both would drop it from the only list that can
   delete it. Accepted cost: a land-only import from outside the region never shows under On land.
3. **Live track — always displayed**, exempt from the axis in the same way the date axis exempts it.
4. **GAP points — never counted**, and never breaking a run.

## 5. The sampling pass — home, place, preservation, trigger

- **Its home** is `data/track`, beside the repository that owns the index, as a resolver taking the water test
  and the region bounds as parameters — the shape `parseHeatmapFamilies(lookup, parseColorHex)` already uses,
  so the rule is unit-testable against a fake with no Android in sight.
- **Its place is `rebuildIndex()`**, which already decodes every track file into a full `Track`: the points
  are in hand, so the sampling costs only the spatial queries, with no second traversal and no extra reads.
- **Counts must be preserved across rebuilds.** The index is re-derived from the track files on every
  mutation, so a summary's counts are carried over for tracks whose `updatedAtEpochMs` is unchanged —
  otherwise every save, delete, merge or import would re-sample the whole library.
- **One explicit run covers what the rebuild cannot.** An index that is read rather than rebuilt never enters
  that path, so the pass also runs once when the coastline becomes ready and the index holds tracks with no
  counts, then writes the index. Without it, an upgraded install would show every legacy track under On water
  until something mutated the library.
- **Its gate is the coastline:** the pass does not run until a water test exists, and an unclassifiable sample
  is never cached, so "no data yet" can never bake itself in as water.

## 6. Export and import

- **Export is untouched.** `GpxExporter` writes the track's points and carries the whole `Track` as a
  base64 protobuf extension blob; nothing in the export path reads a summary.
- **Import needs no hook of its own.** `TrackViewModel.importTracks` saves the imported track and the index is
  rebuilt from the track files — which is exactly where the sampling lives — so an import is classified by
  the same pass that gives it its summary, with the water test available mid-session.
- **Rejected alternative:** putting the counts on the `Track` would let them travel inside the export blob,
  at the price of rewriting user blobs for every legacy track. The index is a cache that may be rebuilt, so
  the counts belong there.

## 7. The edit set

- `data/model/ListFilter.kt` — the axis spec (`All` default, `On water`, `On land`) with its label, and the
  predicate branch over the two counts, carrying the live-track exemption beside the date axis's clause.
- `data/track/Track.kt` — the two counts on `TrackSummary` with their own field numbers. The two transient
  summaries built in `OverlayLayer` take the defaults, which read as unclassifiable, and neither reaches a
  filtered list today.
- `data/track/` — the resolver: the sample stride, the majority rule, the region check, and its tests over a
  fake water test (the tie, an all-GAP track, an unclassifiable one, a mixed one).
- `data/coastline/CoastlineRepository.kt` — the narrow containment accessor the resolver calls.
- `data/track/TrackRepository.kt` — the sampling inside the index build, the counts preserved for unchanged
  tracks, the one coastline-ready run, and the coastline gate.
- Two strings in both locales for the axis label and its options, reusing the app's existing water and land
  wording where it fits.

## 8. Verification

- `apk-build.bat` clean, the resolver's tests green, and the scoped `ui.map` + `config` run unchanged apart
  from the known four reds.
- Device pass: a mixed track under each of the three values, the tie case, an offshore track beyond the baked
  region, the live track while it records, and one imported track's classification.
- The pass is not repeated: a second launch, and a save that touches one track, leave the other tracks'
  counts untouched.

## 9. The findings, folded in

- **F1** → §5 — the sampler's home, gate and write-back, which the first draft left implicit.
- **F2** → §3 — the recorder coupling dropped along with the per-point field; sampling replaced it.
- **F3** → §7 — the two transient summary sites named rather than overlooked.
- **F4** → §2 — the linked filter's effect on the map stated as what will be seen.
- **F5** → §3 — the region check pointed at `CoastlineData.boundingBox`.
- **F6** → §7 — the axis label and option strings given a home in both locales.
- **F7** → nothing to change: the plumbing, the free field numbers and the live exemption all verified.
- **F8** → §5 — the index is re-derived on every mutation, so the counts must survive a rebuild.
- **F9** → §5 and §6 — the index can also be read rather than rebuilt, so the pass needs its own trigger; and
  export/import need no per-path hook, the rebuild being the shared one.

## Outcome

Shipped 2026-09-19 through the `#implement` pipeline, uncommitted at the time of writing.

The axis is in: `position` on the track filter with **All / On water / On land**, its predicate reading two
sampled counts on the summary, and the rule itself living on the summary — water wins the tie, and a track
nothing could be classified for counts as water. `TrackSummary` carries the counts at `@ProtoNumber(17/18)`
with `-1` as the never-classified sentinel, and `TrackPoint` is untouched: the per-point field the first draft
planned was dropped for a sampling pass instead.

The pass lives where the index is built. `rebuildIndex()` already decodes every track, so it samples there and
carries a summary's counts over when the track's `updatedAtEpochMs` is unchanged — which is what keeps one save
from re-sampling the library. An index that is *read* rather than rebuilt gets one pass of its own, guarded so
it runs once per attachment, and a sample the coastline cannot answer for is never cached. The water test
arrives by injection from MapScreen once the coastline is ready, through `NavigationViewModel`'s containment
accessor — never `isOnWater`, which calls open sea land beyond 6 NM.

`TrackPositionTest` covers the resolver across nine cases: water, land, GAP skipping, an all-GAP track, an
out-of-region track, an unanswerable test, the bounded sample, the tie and the unclassified reading, and the
live exemption. `apk-build.bat` SUCCESSFUL, and the widened scoped run came to 253 tests whose failures were
all pre-existing or another change's — the known four, the two marker-migration cases the Global Todos record,
and two heatmap-properties cases caused by an in-flight edit to the ramp's keys.

Deviations: the axis label and its options are hardcoded English, matching the three sibling axes in
`trackFilterAxes()` rather than adding strings for one axis of an English-only sheet. Open: the repository's
pass has no test of its own (F1 of the review) — the carry rule, the refuse-to-cache gate and the one-pass guard
are all reachable through the `File` constructor and all untested; and the device pass over the three values,
the tie, an out-of-region track and the live track.
