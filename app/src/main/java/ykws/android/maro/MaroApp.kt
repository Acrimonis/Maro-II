package ykws.android.maro

import android.app.Application
import android.content.Context
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.markers.AutoMarkerManager
import ykws.android.maro.data.markers.UserMarkerRepository
import ykws.android.maro.data.settings.SettingsManager
import ykws.android.maro.data.track.TrackRepository
import java.io.File

/**
 * The process's composition root — the one owner of everything that must exist exactly once per
 * process: the packaged configuration, the settings store and the two durable repositories.
 *
 * It exists for three reasons that were defects without it. The settings store used to be built
 * once by the UI and once by the foreground service, so a threshold changed in Settings never
 * reached the recorder; the track and marker repositories were built three to five times, so the
 * marker store's write mutex guarded nothing across writers; and [AppConfig.init] ran only from the
 * Activity, so a service recreated by the system without one read code defaults instead of the
 * packaged properties.
 *
 * A reader takes an instance through [of] and falls back to constructing its own only when the
 * application is not this class — a plain-JVM test with a stub context.
 */
class MaroApp : Application() {

    /** The one settings store: every reader and writer, UI and service alike, sees one flow. */
    val settingsManager: SettingsManager by lazy {
        SettingsManager(this, AppConfig.zoneAutoRevealDistanceM, AppConfig.zoneAutoRevealTimeS)
    }

    /** The one marker store, so its write mutex really serialises the UI and the recorder. */
    val userMarkerRepository: UserMarkerRepository by lazy { UserMarkerRepository(this) }

    /** The one track store, sharing the marker store above for its delete cascade. */
    val trackRepository: TrackRepository by lazy {
        TrackRepository(File(filesDir, TrackRepository.TRACKS_DIR_NAME), userMarkerRepository)
    }

    /** The auto-marker lifecycle, over the shared marker store. */
    val autoMarkerManager: AutoMarkerManager by lazy { AutoMarkerManager(userMarkerRepository) }

    override fun onCreate() {
        super.onCreate()
        // Before any consumer in this process — the Activity, the service or a receiver — can read a
        // tunable, so a service-only restart sees the packaged properties too.
        AppConfig.init(this)
    }

    companion object {
        /** The root behind [context], or null where the application is not this class (tests). */
        fun of(context: Context): MaroApp? = context.applicationContext as? MaroApp
    }
}
