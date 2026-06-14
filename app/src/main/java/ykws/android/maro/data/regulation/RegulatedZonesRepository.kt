package ykws.android.maro.data.regulation

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Loads the prebaked [RegulatedZoneSet] from the bundled APK asset.
 *
 * The asset is baked offline by `tools/bake-regulated-zones.bat` and placed at
 * `data/app-assets/regulated-zones/<region>.bin`, which the build system packages
 * into the APK as `assets/regulated-zones/<region>.bin` (see `app/build.gradle.kts`
 * `assets.srcDir`).
 *
 * If no baked asset exists (never baked), [zoneSet] stays `null` — the app simply
 * draws no regulated zones overlay (graceful degradation).
 *
 * Call [load] once at startup (e.g. from [CoastlineViewModel.initCache] or inside
 * a `LaunchedEffect` in the composable).
 */
class RegulatedZonesRepository(
    private val regionId: String = "nice-frejus"
) {
    private val _zoneSet = MutableStateFlow<RegulatedZoneSet?>(null)
    val zoneSet: StateFlow<RegulatedZoneSet?> = _zoneSet.asStateFlow()

    /** Relative asset path for the prebaked regulated zones binary. */
    private fun assetPath(): String = "regulated-zones/$regionId.bin"

    /**
     * Load the prebaked asset from the APK bundle.
     *
     * Best-effort: if the asset is missing, unreadable, or malformed, [zoneSet]
     * stays `null` and no regulated zones overlay is drawn.
     */
    fun load(context: Context) {
        try {
            context.assets.open(assetPath()).use { stream ->
                val bytes = stream.readBytes()
                val parsed = RegulatedZoneSerializer.deserialize(bytes)
                _zoneSet.value = parsed
            }
        } catch (_: Exception) {
            // No baked asset or corrupt — regulated zones overlay is simply absent.
            _zoneSet.value = null
        }
    }
}
