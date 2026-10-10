---
name: Documentation
status: active
created: 2026-06-11 06:42
modified: 2026-10-10 11:53
---

# Feature: Documentation

**Description:**
Cross-cutting project documentation — the README, machine onboarding and troubleshooting, the git detail, the
feature-to-code map, and the maintenance of `docs/`.

## Implemented

- **maro-code map re-verified against the tree (2026-10-10)** — `docs/maro-code.md` checked row by row against `app/src/main/java/ykws/android/maro`: the Package Layout rows for `data/model`, `data/depth` (plus a new `data/depth/validation/` row), `data/track`, `data/regulation`, `data/markers`, `data/power`, `spatial`, `spatial/multipass`, `ui/components`, `ui/markers/wizard`, `ui/icons` and `config` corrected, a `data/route/` row added, the `RouteEngine.kt` anchor updated to its four engines and `RouteEngineChoice.kt`, the volatile MapScreen line-range and OverlayLayer param-count anchors dropped, and three dead names removed — the tree overruling two brief claims → [`260717_FEAT_PLN_Documentation_maro-code-map.md`](260717_FEAT_PLN_Documentation_maro-code-map.md)
- **docs reconciled with the corpus (2026-10-10)** — the SDK level pinned in `docs/SETUP.md` and `docs/FAQ.md` had drifted from `app/build.gradle.kts` (`android-34` against `compileSdk = 36`) and both now point at the declaration; the decisions doc raised and the unattached 260917 plan attached → [`FEAT_DOC_Documentation_decisions.md`](FEAT_DOC_Documentation_decisions.md)
- **maro-code-map** — `docs/maro-code.md`, the feature-to-code map: package skeleton plus anchor classes, single source of truth for source mapping → [`260717_FEAT_PLN_Documentation_maro-code-map.md`](260717_FEAT_PLN_Documentation_maro-code-map.md)
- **file-naming-and-cleanup** — 198 plans date-prefixed, 37 `/plans/` files re-homed into their features and the folder deleted, 38 cross-references rewritten → [`260717_FEAT_PLN_Documentation_file-naming-and-cleanup.md`](260717_FEAT_PLN_Documentation_file-naming-and-cleanup.md)
- **readme** — rebuilt as orientation only: the three-stage pipeline, one layout table, pointers where version pins stood → [`260611_FEAT_PLN_Documentation_readme-update.md`](260611_FEAT_PLN_Documentation_readme-update.md)
- **git-protection-incorporation** — a refused command's exit completed and bare `#move` given its picker; the plan's doc-level Hard-Rule premise reversed → [`260917_FEAT_PLN_Documentation_git-protection-incorporation.md`](260917_FEAT_PLN_Documentation_git-protection-incorporation.md)
- **verif-scattering** — instructions duplicated across `AGENTS.md`, `GLOBAL_CONTEXT.md`, the command pages and the adapters audited and consolidated → [`260611_FEAT_PLN_Documentation_instruction-consolidation-audit.md`](260611_FEAT_PLN_Documentation_instruction-consolidation-audit.md)
- **git shortcuts** — `#merge`, `#move` / `#move new` and `#cherry` / `#copy` designed here, their rows and their page now owned by WorkflowImprovement → [`260610_FEAT_PLN_Documentation_git-merge-command.md`](260610_FEAT_PLN_Documentation_git-merge-command.md) · [`260610_FEAT_PLN_Documentation_git-move-command.md`](260610_FEAT_PLN_Documentation_git-move-command.md)
- **help** — `docs/cmd_help.md` split into one page per command (planless)
- **git rules** — `docs/GIT_WORKFLOW.md` cut to the branch model, the exit and the conflict policy (planless)
- **planeding** — the `xTrack/[Feature]/YYMMDD_FEAT_PLN_*` convention adopted corpus-wide (planless)

## Rules
- Feature-scoped plans and docs live in `xTrack/[Feature]/`, plans named `YYMMDD_FEAT_PLN_[Feature]_[topic].md`; `docs/` holds cross-cutting reference only.
- `docs/maro-code.md` is the single source of truth for feature-to-code mapping — feature files point at it and never repeat its listings.
- No doc restates a version or an inventory the build files hold; it points at the declaration.

## Key Files
- `README.md` — orientation: what the app is, how to build it, where things live
- `docs/maro-code.md` — feature-to-code navigation map
- `docs/SETUP.md` · `docs/FAQ.md` — machine onboarding, troubleshooting
- `docs/GIT_WORKFLOW.md` — branch model, exit and conflict policy
- `AGENTS.md` · `xTrack/GLOBAL_CONTEXT.md` — the Lazy-Load Index row and the routing and summary rows this feature maintains

## Docs
- [`FEAT_DOC_Documentation_decisions.md`](FEAT_DOC_Documentation_decisions.md) — this feature's decisions and their rationale
- `docs/maro-code.md` — feature-to-code navigation map
- `docs/FAQ.md` — build and setup troubleshooting
- `docs/SETUP.md` — machine onboarding
- `docs/GIT_WORKFLOW.md` — the git detail kept off `AGENTS.md`
- [`260610_FEAT_PLN_Documentation_git-merge-command.md`](260610_FEAT_PLN_Documentation_git-merge-command.md)
- [`260610_FEAT_PLN_Documentation_git-move-command.md`](260610_FEAT_PLN_Documentation_git-move-command.md)
- [`260611_FEAT_PLN_Documentation_git-protection-workflow.md`](260611_FEAT_PLN_Documentation_git-protection-workflow.md)
- [`260611_FEAT_PLN_Documentation_instruction-consolidation-audit.md`](260611_FEAT_PLN_Documentation_instruction-consolidation-audit.md)
- [`260611_FEAT_PLN_Documentation_readme-update.md`](260611_FEAT_PLN_Documentation_readme-update.md)
- [`260612_FEAT_PLN_Documentation_round-1-summary-round-2-plan.md`](260612_FEAT_PLN_Documentation_round-1-summary-round-2-plan.md)
- [`260717_FEAT_PLN_Documentation_file-naming-and-cleanup.md`](260717_FEAT_PLN_Documentation_file-naming-and-cleanup.md)
- [`260717_FEAT_PLN_Documentation_maro-code-map.md`](260717_FEAT_PLN_Documentation_maro-code-map.md)
- [`260917_FEAT_PLN_Documentation_git-protection-incorporation.md`](260917_FEAT_PLN_Documentation_git-protection-incorporation.md)
