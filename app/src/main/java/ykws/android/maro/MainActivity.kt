package ykws.android.maro

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.MotionEvent
import android.view.WindowInsetsController
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.combine
import java.util.Locale
import ykws.android.maro.data.power.BatteryExemption
import ykws.android.maro.data.power.PowerKeeper
import ykws.android.maro.data.track.TrackRecordingService
import ykws.android.maro.ui.map.NavigationViewModel
import ykws.android.maro.ui.map.DepthViewModel
import ykws.android.maro.ui.map.MapScreen
import ykws.android.maro.config.AppConfig
import ykws.android.maro.spatial.DirectoryFineTileStore
import ykws.android.maro.spatial.RouteEngineChoice
import ykws.android.maro.spatial.multipass.FineTileCache
import java.io.File

/** The fine-tile reclaim alert's own notification id — distinct from the recording banner's 1001 (D9). */
private const val FINE_TILE_RECLAIM_NOTIFICATION_ID = 1002

class MainActivity : ComponentActivity() {

    /**
     * Power-management owner for the screen channel — decides whether `FLAG_KEEP_SCREEN_ON`
     * should be held. Lazy so [dispatchTouchEvent], which can fire before the first composition,
     * always has an instance. See `xTrack/Performance/FEAT_DSC_Performance.md`.
     */
    private val powerKeeper: PowerKeeper by lazy { PowerKeeper(applicationContext) }

    /**
     * The one `POST_NOTIFICATIONS` ask (D1). Registered as a property so it is bound at construction —
     * legal before `onStart`, unlike a call from `onCreate` — and launched from the composition's head
     * only on Android 13+ and only while the grant is missing. The result needs no reader: a denial is
     * covered by the alert's own `areNotificationsEnabled()` guard, which leaves the toast alone.
     */
    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Start the keeper's observers (recording floor + grace ticker) with the Activity's scope.
        // The window flag itself is applied further down in the composition, from the keeper's state.
        powerKeeper.start(lifecycleScope)

        // Enable edge-to-edge rendering: content draws behind the system status bar
        // and navigation bar, which become transparent. Must be called before setContent().
        enableEdgeToEdge()

        // Dark window background: the light theme's default background shows through
        // transparent system bars, creating a grey gap. Set it to match the app's
        // dark dashboard background (#16213E) so system bar areas blend seamlessly.
        window.setBackgroundDrawable(ColorDrawable(AppConfig.uiToastBackground))

