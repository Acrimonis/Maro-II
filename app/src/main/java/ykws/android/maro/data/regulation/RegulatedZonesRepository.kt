package ykws.android.maro.data.regulation

import ykws.android.maro.BuildConfig
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.InputStream

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
 * The load does its file IO and parsing off the main thread ([loadFrom] wraps the whole body in
 * [Dispatchers.IO]), so a cold start never blocks the composition.
 *
 * Call [load] once at startup, from a coroutine (the map's loader `LaunchedEffect` or a
 * `viewModelScope.launch`); the call is `suspend` and returns once the set has been published.
 */
class RegulatedZonesRepository(
    private val regionId: String = BuildConfig.REGION_ID
) {
    private val _zoneSet = MutableStateFlow<RegulatedZoneSet?>(null)
    val zoneSet: StateFlow<RegulatedZoneSet?> = _zoneSet.asStateFlow()

    /**
     * **Whether the first load has answered** — `true` once [loadFrom] has returned, a clean read, an absent
     * asset and a corrupt one alike, so a reader waiting for the zone list (the route pre-warm, which keys
     * its tiles on the zones) can tell "no zones" from "not loaded yet". `false` until then.
     */
    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()
    private val loadMutex = Mutex()

    /** Relative asset path for the prebaked regulated zones binary. */
    private fun assetPath(): String = "regulated-zones/$regionId.bin"

    /**
     * Load the prebaked asset from the APK bundle, off the main thread.
     *
     * Best-effort: if the asset is missing, unreadable, or malformed, [zoneSet] stays `null` and no
     * regulated zones overlay is drawn.
     */
    suspend fun load(context: Context) = loadFrom { path -> context.assets.open(path) }

    /**
     * The load's own body over an injected asset opener — the seam a JVM test stands in for, since
     * `Context.assets` is unreachable without a device. A missing, empty or corrupt asset leaves
     * [zoneSet] `null` as much as a clean read leaves it set.
     */
    internal suspend fun loadFrom(openAsset: (String) -> InputStream) = loadMutex.withLock {
        if (_zoneSet.value != null) {
            _loaded.value = true
            return@withLock
        }
        withContext(Dispatchers.IO) {
            try {
                openAsset(assetPath()).use { stream ->
                    val bytes = stream.readBytes()
                    val parsed = RegulatedZoneSerializer.deserialize(bytes)
                    val overrides = readOverrides(openAsset)
                    val applied = RegulationSpeedOverrides.apply(parsed.zones, overrides)
                    _zoneSet.value = parsed.copy(
                        zones = applied,
                        metadata = parsed.metadata.copy(totalZones = applied.size)
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // No baked asset or corrupt — regulated zones overlay is simply absent.
                _zoneSet.value = null
            }
        }
        _loaded.value = true
    }

    /**
     * The hand-edited speed overrides and ignore flags shipped as the
     * `zones.properties` asset next to `maro.properties` (see
     * [RegulationSpeedOverrides]). Best-effort: a missing or unreadable file
     * yields no overrides, so the raw baked set is used as-is.
     */
    private fun readOverrides(openAsset: (String) -> InputStream): RegulationSpeedOverrides.Overrides = try {
        openAsset(RegulationSpeedOverrides.FILE_NAME).use {
            RegulationSpeedOverrides.load(it)
        }
    } catch (_: Exception) {
        RegulationSpeedOverrides.Overrides(emptyMap(), emptyMap())
    }
}
