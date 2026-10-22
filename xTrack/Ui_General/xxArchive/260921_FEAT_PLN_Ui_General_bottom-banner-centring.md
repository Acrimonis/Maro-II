<!-- scope: feature -->
# Bottom banner — the exit toast centres in the space the map leaves free

**Date:** 2026-09-21 · **Branch:** `feature/whatever`, cut from `origin/develop` (`76d6d53`) · **Status:** in design — discussed and settled 2026-09-21, nothing applied · **Feature:** Ui_General owns the back-exit guard and its banner; UI_Map owns the pill components and the right-edge column rule

## 1. Report

The banner the app shows after the first back press — `Press back again to exit` / `Appuyez à nouveau pour quitter` — reads left of centre. It should be centred in the space actually available at the bottom of the map: the space the bottom-left regulated-zone tag column leaves free when a tag is drawn, and the space the right-edge control column always reserves.

Nothing in the app is edited by this plan.

## 2. What the code carries today

| Anchor | What it holds |
|--------|---------------|
| `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:3323-3333` | the pill's `Text`: 16sp Medium, `TextAlign.Start`, 16/10 padding, no `maxLines` and no `overflow` |
| `…MapScreen.kt:3302-3337` | the exit toast itself, a top-layer child of the bottom band: `align(BottomStart)`, `fillMaxWidth()`, `padding(start = 6.dp, end = RIGHT_CONTROL_COLUMN_INSET)`, `contentAlignment = Alignment.Center` |
| `…MapScreen.kt:3193` | the left overlay column, `Modifier.weight(1f).fillMaxHeight()` — already `W − 82dp` wide |
| `…MapScreen.kt:3343-3347` | the right column: the 64dp button family padded `start = 12.dp`, `end = 6.dp` |
| `…MapScreen.kt:208` | `RIGHT_CONTROL_COLUMN_INSET = 82.dp`, whose own comment is `12 gap + 64 button + 6 end` — the same 82 the toast reserves again |
| `…MapScreen.kt:3247-3269` | the band's behind layer: `RegulatedZoneWarningStrip` at `padding(start = TOP_TOGGLE_GUTTER)`, then `RegulatedZoneInfoText` with `weight(1f)` and `ui.map.overlay.gap` |
| `…MapScreen.kt:1192` and `:1251-1259` | the double-back guard: the first press raises the banner, which dismisses itself after 2s |
| `…ui/map/RegulatedZoneComponents.kt:126-135` | the tag stack is a **vertical** `Column` — one `ui.map.toggle.square` per tag, so its width is a square and never a count |
| `…ui/map/MapControls.kt:269` and `:309` | `LockBanner` and `MapStatusBanner`: the same pill, the same padding, drawn from full-width parents |
| `docs/ui-drawer-guidelines.md:40-43` | the paint-only right-edge column rule, the only place this placement is written |

## 3. Why it sits left — from the constants, not from a device

`contentAlignment = Alignment.Center` centres the pill inside the region its box's padding leaves. Its parent is already `W − 82dp`, so `start = 6.dp` with `end = 82.dp` puts that region at `[6, W − 164]`, whose centre is **`W/2 − 79`** — the same 79dp at every width, and about half the map on a phone.

The reserve is the first cause: it is applied once by the column layout and a second time by the toast's own `end` padding. The second cause is `TextAlign.Start`, which the pill's own wrap exposes — the recording string is roughly twice the region's width at 16sp, so it wraps and every line starts at the left edge of an already shifted pill.

`LockBanner` and `MapStatusBanner` carry the same padding, but their parent is a full-size box, so their region is `[6, W − 82]` and their centre `W/2 − 38`. The two faults cancel there by accident, which is why only the exit toast reports the symptom.

The expectation is not new: the UI_Map overlay inventory lists the exit toast as a **Bottom-Center** overlay (`xTrack/UI_Map/260616_FEAT_PLN_UI_Map_overlay-layout-inventory.md:10`), written before the paint-only column rule moved the toast into the left column and left the outside-column reserve behind.

