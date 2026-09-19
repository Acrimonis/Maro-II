<!-- scope: feature -->
# Adapter Clean-Up — Pointers Only, No Content

**Feature:** WorkflowImprovement · **Date:** 2026-09-13 · **Branch:** `feature/wrKFl` · **Status:** shipped — applied 2026-09-13, awaiting `#bake`'s fold
**Trigger:** §8a and AGENTS.md's own header both declare `CLAUDE.md`, `.clinerules/` and `.claude/skills/xtrack/` to be thin pointers with no content, yet `.claude/skills/xtrack/references/templates.md` held 182 lines of live file templates and was named as a writable surface by most plans since June — including three edits made earlier the same day.

## Problem

Two rules disagreed and one of them was silently losing. §8a makes every adapter a pointer, while the feature file's `## Key Files` and nine plans treat a file inside `.claude/` as the live template source. The practical consequence is that rulebook content was being edited in the one place rules are not allowed to live, and nothing flagged it because no check covers adapter content.

## Change

1. **Rehomed** the fixtures to `docs/xtrack-templates.md` — the sub-truth home every other reference already uses — with a scope tag and a banner stating it is pointed at from AGENTS.md.
2. **Pointed** AGENTS.md at it: one Lazy-Load Index row, "xTrack file shapes — FEAT_DSC, FEAT_HYD, FEAT_DOC, FEAT_PLN, GLOBAL_CONTEXT, Walk, INDEX".
3. **Deleted** `.claude/skills/xtrack/references/templates.md` and the now-empty `references/` folder; `.claude/skills/xtrack/` holds `SKILL.md` alone, which is already a compliant pointer to AGENTS.md §7a/§7b.
4. **Repaired** the live references naming the old path — the WorkflowImprovement `## Key Files` line, its hydration's target-file list, and the walk-stack plan's surfaces row and ship order.
5. **Rode along with the move:** the two edits made to the old file the same day — the `## Walk` level fixtures and the removed duplicate sentence — are content, so relocating preserved both and nothing was reverted.

## Surfaces

| Surface | Change | State |
|---|---|---|
| `docs/xtrack-templates.md` | created — the fixtures, with scope tag and sub-truth banner | applied 2026-09-13 |
| `.claude/skills/xtrack/references/templates.md` | deleted, folder with it | applied 2026-09-13 |
| `AGENTS.md` | Lazy-Load Index row pointing at the new home | applied 2026-09-13 |
| `xTrack/WorkflowImprovement/FEAT_DSC_WorkflowImprovement.md` | `## Key Files` line → new path | applied 2026-09-13 |
| `xTrack/WorkflowImprovement/FEAT_HYD_WorkflowImprovement.md` | target-file list → new path | applied 2026-09-13 |
| `xTrack/WorkflowImprovement/260913_FEAT_PLN_WorkflowImprovement_walk-stack.md` | surfaces row and ship order → new path | applied 2026-09-13 |

## Out of scope

- **No `#doctor` check (s).** A report-only lint that an adapter carries nothing but a pointer is the obvious regression guard, and it is deliberately left out of this pass as its own decision; `#doctor` stays a–r.
- **No rewrite of the adapters.** `SKILL.md`, `CLAUDE.md` and `.clinerules/rules/agents-source-of-truth.md` keep their current wording; the clinerules file's closing rationale is content by the letter of §8a, flagged rather than trimmed.
- **No archive sweep.** Historical plans that name the old path stay as records of what was true then, including the closed command-flow plan and everything under `xxArchive/`, which feature-summarising commands exclude anyway.
- **No change to the templates themselves**, beyond the walk fixtures that landed earlier the same day.

## Outcome

**Shipped 2026-09-13 on `feature/wrKFl`** — the 182-line template fixtures moved to `docs/xtrack-templates.md` with a scope tag and a banner, `AGENTS.md`'s Lazy-Load Index gained the pointing row, `.claude/skills/xtrack/references/` was deleted so `SKILL.md` stands alone as a pointer, and the four live references to the old path were repaired. Deviations: the two edits made to the old file the same day rode along with the move rather than being reverted. Left out by design: no adapter-content check for `#doctor`, a decision still carried in the feature's `## Todos` as check (t).
