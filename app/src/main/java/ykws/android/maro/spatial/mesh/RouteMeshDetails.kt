package ykws.android.maro.spatial.mesh

import ykws.android.maro.data.model.RouteEngineDetails

/**
 * **The mesh engine's own readings about the route it answered** — its dossier, and nothing the plan
 * needs.
 *
 * Every number here exists because the incumbent walks a prebaked fabric: it has mesh vertices, a
 * chain to price the drawn line against, a shortcut pass over that chain and a fillet over what
 * survives. A visibility graph has none of them, which is why they live with the engine that
 * produces them rather than on [`ykws.android.maro.data.model.RouteResult.Success`] beside the
 * polyline and the clock any engine must answer with.
 *
 * They are instrumentation and stay instrumentation: the trajectory probe reads them to judge a
 * pass, and no reader of the plan — the trip figure, the panel, the saved track — has a use for one.
 */
internal data class RouteMeshDetails(
    /**
     * The route's retained mesh vertices, for a caller that wants them: the vertices the line
     * actually turns at, so a smoothed route's own arc points have no node here and the two lists
     * are not parallel.
     */
    val nodeIndices: List<Int> = emptyList(),
    /**
     * The seconds the search itself accumulated along its own chain, the berth's courtesy excluded.
     * It is the ceiling [ykws.android.maro.data.model.RouteResult.Success.durationSec] is asserted
     * against: the drawn line may be cheaper than the chain the search priced, never dearer, and the
     * two are reported together so that a pass which changed the line without re-pricing it is a
     * reading rather than a suspicion.
     */
    val pricedSec: Double = 0.0,
    /**
     * **The limit the engine produced for each drawn leg** — one per leg, in the polyline's own
     * order, each inherited from the chain edge that leg was cut from, a leg spanning several taking
     * the most restrictive in force over them.
     *
     * These are the values the engine hands the plan's clock
     * ([`ykws.android.maro.spatial.RoutePlanTiming.drawnLegSeconds`]) — the clock takes them as input
     * and never asks where they came from, which is what lets an engine whose path is not a chain of
     * edges answer with limits of its own. Published here so that the supply is a reading rather than
     * a claim about the code.
     */
    val legLimitKn: List<Double> = emptyList(),
    /**
     * The chain's own vertex count **before the shortcut pass** ([`RouteShortcut`]) — what the line
     * was, against [nodeIndices]' own count, which is what it became. Reported because "the line
     * straightened" is a claim that has to read as a number: the pass's whole job is to drop
     * vertices, and only the pair of counts says whether it dropped any.
     */
    val verticesBeforeShortcut: Int = 0,
    /**
     * What the turn-smoothing pass made of this route's corners: the ones that took their whole
     * comfort radius, the ones that had to be cut back to fit, and the ones that stayed sharp
     * because no radius fitted at all.
     *
     * Reported rather than inferred, because a route that could not be smoothed has to say so:
     * "nothing to smooth" and "nowhere to smooth it" read identically off the polyline alone, and
     * only the second one is a defect worth looking at.
     */
    val smoothedVertices: Int = 0,
    val reducedVertices: Int = 0,
    val sharpVertices: Int = 0,
    /**
     * The largest turn radius the water actually allowed, in metres — 0 when no corner took an arc.
     * A count cannot tell "the cap refused every corner" from "the mesh left no room for one"; this
     * can, and it is the number that says whether the cap or the geometry is the limit.
     */
    val largestTurnRadiusM: Double = 0.0,
    /**
     * Why the sharp corners stayed sharp, split by what refused them; the three add up to
     * [sharpVertices]. [sharpNoRoomVertices] — the legs had no room for any radius, so the mesh's own
     * spacing is the limit; [sharpNoWaterVertices] — the arc was sound, the water beside the corner
     * was not; [sharpNoGeometryVertices] — no arc could be drawn at that corner at all, a degenerate
     * or doubling-back pair of legs, so there was no radius to bisect. The third is the one to watch
     * rather than the one to explain away: a near-zero count is the expected reading, and anything
     * approaching [sharpVertices] is a finding — it read 425 of 444 while the arc's centre was built
     * at `R / sin(θ / 2)`.
     */
    val sharpNoRoomVertices: Int = 0,
    val sharpNoWaterVertices: Int = 0,
    val sharpNoGeometryVertices: Int = 0,
    /**
     * The count where the arc was sound, on water, and **dearer than the corner it would replace** —
     * see [`ykws.android.maro.spatial.RouteFillet.Result.sharpNoPrice`]. It is the fourth thing that
     * can refuse a corner, and the one that grew out of the invariant: every pass re-prices what it
     * changes, the fillet included.
     */
    val sharpNoPriceVertices: Int = 0,
    /**
     * How many candidate shortcuts the water refused — the pass's own evidence that it tested
     * anything, beside the count of what it kept.
     *
     * A zero here with many vertices dropped is the good reading: every straight line tried lay on
     * vetted water. A count near the chain's own length says the mesh's own holes and the boat's
     * stretch were what forced the vertices that remain, and the line is short of turns because the
     * water is short of straight runs, not because the pass was idle.
     */
    val shortcutRefusedSegments: Int = 0,
    /**
     * How many candidates the **price** refused — a straight run that lay on vetted water and was
     * still no cheaper than the chain legs it would have replaced.
     *
     * Reported apart from [shortcutRefusedSegments] because the two refusals are different findings,
     * and a count that never mentions the constraint it claims is a bug report rather than a
     * measurement: one says the mesh's water refused the line, this one says the cost model did.
     */
    val shortcutRefusedByCostSegments: Int = 0,
    /**
     * **True when the invariant fell back and the line on the screen is the raw node chain.**
     *
     * Every pass over the chain re-prices what it changes, and the assembly asserts that the drawn
     * line's own cost never exceeds the seconds the search accumulated. A breach is a defect in a
     * pass — but a defect is not worth the boat's route, so the run falls back to the **raw node
     * chain** (no merge, no shortcut, no fillet), warns, and answers with a line whose clock is the
     * search's own. False on every ordinary run; the corrected admissions should mean it stays
     * false, and this flag is how a test or the trajectory probe proves that rather than assuming
     * it.
     */
    val rawChainFallback: Boolean = false,
    /** The mesh search's own A* count — see [`RouteEngineDetails.nodesExpanded`]. */
    override val nodesExpanded: Int = 0
) : RouteEngineDetails
