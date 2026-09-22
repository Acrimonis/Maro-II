# Context Hydration — Route — 2026-09-22

**Last Bake:** 2026-09-22 20:10 UTC — written by `#bake`

**Directive trace:** no covered action stopped, and one claim is named rather than glossed. The device was the user's: the crash they reported is what authorised reading `adb logcat -d -b crash`, and no device was touched before that word. No dependency was added, no machine-shaped data file was opened, and every claim about the code came from a file read, a command's own output or a hop's diff. The session's git state was readable because `#commit` and `#push` were invoked; before that, the read-only checks (`status`, `log`, `diff`) were run by the Debug hop and by the Code task. The one bend is attribution: the run's own readings — the 554-test baseline, the per-suite counts after the correction, the green build — are **the hops' reports**, re-read as reports rather than re-run by the Architect, and the working tree's git state was taken from a `git status` a subtask ran, not from a reading of my own.

## State

**The mode's interaction is built, reviewed and green; the plan has left design.** The eleven-step build order of [`260922_FEAT_PLN_Route_interaction-build.md`](260922_FEAT_PLN_Route_interaction-build.md) landed in one `#implement` Code hop against R1–R42 of the requirement book, and the Ask hop returned **revise** — two blocking findings (the refresh asking from the position the phase opened on rather than the boat's live reading; the all-scope save free to rewrite rather than rename, its outcome resting on dispatch order) and four should-fix — all six closed in a second Code hop, each pinned by an assertion that fails on its revert. The build plan's own stale *six keys* was corrected to the **nine** it and the book both name.

**Then the device answered.** The first launch of the built mode crashed, the same fatal twice fifteen seconds apart, and `adb logcat -d -b crash` named it: `NullPointerException ... GeoPoint.clone() on a null object reference`, at `Marker.setPosition` ← `RouteHost.kt:344`, where the pin's empty branch set `position = null` and osmdroid clones what it is given. The tree's own diff attributed it to this session's attach-once refactor, exonerated the `origin/develop` merge, and found no second instance of the `TrackSummary.isLive` field-number class anywhere on the startup path. The fix hides the overlay with its own `isEnabled` flag — the idiom `CoastlineMapView` already uses — and the whole build is green after it.

**What remains open, named rather than implied:** the device pass the feature has owed since the engine work (the aim under a dragging finger, the fling's cancellation, the panel in the dashboard slot and the saved course appearing in the list as a route), which no test and no reading in this session can take; the flag rename the trace work owns, `plannedCourse` still its name here; and two adjacent matters raised and deliberately left — pressing `Save as Track` twice writes two tracks for one route, and two pre-existing startup crashes in the crash buffer (`DepthSerializer`/`DepthProtos` from 09-18 to 09-21, `SettingsManager.load` from 09-17) which carry no Route frame and belong to whichever feature owns those loads.

## Target Files

- `xTrack/Route/260922_FEAT_PLN_Route_interaction-build.md` — the eleven-step build order, now shipped and pointed at from the epic's `## Implemented`
- `xTrack/Route/260922_FEAT_PLN_Route_ask-policy-and-target-validity.md` — the master requirement book, R1–R42, and the source of the nine keys
- `xTrack/Route/FEAT_DSC_Route.md` — the epic: eight rules moved from *stated and not yet written* to *written*, the key files corrected to the shapes that shipped (the seam as a session, the one worker, the attach-once host, both phases' panel, the new `MapPulseDot.kt`), the state flow redrawn, and two `## Implemented` entries — the build with its review, the crash with its fix
- `app/src/main/java/ykws/android/maro/ui/map/RouteHost.kt` — the crash's fix, and the live origin the refresh gate reads
- `app/src/main/java/ykws/android/maro/ui/map/RouteOverlay.kt` · `RouteViewModel.kt` · `RouteConfirmPanel.kt` · `MapScreen.kt` — the corrected findings' homes
- `xTrack/Tracks/260922_FEAT_PLN_Tracks_trace-flag-and-display.md` — the trace work's build order, untouched by this session

## Next Step

**The trace work's eight steps**, written up in [`../Tracks/260922_FEAT_PLN_Tracks_trace-flag-and-display.md`](../Tracks/260922_FEAT_PLN_Tracks_trace-flag-and-display.md) — the flag's rename from `plannedCourse`, the filter, the card and the Settings rows — ordered after this one because the save it renames is what landed here. The device pass over the mode stays the user's, and the two flagged adjacent matters stand where they were raised.
