package ykws.android.maro.spatial.multipass

import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.markers.BBox
import ykws.android.maro.spatial.SpatialOperations

/**
 * **The corridor chain: a ribbon of small boxes along the pulled line.**
 *
 * A bounding box sizes a region by the line's **span**, so a U-shaped or dog-legged route pays for the
 * rectangle that contains it; the chain pays for a ribbon of side `2w` around the line itself. Its boxes
 * all stand on one [lattice] — the pre-condition that makes the seam between two of them an index
 * relation — and their centres are at most `w` apart along the line, so consecutive boxes overlap by at
 * least `w` and no cell below `w` can slip between them.
 *
 * **The ends are inside by construction**: the first centre is the start and the last is the aim, so the
 * two end discs and the berth carve always land in some box. The box is axis-aligned and the lattice is
 * lat/lon-aligned, so an oblique ribbon over-covers by up to `sqrt 2` — the price of a rotated box being
 * unavailable, named in the plan rather than hidden.
 */
internal fun corridorChain(line: List<LatLng>, halfWidthM: Double, lattice: WalkLattice): List<BBox> {
    if (line.size < 2 || halfWidthM <= 0.0) return emptyList()
    val start = line.first()
    val boxes = ArrayList<BBox>()
    boxes.add(lattice.boxAround(start, halfWidthM))
    var anchor = start
    var travelled = 0.0
    for (i in 1 until line.size) {
        val to = line[i]
        val leg = SpatialOperations.haversine(anchor, to)
        if (leg <= 0.0) {
            anchor = to
            continue
        }
        var along = halfWidthM - travelled
        while (along <= leg) {
            boxes.add(lattice.boxAround(pointAt(anchor, to, along / leg), halfWidthM))
            along += halfWidthM
        }
        travelled = (travelled + leg) % halfWidthM
        anchor = to
    }
    // The aim carries the last centre whatever the walk's own spacing left over, so the chain reaches it.
    val aim = line.last()
    if (SpatialOperations.haversine(anchor, aim) > 0.0 && aim != start) {
        boxes.add(lattice.boxAround(aim, halfWidthM))
    }
    return boxes
}

/** The point [fraction] of the way from [a] to [b], on the straight leg the chain walks. */
private fun pointAt(a: LatLng, b: LatLng, fraction: Double): LatLng = LatLng(
    a.latitude + (b.latitude - a.latitude) * fraction,
    a.longitude + (b.longitude - a.longitude) * fraction
)
