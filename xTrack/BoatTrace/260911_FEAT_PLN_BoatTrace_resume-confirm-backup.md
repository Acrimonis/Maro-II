<!-- scope: feature -->

# Resume from the track card — confirmation sheet with optional backup

- **Feature:** BoatTrace
- **Branch:** feature/tracks-recording
- **Date:** 2026-09-11

## Why

Resume appends new points to an already-stored track, yet today it is a single unguarded tap. The list card
has the button (between pin and export, [`TrackHistoryOverlay.kt:524`](app/src/main/java/ykws/android/maro/ui/map/TrackHistoryOverlay.kt:524)); the
two dashboard drawers have no resume wiring at all ([`OverlayLayer.kt:494`](app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt:494), [`:586`](app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt:586)).
Both get the same confirmation flow.

## Locked decisions (user-approved 2026-09-11)

- **D1** Trigger from the list card *and* both dashboard drawers.
- **D2** `ModalBottomSheet` styled like [`ConfirmSheet`](app/src/main/java/ykws/android/maro/ui/components/ConfirmSheet.kt:43) — title, message, a **default-checked**
  checkbox ("Back up this track before resuming"), then two actions: **Cancel** and **Resume**.
- **D3** Cancel (and scrim/dismiss) = no resume; the sheet closes and the source surface stays as it was.
- **D4** Backup = duplicate the **pre-resume** track: new UUID id, name `<original name> (backup)`,
  `pinned = false`. Markers keep pointing at the original (`UserMarker.trackId` untouched), so the copy
  carries no marker links. *(Amended 2026-09-11 — the `visibleOnMap = false` clause was dropped during the
  Mergitur integration; see the Amendment section below.)*
- **D5** Recording continues on the **original** track.
- **D6** The crash-recovery **Continue** path is unchanged (it already has its own dialog).
- **D7** Naming mirrors existing dialogs: the sheet is declared next to `RecordingExitSheet` /
  `ImportConflictSheet` in `MapScreen.kt`; host state is `pendingResume: PendingTrackResume?` alongside
  `pendingTrackImport` ([`MapScreen.kt:436`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:436)).

## Steps

1. **Backup API.** [`TrackViewModel`](app/src/main/java/ykws/android/maro/data/track/TrackViewModel.kt:183): add `duplicateTrack(id, suffix): String?` — `repository.load(id)` → copy with new
   `UUID.randomUUID()` id, suffixed name, `visibleOnMap = false`, `pinned = false`, refreshed
   `updatedAtEpochMs` → `repository.save(copy)` (which rebuilds the index). Then
   `resumeTrack(id, backup: Boolean)`: write the backup first, `refreshSummaries()` so the new card appears,
   send `ACTION_RESUME_TRACK`, then the existing `delay(500)` + `refreshSummaries()`.
2. **Strings (EN + FR).** `action_resume`, `resume_confirm_title`, `resume_confirm_message`,
   `resume_confirm_backup`, `track_backup_suffix`; also replace the hardcoded `"Resume recording"` at
   [`TrackHistoryOverlay.kt:531`](app/src/main/java/ykws/android/maro/ui/map/TrackHistoryOverlay.kt:531) with a `cd_resume_recording` resource. Reuse `action_cancel`.
3. **Sheet + state.** `PendingTrackResume(trackId, trackName)` data class and `ResumeConfirmSheet`
   composable in `MapScreen.kt` (same file as its siblings), tokens only, checkbox via MD3 `Checkbox`
   tinted with `AppConfig.uiAccent`.
4. **Host.** Invoke the sheet at MapScreen level (after `OverlayLayer`, alongside the other sheets) so it
   survives the source surface; on confirm → backup (if ticked) → resume → close the source surface
   (list overlay / drawer).
5. **Rewire triggers.** List `onResumeTrack` ([`OverlayLayer.kt:664`](app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt:664)) opens the sheet instead of resuming, and loses its
   `onDismissTrackHistory()` call (dismissal moves to confirm). Pass the same lambda plus
   `isRecording = trackRecorderState.state == ON` to the two `TrackCardContent` sites
   ([`OverlayLayer.kt:494`](app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt:494), [`:586`](app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt:586)) so the dash shows the button. Invoke the sheet outside the
   drawer-rendering section (same level as `MapImportConflictHost`) so closing a drawer cannot remove it.
6. **Build** `gradlew assembleDebug`, then Ask review per `#implement`.

## Verified constraints (review pass, 2026-09-11)

- **The recording flag is already in scope at every trigger site.** `OverlayLayer` receives the recorder state
  and forwards it as `liveTrackState = trackRecorderState` ([`OverlayLayer.kt:653`](app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt:653)), so passing `isRecording`
  to the two drawer `TrackCardContent` sites needs no new plumbing — only the `onResumeTrack` lambda.
- **Backup must be written before the service intent.** `recorder.resume(fromCheckpoint = false)` clears
  `endTimeMs` on a finalized track ([`TrackRecorder.kt:319`](app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt:319)), and the service force-saves the resumed
  track with `visibleOnMap = true` when it was hidden ([`TrackRecordingService.kt:335`](app/src/main/java/ykws/android/maro/data/track/TrackRecordingService.kt:335)) — that write only
  touches the resumed id, so a pre-written backup keeps its own `visibleOnMap = false`.
- **Id convention:** `UUID.randomUUID().toString()`, same as new recordings ([`TrackRecorder.kt:521`](app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt:521)); a fresh id also
  gives the backup its own checkpoint namespace (`{id}_checkpoint.bin`).
