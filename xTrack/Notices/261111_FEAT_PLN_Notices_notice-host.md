<!-- scope: feature -->
# Notice host — one queue and priority for every transient map message

**Status:** in design (2026-11-11, `feature/notices-toasts`). Nothing in code until the user says `#impl`.
**Goal (decided, option C):** one notice host with a queue and priority, replacing the ad-hoc single-slot pills.

## 1. State of the code (re-read 2026-11-11)

- **Pill family.** `MapBanner` · `MapBannerText` · `MapStatusBanner` · `LockBanner` in `MapControls.kt`; one skin (`ui.toast.*`), clearance by `bannerStartInset`; rules only in `docs/ui-component-guidelines.md` §5.7.
- **Status pills already stack.** `MapStatusBannerStack` (added with the route-end work) draws `importMessage`, `trackOpStatus`, `trackSavedToast`, `routeNoticeToast`, `routeArrivalNotice` in one column, `.distinct()`, at `MapScreen.kt` ~5707 — so these five no longer overlap each other. **Correction to the brief:** what is still arbitrated by nothing is the column against the exit pill (`MapScreen.kt` ~5477), `LockBanner` (`MapLockLayer.kt`) and the undo rows (`MapSnackbarHost`), all anchored in the same band.
- **Each pill is its own state + timer in `MapScreen.kt`:** `importBanner` (6 000 ms), `routeNoticeToast` (2 000), `trackSavedToast` (2 000), `lockBanner` (2 000), exit banner (2 000), `trackOpStatus` (no timer — set and cleared by the operation), `routeArrivalNotice` (derived from route state, settle `route.follow.arrival.settleSec`). A same-kind message replaces the one showing and restarts the dwell (timestamp key on a `LaunchedEffect`).
- **Every dwell is a literal in code; none is in `maro.properties`** — the five above and the action row's `delay(4000L)` in `SnackRow` (`MapScreen.kt` ~482).
- **The exit pill's 2 s is also the double-back window.** `now - lastBackAt <= 2_000L` (`MapScreen.kt` ~1993) and the pill's `delay(2_000L)` (~2007) are one fact written twice; the pill must stand exactly as long as the window is open.
- **Only queue:** `MapDashboardController` — 3 visible slots + overflow, `ActiveSnack` only (`TrackDelete`, `MarkerDelete`, `CreateUndo`, `RouteEnd`); `RouteEnd` jumps the queue. A row's 4 s starts when the row is **composed**, so a queued row's commit is deferred until it shows; the timeout and the swipe are the same commit path.
- **Two bottom-band cards are not pills:** `LoadingOverlay` and `ErrorOverlay` (`CoastlineMapView.kt`) share the skin, are state-bound and carry Retry — in or out of the host is a D2 question.
- **One ViewModel already raises a notice through a flow:** `RouteViewModel.seatMovedAtEnd` (`SharedFlow<Unit>`, collected in `MapScreen.kt` ~1172, tested in `RouteAcquisitionTest`) — the D6 shape has a precedent beside `TemperatureViewModel.notices`.
- **Z-order.** The status stack and the exit pill live inside `MapContent`; `MapSnackbarHost` sits ahead of `OverlayLayer` (`MapScreen.kt` ~4100, by the 2026-10-23 toast-menu-z-order decision). Both paint **under** the menu, settings and list drawers, so a notice raised while a drawer is open is invisible — the reason `OverlayLayer.kt` ~1007 raises a system Toast for the speed-history result. **`LockBanner` is the exception:** `MapLockLayer` paints last, above the ladder and the lock scrim.
- **System Toasts still raised:** `MainActivity.kt` ×3 (`reportUnreadableColourKeys`, `reportUnclaimedRouteEngineId`, `reportFineTileReclaimed` — the last also posts a notification), `OverlayLayer.kt` ~1007, `TemperatureTile.kt` ~55–57 (fed by `TemperatureViewModel.notices`, a `SharedFlow<TemperatureNotice>` of `Refreshing` / `Problem`, no replay, collected only while the tile is composed).
- **Stale KDoc (contradiction in the repo):** `TemperatureNotice.Refreshing` says "long press on the tile, or the settings control" and `TemperatureTile`'s KDoc says "so the settings control gets it too", but `refreshNow()` has one caller, the tile; Settings carries only the enable switch (`MapScreenSettingsOverlay.kt` ~1198).
- **Naming.** In code "toast" means the in-app pill (`*_toast` strings, `ui.toast.*` tokens, `trackSavedToast`) while `android.widget.Toast` is the same word.

