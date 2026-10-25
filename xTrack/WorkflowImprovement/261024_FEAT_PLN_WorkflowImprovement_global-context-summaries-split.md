<!-- scope: feature -->
# GLOBAL_CONTEXT — Split the Feature Summaries out

## Problem
`xTrack/GLOBAL_CONTEXT.md` is read at every cold open, yet its `## Feature Summaries` block carries full per-feature prose — the Route card alone is 25 bullets that read like a changelog. A Tracks or GPS session pays for every feature's prose. The file must stay small enough for on-demand reads.

## Decisions
- The per-feature prose moves out of `GLOBAL_CONTEXT.md` into each feature's own `FEAT_DSC_[Feature].md`.
- `GLOBAL_CONTEXT.md` keeps a per-feature index row — name, status, created, modified and a link to the feature file — so the single-file skim and the dates survive.
- The `## Routing Map` stays — it is what lets a session suggest a focus automatically.
- Focus History, Global Todos and the doc index stay; they are global by nature.
- This reverses decision 2 of `260609_FEAT_PLN_WorkflowImprovement_feat-summary-layer.md` (summaries embedded as a table in GLOBAL_CONTEXT).

## Shape
`GLOBAL_CONTEXT.md`'s `## Feature Summaries` block becomes `## Features`, one metadata row per feature:

| Feature | Status | Created | Modified | Link |
|---|---|---|---|---|
| Route | active | 2026-08-16 10:44 | 2026-10-23 14:47 | [FEAT_DSC_Route.md](Route/FEAT_DSC_Route.md) |

Each `FEAT_DSC_[Feature].md` gains a `## Summary` block that `#bake` owns as one writable block:

- [one sentence: only what the next reader would otherwise have to go and find]

## Blast radius
The block is read and written by more than one command, so the move is a change to the tooling, not a file move:

- `#list` — reads the `## Features` index (metadata + link, no bullets); bullets come from the feature file on `#focus` / `#status`.
- `#bake` — step 3 rewrites the feature's `## Summary` in `FEAT_DSC_` and bumps the feature's `## Features` row dates in `GLOBAL_CONTEXT.md`.
- `#doctor` — checks that assert the block's shape or staleness re-point.
- `docs/xtrack-templates.md` — GLOBAL_CONTEXT and FEAT_DSC templates change.
- `AGENTS.md` §7a state list and §7b `#list` / `#bake` rows change.
- `docs/cmd_help_list.md`, `docs/cmd_help_bake.md` — command pages change.

## Settled points
- OP1 · `#list` shows metadata + link only; the bullets belong to per-feature reads.
- OP2 · the "date history of each focus" is the Focus History stack, and it stays untouched; the refactor never rewrites it. The index row's created/modified pair is OP1's metadata, not this point.
- OP3 · a dedicated `## Summary` section in `FEAT_DSC_[Feature].md`, separate from `## Description` and `## Implemented`.
- OP4 · `#bake` stays the writer; the one-off migration prunes each card by hand first, keeping the current-state one-liner and the open owes, and dropping a dated bullet only after confirming it exists in `## Implemented` or the Focus History.

## Implemented
- Shipped 2026-10-24 on `feature/workflow`: the `## Feature Summaries` block became a 25-row `## Features` index (name · status · created · modified · link); each `FEAT_DSC_` gained a `## Summary`; Route was pruned to a current-state one-liner plus the owed list; the templates, the §7a/§7b rows and the eight cmd_help pages were re-pointed. Deviations: none — the index keeps the block's pre-existing row order rather than Modified-desc, which `#list` sorts at display.
