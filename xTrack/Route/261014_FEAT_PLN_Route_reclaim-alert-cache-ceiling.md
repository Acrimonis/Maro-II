# FEAT_PLN Route — the reclaim alert as a notification, and the tile store's ceiling

**Feature:** Route · **Date:** 2026-10-14 · **Status:** implemented 2026-10-14 — the pointer sits in the feature's `## Docs`, and the landing is recorded in `## Implemented` at the foot of this file.

## 1. The ask

The store's first reclamation is reported by a toast and nothing else — [`reportFineTileReclaimed()`](../../app/src/main/java/ykws/android/maro/MainActivity.kt:280), raised from `DirectoryFineTileStore`'s `onReclaim` sink wired at the install ([`MainActivity.kt`](../../app/src/main/java/ykws/android/maro/MainActivity.kt:97), D9). A toast is gone in seconds, so the same message is to be **pushed as an Android notification as well**, and the store's disk ceiling — `route.fineTile.maxBytes=16777216`, 16 MiB (D8) — is to rise to **48 MiB**.

## 2. D1 — The alert rides the app's one channel, behind one permission ask

The app carries exactly one notification channel, `maro_persistent`, created in [`TrackRecordingService.createNotificationChannel()`](../../app/src/main/java/ykws/android/maro/data/track/TrackRecordingService.kt:426) with `NotificationManager.IMPORTANCE_DEFAULT` and sound off. The alert **reuses it** — the user's choice — so no second entry appears in Android's notification settings and no channel id is born twice.

- The id becomes readable: `private const val CHANNEL_ID` → `internal const val CHANNEL_ID` in [`TrackRecordingService`](../../app/src/main/java/ykws/android/maro/data/track/TrackRecordingService.kt:444), with one KDoc line saying it is the app's single channel and that the alert reads it from here. It stays the id's one home.
- Consequence, accepted: the alert inherits that channel's importance and its "Persistent notification while Maro II is running" description, and a user who silences that channel silences this too.

`POST_NOTIFICATIONS` is declared in the manifest and **never requested**, while `targetSdk = 36`: on Android 13+ a posted notification stays invisible until the grant exists. So the permission is asked **once at startup**.

- A `RequestPermission()` launcher is registered as a MainActivity property — registered at construction, so it is legal before `onStart`.
- It is launched from a `LaunchedEffect(Unit)` at the head of the composition, so the prompt lands over a drawn window rather than a blank one, and it fires only on `Build.VERSION.SDK_INT >= TIRAMISU` and only while `POST_NOTIFICATIONS` is not granted, so a granted app is never asked again.
- `androidx.activity` and `androidx.core` already ship; **no dependency is added**.

## 3. D2 — The ceiling moves to 48 MiB at its one home

`maro.properties` is the value's one home and the two code copies mirror it; all three move in one write so they can never disagree.

| Home | Was | Becomes |
|---|---|---|
| [`maro.properties`](../../app/src/main/assets/maro.properties:366) | `route.fineTile.maxBytes=16777216`, comment `Default 16 MiB.` | `50331648`, comment `Default 48 MiB.` |
| [`AppConfig.kt`](../../app/src/main/java/ykws/android/maro/config/AppConfig.kt:253) | `16L * 1024L * 1024L`, KDoc `default 16 MiB (16 777 216)` | `48L * 1024L * 1024L`, KDoc `default 48 MiB (50 331 648)` |
| [`DirectoryFineTileStore.kt`](../../app/src/main/java/ykws/android/maro/spatial/DirectoryFineTileStore.kt:182) | `16L * 1024L * 1024L`, KDoc `(16 MiB)` | `48L * 1024L * 1024L`, KDoc `(48 MiB)` |

- Untouched: `FineTileMap.DEFAULT_MAX_BYTES` — the **in-memory** map's own 8 MiB ceiling, a different knob — the store's incremental total, its touch-on-load, its once-guard and its `STORE` trace lines.
- The plan [`261013_FEAT_PLN_Route_pace-free-tiles.md`](261013_FEAT_PLN_Route_pace-free-tiles.md) D8 keeps its dated 16 MiB: it records what was decided on 2026-10-13 and is not the value's home. This plan is where the new value is decided.
- Consequence, accepted: a bigger cache reclaims far more rarely, so **the alert becomes rarer as it becomes visible** — the device pass needs a deliberately small ceiling to see it at all.

## 4. D3 — The notification's own shape, and the toast stays

- The toast is **not** replaced: the same trigger raises both, once, through the store's own once-guard.
- Title is the existing `app_name` and the body the existing `startup_fine_tile_reclaimed` — **no new string key in either locale**, so nothing is written into `strings.xml` or `strings.xml` (fr).
- Silent and non-ongoing: `NotificationCompat.PRIORITY_DEFAULT` on an already-silent channel, `setAutoCancel(true)`, no `setOngoing`, the small icon `R.mipmap.ic_launcher`.
- Tapping opens the app on the same intent the persistent notification uses — `MainActivity` with `SINGLE_TOP or CLEAR_TOP`, `PendingIntent.FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE`.
- Its own notification id, `1002`, distinct from the service's `1001`, so the alert never overwrites the recording banner.
- Posted through `NotificationManagerCompat` and only while `areNotificationsEnabled()`, so a denied or revoked grant degrades to the toast alone rather than logging a silent refusal.
- Raised from `reportFineTileReclaimed()`, which already runs on the UI thread by the sink's own hop.

## 5. Verification

