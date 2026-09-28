# Context Hydration — Route — 2026-09-28

**Last Bake:** 2026-09-28 21:19 UTC — written by `#bake`

**Directive trace:** this session ran the covered action classes on the user's own word — every `#implement` hop they invoked, the three orders they gave in prose for the drawer's Route sub-section (the wheel retired, then the head and the per-row labels, then the card's order), the six re-facings of the action buttons they directed, and the `#bake`, `#commit` and `#merge` they called at its end — and none was taken without it; no dependency was added, no machine-shaped data file was opened, the device was never touched and nothing was deployed, so every claim about the code came from a file read or a command's own output.

## State

**The drawer's Route sub-section is rebuilt, and the app's action faces are settled.** The wheel of R70 is **gone** — `RollerRow` and its component deleted, its instrumentation cut short before its trace arrived — and each end is a **label-less `DropdownRow`** (`label = null`) showing only its value, under **one comment naming the two fields** (`route_comment_ends`: `Route origin and destination` / `Origine et destination de la route`), with **no rule between the rows**; the `Route` action closes the block, and **Auto-show zones moved to the card's foot**. In the Tracks card, `Display Tracks with:` came out of its collapse, and the Import/Export pair left `ActionRow` — deleted with its glyph imports — for two **middle action buttons**.

**The action family, settled by six re-facings in one hour:** `PRIMARY` wears `uiAccent` full with a white bold label; `SECONDARY` a **half-strength fill** (`ui.action.neutral.background` = `#801565C0`, the accent's own RGB at 50 %) under a **2 dp `ui.accent` rim at full opacity**, carrying the primary's own label; `DANGER` the red fill; and the **disabled face is the only bare outline** (1 dp `uiDividerColor`, `uiTextMuted`). A **connected bar is the norm for any set of choices**, single or multiple, its on-half wearing the shared taken-choice face — `ui.select.container` at the accent's **30 %** with a 1 dp accent border and an 18 dp `ui.value.text` check glyph. **R70 of the master book is superseded** by the dropdown, and `docs/ui-component-guidelines.md` §2.15 is now the record of the shape the app tried and withdrew.

**Dead code went with it:** `ui/icons/Route.kt`, `ActionRow.kt`, `RollerRow.kt`, `settings_section_route`, `route_end_caption_start` / `route_end_caption_destination`, and the `ui.action.tonal` key with its `AppConfig` accessor and parse line.

**Not validated:** neither the Phase 6 fairing of the morning nor any of today's drawer controls has been seen on the device.

## Target Files

- `app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt` — the Route sub-section, the card's order, the middle-action pair
- `app/src/main/java/ykws/android/maro/ui/components/ConfirmDialog.kt` · `MultiSelectRow.kt` · `SegmentedRow.kt` — the action and choice faces
- `app/src/main/assets/colors.properties` · `app/src/main/java/ykws/android/maro/config/AppConfig.kt` — `ui.select.container`, `ui.action.neutral.background`
- `app/src/main/res/values/strings.xml` · `values-fr/strings.xml` — `route_comment_ends`, and the keys retired beside it
- `docs/ui-component-guidelines.md` §2.7 / §2.7b / §2.15 / §5.6 / §5.9 · `docs/ui-drawer-guidelines.md` §8 / §8a · `docs/color-scheme.md`
- `xTrack/Route/260922_FEAT_PLN_Route_ask-policy-and-target-validity.md` — R70 superseded
- `xTrack/Ui_General/260928_FEAT_PLN_Ui_General_active-action-face.md` — the action face's own plan, its six revisions and their reasons

## Next Step

The walk at [`FEAT_DSC_Route.md`](FEAT_DSC_Route.md) is **open** — its cursor on the trigger's coroutine read — so it blocks the bake's fold and any `#archive` until it closes. Behind it, in the walk's own order: `MapScreen.kt:91`'s unused `OutlinedButton` import (one line of dead code), Phase 6's folded fixes never re-reviewed, Phase 5's marker weights, Change 4's fine band, the progressive-draw findings, the F1/F2 wording pair, the two long-line record rows, and the device passes — the fairing's and the drawer's.
