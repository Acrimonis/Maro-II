<!-- scope: feature -->
# Mergitur — Three-Branch Integration Plan

Captured 2026-09-11 20:08 UTC · v3 (post-review) 2026-09-11 20:22 UTC.
Reviewed by Code mode (technical feasibility) and Ask mode (design/coherence). Verdicts: Code = blocking corrections (folded in), Ask = sound-with-changes (no rethink).
Status: **approach + order locked — TR → TI → GL. Execution not started.**

## Goal
Consolidate three remote branches, all cut from the same `origin/develop` tip, into `feature/mergitur`, then land on `develop` via PR.

## Inventory

| Branch | Tip | Commits | Content | Size |
|---|---|---|---|---|
| `feature/tracks-recording` (TR) | `9ccf6ff` | 3 | Confirm-dialog normalization (ConfirmSheet → ConfirmDialog on the overlay ladder, dialog-owned scrim, P4 on/off scrims), resume-with-backup confirm, live-polyline paint-regression fix | 29 files, +1693/−536 |
| `feature/tracks-import` (TI) | `59720aa` | 2 | Derived map track visibility (drops persisted `visibleOnMap`), shared selection policy, `MapTrackSegments` extraction, GPX off-route cleanup harness + 4 test classes | 25 files, +980/−140 |
| `feature/global` (GL) | `5bc9b27` | 1 | `GLOBAL_CONTEXT.md` is state-only — rules consolidated into `AGENTS.md`; templates + `#help` docs updated | 9 files, +164/−58 |

Merge-base for all three = `025e1bc` = current `origin/develop` tip → no develop catch-up merge required.

## Conflict matrix
Two independent methods agree (three-tree `read-tree` scratch-index probe; legacy `git merge-tree <base> <b1> <b2>`).

| Pair | Verdict |
|---|---|
| TR × GL | 1 real conflict: `xTrack/GLOBAL_CONTEXT.md`. 1 trivial: TR deletes `ConfirmSheet.kt`, GL untouched (deletion kept automatically) |
| TR × TI | 4 real conflicts: `TrackViewModel.kt`, `MapScreen.kt`, `MapTrackOverlayEffects.kt`, `OverlayLayer.kt` |
| GL × TI | clean |

**Probe method (corrected).** Standardise on `git merge-tree 025e1bc origin/<b1> origin/<b2>`. The scratch-index `read-tree -m` route is only valid in its **three-tree** form; the two-tree form and `-i` variants silently report "clean", and an unquoted `set GIT_INDEX_FILE=%TEMP%\x` appends a trailing space. Use the `merge-tree` form for re-validation.

### Source-hotspot sizing (lines changed vs develop)
| File | TR | TI | Risk |
|---|---|---|---|
| `MapTrackOverlayEffects.kt` | +55/−30 | +30/−114 | **High** — TI evacuated 114 lines into `MapTrackSegments.kt` while TR rewrote the same file in place |
| `MapScreen.kt` | +121/−150 | +8/−4 | Medium |
| `MapTrackSegments.kt` | — | new file | — |
| `TrackViewModel.kt` | +31/−4 | +26/−2 | Low — both additive, different concerns |
| `OverlayLayer.kt` | +19/−15 | 0/−2 | Low |
| `OverlayLayerParams.kt` | +5/−0 | — | None — free assertion (TR-only) |
| `xTrack/GLOBAL_CONTEXT.md` | focus history + summaries | — | **Deferred to a single final pass** (see below) |

## Decision
**Hardened Approach A — sequential merges into `feature/mergitur`, order TR → TI → GL.** One merge commit per branch, `--no-commit` review on the risky hop, build + tests between hops.

Rejected: **B (squash)** loses per-commit provenance without shrinking the conflict surface; **C (rebase + force-push)** rewrites published branches and replays TR over TI up to 3×; **D (octopus)** aborts on the five known path conflicts.

## Why this order