## 2. Decisions

One point at a time, each with a recommendation; a decision is written here when the user takes it.

| # | Point | State |
|---|---|---|
| D1 | Vocabulary | **decided 2026-11-11** — the agent's call (user: no preference), as written below |
| D2 | One host or two lanes under one arbiter | **decided** — column order by the user 2026-11-11: action rows at the edge |
| D3 | Priority and replacement rules | recommended (agent's call) |
| D4 | Dwell per class | recommended (agent's call; every figure a key) |
| D5 | Visibility under drawers; fate of system Toasts | **decided** — the user 2026-11-11: pills show **above** the covering panel |
| D6 | API: typed notice raised from ViewModels through a flow | recommended (agent's call) |
| D7 | Migration order | **decided** — the user took the agent's order 2026-11-11 |
| D8 | Docs: the "which vehicle when" rule in §5.7 | recommended (agent's call) |
| D9 | Properties: notice family consolidated into `maro.properties` (§6) | **ordered 2026-11-11**; §6.4 sub-points decided by the agent (nothing user-visible) |

### D1 — Vocabulary (decided)

- **Notice** — a transient, read-only message on the map; the host's unit.
- **Pill** — the face a notice wears (`MapBanner` + `MapBannerText`). State-bound status (`trackOpStatus`, the arrival notice) wears it too; whether that is a notice class or a separate thing is D4's.
- **Action row** — the face that carries commands (`SnackRow`). §5.7 already says "action and undo rows" and the route ending's row carries `New` beside Undo, so the name is the command, not the undo; "snack" is retired from prose, and `ActiveSnack` stays a code name until D2 decides its home.
- **Card** — `LoadingOverlay` / `ErrorOverlay`, the family's state-bound full-width faces; named so D2 can scope them.
- **System Toast** — always said with both words, always the `android.widget.Toast` class; bare "toast" is not used in new prose or new identifiers.
- **Legacy keys stay:** `*_toast` strings and `ui.toast.*` tokens are not renamed by this feature; §5.7 states that `toast` in an existing key means the pill. New keys use `notice_*`.

### D2 — One arbiter, two lanes, one column (recommended)

- **One pure arbiter, two lanes.** `NoticeArbiter` (pure Kotlin) owns both lists: the **notice lane** (pills, read-only) and the **action lane** (rows, today's `MapDashboardController` queue moved in verbatim — 3 visible, overflow, `RouteEnd` jumps).
- **Lanes never compete for slots.** A pill can neither pre-empt nor delay a row, so a row's commit timing (§3.1–3.2) cannot change by construction.
- **One host composable, one column** in the band, replacing `MapStatusBannerStack` and `MapSnackbarHost`'s column; **action rows on the band's edge, pills stacked above them** (user, 2026-11-11).
- **Cards stay out.** `LoadingOverlay` / `ErrorOverlay` are state-bound full-width faces with Retry; they keep their own slot.
- Objection: two lanes keep two sets of rules; one list would be simpler. Rejected because a single priority list is exactly what can delay a commit.

### D3 — Priority and replacement (recommended)

- **Classes, highest first:** `EXIT` › `ERROR` › `STATUS` › `RESULT` › `CONFIRM`.
- **Coalesce by key:** a notice whose key is showing or queued replaces it in place and restarts its dwell — today's same-kind behaviour, now by key instead of `.distinct()` on text.
- **`EXIT` pre-empts and never queues:** it takes a slot at once, pushing the lowest-class pill back to the queue (§3.5).
- **`STATUS` is posted and cleared, never timed;** it holds its slot while it stands (`trackOpStatus`, arrival — §3.3–3.4).
- **Full lane:** a new notice queues behind its class (FIFO within a class); a higher class displaces the lowest visible one back to the queue's head.
- **Stale confirmations drop:** a `CONFIRM` that waited longer than its own dwell is discarded; `ERROR` and `RESULT` wait.
- **Max visible pills:** `notice.maxVisible=3`.
- Objection: displacement can make a pill flicker out and back; acceptable at three slots and the rarity of five simultaneous notices.

### D4 — Dwell per class (recommended)

| Class | Dwell | Key |
|---|---|---|
| `CONFIRM` (saved, route started/refused, lock, temperature refreshing) | 2 s | `notice.dwell.shortMs` |
| `RESULT` (import result, speed history) | 6 s | `notice.dwell.readableMs` |
| `ERROR` (temperature problem, startup diagnostics, fine-tile reclaim) | 6 s | `notice.dwell.errorMs` |
| `STATUS` | until cleared | — |
| `EXIT` | the back window | `map.exit.backWindowMs` |
| Action row | 4 s | `notice.row.timeoutMs` |

- **The dwell clock starts when the pill shows** — a queued notice's wait is not its dwell (a stale `CONFIRM` drops, D3).
- Objection: 6 s errors may still be short for a startup diagnostic listing many keys; the key is tunable.

### D5 — Visibility and system Toasts (decided by the user 2026-11-11: above the panel)

- **The notice lane paints above the drawer ladder** — after `OverlayLayer` and below the dialogs and `ConfirmRequestHost` — so a pill shows over an open menu, list or Settings at the band's bottom. **This reverses the 2026-10-23 toast-menu-z-order rule for pills**; the Ui_General `## Implemented` entry and `docs/ui-drawer-guidelines.md` §1 are restated when step 1 lands.
- **The action lane keeps today's layer**, under the ladder: a row's commands act on the map, and the 2026-10-23 rule stands for rows. While the band is uncovered the pills sit above the rows' measured height, so the two layers read as one column (D2).
- **No hold, no pause:** a notice is visible the moment it shows, so §3.9 is met without a covered-band state.
- **The lock pill joins the host.** `LockScrim` is touch-only and draws nothing, and the notice lane already paints above the ladder as `MapLockLayer` does today; §3.8 is met.
- **System Toasts end:** `OverlayLayer`'s speed history and the temperature notices become `RESULT` / `ERROR`; `reportUnreadableColourKeys` (runs before `setContent`) and `reportUnclaimedRouteEngineId` post to the arbiter, which is process-scoped and holds them until the map composes; the fine-tile reclaim keeps its notification and its toast becomes an `ERROR`.
- **The one exception left:** none. A message that needs the app off-screen uses a notification, never a system Toast.

### D6 — API (recommended)

```kotlin
data class Notice(val key: String, val text: NoticeText, val cls: NoticeClass)
sealed interface NoticeText {
    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : NoticeText
    data class Plural(@PluralsRes val id: Int, val count: Int, val args: List<Any> = emptyList()) : NoticeText
    data class Joined(@StringRes val format: Int, val parts: List<NoticeText>) : NoticeText   // the import result
}
enum class NoticeClass { EXIT, ERROR, STATUS, RESULT, CONFIRM }
```

- **Raised through one process-scoped `NoticeCenter`** (`post(notice)`, `clear(key)`), exposing `StateFlow<NoticeBoard>`; ViewModels and `MainActivity` post, the host collects. Its rules are the pure `NoticeArbiter`, a reducer `(board, event, nowMs) → board` with an injected clock, unit-tested without Compose.
- **Text resolves in the host**, so locale changes apply and no `Context` enters a ViewModel for a string.
- **Existing flows adapt, not rewrite:** `TemperatureViewModel.notices` and `RouteViewModel.seatMovedAtEnd` are mapped to posts at one collection site.
- Objection: a process-scoped singleton is global state; accepted because the app has no DI and the startup diagnostics must post before any ViewModel exists.

### D7 — Migration order (decided by the user 2026-11-11: the agent's order)

0. Properties consolidation (§6).
1. `NoticeArbiter` + `NoticeCenter` + unit tests; the host, on its above-the-ladder layer, replaces `MapStatusBannerStack` and carries **the five pills already stacked** (import, track op, saved, route, arrival).
2. The **temperature notices** and the speed-history result.
3. Exit and lock pills.
4. The action lane: `MapDashboardController`'s queue moved into the arbiter — last, as the step that touches commits.
5. `MainActivity`'s three system Toasts.

### D8 — Docs (recommended)

- §5.7 gains the **"which vehicle when"** rule: a **pill** (through `NoticeCenter`) for a transient read-only message; an **action row** when there is a command; `ConfirmDialog` for a decision; the control's own inline state for state its surface owns; a **notification** when the app may be off-screen; **never a system Toast**.
- §5.7 points at the `notice.*` keys and the arbiter's KDoc for the rules instead of restating figures; `docs/ui-drawer-guidelines.md` §1 lists the one host in Layer 0; `docs/ui-lists-guidelines.md` keeps the row dismiss contract.

## 3. Regression guards — behaviour the host must keep

Each line is something today's code does that a unified queue can silently break; the decision named owns it.

1. **Route-end confirmation window (D3).** `RouteEnd` jumps the queue because its 4 s timeout *commits* a deferred route disposal; a notice that pre-empts it or queues it ahead lengthens or hides the window.
2. **Deferred deletes commit on the row's timeout (D2/D3).** The timer starts on composition, the swipe takes the same path; a merged queue must keep both, or a delete commits unseen or never.
3. **Progress stands until cleared (D4).** `trackOpStatus` has no timer; a default dwell would drop the import or export pill mid-operation.
4. **Arrival is a state, not an event (D4/D6).** `routeArrivalNotice` stands while the settled reading holds; turning it into a raised notice loses "still arrived".
5. **Exit pill = double-back window (D3/D4).** It must pre-empt and never queue, and its dwell must be the window's own key, not a second one.
6. **Import result readable (D4).** The 6 s exists for the 2026-10-22 review §4.4 complaint; a uniform short dwell reopens it.
7. **Z-order is a written decision (D5).** Decided 2026-11-11: pills above the ladder, rows under it; the 2026-10-23 rule's two homes (Ui_General `## Implemented`, `docs/ui-drawer-guidelines.md` §1) are restated in step 1, not left contradicting the code.
8. **Lock banner over open drawers (D5).** `LockScrim` draws nothing (touch only), so the band stays visible when locked; but `MapLockLayer` paints above the ladder, so today the lock pill also shows over an open drawer — under the host it holds instead (D5's rule).
9. **Temperature errors stay visible (D5 → D7).** Today's system Toast shows over every drawer; migrated to the band, a fetch failure that lands while a drawer or Settings is open — the switch that starts the first fetch lives there — is silent.
10. **Text dedupe (D3).** `.distinct()` collapses two pills of different kinds whose text happens to match; key-based coalescing replaces it, so equal texts from different keys would both show.

## 4. Constraints (AGENTS.md)

- Every user-facing string in `values/strings.xml` and `values-fr/strings.xml` through `@StringRes`; every notice-family tunable in `maro.properties` read through `AppConfig` (§6).
- Queue and rules are pure Kotlin with unit tests; the host face is stateless Compose; ViewModels raise notices through a flow, no Compose state per kind in `MapScreen`.
- No new dependency; no deploy to the device; no git write unless invoked.

## 5. Migration order

Lives in D7. The user's provisional order was temperature → route/track/import → lock/exit → system Toasts; D7 departs from it (Q-C).

## 6. Properties consolidation (D9, ordered 2026-11-11)

### 6.1 How the files load today

- `AppConfig.init` loads `maro.properties`, then `ui.properties`, then `colors.properties` into **one** `Properties` bag; a later file overwrites an earlier one, so on a duplicate key **colors wins, maro loses** (`AppConfig.kt` ~2064). No key is duplicated across the three files today (checked 2026-11-11).
- `${key}` references resolve against that merged bag, so a reference may cross files at runtime.
- `app/build.gradle.kts` ~41 parses `maro.properties` at configuration time for `BuildConfig` fields only; runtime keys never reach the compiler, so nothing below lands there.
- The notice family's values are spread over three homes: code literals, the `ui.map.*` band geometry sitting in `colors.properties` (non-colour keys there: 32), and the `ui.toast.*` colours in `colors.properties`.

### 6.2 What moves — the notice family's slice

| Value | Today | Target key in `maro.properties` |
|---|---|---|
| Short dwell (route notice, track saved, lock) | `delay(2_000L)` ×3, `MapScreen.kt` | `notice.dwell.shortMs=2000` |
| Readable dwell (import result) | `delay(6_000L)`, `MapScreen.kt` ~1160 | `notice.dwell.readableMs=6000` |
| Action-row timeout | `delay(4000L)`, `SnackRow` ~482 | `notice.row.timeoutMs=4000` |
| Double-back window **and** exit pill dwell | `2_000L` ×2, `MapScreen.kt` ~1993 / ~2007 | `map.exit.backWindowMs=2000` — one key, both readers |
| Visible action rows | `3` ×3, `MapDashboardController` | `notice.row.maxVisible=3` |
| Row swipe threshold | `SNACK_SWIPE_THRESHOLD_DP = 48f`, `MapScreen.kt` ~464 | `notice.row.swipeDp=48` |
| Skin corner · border · shadow | `14.dp` · `2.dp` · `8.dp`, `MapControls.kt` ~97–103 | `notice.skin.cornerDp` · `.borderDp` · `.shadowDp` |
| Pill text · padding · stack gap | `16.sp` · `16/10 dp` · `6.dp`, `MapControls.kt` | `notice.text.sizeSp` · `notice.pill.padH/padVDp` · `notice.stack.gapDp` |
| Band geometry the clearance reads | `ui.map.toggle.square` · `ui.map.toggle.gutter` · `ui.map.overlay.gap` in `colors.properties` | **same key names**, moved to `maro.properties` |
| Right control column | `RIGHT_CONTROL_COLUMN_INSET = 82.dp`, `MapScreen.kt` ~243 | derived from the band keys if it is their sum, else `ui.map.controlColumnDp=82` — read at implementation |

New D3/D4 tunables (priority dwells, max visible pills) land under the same `notice.*` block when those decisions are taken.

### 6.3 Rules of the move

- **Move, never copy:** each key leaves its old file in the same change — a copy left in `colors.properties` would silently win over the moved one.
- **Moved keys keep their names** (`ui.map.*`), so their other readers (toggles, tag stack, info text) do not change; only new keys are named `notice.*`.
- **Code defaults follow the file** (ONE HOME PER FACT): every `AppConfig` default equals the shipped value, pinned by a properties test in the `TitleSortPropertiesTest` shape, plus one test that no key is defined in two of the three files.
- **Tests that read `colors.properties` directly are retargeted**, not left failing: `BannerStartInsetTest` (reads the three band keys and resolves `${}` within that one file — `ui.map.overlay.gap=${ui.map.toggle.gutter}` must move with its target), and `MapPulseDotTest` · `MapTogglePaletteTest` · `RouteCrownMarksTest` checked for the same keys at implementation.
- **Headers and docs follow:** the `AppConfig.init` load comment, the `ui.properties` / `colors.properties` headers, `docs/color-scheme.md` rows for any moved key, and §5.7's skin bullet pointing at the keys instead of restating the numbers.

### 6.4 Sub-points (decided by the agent 2026-11-11 — file placement, nothing the user sees)

- **Colours:** `ui.toast.background` / `ui.toast.text` — they stay in `colors.properties`, the palette's one home behind `docs/color-scheme.md`; moving them splits the palette in two. Objection: the notice family then lives in two files.
- **Scope of the sweep:** this feature moves the notice slice only; the other ~30 numeric non-colour keys in `colors.properties` (most of them `ui.map.*`) and the 44 `ui.*` dimension keys in `ui.properties` are named here, not moved. Objection: it leaves the band's other geometry split from the three keys moved.

## 7. Big risks (assessed 2026-11-11)

Ranked by damage × likelihood; §3 holds the full guard list.

1. **Data committed or lost by timing (high).** The action rows' timeouts *commit* deletes and route disposals (§3.1–3.2). Any queue change that delays, pre-empts or drops a row changes when user data is written. Mitigation: rows keep their own timer semantics, and the pure queue's unit tests cover pre-emption, overflow and promotion before any UI moves.
2. **Pills over panel content (medium, decided).** D5 puts pills above the ladder: a pill can sit over a drawer's bottom rows or Settings' last card for its dwell. Accepted by the user; mitigation is the dwell itself and the stale-`CONFIRM` drop. The layer split (pills above, rows below) must keep one visual column — the host measures the rows.
3. **Silent shadowing during the props move (high likelihood, invisible).** Load order lets a forgotten copy in `colors.properties` override the moved key with no error. Mitigation: §6.3's duplicate-key test lands in step 0, before any key moves.
4. **Regression surface of `MapScreen.kt` (high).** ~5 700 lines, ~20 pill write sites across back handling, route mode and import flows, and no Compose UI tests. Mitigation: one client per step, build and the full unit suite after each, the device pass the user's.
5. **The typed notice cannot carry today's messages (medium).** The import result composes three plurals into one line, `snackbar_saved` takes a name, temperature maps an enum to a string. D6's type must carry plurals and composite text, or the import result stays outside the host.
6. **Exit window split (medium).** If the pill's dwell and the back window become two keys, the pill lies about whether a second back exits. §6.2 gives them one key.
