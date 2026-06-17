<!-- scope: feature -->
# Instruction Scattering & Duplication Audit

## Findings

### 🔴 Issue 1: Explain/Discuss Gate duplicated in §1 and §3

**AGENTS.md §1 (Core Directives), line 21:**
Full rule: "If prompt ends with discuss/explain → discussion only. Exceptions: (a) one plans file, (b) xTrack commands."

**AGENTS.md §3 (Token Optimization), line 42:**
Same exception repeated verbatim: "Exception — Discussion Plan Files: During discussion... exactly one markdown file... Context-changing commands are permitted."

**Fix:** Remove the duplicate from §3. Add a brief cross-ref: "See §1 Core Directives for the Explain/Discuss Gate." Saves ~6 lines.

### 🔴 Issue 2: GLOBAL_CONTEXT.md Global Instructions overlap AGENTS.md §7a

**GLOBAL_CONTEXT.md lines 63-65:**
- "xTrack #-command system is canonical" → duplicates AGENTS.md §7a
- "On Turn 1: read GLOBAL_CONTEXT.md, match intent" → duplicates AGENTS.md §7a line 61
- "Route docs, key files, todos to correct scope" → duplicates AGENTS.md §7b.11

**Fix:** Condense to one line: "Follow AGENTS.md §7a/7b for xTrack workflow." Saves ~4 lines.

### 🟡 Issue 3: GLOBAL_CONTEXT.md line 66 stale file paths

```
`docs/cmd_help.md`, `references/fuzzy-resolve.md`, and `references/templates.md`
```

The last two files are at `.claude/skills/xtrack/references/`, not `references/`.

**Fix:** Update paths or remove the stale reference. Saves 0 lines (but fixes correctness).

### 🟡 Issue 4: cmd_help_git.md duplicates cmd_help.md summary

Before the split, cmd_help.md had full descriptions. After the split, cmd_help.md has compact one-liners and cmd_help_git.md has slightly more detail. The git section in cmd_help.md still has inline descriptions that partially duplicate cmd_help_git.md.

**Fix:** Make cmd_help.md summary git section even more terse — just the command name and "see cmd_help_git.md". Saves ~5 lines.

### ✅ No issue: .clinerules and CLAUDE.md

Both are correctly thin adapters pointing to AGENTS.md. No changes needed.

### ✅ No issue: cmd_help_*.md drift vs AGENTS.md §7b

The per-command files were generated directly from the monolithic cmd_help.md which was in sync with AGENTS.md §7b. No drift detected.

---

## Proposed consolidations

| # | File | Change | Token savings |
|---|---|---|---|
| 1 | AGENTS.md §3 line 42 | Remove Explain/Discuss Gate duplicate; add cross-ref | ~6 lines (~120 tokens) |
| 2 | GLOBAL_CONTEXT.md lines 63-65 | Condense to single reference line | ~4 lines (~80 tokens) |
| 3 | GLOBAL_CONTEXT.md line 66 | Fix stale reference paths | ~0 tokens (correctness) |
| 4 | docs/cmd_help.md git section | Make more terse, refer to cmd_help_git.md | ~5 lines (~100 tokens) |

**Total potential savings:** ~300 tokens from the Always-Loaded Context prefix — small but improves cache-hit ratio.

**Design principle applied:** One source of truth per concern, references only everywhere else.

