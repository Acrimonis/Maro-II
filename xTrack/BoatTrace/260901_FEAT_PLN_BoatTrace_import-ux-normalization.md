# BoatTrace — Import UX normalization (archive toast + single confirm sheet)

**Date:** 2026-09-01
**Status:** revised after Ask review (9 amendments folded in).

## Problems

1. **ZIP archive import broken** — extension is derived from the `content://` URI string ([`MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:639)); `OpenDocument()` URIs have no usable extension, so a ZIP is routed to the single-GPX path and silently fails.
2. **No result feedback for archive import.**
3. **Single-GPX dialog not normalized** — `AlertDialog` with Skip/Update/New instead of a dashboard-panel bottom sheet with Duplicate/Override/Cancel.

## Confirmed functional flow

**Archive (ZIP)**
- Detect by magic bytes (`PK`).
- Per `.gpx` entry: skip if the track already exists (id for Maro blobs, name for foreign GPX) — never override.
- End with normalized feedback: **"x imported, x ignored"**.

**Single GPX**
- No match → import as new → feedback.
- Match → dashboard-panel 3-action bottom sheet:
  - **Duplicate** → import as new copy (`IMPORT_NEW`).
  - **Override** → replace existing, keep id, prefer edited `<trkpt>` points (`UPDATE_EXISTING`).
  - **Cancel** → dismiss, nothing imported (silent).

## Changes (amended)

1. **Sniffing** — [`MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:639): `isZip = bytes.size >= 2 && bytes[0]=='P'.code.toByte() && bytes[1]=='K'.code.toByte()`; `< 2` bytes → GPX path (graceful failure). Keep magic bytes; do NOT use `getType` (unreliable for GPX) or `DISPLAY_NAME` (provider-dependent).
2. **Single-import outcome** — private sealed result `Imported / SkippedDuplicate / Invalid` so `importZip` counts only true duplicates as "ignored" and the single path distinguishes invalid input from skip (today `importSingleGpx` returns `null` for both). **Duplicate criteria:** a Maro export is a duplicate when its decoded `Track.id` already exists; a foreign GPX is a duplicate when its parsed `name` already exists. `Invalid` (corrupt/empty/no points) is not counted as ignored.
3. **ImportResult(imported, ignored)** — `importZip` counts `SkippedDuplicate`; `importTracks` returns it (call sites: 3, all toast-only).
4. **Corrupt ZIP** — wrap `importZip`'s `ZipInputStream` iteration in try/catch; on `ZipException` return partial `ImportResult` instead of propagating.
5. **Feedback** — replace `showImportToast` (`android.widget.Toast`, hardcoded English) with the codebase's normalized Compose banner (exit-toast / `LockBanner` style: `Surface` 14dp, 2dp border, `uiCardBackground`, `uiSettingsToastText`), placed at the bottom of the map, **horizontally centered in the space left of the zoom buttons** (i.e., excluding the right-edge control column). Shows localized "x imported, x ignored" with plurals. Consolidate the two inline "Import failed" toasts.
6. **3-action bottom sheet** — dedicated sheet modeled on [`RecordingExitSheet`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:6137) (already a 3-action `ModalBottomSheet`), using `stringResource` labels Duplicate/Override/Cancel. Do NOT extend [`ConfirmSheet`](app/src/main/java/ykws/android/maro/ui/components/ConfirmSheet.kt:43). Override uses `semanticDanger` red (destructive); Duplicate uses accent.
7. **Strings** — add `import_duplicate` / `import_override` (+ FR), reuse `action_cancel`, retire `import_skip`/`import_update`/`import_new`, add localized import-result strings (EN + FR, with agreement "importée(s)/ignorée(s)").

## Edge cases

- Empty ZIP (no `.gpx` entries) → failure message, not "0 imported, 0 ignored".
- Re-import all-skipped → "0 imported, N ignored" (expected).
- Cancel → silent dismiss, no import, no feedback.
- Empty/1-byte file → guard before sniffing.
- BOM-prefixed GPX (`EF BB BF`) → not `P`, routes GPX correctly.
- Duplicate within same ZIP → `nameSet`/`idSet` update per success already handles it.

## Verification

- Export tracks → import ZIP → imported once; re-import → "0 imported, N ignored"; no duplicates.
- Single GPX match → bottom sheet Duplicate/Override/Cancel behave; foreign GPX imports as new.
- Feedback uses the normalized Compose banner; FR strings present.
