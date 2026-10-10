<!-- scope: feature -->
# Documentation — Decisions

> Harvested from the feature's plans and from the shipped files on 2026-10-10. Each entry names the
> decision, the reason behind it and the file that carries it. Live state: [`FEAT_DSC_Documentation.md`](FEAT_DSC_Documentation.md).

## Doc ownership and placement

- **Feature material lives under `xTrack/[Feature]/`; `docs/` holds cross-cutting reference only.** A shared
  `plans/` folder was deleted after its 37 files were classified and re-homed into the feature owning each
  concern, three cross-cutting ones among them. Reason: one home per fact — a file listed twice drifts.
  → `260717_FEAT_PLN_Documentation_file-naming-and-cleanup.md`
- **Plans are named `YYMMDD_FEAT_PLN_[Feature]_[topic].md`, the prefix being the creation date.** 198 files
  were renamed by `git mv` in one pass. Reason: chronological sorting inside a feature directory with no
  registry to maintain. → same plan
- **Cross-references are rewritten by per-file string replacement, never through a shared buffer.** The first
  attempt concatenated and wrote 37,896 lines into `GLOBAL_CONTEXT.md`; that run was reverted. → same plan
- **The templates are a sub-truth pointed from `AGENTS.md`'s Lazy-Load Index, and their former home under
  `.claude/skills/xtrack/references/` was removed.** Reason: adapters carry pointers, never content (§8a).
  → `docs/xtrack-templates.md`

## Code navigation map

- **`docs/maro-code.md` is the single source of truth for feature-to-code mapping, and it replaced `## Key
  Files` as that map.** Feature files keep only the files specific to their current work. Reason: two copies
  of one list guarantee drift. → `260717_FEAT_PLN_Documentation_maro-code-map.md`
- **Granularity is package-level entries plus ~15–20 anchor classes — no methods, signatures or supporting
  classes.** The rot curve is exponential in depth: a package rarely changes, an anchor class is the stable
  entry point, and a method signature rots within days. → same plan
- **Drift is detected passively rather than scanned.** An agent that finds a referenced file missing or
  renamed flags it in the hydration `## Drift Log`, which `#doctor` surfaces and `#doctor fix` clears.
  Reason: cheaper than any periodic scan, and it fires exactly when the stale reference is used. → same plan

## Reference-doc shape

- **`README.md` is orientation only — no versions and no inventories, pointers instead.** Versions are
  authoritative in `gradle/libs.versions.toml` and the wrapper properties. Reason: the README had
  accumulated rows that were simply false, such as a non-existent `docs/MARKER_SIZING.md` and a missing AGP
  line. → `260611_FEAT_PLN_Documentation_readme-update.md`, `README.md`
- **No doc restates a value the build files hold.** `docs/SETUP.md` and `docs/FAQ.md` both carried
  `platforms;android-34` while `app/build.gradle.kts` declares `compileSdk = 36`; both now point at the
  declaration. → `docs/SETUP.md`, `docs/FAQ.md`, `app/build.gradle.kts`
- **A thing lives once: the command rows in `AGENTS.md` §7b, the behaviour in `docs/cmd_help_[cmd].md`, the
  summary view derived in `docs/cmd_help.md`.** The audit that established this removed the duplicated
  Explain/Discuss Gate and the `GLOBAL_CONTEXT.md` restatements of §7a, some 300 tokens off the
  always-loaded prefix. → `260611_FEAT_PLN_Documentation_instruction-consolidation-audit.md`

## Git detail

- **`docs/GIT_WORKFLOW.md` owns the branch model, the exit and the conflict policy; the prohibition and the
  command rows are single-sourced in `AGENTS.md`, which wins on any conflict.** The doc-level Hard-Rule
  section that plan proposed was reversed, since CENTRALISE AND TRIM requires one home for a rule.
  → `260917_FEAT_PLN_Documentation_git-protection-incorporation.md`
- **A command refused on a protected branch is completed on the new branch afterwards, without asking
  again.** Reason: the invocation was the go-ahead, and the move changes the target rather than the
  entitlement. → same plan, `docs/GIT_WORKFLOW.md` §Protected branches
- **`#move` and `#move new` are the two halves of the old *new branch or existing branch?* question, so
  each gained the half it lacked** — bare `#move` lists local branches newest first, bare `#move new`
  prompts with `feature/` prefilled. → `260610_FEAT_PLN_Documentation_git-move-command.md`
- **`#merge` is a smart sync from `origin/develop` into the current feature branch, classified trivial or
  non-trivial, and it never writes `develop` or `main`.** → `260610_FEAT_PLN_Documentation_git-merge-command.md`

## Feature bookkeeping

- **A plan is in design until its pointer appears in the feature's `## Implemented`; a shipped plan is a
  retirement candidate, and only `#archive` may enter an archive folder.** → `AGENTS.md` §7a

## Findings not actioned — 2026-10-10

- `260610_FEAT_PLN_Documentation_git-merge-command.md` and `260610_FEAT_PLN_Documentation_git-move-command.md`
  are hosted here while declaring themselves discussion for WorkflowImprovement's `gitting-it`; moving a file
  is not a refresh's remit, so the ownership question is left open.
- `260612_FEAT_PLN_Documentation_round-1-summary-round-2-plan.md` documents RegulatedZones work — multi-source
  normalization, boat size and category filtering — although it sits under `Documentation/`.
- `260717_FEAT_PLN_Documentation_maro-code-map.md` still declares **Status: discussion** while its work
  shipped and its pointer sits in `## Implemented`.
- `docs/xtrack-templates.md` tells a plan to complete with `## Outcome` while the corpus uses `## Implemented`;
  already carried as a global todo.
- `docs/SETUP.md` names `C:\Users\nbadino\…` paths in its environment table and keeps its own `## FAQ`
  section beside `docs/FAQ.md`.
