<!-- scope: feature -->
# Walk Stack — Depth-1 Child Walks

**Feature:** WorkflowImprovement · **Date:** 2026-09-13 · **Branch:** `feature/wrKFl` · **Status:** shipped — steps 1–5 applied 2026-09-13, awaiting `#bake`'s fold
**Decision:** adopted. A walk descends one level into its active item, and the `#done` facet is retired — exhaustion is the only close. The containment size bound is rejected outright and the Clarity exception keeps the wording it shipped with.
**Origin:** remediation of `260912_FEAT_PLN_WorkflowImprovement_command-flow.md`, item 6 — a size bound would bound a failure never observed, while the failure this fixes happened seven exchanges deep for one line of rule text.
**Review pass:** 2026-09-13, folded below as D1–D8 — the opening trigger (a gate), the resolution rendering, parent-number stability, the C13 trim exclusion, the parked-level semantics, row and derived-file coherence, the schema fixture, and the D2/D5 contradiction, which the `#done` retirement dissolved rather than patched.

## Problem

A walk's active item sometimes needs several exchanges instead of one. The single cursor has no way to descend into an item and come back, so either the discussion hijacks the walk or the detail is crammed into one oversized reply — the same defect a block size bound would have attacked from the wrong end.

## Design

### State — the `## Walk` section

```markdown
## Walk
**Level 1 — Date:** [YYYY-MM-DD] · **Source:** [pending set / named plan] · **Active:** [n]
- [ ] 1 · [item]
- [ ] 2 · [subject] — summary; child walk open
- [ ] 3 · [item]

**Level 2 — Date:** [YYYY-MM-DD] · **Parent:** 2 · **Active:** [n]
- [ ] 1 · [sub-item]
- [ ] 2 · [sub-item]
```

- The child lives in the same section beneath its parent, opened by the bold **Level 2** line carrying its own date and a `Parent:` pointer to the parent item's number.
- The child is never `###`-prefixed, so section handling in `#bake` and `#doctor` cannot mistake it for a feature section.
- No child state lives in the reply — a thread kept only in the reply dies with the turn.
- The parent item stays a flat marker, so the reader sees the subject compressed and knows the detail is in its own thread.

### Opening a child

- A child opens when the active item cannot be resolved inside the reply — that is, when its answer ends in a gate, a question the user must answer before the item closes (D1).
- Opening is automatic and agent-side: no new facet, no new command, and the reply names the item it descended into.
- One child at a time. A second subject needing a thread waits until the first closes, which keeps the stack one deep by construction rather than by policing.
- While a child is open, its parent item is the active item of level 1 and cannot be exhausted, so nothing has to return the cursor by hand.

### Closing a child

- A level closes when its cursor advances past the last item, and that close is the whole mechanism — no close verb exists (D8, dissolved by the `#done` retirement).
- Closing writes a bullet summary of resolutions and of anything dropped into the parent item, and ticks the parent in the same step; `#skip` is what marks a drop, so the summary is complete by the time the level closes.
- The parent line becomes `- [x] n · [subject] — child walk closed: [one-line resolution, drops named]`, and the level-2 block is retained beneath it, snapshotted and never cleared.
- The resolutions stay flat: the parent line carries one summary sentence, and dropped points are named in that same line rather than nested beneath it.

### Rules

- **Depth 1.** A level-2 walk may not open a level-3 walk; a sub-item that would need more exchanges triggers the gate challenge instead, with promotion to a plan file as the escape for a genuinely large subject.
- **Parent numbering is frozen** while a child is open — parent items are not reordered, inserted or renumbered, so the `Parent:` pointer cannot dangle (D3).
- **One cursor, top of stack.** Bare `#walk` resumes the top level; `#next`, `#prev` and `#skip` act on the top level, and advancing past the last item of a child closes it and returns the cursor to its parent; at level 1 the same advance closes the walk. `#review`'s cascade step 1 stays the active item, which is now the top of the stack.
- **Stepping never interrogates.** The three exits — close, resume, park — are no longer asked during stepping; they are challenged at the two gates, which is where open points can actually be lost. A park is simply a level left unexhausted, and an unexhausted level is an open walk (D5).
- **Drops are promoted on close.** A skipped item's open points are promoted into the parent item's note at level 2, and into the digest's `## Outcome` when level 1 closes.
- **Any open level blocks both gates.** `#bake`'s fold and `#archive`'s retirement read any open level, including a parked parent beneath an open child.
- **Bake never trims walk items.** C13's live trim drops done todos, so the walk section is excluded from it at every level — a ticked walk item is history, not a completed todo (D4).
- **Bake snapshots every level and clears none**, and never copies walk state into `FEAT_HYD_`.
- **Turn 1 reports the top of the stack** and names the parked parent beneath it.
- **No new `#doctor` check.** The read path surfaces the stack; a lint would be a second witness for something the first already covers.

