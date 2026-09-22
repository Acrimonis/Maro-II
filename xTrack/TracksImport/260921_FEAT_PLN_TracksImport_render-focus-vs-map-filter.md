<!-- scope: feature -->
# The map filter and the render focus — the badge and the map disagree

- **Feature:** TracksImport
- **Branch:** `feature/on-water-lannd-filter`
- **Date:** 2026-09-21
- **Status:** SHIPPED 2026-09-21 — see §12 for the outcome and the deviations
- **Type:** device report against a decided rule (D1a of the 2026-09-11 plan), not against a broken predicate

Filed under TracksImport because the two files the behaviour lives in are this feature's —
`MapSelectionPolicy.kt` and `MapRenderFocus.kt` — and because D1a is this feature's decision. The axis
that exposed it is Tracks'.

## 1. The report

From the menu, the track filter's axis changes the badge, but the map keeps drawing tracks the filter
excludes. The list behaves correctly under the same filter value, so the axis itself is sound: the
fault is in what the map draws, not in what the filter matches.

It was the **position** axis (All / On water / On land, shipped 2026-09-19) that made it visible. On a
categorical axis an extra line is a contradiction — a water track drawn under On land is a wrong
answer — where on the date axis extra lines only read as more tracks.

## 2. Verified mechanism

Two derivations answer the same question, and only one of them honours the override:

- **The badge counts the filter alone** — `MapScreen.kt:2356-2359`:
  `allTrackSummaries.count { !it.isLive && it.matchesFilter(appSettings.trackMapFilter, …) }`, whose own
  comment already concedes divergence ("Render-cap divergence is acceptable"). It reaches the menu's
  filter button as `trackMapCount` (`MapScreen.kt:2418` → `OverlayLayerParams.kt:58` →
  `OverlayLayer.kt:197`).
- **The map draws the filter plus the focus** — `MapTrackOverlayEffects.kt:144` takes the unfiltered
  `storedSummaries` and calls `TrackSelectionPolicy().select(items = storedSummaries, filter =
  appSettings.trackMapFilter, cap = nbToRender, focus = focus, …)` (`:147-155`).
- **Eligibility carries the override** — `MapSelectionPolicy.kt:41-44`:
  `!candidate.pinned && (focus.includes(candidate.id) || candidate.matchesFilter(filter, todayMidnightMs))`.
  The highlighted track is additionally kept past the cap (`:52-53`).
- **The pinned loop repeats it** — `MapTrackOverlayEffects.kt:236-240`:
  `it.pinned && (it.id == highlightedTrackId || it.matchesFilter(appSettings.trackMapFilter, midnightMs))`.

So the filter is not leaking and nothing is stale: the effect's rebuild keys include
`appSettings.trackMapFilter` (`MapTrackOverlayEffects.kt:77`), the pass does re-run, and the policy
re-admits the focused tracks on purpose.

The focused ids are exactly two sets (`MapRenderFocus.kt:53-54`, `includes(id) = isHighlighted || isBoosted`):

- **the highlighted track** — whichever track the drawer has open (and the id that also drives the
  gold/halo selected appearance);
- **the session boost** — up to eight ids (`MapRenderFocus.kt:57-58`) touched this session, marked at
  `TrackViewModel.kt:105` (a finalized recording), `:355` (an edited track, verified inside `updateTrack`
  at `:352-360`), `:388` (a merge) and `:477-478` (an import), and cleared only by `clearRenderBoost`
  (`TrackViewModel.kt:288-290`), which runs on the filter's **RESET** alone — `MapScreen.kt:2484`, the
  list reset and only while `trackFilterLinked`, and `:2511`, the map reset, always — while the axis
  handler at `:2488-2499` clears nothing. That is D1a as shipped.

**Which override the report actually caught.** A map-filter change under the linked default runs
`closeDashboardsForScopeChange(trackListWorld = linked)` (`MapScreen.kt:2492`), and that calls
`closeTrackDrawer()` (`:649`), which sets `highlightedTrackId = null` (`:623`). The highlighted exception
is therefore already gone when the filter lands, so the lines still drawn could only have come from the
**session boost** — the very term reading 2 narrows, which is why the report and the fix agree.

## 3. What the override is for — the promise this plan must not silently break

- The **boost** exists because of the defect the 2026-09-11 plan opened on: an imported track appeared
  in the list but was not drawn on the map. Its ranking sits it above the render cap, so a track with an
  old `startTimeMs` is drawn the moment it arrives. Its *filter* bypass was folded into the same
  predicate rather than argued for on its own — §7 of that plan asserts "focus id overrides cap **and**
  filter" and "session-boosted ids included even when the filter excludes them", which is the behaviour
  being reported today.
