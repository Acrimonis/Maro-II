# Markers — Page dots on both headers, the panel's arrows retired (2026-10-06)

## Purpose

The route acquisition panel and the marker wizard both live in the shared drawer frame, yet each drew
its own page indicator: the wizard showed `WizardStepDots` — 6 dp circles, 4 dp gap, accent up to the
current step — and the AQ showed `PagingControls`, a 32 dp ‹ › pair flanking 5 dp rounded squares with
only the current page filled. This pass leaves **one dot indicator** on both headers and **no arrows**
on the panel.

## Decisions

- **One indicator — `ui/components/PageDots.kt`.** The wizard's look is the reference: 6 dp circles, a
  4 dp gap, drawn in a header's trailing slot. Its **fill is the caller's**: the wizard fills up to and
  including the current step, reading as progress, while the acquisition panel fills **only the current
  page**, because its pages are alternatives rather than stages.
- **The page-switch effect is not shared.** The wizard keeps its own `AnimatedContent` step body and the
  `wizardForward` direction flag; the acquisition panel keeps the lateral pager it already had, now
  named `ui/components/SwipePager.kt` — settled-page to an absolute callback, a jump-only `scrollToPage`,
  never mid-drag. A swipe was added to the wizard and **withdrawn the same session on the user's own
  reading**: it did not help, and the footer's Next/Previous/Finish are the step switch.
- **The AQ keeps no arrows.** Its page changes come from a swipe and a row tap alone; the header keeps
  the status word and the stage readings, then the dots.

## Change table

| File | Change |
|---|---|
| `ui/components/PageDots.kt` (new) | The indicator's one home — the wizard's dot row, its fill rule the caller's |
| `ui/components/SwipePager.kt` (new) | The acquisition panel's lateral pager, lifted whole — the settled/jump sync over caller content |
| `ui/map/WizardDrawer.kt` | Header uses `PageDots` (progress fill); the step body stays `AnimatedContent` over `step`; `WizardStepDots` removed |
| `ui/map/RouteConfirmPanel.kt` | `PagingControls` removed; header dots become `PageDots` with the current-only fill; `RouteTablePager` rebuilt on `SwipePager` |
| `ui/map/MapScreen.kt` | Both `onStepPage` arguments dropped |
| `ui/map/RouteViewModel.kt` | `stepPage(delta)` deleted — unreachable once the arrows go |

## Retired with the arrows

- `RouteConfirmationPanel`'s `onStepPage` parameter and its KDoc line.
- `cd_route_candidate_prev` / `cd_route_candidate_next` in `values/strings.xml` and
  `values-fr/strings.xml`.
- `RouteViewModel.stepPage` and, with it, the pure rule `routeStepIndex` (R54) in `RouteOverlay.kt`,
  which had no other caller. The three unit tests that drove the seat through `stepPage` now drive it
  through the surviving absolute `selectPage`, and the wrap assertion retires with the rule.
- `WizardDrawer`'s `WizardStepDots`, folded into `PageDots`.

## Withdrawn the same session

- The wizard's swipe and the absolute `MarkersViewModel.wizardGoTo(index)` it needed. `wizardForward`
  and the `AnimatedContent` step body are restored, and `WizardDrawer`'s `steps` argument stays only as
  the sequence's own home for the current index and the count.

## Verification and open points

- **Accessibility — decided.** The arrows carried the AQ's only page announcement
  (`cd_route_candidate_*`); the shared `PageDots` is **decorative** and announces nothing, consistent
  with the wizard's dots, and the pages stay reachable by a swipe and a row tap.
- **Suite.** `apk-build.bat` SUCCESSFUL; three reds predate this change and lie outside it — the parked
  `route.avoid.fine.cellRatio` red and two `TrackOutlineTest` shipped-property drifts in
  `maro.properties`, a file this change does not touch. The two retargeted route tests pass.
- **Device pass** over the panel's single-lit dots and the wizard's step animation is owed.
