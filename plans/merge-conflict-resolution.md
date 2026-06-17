<!-- scope: feature -->

# AI-Assisted `#merge` Conflict Resolution

## Problem

Today [`#merge`](docs/cmd_help_git.md:13) runs `git rebase origin/develop` + `git push --force-with-lease`. When conflicts arise, git pauses mid-rebase and the user must resolve each conflicted hunk manually. No automated strategy is applied — the AI simply presents the conflict and waits.

This is slow and error-prone, especially for:
- **Tracking files** (`xTrack/GLOBAL_CONTEXT.md`, `xTrack/*/FEAT_DSC_*.md`) where both branches have valid independent entries
- **Shared source files** where two features modify different methods in the same class

## Proposal

### Policy: Feature-Owned Resolution

The active feature's [`FEAT_DSC_*.md`](xTrack/WorkflowImprovement/FEAT_DSC_WorkflowImprovement.md:150) `## Key Files` section defines which source files "belong" to it. During conflict resolution:

| Scope | Rule | Rationale |
|---|---|---|
| **File owned by active feature** | Accept `theirs` (feature branch) | The feature knows its own code |
| **File owned by a different feature** | Accept `ours` (develop) | Other feature's work is authoritative on develop |
| **File owned by no feature / shared** | Per-hunk AI inspection | Cross-reference hunk symbols against feature scope |
| **Ambiguous hunk** | Flag for manual review | Safety: don't guess |

### Resolution Protocol

```
#merge
```

1. `git rebase origin/develop` — standard rebase
2. On conflict → **AI intercepts**:
   a. Read each conflicted file
   b. Read the active feature's `## Key Files` and `FEAT_DSC_*.md` for other features owning the file
   c. For each conflict hunk:
      - **File-level match** → auto-resolve (feature wins / develop wins)
      - **Symbol-level match** → AI inspects if hunk symbols (function names, class names, field names) appear in the active feature's scope → if yes, keep `theirs`; if no, keep `ours`
      - **Ambiguous** → leave conflicted, report to user
   d. `git add` all auto-resolved files
   e. Present summary: *"Resolved N files automatically. M files need manual attention: [list]"*
3. User resolves remaining conflicts, runs `git rebase --continue`
4. `git push --force-with-lease`

### File Ownership Mapping

The Key Files in each [`FEAT_DSC_*.md`](xTrack/GLOBAL_CONTEXT.md:17) already define this. During `#merge` the AI:

1. Reads `xTrack/GLOBAL_CONTEXT.md` Routing Map to list all features
2. Reads each active feature's `FEAT_DSC_*.md` → collects `## Key Files` → builds a `path → feature name` map
3. For each conflicted file, looks up ownership

### Edge Cases

| Case | Handling |
|---|---|
| **File owned by no feature** (e.g., new file on both branches) | Flag for manual review — no ownership metadata |
| **File owned by multiple features** | Per-hunk symbol analysis; if symbols match active feature → `theirs`, else `ours` |
| **Both branches modify same feature's file** (e.g., both add to GPS settings) | Per-hunk: each hunk's symbol context determines winner. If truly overlapping (same line), flag for review |
| **Binary files** (`.png`, `.bin`, `.proto`) | Flag for manual review — AI cannot inspect symbols |
| **Build files** (`build.gradle.kts`) | Accept `ours` (develop) unless active feature's `Key Files` explicitly includes it |
| **xTrack tracking files** (`GLOBAL_CONTEXT.md`, `FEAT_DSC_*.md`) | Special merge: dedupe Routing Map, keep both features' rows, merge Subfeature lists, update Active Pointer to current branch |

### Implementation Notes

- Protocol runs **during** the rebase pause — not before, not after
- The AI reads the active feature's scope from `xTrack/` **before** starting the rebase, so it has context ready
- After resolution, the AI should **not** commit/push — it only stages resolved files. The user runs `git rebase --continue` then `#push` (or manual push)

### Resolved Decisions

1. **Always-on** — `#merge` auto-resolves conflicts by default. No `--auto` flag needed. If the AI makes a mistake, the user can `git rebase --abort` and resolve manually.
2. **Keep `## Key Files` + add `## OwnedFiles`** — `## Key Files` remains as-is for important source references. A new `## OwnedFiles` section is added to `FEAT_DSC_*.md` for explicit auto-resolution ownership declarations. Files in `## OwnedFiles` are the ones the AI auto-resolves in favour of the feature branch during conflicts.
3. **Guess, but prompt on high risk** — If `## OwnedFiles` is empty/missing, the AI scans the feature's description, todos, and `## Key Files` to infer likely owned files. If the confidence is low or the conflict touches critical infrastructure (build files, xTrack tracking files, protos), prompt the user instead of auto-resolving.
