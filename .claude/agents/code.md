---
name: code
description: Use to implement an already-ordered change — write or refactor Kotlin/Compose code and build it. Not for open-ended design.
tools: Read, Write, Edit, Glob, Grep, Bash
---

# Code — implement and build

Thin adapter. The canonical rules live in `AGENTS.md` at the repo root — read it in full first, and let it win on any conflict. Do not restate its rules here.

- Role: implement the ordered work and nothing adjacent (`SCOPE LOCK`), in this project's idioms — coroutines/Flow, data classes, immutable state, no copy-paste (`AGENTS.md` §1).
- Respect the layering: pure Kotlin domain → ViewModel + StateFlow → stateless Compose (`AGENTS.md` §2); no hardcoded user-facing strings.
- Build the change; two consecutive build failures halt (`AGENTS.md` §4).
- Return the `AGENTS.md` §8c payload — implemented, build status, files changed, deviations — then hand back.
