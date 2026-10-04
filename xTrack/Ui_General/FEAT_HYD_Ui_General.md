# Context Hydration — Ui_General — 2026-10-04

**Last Bake:** 2026-10-04 14:02 UTC — written by `#bake`; absence means never baked

**Directive trace:** All five covered action classes were met this session — no dependency was added, no machine-shaped data file was opened, every write followed an order (the branch on `#new`, the implementation on `#impl`, the revision on the user's own directive to make the command face that control, the bake on `#bake`), the device was never touched (builds only; the device pass that drove Revision 2 was the user's own), and every claim written about the code rests on a file read in the session. The gaps are named, not hidden: the toasts' look, the compact control's measured height and the 6 dp gap are device judgements, and the Focus History entry standing above this one was left untouched rather than rewritten to this session's work.

## State

One branch, `feature/ui-toast`, cut from `origin/develop` (`11dd182`) on `#new`, carrying no upstream yet — the first `#push` writes its own name.

**The map's action and undo toasts joined the bottom banner family.** [`SnackRow`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:448) renders through [`MapBanner`](../../app/src/main/java/ykws/android/maro/ui/map/MapControls.kt:314) as the family's third **full-width face**, so its 14 dp corner, 2 dp border, fill and 8 dp shadow are the one control's own; [`MapSnackbarHost`](../../app/src/main/java/ykws/android/maro/ui/map/MapSnackbarHost.kt:32) no longer pads its column, `MapBanner` owning `bannerStartInset(tagsDrawn)` and the right-column reserve, with the tag Boolean threaded from `MapScreen`. `bannerLineStyle()` is the family's one text definition, read by the pill's line and by the row's message, which stays left with `maxLines = 2` while its commands sit right and everything anchors to the row's top.

**The three hardcoded English messages became resources** — `snack_track_deleted`, `snack_marker_deleted` and `snack_marker_created` in both locales, the French reading `Trace` and `Repère`, the app's own words — closing the §1 breach on the lines the pass touched. The Ask hop returned no blocker and its one Medium (the French twins first read `Piste` and `Marqueur`, words that appear nowhere in `values-fr`) and one Low (a §5.7 pointer naming a section that does not exist) were folded.

**The device pass drove Revision 2.** The stack now clears the dashboard by the band's own gutter — `AppConfig.uiMapToggleGutter`, 6 dp, added with the landscape branch preserved so the band offset is not doubled — and the row's commands left `ui.accent` text, measured at roughly 1.5:1 on the family's translucent fill, for [`ConfirmActionButton`](../../app/src/main/java/ykws/android/maro/ui/components/ConfirmDialog.kt:337)'s new **compact `SECONDARY`** face: white bold on the accent at 50 % under the 2 dp full-opacity rim, 8 dp corner, 28 dp height, 12 sp label. The compact size lives inside that one control, so §5.6's "the app's only rendering of a `ConfirmAction`" holds and no second button appeared.

**Rules moved once.** [`docs/ui-component-guidelines.md`](../../docs/ui-component-guidelines.md:817) §5.6 holds the compact size with its numbers, §5.7 carries six instances and three full-width faces, §5.9 tier 1's host list gained the map row; [`docs/ui-lists-guidelines.md`](../../docs/ui-lists-guidelines.md:252) keeps the dismiss contract, points at §5.7 for the skin, and records that the list's own Undo stays accent text — the deliberate divergence the map row now runs against.

`apk-build.bat` BUILD SUCCESSFUL on every pass with no new warning naming the touched files, and `gradlew :app:testDebugUnitTest --tests "ykws.android.maro.ui.map.*"` green; nothing committed before this bake.

## Target Files

- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — `SnackRow`'s container, its top-anchored contents, `SnackAction` on the compact control
- `app/src/main/java/ykws/android/maro/ui/map/MapSnackbarHost.kt` — the band gutter, the `tagsDrawn` parameter, the three extracted messages
- `app/src/main/java/ykws/android/maro/ui/map/MapControls.kt` — `bannerLineStyle`, and `MapBannerText`'s refactor onto it
- `app/src/main/java/ykws/android/maro/ui/components/ConfirmDialog.kt` — `ConfirmActionButton`'s `compact` size
- `app/src/main/res/values/strings.xml` · `values-fr/strings.xml` — the three new keys
- `docs/ui-component-guidelines.md` · `docs/ui-lists-guidelines.md` — the two rule homes
- `xTrack/Ui_General/261004_FEAT_PLN_Ui_General_action-toasts.md` — the plan, its Outcome and its §8 Revision 2

## Next Step

The device pass over the revision is owed: whether the 6 dp reads as a gap above the dashboard, whether the compact control's **measured** height is the painted 28 dp or Material3's interactive minimum, and whether two 2 dp rims in the route-discard row read as heavy. Nothing here is device-validated.
