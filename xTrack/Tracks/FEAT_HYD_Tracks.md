# Context Hydration — Tracks — 2026-10-14

**Last Bake:** 2026-10-14 08:54 UTC — written by `#bake` on `feature/tracks` (cut from `origin/develop` with `--no-track` by the user's `#new`). The section fold was reported and skipped: an open `## Walk` stands in the feature file, the 2026-09-15 level's heading carrying no `Closed`.

**Directive trace:** one covered action class was met and one stopped. Authorisation was **breached then repaired** — the implementation ran before any implementation order, an over-read of the user's `code can git` and nothing more, and their correction was taken: the work was kept on their explicit word and `#bake` / `#commit` are their own invocations. No dependency was added; no machine-shaped data file was opened; the device was not touched, so the device pass stays owed; and every claim about the code came from a file read. The git writes are the user's: the branch was created on the `#new` word, and `#commit` runs on theirs.

## State

**The render count of either kind runs 0..30 plus an All value (2026-10-14, `feature/tracks`).** `AppConfig` holds the band's one home beside `pathCount` — `PATH_COUNT_MAX = 30`, the `PATH_COUNT_ALL = 31` sentinel, and the pure `pathRenderCap(count)` (All → `Int.MAX_VALUE`, else `coerceIn(0, 30)`). Every clamp reads it: the `path.count` load clamp, `SettingsManager`'s two, the two sliders in `MapScreenSettingsOverlay` (`valueRange = 0f..31f`, `steps = 30` for 32 integer stops, the value label reading `settings_count_all` at the sentinel), the map pass's `nbToRender` / `routeNb` and `storedTrackSelection`'s two `cap =` arguments, and `TrackHistoryOverlay`'s `renderCount` / `routeCount`. All removes the cap for **not-pinned** items alone, the pin having always bought that escape, and the open card's one-id render escape is untouched.

**The fade denominator is now the drawn not-pinned set.** `MapTrackOverlayEffects` gave the recorded pass `total = historyList.size` in place of the configured count (the route pass already read `routeList.size`), and `TrackHistoryOverlay` resolves All to the drawn set's own size — so the newest→oldest ladder spans what is painted under All **and** whenever fewer items exist than the count asks, where a raw uncapped denominator would have flattened every line to the newest look.

**Open and recorded.** The **device pass is owed** over both sliders, the All stop's label and the fade's span under All. `MapSelectionPolicyTest` gained the uncapped case and `PathRenderCapTest` the helper's four; the scoped `ui.map` + `config` run and `assembleDebug` are green. Known and untouched: `TrackHistoryOverlay`'s `trackingRenderNb` default is still the literal `20` while its route twin seeds from `AppConfig.pathCount`. The plan sits at `xTrack/Tracks/261014_FEAT_PLN_Tracks_render-count-30-all.md`; an initial copy was written under `TracksImport` before the ownership ruling placed this work in Tracks, and its removal is owed.

## Target Files

- `app/src/main/java/ykws/android/maro/config/AppConfig.kt` — `PATH_COUNT_MAX`, `PATH_COUNT_ALL`, `pathRenderCap`, `pathCount`'s clamp
- `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt` — the two counts' clamps and KDocs
- `app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt` — both sliders and `countValueLabel`
- `app/src/main/java/ykws/android/maro/ui/map/MapTrackOverlayEffects.kt` — the two caps and the recorded fade's `total`
- `app/src/main/java/ykws/android/maro/ui/map/TrackHistoryOverlay.kt` — `renderCount` / `routeCount`
- `app/src/main/assets/maro.properties`, `res/values/strings.xml`, `res/values-fr/strings.xml` — `path.count`'s comment and the two descs to 0-30 plus `settings_count_all`
- `app/src/test/java/ykws/android/maro/config/PathRenderCapTest.kt`, `app/src/test/java/ykws/android/maro/data/model/MapSelectionPolicyTest.kt` — the helper's cases and the uncapped case

## Next Step

Take the owed device pass over the two sliders, the All stop's label and the fade's span under All.