The dominant risk is a **structural move colliding with a rewrite**: TI evacuated ~114 lines out of `MapTrackOverlayEffects.kt` into the new `MapTrackSegments.kt`, while TR rewrote that same file. Git cannot follow a partial move (both files exist, so it is not a rename), so a "clean" merge can silently drop TR's edits to moved code.

1. **TR first (the rewriter).** Lands conflict-free once the local `GLOBAL_CONTEXT.md` bookkeeping is set aside. The edits that must survive are then in-tree, presented to later merges as the `ours` side.
2. **TI second (the mover).** Its restructured shape arrives as the `theirs` side, giving a concrete destination to re-seat TR's displaced hunks into — a forward port with a known target, rather than hunting for a home in an already-restructured tree. This legibility argument decides the TR/TI order.
3. **GL last (governance/docs).** Zero source risk. Keeps `AGENTS.md` stable during all conflict work, so no mid-flight rule change can invalidate the plan; the rulebook reload becomes the final act. Every intermediate state also stays coherent: TR+TI is the functional deliverable, GL is an independent overlay. (Ask review confirmed: reordering GL first would trade a docs conflict for a mid-flight rulebook change.)

## GLOBAL_CONTEXT.md handling — one pass, at the end
Both TR and GL rewrite this file; the local Mergitur bookkeeping hunks sit on top and overlap TR's hunks (lines 4, 13, 42, 69-71 vs TR's 4-5, 13-14, 59, 64). Seeding them would make hop 1 conflict and force **two** manual passes on that file.

Instead: **defer all `GLOBAL_CONTEXT.md` reconciliation to a single post-GL pass (P4).** Focus History is an append-only stack and the routing/summary tables are derived indexes — they are regenerated, not textually merged. Concretely:
1. Capture the pending hunks: `git diff xTrack/GLOBAL_CONTEXT.md > %TEMP%\mergitur_bookkeeping.patch`
2. Restore a clean file: `git checkout -- xTrack/GLOBAL_CONTEXT.md` (hop 1 becomes conflict-free again)
3. At P4 re-apply, on the integrated file: the `mergitur, merge` routing row, the Mergitur summary row, the Focus History entry — reconciled against TR's content and GL's state-only trim (rules now live in `AGENTS.md`).

## Hardening tactics
1. **Safety net:** `git tag -a pre-mergitur 025e1bc -m "pre-Mergitur integration base"` before hop 1. This tag is the real pre-PR rollback.
2. **`--no-ff` on every hop.** Load-bearing on hop 1 (which would otherwise fast-forward, producing no merge commit and killing per-hop revertability); hops 2–3 cannot fast-forward anyway.
3. **`--no-edit` on every merge and commit** — `core.editor` is unset and `GIT_MERGE_AUTOEDIT` is not, so a bare merge stops at an editor (AGENTS.md §5 violation). `--no-commit` is inert without `--no-ff`.
4. **`-c merge.conflictStyle=diff3`** — the base section is what distinguishes "they moved it" from "they changed it". (`zdiff3` is not available on git 2.29.)
5. **`-X diff-algorithm=histogram`** to align moved code and suppress spurious hunks.
6. **Move map before the TI hop:** list which functions TI relocated to `MapTrackSegments.kt`, so re-seating TR's hunks is a checklist.
7. **Pre-capture TR's intent:** `git diff 025e1bc origin/feature/tracks-recording -- <5 paths> > %TEMP%\tr_intent.patch`.
8. **Residual-diff verification (corrected):**
   `git diff --stat origin/feature/tracks-import HEAD -- <TrackViewModel.kt> <MapScreen.kt> <MapTrackOverlayEffects.kt> <OverlayLayer.kt> <MapTrackSegments.kt> <OverlayLayerParams.kt>`
   - `MapTrackSegments.kt` **must diff empty** (exact assertion — TR never had it).
   - The other files need **hunk-level review** against TR's own magnitudes; textual equality is neither expected nor a valid gate (offsets shifted by the move).
   - **Bidirectional survival check:** every TR hunk must map to either a surviving location or a documented supersession. Presence is not survival.
