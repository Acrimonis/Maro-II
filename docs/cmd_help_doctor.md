<!-- scope: reference -->
## #doctor

Lint the xTrack stack for structural drift.

  [no param]  Run all checks and print findings grouped by severity.
              Checks: routing row duplicates, duplicate rules, deprecated active_subfeature/#sub remnants,
              date format, status vs completion, malformed sections,
              orphan docs, missing routing rows, Feature Summaries ↔ FEAT_DSC_ mismatch,
              rule sections in GLOBAL_CONTEXT.md (rules belong in AGENTS.md).

  fix         Auto-repair safe classes: dedupe routing rows, normalize dates,
              strip active_subfeature/#sub remnants, sync Feature Summaries ↔ FEAT_DSC_ files.
              Report-only items (malformed sections, orphan docs, status/rule-dedupe
              judgment calls, rule sections found in GLOBAL_CONTEXT.md) are printed
              but not auto-fixed.
              #bake runs the auto-fix subset first.
