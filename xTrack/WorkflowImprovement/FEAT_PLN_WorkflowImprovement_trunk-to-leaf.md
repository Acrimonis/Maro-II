<!-- scope: feature -->
# AGENTS.md Trunk-to-Leaf Token Optimization

**Feature:** WorkflowImprovement
**Date:** 2026-06-28
**Branch:** feature/wf-plus

## Goal

Restructure AGENTS.md as the single trunk that routes to all other docs. Eliminate duplication, condense rarely-triggered sections, add a Lazy-Load Index so every reference doc is discoverable from one place.

## Changes

### 1. Merge §5 into Core Directives (eliminate duplication)

Remove §5 (lines 92-96). Move the one unique line into Core Directives git rule (line 26-30). The Core Directives already have the full git policy; §5 restates it.

**Before (Core Directives + §5):** 12 lines
**After (Core Directives only):** 7 lines — add "`git add` may stage for `#commit`" to the existing rule
**§5 becomes:** `# 5. Git Operations — See Core Directives above +` [`docs/GIT_WORKFLOW.md`](docs/GIT_WORKFLOW.md) `.`

### 2. Condense §§1-4 (rarely triggered, always loaded)

| § | Current | Target |
|---|---------|--------|
| §1 MAD | 2 lines | 1 line: "Port legacy → Compose + ViewModel + StateFlow + Coroutines/Flow. Never copy-paste legacy." |
| §2 Extraction | 3 lines | 1 line: "Greenfield: (1) pure Kotlin domain, (2) ViewModel+StateFlow, (3) stateless Compose UI." |
| §3 Token | 3 lines | 1 line: "Bulk writes, strict context isolation — see Core Directives WRITE-ONCE + CONCISE." |
| §4 Loop | 4 lines | 2 lines: "Max 3-5 loops. 2 consecutive build failures → ask. New deps → approval first." |
| §6 Spatial | 2 lines | Keep as-is (already minimal pointer) |

**Saves: ~9 lines**

### 3. Compress §8 Handoff Table

Remove the "Every mode follows..." preamble. Compact the table headers.

**Saves: ~4 lines**

### 4. Add Lazy-Load Index (NEW)

```
## Lazy-Load Index

Load these only when the task domain matches:

| Task | Load |
|------|------|
| Spatial, bathymetry, depth, coastline | `docs/MARO_ARCHITECTURE.md` |
| UI components, layouts, theme | `docs/ui-component-guidelines.md` |
| Drawers, bottom sheets, overlays | `docs/ui-drawer-guidelines.md` |
| Color tokens, theming, palette | `docs/color-scheme.md` |
| Material Symbols icons (standalone) | `docs/material-icons-standalone-guide.md` |
| Git workflow, merge strategy, conflicts | `docs/GIT_WORKFLOW.md` |
| Project setup, build, deploy | `docs/SETUP.md` |
| FAQs, common issues | `docs/FAQ.md` |
| Any #-command detail | `docs/cmd_help_[cmd].md` via `#help` |
```

### 5. GLOBAL_CONTEXT.md — tighten Global Instructions

Condense 5 lines → 3 lines. Reference the Lazy-Load Index in AGENTS.md.

## Files Touched

| File | Change |
|------|--------|
| `AGENTS.md` | Merge §5→Core Directives, condense §§1-4, compress §8, add Lazy-Load Index |
| `xTrack/GLOBAL_CONTEXT.md` | Tighten Global Instructions, reference AGENTS.md Lazy-Load Index |

## Token Impact

| File | Before | After | Delta |
|------|--------|-------|-------|
| AGENTS.md | 161 lines | ~148 lines | -13 lines (-8%) |
| GLOBAL_CONTEXT.md | ~99 lines | ~97 lines | -2 lines |

## What Does NOT Change

- All Core Directives preserved verbatim
- §7a/§7b command table untouched
- #-command behavior unchanged
- No rules weakened or removed
