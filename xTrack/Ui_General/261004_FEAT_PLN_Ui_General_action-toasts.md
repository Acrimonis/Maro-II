<!-- scope: feature -->
# Bottom banner — the action and undo toasts wear the exit banner's skin

**Date:** 2026-10-04 · **Branch:** `feature/ui-toast`, cut from `origin/develop` (`11dd182`) · **Status:** in design — settled 2026-10-04, implementation under way · **Feature:** Ui_General owns the bottom band and the delete-undo snackbar; [`UI_Map`](../UI_Map/FEAT_DSC_UI_Map.md) owns the right-edge column rule the band reads

## 1. Report

The map's bottom band already paints one skin through one control: `MapBanner` serves five instances — the exit toast (`Press back again to exit`), `LockBanner`, `MapStatusBanner`, `LoadingOverlay` and `ErrorOverlay` — and its rules live once in `docs/ui-component-guidelines.md` §5.7. The **action and undo toasts** never joined it: the vertical snack stack still paints its own corner, its own grey fill, its own white text and its own teal action, so the one family the user sees most often is the one dressed apart from the rest.

This plan brings that stack onto the family's skin without changing what it does: the same three-deep stack, the same 4 s timeout, the same horizontal-swipe commit, the same Undo and optional second action. The row keeps its full-width stretch — the user declined the banner's wrap-content centred pill — its message left and its commands right, with the contents anchored to the top of the row rather than centred vertically.

## 2. What the code carries today

| Anchor | What it holds |
|--------|---------------|
| [`SnackRow`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:447) | the row: `RoundedCornerShape(12.dp)`, a hardcoded `0xE62A2A2A` fill, `White` 14 sp text with `maxLines = 2` and ellipsis, `padding(16/10)`, `Arrangement.SpaceBetween` and `Alignment.CenterVertically` |
| [`SnackRow` actions](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:502) | two `TextButton`s in a hardcoded `0xFF80CBC4`, Bold 14 sp — the second action's label plus `action_undo`, which is already a `@StringRes` |
| [`MapSnackbarHost`](../../app/src/main/java/ykws/android/maro/ui/map/MapSnackbarHost.kt:42) | a `Box(fillMaxSize)` holding a `Column` aligned `BottomStart`, padded `bottom = bandHeight`, `start = 12.dp`, `end = RIGHT_CONTROL_COLUMN_INSET`, rows `spacedBy(8.dp)` |
| [`MapSnackbarHost` messages](../../app/src/main/java/ykws/android/maro/ui/map/MapSnackbarHost.kt:58) | three hardcoded English literals — `Track '…' deleted`, `Marker '…' deleted`, `Marker "…" created` — beside two `stringResource` route-discard lines |
| [`MapDashboardController`](../../app/src/main/java/ykws/android/maro/ui/map/MapDashboardController.kt:25) | the three visible slots, the overflow queue, and the route discuss's jump to the front |
| [`ActiveSnack`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:410) | the four sub-types the stack renders |
| [`MapBanner`](../../app/src/main/java/ykws/android/maro/ui/map/MapControls.kt:313) | the family's one control: `BANNER_CORNER`, a 2 dp border in the caller's colour over `uiCardBackground`, `buttonActionBgColor` as the fill, an 8 dp shadow, and the band clearance |
| [`MapBannerText`](../../app/src/main/java/ykws/android/maro/ui/map/MapControls.kt:348) | the pillow line: 16 sp Medium in `uiToastText`, `TextAlign.Center`, 16/10 padding, no `maxLines` |
| [`BANNER_CORNER`](../../app/src/main/java/ykws/android/maro/ui/map/MapControls.kt:66) | `private` — 14 dp corner, 2 dp border and 8 dp shadow, reachable by no other file today |
| [`MapSnackbarHost` call](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:3661) | the one call site, handed `activeSnacks`, `dashboardBandHeight`, `landscapeDashboardWidth`, `onSnackUndo`, `onSnackTimeout`, `onSecondAction` |
| [`RIGHT_CONTROL_COLUMN_INSET`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:232) | `82.dp`, the same reserve both the host and the control read |
| [`§5.7`](../../docs/ui-component-guidelines.md:841) | the family's only rule home: the contract, the pill face, the two card faces and the numbers table |
| [`ui-lists-guidelines.md`](../../docs/ui-lists-guidelines.md:252) | the row's dismiss contract — 4 s timeout and a horizontal swipe commit, Undo reverses, one optional second action |
| [`ui-drawer-guidelines.md`](../../docs/ui-drawer-guidelines.md:39) | the paint-only right-edge column rule, whose live users include the undo snackbar stack |