## Rejected

- **A containment size bound** — by lines, sentences or read time. Every variant measures a failure the record never produced, and the fix routes detail into files, which relocates the reading cost and adds an artefact to a repo already carrying a 198-file plan backlog. Rejected in favour of the child walk, which releases the same pressure by giving the subject its own thread.
- **`#done`** — retired. Exhaustion closes a level, `#skip` marks a drop, and the close-or-park challenge survives at the two gates; keeping the facet would have needed a conditional to stop a clean-close rule ticking a parent whose child still held open points.

## Surfaces

| Surface | Change | State |
|---|---|---|
| `AGENTS.md` §7b | `#walk` row — drop `#done`, note that exhaustion closes | applied 2026-09-13 |
| `docs/cmd_help.md` | `#walk` facet line — drop `#done` | applied 2026-09-13 |
| `docs/cmd_help_walk.md` | `#done` removed, close rule restated, exits moved to the gates | applied 2026-09-13 |
| `docs/xtrack-templates.md` | `## Walk` template gains the Level and Parent lines plus the closed-child rendering (D7) — rehomed there the same day by the adapter clean-up | applied 2026-09-13 |
| `docs/cmd_help_walk.md` | the full stack spec — levels, `Parent:` pointer, opening trigger, depth cap, return step, parked semantics | applied 2026-09-13 |
| `AGENTS.md` §7a | keep-criterion wording → any open level | applied 2026-09-13 |
| `docs/cmd_help_bake.md` | C12 clause → any open level; C13 exclusion for walk items | applied 2026-09-13 |
| `docs/cmd_help_archive.md` | fourth gate → any open level | applied 2026-09-13 |
| `docs/cmd_help_review.md` | cascade step 1 wording → the active item at the top of the stack | applied 2026-09-13 |

Three surfaces moved together for `#done` so check (k)'s row ↔ page ↔ line agreement holds (D6).

## Ship order

1. Retire `#done` across the row, the walk page's close rules and the `cmd_help.md` facet line — **applied 2026-09-13**.
2. `docs/xtrack-templates.md` — the schema, so the shape exists before the rules point at it.
3. `docs/cmd_help_walk.md` — the full stack spec.
4. `AGENTS.md` §7a — criterion wording only; §7b already carries the retirement.
5. The three gate surfaces — `cmd_help_bake.md` (C12 and C13), `cmd_help_archive.md`, `cmd_help_review.md`.

## Out of scope

- No block size bound, no containment change, no reply length rule beyond what already ships.
- No depth beyond 1, no walk state in hydration, no new `#doctor` check, no new facet — the stack reuses `#walk`, `#next`, `#prev` and `#skip`, and `#done` is gone.
- No change to how a walk is started from the pending set; only its ability to descend.

## Outcome

**Shipped 2026-09-13 on `feature/wrKFl`** — a walk descends one level into its active item, `#done` is retired so exhaustion is the only close, and the `## Walk` schema with its Level 2 and `Parent:` lines plus the closed-child rendering now lives in `docs/xtrack-templates.md`. The §7a keep-criterion, `cmd_help_bake.md` C12 and C13, the archive page's fourth gate and the review cascade's step 1 were re-pointed together, and the derived `cmd_help.md` facet line lost `#done`. Deviations: none material — the depth cap, the frozen parent numbering and the walk section's exclusion from the live trim all shipped as designed.
