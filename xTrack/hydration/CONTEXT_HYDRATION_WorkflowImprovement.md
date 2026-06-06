# Context Hydration — WorkflowImprovement — 2026-06-06

**Active Subfeature:** none

## State
**AGENTSmdNormalization workstream complete** (all 6 fixes implemented + committed on branch `feature/ai-tooling`, pushed to `origin`). Tip `e806c22`. Subfeature 18/18; status stays `active` only because the post-merge reconcile parent todo is pending. Self-validation green (front-matter counts match checkboxes; no stale singular `CONTEXT_HYDRATION.md` refs; dates normalized; rename `#list`→`#features` complete with alias kept; new `#feature` orientation command everywhere). Branch is **not yet merged** to `develop` — PR not opened.

## Target Files
- `AGENTS.md` (canonical § 7a/7b incl. new § 7b.12 `#doctor` and § 7b.13 `#feature`)
- `.claude/skills/xtrack/{SKILL.md, references/commands.md, references/templates.md}`
- `docs/cmd_help.md`; `xTrack/FEATURE_SCOPE_*.md` (front-matter); `xTrack/hydration/`

## Next Step
Open PR `feature/ai-tooling → develop` and merge. Then execute the parent-level reconcile procedure when spatial branches (e.g. `feature/300M-Claude-II`) later land on `develop`.

## Still open (other workstream — separate branch)
- **Coastline** (`feature/300M-Claude-II`, folder `D:\.src\Maro_II_c`): on-device «Côte» full-regen check of 6 OSM seamarks + Tradelière; verify no Zone300 donut around hazard rings. State preserved in that folder's own `xTrack/`.
