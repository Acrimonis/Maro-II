<!-- scope: feature -->
# Routing tab — a new Appearance block holding the route rendering gates

**Status:** in design · nothing implemented
**Branch:** `feature/more-settings`
**Feature:** Ui_Settings (a Settings-block move; the gates' semantics stay Route's)

## Request

- Move the **Route** sub-section — the two toggles **Speed colours on routes** and **Arrows on routes** — out of the Layers tab's Tracks card into a new block named **Appearance** in the **Routing** tab.
- Normalise that block's rendering to the guidelines.
- Decided with the user: the toggle **names take the comment font** — 13sp `uiTextMuted`, the description's own typography — and the **"Route" sub-heading is dropped**, the Appearance block's title alone heading the two rows.

## Current state

- The two gates sit inside the Layers tab's Tracks card, in the *Track Speed and Direction* expander's `NestedCard`: [`MapScreenSettingsOverlay.kt:484`–`:502`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:484) — `SubSectionHeader(route_trip_title)` ("Route"), an 8dp spacer, the R37/R38 comment, then `ToggleRow(settings_routes_speed_color_label)` → `routeSpeedColor` and `ToggleRow(settings_routes_arrows_label)` → `routeSpeedArrows`, both label-only.
- That group is bounded by two `SectionDivider`s, [`482`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:482) and [`504`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:504); one of them is the separator between *Speed Display* and *Arrow density* and must survive.
- The Routing tab holds one block today, the Route section — the free-water pace and slow-water budget sliders — [`RoutingSettings`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:1463), and no trailing spacer after it.
- [`ToggleRow`](../../app/src/main/java/ykws/android/maro/ui/components/ToggleRow.kt:32) takes `label`, an optional `description`, `checked`, `onCheckedChange`, `leadingIcon`, `checkedColor`; the label is hard-typed at 16sp Medium `uiTextPrimary`, the description at 13sp `uiTextMuted`. It is shared, so any change must be a defaulted parameter.
- Guidelines: §2.1's table and code sketch state the row's typography; §2.3 defines a `CardArea` as one surface holding 1..N rows, sections divided by `SectionDivider()` and **8dp between the rows of one section**; §2.9 fixes the three header roles and states that a heading is white — hierarchy from weight and spacing, never a dimmed colour.
- Strings: `settings_routes_speed_color_label` "Speed colours on routes" / "Couleurs de vitesse sur les itinéraires" ([EN `573`](../../app/src/main/res/values/strings.xml:573)) and `settings_routes_arrows_label` "Arrows on routes" / "Flèches sur les itinéraires" ([EN `574`](../../app/src/main/res/values/strings.xml:574)) — both travel unchanged. There is **no** bare "Appearance" title: every existing one is subject-prefixed ("Coastline Appearance", "Arrow Appearance"), so the block needs its own key.
- This **reverses the placement** the 2026-10-01 session recorded as shipped ([`xTrack/Ui_General/FEAT_DSC_Ui_General.md:119`](../Ui_General/FEAT_DSC_Ui_General.md)) — that record stays as history; nothing in it claims the row must stay in the Layers tab.
- No requirement text names the location: R37/R38 describe the gates' *effect* (`trackColours && routeSpeedColor`, the arrow veto), carried in Route's master book and the `SettingsManager` KDocs, and nothing there names the tab a switch lives in.

## Target

```
Routing tab
  SectionHeader("Route")                       ← the pace and budget sliders (unchanged)
  CardArea { SliderRow ; SectionDivider ; SliderRow }

  SectionHeader("Appearance")                  ← new
  CardArea {
      ToggleRow("Speed colours on routes", name in the comment font)
      Spacer(8dp)                              ← §2.3, within one section
      ToggleRow("Arrows on routes", name in the comment font)
  }
```

## Steps