## 4. The place the fix takes — decided 2026-09-21

- **D1 — the region is the band's free space, adaptively.** `[gutter + tagWidth, W − 82]` inside the left column, where `tagWidth` is `TOP_TOGGLE_SQUARE + ui.map.overlay.gap` while a tag is drawn and nothing when the stack is empty. The tag column being a vertical stack one square wide, this is a constant per state rather than a measurement.
- **D2 — the pill stays wrap-content.** A short message is a small pill centred in the region; a long one fills the region and wraps. No stretch, and no `fillMaxWidth` on the Surface.
- **D3 — the text is centred** (`TextAlign.Center`), so a wrapped message reads centred inside the centred pill.
- **D4 — the wrap is uncapped and grows upward.** No `maxLines` and no ellipsis: both messages are fixed strings whose actionable half sits late — `…press back again to stop and exit` — so an ellipsis would cut the instruction. The pill is bottom-anchored, so extra lines grow up over the map.
- **D5 — the agent's, invisible on screen.** A pure helper `bannerStartInset(tagsDrawn: Boolean): Dp = TOP_TOGGLE_GUTTER + (if (tagsDrawn) TOP_TOGGLE_SQUARE + AppConfig.uiMapOverlayGap.dp else 0.dp)`, with JVM tests, following `topToggleSlotOffset` and `lockMirrorStartOffset`. A layout-driven `Row` with a trailing `Spacer` would need the same predicate, so the helper buys the test without the indirection.
- **D6 — one control, every instance, one guideline.** `MapBanner(message, borderColor, modifier)` in `MapControls.kt` is the only banner control; the exit toast's two border states and the two fixed ones become its parameter, and nothing keeps its own copy. The rules the control follows are written in the component guideline and nowhere else, the drawer guideline pointing at that entry instead of restating it.

At defaults on a 411dp-wide screen: with a tag up the region is `[56, W − 82]`, centre `W/2 − 13` (192.5dp); with no tag `[6, W − 82]`, centre `W/2 − 38` (167.5dp); today `W/2 − 79` (126.5dp) either way.

## 5. Prerequisite — one home for the tag answer

`RegulatedZoneWarningStrip` derives its `(category, speedKn)` pairs and returns when the list is empty (`…ui/map/RegulatedZoneComponents.kt:80-123`), and `RegulatedZoneInfoText` repeats that derivation almost line for line (`:222-265`). The toast's inset needs the same answer, so the derivation becomes one pure function returning the deduplicated pairs, read by the strip, the info text and the inset alike — the extraction removes a duplication that already exists rather than adding a coupling.

## 6. Decided 2026-09-21 — one control, every instance, one guideline

The band holds three transient pills and all three move. `LockBanner` (the lock toggle, `MapScreen.kt:2953`) and `MapStatusBanner` (import and export status, `MapScreen.kt:3494` and `:3502`) are drawn from full-width parents today, so they centre at `W/2 − 38` and overlap the tag column at `[6, 50]` whenever a tag is up — the exit toast's fault class, one 82dp reserve short. The user's decision: the rules live in the guideline, and no instance keeps a copy of the control.

| Option | Verdict |
|---|---|
| Exit toast only | declined — the band rule would have three users and two of them would ignore it |
| The three pills on one control | **taken**, and the exit toast is just its first caller |
| One `MapBanner` with `borderColor` | **taken** — the exit toast's recording-red, its plain border and the two fixed borders become one parameter |
| The two legacy cards, `LoadingOverlay` and `ErrorOverlay` | **taken like the rest, no exemption** — one container, one rule set, their own content passed into it |

Five paint sites carry that skin today — the three pills and the two cards — so it is held once, inside the control, and every instance takes it. `MapBanner` is therefore the container rather than the text: it owns the shape, the border colour and the placement, and takes the instance's content as its slot, so the pill passes one `Text` while the progress and error cards pass their own column. The band rule is every instance's too — each one clears the bottom-left tag column and the right control column — while each face keeps its own interior: the 16sp centred line for the banner face, the title, message and Retry for the error face, the progress bar and percentage for the loading face.