- **Strings:** `action_cancel` / `action_delete` exist in both locales ([EN `:274`](app/src/main/res/values/strings.xml:274), [FR `:273`](app/src/main/res/values-fr/strings.xml:273)); `action_resume`,
  `resume_confirm_*`, `track_backup_suffix` and `cd_resume_recording` are all new.
- **The list currently dismisses on the trigger, not on confirmation** ([`OverlayLayer.kt:664-667`](app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt:664)) — that
  dismissal moves into the confirm branch.
- **Post-resume there is no `isLive` summary** (the index only reports a live track once a stored `endTimeMs`
  is null), so the button on the resumed track is suppressed by `isRecording`, which is the intended gate.

## Files

- `app/src/main/java/ykws/android/maro/data/track/TrackViewModel.kt` (backup + resume-with-backup)
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` (sheet, `PendingTrackResume`, host)
- `app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt` (trigger wiring at three card sites)
- `app/src/main/java/ykws/android/maro/ui/map/TrackHistoryOverlay.kt` (content-description string only)
- `app/src/main/res/values/strings.xml`, `app/src/main/res/values-fr/strings.xml`

## Verification

- Sheet appears from the list card and both drawers; default checkbox state is checked.
- Ticked + Resume → backup file written (new id, suffixed name, hidden, unpinned, no markers) and recording
  continues on the original; unticked + Resume → no extra track; Cancel → nothing happens, surface intact.
- Backup does not draw on the map and does not appear pinned; track list shows it after refresh.
- Resume still refuses while already recording (existing VM guard).
- Card renders the button between Pin and Export on all three surfaces; the list no longer auto-dismisses
  before confirmation, and closing the source drawer while the sheet is open does not drop the sheet.

## Open (small, decide at review)

- Checkbox glyph: MD3 `Checkbox` + accent tint vs the shared `ToggleRow`.
- Exact `track_backup_suffix` wording EN/FR.
- Whether the source surface closes on confirm only (current plan) or immediately when the sheet opens.

## Implemented

- **✓ Backup API** — `TrackViewModel.duplicateTrack(id, nameSuffix)` writes an unpinned copy (fresh
  UUID, suffixed name) and `resumeTrack(id, backupNameSuffix)` snapshots first, then sends the resume intent
  ([`TrackViewModel.kt:183`](app/src/main/java/ykws/android/maro/data/track/TrackViewModel.kt:183)).
  *(Visibility: the copy is no longer force-hidden — see the Amendment section.)*
- **✓ Confirmation sheet** — `PendingTrackResume` + `ResumeConfirmSheet` (ConfirmSheet geometry, accent
  checkbox checked by default, Cancel/Resume) hosted beside `MapImportConflictHost`
  ([`MapScreen.kt:2558`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:2558), [`:1888`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1888)). Confirm resumes, then closes the source
  surface (list overlay or track drawer — the drawer close lambda was extracted to `closeTrackDrawer`).
- **✓ Triggers** — one `onResumeRequest(id, fromList)` param; the list requests instead of resuming (its
  early dismiss dropped) and both drawer cards now show the button, gated by `isRecording`
  ([`OverlayLayer.kt:141`](app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt:141), [`:505`](app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt:505), [`:599`](app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt:599), [`:670`](app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt:670)).
- **✓ i18n** — `action_resume`, `resume_confirm_title/message/backup`, `track_backup_suffix`,
  `cd_resume_recording` in EN + FR; the card's hardcoded content description is gone.
- **✓ Build + review** — `assembleDebug` SUCCESSFUL, no new warnings; Ask review SOUND. (First build failed on
  a nested-comment slip in my own insertion; fixed and re-verified.)
- **Pending — device confirmation:** sheet on all three surfaces, checkbox default, backup written only when
  ticked (new card, hidden on map, unpinned, no marker links), recording continues on the original.

## Amendment (2026-09-11) — backup copy visibility (post-Mergitur)

The `visibleOnMap = false` clause in D4 / step 1 is **no longer applicable**. `feature/tracks-import`
deleted the persisted `visibleOnMap` field (its `ProtoNumber` is reserved) and replaced stored visibility
with a derived selection policy, so the Mergitur integration dropped the now-dead argument from
`duplicateTrack`.

**Decision (user, 2026-09-11): accept the new behaviour as correct.** The backup copy renders like any
ordinary stored track — unpinned and un-boosted, but no longer hidden — so it can appear on the map,
compete for the display cap, and draw the same geometry as the replayed live line after a resume. The
KDoc on `resumeTrack` was corrected to match; no code logic was changed.

Consequences for the device smoke test: after a ticked resume, confirm the backup card exists, is
unpinned, and that the duplicated geometry on the map is acceptable. Surrounding integration record:
`xTrack/Mergitur/260911_FEAT_PLN_Mergitur_three-branch-integration.md`.

## Follow-ups (flagged, not done)

- Make the checkbox **label** toggle the box (`Modifier.toggleable(..., role = Role.Checkbox)`) and give it a
  `contentDescription` — currently only the box itself is tappable and unlabelled for screen readers.
- Decide `duplicateTrack`'s visibility: public with an unused return id today — make it `private` unless a
  standalone Duplicate action is planned.
- If the source track fails to load, the backup is silently skipped and the resume proceeds — surface a
  failure banner if that matters.
- `updatedAtEpochMs = now` places the backup near the top of the default list sort (expected, but visible).
