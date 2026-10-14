# Context Hydration — WorkflowImprovement — 2026-10-14

**Last Bake:** 2026-10-14 07:59 UTC

**Directive trace:** Since the last bake (2026-10-04 00:34 UTC) this feature's own work ran inside a Route session, on the user's words: "Update the rule to: have this text be with as little verbosity as possible, have each sentence followed by a line break", then "reformat to they are bulleted points paragraphs with metadata on top", `discuss`, `eli cap on what`, "It depends on the relevance of the context to save doesn't it?", and `#impl`. No dependency was added, no machine-shaped data file was opened, no work was started unordered, the device was never touched, and every claim about the corpus follows a file read.

## State

**The feature summaries became cards, and the rule that shapes them was rewritten.** The `## Feature Summaries` table in `GLOBAL_CONTEXT.md` — 26 rows, one of them a single line of 4 646 characters — became 26 cards: one metadata line (name · status · created · modified), then bullets, one sentence each. The discipline is **relevance, not a count**: a bullet carries only what the next reader would otherwise have to go and find, and is kept only while it still passes that test, applied when the card is rewritten rather than when it is read. That test is the point — "as little as possible" had been the intent for months while the Route row still reached 4 646 characters, because the intent was applied only to what was being added and never to what was already there.

**The shape has one home.** [`docs/xtrack-templates.md`](../../docs/xtrack-templates.md:33) carries the block; `docs/cmd_help_bake.md` step 3, `docs/cmd_help_list.md`, `docs/cmd_help_track.md`, `docs/cmd_help_status.md`, `docs/cmd_help_now.md`, `docs/cmd_help_doc_update.md`, the master `docs/cmd_help.md` and `AGENTS.md`'s `#list` and `#track` rows all point at it rather than restating it.

**The dates were settled, and the drift was not the migration's.** An independent review found the cards disagreeing with their feature files' front-matter on 9 `created` and 10 `modified` values. `git show HEAD` against the replaced table then settled it 26-for-26: the migration had preserved every value, and **the old table was itself the drifted copy**. The cards now restate each feature file's front-matter, which is the one home, so 14 cards and 19 values were re-synced and the two can no longer drift apart.

**One long-standing gap closed by removal rather than workaround.** The Route row resisted four consecutive bakes because it was a single 4 646-byte line no whole-line matcher could edit. It is a card now, its date is correct, and the line it was no longer exists.

**Open and untouched.** The `#archive sweep` extension is specified but unbuilt ([`260919_FEAT_PLN_WorkflowImprovement_archive-sweep.md`](260919_FEAT_PLN_WorkflowImprovement_archive-sweep.md:1), its walk closed, its Stage B unimplemented), and the feature's standing todos stand: the rule-lifecycle residuals, the review's two-force reading, the `ASK, DON'T GUESS` first-clause rescope, the `#doctor` adapter check (t), and the deferred on-device git verification.

## Target Files

- `xTrack/GLOBAL_CONTEXT.md` — the 26 cards and the focus entry
- [`docs/xtrack-templates.md`](../../docs/xtrack-templates.md:33) — the shape's one home
- `docs/cmd_help_bake.md`, `cmd_help_list.md`, `cmd_help_track.md`, `cmd_help_status.md`, `cmd_help_now.md`, `cmd_help_doc_update.md`, `cmd_help.md` — the pointers to it
- `AGENTS.md` — §7b's `#list` and `#track` rows
- [`260919_FEAT_PLN_WorkflowImprovement_archive-sweep.md`](260919_FEAT_PLN_WorkflowImprovement_archive-sweep.md:1) — the plan still in design

## Next Step

Implement Stage B of the archive-sweep plan at its §7 order, beginning with the §7b row and the reserved word; decide `#doctor` check (t) with `.claude/agents/` now in scope rather than the three adapters it names.
