# Context Hydration — Markers — 2026-10-23

**Last Bake:** 2026-10-23 17:41 UTC — written by `#bake`. One session on `feature/mrk-lst-edit`: the marker edit form rebuild.

**Directive trace:** All five covered action classes were met and none stopped the session — no dependency added, no machine-shaped data file opened, the work ran only on the user's `#new` and `#impl`, no device was touched (the device pass stays the user's), and every claim written here about the code carries a file read behind it, save the build result, which is carried forward as its own report.

## State

`feature/mrk-lst-edit` carries the marker-edit-form fix: [`startWizard`](../../app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt:819) now always rebuilds `_createForm` from the marker being edited, dropping the name-match gate, so a menu-opened card stepped Prev/Next to a same-named neighbour no longer opens the first marker's form, position and colour in the wizard. `apk-build.bat` BUILD SUCCESSFUL. The review hop approved; its one non-blocking finding — the form-building block duplicated between `openEditDrawer` and `startWizard` — is owed as a shared factory.

## Target Files

- `app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt` — `startWizard` rebuilds the form from the marker being edited
- `xTrack/Markers/FEAT_DSC_Markers.md` — the `## Implemented` entry

## Next Step

Extract the shared `CreateFormState`-from-marker factory to dedupe `openEditDrawer` and `startWizard`; the device pass on the edit fix is the user's.
