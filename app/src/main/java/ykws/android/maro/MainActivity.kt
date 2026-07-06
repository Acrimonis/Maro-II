package ykws.android.maro

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.WindowInsetsController
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.lifecycle.viewmodel.compose.viewModel
import java.util.Locale
import ykws.android.maro.ui.map.NavigationViewModel
import ykws.android.maro.ui.map.DepthViewModel
import ykws.android.maro.ui.map.MapScreen
import ykws.android.maro.config.AppConfig

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable edge-to-edge rendering: content draws behind the system status bar
        // and navigation bar, which become transparent. Must be called before setContent().
        enableEdgeToEdge()

        // Dark window background: the light theme's default background shows through
        // transparent system bars, creating a grey gap. Set it to match the app's
        // dark dashboard background (#16213E) so system bar areas blend seamlessly.
        window.setBackgroundDrawable(ColorDrawable(AppConfig.uiSettingsToastBackground))

        // Set status bar icons to light (white) on the dark background.
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false  // false = light icons on dark
            isAppearanceLightNavigationBars = false
        }

        // Load zone gradient tunables from zone.properties before the UI composes.
        AppConfig.init(this)

        setContent {
            // Use a factory because NavigationViewModel now extends AndroidViewModel
            // with a multi-param constructor that AndroidViewModelFactory can't match.
            val viewModel: NavigationViewModel = viewModel(
                factory = NavigationViewModel.Factory
            )
            val depthViewModel: DepthViewModel = viewModel()
            val appSettings by viewModel.settings.collectAsState()

            // Apply the user's language choice by overriding the Compose context + configuration
            // so every stringResource below resolves in the chosen locale. "system" leaves the
            // device locale intact (English default, French on a fr device); "en"/"fr" force it.
            val localizedContext = rememberLocalizedContext(appSettings.languageCode)

            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalConfiguration provides localizedContext.resources.configuration
            ) {
                MaterialTheme(colorScheme = darkColorScheme(
                    primary = Color(0xFF1565C0) // Blue — matches AppConfig.uiSettingsAccent
                )) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        // --- Keep screen on at the Activity level (not the composable tree) ---
                        // Uses the window flag directly instead of View.keepScreenOn to avoid the
                        // DisposableEffect onDispose toggle glitch (brief false→true reset that
                        // trips Android 16's aggressive power management). Reset on dispose.
                        DisposableEffect(appSettings.keepScreenOn) {
                            if (appSettings.keepScreenOn) {
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

                        // ── Battery optimization prompt on startup (recovery scenario) ──
                        var showBatteryDialog by remember { mutableStateOf(false) }
                        val prefs = remember { getSharedPreferences(PREFS_NAME, MODE_PRIVATE) }
                        LaunchedEffect(Unit) {
                            val wasPrompted = prefs.getBoolean(KEY_BATTERY_OPT_PROMPTED, false)
                            if (!wasPrompted) {
                                // Check if recording was active — orphaned checkpoint implies it was
                                val orphans = ykws.android.maro.data.track.TrackRepository(this@MainActivity)
                                    .recoverOrphanedCheckpoints()
                                if (orphans.isNotEmpty()) {
                                    showBatteryDialog = true
                                }
                            }
                        }

                        if (showBatteryDialog) {
                            BatteryOptimizationDialog(
                                onOpenSettings = {
                                    showBatteryDialog = false
                                    prefs.edit().putBoolean(KEY_BATTERY_OPT_PROMPTED, true).apply()
                                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                        data = Uri.parse("package:$packageName")
                                    }
                                    startActivity(intent)
                                },
                                onNotNow = {
                                    showBatteryDialog = false
                                    prefs.edit().putBoolean(KEY_BATTERY_OPT_PROMPTED, true).apply()
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

    companion object {
        private const val PREFS_NAME = "maro_battery_prefs"
        const val KEY_BATTERY_OPT_PROMPTED = "battery_opt_prompted"
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
