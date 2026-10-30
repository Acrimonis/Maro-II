package ykws.android.maro.spatial.multipass

import ykws.android.maro.data.model.LatLng
import ykws.android.maro.spatial.SpatialOperations
import java.util.Random

/** One named pair of route ends. */
internal class EndPair(val name: String, val a: LatLng, val b: LatLng)

/**
 * **The seeded survey's pairs** — open sea round the Lérins and the Cap d'Antibes, 3–18 km apart, drawn from one
 * seed so the direction-symmetry survey and the bend harness route the same [COUNT] pairs.
 */
internal object RouteSurveyPairs {

    const val COUNT = 24
    private const val SEED = 20261020L

    /** [count] seeded pairs of open sea, 3–18 km apart, over the baked region's own sea. */
    fun draw(world: BakedRegionWorld, count: Int = COUNT): List<EndPair> {
        val rng = Random(SEED)
        val b = world.regionBounds
        // The coast the Lérins and the Cap d'Antibes stand on, not the whole 6 NM envelope: a pair across a
        // continent of open sea would only measure the empty case again.
        val latS = maxOf(b.latSouth, 43.43)
        val latN = minOf(b.latNorth, 43.70)
        val lonW = maxOf(b.lonWest, 6.93)
        val lonE = minOf(b.lonEast, 7.32)
        fun point(): LatLng = LatLng(latS + rng.nextDouble() * (latN - latS), lonW + rng.nextDouble() * (lonE - lonW))
        val out = ArrayList<EndPair>()
        var tries = 0
        while (out.size < count && tries++ < 20_000) {
            val a = point()
            val c = point()
            val km = SpatialOperations.haversine(a, c) / 1000.0
            if (km < 3.0 || km > 18.0) continue
            if (refusal(world, a) != null || refusal(world, c) != null) continue
            out += EndPair("random ${out.size + 1}", a, c)
        }
        return out
    }

    /** Why [p] is not a fair end — land, a coast hug, a shoal — or `null` where it is open deep water. */
    fun refusal(world: BakedRegionWorld, p: LatLng): String? {
        if (!world.isWater(p.latitude, p.longitude)) return "$p is on land"
        val coastM = world.distanceToCoastM(p.latitude, p.longitude)
        if (coastM < 150.0) return "$p is ${"%.1f".format(java.util.Locale.ROOT, coastM)} m off the coast"
        val depth = world.depthAt(p.latitude, p.longitude)
        if (depth.hasData && depth.depthM < 6f) return "$p reads ${depth.depthM} m deep"
        return null
    }
}
