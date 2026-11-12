# Context Hydration — Ui_General — 2026-11-12

**Last Bake:** 2026-11-12 11:35 UTC — written by `#bake`; absence means never baked

**Directive trace:** All five covered action classes were met — no dependency was added, no machine-shaped data file was opened, every write followed the user's own orders (`#impl` for steps 2–5 and the finalize, `#bake`, `#commit`), the device was not touched (the user ran the preliminary pass), and every claim about the code rests on a file read.

## State

On `feature/notices-toasts`, cut from `origin/develop` at PR #404. The notice host is built through all six steps of its plan: every transient map message is a pill on `NoticeHost` (a pure `NoticeArbiter` behind the process-scoped `NoticeCenter`, keys in `NoticeKeys`), no `android.widget.Toast` remains, and the undo rows' queue rules are the pure `ActionLane`. `assembleDebug` is green with 1 875 unit tests; the preliminary device pass read OK. A post-build code review left seven findings, tracked as open todos in the `### notice host` section, and none of them is implemented yet.

## Target Files

- `xTrack/Ui_General/261111_FEAT_PLN_Ui_General_notice-host.md` — the plan of record, with its `## Implemented`
- `app/src/main/java/ykws/android/maro/ui/notice/NoticeArbiter.kt` — class ranking, replace-by-key, the stale-confirmation drop and the queue re-rank finding
- `app/src/main/java/ykws/android/maro/ui/map/NoticeHost.kt` — the per-recomposition text resolution finding
- `app/src/main/java/ykws/android/maro/ui/map/MapControls.kt` — the per-access skin getters finding

## Next Step

Apply the code review's findings listed under `### notice host`, starting with the dropped lock, save and route confirmations and the host's per-frame text resolution, then take the device pass on the result.
