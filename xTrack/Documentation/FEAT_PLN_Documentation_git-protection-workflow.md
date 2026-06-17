<!-- scope: feature -->
# Git Protection — Workflow Enforcement Rules

## Rule

When editing [`docs/GIT_WORKFLOW.md`](docs/GIT_WORKFLOW.md), always ensure a **🔴 Hard Rule** section exists at the top forbidding direct pushes to `develop` or `main`.

## Enforcement Flow

If any git command violates this rule (push, commit, or merge attempted on `develop` or `main`), the AI must **immediately abort the operation** and propose to `#move` to a different branch:

1. **Ask:** new branch or existing branch?
   - **New branch** → prompt for name, prepopulating the suggestion with `feature/`
   - **Existing branch** → list all local branches sorted by last commit date descending, let the user pick

2. After the move via `#move new [name]` or `#move [name]`, re-execute the original command on the feature branch.

## Where to Enforce

| Layer | File | Current Status |
|---|---|---|
| ✅ Project rules | [`xTrack/GLOBAL_CONTEXT.md`](xTrack/GLOBAL_CONTEXT.md:51) | Already has a rule |
| ✅ Git workflow doc | [`docs/GIT_WORKFLOW.md`](docs/GIT_WORKFLOW.md) | Already has 🔴 section |
| ✅ Command ref detail | [`docs/cmd_help_git.md`](docs/cmd_help_git.md) | Already says 🚫 refuses |
| ✅ Summary table | [`docs/cmd_help.md`](docs/cmd_help.md) | Already shows 🚫 guards |
| ✅ AGENTS.md | [`AGENTS.md:76`](AGENTS.md:76) | Already has 🔴 hard rule |
| ✅ Global rules | [`xTrack/GLOBAL_CONTEXT.md`](xTrack/GLOBAL_CONTEXT.md:51) | Updated with move flow |
| ❌ Enforcement logic | Agent behavior | Needs build-time rule |

## Open Question

Should the `#move` prompt logic be:
- (a) A hardcoded rule in [`AGENTS.md`](AGENTS.md), or
- (b) A `#rule global` entry in [`xTrack/GLOBAL_CONTEXT.md`](xTrack/GLOBAL_CONTEXT.md), or
- (c) Both?

I'd suggest (b) — keeping it in [`GLOBAL_CONTEXT.md`](xTrack/GLOBAL_CONTEXT.md:51) Global Rules so `#doctor` can lint-check it, with an explicit mention in [`AGENTS.md`](AGENTS.md:76) §7b.8 since that's the command reference section.

