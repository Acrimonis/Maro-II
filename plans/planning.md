<!-- scope: feature -->
# Planning — Zero-Piecemeal Writes Discussion Exception

**Feature:** WorkflowImprovement
**Subfeature:** planning
**Created:** 2026-06-08

## Problem

The Zero-Piecemeal Writes rule (§3) and Explain/Discuss Gate (Core Directives) together prevent any file creation during discussion mode, even for legitimate planning documents. This forces the architect to hold all planning output purely in chat, losing it when context is cleared.

Additionally, context-changing commands (`#focus`, `#sub`) are blocked during discussion, preventing the user from pivoting the conversation scope naturally.

## Solution

Two targeted exceptions:

### 1. Explain/Discuss Gate — Plan File Exception

During discussion (prompt ending with "explain" or "discuss"), the agent may create **exactly one** markdown file at `plans/[topic].md` to capture the discussion topic. This file must be auto-attached to the active feature's `## Docs`.

Rationale: one file maximizes cache stability (no piecemeal writes), keeps the discussion persistent across context clearances, and the file is trackable via `#doc list` / `#status`.

### 2. Explain/Discuss Gate — Context Commands Exception

`#focus`, `#sub`, `#sub out` are permitted even during discussion. The agent must automatically follow these commands to pivot context.

Rationale: these are xTrack management commands, not implementation — they manage scope and context, not code. Blocking them during discussion prevents the user from steering the conversation effectively.

## File

- Created: `plans/planning.md` (this file)
- Attached to: `xTrack/FEATURE_SCOPE_WorkflowImprovement.md` `## Docs`
