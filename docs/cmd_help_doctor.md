<!-- scope: reference -->
## #doctor

Lint the xTrack stack for structural drift.

  [no param]  Run all checks and print findings grouped by severity.

              Structure (a-e): routing row duplicates · duplicate rules · deprecated
              active_subfeature/#sub remnants · date format · status vs completion.
              Shape (f-h): malformed sections · missing routing rows · Feature Summaries
              ↔ FEAT_DSC_ mismatch.
              Ownership (i-j): rule sections in GLOBAL_CONTEXT.md (rules belong in AGENTS.md) ·
              orphan docs inside xTrack (the docs/ footprint is `#doc audit`'s job).
              Registry integrity (k-o): command rows diverging between AGENTS.md §7b,
              docs/cmd_help.md and docs/cmd_help_git.md · backticked paths in AGENTS.md and
              docs/*.md that do not resolve (skip [placeholders] and glob patterns) · duplicate
              bullets in AGENTS.md · `plans/` residue or *plan*.md outside xTrack/ · a feature's
              ## Docs index disagreeing with disk.
              Doc drift (p): semver-like tokens in README — README is pointer-only.

  fix         Auto-repair safe classes: dedupe routing rows, normalize dates,
              strip active_subfeature/#sub remnants, sync Feature Summaries ↔ FEAT_DSC_ files.
              Everything from shape, ownership, registry integrity and doc drift onward is
              report-only — printed, never auto-fixed.
              #bake runs the auto-fix subset first.
