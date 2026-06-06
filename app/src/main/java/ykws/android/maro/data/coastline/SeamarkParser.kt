package ykws.android.maro.data.coastline

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import ykws.android.maro.data.model.HazardType
import ykws.android.maro.data.model.PointHazard

/**
 * Maps OpenStreetMap **seamark** nodes (OpenSeaMap tagging) to isolated offshore
 * [PointHazard]s.
 *
 * ## Why
 *
 * The coastline pipeline ingests only continuous `natural=coastline` land. Isolated
 * point dangers — La Fourmigue, the cardinal-marked shoals (Batéguier, Chrétienne,
 * Vaquette, Les Moines), wrecks — are separate `seamark:type=*` point features. They
 * are fetched in the **same Overpass call** as the coastline and ringed into the
 * island set ([HazardRings]). This replaces the old hand-typed `HazardSeeds` (guessed
 * coordinates, 2 of 4 ~1.3 km off) and the never-confirmed Shom WFS placeholder:
 * OSM seamark data is itself sourced from the official Shom/NGA charts (see e.g. the
 * Fourmigue node's `ref:inspire` / `source` tags).
 *
 * ## What counts as a danger
 *
 * Only [DANGER_TYPES] are kept — the marks flagging a thing you can hit, or a shoal
 * under the surface. Harbour/approach **lights** (`light_minor`/`light_major`) and
 * channel marks (`*_lateral`, buoys, `small_craft_facility`, …) are deliberately
 * **excluded**: they are navigation aids, not isolated dangers, and would otherwise
 * litter the harbours with phantom obstructions. Emergent rocks/islets are *not*
 * fetched here — those are already carried by `natural=coastline`.
 *
 * Pure & framework-free (no network, no Android) → directly unit-testable.
 */
object SeamarkParser {

    /**
     * `seamark:type` values treated as isolated dangers. Cardinal & isolated-danger
     * beacons are the "balises" marking high shoals; rock/obstruction/wreck are the
     * dangers themselves. Insertion order is preserved for a stable Overpass regex.
     */
    val DANGER_TYPES: Set<String> = linkedSetOf(
        "beacon_isolated_danger",
        "beacon_cardinal",
        "rock",
        "obstruction",
        "wreck"
    )

    /**
     * Parses Overpass `node` elements into hazards, keeping only [DANGER_TYPES].
     * Anything that is not a node, or lacks a danger `seamark:type`, lat or lon, is
     * skipped. Never throws.
     */
    fun parse(elements: List<JsonObject>): List<PointHazard> {
        val out = ArrayList<PointHazard>()
        for (el in elements) {
            if (str(el["type"]) != "node") continue
            val tags = el["tags"] as? JsonObject ?: continue
            val seamarkType = str(tags["seamark:type"]) ?: continue
            val hazardType = classify(seamarkType) ?: continue
            val lat = (el["lat"] as? JsonPrimitive)?.doubleOrNull ?: continue
            val lon = (el["lon"] as? JsonPrimitive)?.doubleOrNull ?: continue
            val name = str(tags["seamark:name"]) ?: str(tags["name"]) ?: ""
            out.add(PointHazard(lat = lat, lon = lon, name = name, type = hazardType))
        }
        return out
    }

    /** Maps a `seamark:type` to a [HazardType], or `null` when it is not a danger. */
    fun classify(seamarkType: String): HazardType? = when (seamarkType) {
        "beacon_isolated_danger", "beacon_cardinal", "obstruction", "wreck" -> HazardType.ISOLATED_DANGER
        "rock" -> HazardType.ROCK
        else -> null
    }

    private fun str(e: JsonElement?): String? =
        (e as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
}
