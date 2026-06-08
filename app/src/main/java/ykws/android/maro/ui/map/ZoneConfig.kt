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
 * zoneAutoRevealDistanceM=200       # Auto-reveal distance outside band (m)
 * zoneAutoRevealTimeS=20            # Auto-reveal time-to-band at SOG (s)
 * zoneRegulatorySpeedKn=5           # Regulatory speed inside band (kn)
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

    /** Distance (m) outside the 300 m band edge at which a hidden band auto-reveals (default for the in-app setting). */
    var zoneAutoRevealDistanceM = 200f
        private set

    /** Time (s) before reaching the 300 m band edge (at SOG) at which a hidden band auto-reveals (default for the in-app setting). */
    var zoneAutoRevealTimeS = 20
        private set

    /** Regulatory speed limit (kn) inside the 300 m band — at/below this, an auto-revealed band re-hides. */
    var zoneRegulatorySpeedKn = 5f
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
            props.getProperty("zoneAutoRevealDistanceM")?.toFloatOrNull()?.let {
                zoneAutoRevealDistanceM = it.coerceIn(50f, 500f)
            }
            props.getProperty("zoneAutoRevealTimeS")?.toIntOrNull()?.let {
                zoneAutoRevealTimeS = it.coerceIn(5, 120)
            }
            props.getProperty("zoneRegulatorySpeedKn")?.toFloatOrNull()?.let {
                zoneRegulatorySpeedKn = it.coerceIn(1f, 20f)
            }
        } catch (_: Exception) {
            // Keep defaults — properties file missing or corrupt.
        }
    }
}