- The properties pin, in the shape [`CoastlineAppearancePropertiesTest`](../../app/src/test/java/ykws/android/maro/config/CoastlineAppearancePropertiesTest.kt:66) uses: a new `app/src/test/java/ykws/android/maro/config/` suite loads the shipped `maro.properties` and asserts `route.fineTile.maxBytes` carries 48 MiB and equals `AppConfig.routeFineTileMaxBytes`, so a one-sided edit fails there. **Strike this suite if the user says the value needs no pin** — nothing else in the tree reads the key in a unit test.
- `apk-build.bat` green, plus the scoped suites: the `config/` property suites, `DirectoryFineTileStoreTest`, `FineTileMapTest`, `RouteFineWindowShapeTest` and the route pass suites. No suite passes the store's default ceiling today — `DirectoryFineTileStoreTest` always names `maxBytes` — so the value change should move no fixture; confirm it rather than assume.
- Nothing in the route's own arithmetic reads the ceiling, so no line or clock can move.

## 6. Owed device pass (R97 — the user's own, and no gate on this landing)

- Grant the permission on the prompt, then read the alert in the shade on a reclaim forced with a temporary small ceiling; check it is silent, that it is grouped with the persistent banner, and that tapping it opens the app.
- Confirm the 48 MiB ceiling behaves: the pre-warm fills more before the first reclaim, and the directory stops growing at the new bound.

## 7. Files

- `app/src/main/java/ykws/android/maro/MainActivity.kt` — the notification beside the toast, and the one permission ask
- `app/src/main/java/ykws/android/maro/data/track/TrackRecordingService.kt` — the channel id made readable
- `app/src/main/assets/maro.properties`, `config/AppConfig.kt`, `spatial/DirectoryFineTileStore.kt` — the ceiling, three homes in step
- `app/src/test/java/ykws/android/maro/config/` — the ceiling pin
- `xTrack/Route/FEAT_DSC_Route.md` — the store bullet gains the alert sentence, and this plan joins its `## Docs`

## Implemented — 2026-10-14

Landed through the `#implement` pipeline (Code → build → Ask → this report).

- **The alert** — [`postFineTileReclaimNotification()`](../../app/src/main/java/ykws/android/maro/MainActivity.kt:335) posts on [`TrackRecordingService.CHANNEL_ID`](../../app/src/main/java/ykws/android/maro/data/track/TrackRecordingService.kt:449), which went `private` → `internal` with its one KDoc line and remains the id's only literal. Id 1002, the banner's 1001 untouched; `PRIORITY_DEFAULT`, `setAutoCancel(true)`, `R.mipmap.ic_launcher`, a tap intent on `MainActivity` with `SINGLE_TOP or CLEAR_TOP`, and posted only while `areNotificationsEnabled()`. [`reportFineTileReclaimed()`](../../app/src/main/java/ykws/android/maro/MainActivity.kt:319) raises the toast and then the notification, so both ride the store's one once-guard.
- **The permission** — [`requestNotificationPermission`](../../app/src/main/java/ykws/android/maro/MainActivity.kt:82) is registered as a MainActivity property, so it is bound at construction, and launched from a `LaunchedEffect(Unit)` at the head of the composition — over a drawn window — on API 33+ only and only while the grant is missing. No new string key: `app_name` and `startup_fine_tile_reclaimed` carry both surfaces, so neither locale changed.
- **The ceiling** — 48 MiB written in step at its one home `route.fineTile.maxBytes=50331648` ([`maro.properties`](../../app/src/main/assets/maro.properties:366)) and its two mirrors, [`AppConfig.routeFineTileMaxBytes`](../../app/src/main/java/ykws/android/maro/config/AppConfig.kt:253) and [`DirectoryFineTileStore.DEFAULT_MAX_BYTES`](../../app/src/main/java/ykws/android/maro/spatial/DirectoryFineTileStore.kt:182), comment and KDoc copies with them. `FineTileMap.DEFAULT_MAX_BYTES`, the in-memory 8 MiB, is untouched.
- **Verification** — `assembleDebug` BUILD SUCCESSFUL, carrying only the pre-existing warnings, and the scoped suites green: the `config/` property suites, `DirectoryFineTileStoreTest`, `FineTileMapTest`, `RouteFineWindowShapeTest`, the spatial `Route*`, `multipass`, `ui.map Route*` and `data.route` suites, with the new pin at 1/1. `DirectoryFineTileStoreTest` needed no fixture move — confirmed, not assumed.
- **Deviation from §5, with its reason** — the pin [`FineTileStoreCeilingPropertiesTest`](../../app/src/test/java/ykws/android/maro/config/FineTileStoreCeilingPropertiesTest.kt:38) asserts **all three** homes rather than the key and one mirror, because §3's own rationale is that the three move in one write so they can never disagree; a two-sided pin would leave the store's default free to drift.
- **Environment note** — `apk-build.bat`'s bare `gradlew` call is unresolvable in this shell, so the identical task ran as `.\gradlew.bat assembleDebug`; the batch's own contract — packages only, no bake — is preserved.
- **Ask hop's findings, no action implied** — the alert's `PendingIntent` resolves to the same instance the recording banner uses, same request code and an equal Intent, which is harmless because both open `MainActivity`; the feature file carries no `## Implemented` section by its 2026-10-07 rule, so the landing lives in its store bullet and this Docs pointer; and the notification's visibility is the user's own pass (R97), which gated nothing.
- **Owed** — the device pass of §6: the grant taken on the prompt, the alert read in the shade on a reclaim forced with a temporary small ceiling, and the 48 MiB bound behaving across a pre-warm.
