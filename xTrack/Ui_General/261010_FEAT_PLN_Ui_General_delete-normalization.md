# Ui_General — Delete normalization: one pending rule, two surfaces kept

**Status:** shipped 2026-10-10 on `feature/rte-n-trck-list` — `apk-build.bat` and the scoped `ui.map` + `ui.components` suites green; the pipeline's Ask hop was **declined by the user**, so the pass ships **without an independent review**, and the device pass is owed
**Feature:** Ui_General (the list scaffold's delete lifecycle and the map's dashboard toast). The kind gates the map honours are UI_Map's; the marker store's post-create pending is Markers'.

## The retraction (first, so the record is honest)

- The first draft's second rule claimed that [`MarkersViewModel.pendingDeletes`](../../app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt:1156) was never filled and that `softDeleteMarker` / `undoDeleteMarker` / `commitPendingDeletes` had no caller. **That claim is false**, and the pipeline's own "verify with a search before removing" gate is what caught it.
- The path is live: the post-save undo enqueues [`ActiveSnack.CreateUndo`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:3915), [`onSnackUndo`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1046) answers it with `undoCreateMarker()`, and [`undoCreateMarker()`](../../app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt:1229) calls `softDeleteMarker(id)` then `commitPendingDeletes()` — so a marker created and then un-created really is deleted.
- The search behind the claim was case-sensitive on the wrong token and missed it. The fault was the agent's, and it is recorded here rather than re-planned around.

## The inventory (the plan's own guard that nothing is lost)

| # | Door | Surface | What hides the item today | What commits the deletion, and when | What restores it |
|---|---|---|---|---|---|
| 1 | List row swipe | the list panel | the scaffold's own `SwipeState`, the row sliding away (per-item local state) | `ListOverlayScaffold.pendingDeletes` committed by back ([`:619`](../../app/src/main/java/ykws/android/maro/ui/components/ListOverlayScaffold.kt:619)), dispose/scrim ([`:629`](../../app/src/main/java/ykws/android/maro/ui/components/ListOverlayScaffold.kt:629)), multiselect entry ([`:582`](../../app/src/main/java/ykws/android/maro/ui/components/ListOverlayScaffold.kt:582)) and the header Close ([`:725`](../../app/src/main/java/ykws/android/maro/ui/components/ListOverlayScaffold.kt:725)) | the scaffold's inline `SnackbarSlot`, no recompute |
| 2 | Card delete, tracks and routes | the map's toast | **nothing** — the map keeps drawing it | the shell's `pendingDeleteIds` (`t:`), committed at the toast's end by [`onSnackTimeout`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1053) → `deleteTrack` | [`onSnackUndo`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1017): clears the pending id and re-opens the card |
| 3 | Card delete, markers | the map's toast | **nothing** | as row 2, `m:`, → `deleteMarker` | as row 2, re-opening the card on the MAP source |
| 4 | Multiselect confirm | the list panel | the rows leave with the list's own state | **immediately**, at the dialog's confirm — no pending window | none; the dialog *is* the confirmation |
| 5 | Route discard | the map's toast | the display ends at once (R92) | the disposal waits on the toast's end | Undo restores; *New acquisition* commits |
| 6 | Post-save create undo | the map's toast | nothing (the marker is already drawn) | **its timeout deletes nothing** — [`dismissLastSaved()`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1064); the **Undo** is what deletes, through `softDeleteMarker` + `commitPendingDeletes` | the timeout's dismiss, or nothing |

- Four timings, not one: the toast's window, the scaffold's four commit triggers, the dialog's immediate commit, and row 6's **inverted polarity** where Undo deletes and the timeout is the no-op.
- Rows 2 and 3 are the reported gap: the deletion is deferred exactly as the user wants, and **nothing hides the item while it waits**.

## The revised rule

