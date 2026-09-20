package ykws.android.maro.data.model

/**
 * **An engine's own readings about the route it answered — its dossier, not the plan's.**
 *
 * [RouteResult.Success] carries what *any* engine must answer: the polyline, the time of each drawn
 * leg, the length, the duration, whether the line lies in the coastal band, whether the aim resolved
 * elsewhere, and the zones a forced crossing entered. Everything an engine only knows because of the
 * machine it uses lives here instead, so a second engine can answer the same question without
 * borrowing — or being asked for — the first one's vocabulary.
 *
 * The readings of one engine are its own type (the mesh's is
 * [`ykws.android.maro.spatial.mesh.RouteMeshDetails`]), because the numbers are not comparable across
 * machines: a mesh engine counts mesh vertices, and an engine that walks a visibility graph counts
 * something else entirely. What the base type carries is the one reading the comparison in §14.5 of
 * the parked design needs from **every** engine, so that a seam can be measured through rather than
 * merely believed in.
 */
interface RouteEngineDetails {

    /**
     * **How many scoring steps the engine expanded while it answered** — the mesh engine's own A*
     * count, and whatever that machine's analogue is for another.
     *
     * It is a cost, in the only unit every engine has, and it is reported beside the wall clock so
     * that a difference between two engines can be read as work rather than only as time. An engine
     * that does not count leaves it at zero, which is the honest reading of "not measured" — a count
     * invented to fill the field would be worse than the absence.
     */
    val nodesExpanded: Int get() = 0
}