## 3. The decisions — settled 2026-10-04

- **D1 — the row keeps its full-width stretch.** No wrap-content centred pill: the row auto-fills the band's free space, exactly as it does today, so it becomes the family's **third full-width face** beside `LoadingOverlay` and `ErrorOverlay`.
- **D2 — the row's own alignment.** The message stays left (`weight(1f)`), the commands stay right (`Arrangement.SpaceBetween`), and the contents anchor to the **top** of the row — `Alignment.Top` in place of `Alignment.CenterVertically`.
- **D3 — the skin is the family's, taken once.** 14 dp corner, a 2 dp border in the caller's colour over `uiCardBackground`, `buttonActionBgColor` as the fill, an 8 dp shadow, and `uiToastText` for the text. The row carries no copy of any of those values.
- **D4 — the action colour is the accent token.** `0xFF80CBC4` becomes the `AppConfig.uiAccent` the list snackbar already reads for Undo, so one token states what a toaster's action looks like wherever it appears.
- **D5 — the clearance is the band's.** The stack starts at `bannerStartInset(tagsDrawn)` with `reservesControlColumn = true`, its parent being the whole map area, in place of today's fixed `start = 12.dp`.
- **D6 — the three messages become resources.** `@StringRes` keys in `values/strings.xml` and `values-fr/strings.xml`, read through `stringResource` in the host, closing the §1 breach on the same lines.
- **D7 — the page's line stays written once.** `MapBannerText` gains whatever it needs — a `textAlign` and its horizontal padding — so "16 sp Medium in `uiToastText`" is defined once and the row's left-aligned line is not a second copy of it. The exact parameterisation is the Code hop's call.
- **D8 — the stack itself does not move.** Three visible slots, the FIFO overflow queue, the route discard's jump to the front, the 48 dp swipe threshold and the 4 s timeout all stand as they are.

## 4. Files to touch