9. **`--no-commit` on the TI hop**, review, then `git commit --no-edit`.
10. **Pre-flight re-validation** immediately before hop 1 (read-only): `git fetch origin`, then re-check the four tips and that `feature/mergitur` is still the common merge-base.

## Pre-conditions / housekeeping
- Seed commit (needs `#commit` / explicit go-ahead): commit **only the new `xTrack/Mergitur/` directory** (feature file + this plan). No branch tree contains those paths, so it conflicts with nothing. The `GLOBAL_CONTEXT.md` hunks are patch-backed-up and reverted per the section above.
- `feature/global` and `feature/tracks-import` exist **only on origin**; `feature/tracks-recording` exists locally and matches origin. Merge the `origin/...` refs directly.
- `feature/mergitur` sits exactly at the merge-base of all three branches — the ideal integration base.

## Risk register
- **Green-but-regressed map (highest residual risk).** Off-matrix coupling: TR's resume path intersects TI's `visibleOnMap` removal in `TrackRecordingService.kt`, `TrackMerger` and `TrackRecorder`, and `MapScreen.kt`/`MapTrackOverlayEffects.kt` carry both the visibility+selection policy TI redefines and TR's rewrite. Neither the build nor the residual diff detects this → device smoke test is mandatory.
- `MapTrackOverlayEffects.kt` / `MapTrackSegments.kt` — silent loss of TR's edits to moved code. Mitigated by tactics 6–8.
- TI drops persisted `visibleOnMap` (schema/back-compat) — TI added `TrackLegacyBlobDecodeTest.kt`; run the scoped tests.
- TI ships `xTrack/TracksImport/` but never registered it in `GLOBAL_CONTEXT.md` → register at P4 + `#doctor` sweep.
- No `FEAT_HYD_Mergitur.md` exists yet, so the Focus History pointer is dangling until the first `#bake` → create it at P4.
- TI adds two root-level GPX artifacts (`2026_09_06_15_52-Iles_de_Lerins_+_Bouë_à_4.gpx`, `…_no-resume.gpx`) — merges handle the bytes correctly; exposure is console-level only (cmd codepage, `findstr` aborts on the long line). **Decide keep vs relocate.**
- GL rewrites `AGENTS.md` → reload the rulebook immediately after that hop (source-of-truth rule).
- Global Rule: merges to `develop` happen **only via PR**; `feature/mergitur` integrates locally, then opens the PR.
- Line endings: blobs are LF-only while `core.autocrlf=true` is machine-wide; `.gitattributes` covers only `*.bat/*.cmd/*.ps1`. No spurious-conflict surface. No active git hooks.