- The **highlighted track** rides along because the user is looking at it; it is the one id whose
  presence is self-explanatory on screen, and §11 records that Stage 0's import-focus was removed for
  exactly this reason — `highlightedTrackId` also paints the track as *selected*, so borrowing it as a
  visibility device makes a track look chosen when it was not.
- **D1a** decided the clearing rule: the boost is dropped on the track-filter **reset**, and
  "individual axis changes keep the boost". That last clause is the sentence in conflict with the report.
- **The pinned path already treats the filter as authoritative** — `MapTrackOverlayEffects.kt:236-240`
  draws a pinned track only when it matches the map filter, with the highlighted id its one exception,
  and the boost is not consulted there at all. The boost's *filter* bypass therefore lives in the history
  term alone, and reading 2 makes that term agree with the sibling loop it sits beside.

## 4. The decision this forces

The map filter has to mean one of two things, and they cannot both hold:

1. **Soft — the filter prioritises.** It selects what leads the render, and the session's own tracks
   ride along. Today's behaviour; the badge must then count the drawn set to stop disagreeing.
2. **Hard — the filter is authoritative.** What it excludes is not drawn, with the open track the one
   visible exception. The badge can stay as it is, off by one at most while a track is open, and that
   one is explainable on screen.

**Recommendation — reading 2, with the badge moved onto the painted set regardless.** The predicate
already carries both jobs in one term, and separating them is the smaller change: the highlighted track
keeps the exception, the boost keeps the cap override it was built for. The badge then reads the same
derivation the map paints — one home for "what is drawn" — which is the property the 2026-09-11
refactor was written to establish.

**The strongest objection to it:** a track recorded or imported while a non-empty filter is active
would no longer appear, which revives the class of defect §1 of the 2026-09-11 plan was closed on; and a
badge counting the painted set stops answering "how many of my tracks match this filter". Both costs are
real, and the mitigation is narrow — the default filter is empty, so the loss only appears under an
active filter, which is when the user has just said what they want to see.

## 5. Edit set — reading 2 (recommended)

- `data/model/MapSelectionPolicy.kt` — split the override in `TrackSelectionPolicy.select` (`:41-44`)
  into its two terms: `isHighlighted(id)` bypasses the filter and the cap; `isBoosted(id)` bypasses the
  cap only, and its ids must satisfy `matchesFilter`. `MarkerSelectionPolicy` is untouched.
- `data/model/MapSelectionPolicyTest.kt` — the two boost cases flip: a boosted id that fails the filter
  is now excluded, and one that passes it still survives the cap.
- `ui/map/MapScreen.kt` — the badge stops re-deriving the filter count and reads the drawn set instead.
  The pass publishes it at `MapTrackOverlayEffects.kt:320` (`paintedTrackIds.value = painted.toSet()`),
  the state is created at `MapScreen.kt:1119` inside the same composable, and it already has a consumer
  at `:2074` (`legendVisibleForState`) whose comment at `:1118` states the rule this edit follows — the
  effect alone knows which summaries settled into an overlay. The number it feeds (`trackCount`, drawn
  at `MenuDrawerOverlay.kt:238`) is display-only, so nothing downstream changes meaning.
- Consequences to accept and record: the number becomes the drawn set's, so it now reflects the render
  cap (≤ 20) and drops any track whose detail failed to load, and it reads 0 until the first pass lands —
  which is also exactly what the map shows at that instant.
- **The layer toggle now reads through the badge.** With the tracks layer off the pass paints nothing, so
  the menu shows 0 tracks — where the marker count beside it still ignores its own layer, `mapMarkers`
  being the map filter's output alone (`MarkersViewModel.kt:186-188`) with the layer gate applied by its
  consumers (`MapScreen.kt:969`). Truthful under this reading and accepted here; making the marker side
  follow suit is its own decision, not this plan's.
- The pass early-returns on a null `mapView` (`MapTrackOverlayEffects.kt:118`) without republishing, so a
  disposed map leaves the last count standing instead of reading 0. Low: nothing draws with no map, and
  the badge that replaced this one was independent of the pass entirely.
- `data/model/MapRenderFocus.kt` — `includes(id)` has one caller (`MapSelectionPolicy.kt:43`) and this
  change makes it dead, so it goes with its KDoc; `isHighlighted` and `isBoosted` stay, and the boost
  keeps its rank term (`MapSelectionPolicy.kt:47`) and with it the cap override it was built for.
