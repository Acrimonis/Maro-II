package ykws.android.maro.data.route

import ykws.android.maro.data.model.BoundingBox
import ykws.android.maro.data.model.RoutePoint

/**
 * The bake-time geometry the mesh builder consumes.
 *
 * Declared here, in `src/test`, so no bake-only interface ever ships in the APK: the prebake
 * implementation reads the coastline, regulated-zone and depth `.bin` files directly through their
 * serializers, which is what lets the whole builder run as a plain JVM test. Nothing in
 * `src/main` implements or imports this type.
 */
interface RouteGeometry {

    /** The mesh box — the app's own water definition, never a hardcoded extent. */
    val box: BoundingBox

    /**
     * Coastline geometry to constrain the triangulation with: the mainland first, then islands and
     * offshore hazards, each as an open or closed point list.
     */
    fun coastlineLines(): List<List<RoutePoint>>

    /**
     * Regulated-zone outer rings to respect with constraints.
     *
     * Boundaries only — the mesh carries no limit, so a zone contributes its shape and nothing else.
     */
    fun zoneRings(): List<List<RoutePoint>>

    /** @return true when the point is water, so the builder never places a node on land. */
    fun isWater(latitude: Double, longitude: Double): Boolean

    /** @return the depth (m below datum) at the point, so the 2.5 m gate can close shallow water. */
    fun depthM(latitude: Double, longitude: Double): Double
}
