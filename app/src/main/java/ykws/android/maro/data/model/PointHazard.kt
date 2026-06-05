package ykws.android.maro.data.model

/**
 * An isolated offshore point hazard that is **not** part of the continuous
 * coastline (trait de côte) and therefore absent from the OSM
 * `natural=coastline` vectors.
 *
 * Examples in the Nice–Fréjus zone:
 *  - **Phare de la Fourmigue** — a 16 m concrete turret on an isolated reef in
 *    Golfe Juan, between Cap d'Antibes and the Lérins.
 *  - **Basses de la Chrétienne** — submerged rocks off Agay.
 *  - **Sec de la Tradelière** — a shallow near the Lérins.
 *
 * The Shom stores these as *point* features (aides à la navigation /
 * `danger_isolé` / balisage), so the wheel/ray-cast engine — which only rolls
 * along continuous land polygons — misses them entirely. We ingest each point,
 * buffer it into a small closed ring ([bufferRadiusM]) and merge it into the
 * island set so every downstream consumer (spatial index, isOnWater ray-cast,
 * 300 m band) treats it as land/obstruction.
 *
 * @property lat Latitude in WGS84 decimal degrees.
 * @property lon Longitude in WGS84 decimal degrees.
 * @property name Human-readable label (e.g. "Phare de la Fourmigue"), may be blank.
 * @property type Classified hazard type, drives the default buffer radius.
 * @property bufferRadiusM Radius (m) of the micro-circle to union into the land mass.
 *                         This is a **safety** choice, not a measurement.
 */
data class PointHazard(
    val lat: Double,
    val lon: Double,
    val name: String = "",
    val type: HazardType = HazardType.ISOLATED_DANGER,
    val bufferRadiusM: Double = type.defaultRadiusM
)

/**
 * Hazard category with a default safety buffer radius. The radius reflects how
 * much surrounding water should be treated as no-go around the feature, not the
 * physical size of the object.
 */
enum class HazardType(val defaultRadiusM: Double) {
    /** Lighthouse / lit turret (phare, tourelle, feu). */
    LIGHT(15.0),

    /** Unlit beacon / mark (balise, espar). */
    BEACON(12.0),

    /** Isolated danger mark / charted isolated danger (danger_isolé). */
    ISOLATED_DANGER(25.0),

    /** Bare rock or reef awash (roche, sec, écueil). */
    ROCK(20.0),

    /** Unclassified — conservative default. */
    OTHER(20.0)
}