        // Set status bar icons to light (white) on the dark background.
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false  // false = light icons on dark
            isAppearanceLightNavigationBars = false
        }

        // Load zone gradient tunables from zone.properties before the UI composes.
        AppConfig.init(this)

        // The fine-tile store: the route engine's fine layer persisted beside the depth raster's own
        // cache, so the first arm after a cold start reuses the tiles a previous run already marked
        // rather than building them again. Installed once, here, before the UI composes and any engine
        // can arm; the holder is the store's one reader and writer and keeps the cache across an engine
        // switch. The warm-up that fills it is the map's own effect (`MapRoutePrewarmEffect`), which
        // starts with the app and pauses while a route is armed — this install only gives it the store.
        // The disk ceiling's one home is `route.fineTile.maxBytes` (D8); the first reclamation says so
        // once (D9), hopped to the UI thread here; each load and save prints one `STORE` line on the tag.
        FineTileCache.install(
            DirectoryFineTileStore(
                dir = File(cacheDir, DirectoryFineTileStore.DIR_NAME),
                maxBytes = AppConfig.routeFineTileMaxBytes,
                onReclaim = { runOnUiThread { reportFineTileReclaimed() } },
                trace = { line -> android.util.Log.i("MaroRoute", line) }
            )
        )
        // The busy window's two edges print one `busy on` / `busy off` line each on the same route tag,
        // so a device pass reads the fine layer's building window rather than eyeballing the dot.
        FineTileCache.traceTo { line -> android.util.Log.i("MaroRoute", line) }

        // R42's report: the build could not read one of the app's #AARRGGBB colour keys and fell back
        // to its sibling's default, so the app says so at start rather than leaving the fallback silent.
        // The names arrive through BuildConfig — the build stays the one reader of maro.properties.
        reportUnreadableColourKeys()

        setContent {
            // D1's one ask: the alert rides the app's single channel, but POST_NOTIFICATIONS is declared
            // and never requested, so a posted notification stays invisible on Android 13+ without it.
            // Fired at the head of the composition — over a drawn window rather than a blank one — and
            // only while the grant is missing, so a granted app is never asked again. A denial needs no
            // handling: the alert's own guard degrades it to the toast.
            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            // Use a factory because NavigationViewModel now extends AndroidViewModel
            // with a multi-param constructor that AndroidViewModelFactory can't match.
            val viewModel: NavigationViewModel = viewModel(
                factory = NavigationViewModel.Factory
            )
            val depthViewModel: DepthViewModel = viewModel()
            val appSettings by viewModel.settings.collectAsState()

            // D4's report: a persisted route-engine id nothing claims fell back to the registry's
            // default, and the app says so at start rather than leaving the fallback silent.
            LaunchedEffect(Unit) {
                this@MainActivity.reportUnclaimedRouteEngineId(appSettings.routeEngineId)
            }

            // Feed the keeper. It owns the decision; the Activity only supplies what only the UI
            // can see — the persisted settings, the live speed, and (below) the touch stream.
            LaunchedEffect(appSettings) {
                powerKeeper.applySettings(appSettings)
            }
            LaunchedEffect(appSettings.gpsMode) {
                if (appSettings.gpsMode) {
                    // Freshness must come from the data, never from a push arriving: the navigation
                    // state re-publishes its cached speed on unrelated emissions, and the app's own
                    // staleness signal is what proves a reading is new.
                    combine(viewModel.navigationState, viewModel.gpsStale) { nav, stale ->
                        nav.speedKnots to !stale
                    }.collect { (speedKn, isNew) ->
                        powerKeeper.onSpeed(speedKn, isNewReading = isNew)
                    }
                } else {
                    // Demo mode has no speed truth — dragging the map *is* the interaction, and the
                    // lock-delay grace already covers that. Pan speed is deliberately not used as
                    // motion here, which also makes the demo behaviour deterministic.
                    powerKeeper.onSpeed(0f, isNewReading = false)
                }
            }

            // Apply the user's language choice by overriding the Compose context + configuration
            // so every stringResource below resolves in the chosen locale. "system" leaves the
            // device locale intact (English default, French on a fr device); "en"/"fr" force it.
            val localizedContext = rememberLocalizedContext(appSettings.languageCode)

            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalConfiguration provides localizedContext.resources.configuration
            ) {
                MaterialTheme(colorScheme = darkColorScheme(
                    primary = Color(0xFF1565C0) // Blue — matches AppConfig.uiAccent
                )) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        // --- Keep screen on at the Activity level (not the composable tree) ---
                        // Uses the window flag directly instead of View.keepScreenOn to avoid the
                        // DisposableEffect onDispose toggle glitch (brief false→true reset that
                        // trips Android 16's aggressive power management). Reset on dispose.
                        // The decision now comes from PowerKeeper (movement gate + interaction
                        // grace); this stays the single place the flag is ever mutated.
                        val powerState by powerKeeper.state.collectAsState()
                        DisposableEffect(powerState.screenOn) {
                            if (powerState.screenOn) {
                                window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                            } else {
                                window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                            }
                            onDispose {
                                window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                            }
                        }

                        // Restore persisted coastline + depth caches on first composition
                        LaunchedEffect(Unit) {
                            viewModel.initCache(this@MainActivity)
                            depthViewModel.initCache(this@MainActivity)
                        }

                        // ── Battery optimization prompt ──
                        // Primary trigger: a recording starts — that is when the exemption matters,
                        // and it fires before the problem rather than after it. Secondary: startup
                        // recovery, where an orphaned checkpoint proves a recording was live when the
                        // app was killed. The "already asked" flag now lives in AppSettings.
                        var showBatteryDialog by remember { mutableStateOf(false) }
                        val recording by TrackRecordingService.isRecording.collectAsState()
                        LaunchedEffect(recording, appSettings.batteryOptimizationPrompted) {
                            if (recording &&
                                BatteryExemption.shouldPrompt(this@MainActivity, appSettings)
                            ) {
                                showBatteryDialog = true
                            }
                        }
                        LaunchedEffect(Unit) {
                            if (BatteryExemption.shouldPrompt(this@MainActivity, appSettings)) {
                                val orphans = ykws.android.maro.data.track.TrackRepository(this@MainActivity)
                                    .recoverOrphanedCheckpoints()
                                if (orphans.isNotEmpty()) showBatteryDialog = true
                            }
                        }

                        if (showBatteryDialog) {
                            BatteryOptimizationDialog(
                                onOpenSettings = {
                                    showBatteryDialog = false
                                    viewModel.updateSettings { it.copy(batteryOptimizationPrompted = true) }
                                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                        data = Uri.parse("package:$packageName")
                                    }
                                    startActivity(intent)
                                },
                                onNotNow = {
                                    showBatteryDialog = false
                                    viewModel.updateSettings { it.copy(batteryOptimizationPrompted = true) }
                                }
                            )
                        }

                        MapScreen(
                            viewModel = viewModel,
                            depthViewModel = depthViewModel,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }

        // Start the foreground service to keep the app alive when backgrounded.
        // The service shows a persistent "Maro II — Ready" notification.
        startForegroundService(Intent(this, ykws.android.maro.data.track.TrackRecordingService::class.java))
    }

    /**
     * Reports the colour keys the runtime parser could not read, at start (R42). Empty — the ordinary
     * case — shows nothing at all, so a correct `maro.properties` stays silent and a broken one does
     * not. The keys come from the runtime parser's own channel ([AppConfig.unreadableColourKeys]), the
     * build no longer publishing them.
     */
    private fun reportUnreadableColourKeys() {
        val keys = ykws.android.maro.config.AppConfig.unreadableColourKeys
        if (keys.isEmpty()) return
        Toast.makeText(
            this,
            getString(R.string.startup_colour_value_unreadable, keys.joinToString(",")),
            Toast.LENGTH_LONG
        ).show()
    }

    /**
     * Reports a persisted route-engine id no registry row claims, at start (D4). A claimed id — the
     * ordinary case — shows nothing, so a correct choice stays silent and a stale one does not.
     */
    private fun reportUnclaimedRouteEngineId(id: String) {
        if (RouteEngineChoice.all.any { it.id == id }) return
        Toast.makeText(
            this,
            getString(R.string.startup_route_engine_id_unclaimed, id),
            Toast.LENGTH_LONG
        ).show()
    }

    /**
     * Reports the fine-tile store's **first reclamation** (D9) — the disk ceiling evicted the oldest
     * tiles. Raised mid-arm by the store's own `onReclaim`, already hopped to the UI thread there, and
     * once-guarded in the store, so a long session says it at most once. The toast is seen at once and
     * the notification survives the shade, so the event says itself both ways and neither replaces the
     * other.
     */
    private fun reportFineTileReclaimed() {
        Toast.makeText(
            this,
            getString(R.string.startup_fine_tile_reclaimed),
            Toast.LENGTH_LONG
        ).show()
        postFineTileReclaimNotification()
    }

    /**
     * Pushes the reclamation as a notification beside the toast (D9). It rides the app's one channel —
     * [TrackRecordingService.CHANNEL_ID], already silent — so no second entry appears in Android's
     * notification settings; it is auto-cancelling and non-ongoing, and it opens the app on the same
     * intent the recording banner uses. Posted only while notifications are enabled, so a denied or
     * revoked grant degrades to the toast alone rather than a silent refusal.
     */
    private fun postFineTileReclaimNotification() {
        val manager = NotificationManagerCompat.from(this)
        if (!manager.areNotificationsEnabled()) return
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, TrackRecordingService.CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.startup_fine_tile_reclaimed))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()
        manager.notify(FINE_TILE_RECLAIM_NOTIFICATION_ID, notification)
    }

    /**
     * Every touch — Compose or the osmdroid `MapView` — passes through here, so this is the one
     * choke point that can see all user interaction. The keeper only records a timestamp, so this
     * stays cheap enough to sit in the touch path; it never drives recomposition per event.
     */
    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        powerKeeper.onTouch()
        return super.dispatchTouchEvent(ev)
    }

}

