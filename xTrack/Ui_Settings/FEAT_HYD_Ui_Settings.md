# Ui_Settings — Hydration (2026-09-10)

## State
C12 OverlayLayer parameter-object collapse is **complete** on `feature/refact-C12` — the final slice of the
MapScreen orchestration-monolith refactor (step 2; C1–C11 were merged earlier via PR #225). The new
same-package `OverlayLayerParams.kt` hosts six public `@Immutable` data classes:

| Bundle | Fields |
|---|---|
| `OverlayChrome` | 7 — drawer/wizard visibility flags + `wizardStep` + `drawerState` |
| `MenuOverlayData` | 12 — GPS mode, menu toggles, first-track/marker ids, track/marker map referentials + counts |
| `SettingsOverlayData` | 5 — settings tab + four scroll positions |
| `TrackInfoOverlayData` | 4 — track-info drawer visibility, track, id list, index |
| `TrackListOverlayData` | 3 — track sort, track filter, track list scroll state |
| `MarkerListOverlayData` | 4 — markers, marker sort, marker filter, marker list scroll state |

`OverlayLayer` went **89 → 60 params**: the read-only params were removed from several separated signature
regions with every interleaved callback left in place, and all six bundles are unpacked into 35 same-named
locals at the top of the body, so the body and every child call are textually unchanged. The single call
site in `MapScreen.kt` builds the bundles inline (plain values, never `remember`-ed). The 44 callbacks
deliberately stay individual params — bundling them pulls them out of composable-call argument position and
defeats Compose lambda memoization. Two dead `rememberLazyListState()` defaults and their two orphan imports
were deleted. Public visibility on the bundles is required: a public `OverlayLayer` cannot expose an
`internal` type.

`apk-build.bat` SUCCESS with zero new warnings on every tier. Ask reviews 8/8 and 8/8 passed for tiers
1a-i/1a-ii; tiers 1b/1c were verified by a direct pre-image `git diff` (params in/out, `ScrollState` and
`LazyListState` routing, byte-identical call-site expressions). The R10 recomposition metric is waived in
writing (plan C12 appendix). The ~30 line-anchored citations the signature shrink invalidated were repaired
across 22 markdown files (docs only), and the drawer/code docs were synced.

Commits so far on this branch: `ec57458` (tiers 1a-i + 1a-ii), `a000c18` (tier 1b); tier 1c plus the
citation/doc pass is the third commit.

Earlier in the feature (all merged): settings subtree extraction into `MapScreenSettingsOverlay.kt`, header
normalization (PR #217), properties and opacity/transparency normalization, Card/Expander/NestedCard
refactor, drawer content measurement, marker "Belongs to track".

## Target Files
- `app/src/main/java/ykws/android/maro/ui/map/OverlayLayerParams.kt` — new; the six C12 bundles
- `app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt` — signature + 35-line destructure block
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — the single `OverlayLayer(...)` call site
- `xTrack/Ui_Settings/260909_FEAT_PLN_Ui_Settings_mapScreen-orchestration-monolith-refactor.md` — C12 plan of record
- `docs/ui-drawer-guidelines.md` — new-drawer wiring procedure (bundle field, never a new signature param)
- `docs/maro-code.md` — `ui/map` package listing
- `app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt` — extracted settings subtree

## Next Step
On-device smoke test (user-driven, per the logcat workflow): deploy `feature/refact-C12` and confirm every
drawer opens/dismisses, the menu and list filters behave, both Link toggles work, track-info prev/next
navigates, and the settings tabs render and scroll. If clean, run `#bake` and open the PR into `develop` —
the branch still tracks `origin/develop`, so push with `#push`'s explicit refspec (or a one-time
`git push -u origin feature/refact-C12`) rather than a bare `git push`.
