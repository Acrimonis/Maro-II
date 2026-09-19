# FEAT_PLN — Ui_Settings — colour row pick label

**Status:** implemented 2026-09-19 on `feature/extra-settings` — the swatch alone, and the rule recorded in the guideline. The build is **unverified**, blocked on a resource lock, and a green run is still owed.
**Feature:** Ui_Settings (settings UI). The guideline edit belongs to the same request: the rule it adds is the one the settings rows follow.
**Related docs:** [`docs/ui-component-guidelines.md`](../../docs/ui-component-guidelines.md) §2.4 (inside-expander content, colour rows).

## 1. Request

- No text next to an individual colour picker: the square is enough.
- Record that rule in the guideline rather than leaving it to be re-derived.

## 2. What the code carries today

- [`ColorRow`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:1860) draws a third child beside the 24dp swatch — an accent `Text` reading `R.string.color_picker_pick` — behind a `showPickLabel` parameter that defaults to true.
- Three call sites already suppress it: the active-track colour ([`:326`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:326)) and the two marker-halo colours ([`:514`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:514), [`:520`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:520)). The two **Default colour** rows of the arrow and the line ([`:1014`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:1014), [`:1089`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:1089)) keep it.
- The key has no other reader: `color_picker_pick` appears in the two locale files and in that one `Text`.
- The guideline's colour-row text ([§2.4](../../docs/ui-component-guidelines.md:164)) never mentions a pick text — it fixes where the swatch sits, and reserves labelled `ColorRow` rows for multi-colour groups. The stray label is code that outran the guideline.

## 3. The change

- `ColorRow` loses `showPickLabel`, its `Spacer` and its `Text`: the swatch is the row's only trailing control, which is what §2.4 already describes.
- The swatch takes the row's own label as its `contentDescription`, so removing the text does not leave an unnamed control for TalkBack. No new string: the label is the composable's own parameter.
- The three dead `showPickLabel = false` arguments go with the parameter, and `color_picker_pick` leaves `values/strings.xml` and `values-fr/strings.xml` — one home per fact, and the fact is gone.
- The `ColorRow` KDoc drops "and a pick button" and keeps its swatch-first description.
- Guideline §2.4 gains the rule: **a colour control is the swatch alone, never a text action beside it** — stated once, beside the swatch's own rule, so the next row cannot re-derive it.
- **Decided:** the labelled `ColorRow` stays the shape for both **Default colour** rows. §2.4 gives the `SingleColorSubSection` shape to a section holding exactly one colour control, while each Appearance expander holds three controls with one colour among them; the rejected alternative was switching both rows to title-plus-description, a visible layout change the request did not ask for.

## 4. Verification

- `apk-build.bat` **unverified**: three runs died at `processDebugResources` on a lock over `R.jar` held by a process outside Gradle's daemon, so no compile ever judged the removed parameter, the trimmed call sites or the new `semantics` import. Still to establish: a green build, and the two swatches opening their picker with no text beside them.
- No unit test touches this row, so the scoped run carries no signal for it and is not part of the verification.
