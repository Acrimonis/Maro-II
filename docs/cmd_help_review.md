<!-- scope: reference -->
## #review

An independent review of the artefact the cascade resolves — the reviewer argues against the work.

  [no param]       Resolve in order: the active item at the top of the walk stack → the active feature's plan still in
                   design → the last `#implement` run's ## Target Files when that plan is implemented
                   → with no artefact at all, the live proposal, reviewed in place as a challenge.
                   Print "Reviewing X because Y" before starting.

  [target]         A name or ID fuzzy-resolves to the artefact it matches; an ambiguous match asks
                   instead of picking.

**In design** follows the plan lifecycle in `AGENTS.md` §7a: implemented-ness is read from the feature's
`## Implemented` pointer alone — never from a plan's own status line, so a stale header cannot steer
the cascade.

**Guard:** while an `#implement` pipeline is running, that pipeline owns its Ask hop — `#review`
performs no mid-run review, and the pipeline is never steered from outside it.

A challenge is deliberately weaker than a review: the same agent argues against itself, so it is
flagged as self-review rather than independent confirmation.