- `ui/map/MapTrackOverlayEffects.kt` — **unchanged**. The pinned carve-out at `:238` keeps
  `id == highlightedTrackId` exactly as it is; only the history term's `focus.includes` leaves.

## 6. Edit set — reading 1 (soft, if chosen)

- `ui/map/MapScreen.kt` — the badge counts what the map draws, the override left in place. One
  derivation, no predicate change, no test change: the disagreement disappears without deciding anything
  about the boost.
- Consequence to record explicitly: the badge then reports "drawn, capped, plus focus" — it no longer
  answers "how many match this filter", and the cap (≤ 20) becomes visible in it.

## 7. Tests

- `MapSelectionPolicyTest` — the pair above, plus the existing "focus id overrides cap and filter" kept
  for the highlighted id alone, and a case pinning that a boosted id failing the filter is dropped.
- The badge's agreement with the map is **not** unit-testable — `paintedTrackIds` is Compose state
  written inside the effect over real osmdroid overlays — so that check moved to the device pass below.
- Unchanged and still asserted: pinned excluded from the capped history set, `isLive` exempt from the
  date axis, markers filter-only.

## 8. Verification

- `apk-build.bat` clean and the scoped `ui.map` + `config` run green — **the "known four reds" this plan
  first expected did not appear**: the run came back BUILD SUCCESSFUL with no failures, so the
  properties-drift expectation is dropped rather than left standing.
- Device pass, owed by the user: with a track open, and again after recording one, switch the position
  axis through all three values and check the badge against the lines on the map — the reported case
  being a track that no longer matches the filter. The badge must also read 0 on a cold start until the
  first overlay pass lands, and match the drawn set exactly on a library larger than the render cap.
- The RESET path still clears the boost, and the layer toggle still wins over everything.

## 9. Non-goals

- The position axis's classification, the cap value, and the pinned path's own highlight exception.
- `MarkerSelectionPolicy`, the marker badge, and the live recording line's non-filterability.
- The 2026-09-11 no-touch list stands: `pinned`, the master layer toggles, filter state and both link
  toggles, and the GAP/dash split rendering.

## 10. Beside this, not in it

- The On land option borrows the dashboard's string at `ListFilter.kt:161` (`R.string.dash_not_at_sea`)
  where a filter string of its own belongs. One line, in the Tracks feature's file, and it does not
  change this plan.

## 11. Decision — reading 2, taken 2026-09-21

The user chose **reading 2 with the badge moved onto the drawn set** and ordered it through `#impl`. §5
is therefore the edit set and §6 stands as the rejected reading: D1a's "individual axis changes keep the
boost" is amended so the boost keeps the cap override and gives up the filter bypass, while the
highlighted track keeps both.

## 12. Outcome — shipped 2026-09-21

One `#implement` pass on `feature/on-water-lannd-filter`, Code → Ask → Architect, and the change is two
terms and one source: `TrackSelectionPolicy.select` gates on `focus.isHighlighted(candidate.id)` where it
ORed in `focus.includes(candidate.id)` before, the boost keeping its rank term and with it the cap
override; `MapRenderFocus.includes` had exactly one caller, went dead and was deleted with its KDoc; and
the menu's track count reads `paintedTrackIds.value.size` (`MapScreen.kt:2355-2359`) instead of counting
the filter's matches, so the number and the map are one derivation — the property the 2026-09-11 refactor
existed to establish. `MapSelectionPolicyTest`'s two boost cases were rewritten around the new pair: a
boosted id failing the filter is dropped, one passing it still survives the cap, and the reset case moved
its ids inside the filter so that what it observes is the rank the reset takes away.

`apk-build.bat` SUCCESSFUL in 22 s, and the scoped `MapSelectionPolicyTest` + `ui.map.*` + `config.*` run
BUILD SUCCESSFUL with no failures. The Ask hop returned no blocking finding, and three of its findings
are folded above: the layer toggle now reading through the badge, the null-`mapView` stale set, and the
dropped reds expectation. Two are recorded and not actioned — the shell-side pinned carve-out has no test
of its own (pre-existing), and the marker count is now the asymmetric one, which is its own decision.

Deviations from the drafted plan: the badge's source is verified as the painted set the pass publishes at
`MapTrackOverlayEffects.kt:320`, which §5 anticipated but had not read; and the 2026-09-11 plan's §7 and
D1a now carry the forward pointer this plan owed them, both stating the rule the code no longer follows.
The coverage side gained one confirmation worth keeping: the inspect candidate set already read the map
filter without the boost (`MapScreen.kt:972`), so a boosted track could previously be drawn but not
pickable, and the two now agree.
