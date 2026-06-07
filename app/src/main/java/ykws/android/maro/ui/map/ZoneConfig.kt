package ykws.android.maro.ui.map

import android.content.Context
import java.util.Properties

/**
 * Runtime loader for [zone.properties](app/src/main/assets/zone.properties).
 *
 * Reads the three gradient tunables from the properties file bundled in assets.
 * If the file is missing or any value cannot be parsed, the corresponding
 * hardcoded default is used so the app never crashes on a bad config.
 *
 * Call [init] once from [MainActivity] before the UI composes.
 *
 * ## Properties
 *
 * ```
 * distanceToZoneGradientText=600    # Text fade range (m)
 * distanceToZoneGradientColor=300   # Tile fade range outward (m)
 * distanceToZoneGradientTransp=33   # Outside-zone transparency (0–100)
 * ```
 */
object ZoneConfig {

    /** Distance (m) from zone boundary at which text reaches near-invisible alpha. */
    var distanceToZoneGradientText = 600f
        private set

    /** Distance (m) outward from zone boundary at which tile reaches plain cardBg. */
    var distanceToZoneGradientColor = 300f
        private set

    /** Transparency percentage (0–100) applied to the zone colour when outside the zone. */
    var distanceToZoneGradientTransp = 33
        private set

    /**
     * Load tunables from [zone.properties](app/src/main/assets/zone.properties).
     * Must be called once (e.g. from [MainActivity.onCreate]) before the UI
     * reads the values. Missing or unparseable entries silently keep the default.
     */
    fun init(context: Context) {
        try {
            val props = Properties()
            context.assets.open("zone.properties").use { stream ->
                props.load(stream)
            }

            props.getProperty("distanceToZoneGradientText")?.toFloatOrNull()?.let {
                distanceToZoneGradientText = it.coerceIn(100f, 2000f)
            }
            props.getProperty("distanceToZoneGradientColor")?.toFloatOrNull()?.let {
                distanceToZoneGradientColor = it.coerceIn(50f, 1000f)
            }
            props.getProperty("distanceToZoneGradientTransp")?.toIntOrNull()?.let {
                distanceToZoneGradientTransp = it.coerceIn(0, 100)
            }
        } catch (_: Exception) {
            // Keep defaults — properties file missing or corrupt.
        }
    }
}
