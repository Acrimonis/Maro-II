# Context Hydration — WorkflowImprovement — 2026-09-11

## State
GLOBAL_CONTEXT.md is now state-only. The rules it carried were triaged bullet-by-bullet, not bulk-deleted: five were verbatim duplicates of AGENTS.md and were dropped; three environment facts moved to a new AGENTS.md §9 Environment & Tooling (as imperatives plus a pointer — README and BakeNormalization already own the build detail, so a fourth copy was avoided); two unique fragments were promoted into Core Directives (QUESTIONS, and the anti-`ready for #implement` clause folded into MODE LOCK); the auto-switch bullet was deleted as a MODE LOCK conflict. §7a gained the state-only invariant so the drift is explicitly forbidden, and `#doctor` gained a report-only lint for rule sections reappearing there. `#rule global:` now appends to AGENTS.md Core Directives; command doc, `#help rule`, and the bootstrap template changed together so the sections cannot regenerate.

## Target Files
- `AGENTS.md` — §9 Environment & Tooling, Core Directives, §7a invariant, §7b `#rule`
- `xTrack/GLOBAL_CONTEXT.md` — state-only sections
- `docs/cmd_help_rule.md`, `docs/cmd_help_doctor.md`
- `.claude/skills/xtrack/references/templates.md`

## Next Step
Deferred hygiene on GLOBAL_CONTEXT.md: the Ui_Settings summary cell is truncated mid-word, several one-liners have grown into changelogs, and the summaries table is not sorted by Modified desc.
