<!-- scope: reference -->
## #implement

Full implementation pipeline: Code → build → Ask review → Architect report.

  [no param]         Execute the pipeline on the active feature's plan/todos.
  [feature]          Execute the pipeline on the named feature.

**Pipeline:** Code (implement + apk-build.bat) → Ask (feature coverage + code health, one pass each) → Architect (report). Error back-off (§4) applies. No git auto-write. No Code↔Ask ping-pong.

**Hops per harness:** Roo — `switch_mode` Code → Ask → Architect, each hop carrying its §8c payload. Claude Code — the main session implements and builds, calls the `ask` subagent with its payload and Target Files, then writes the report and `## Implemented` itself; the subagent's final message is its payload.

**Guard:** a running pipeline owns its Ask hop — `#review` performs no mid-run review, and the pipeline is never steered from outside it.

See the `#implement` row in `AGENTS.md` §7b for the command spec.
