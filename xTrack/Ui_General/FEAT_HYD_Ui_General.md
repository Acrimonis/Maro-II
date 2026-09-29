# Context Hydration — Ui_General — 2026-09-29

**Last Bake:** 2026-09-29 17:22 UTC — written by `#bake`; absence means never baked

**Directive trace:** All five covered action classes were met and none stopped this session — no dependency was added, no machine-shaped data file was opened, every write followed an order (the branch on `#new`, each pass on `#impl`, the commit on `#commit`, this bake on `#bake`), the device was never touched (builds only), and every claim written about the code rests on a file read in the session. The gaps left open are named: the wheel guard's read-back timing is reasoned rather than measured, and the three passes' behaviour on a device is owed.

## State

One branch, `feature/dropdown-sel`, cut from `origin/develop` on the user's `#new`: the dropdown wheel's selection was fixed, then two UI passes followed it — one shared title order for the lists and the route ends' selector, and a filter popup that no longer closes on a row tap.

**The wheel lands the entry the box shows.** [`DropdownWheel`](../../app/src/main/java/ykws/android/maro/ui/components/DropdownWheel.kt) no longer hands a *relative* quantity to `scrollToItem`'s absolute offset — the form that put the band `(slots − 1)/2` entries past the box, clamped onto the last entry. It scrolls **by** the entry's own distance on the slot grid from the popup's rest frame ([`wheelTargetScrollPx()`](../../app/src/main/java/ykws/android/maro/ui/components/WheelPolicy.kt)), then reads the row the band names from the first laid-out frame and closes the gap once ([`wheelCorrectionSlots()`](../../app/src/main/java/ykws/android/maro/ui/components/WheelPolicy.kt)), and [`DropdownRow`](../../app/src/main/java/ykws/android/maro/ui/components/DropdownRow.kt) resolves the option index **once** for both the box's word and the wheel's entry. The Ask hop returned **revise**: one High left standing — a stale frame read would double the landing — plus one Medium and four Lows, in the plan's §9.

**One title order, two readers.** The route ends' selector takes the fixed entries first in their declared order and the flagged ones by title, through the pure [`routeEndEntries()`](../../app/src/main/java/ykws/android/maro/data/route/RouteEndSelection.kt) beside `MarkerRouteFlags`; the lists' `TITLE` field reads the same comparator, [`titleOrder`](../../app/src/main/java/ykws/android/maro/data/model/ListSortOrder.kt), whose key [`titleSortKey()`](../../app/src/main/java/ykws/android/maro/data/model/ListSortOrder.kt) drops **one** configured word under the user's own condition — the title starts with it character for character and whitespace follows, the key then beginning at the next word — so `Le Port` files under P while `Leman` and `Léman` keep their L. The words are `title.sort.ignoredPrefixes=La,Le,Les` in [`maro.properties`](../../app/src/main/assets/maro.properties), parsed by `AppConfig.parseIgnoredPrefixes` and read per comparison. Two reviews folded; both gates green; committed as `01b2524`.

**A filter row tap leaves the popup open.** `FilterControl`'s `expanded = false` after `onFilterChange` went: the filter applies **live** on each tap, the list answering behind the standing popup, and the popup is dismissed by an outside tap or by back alone — Compose's own `onDismissRequest`. No group count enters the rule and no draft state exists. Verdict **ship**, with three records: the popup standing over the list it filters, `expanded` being `remember`-only across a configuration change, and no dismiss affordance inside it beyond the platform's pair.

**Carried.** The route-dialogs section still holds its two open todos: the device pass over the panel, the exit dialog and the aim ring, and the removal of the three diagnostic log lines in `RouteHost` once that pass has answered.

## Target Files

- `app/src/main/java/ykws/android/maro/ui/components/WheelPolicy.kt` · `DropdownWheel.kt` · `DropdownRow.kt` — the landing, the one comparison, and the single resolution
- `app/src/main/java/ykws/android/maro/data/model/ListSortOrder.kt` — `titleOrder`, `titleSortKey`, the lists' `TITLE` branch
- `app/src/main/java/ykws/android/maro/data/route/RouteEndSelection.kt` — `routeEndEntries`, beside `MarkerRouteFlags`
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — `routeEndOptions`, now ordered by the shared rule
- `app/src/main/java/ykws/android/maro/config/AppConfig.kt` · `app/src/main/assets/maro.properties` — `titleSortIgnoredPrefixes` and its parse
- `app/src/main/java/ykws/android/maro/ui/components/ListOverlayScaffold.kt` — `FilterControl`'s dismissal
- Tests: `WheelPolicyTest`, `ListSortOrderTest`, `RouteEndEntriesTest`, `TitleSortPropertiesTest`
- Docs: `docs/ui-lists-guidelines.md` (the ignored-prefix rule and the filter popup's dismissal), `docs/ui-component-guidelines.md` §2.10 · §2.12

## Next Step

The device passes the three passes owe: the wheel opening on the entry the box shows at three, four and five-plus entries — with the High finding's hardening (two reads a frame apart) still unbuilt; the marker list on Title and the route ends' selector both filing `Le Port` under P; and the filter popup staying open across taps, closing on the outside tap and on back. Then the carried work of the route-dialogs section above, and the wheel plan's §9 findings, none of which are folded.
