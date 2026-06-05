package ykws.android.maro.data.model

/**
 * Complete coastline dataset for one region.
 *
 * Explicitly separates the mainland from islands, rather than storing them
 * as an undifferentiated flat list of segments. This avoids heuristic
 * re-detection of mainland/islands at render or query time.
 *
 * @property mainland The main continuous coastline polyline (open, water on right).
 * @property islands Zero or more island polylines (closed rings, water on right = exterior).
 * @property metadata Generation metadata.
 * @property regionId Identifier for this coastline region (e.g. "nice-frejus").
 * @property boundingBox Geographic extent of this coastline dataset.
 * @property zone300 Precomputed 300 m regulatory band geometry, or `null` if not
 *                   yet built (progressive load) or absent from a pre-feature cache.
 */
data class CoastlineData(
    val mainland: CoastlineSegment,
    val islands: List<CoastlineSegment> = emptyList(),
    val metadata: CoastlineMetadata,
    val regionId: String,
    val boundingBox: BoundingBox,
    val zone300: Zone300Data? = null
) {
    /** All segments (mainland + islands) as a flat list. Convenience for rendering. */
    val allSegments: List<CoastlineSegment> get() = listOf(mainland) + islands

    /** Total number of points across all segments. */
    val totalPointCount: Int get() = mainland.points.size + islands.sumOf { it.points.size }
}
