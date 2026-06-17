## #implement

Full implementation pipeline: Code → build → Ask review → Architect report.

  [no param]         Execute the pipeline on the active feature's plan/todos.
  [feature]          Execute the pipeline on the named feature.

**Pipeline:** Code (implement + apk-build.bat) → Ask (feature coverage + code health, one pass each) → Architect (report). Error back-off (§4) applies. No git auto-write. No Code↔Ask ping-pong.

See `AGENTS.md §7b.16` for full spec.
