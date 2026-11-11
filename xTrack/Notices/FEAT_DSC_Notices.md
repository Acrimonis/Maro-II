---
name: Notices
status: active
created: 2026-11-11 17:08
modified: 2026-11-11 17:25
---

# Feature: Notices

**Description:**
One notice host for every transient message on the map — a queue with priority and replacement rules in pure Kotlin, a stateless Compose face on the bottom-band pill skin — replacing the ad-hoc single-slot pills and the Android system Toasts the app still raises.

## Summary
- Planned, not built: all nine decisions (D1–D9) are settled in the plan — one pure `NoticeArbiter` with two lanes, pills above the drawer ladder and action rows under it, rows on the band's edge.
- The first build step is the properties move: the notice family's dwells, row timeout, exit window and skin figures leave code literals for `maro.properties`, the band geometry keys moving there from `colors.properties` under the same names.
- The plan's regression guards (§3) and ranked risks (§7) are the review checklist — above all that a row's timeout still commits deletes and route disposals.

## Sections

### notice host

The plan's migration order, each step built and tested before the next; step 0 is a pure move with no behaviour change.

#### Todos
- [ ] Step 0 — the notice family's properties consolidated into `maro.properties`, with the duplicate-key test and `BannerStartInsetTest` retargeted.
- [ ] Step 1 — `NoticeArbiter` + `NoticeCenter` + unit tests; the host replaces `MapStatusBannerStack` on a layer above the drawer ladder.
- [ ] Step 2 — the temperature notices and the speed-history result move onto the host.
- [ ] Step 3 — the exit and lock pills move onto the host.
- [ ] Step 4 — `MapDashboardController`'s queue moves into the arbiter's action lane.
- [ ] Step 5 — `MainActivity`'s three system Toasts post to the arbiter.
- [ ] Restate the 2026-10-23 z-order rule in Ui_General's `## Implemented` and `docs/ui-drawer-guidelines.md` §1, and add §5.7's "which vehicle when" rule, as the steps land.

#### Rules
- The notice lane paints above the drawer ladder; the action lane stays under it (user, 2026-11-11). Action rows sit on the band's edge, pills stacked above.
- A row's timeout commits its delete or route disposal: no notice may delay, pre-empt or drop a row, and the double-back window and the exit pill share one key.
- Moved properties keep their names and leave their old file in the same change; `ui.toast.*` colours stay in `colors.properties`.

#### Docs
- `xTrack/Notices/261111_FEAT_PLN_Notices_notice-host.md` — the decisions, the model, the guards and the migration order

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/MapControls.kt` — `MapBanner` family, `MapStatusBannerStack`
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — the single-slot pill states and their dwells
- `app/src/main/java/ykws/android/maro/ui/map/MapDashboardController.kt` — the only queue today (action rows)
- `app/src/main/java/ykws/android/maro/ui/map/TemperatureTile.kt` — first migration client
- `app/src/main/java/ykws/android/maro/config/AppConfig.kt` — the load order the properties move depends on
- `docs/ui-component-guidelines.md` §5.7 — the family's only rule home
