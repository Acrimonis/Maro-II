# Context Hydration — WorkflowImprovement — 2026-11-11

**Last Bake:** 2026-11-11 17:22 UTC

**Directive trace:** Since the last bake (2026-10-24 07:24 UTC) this feature's work ran on the user's words: "#focus workflow", "#new workflow-tracking", "do this", "#impl" twice, "force stage script" and "#bake and commit". No dependency was added (PowerShell and git only), no work started unordered, the device was never touched, and every claim about the corpus follows a file read; the local Claude transcripts, machine-shaped files, were sampled and counted to learn their shape and are now read by the backfill.

## State

Workflow-usage tracking is built and ready to commit on `feature/workflow-tracking`: `tools/usage-log.ps1` (force-added past the `/tools/` ignore), the hook in `.claude/settings.json`, the `merge=union` line, and the doc and rulebook edits. The backfill wrote `usage/digest.md` from the local transcripts (2608–2609 expired) and the first report printed, its removal list provisional. Two Ask hops are folded: four of the first eight should-fix items were applied and the rest kept in the plan and a feature todo, and the second hop found one claimed blocker that did not reproduce and one fix, applied. The hook is confirmed live; a real compound is unverified.

## Target Files

- tools/usage-log.ps1 — capture, flush, compound, slugs, report, backfill
- .claude/settings.json, .gitattributes — the hook and the union merge
- xTrack/WorkflowImprovement/usage/ — the generated record (script-read only)
- xTrack/WorkflowImprovement/261111_FEAT_PLN_WorkflowImprovement_workflow-tracking.md — the design, its Outcome and the kept Ask items

## Next Step

Let tracking accumulate, then review the kept Ask items (the feature todo) and run the first real compound once a raw month has closed.
