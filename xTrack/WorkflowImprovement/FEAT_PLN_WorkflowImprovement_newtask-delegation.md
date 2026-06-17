<!-- scope: feature -->
# new_task Delegation & #implement Pipeline

## Problem
- Architect `switch_mode` → Code costs user approval, context reload, lost planning context
- No auto-return: Code finishes, stays in Code mode, no handoff back to Architect

## Part A: `new_task` Delegation (Small Atomic Ops)
- **Default** for small CLI ops (git, adb, build) — prefer over `switch_mode`
- **Tool:** `new_task(mode=code, message=..., todos=...)` already available in Architect
- **Context passing:** plan-file path in `message` + executable checklist in `todos`. Code reads from disk — no context dump.
- **Flow:** Architect defines → Code executes → Code completes → Architect resumes

### Auto-Return to Architect
- Code entered via `switch_mode` from Architect → auto-return with `switch_mode(architect, ...)`
- Code entered directly → stay
- `new_task` subtask → auto-returns by design
- **Report:** what was implemented, build status, files changed, issues/deviation

## Part B: `#implement` Pipeline

### Flow
```
Architect → Code (implement + compile) → Ask (review) → Architect (report)
```

### Steps
1. **Code** — implements + runs `apk-build.bat`. Build fails → fix, retry (max 2 consecutive §4)
2. **Ask (feature coverage)** — one pass: does impl cover the scope from FEAT_DSC_*.md?
3. **Ask (code health)** — one pass: spaghetti, factorization, maintenance issues within scope?
4. **Architect** — report: what was built, build status, Ask findings, gaps/issues

### Rules
- ❌ No Code↔Ask ping-pong (each Ask review is single pass)
- ❌ No git auto-write (§5) — Code stages only, user runs `#commit`
- ❌ Ask does NOT check build issues (that's Code's gate)
- ❌ Ask does NOT check external libs or out-of-scope refactors
- ✅ Error back-off (§4) per-step within Code's build loop

### AGENTS.md §7b Add
> **16. `#implement`:** Full pipeline: Code → implement + build → Ask → feature coverage + code health (one pass each) → Architect → report. Error back-off per-step. No git auto-write. No Code↔Ask loops.