## 7. Files to touch

- `app/src/main/java/ykws/android/maro/ui/map/MapControls.kt` — `MapBanner` (the one control), the shared skin, the tag-aware `bannerStartInset`, and the two KDocs that contradict each other on where the banner sits (`:265` says bottom-left, `:304` says centred)
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — the exit toast's box, its `TextAlign` and its border colour becoming `MapBanner`'s parameter, and the lock and status call sites moving onto the same control
- `app/src/main/java/ykws/android/maro/ui/map/CoastlineMapView.kt` — `LoadingOverlay` and `ErrorOverlay` taking the control as their container, their own content inside it, no exemption from the band rule
- `app/src/main/java/ykws/android/maro/ui/map/RegulatedZoneComponents.kt` — the extracted pair-list function the strip and the info text then read
- `app/src/test/java/ykws/android/maro/ui/map/` — the inset helper's tests
- `docs/ui-drawer-guidelines.md` §1 and `docs/ui-component-guidelines.md` §5 — the two doc changes the report asked for, the numbers living in the §5 entry alone

## 8. The two guideline changes

`docs/ui-drawer-guidelines.md:42` reads today that inside-column overlays are already bounded by the column, so they align `BottomStart`/`CenterStart` like the snackbar and add no extra `end` padding. It is right about the padding and wrong on both other counts: the code adds 82dp of `end` padding — the defect — and the pill centres rather than hugging left. It becomes: every inside-column overlay in the bottom band clears the bottom-left zone tag column when one is drawn and the right control column always, adding no `end` reserve of its own.

`docs/ui-component-guidelines.md` gains the family as a §5 entry, and that entry is the rule's only home: the control's API (`MapBanner(borderColor, modifier) { content }`), its skin, the band it occupies and the clearance every instance keeps. It then carries the banner face's own rules — tag-aware adaptive centring, wrap-content width, centred text, uncapped wrap — and the two card faces' — full width, symmetric 6dp, their own border and content. §5 runs 5.1 drawer cards to 5.6 confirm dialog, so the family has never had an entry, which is precisely how three copies and two contradicting KDocs could drift apart. Every instance reads that one entry, and none of the five is exempt from it.

## 9. Verification

- **Build:** `apk-build.bat` → SUCCESS, with no new warning naming the touched files.
- **Tests:** the inset helper's JVM tests for both states of `tagsDrawn`, and the scoped `ui.map` run at its known-red baseline.
- **Device pass, the user's:** one back press with a tag up and once with none, in portrait and landscape, in both locales, plus the recording variant wrapped over two or three lines and not clipped. No before-and-after baseline exists, so the pass shows the after state alone.
- **By construction, not by test:** the layout has no Compose UI harness in this repo (`app/src` carries `main/` and `test/` only), so the centring itself is judged on the device; the arithmetic in §3 is read from the constants, not measured on a screen.

## 10. Risks and objections

- **Against D1 (adaptive):** the pill moves by half the tag slot — 25dp — with whether the boat happens to be in a zone, so its position is not stable over time. It lives 2s and the shift is invisible in isolation; the stable alternative was declined on 2026-09-21.
- **Against D4 (uncapped):** a three-line pill roughly doubles the chrome over the map for those 2s, and the only lever against it is the wording of the recording string — `arrêter et` or `et quitter` would have to go, which is a user-visible string change.
- **Against the one-control decision:** it edits four call sites and two card interiors the report never named, so a regression there is possible; the counter is that all five paint the same skin today, and one definition is the only way it stops drifting.
- **The visible consequence to expect:** the loading and error cards sit at `start = 6.dp` today, so once they obey the band rule they shift by the tag column's width — 50dp — whenever a tag is drawn. That is the decision applied literally; the alternative reading, that the rule binds the pill alone, is the one declined here.
- **Against the estimate:** the recording message's two-or-three-line wrap is derived from 16sp, not measured, so the device pass is what settles it.