1. **One pending-deletion home, for the delete family alone.** The shell's set (rows 2, 3) and the scaffold's (row 1) become **one instance**; the route mode's pending (row 5) and the post-create pending (row 6) are **different concepts** and keep their own homes, named apart in code and in the record.
2. **One keying.** The shared set keeps the shell's `kind:id` spelling; the scaffold is generic and must not learn kinds, so its bare ids translate at **one** boundary.
3. **One hiding rule, reaching the map — for the card doors alone.** A card-deleted item leaves the map's drawing and the card's walk; a **list-swiped** row does not, and the map keeps drawing it until the commit (the user's correction, 2026-10-10). The one pending set carries that difference as **data** — each entry names whether the map hides it — so the single home stands. The exclusion needs the pending keys as an **input to the stored-tracks pass and in its rebuild keys** (or it cannot repaint), and a **new exclusion site in the marker overlay**, which does not exist today. A list surface hands its records their key through a `pendingKeyPrefix` it is given, the one boundary at which a generic scaffold meets the shell's `kind:id` spelling.
4. **Triggers and restores verbatim.** The toast's timeout and swipe; the scaffold's back, dispose, multiselect entry and header Close; every Undo. Nothing on the list may commit one shared set twice — the two `onDispose` arms are audited and ordered so one owner commits.
5. **The two odd doors keep their polarity.** Row 4 commits immediately with no window; row 6 keeps Undo-deletes, timeout-dismisses.
6. **The route discard keeps R92's two-phase mechanics** inside the route mode, and is the family's documented exception: its disposal belongs to the engine, not to the record.

## Resolved by the user

- The list swipe's hiding was put and answered on 2026-10-10: **only the card's delete hides the map's item.** The pending entry therefore carries `hideFromMap`, and a list surface's records enter with it false — which is also why the one set needs a typed entry rather than bare keys.

## Decisions

- **D1 — one home is the deletion pending alone.** The post-create pending and the route discard's are named apart, the retraction above being the reason the first draft's merge could not stand.
- **D2 — the scaffold keeps a generic contract.** It takes the shared set as a parameter and translates to `kind:id` at one boundary, so `ui/components` learns no kinds. **Objection:** the scaffold stops being self-sufficient, so a consumer that forgets to pass the set leaves its inline Undo inert.
- **D3 — no new test dependency.** The scaffold's commit triggers live inside a composable, so pinning them in JVM tests would need a Compose UI-test artifact — a dependency, which the rules make the user's call. The plan therefore **proposes none**, pins what is pure, and names the triggers' proof as the device pass rather than leaving the gap unstated.
- **D4 — hiding reaches the map only, and only from the card doors.** The list's own hiding stays the scaffold's visual, per the user's *surfaces kept*, and a swipe's item stays drawn on the map per the user's correction.
- **D5 — one set, typed entries.** The user's correction is carried as `PendingDeletion(key, hideFromMap)` inside the shared set, rather than as a second set — the one home standing while the doors differ. **Objection:** it widens the scaffold's contract from `String` to a type, so a consumer that supplies the set must know the entry shape.

## Risks

- The exclusion must sit in the pass's **inputs and its keys**; a keyed miss leaves the item drawn until an unrelated repaint.
- One shared set makes a double-commit reachable where two local sets could not; the commit empties the set so the second arm is a no-op, but that must be stated and each arm still observed.
- The bare-id ↔ `kind:id` boundary is a new join: a mistranslation hides the wrong item, or none.
- The map hiding a list-swiped item is the one visible behaviour change beyond the reported bug.

## Verification

- Scoped `ui.map` + `ui.components` suites, then `apk-build.bat`.
- Tests owed: the hiding rule per kind (pending leaves the drawn set and returns on Undo) and the boundary translation.
- Not testable without a new dependency: the scaffold's commit triggers — the device pass is their proof (D3).
- Device pass owed: a card delete hiding at once and returning on Undo; a list swipe hiding the map's item at once; the multiselect confirm still deleting at once; the route discard unchanged; the create-undo still deleting on Undo and doing nothing on its timeout.
