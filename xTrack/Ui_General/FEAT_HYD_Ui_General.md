# Context Hydration — Ui_General — 2026-11-12

**Last Bake:** 2026-11-12 13:25 UTC — written by `#bake`; absence means never baked

**Directive trace:** All five covered action classes were met — no dependency was added, no machine-shaped data file was opened, every write followed the user's own orders (`#impl` for the steps, the finalize and the review fixes, `#bake`, `#commit`), the device was not touched (the user ran both device passes), and every claim about the code rests on a file read.

## State

On `feature/notices-toasts`, cut from `origin/develop` at PR #404. The notice host is built, reviewed and device-validated: every transient map message is a pill on `NoticeHost` (a pure `NoticeArbiter` behind the process-scoped `NoticeCenter`, keys in `NoticeKeys`), no `android.widget.Toast` remains, and the undo rows' queue rules are the pure `ActionLane`. The post-build code review's seven findings are folded (waiting notices show late instead of dropping, queued replacements re-rank, the host resolves text once per notice, the center tracks attached hosts for startup posts and orphan statuses); `assembleDebug` is green with 1 882 unit tests, and the user's device pass on the final build read OK. The `### notice host` section is folded into `## Implemented`; its rules, docs and key files now sit at feature level.

## Target Files

- `xTrack/Ui_General/261111_FEAT_PLN_Ui_General_notice-host.md` — the plan of record, with its `## Implemented` and `## Outcome`
- `app/src/main/java/ykws/android/maro/ui/notice/NoticeArbiter.kt` — class ranking, replace-by-key and re-rank, late-not-dropped waiting
- `app/src/main/java/ykws/android/maro/ui/notice/NoticeCenter.kt` — attached-host count, startup restamp, orphan-status guard
- `app/src/main/java/ykws/android/maro/ui/map/NoticeHost.kt` — text resolved once per notice

## Next Step

No open todo in the notice host. Remaining Ui_General todos: the route-dialogs device pass and the three diagnostic log lines in `RouteHost`.