## 11. Findings — named, not in scope

- Five paint sites carry the same skin today — `MapScreen.kt:3316` for the exit toast, `MapControls.kt:279` and `:319` for the two banners, and `CoastlineMapView.kt:66` and `:133` for the progress and error cards; D6's control is what turns that into one definition, with no instance exempt.
- `LockBanner`'s KDoc describes it as painting at the bottom-left of the map while it centres, and `MapStatusBanner`'s says centred left of the column — the pair should end up as one sentence wherever the family entry lands.
- `RegulatedZoneWarningStrip` and `RegulatedZoneInfoText` hold near-identical derivations today; §5 removes that duplication as a prerequisite rather than as a wish.

## Outcome

**Shipped 2026-09-21 on `feature/whatever`** through the `#implement` pipeline — a Code hop, an Ask hop that returned no blocking finding and seven should-fix items, and one remediation hop that closed all seven. Nothing was committed.

- **What shipped.** `MapBanner(borderColor, tagsDrawn, reservesControlColumn, modifier) { content }` in `MapControls.kt` is the band's one control: it owns the skin, the border colour and the clearance, and all five instances take it — the exit toast, `LockBanner`, `MapStatusBanner`, and `LoadingOverlay` and `ErrorOverlay`, the last two keeping their interiors and full width. `MapBannerText` is the pill's line — 16sp Medium, `TextAlign.Center`, 16/10 padding, no `maxLines` — and `bannerStartInset(tagsDrawn)` holds the adaptive start inset the toast and the two cards read. The `(category, speedKn)` derivation is now one `regulatedZoneTags`, read by the strip, the info text and the inset, and the tag Boolean is computed once at `MapScreen.kt:1306` and passed down as `bandTagsDrawn`.
- **The defect is gone.** The exit toast no longer reserves `RIGHT_CONTROL_COLUMN_INSET` inside a column that already excludes the control column, so its centre moves from `W/2 − 79` to the band's free-space centre, and its text is centred rather than started.
- **Deviations from this plan as written.** The API gained `tagsDrawn` and `reservesControlColumn`, where §6 and §8 sketched `MapBanner(message, borderColor, modifier)` and `MapBanner(borderColor, modifier) { content }` — the clearance is the control's, so the tag answer had to reach it, and the two placements answered differently on the column reserve: `true` for the two full-size-parent banners, `false` for the toast and the two cards. `regulatedZoneTags` returns a `RegulatedZoneTag` (category, speedKn, source zone) rather than a bare pair, because the info text needs the zone's name and description, and `tagRegulatedZones` was hoisted from `MapContent` to `MapScreen` so the locked-screen banner reads the same filtered set. The rules landed in `docs/ui-component-guidelines.md` §5.7, with §1 of the drawer page pointing at it rather than restating it.
- **Verification.** `apk-build.bat` SUCCESS with no new warning naming the touched files; `gradlew :app:testDebugUnitTest --tests "ykws.android.maro.ui.map.*"` green at 23 classes and 227 tests, including the five `BannerStartInsetTest` cases and the seven `RegulatedZoneTagsTest` cases the remediation hop added. The properties-versus-`AppConfig` reds and the `TrackOutlineTest` reds recorded in `app/build/ui_map_test.txt` did not reproduce — that record holds 119 tests against the run's 227, so it predates this work.
- **Unproven, claimed as such.** The centring itself — `app/src` carries no Compose UI harness — and the recording string's two-to-three-line wrap at 16sp, both waiting on the device pass in two locales and both orientations.
- **Findings left unpatched.** The cards' 6dp end gap is still a literal at `MapScreen.kt:3289` with no property key, so the band's end clearance lives in two places; and the skin's numbers — corner, border, shadow, 16sp — stay private literals in §5.7's table, which credits property homes only to the three inset keys.