## Verification per step
1. `apk-build.bat` (runs `gradlew assembleDebug`) after every hop. Note: `data/` is gitignored yet mounted as an asset srcDir, so a green build does **not** prove the depth/coastline assets are present.
2. **Test baseline first:** record the three pre-existing failures (`MarkerFilterMigrationTest` ×2, `RegulationAggregatorTest`) so they are not mistaken for regressions. Never gate on an unscoped `:app:testDebugUnitTest`.
3. Scoped unit tests after the TI hop:
   `gradlew :app:testDebugUnitTest --tests "ykws.android.maro.data.model.MapSelectionPolicyTest" --tests "ykws.android.maro.ui.map.MapTrackSegmentsTest" --tests "ykws.android.maro.data.track.TrackLegacyBlobDecodeTest" --tests "ykws.android.maro.data.track.GpxBBoxCleanToolTest"`
   `GpxBBoxCleanToolTest` is inert unless `-Dmaro.cleanGpx=true` (TI's 2-line `app/build.gradle.kts` change wires that system property; no build/variant impact) — verify the wiring rather than trusting a green pass.
4. Residual-diff + bidirectional survival check after the TI hop (tactic 8).
5. Device smoke test after the GL hop: confirm dialog, resume-with-backup, live polyline, and map track visibility/selection (the green-but-regressed risk). Verify depth/coastline layers render.

## Rollback
- `git revert -m 1 <merge>` per hop works **LIFO only** (GL → TI → TR); reverting hop 1 beneath hops 2–3 re-introduces `ConfirmSheet.kt` and collides with TI's restructure.
- `pre-mergitur` tag = full reset to the pre-integration base.
- If the PR is later squash-merged, per-hop revertability is lost — record the per-hop SHAs in the PR body.

## Open questions
- Keep or relocate TI's two root-level GPX files? — **resolved: ignore, merged as-is** (user decision).

## Outcome
Landed 2026-09-11 20:29 UTC. Hops: seed `32352f9`, TR `735cd29`, TI `e4d56f6`, GL `431d165` on base `025e1bc`; safety tag `pre-mergitur`; nothing pushed. Every hop used `--no-ff --no-edit -c merge.conflictStyle=diff3 -X diff-algorithm=histogram`. `apk-build.bat` SUCCESS after each hop; scoped TI tests green (`MapSelectionPolicyTest` 10/0, `MapTrackSegmentsTest` 4/0, `TrackLegacyBlobDecodeTest` 3/0, `GpxBBoxCleanToolTest` skipped by design); the three pre-existing failures unchanged.

Deviations:
- TR×TI produced **2** conflicts, not 4 — `MapScreen.kt` and `OverlayLayer.kt` auto-merged (covered by the survival token audit).
- TR×GL `GLOBAL_CONTEXT.md` auto-merged cleanly; reconciled in the single P4 pass as designed.
- **Silent collision found by the compiler, not by git:** TR's `duplicateTrack` set `visibleOnMap = false`, a field TI deleted (ProtoNumber reserved). The dead argument was dropped → open finding B1.
- P4 doc edits remain uncommitted; Focus History holds 11 entries pending the bake-side prune.
- TI's root-level GPX files merged as-is per the user's decision.

Review findings (Ask leg) — both closed 2026-09-11:
- **B1 — RESOLVED by decision: accept the behaviour.** The backup copy stays unhidden and renders like an ordinary stored track (unpinned, un-boosted). The `resumeTrack` KDoc was corrected and D4 of `xTrack/BoatTrace/260911_FEAT_PLN_BoatTrace_resume-confirm-backup.md` now carries an Amendment recording the change.
- **B1 follow-up (20:57) — the twin tie is closed.** `duplicateTrack` nudges the copy's `startTimeMs` 1 ms older, so at an exact render cap the copy is deterministically the one dropped, independent of summary list order — locked by `MapSelectionPolicyTest.resumeBackupTwin_copyDroppedFirst_regardlessOfInputOrder`. The residual slot cost and the coincident overlap were consciously accepted: the copy is an operational, short-lived artifact.
- **B2 — EVIDENCE PRODUCED, gate PASSES.** At `HEAD` `431d165` vs TI `59720aa`: `MapTrackSegments.kt` diffs **empty** (untouched by TR — exactly TI's file); `OverlayLayerParams.kt` +5 (TR's addition survives); `TrackViewModel.kt` 35, `OverlayLayer.kt` 34, `MapTrackOverlayEffects.kt` 85, `MapScreen.kt` 271 changed lines — consistent with TR's own magnitudes, so no hunk was dropped in the move.

Verified clean by the review: TR's superseded `mapFiltered`/`filteredSummaries` paths are genuinely redundant with no dangling references; TR's live-paint and direction-arrow fixes survive; GL's consolidation deliberately dropped the old auto-switch rule as a MODE LOCK conflict (recorded in its migration plan) and lost nothing else; `GLOBAL_CONTEXT.md` is truly state-only with consistent Mergitur/TracksImport registrations. The `visibleOnMap` removal is back-compat-guarded by reserved ProtoNumbers.

Post-task suggestions (logged, not fixed — scope lock): three eligibility implementations with a highlight-only vs highlight+boost divergence; GAP-split logic duplicated three times; write-only `renderedTrackIds` plus stale incremental-diff comments; near-identical history/pinned render blocks; non-observable `MapRenderFocus` mutated inside a `LaunchedEffect`; the generic `merge` routing keyword added at `#track` time.