/**
 * Battery optimization exemption dialog — shown once at recording start or startup recovery.
 */
@Composable
fun BatteryOptimizationDialog(
    onOpenSettings: () -> Unit,
    onNotNow: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onNotNow,
        title = { Text(stringResource(R.string.battery_opt_title)) },
        text = {
            Text(stringResource(R.string.battery_opt_message))
        },
        confirmButton = {
            TextButton(onClick = onOpenSettings) {
                Text(stringResource(R.string.battery_opt_open_settings))
            }
        },
        dismissButton = {
            TextButton(onClick = onNotNow) {
                Text(stringResource(R.string.battery_opt_not_now))
            }
        }
    )
}

/**
 * Builds a [Context] whose resources resolve in the chosen app language.
 *
 * The app only ships two languages, so the effective language is always exactly `fr` or `en`:
 * - `"fr"` / `"en"` → forced to that language.
 * - `"system"` (or any unknown value) → the **device** language when it is French, otherwise
 *   English. This makes "default to English" total: a device set to e.g. German gets a fully
 *   English experience (text *and* number formatting), not English text over German decimals.
 *
 * The result is a [ContextWrapper] **around the Activity** that returns locale-overridden
 * [Resources]. We deliberately wrap `base` (rather than returning `createConfigurationContext()`
 * directly) so `findActivity()` / `findOwner()` still reach the [ComponentActivity] up the
 * `baseContext` chain — the detached `createConfigurationContext` result instead crashes
 * `rememberLauncherForActivityResult` (the GPS permission launcher) with
 * "No ActivityResultRegistryOwner was provided".
 *
 * Keyed on [languageCode] and the current [Configuration] so it rebuilds on a language change
 * *and* on device config changes (e.g. rotation or the user changing the system language),
 * keeping orientation/size current. Switching is instant — no Activity recreation — because the
 * override flows through [LocalContext]/[LocalConfiguration] to every `stringResource` in the tree.
 */
@Composable
private fun rememberLocalizedContext(languageCode: String): Context {
    val base = LocalContext.current
    val configuration = LocalConfiguration.current
    return remember(languageCode, base, configuration) {
        // Collapse the choice to one of the two shipped languages; "system" honours the device
        // language only when it is French, else falls back to English.
        val effective = when (languageCode) {
            "fr", "en" -> languageCode
            else -> if (configuration.locales.get(0)?.language == "fr") "fr" else "en"
        }
        val localizedConfig = Configuration(configuration).apply { setLocale(Locale(effective)) }
        val localizedResources = base.createConfigurationContext(localizedConfig).resources
        object : ContextWrapper(base) {
            override fun getResources(): Resources = localizedResources
        }
    }
}
