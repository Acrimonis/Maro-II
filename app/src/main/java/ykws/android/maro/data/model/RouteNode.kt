package ykws.android.maro.data.model

/**
 * One node of the navigation mesh: its point plus the ids of the edges incident to it.
 *
 * Object-shaped, so it lives only in the mesh builder and the serializer — what the file and the
 * search read is the packed array form in [RouteMeshArrays].
 */
data class RouteNode(
    val point: RoutePoint,
    val edges: List<Int> = emptyList()
)
