<!-- scope: feature -->
# Session-review fixes — four findings against the rulebook

**Status:** shipped 2026-09-17 on `feature/hide-law`.
**Origin:** a review asked for at the close of the hide-land-water-icon session, measuring that session against `AGENTS.md`. It found no blocking defect — one unsourced claim, caught and corrected during the run — and four should-fix items, every one of them in the rulebook and its docs rather than in the shipped code.

## F1 — `#bake` never refreshed the Focus History entry it left behind

- `#focus` pushes a one-liner describing the *intent*. The bake's eight steps refreshed the summary row, the front-matter date and the hydration, and pruned the stack, so an implemented session left the top of the stack describing a plan that no longer existed — the class the 2026-09-17 review had already flagged as stale live records.
- Shipped: the §7a Focus History bullet and the §7b `#bake` row name the duty; `docs/cmd_help_bake.md` gained it as step 6, with the retirement report and the workspace clear renumbered to 8 and 9; the derived `docs/cmd_help.md` follows.
- The live entry was repaired with it — the Ui_Settings line now states what shipped, its timestamp left as pushed.

## F2 — nothing routed to the new capability

- The routing map sends `ui` to Ui_Dashboard, and the Ui_Settings row carried `settings, preferences, config, scroll, options`, so a cold session asking for this toggle would have repeated the five-way ambiguity that made `#focus ui` unresolvable.
- Shipped: `land/water icon`, `show land/water icon` and `earth/water` joined the Ui_Settings row.
- Named, not fixed: the five-way match on `ui` itself belongs to the `#focus` cascade against feature *names*, which a routing keyword cannot change.

## F3 — `#commit` and `#push` were specified twice, in opposite directions

- §7b's rows said each "asks before" acting; the Core Directive says an invoked git command is its own go-ahead, executed rather than re-asked, with only the scope confirmed. The session resolved the clash with one scope gate naming the staged set, the message and the target — a reading, not a rule.
- Shipped: both §7b rows and the two rows in `docs/cmd_help_git.md` now say what they confirm and call the gate a scope gate, never a permission one; the derived view matches.

## F4 — the hydration shape was stated twice

- `docs/xtrack-templates.md` defines the shape, while `docs/cmd_help_bake.md` step 5 restated a shortened form of it — and the estate carries three title variants: the template's, `# Hydration — X`, and a `Session:` variant written by the two most recent bakes.
- Shipped: the template block declares itself the shape's only statement, and the bake page points at it instead of restating it.
- Not normalised by hand: files predating the template, and the recent ones that lack a `Directive trace:` line, converge at their next bake, which rewrites the file whole. A trace written now would describe a session that has already ended, which the template now forbids.

## Outcome

All four shipped in one pass on 2026-09-17 — docs and rulebook only, no code, no build. Files touched: `AGENTS.md` (four edits), `docs/cmd_help_bake.md`, `docs/cmd_help_git.md`, `docs/cmd_help.md`, `docs/xtrack-templates.md` and `xTrack/GLOBAL_CONTEXT.md` (two edits), plus this plan and its pointer. No deviation from the findings as described; the two decisions taken are F4's, that the estate converges by bake rather than by a hand sweep, and F2's residual, named rather than papered over.