- [`app/src/main/java/ykws/android/maro/ui/map/MapControls.kt`](../../app/src/main/java/ykws/android/maro/ui/map/MapControls.kt) — the `BANNER_*` values become reachable by the row, or the row reaches the skin through `MapBanner` itself; `MapBannerText` gains its alignment
- [`app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt) — `SnackRow`'s container, its alignment and its two action buttons; the tag Boolean threaded to the host
- [`app/src/main/java/ykws/android/maro/ui/map/MapSnackbarHost.kt`](../../app/src/main/java/ykws/android/maro/ui/map/MapSnackbarHost.kt) — the clearance, the `tagsDrawn` parameter and the three messages
- [`app/src/main/res/values/strings.xml`](../../app/src/main/res/values/strings.xml) · [`values-fr/strings.xml`](../../app/src/main/res/values-fr/strings.xml) — the three new keys with the French written for each
- [`docs/ui-component-guidelines.md`](../../docs/ui-component-guidelines.md) — §5.7 gains the row as the family's sixth instance and its third full-width face; the skin's numbers stay in its table alone
- [`docs/ui-lists-guidelines.md`](../../docs/ui-lists-guidelines.md) — the dismiss contract stays and points at §5.7 for the skin
- [`xTrack/Ui_General/FEAT_DSC_Ui_General.md`](../Ui_General/FEAT_DSC_Ui_General.md) — the `### bottom banner` section and the `## Docs` pointer on completion

## 5. Verification

- **Build:** `apk-build.bat` → SUCCESS, with no new warning naming the touched files.
- **Tests:** `gradlew :app:testDebugUnitTest --tests "ykws.android.maro.ui.map.*"` at its known baseline, with `BannerStartInsetTest` extended if the inset's inputs move.
- **Device pass, the user's:** a delete with a tag up and once with none, in both orientations and both locales; a wrapped message beside its two actions; three rows standing at once and the overflow promoting into a freed slot.
- **By construction, not by test:** `app/src` carries no Compose UI harness, so the top-anchoring and the wrap are judged on the device; the geometry here is read from the constants, not measured on a screen.

## 6. Risks and objections

- **Against D2 (top-anchored):** the row's message and its commands have different heights, and centring is what hides the mismatch today; anchoring to the top makes a two-line message and a one-line action set visibly unequal — which is the user's own word, taken deliberately.
- **Against D5 (band clearance):** the stack moves by the tag column's width — 50 dp at defaults — whenever a tag is drawn, a change the skin alone would not have caused.
- **Against the family join:** §5.7's register is one pill at a time, so a three-deep stack of bordered, shadowed faces is a divergence from the family's own habit; the stack is deliberately kept and the divergence recorded rather than dissolved.
- **The cap against the family norm:** the pill's wrap is uncapped so an instruction is never cut, while the row keeps `maxLines = 2` with an ellipsis to protect the row's height beside its actions — a long marker name is ellipsised in the row where the pill would have wrapped.
- **Weight over the map:** three simultaneous rows, each with a 2 dp border and an 8 dp shadow, is more chrome than three flat bars; only the device pass can judge it.

## 7. Findings — named, not in scope

- The three hardcoded messages at [`MapSnackbarHost.kt:58`](../../app/src/main/java/ykws/android/maro/ui/map/MapSnackbarHost.kt:58) are a standing §1 breach that this pass repairs on the lines it touches; they were never part of the requested look.
- The list side's [`SnackbarSlot`](../../app/src/main/java/ykws/android/maro/ui/components/ListOverlayScaffold.kt:526) is a different family — an inline list slot on `uiCardBackground` at 7.65 % alpha with an accent Undo — and is not touched here; if the two are ever to read as one, that is its own plan.
- [`MapScreen.kt:477`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:477) and the route-discard messages already read from resources, so the extracted keys align with their neighbours rather than introducing a new idiom.
- Nothing here is device-validated; every claim above rests on a file read, and the top-anchoring and the wrap wait on the user's device pass.

## 8. Revision 2 — the gap and the command face (2026-10-04, after the user's device pass)

The shipped pass left two things visible on the device; both are decided, and neither reopens §3's shape.

- **R1 — the gap above the dashboard.** [`MapSnackbarHost()`](../../app/src/main/java/ykws/android/maro/ui/map/MapSnackbarHost.kt:32) ends its column at the live band height alone, so the lowest row sits exactly on the dashboard's top edge. It gains the band's own gutter — `bottom = bandHeight + AppConfig.uiMapToggleGutter.dp`, `ui.map.toggle.gutter`, 6 dp — the amount [`bannerStartInset()`](../../app/src/main/java/ykws/android/maro/ui/map/MapControls.kt:63) already reads for the band's start.
- **R2 — the commands become the family's own button.** [`SnackAction()`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:526) stops rendering a bare `TextButton` in `ui.accent` text — roughly 1.5:1 on the family's translucent fill, where the teal it replaced was about 7.8:1 — and renders [`ConfirmActionButton()`](../../app/src/main/java/ykws/android/maro/ui/components/ConfirmDialog.kt:328) in the `SECONDARY` role: the primary's white bold label on `ui.action.neutral.background` under a 2 dp full-opacity `ui.accent` rim. §5.9 tier 1 gains the map row as a fourth host.
- **R3 — the size lives inside the one control.** `ConfirmActionButton(action, modifier, compact: Boolean = false)`: compact drops the `fillMaxWidth()` stretch so the control wraps its label, and takes a smaller corner, tighter content padding, a smaller label and an explicit height; the dialog's other hosts are untouched, so §5.6's "the app's only rendering of a `ConfirmAction`" holds rather than a second button appearing beside it.
- **R4 — the text keeps the space.** The row's message stays `Modifier.weight(1f)`; the two commands sit wrap-content at the row's end.
- **R5 — this plan's own numbers.** Compact corner 8 dp against the dialog's 12 dp · compact height 28 dp against 40 dp · compact label 12 sp bold against 14 sp · the 2 dp `ui.accent` rim and the `ui.action.neutral.background` fill at both sizes.
- **R6 — the divergence, recorded rather than dissolved.** The list side's `SnackbarSlot` keeps accent **text** for its Undo, so the map row's Undo is now a different species from the list's — deliberate, because the row rides a translucent pill over a live map where accent text does not read.

**Files.** `ConfirmDialog.kt` · `MapScreen.kt` · `MapSnackbarHost.kt` · `docs/ui-component-guidelines.md` §5.6 and §5.7 · `docs/ui-lists-guidelines.md` for R6.

**Owed to the device.** The 6 dp gap; the compact button's real height, M3 buttons carrying their own minimum; and whether two 2 dp rims in one row read as heavy — the route-discard row being the one that carries both commands.

**Named, not in scope.** The two pills' literal `6.dp` ([`MapControls.kt:386`](../../app/src/main/java/ykws/android/maro/ui/map/MapControls.kt:386) and `:412`) still bypasses `AppConfig.uiMapToggleGutter`, which the number table claims as its home.

## Outcome

**Shipped 2026-10-04 on `feature/ui-toast`** through the `#implement` pipeline — a Code hop, an Ask hop that returned no blocking finding with one Medium and one Low, and one remediation hop that folded both. Nothing was committed and nothing was deployed.

- **What shipped.** [`SnackRow`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:448) renders through [`MapBanner`](../../app/src/main/java/ykws/android/maro/ui/map/MapControls.kt:313) as the family's third full-width face: the skin — 14 dp corner, a 2 dp border in `ui.dashboard.background`, `ui.card.background` over `ui.button.background`, an 8 dp shadow — and the band's clearance are the control's, and the row's commands moved from a hardcoded teal to `AppConfig.uiAccent`. `bannerLineStyle()` is the family's one text definition, 16 sp Medium in `ui.toast.text`, read by the pill's line and by the row's message, which stays left with `maxLines = 2` while its commands sit right and everything anchors to the row's top.
- **The clearance is the band's.** [`MapSnackbarHost`](../../app/src/main/java/ykws/android/maro/ui/map/MapSnackbarHost.kt:38) no longer pads its column; `MapBanner` owns `bannerStartInset(tagsDrawn)` and the right-column reserve, with the tag Boolean threaded from `MapScreen`.
- **The three messages became resources** — `snack_track_deleted`, `snack_marker_deleted` and `snack_marker_created` in both locales, closing the §1 breach, with the French written as `Trace` and `Repère`, the app's own words.
- **Deviations from this plan as written.** §3's D7 landed as the shared `bannerLineStyle()` rather than as parameters on `MapBannerText`, which §3 left to the Code hop; §4's option of lifting the `BANNER_*` values was not taken, the row reaching the skin through the control itself, so no constant left its file and no face keeps a copy; and the three messages now use the app's existing `"%s"` quoting in place of the previous single quotes.
- **Verification.** `apk-build.bat` BUILD SUCCESSFUL with no new warning naming the touched files; `gradlew :app:testDebugUnitTest --tests "ykws.android.maro.ui.map.*"` green. The review returned one Medium — the French twins first read `Piste` and `Marqueur`, words that appear nowhere in `values-fr` — and one Low, a §5.7 pointer naming a section that does not exist; both were folded.
- **Unproven, claimed as such.** `app/src` carries no Compose UI harness, so the row's top-anchoring, its wrap and three rows' weight over the map are device judgements, as is the accent label's contrast — `AppConfig.uiAccent` over the family's translucent fill computes to roughly 1.5:1 where the teal it replaced was about 7.8:1, which is the order's own choice and the device pass's to judge.
- **Findings left unpatched.** [`MapScreen.kt:3657`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:3657)'s `savedMarker?.name ?: "Unknown"` now feeds `snack_marker_created`, an English fallback in the French locale and a §1 breach on this same surface, outside the plan's named scope.
- **Revision 2 shipped the same day.** §8 followed this pass: the stack clears the dashboard by `AppConfig.uiMapToggleGutter`, and the row's commands wear [`ConfirmActionButton`](../../app/src/main/java/ykws/android/maro/ui/components/ConfirmDialog.kt:337)'s compact `SECONDARY` face; `apk-build.bat` and the scoped `ui.map` run are green again, with the compact control's measured height left to the device.
