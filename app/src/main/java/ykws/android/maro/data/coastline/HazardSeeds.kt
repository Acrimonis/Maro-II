package ykws.android.maro.data.coastline

import ykws.android.maro.data.model.HazardType
import ykws.android.maro.data.model.PointHazard

/**
 * Hard-coded fallback list of known isolated offshore hazards in the
 * Nice–Fréjus zone.
 *
 * These are **always** merged into the land mass, independent of the Shom WFS
 * ([ShomAtonClient]). They guarantee the most important hazards are present even
 * if the WFS is unreachable, its layer name is wrong, or the device is offline.
 * The WFS, once confirmed, adds further coverage on top (deduplicated against
 * these seeds by proximity in [CoastlineGenerator]).
 *
 * Coordinates are decimal WGS84. The Fourmigue is precise (charted turret); the
 * other two are reef/plateau centres accurate to ~100 m — fine given the safety
 * buffer radius.
 */
object HazardSeeds {

    /** Phare/Tourelle de la Fourmigue — Golfe Juan (43°32.4′N, 7°05.0′E). */
    val FOURMIGUE = PointHazard(
        lat = 43.5400, lon = 7.0833,
        name = "Phare de la Fourmigue",
        type = HazardType.LIGHT
    )

    /** Basses de la Chrétienne — between Agay and Anthéor (43°25′38″N, 6°54′35″E). */
    val BASSES_CHRETIENNE = PointHazard(
        lat = 43.4272, lon = 6.9097,
        name = "Basses de la Chrétienne",
        type = HazardType.ISOLATED_DANGER
    )

    /** Sec / Îlot de la Tradelière — ~150 m E of Sainte-Marguerite (Lérins). */
    val TRADELIERE = PointHazard(
        lat = 43.5240, lon = 7.0610,
        name = "Sec de la Tradelière",
        type = HazardType.ROCK
    )

    /**
     * West-cardinal danger off the **NW of Île Sainte-Marguerite** — the rocky
     * plateau du Batéguier / Plateau de la Jonquière, a shoal (< 2 m) fronting the
     * island's NW extremity. Position from OSM `seamark:type=beacon_cardinal`
     * (category west) at 43°31.6′N, 7°01.8′E. This charted danger sits off the
     * continuous trait de côte, so — like La Fourmigue — it was invisible to the
     * spatial engine until seeded here.
     */
    val BATEGUIER = PointHazard(
        lat = 43.52655, lon = 7.03046,
        name = "Plateau du Batéguier (NO Sainte-Marguerite)",
        type = HazardType.ISOLATED_DANGER
    )

    /** All seeds for the Nice–Fréjus region. */
    val NICE_FREJUS: List<PointHazard> =
        listOf(FOURMIGUE, BASSES_CHRETIENNE, TRADELIERE, BATEGUIER)
}
