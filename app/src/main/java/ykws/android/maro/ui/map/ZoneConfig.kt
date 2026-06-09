package ykws.android.maro.ui.map

import android.content.Context
import android.graphics.Color
import ykws.android.maro.data.model.DepthSource
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
 * lowDepthWarningMinOpacityPct=25   # Pink warning opacity at threshold (0–100)
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

    /** Minimum opacity (%, 0–100) of the low-depth warning at the threshold depth; 100 % at the shoreline fades to this. */
    var lowDepthWarningMinOpacityPct = 25
        private set

    /** ARGB colour for NoData / above-datum cells on the depth colour map.
     *  Default `0xFFCCCCCC` (light grey). Set via `nodata.color` in zone.properties. */
    var nodataColor: Int = 0xFFCCCCCC.toInt()
        private set

    /** Isobath line colour per data source (ARGB int); a source with no entry falls back to [isobarColorDefault]. */
    private val isobarColors = hashMapOf(
        DepthSource.LITTO3D to 0xFF1B5E20.toInt(), // dark green (Material 900)
        DepthSource.EMODNET to 0xFF00008B.toInt(), // dark blue
    )

    /** Fallback isobath colour for any source without an explicit entry. */
    var isobarColorDefault = 0xFF37474F.toInt() // muted blue-grey
        private set

    /** Per-source extra stroke width (px) added on top of the major/minor base; default 0. */
    private val isobarWidthBonuses = hashMapOf(
        DepthSource.LITTO3D to 1f,  // Litto3D (precise nearshore) reads a touch bolder
        DepthSource.EMODNET to -1f, // EMODnet (coarse deep) reads thinner
    )

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
            props.getProperty("lowDepthWarningMinOpacityPct")?.toIntOrNull()?.let {
                lowDepthWarningMinOpacityPct = it.coerceIn(0, 100)
            }
            props.getProperty("nodata.color")?.let { parseColorOrNull(it) }?.let {
                nodataColor = it
            }
            for (src in DepthSource.entries) {
                val key = src.name.lowercase()
                props.getProperty("isobar.color.$key")?.let { parseColorOrNull(it) }?.let { isobarColors[src] = it }
                props.getProperty("isobar.width.$key")?.toFloatOrNull()?.let { isobarWidthBonuses[src] = it.coerceIn(-4f, 6f) }
            }
            props.getProperty("isobar.color.default")?.let { parseColorOrNull(it) }?.let {
                isobarColorDefault = it
            }
        } catch (_: Exception) {
            // Keep defaults — properties file missing or corrupt.
        }
    }

    /** Isobath stroke colour (ARGB) for a data source, from zone.properties (defaults baked in). */
    fun isobarColor(source: DepthSource): Int = isobarColors[source] ?: isobarColorDefault

    /** Extra isobath stroke width (px) for a data source (0 if unset). */
    fun isobarWidthBonus(source: DepthSource): Float = isobarWidthBonuses[source] ?: 0f

    private fun parseColorOrNull(s: String): Int? =
        try { Color.parseColor(s.trim()) } catch (_: Exception) { null }
}
