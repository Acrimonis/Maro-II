---
name: Documentation
status: active
created: 2026-06-11 06:42
modified: 2026-06-11 10:38
active_subfeature: none
---

# Feature: Documentation

**Description:**
Cross-cutting project documentation — README, FAQs, setup guides, architecture docs, git workflow, and maintenance of docs/ directory. Ensures all plans and reference docs are properly organized and attached to the correct features.

## Subfeatures

### cleanup  [x]

#### Todos
- [x] List all `plans/*.md` files and classify by target feature
- [x] List all unattached `docs/*.md` files and classify by target feature
- [x] Attach or move plans/docs to their corresponding feature `## Docs` sections
- [x] Resolve ambiguous cases through discussion

#### Rules

#### Key Files

### help  [x]

#### Todos
- [x] Split `docs/cmd_help.md` into per-command detail files: `docs/cmd_help_[cmd].md`
- [x] Update `AGENTS.md` `#help` reference to use the new split files
- [x] Update `xTrack/WorkflowImprovement/FEAT_DSC_WorkflowImprovement.md` ## Docs to reference the split files

#### Rules

#### Key Files
- `docs/cmd_help.md` — summary reference table
- `docs/cmd_help_*.md` — per-command detail files
- `AGENTS.md` — §7b.8 Command Reference spec

### git rules  [x]

#### Todos
- [x] Rewrite `docs/GIT_WORKFLOW.md` into compact token-optimized source of truth (~30 lines)
- [x] Update `AGENTS.md` §7b.8 to reference `docs/GIT_WORKFLOW.md` instead of inline rule
- [x] Update `docs/cmd_help_git.md` — compact, reference GIT_WORKFLOW.md
- [x] Update `docs/cmd_help.md` summary — compact git section, ref GIT_WORKFLOW.md
- [x] Update `xTrack/GLOBAL_CONTEXT.md` Global Rules — ref GIT_WORKFLOW.md

#### Rules
- `docs/GIT_WORKFLOW.md` is the single source of truth for git rules.
- Upper-layer files (AGENTS.md, cmd_help_*.md, GLOBAL_CONTEXT.md) only reference it.
- 🔴 Hard rule: `#merge`/`#push`/`#commit` refuse on `develop`/`main`.

#### Key Files
- `docs/GIT_WORKFLOW.md` — source of truth
- `docs/cmd_help_git.md` — compact command reference
- `AGENTS.md` §7b.8 — hard rule reference
- `xTrack/GLOBAL_CONTEXT.md` — Global Rules reference

### verif-scattering  [x]

#### Todos
- [x] Scan `AGENTS.md` for scattered/duplicated instructions across sections
- [x] Scan `xTrack/GLOBAL_CONTEXT.md` Global Rules + Global Instructions for overlap with AGENTS.md
- [x] Scan `docs/cmd_help*.md` for drift vs AGENTS.md §7b spec
- [x] Scan `.clinerules`, `CLAUDE.md` — ensure they only point to AGENTS.md
- [x] Propose consolidations: compact token-optimized rewrites

#### Rules
- AGENTS.md is the canonical rulebook — vendor files (.clinerules, CLAUDE.md) must only be thin adapters.
- One source of truth per concern; no duplication across files.

#### Key Files
- `AGENTS.md` — canonical rules
- `xTrack/GLOBAL_CONTEXT.md` — routing + global rules
- `docs/cmd_help*.md` — command reference
- `.clinerules`, `CLAUDE.md` — vendor adapters

### readme  [x]

#### Todos
- [x] Update tech stack with missing AGP 8.4.1 row
- [x] Rewrite Quick Build as three-stage bake→build→deploy pipeline (from FEAT_DSC_BakeNormalization.md)
- [x] Remove stale doc references (MARKER_SIZING.md, DepthMappingBake.md)
- [x] Modernize project structure tree (add data/, tools/, xTrack/, proto/, gradle/libs.versions.toml)
- [x] Sync documentation index (add MARO_ARCHITECTURE.md, remove dead entries)

#### Rules
- README scope tag must remain `<!-- scope: core -->`
- Pipeline model: bake = data prep, build = package only, deploy = install+launch

#### Key Files
- `README.md` — updated project readme
- `xTrack/BakeNormalization/FEAT_DSC_BakeNormalization.md` — bake pipeline reference

### planneding  [x]

#### Todos
- [x] Move each plan to `xTrack/[Feature]/FEAT_PLN_[Feature]_[topic].md`
- [x] Update `## Docs` references in target feature files
- [x] Leave app icon/image assets (`.ico`, `.png`, `.pdn`, `.bat`) in `plans/` as non-plan artifacts

#### Rules
- Feature-scoped plans → `xTrack/[Feature]/FEAT_PLN_[Feature]_[topic].md`
- Only `.md` plan files are moved; binary/image assets stay in `plans/`

#### Key Files
- `plans/` — legacy directory to migrate from

## Todos

## Rules
- Feature-scoped discussion/plan files go in `xTrack/[Feature]/FEAT_PLN_[Feature]_[topic].md` (not in `plans/`). The `plans/` directory is legacy; new plans use the `FEAT_PLN_` convention under the target feature's xTrack directory.
## Key Files

## Docs
- `docs/FAQ.md` — Project FAQ
- `docs/SETUP.md` — Project setup guide
- `xTrack/Documentation/FEAT_PLN_Documentation_git-protection-workflow.md` — Git protection enforcement flow design
- `xTrack/Documentation/FEAT_PLN_Documentation_instruction-consolidation-audit.md` — Scattered/duplicated instructions audit findings
- `xTrack/Documentation/FEAT_PLN_Documentation_readme-update.md` — README update discussion & proposed changes
- `xTrack/Documentation/FEAT_PLN_Documentation_git-merge-command.md` — Git merge command design
- `xTrack/Documentation/FEAT_PLN_Documentation_git-move-command.md` — Git move/rename command design
- `xTrack/Documentation/FEAT_PLN_Documentation_round-1-summary-round-2-plan.md` — Round 1 summary and round 2 plan
