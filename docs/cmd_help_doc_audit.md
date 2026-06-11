## #doc audit

Audit all documentation for structural issues.

  [no param]      Scan docs/**/*.md (recursive), xTrack/*/FEAT_DOC_*.md,
                  xTrack/*/FEAT_PLN_*.md.
                  Report:
                  (a) docs missing <!-- scope: ... --> tag
                  (b) orphan docs (attached to no feature ## Docs / #### Docs, not README.md)
                  (c) docs with invalid scope (not core|onboarding|feature|reference|archived)
                  Print findings grouped by severity. Does NOT auto-fix.
