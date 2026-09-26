# Context Hydration — Markers — 2026-09-26

**Last Bake:** 2026-09-26 09:50 UTC — written by `#bake`; absence means never baked

**Directive trace:** No dependency was added, no machine-shaped data file was opened, no work started without an order, and the device was untouched — the user's orders each named their action (the two defects, P7a and P8, the select-all, then the bake, commit and push); every claim written about the code carried a read behind it, and the one gap named in the session's own review still stands: the earlier `#focus` ran on a line typed without the `#` prefix.

## State

One session, 2026-09-26, on `feature/dash-n-wiz` — the creation wizard re-shelled onto the drawer the
selected-item dashboards use, then corrected by the device pass's first look.

**The shell.** [`WizardDrawer.kt`](../../app/src/main/java/ykws/android/maro/ui/map/WizardDrawer.kt) is a
`DrawerScaffold`: the header carries the mode's title — `wizard_title_create` with the new
`wizard_title_edit` ("Edit Marker" / "Modifier le repère") chosen from `MarkerDrawerState.Editing` — and
the dot progress in its trailing `headerActions` slot; the footer carries Previous, Next and Finish as
the shared `ConfirmActionButton`s over `ConfirmAction`, the accent on the one enabled forward action and
the rest SECONDARY. `WizardTopBar.kt` and `WizardButtonRow.kt` are deleted. `OverlayLayer.kt`'s wizard
slot no longer forces a portrait height, and the panel wraps its card floored at
`portraitDashboardHeight`, taking `statusBarsInset` alone in landscape.

**The frame's weighting (P7).** [`DrawerScaffold.kt`](../../app/src/main/java/ykws/android/maro/ui/components/DrawerScaffold.kt)'s
wrap branch now honours `bottomAnchoredContent`: a slack of `wrapContentMinHeight` minus header, body and
footer becomes a spacer above the body, so the card and the footer sit at the panel's bottom with the gap
between the header and the card. **Not gated to the wizard** — the marker detail card, the Where-Am-I
card and the track drawer pass the same flag and now bottom-pack their content too; whether that is kept
on all four panels or gated back is still the user's call.

**The steps.** Every step is a `CardArea` stack with its `fillMaxSize` gone; `TypeSelectStep` is one card
whose two sections — the type segments and the icon row — are divided by `SectionDivider()`. The
segments stay hand-drawn because the shared `SegmentedRow` has no icon slot (§2.7's preference unmet,
documented in the step's KDoc).

**The keyboard and the field (P7a, P8).** The wizard sets no soft-input mode and lifts nothing by hand —
`ADJUST_NOTHING`, its `isTextStep` flag, the `findActivity` helper and the portrait `keyboardOffsetDp`
are deleted — so it takes the platform's pan, which `MainActivity` already declares and the track card's
fields already rely on. `TextInputStep` renders the app's inline transparent `TextField` (see
`ui-component-guidelines.md` §2.13), opens with its text selected on focus, and keeps a label line above
the field plus a muted placeholder.

**Shared pieces.** `SliderRow`, `NestedCard` and `Expander` left `MapScreenSettingsOverlay.kt` for
`ui/components`; `SliderRow` gained an optional description and the two end labels. The guidelines were
amended and then consolidated: §2.0's Tight row, §2.2's slider clause, the new §2.13 for the field,
§5.6's dead-button rule generalised to the family, and the drawer page's §6 and §12.

**Build.** `apk-build.bat` SUCCESSFUL with `app-debug.apk` produced, and
`gradlew :app:testDebugUnitTest --tests "*Marker*" --tests "*RoutingCost*"` green, run after each hop.

Carried forward from the 2026-09-18 bake: the focus-zoom framing (`MarkerFocus`, `marker.focus.*`) and the
Where-Am-I tap zone, flash and card-close rule, whose device pass was closed by decision as the user's.

## Target Files

- `app/src/main/java/ykws/android/maro/ui/map/WizardDrawer.kt` — the shell read, the steps dispatched and the footer's actions
- `app/src/main/java/ykws/android/maro/ui/components/DrawerScaffold.kt` — the wrap branch's slack spacer, and the header every drawer shares
- `app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt` — the wizard's two slots, and the scrim's `imeHeightDp`
- `app/src/main/java/ykws/android/maro/ui/markers/wizard/steps/` — `TextInputStep.kt` (the inline field, select-all on focus), `TypeSelectStep.kt` (one card, two sections), `SliderStep.kt`, `PositionStep.kt`
- `app/src/main/java/ykws/android/maro/ui/components/SliderRow.kt`, `NestedCard.kt`, `Expander.kt` — promoted from the settings overlay
- `app/src/main/assets/maro.properties` and `app/src/main/res/values*/strings.xml` — unchanged by this session beyond `wizard_title_edit`
- `docs/ui-component-guidelines.md`, `docs/ui-drawer-guidelines.md` — the consolidated clauses

## Next Step

The device pass over the re-shelled wizard in both orientations: the card and the buttons sitting at the
frame's bottom, the panel panning with the keyboard rather than being lifted, the fields opening
selected, and the map's travel while a field is edited. Then the user's call on the frame fix's reach —
kept on the marker detail, Where-Am-I and track panels, or gated back to the wizard — and the small one
the review named, `TextInputStep`'s now-dead `isLandscape` parameter and its two call sites.
