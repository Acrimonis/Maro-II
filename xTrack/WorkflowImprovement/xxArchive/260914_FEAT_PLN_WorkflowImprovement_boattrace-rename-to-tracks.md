<!-- scope: feature -->
# Rename the BoatTrace feature to Tracks

**Date:** 2026-09-14 · **Status:** shipped — committed as `322f4cc` on `feature/track-speed`; see `## Outcome`
**Scope:** xTrack state migration — directory, file names, in-file references, routing/summary rows, cross-feature live pointers, two code comments.

## 1. Why

BoatTrace names a recording subsystem after a nautical pun while the feature owns the app's `Track` domain — persist, render, export, list, history. `Tracks` is how the user already addresses it (`#focus track`) and how the code already speaks (`data/track/`, `TrackRepository`, `TrackViewModel`, `TrackHistoryOverlay`).

## 2. Measured footprint

| Area | Extent |
|---|---|
| `xTrack/BoatTrace/` | 56 files — 1 `FEAT_DSC`, 1 `FEAT_HYD`, 1 `FEAT_DOC`, 53 plans |
| `xTrack/GLOBAL_CONTEXT.md` | routing row, summary row, focus-history paths (2026-09-11 entry + this session's) |
| `docs/maro-code.md` | feature → code map row label |
| Live pointers in other features | Mergitur `FEAT_HYD` · Markers 260622 plan · Ui_Settings tab-finalization plan · TracksImport map-render-visibility plan (3 sites) · Performance power-management plan · Ui_Menu decisions doc · WorkflowImprovement bake-sweep + implemented-migration plans |
| Code | `TrackViewModel.kt` KDoc path · `maro.properties` comment |
| Kotlin classes, XML, resources named after the feature | none — 0 hits in `*.xml`, no class, package or resource token |
| `AGENTS.md`, `docs/cmd_help_*.md`, adapters | none — no reference to the feature name |

## 3. Decisions

- **D1 — branch (decided 2026-09-14).** The migration runs on `feature/track-speed`, cut from `origin/develop`. The base carries less xTrack state than the branches that hold the 2026-09-11→13 work, so the cross-feature references reachable only from those branches are re-checked when they merge.
- **D2 — historical prose (decided 2026-09-14).** Filenames and prose are both renamed — plan titles, `**Feature:** BoatTrace` headers and the text of shipped `## Implemented` entries included — so no `BoatTrace` text survives anywhere outside `xxArchive/`, which is never entered (§7a). Objection: this inflates the diff and edits shipped records; the accepted trade is one consistent name across the live tree.
- **D3 — routing keywords.** Keep the row's intent keywords (`boat, trace, trip, boat-trace, boat-tracing, recording, port-salis, journey`) and add `tracks, track`; the overlap with TracksImport's keywords is accepted.
- **D4 — plan home.** Filed under WorkflowImprovement, which routes `xtrack`, `workflow` and `commands`, and hosted the analogue migrations (`implemented-migration`, `docs-integrity`, `global-context-rules-migration`). Objection: the subject feature is Tracks, so the record sits away from the renamed material; counter: `xTrack/Tracks/` cannot host a plan before it exists.
- **D5 — fuzzy-resolve collision (accepted).** After the rename, `track` and `tracks` substring-match both `Tracks` and `TracksImport`, so the cascade loses its unique match; `#focus Tracks` and `#focus TracksImport` stay unambiguous as exact hits.

## 4. Steps

1. **Pre-flight (read-only).** `git branch --show-current`, `git status --short`, `git log --oneline -3`; confirm a clean tree, confirm the chosen base actually holds the current xTrack state (plan files present under `xTrack/BoatTrace/`), then create the branch per D1.
2. **Directory rename.** `git mv xTrack/BoatTrace xTrack/Tracks` so history follows the move.
3. **File rename.** `git mv` the three `FEAT_` files and all 53 plans, swapping the feature token only — `[YYMMDD]_FEAT_PLN_BoatTrace_[topic].md` → `[YYMMDD]_FEAT_PLN_Tracks_[topic].md`, dates untouched.
4. **Feature-file content.** Front-matter `name: Tracks`; H1 `# Feature: Tracks`; `**Feature:** BoatTrace` lines; intra-feature relative links; `## Docs` and `## Implemented` pointers.
5. **GLOBAL_CONTEXT.md.** Routing row path → `xTrack/Tracks/FEAT_DSC_Tracks.md` plus D3 keywords; Feature Summaries row name (one-liner kept); focus-history paths for both the 2026-09-11 entry and this session's entry.
6. **Docs sweep.** `docs/maro-code.md` row label, then a grep for `xTrack/BoatTrace` under `docs/` — `#doctor` check (o) requires every backticked path in `docs/*.md` to resolve.
7. **Cross-feature pointers.** The live references listed in §2, repaired per D2.
8. **Code comments.** `TrackViewModel.kt` KDoc path to the renamed plan, and the `maro.properties` comment in the same pass.
9. **Verify.** `#doctor` (shape f–h, registry k–o) plus a repo-wide grep for residual `BoatTrace`; expected residue is historical archived material only.
10. **Bake.** `#bake` on Tracks — feature summary, `FEAT_HYD_Tracks.md`, `Last Bake` — which also prunes Focus History back to 10.
11. **No build gate by default.** The change set is Markdown plus two comments and renames no identifier, so `apk-build.bat` proves nothing; run it only if step 8 grows past a comment.
12. **Commit** the migration as one commit (`#commit` asks first). Pushing stays user-owned.

## 5. Non-goals

- No `#rename-feature` command is registered — a post-task suggestion only.
- No change to the `data/track/` package, the protobuf schema, or any `Track*` identifier.
- No edits inside any `xxArchive/` folder.

## Outcome

**Executed 2026-09-14 on `feature/track-speed`** — the feature is `Tracks`, and nothing named `BoatTrace` survives outside `xxArchive/` and this record.

- **Renames:** 57 paths moved by `git mv` — `xTrack/BoatTrace/` → `xTrack/Tracks/` plus all 56 files inside (53 plans, `FEAT_DSC_`, `FEAT_HYD_`, `FEAT_DOC_`), the token swapped in the name only, `YYMMDD_` prefixes and topic slugs untouched, zero failures.
- **Text:** 142 occurrences of the exact token replaced across 56 files, then four closing edits found by verification — three cross-feature references in `xTrack/Markers/260624_…`, `xTrack/Markers/260702_…` and `xTrack/Mergitur/260911_…` (the last holding the only surviving dangling path), plus the D3 `tracks` keyword in the routing row.
- **Verification:** a repo-wide search returns zero hits in `*.kt`, `*.properties`, `*.bat` and all of `docs/`; the only `xTrack` survivors are this record and `xxArchive/260912_…_docs-integrity.md`, both deliberate. D1's staleness concern proved unfounded — `origin/develop` already carried the full 2026-09-13 xTrack state.
- **Deviations:** a throwaway `.bat` for the renames and a `.ps1` for the text pass were used and then deleted — cmd cannot substring-substitute a `for` variable and a batch line-read destroys Markdown blank lines and `!` — a deliberate bend of §9's CMD-only rule. `BoatTraceViewModel` in the verification plan's logcat line became `TracksViewModel`, which names a class that never existed either way. Only the PascalCase token was in scope, so the `boat-trace` / `boat-tracing` / `boat` / `trace` routing keywords and slugs stay, as D3 intended.
- **Committed and closed:** `322f4cc` on `feature/track-speed` staged 75 files — 55 renames, one delete/add pair, 17 modifications and this plan — leaving a clean tree, and the pointer now sits in WorkflowImprovement's `## Docs` and `## Implemented`.
- **One non-rename presentation:** the move recorded as delete+create is the one-line redirect stub `260618_FEAT_PLN_Tracks_adaptive-isstill-settings-redesign.md`, which git re-added rather than renamed after line-ending normalisation — content unchanged, history presentation only.
- **Still open:** `#bake` on Tracks, which fires only on explicit invocation and would also prune Focus History back to 10.
