package ykws.android.maro.ui.map

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.AtomicFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import ykws.android.maro.BuildConfig
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.temperature.OpenMeteoClient
import ykws.android.maro.data.temperature.TemperatureCache
import ykws.android.maro.data.temperature.TemperatureEngine
import ykws.android.maro.data.temperature.TemperatureGrid
import ykws.android.maro.data.temperature.TemperatureProblem
import ykws.android.maro.data.temperature.TemperatureSource
import ykws.android.maro.data.temperature.TemperatureState
import ykws.android.maro.data.temperature.TemperatureTiming
import java.io.File
import java.io.IOException

/**
 * Owns the temperature engine for the map screen: builds it from `maro.properties`, restores and saves its
 * cache, and keeps the view the user picked by tapping.
 *
 * The cache file is written through [AtomicFile], so a kill mid-write leaves the previous file whole, and by
 * one writer fed through a conflated channel, so snapshots land in the order they were taken and a burst of
 * changes costs one write of the latest.
 *
 * The picked view is sticky for the life of this ViewModel — the app run, surviving rotation — and a new
 * run starts from the default (water on water, air on land).
 */
internal class TemperatureViewModel(application: Application) : AndroidViewModel(application) {

    private val cacheFile = AtomicFile(File(application.filesDir, CACHE_FILE_NAME))
    private val connectivity = application.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    private val pendingWrites = Channel<String>(Channel.CONFLATED)

    private val airGrid = TemperatureGrid(
        TemperatureSource.AIR, BuildConfig.TRACK_ORIGIN_LAT, BuildConfig.TRACK_ORIGIN_LON,
        AppConfig.temperatureAirCellSideM
    )
    private val waterGrid = TemperatureGrid(
        TemperatureSource.WATER, BuildConfig.TRACK_ORIGIN_LAT, BuildConfig.TRACK_ORIGIN_LON,
        AppConfig.temperatureWaterCellSideM
    )

    /** What a stored cache must have been written under, or it is discarded. */
    private val signature =
        "air=${AppConfig.temperatureAirCellSideM};water=${AppConfig.temperatureWaterCellSideM};" +
            "origin=${BuildConfig.TRACK_ORIGIN_LAT},${BuildConfig.TRACK_ORIGIN_LON}"

    private val _notices = MutableSharedFlow<TemperatureNotice>(extraBufferCapacity = 4)

    /** What the user is told by a pill on the notice host: a refresh begun, a request that went wrong, no connection to refresh on. */
    val notices: SharedFlow<TemperatureNotice> = _notices


    private val cache = TemperatureCache { source ->
        when (source) {
            TemperatureSource.AIR -> AppConfig.temperatureAirExpiryMin
            TemperatureSource.WATER -> AppConfig.temperatureWaterExpiryMin
        } * 60_000L
    }

    /** Set by the map screen once the coastline can answer; until then nothing counts as water. */
    @Volatile
    private var waterTest: (Double, Double) -> Boolean = { _, _ -> false }

    private val engine = TemperatureEngine(
        scope = viewModelScope,
        fetcher = OpenMeteoClient(),
        airGrid = airGrid,
        waterGrid = waterGrid,
        cache = cache,
        isWater = { lat, lon -> waterTest(lat, lon) },
        isOnline = ::isOnline,
        onCacheChanged = ::persist,
        onProblem = { _notices.tryEmit(TemperatureNotice.Problem(it)) },
        timing = TemperatureTiming(
            settleMs = AppConfig.temperatureSettleMs,
            backoffS = AppConfig.temperatureBackoffS,
            waterMaxTries = AppConfig.temperatureWaterMaxTries,
            waterWindowFactor = AppConfig.temperatureWaterWindowFactor,
            waterWindowMaxFactor = AppConfig.temperatureWaterWindowMaxFactor,
            tickMs = AppConfig.temperatureRecheckS * 1_000L
        )
    )

    val state: StateFlow<TemperatureState> = engine.state

    private val _preferredView = MutableStateFlow<TemperatureView?>(null)

    /** The view the user tapped to, or null while the default applies. */
    val preferredView: StateFlow<TemperatureView?> = _preferredView

    init {
        // A small file read once; the cache must be loaded before the engine's first check.
        runCatching {
            cache.restore(String(cacheFile.readFully(), Charsets.UTF_8), signature, System.currentTimeMillis(), ::sourceOfKey)
        }
        viewModelScope.launch(Dispatchers.IO) {
            for (text in pendingWrites) write(text)
        }
    }

    /**
     * Feeds the water/land answer the engine uses. [isWaterOrNull] is the containment test without the
     * 6 NM limit; unknown (null) counts as land.
     */
    fun bindWaterTest(isWaterOrNull: (Double, Double) -> Boolean?) {
        waterTest = { lat, lon -> isWaterOrNull(lat, lon) == true }
        engine.waterTestChanged()
    }

    fun configure(enabled: Boolean) {
        engine.setEnabled(enabled)
    }

    fun onPosition(latitude: Double, longitude: Double) = engine.onPosition(latitude, longitude)

    /** Long press on the tile (which only shows while the feature is on): mark everything outdated and fetch once. */
    fun refreshNow() {
        _notices.tryEmit(TemperatureNotice.Refreshing)
        engine.refreshNow()
    }

    /** A tap: switch views, when there is a water view to switch to. */
    fun toggleView() {
        tappedTemperatureView(engine.state.value, _preferredView.value)?.let { _preferredView.value = it }
    }

    private fun isOnline(): Boolean {
        val manager = connectivity ?: return true
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun persist(source: TemperatureCache) {
        pendingWrites.trySend(source.encode(signature))
    }

    /** Replaces the file whole or not at all; a failed write keeps the previous file. */
    private fun write(text: String) {
        val out = runCatching { cacheFile.startWrite() }.getOrNull() ?: return
        try {
            out.write(text.toByteArray(Charsets.UTF_8))
            cacheFile.finishWrite(out)
        } catch (e: IOException) {
            cacheFile.failWrite(out)
        }
    }

    private fun sourceOfKey(key: String): TemperatureSource? = when (key.substringBefore(':')) {
        "air" -> TemperatureSource.AIR
        "water" -> TemperatureSource.WATER
        else -> null
    }

    private companion object {
        const val CACHE_FILE_NAME = "temperature_cache.json"
    }
}