- **S1 — take the group out of Layers.** In the *Track Speed and Direction* `NestedCard`, delete [`484`–`:504`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:484): the `route_trip_title` `SubSectionHeader`, its 8dp spacer, the two `ToggleRow`s and the trailing `SectionDivider` — leaving [`482`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:482)'s divider as the single separator between *Speed Display* and *Arrow density*. The R37/R38 comment travels with the rows.
- **S2 — give the row a comment-styled name.** Add a spec type to [`ToggleRow`](../../app/src/main/java/ykws/android/maro/ui/components/ToggleRow.kt:32) — `ToggleLabelStyle { ROW, COMMENT }` — and an optional parameter defaulted to `ROW`; `COMMENT` draws the label exactly as the description line is drawn (13sp `AppConfig.uiFontDescSize`, `uiTextMuted`). The default keeps every existing call site byte-identical, and the KDoc names the variant.
- **S3 — build the Appearance block** in [`RoutingSettings`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:1463), after the Route block: a `Spacer(uiSpacingSectionGap)`, `SectionHeader(settings_section_appearance)`, its `uiSpacingHeaderBottom` spacer, then `CardArea` holding the two rows at `ToggleLabelStyle.COMMENT` with an 8dp `uiSpacingGroupedRowGap` between them, the R37/R38 comment above them, and the tab ending on that card.
- **S4 — the one string.** `settings_section_appearance` — EN "Appearance", FR "Apparence" — in both locales, beside the other `settings_section_*` titles, and added to the `expected` list of [`TrackRenderStringsTest`](../../app/src/test/java/ykws/android/maro/ui/map/TrackRenderStringsTest.kt:88) so neither locale can lose it.
- **S5 — write it into the guidelines**, since they are the authority the request names. §2.1's table and sketch gain the `COMMENT` label style and its one use; §2.9 gains the sentence that a comment-styled *row name* is not a heading, so the white-heading rule stands untouched; §2.3's worked examples gain the Routing tab's Appearance block as the example of one section holding two related rows 8dp apart.
- **S6 — build and verify.** `apk-build.bat`, then `gradlew :app:testDebugUnitTest --tests "*TrackRenderStringsTest*"`; the scoped `ui.map` + `config` run and the device pass follow the same pattern as the session before.

## Decisions

- **D1 — the label style's shape, taken.** A `ToggleLabelStyle` enum on the shared `ToggleRow`, defaulted, rather than a boolean flag or a Settings-local wrapper composable: the repo's habit is a spec type at such a seam, and a wrapper would duplicate the switch's colours and spacing. This is an invisible implementation choice, so it is the agent's.
- **D2 — placement, taken.** Appearance sits **after** Route, so the tab reads settings then looks. Moving it above is a two-line change if the order is preferred the other way.
- **D3 — the title, taken.** `settings_section_appearance` with no subject prefix — the tab scopes the block, and the existing subject-prefixed "Appearance" strings are expander labels inside a subject's own card, a different role.
- **D4 — the labels, left alone.** "Speed colours on routes" and "Arrows on routes" keep their wording; the "on routes" is redundant inside the Routing tab but not wrong, and rewording them was not asked for.

## Risks

- `ToggleRow` is shared across the Settings overlay, the menu drawer and the wizard-adjacent surfaces; the parameter is defaulted and no other site passes it, so the blast radius is nil — the build is the proof.
- The divider arithmetic around the removed group is the one place a stray line shows: after S1 the card holds *Speed Display* (chips) and *Arrow density* / *gap range* / *speed range*, separated by exactly one divider.
- A 13sp comment-styled name is smaller than a row's norm; §2.1's variant must be written so a later reader does not "fix" it back to 16sp Medium.

## Verification

- `apk-build.bat` with no new warning on the three touched files, `TrackRenderStringsTest` green including the new key, and the scoped `ui.map` + `config` run.
- Device check, owed: the Routing tab shows Route then Appearance, the two names render in the comment font, both switches still gate the rendered route per R37/R38, and the Layers tab's Tracks card no longer shows them.

## Outcome

Shipped on `feature/more-settings`; `apk-build.bat` SUCCESSFUL and the whole unit suite green, nothing device-validated.

- **S1–S5 as planned.** The two gates left the Layers card, which now runs the chips, one `SectionDivider` and *Arrow density*; the Appearance block sits after Route in the Routing tab; `ToggleLabelStyle { ROW, COMMENT }` landed on the shared `ToggleRow` with `ROW` the default, so no existing call site changed — `labelStyle` occurs in exactly three places; `settings_section_appearance` (EN "Appearance" / FR "Apparence") is in both locales and pinned in the strings guard; and §2.1, §2.3 and §2.9 carry the variant, the block and the sentence that a comment-styled row name is not a heading.
- **One decision taken at S2.** The variant is a spec type on the shared row rather than a Settings-local wrapper composable, and `COMMENT` reproduces the description's typography exactly — 13sp `uiFontDescSize`, `uiTextMuted`, and `FontWeight.Normal`, which is the description's own implicit weight.
- **Verification, stronger than the plan asked.** `apk-build.bat` BUILD SUCCESSFUL in 32s with no new warning naming a touched file, and the **whole** unit suite green rather than the scoped run — `testDebugUnitTest` SUCCESSFUL in 28s, which also closes the scoped run the previous session left owed. The device pass stays owed.
- **Open after the review.** One should-fix, latent and documentation-level: a `COMMENT` row that also passed a `description` would draw two lines in identical typography, and §2.1 does not yet say the two are mutually exclusive. Nothing does that today.
- **Reversal recorded.** This undoes the placement the 2026-10-01 session shipped and [`xTrack/Ui_General/FEAT_DSC_Ui_General.md:119`](../Ui_General/FEAT_DSC_Ui_General.md) records. That record stands as history, and no requirement text names where a switch lives — R37/R38 describe only the gates' effect.
