# AGENTS.md Token Optimization Plan

**Feature:** WorkflowImprovement — subfeature `hard rules`
**Date:** 2026-06-20
**Branch:** `feature/ymwflow`

## Goal

Reduce AGENTS.md from ~207 lines to ~125 lines by compressing verbose sections and delegating detail to `docs/cmd_help_*.md`.

---

## Changes

### 1. Header banner (lines 1-14) → 5 lines

Compress the quote block, adapter table, and prose into compact bullet points:

```
AGENTS.md — Canonical rulebook for Maro-II.
Adapters: CLAUDE.md = @AGENTS.md import | .clinerules = pointer | .claude/skills/xtrack/ = #-commands.
Section numbers (3, 6, 7a, 7b, ...) stable — referenced from other docs.
```

### 2. Core Directives (lines 16-79) → Tighten ~10%

- Remove blank lines between rules (save ~8 lines)
- "You are allowed to challenge" → 1 line: `- You may challenge ideas, but defer to my judgement.`
- Compress Explain/Discuss Gate exceptions to single line each
- Remove "Exception:" redundancy in WRITE-ONCE (already clear from Explain/Discuss Gate section)

### 3. Developer Profile (lines 81-85) → 3 lines

Merge:
```
- User: Senior Java backend dev → Android/Kotlin. Map ViewModels/Repos ↔ Spring Beans/Services,
  StateFlow ↔ reactive streams. Highlight idiomatic Kotlin (coroutines, data classes, functional collections).
- Async: Kotlin Coroutines + Flow only — no raw threads or executors.
```

### 4. §2 Component Extraction (lines 90-95) → 3 lines

Compress numbered steps:
```
- **Extraction:** Port one component at a time: (1) domain logic pure Kotlin, (2) ViewModel with StateFlow/Coroutines, (3) stateless Compose UI bound to ViewModel state.
```

### 5. §7b Command Reference (lines 120-177) → ~25 lines (biggest win)

Replace verbose descriptions with compact reference table:

```
# 7b. xTrack — Command Reference
Intercept #-prefix. Fuzzy-resolve per protocol below. Full detail: #help [cmd].

**Fuzzy Resolution:** exact → substring → edit-distance → reject. Cascade stops on first unique match.

**Feature File FM:** YAML front-matter: name, status, created, modified (YYYY-MM-DD HH:mm UTC), active_subfeature.

| #cmd | Action |
|------|--------|
| #list | Dashboard of all features (from GLOBAL_CONTEXT.md Feature Summaries) |
| #focus [name] | Pivot active feature; bare=prompt pick; sub-commands: #focus sub, #focus out |
| #track [name] | Create new feature file + GLOBAL_CONTEXT.md routing/summary rows |
| #sub [name] | Decompose subfeature under active feature; bare=list |
| #bake | Snapshot: update checkmarks, feature summary, front-matter date, hydration file, Last Bake |
| #todo | 3-tier: bare=list, /description=append, [target]:/description=cross-feature |
| #rule | Same 3-tier as #todo |
| #doc | Sub-commands: create, list, read, attach, detach, audit, update |
| #status | Dashboard of active/named feature; #status diff for changes since last bake |
| #now | Lightweight orientation: active feature, subfeature, CWD, Last Bake |
| #help [cmd] | Lazy-load docs/cmd_help_[cmd].md; bare=print reference table |
| #doctor | Lint xTrack (a-j checks); #doctor fix for auto-repairs |
| #merge | Rebase+force-push with AI conflict resolution (#merge [branch]) |
| #implement | Pipeline: Code→Ask→Architect with review gates |

Detailed specs for each command in docs/cmd_help_*.md — loaded by #help.
```

### 6. §8 Handoff Protocol (lines 179-206) → ~18 lines

Compress table, remove repetitive prose:
- The table stays but rows condense
- §8a adapter files: compress to 2 lines
- §8c payload format: compress to 3 lines

---

## Execution Order (single write)

All changes to AGENTS.md as one bulk apply_diff:
1. Header + Core Directives + Dev Profile + §1 + §2 + §3 + §4 + §5 + §6 + §7a + §7b + §8

No other files need changing — this is AGENTS.md only.

---

## Files Touched

| File | Change |
|------|--------|
| [`AGENTS.md`](AGENTS.md) | Token optimization — ~207 → ~125 lines |

## Verification

- All §7b commands still present in the reference table
- Fuzzy resolution protocol retained (critical for #-command dispatch)
- Core Directives intact — no behavioral rules removed
- `#help [cmd]` fallback documented so AI knows where to find full detail
