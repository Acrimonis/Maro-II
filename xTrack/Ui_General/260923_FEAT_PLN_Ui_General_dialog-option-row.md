# 260923 · Ui_General — one option row, one gap between the box and its label

**Status:** shipped 2026-09-23, uncommitted — one Code hop, `apk-build.bat` SUCCESS and the `ui.map` suite green at 28 classes with no failure. The user's own words: the checkboxes in dialogs should have a normalized gap between the box and the text. The two consequences this plan did not foresee, and the §1 correction behind them, are in §4.

## 1. What exists

Four checkbox-and-label rows, each rebuilding the same `Row` by hand, and they do not agree:

| Row | Site | Box | Gap | Label |
|---|---|---|---|---|
| Route exit — save the whole session's routes | [`MapScreen.kt:3257`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:3257) | `uiAccent` tint, `enabled` gated on there being two routes | none stated | 14 sp `uiTextPrimary`, `weight(1f)` |
| Resume — back the target up first | [`MapScreen.kt:3314`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:3314) | `uiAccent` tint | none stated | 14 sp `uiTextPrimary`, `weight(1f)` |
| GPX import — keep the originals | [`TrackHistoryOverlay.kt:457`](../../app/src/main/java/ykws/android/maro/ui/map/TrackHistoryOverlay.kt:457) | `uiAccent` tint | none stated | 14 sp `uiTextPrimary`, `weight(1f)` |
| Route panel — pin the track | [`RouteOverlay.kt:394`](../../app/src/main/java/ykws/android/maro/ui/map/RouteOverlay.kt:394) | theme default | `Spacer(4.dp)` | 15 sp `uiTextPrimary` |

Three of the four leave the gap to the checkbox's own target inset; the fourth adds 4 dp of its own, which is exactly the distance the same row shows twice with two values. The three dialog rows carry the same two semantics — `toggleable(role = Role.Checkbox)` on the row and `semantics(mergeDescendants = true)` — and the same `onCheckedChange = null` on the box, so they are one shape wearing three copies. **The pin is the odd one out in more than its gap:** it writes neither, so only the box itself was tappable and nothing announced the row — a correction to this section's first reading, found when the component was built.

## 2. The solution

**One component, [`ui/components/OptionRow.kt`](../../app/src/main/java/ykws/android/maro/ui/components/), the app's only rendering of a checkbox and its label.**

```kotlin
@Composable
internal fun OptionRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    labelFontSize: TextUnit = 14.sp
)
```

- The row carries `toggleable(value = checked, role = Role.Checkbox, enabled = enabled, onValueChange = onCheckedChange)` and `semantics(mergeDescendants = true)`, with the box at `onCheckedChange = null` — the same two lines the four sites write today.
- The box is tinted `uiAccent` in every instance, which is what three of the four already do.
- The label is `uiTextPrimary` at `Modifier.weight(1f)`, its size the caller's: 14 sp in a dialog, 15 sp on the panel (the one visible difference the two families keep, and it is the panel's own type, not the row's).
- **The row states no gap of its own: the checkbox's target inset is the gap.** That is what makes the distance the same everywhere without inventing a number, and it is the sentence the component's KDoc carries.

## 3. Rejected

**Stating an 8 dp gap.** A `Checkbox` draws its box inside a 48 dp target, so any stated gap adds to the inset the user sees; stating one would mean shrinking the box to kill that inset first, which changes the checkbox's own look — a bigger change than the normalization asked for, and one nobody asked for.

## 4. What the panel's pin gains and loses

- It **loses** its `Spacer(4.dp)`, its label's own `padding(vertical = 4.dp)` (the component now owns the label's modifier), and — where the theme's default checkbox colour differs from `uiAccent` — its box joins the accent tint the other three carry.
- It **gains** what the three dialogs already had: the whole row as the tap target, and the row announced as one merged check box, where before only the box itself was clickable and nothing named the row.
- All of it is the normalization itself and is named here so none lands as a surprise; §1's first reading of the pin was wrong on its semantics, and this is that correction.

## 5. Where the rule lives

One sentence in [`ui-component-guidelines.md` §5.6](../../docs/ui-component-guidelines.md:522), the dialog's options slot — the home of any option row a dialog hosts — and §5.8's pin row points at it rather than restating it.

## 6. Files

- `app/src/main/java/ykws/android/maro/ui/components/OptionRow.kt` — new, the row's one home.
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — the two dialog rows read it; their strings, state and side effects stay where they are.
- `app/src/main/java/ykws/android/maro/ui/map/TrackHistoryOverlay.kt` — the GPX import row reads it.
- `app/src/main/java/ykws/android/maro/ui/map/RouteOverlay.kt` — `RoutePinOption` reads it.
- `docs/ui-component-guidelines.md` — §5.6's sentence and §5.8's pointer.

## 7. Verification

`apk-build.bat` SUCCESS with no new warning from the touched files, and `gradlew.bat testDebugUnitTest --tests "ykws.android.maro.ui.map.*"` green; the four rows' spacing is a device judgement, the shapes being one by construction.
