<!-- scope: feature -->
# AGENTS.md Optimization Pass — Findings & Suggestions

## Redundancies / Double Entries

| Issue | Current | Suggested |
|---|---|---|
| **🔴 develop/main rule in §1 Core Directives** (line 22) **+ §7b item 8** (line 77) **+ GIT_WORKFLOW.md** | Rule appears 3x with slightly different wording | Keep only in §1 Core Directives + GIT_WORKFLOW.md. Remove from §7b item 8. |
| **"doc sync" still referenced** (line 77) | Lists `doc sync` as sub-command + maps to `docs/cmd_help_doc_sync.md` | Remove — deprecated this session |
| **Cache-Prefix Preservation** (line 44) **vs Cache Interlock** (line 46) | Both say "group changes, avoid task switching, preserve cache" | Merge into one rule: **Cache Optimization** |
| **Zero-Piecemeal Writes** (line 43) explanation is verbose | "CRITICAL: Consolidate ALL... You are FORBIDDEN from... and then..." | Tighten: "Write each source file exactly once per task. No edit-after-write." |

## Token Optimization Opportunities

| Section | Current size | Savings |
|---|---|---|
| **§7b item 8 — Command Reference** (line 77) | ~6 lines listing every command + sub-command mapping | ~40%: Remove inline command list — `#help` already reads `cmd_help.md` |
| **§7b item 12 — #doctor** (line 88) | ~8 lines with 9 sub-checks (a-i) | ~50%: Shorten to key categories, full spec in cmd_help_doctor.md |
| **§7b item 15 — merge** (lines 98-109) | ~12 lines with 6 sub-points (a-f) | ~40%: Core protocol in AGENTS.md, full detail in GIT_WORKFLOW.md |
| **#doc sub-commands** (lines 81-87) | Each repeats "fuzzy-resolve against docs/**, xTrack/*/FEAT_DOC_*, xTrack/*/FEAT_PLN_*" | ~30%: Consolidate preamble: "All doc sub-commands use fuzzy-resolve (see §7b preamble)" |

## Clarity Improvements

| Current | Problem | Fix |
|---|---|---|
| §4 Max Iteration Cap (line 50) | 3-5 turns → break → 3 turns → checkpoint. Confusing. | Simplify: "Max 5 autonomous turns, then force checkpoint. Break complex tasks into smaller subtasks." |
| §7b item 5 — Memory Bake (line 74) | Single 5-step sentence, hard to parse | Numbered list |
| §7b item 6 — Todo (line 75) | Nested parenthetical logic | Compact table format |
| §3 Zero-Piecemeal (line 43) | "CRITICAL" + "FORBIDDEN" + "Explain/Discuss exception" all in one | Split: rule + exception |

## Deprecated References to Remove

- Line 77: Remove `doc sync` from command list
- Line 77: Remove `doc sync → docs/cmd_help_doc_sync.md` mapping
- Line 77: Remove `doc_sync` from known sub-commands

## Suggested Rewrite Order (by impact)

1. **Remove doc sync references** — quick fix, 2 deletions
2. **Merge Cache-Prefix + Cache Interlock** — saves ~2 lines
3. **Consolidate #doc preamble** — saves ~4 lines across 6 sub-commands
4. **Shorten #doctor to key spec** — saves ~4 lines
5. **Remove inline command list from item 8** — saves ~6 lines
6. **Shorten #merge section** — saves ~5 lines
7. **Simplify §4 loop control** — clarity improvement

Total estimated savings: **~25 lines** (from ~110 → ~85) without losing any feature coverage.
