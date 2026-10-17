package ykws.android.maro.spatial.multipass

import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.spatial.SpatialOperations
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min

/**
 * The clearance taut pull: collapses the coarse cell path into straight waypoints that stay clear of
 * the field's walls. The classic two-pointer string-pull — the anchor stands while the probe advances,
 * and on a failed extension the probe's predecessor becomes the new anchor — walked over the
 * **unified cost field** [RouteCostField] rather than a bare distance, so the same walk serves the
 * coastline of stage 1 and every source the later phases add.
 *
 * A chord is clear when every sample along it stands at least [marginM] from the field's nearest hard
 * wall, sampled at `≤ marginM / 2` so the margin is a guarantee rather than an approximation. The
 * clearance is **exempt where the margin itself is waived** — within a margin-radius disc around each
 * forced-free end (the raw start and aim), and on the **carved approach** each end was granted
 * ([EndApproaches]), which is how the first and last legs reconcile with the margin predicate even
 * when the berth runs deeper than that disc. A rejected chord is re-walked once from its
 * predecessor's predecessor — the concave-coast retry, bounded rather than looped.
 *
 * **The field's two hard shapes are read apart.** A mark pays the rastered walls' own step
 * ([RouteCostField.hardBlocked]) and its distance to the materialized walls
 * ([RouteCostField.hardDistanceM]) — the live coastline query whose per-mark price this walk exists to
 * bound. Beside the fine step the walk takes a **coarse step**, and it is a required parameter for the
 * reason a caller-owned step always is: a defaulted one would skip a read no caller chose to skip.
 *
 * **The coarse marks are what spares a read.** They are placed first, one per coarse step, at the
 * midpoints of the equal divisions, and every one of them pays the distance read; a fine mark then pays
 * it only where the coarse mark covering it failed to prove its half-step clear. The proof is the
 * distance's own 1-Lipschitz bound — a reading `d` puts every point within half a coarse step at least
 * `d − coarseStep / 2` off the wall — so **no verdict can move**: a chord refused today is refused, one
 * pulled taut today is taut, and the marks themselves are exactly the ones the fine walk would have made.
 *
 * **No ring clearance.** The pull carries no standoff: a speed zone's outside margin is a **price** in
 * the search and in the field's own price guard, never a clearance here. The two walls that still bind
 * are the land margin and the price guard.
 *
 * **The priced half of the guarantee.** Where the field carries a price, a chord is accepted only
 * while its own summed price stays within the A* cell path's over the span it would replace — so the
 * pull can never shortcut a corner through a priced band the search just went around. The price is a
 * **time** here as it is in the search, so the two sides of that comparison are the same unit. The
 * path's price is a prefix sum, so each candidate costs one walk of the chord alone, and a field with
 * no price skips the whole reading.
 *
 * **The price walk's own reads are coarsened behind a proof its sources declare.** A soft source
 * answers `priceClearanceM(p)` — the distance to the nearest place its own price arm changes — and the
 * walk prices a whole group of fine intervals from one reading exactly where that clearance is at least
 * the group's half-length: no boundary stands inside, so the arm is constant and the group's product is
 * identically the fine sum it replaces. It reads the field's price alone for it, never the hard walls'
 * `blocked` test, since that flag is discarded. A source that declares no boundary proves nothing, and
 * every unproved group keeps today's reads.
 */
private const val MIN_SAMPLE_STEP_M = 1.0

/**
 * **The settled lookback's window: the largest span (segments) one chord may replace.** A span of `k`
 * runs from an anchor over `k` segments, so `k = 2` is today's triple and `k = 3, 4` collapse a run of
 * two or three flat vertices into one chord. Small on purpose — the shape to try — so the added work is
 * proportional to the flat, jagged runs rather than to the line. A constant of the algorithm, not a key.
 */
private const val SWEEP_WINDOW_MAX_SPAN = 4

/**
 * **The window's geometry gate, in degrees** — the largest turn an intermediate vertex may carry and
 * still belong to a run a single longer chord may collapse. A run of flat vertices is what the triple
 * alone cannot see; a genuinely turned run keeps today's triple. It is tested as a bearing comparison
 * before any field read, and it is a constant of the algorithm, not a key.
 */
private const val SWEEP_WINDOW_MAX_TURN_DEG = 45.0

/**
 * **The marks of one sampled walk, on a fixed step from the chord's own anchor.** A walk's marks stand
 * at `anchor + (j + 0.5) × step`, one per whole step along the chord, and the **last interval is sized
 * to the chord's own remainder** rather than left over — so the intervals' lengths sum to the chord's
 * haversine exactly and a one-interval error cannot hide in an uncovered tail.
 *
 * The fixed anchor is the whole point: two attempts that share an anchor share every mark but the tail,
 * which is the property a memo of the reads already paid needs to hit **exactly** — the same point, the
 * same pure function, the same double. Chord-relative marks never do, because their own step
 * `dist / ceil(dist / step)` carries the chord's length into every mark and the two attempts' marks fall
 * apart.
 *
 * The step is the walk's own — the clearance walk's **coarse** step, the price walk's **sampling** step —
 * and it is floored here, at the one home that floors it, so no degenerate step can turn the partition
 * into a runaway loop.
 */
internal class MarkLattice(val lengthM: Double, stepM: Double) {

    /** The step in force, floored at the walk's own floor. */
    val stepM: Double = stepM.coerceAtLeast(MIN_SAMPLE_STEP_M)

    /** The interval count: the whole steps, plus the remainder's own interval where there is one. */
    val count: Int = ceil(lengthM / this.stepM).toInt().coerceAtLeast(1)

    /** The width (m) of interval [j] — one step, the last one sized to the chord's remainder. */
    fun widthM(j: Int): Double = minOf((j + 1) * stepM, lengthM) - minOf(j * stepM, lengthM)

    /** The fraction of the chord at interval [j]'s own midpoint — the mark a walk reads there. */
    fun markT(j: Int): Double = fraction(j * stepM + widthM(j) / 2.0)

    /** The length (m) the interval run `[i0, i1)` covers — never left over at the tail. */
    fun spanLengthM(i0: Int, i1: Int): Double =
        minOf(i1 * stepM, lengthM) - minOf(i0 * stepM, lengthM)

    /** The fraction of the chord at the run `[i0, i1)`'s own midpoint — the point a span proof reads. */
    fun spanMidT(i0: Int, i1: Int): Double =
        fraction((minOf(i0 * stepM, lengthM) + minOf(i1 * stepM, lengthM)) / 2.0)

    /** The interval covering fraction [t] of the chord — the coarse mark that answers for a fine one. */
    fun indexOf(t: Double): Int = minOf((t * lengthM / stepM).toInt(), count - 1)

    /** [metres] from the anchor as a fraction of the chord, or a half for a degenerate empty chord. */
    private fun fraction(metres: Double): Double = if (lengthM > 0.0) metres / lengthM else 0.5
}

/**
 * **The reads one walk has already paid, keyed on the mark's own point.** One memo per walk, shared by
 * the clearance walk and the price walk.
 *
 * **The key is the point itself, never a rounded cell index.** The marks stand on the fixed lattice, so
 * two attempts that share a stretch land on the very same [LatLng] — the same double coordinates — and a
 * `HashMap` keyed by the point answers them with the same value the field would. A cell-rounded key
 * would answer a neighbouring mark's price while every fixture on the walk's own points still passed.
 *
 * **It is bound to its field, and a different field wipes it.** Every accessor checks the field's own
 * **identity** before it answers: a memo that outlived its walk — the field is rebuilt per λ pass — can
 * only ever re-read under the new field, never answer it with the previous field's numbers, so the
 * plan's one-walk lifetime is structural rather than assumed.
 *
 * **Both walks share it, but in the pull's own shape the win is the price half's.** The pull threads one
 * memo through the clearance walk and the price walk alike, and a hit answers the field's own double.
 * The clearance half is **inert** in that walk — every chord evaluation is a fresh `(anchor, probe)`
 * pair, so its marks are its own and none is re-visited — while the price half's hits come from
 * [MultipassPull.softPricePrefix] walking each raw segment before an attempt walks the chord that
 * retraces it.
 *
 * Nothing here is static, shared or cached across walks.
 */
internal class MarkMemo {

    /** The field the two tables were filled under; a different identity wipes them. */
    private var boundField: RouteCostField? = null

    /** The materialized walls' distance already read, by the point it was read at. */
    private val hard = HashMap<LatLng, Double>()

    /** The summed soft price already read, by the point it was read at. */
    private val price = HashMap<LatLng, Double>()

    /** The hard reads answered from the table rather than the field — raised on [hardDistance]'s hit side. */
    var hardHits: Long = 0L
        private set

    /**
     * The price reads answered from the table rather than the field — raised on the hit side of both
     * [price] and [priceOrNull], the pair the one-probe read in `readPrice` splits across.
     */
    var priceHits: Long = 0L
        private set

    /** The materialized walls' distance at [p] under [field] — read once, then answered from here. */
    fun hardDistance(field: RouteCostField, p: LatLng): Double {
        bind(field)
        val held = hard[p]
        if (held != null) {
            hardHits++
            return held
        }
        val value = field.hardDistanceM(p)
        hard[p] = value
        return value
    }

    /** The soft price at [p] under [field] — read once, then answered, the same double each time. */
    fun price(field: RouteCostField, p: LatLng): Double {
        bind(field)
        val held = price[p]
        if (held != null) {
            priceHits++
            return held
        }
        val value = field.softPriceSecAt(p)
        price[p] = value
        return value
    }

    /**
     * The price at [p] under [field], or `null` where no read is held yet — the **one** lookup that
     * answers both questions the read's own home asks: whether a read is owed and what its answer is.
     * The caller stores the fresh reading with [putPrice], so the probe is never paid twice.
     */
    fun priceOrNull(field: RouteCostField, p: LatLng): Double? {
        bind(field)
        val held = price[p]
        if (held != null) priceHits++
        return held
    }

    /** Stores [value] as the price at [p] under [field] — the other half of [priceOrNull]'s one probe. */
    fun putPrice(field: RouteCostField, p: LatLng, value: Double) {
        bind(field)
        price[p] = value
    }

    /** Wipes both tables when the field's identity changes, so no answer can cross a rebuild. */
    private fun bind(field: RouteCostField) {
        if (boundField !== field) {
            hard.clear()
            price.clear()
            boundField = field
        }
    }
}

/**
 * **The water one call walks, in one value.** It holds the seven constants every walking call re-listed
 * before this step — the margin, the two steps, the field, the two raw ends and the carved approaches —
 * and it **is the walk's own context ([PullContext]) cut back to those seven**: the walk's three tallies
 * stay out, because a counter shared between two walks of one pass cannot change an answer while a cache
 * can, so the two constructs keep one lifetime each — the setup per call, the context per walk.
 *
 * The two steps are the walk's own cells and are **never defaulted**: [coarseStepM] is the clearance
 * walk's sampling step (the cell the call was resolved on) and [priceStepM] the price walk's own interior
 * cell, the step and grouping rule its prefix is built from, so the guard compares two walks of one law.
 * [field] is the unified cost field, [marginM] the shore clearance, and [start] and [aim] the two raw
 * ends whose discs, beside the carved [approaches], the margin waives.
 *
 * **One lifetime, per call.** A call builds its setup once, immediately after the field it hands the
 * walk, and never stores it: a corridor that grows hands the grown context's fresh field to a new setup,
 * never a cached one. Inside [MultipassPull.pull] the setup is folded, with the walk's own tallies, into
 * the context the internal walk threads — and it wraps the field value the call already holds, so it
 * never builds one.
 */
internal class PullSetup(
    val marginM: Double,
    val coarseStepM: Double,
    val priceStepM: Double,
    val field: RouteCostField,
    val start: LatLng,
    val aim: LatLng,
    val approaches: EndApproaches = EndApproaches.NONE,
    /** The gate distance (m) read at a point — the walk/grid's own field, `MAX_VALUE` where none. */
    val gateDistanceMAt: (LatLng) -> Double = { Double.MAX_VALUE },
    /** The gate's standoff margin (m) — the hard standoff, both engines, 0 where the gate is off. */
    val gateMarginM: Double = 0.0,
    /** The berth's outer edge (m off the gate edge), 0 where this plan prices no berth. */
    val berthOuterM: Double = 0.0,
    /** The berth's full rate (s/m at the margin), 0 where this plan prices no berth. */
    val berthSecPerM: Double = 0.0,
    /** The pass's own λ for the berth — the cursor that turns the grid's distance into seconds. */
    val berthLambda: Double = 0.0,
    /** The world's depth (m) at a point — the water ring's own read, `NaN` where none is supplied. */
    val gateDepthAt: (LatLng) -> Double = { Double.NaN },
    /** The gate's minimum depth (m); `0.0` disables the water ring, as a fixture without a gate does. */
    val gateMinDepthM: Double = 0.0,
    /** The walk's pace (m/s) — the travel term every proof compares; `0.0` (a fixture) reads soft price only. */
    val paceMps: Double = 0.0
) {

    /**
     * **The walk's context, folded from this setup in one place** — the seven water values plus whichever
     * of the walk's three tallies the caller names. `pricedLineCost` and `snapToCorners` pass none — they
     * count and cache nothing of their own — and [MultipassPull.pull] passes the walk's own refusals,
     * timing and memo, so no site re-lists the seven values by hand.
     */
    internal fun context(
        refusals: PullRefusals? = null,
        timing: PullTiming? = null,
        memo: MarkMemo? = null
    ): PullContext = PullContext(
        marginM, coarseStepM, priceStepM, field, start, aim, approaches,
        gateDistanceMAt, gateMarginM, berthOuterM, berthSecPerM, berthLambda,
        gateDepthAt, gateMinDepthM, paceMps,
        refusals, timing, memo
    )
}

/**
 * **One walk's own values, made once at its start and threaded instead of re-declared.** The context
 * carries the walk's constants — the margin, the two steps, the field, the two raw ends and the carved
 * approaches — beside its three tallies: the refusals it counts, the timing it keeps and the memo it
 * fills, each **exactly as nullable as the walk demands**, so a solve that wants none of them passes
 * none. Nothing per-chord lives here — no endpoint, no anchor or probe index, no prefix array, no
 * lattice — and the membership is closed at what travels with the walk today: a new field is a new
 * decision, never an addition.
 *
 * **It is made inside [MultipassPull.pull] and never stored**, so a memo can no more outlive its walk
 * than it can today; the context is never a field of an engine, a runner or a seat.
 */
internal class PullContext(
    val marginM: Double,
    val coarseStepM: Double,
    val priceStepM: Double,
    val field: RouteCostField,
    val start: LatLng,
    val aim: LatLng,
    val approaches: EndApproaches = EndApproaches.NONE,
    val gateDistanceMAt: (LatLng) -> Double = { Double.MAX_VALUE },
    val gateMarginM: Double = 0.0,
    val berthOuterM: Double = 0.0,
    val berthSecPerM: Double = 0.0,
    val berthLambda: Double = 0.0,
    val gateDepthAt: (LatLng) -> Double = { Double.NaN },
    val gateMinDepthM: Double = 0.0,
    /** The walk's pace (m/s) — the travel term every proof compares; `0.0` reads soft price only. */
    val paceMps: Double = 0.0,
    val refusals: PullRefusals? = null,
    val timing: PullTiming? = null,
    val memo: MarkMemo? = null
) {
    /** Whether any price is in force — the field's soft sources or the berth's own rate. */
    val hasSoftPrice: Boolean =
        field.hasSoft || (berthLambda > 0.0 && berthSecPerM > 0.0 && berthOuterM > gateMarginM)

    /** The berth's rate at [p], in seconds per metre, at this pass's cursor. */
    fun berthRate(p: LatLng): Double {
        if (berthLambda <= 0.0 || berthSecPerM <= 0.0) return 0.0
        val share = berthShare(gateDistanceMAt(p), gateMarginM, berthOuterM)
        if (share <= 0.0) return 0.0
        return berthLambda * berthSecPerM * share
    }

    /** The berth's own boundary clearance at [p] — `min(|d − margin|, |d − outer|)` — or `MAX_VALUE`. */
    fun berthClearanceM(p: LatLng): Double {
        if (berthLambda <= 0.0 || berthSecPerM <= 0.0 || berthOuterM <= gateMarginM) return Double.MAX_VALUE
        val d = gateDistanceMAt(p)
        return min(abs(d - gateMarginM), abs(d - berthOuterM))
    }

    /** The soft clearance the price-walk's group proof reads — the field's and the berth's, one number. */
    fun softClearanceM(p: LatLng): Double = min(field.priceClearanceM(p), berthClearanceM(p))
}

object MultipassPull {

    /**
     * Pulls [path] (raw start first, raw aim last) taut into the ordered waypoint list, as direct as
     * the field's margin allows and independent of grid orientation.
     *
     * @param setup the water this call walks — the margin, the clearance and price steps, the field,
     *   the two raw ends and the carved approaches — built by the caller once and immediately after
     *   the field it hands the walk, never cached across a corridor's growth.
     * @param refusals the tally the walk counts its refused chords into, or `null` where none is read.
     * @param timing the walk's own tally of what it spent, or `null` where no reader wants it.
     * @param memo the walk's own read memo — **one per walk**, keyed on the mark's own point and bound to
     *   the field it fills under, so a rebuild wipes it rather than letting it answer stale. In this
     *   walk's own shape the shared points are the price walk's: the prefix walks each raw segment before
     *   an attempt walks the chord that retraces it, while the clearance walk never re-visits a point. A
     *   fresh memo is made here, at the walk's own start, and **no call at the seam passes one**. A
     *   caller passing `null` walks as before.
     */
    internal fun pull(
        setup: PullSetup,
        path: List<LatLng>,
        refusals: PullRefusals? = null,
        timing: PullTiming? = null,
        memo: MarkMemo? = MarkMemo()
    ): List<LatLng> {
        val ctx = setup.context(refusals, timing, memo)
        if (path.size <= 2) {
            ctx.timing?.recordWalk(setup, memo)
            return path
        }
        val result = ArrayList<LatLng>(path.size)
        result.add(path.first())
        val prefixStartNs = System.nanoTime()
        val pathTotalSec = if (ctx.hasSoftPrice || ctx.paceMps > 0.0) totalPrefix(path, ctx) else null
        ctx.timing?.addPrice(System.nanoTime() - prefixStartNs)
        var anchor = 0
        var probe = 1
        while (probe < path.size) {
            val decision = chordDecision(pathTotalSec, path, anchor, probe, ctx)
            val refused = decision.refusal
            when {
                refused == null -> probe++
                // The immediate step grazes land in a corner: it cannot be pulled, so it is accepted
                // once and the walk moves on — the bounded form of the concave re-walk.
                probe == anchor + 1 -> {
                    ctx.refusals?.record(refused)
                    result.add(path[probe])
                    anchor = probe
                    probe++
                }
                else -> {
                    ctx.refusals?.record(refused)
                    result.add(path[probe - 1])
                    anchor = probe - 1
                    // probe stands; the loop re-walks the chord from the new anchor exactly once.
                }
            }
        }
        if (result.last() != path.last()) result.add(path.last())
        ctx.timing?.recordWalk(setup, memo)
        return result
    }

    /** The deviation (m) under which a run of waypoints reads as one straight line. */
    private const val STRAIGHTEN_EPSILON_M = 10.0
    /** Metres per degree, near enough for a deviation tolerance — the straightener needs no geodesy. */
    private const val M_PER_DEG = 111_320.0

    /**
     * **The wall-checked straightening** — a Douglas–Peucker pass over [points], run after the pull and
     * before the corner pass, so a run of near-collinear waypoints collapses into one straight chord and
     * the corner pass sees fewer bends rather than more. It only ever **drops** points: a run is replaced
     * by its chord when every dropped point lies within [epsilonM] of that chord **and** the chord clears
     * the wall and is no dearer than the run it replaces; otherwise the farthest point is kept and the two
     * halves are straightened in turn. Ends are untouched and no point is ever invented, so every kept
     * point is one the pull already proved clear — the pass can straighten but never breach the wall.
     */
    internal fun straightenLine(
        setup: PullSetup,
        points: List<LatLng>,
        epsilonM: Double = STRAIGHTEN_EPSILON_M
    ): List<LatLng> {
        if (points.size < 3 || epsilonM <= 0.0) return points
        val ctx = setup.context()
        val keep = BooleanArray(points.size)
        keep[0] = true
        keep[points.lastIndex] = true
        markStraight(points, 0, points.lastIndex, epsilonM, ctx, keep)
        val out = ArrayList<LatLng>(points.size)
        for (i in points.indices) if (keep[i]) out.add(points[i])
        return out
    }

    /** The recursive split — keep the farthest point of a run, and its two halves, unless the chord is legal. */
    private fun markStraight(
        points: List<LatLng>,
        lo: Int,
        hi: Int,
        epsilonM: Double,
        ctx: PullContext,
        keep: BooleanArray
    ) {
        if (hi <= lo + 1) return
        var far = lo + 1
        var farthest = -1.0
        for (i in lo + 1 until hi) {
            val deviation = perpendicularM(points[i], points[lo], points[hi])
            if (deviation > farthest) {
                farthest = deviation
                far = i
            }
        }
        if (farthest <= epsilonM && chordLegal(points, lo, hi, ctx)) return
        keep[far] = true
        markStraight(points, lo, far, epsilonM, ctx, keep)
        markStraight(points, far, hi, epsilonM, ctx, keep)
    }

    /** The chord `lo → hi` replaces the run `lo..hi` only where it clears the wall and is no dearer. */
    private fun chordLegal(points: List<LatLng>, lo: Int, hi: Int, ctx: PullContext): Boolean {
        val a = points[lo]
        val b = points[hi]
        if (legClearCause(a, b, ctx) != null) return false
        var runTotal = 0.0
        for (i in lo until hi) runTotal += totalSec(points[i], points[i + 1], ctx)
        return totalSec(a, b, ctx) <= runTotal
    }

    /** The perpendicular distance (m) from [p] to the segment [a]→[b], equirectangular about [a]. */
    private fun perpendicularM(p: LatLng, a: LatLng, b: LatLng): Double {
        val cosLat = Math.cos(Math.toRadians((a.latitude + b.latitude) / 2.0))
        val px = (p.longitude - a.longitude) * M_PER_DEG * cosLat
        val py = (p.latitude - a.latitude) * M_PER_DEG
        val bx = (b.longitude - a.longitude) * M_PER_DEG * cosLat
        val by = (b.latitude - a.latitude) * M_PER_DEG
        val len2 = bx * bx + by * by
        if (len2 <= 0.0) return Math.hypot(px, py)
        val t = ((px * bx + py * by) / len2).coerceIn(0.0, 1.0)
        return Math.hypot(px - t * bx, py - t * by)
    }

    /**
     * **The lookback the forward-only walk lacks** — a fixpoint removable-vertex sweep over a settled
     * line, run on the fine pass's own answer so the line the corner pass consumes is the genuinely taut
     * one. [`pull`] is a forward-only greedy: it commits a waypoint on the **first** refused chord and
     * re-anchors there, so a vertex standing over open water between two real corners survives a chord
     * that would skip it — a keep the walk never re-tests. This sweep re-tests exactly those chords, and
     * drops a vertex only where the walk's own two proofs clear it.
     *
     * **The two proofs, read from the walk's own context.** For each consecutive triple `A, B, C` the
     * interior `B` drops when the chord `A → C` is **clear** ([legClearCause] answers `null`) **and** it
     * is **not dearer** than the two segments it replaces (`softPriceSec(A, C)` at most
     * `softPriceSec(A, B) + softPriceSec(B, C)`). Both are the very tests the pull already trusts, so a
     * swept line can no more stand nearer to land than the margin nor shortcut priced water than the line
     * it came from — the sweep is precision-**improving**, never a licence to bend the route.
     *
     * **The allowance makes the price proof tolerant of the field's own sampling.** The strict test pins a
     * vertex on a difference smaller than the water can be read — the log's own `+16 ms` against `7.463 s`,
     * and a pair equal below the printed precision. The chord now drops while `chordSec <= pathSec ×
     * (1 + [allowanceShare])`, a **relative** term: where the legs cost nothing the allowance is nothing,
     * so a priced chord is never cut by it, and it reads no new field value — the same two seconds the
     * proof already compares. The wall stays absolute: clearance is still read first and short-circuits, so
     * a chord refused by land pays no price read at all.
     *
     * **A window collapses a flat run the triple alone cannot see.** One interior vertex at a time is
     * invisible to a run of three or more flat vertices — an open-water wiggle, a pocket-boundary
     * oscillation. From each anchor the sweep tests chords over `k = 2 … [SWEEP_WINDOW_MAX_SPAN]` segments,
     * but only where every intermediate turn is under [SWEEP_WINDOW_MAX_TURN_DEG] (a bearing comparison,
     * read before any field read), taking the **longest acceptable** chord and never re-opening a closed
     * window. `k = 2` is today's triple and is always tried, so a genuinely turned run keeps exactly
     * today's behaviour, and the window's extra reads scale with the flat runs and are answered from the
     * pull's own memo.
     *
     * **One insertion point, gated on the line's own refusals.** The fine pass calls this on its settled
     * line alone: the runner's final pull is never swept, because the coarse line is re-refined here and
     * sweeping it would pay twice for one answer. The sweep is skipped where the line's own pull recorded
     * no refusal (`refusals.land + refusals.price == 0`), since a line the greedy pulled in one chord
     * carries no artifact — the common case pays nothing at all. A `null` tally proves nothing of the
     * line, so the sweep still runs; the gate is a saving, never a verdict.
     *
     * **The fixed ends are never candidates.** Only an interior `B` is tested, so the raw [PullSetup.start]
     * and [PullSetup.aim] the walk must reach are untouched. **The tie-break is deterministic**: a triple
     * drops on `<=` — not dearer, not merely cheaper — and the sweeps run left to right to a fixed point,
     * so the same input always yields the same line; every removal shortens the list, so the loop
     * terminates.
     *
     * **It reuses the walk's own context and memo** — the field, the margin, both steps and the carved
     * approaches — so the end-exemption and the marks the pull already paid apply unchanged, and a shared
     * [MarkMemo] answers the sweep's retraced marks rather than paying them again.
     *
     * **It reports itself through [trace], so a silent sweep is never mistaken for one that ran.** One
     * summary line carries the in/out point counts, the passes walked and the drops and the two refusals,
     * one line per candidate vertex names its `at=` and the proof that kept or dropped it, and a
     * `skipped=` line stands where the gate or the size guard stopped the sweep before any candidate was
     * tested. A `SWEEP window` line names each run a longer chord collapsed — the window's own verdict —
     * while the summary keeps its shape, a window's vertices counting into its `dropped=` beside the
     * triple's. The report decides nothing: the drawn line is the walk's either way.
     *
     * @param allowanceShare the price allowance as a share of the legs' own price —
     *   `route.avoid.pull.allowanceShare` through [AppConfig] where none is handed, so a caller reading a
     *   fixture passes its own and the shipped key is the default.
     * @param setup the water the settled walk was handed — the same margin, steps, field, raw ends and
     *   approaches, so the sweep's proofs are the walk's own.
     * @param line the settled line, raw start first and raw aim last, exactly as [`pull`] returned it.
     * @param refusals the tally the settled pull counted into; the sweep runs only where it is non-empty,
     *   and a `null` tally proves nothing so the sweep runs.
     * @param timing the walk's own tally of what it spent, or `null` where no reader wants it.
     * @param memo the walk's own read memo, shared with the settled pull so a retraced mark is answered
     *   from it rather than paid again; a fresh one is made here where none is handed.
     * @param trace the report's own line channel, carried exactly as [RoutePassRunner.runPass] carries its
     *   own, so the engine's `MaroRoute` sink reaches it and nothing else is needed; the default sink
     *   discards every line and the walk is then read exactly as before.
     */
    internal fun sweep(
        setup: PullSetup,
        line: List<LatLng>,
        refusals: PullRefusals? = null,
        timing: PullTiming? = null,
        memo: MarkMemo? = MarkMemo(),
        trace: (() -> String) -> Unit = {},
        allowanceShare: Double = AppConfig.routeAvoidPullAllowanceShare
    ): List<LatLng> {
        if (line.size < 3) {
            trace { "SWEEP skipped=short" }
            return line
        }
        // The gate: a line the greedy pulled in one chord has no artifact to remove, so the sweep is
        // skipped where the settled pull recorded no refusal.
        if (refusals != null && refusals.land + refusals.price == 0) {
            trace { "SWEEP skipped=no-refusals" }
            return line
        }
        val ctx = setup.context(refusals, timing, memo)
        val work = ArrayList(line)
        var passes = 0
        var dropped = 0
        var refusedClear = 0
        var refusedPrice = 0
        var changed = true
        while (changed) {
            passes++
            changed = false
            var i = 1
            while (i < work.size - 1) {
                // The fixed ends are never candidates: only an interior `B` at `i` is tested, so the raw
                // start and aim the walk must reach are left where they are.
                val a = work[i - 1]
                val b = work[i]
                // ── The window: a flat run may collapse to one longer chord, ahead of today's triple ──
                // The geometry gate is a bearing comparison, read before any field read, so only a run the
                // triple cannot see is worth a longer chord; a turned run is left to the triple below.
                val span = windowSpan(work, i)
                if (span > 2) {
                    val collapsed = longestWindowChord(work, i, span, ctx, allowanceShare)
                    if (collapsed != null) {
                        trace {
                            "SWEEP window idx=$i span=$collapsed at=${atText(b)} " +
                                "turn=${fmt(sweepTurnDeg(a, b, work[i + 1]))}"
                        }
                        repeat(collapsed - 1) { work.removeAt(i) }
                        dropped += collapsed - 1
                        changed = true
                        // The collapsed run now sits behind the new anchor `b`, never re-opened.
                        continue
                    }
                }
                // ── Today's triple, with the allowance on its price proof ──
                val c = work[i + 1]
                // **Whether `b` is removable** — the pull's own lookback, read as the two proofs this
                // sweep drops on: the skipping chord `a → c` is clear and it is not dearer than the two
                // segments it replaces. The clearance proof is read first and short-circuits exactly as it
                // always has, so a chord refused by land never pays its price and the sweep's own reads are
                // unmoved by carrying the report; the price's two sides stay in hand for the keep line.
                if (legClearCause(a, c, ctx) != null) {
                    trace {
                        "SWEEP keep idx=$i at=${atText(b)} turn=${fmt(sweepTurnDeg(a, b, c))} why=LAND"
                    }
                    refusedClear++
                    i++
                    continue
                }
                val chordSec = totalSec(a, c, ctx)
                val pathSec = totalSec(a, b, ctx) + totalSec(b, c, ctx)
                // The allowance is relative: a share of the legs' own price, and nothing where they are free.
                if (chordSec <= pathSec * (1.0 + allowanceShare)) {
                    trace {
                        "SWEEP drop idx=$i at=${atText(b)} turn=${fmt(sweepTurnDeg(a, b, c))}"
                    }
                    work.removeAt(i)
                    dropped++
                    changed = true
                    continue
                }
                trace {
                    "SWEEP keep idx=$i at=${atText(b)} turn=${fmt(sweepTurnDeg(a, b, c))} " +
                        "why=PRICE chordSec=${fmt(chordSec, 3)} pathSec=${fmt(pathSec, 3)}"
                }
                // Only a kept-by-price vertex is worth the arm split: it is the one case a reader has to
                // explain, and it is rare enough that the walk can afford to say which arm charged it.
                traceArms(a, c, ctx, trace, i)
                refusedPrice++
                i++
            }
        }
        trace {
            "SWEEP in=${line.size} out=${work.size} passes=$passes dropped=$dropped " +
                "refusedClear=$refusedClear refusedPrice=$refusedPrice"
        }
        return work
    }

    /**
     * The `lat,lon` pair the report writes after `at=` for [p] — the identifier a bend carries across the
     * rungs, exactly as the `CORNER` lines spell it.
     */
    private fun atText(p: LatLng): String = "${fmt(p.latitude, 5)},${fmt(p.longitude, 5)}"

    /**
     * The turn at [at] between its two legs, in degrees — the size of the heading change from the
     * incoming leg `prev → at` to the outgoing `at → next`, so a `SWEEP` line names a bend by the figure a
     * reader reasons about and a straight run reads `0.0`. It is the report's own reading and decides
     * nothing: the sweep's verdicts are its two proofs, never this angle.
     */
    private fun sweepTurnDeg(prev: LatLng, at: LatLng, next: LatLng): Double {
        val inBearing = SpatialOperations.initialBearing(prev, at)
        val outBearing = SpatialOperations.initialBearing(at, next)
        val delta = ((outBearing - inBearing + 540.0) % 360.0) - 180.0
        return abs(delta)
    }

    /**
     * **The longest flat run of interior vertices from the anchor at `i - 1`, in segments** — the largest
     * span `k` (at most [SWEEP_WINDOW_MAX_SPAN]) whose **every** intermediate vertex turns by less than
     * [SWEEP_WINDOW_MAX_TURN_DEG] and whose endpoint stands inside the line. `k = 2` is today's triple and
     * carries no gate, so no turn blocks it; a window of three or more segments opens only where the first
     * interior is flat **and** each further one is, so a genuinely turned run keeps exactly today's
     * behaviour. Bearings alone — it reads no field value.
     */
    private fun windowSpan(work: List<LatLng>, i: Int): Int {
        if (sweepTurnDeg(work[i - 1], work[i], work[i + 1]) >= SWEEP_WINDOW_MAX_TURN_DEG) return 2
        var span = 2
        while (span < SWEEP_WINDOW_MAX_SPAN) {
            val at = i + span - 1
            if (at + 1 >= work.size) break
            if (sweepTurnDeg(work[at - 1], work[at], work[at + 1]) >= SWEEP_WINDOW_MAX_TURN_DEG) break
            span++
        }
        return span
    }

    /**
     * **The longest window chord from the anchor at `i - 1` that both proofs accept, or `null`.** The
     * chords are tried longest first — `k` from [maxSpan] down to `3`, `k = 2` being the triple the caller
     * runs beside this — and the first the field clears and the allowance accepts wins. The clearance proof
     * is read first and short-circuits, so a chord refused by land pays no price read at all, and the legs'
     * price is the run's own sum, so a span is dearer or not exactly as the triple reads it.
     */
    private fun longestWindowChord(
        work: List<LatLng>,
        i: Int,
        maxSpan: Int,
        ctx: PullContext,
        allowanceShare: Double
    ): Int? {
        var span = maxSpan
        while (span >= 3) {
            val a = work[i - 1]
            val e = work[i - 1 + span]
            if (legClearCause(a, e, ctx) == null) {
                val chordSec = totalSec(a, e, ctx)
                var pathSec = 0.0
                for (j in i - 1 until i - 1 + span) pathSec += totalSec(work[j], work[j + 1], ctx)
                if (chordSec <= pathSec * (1.0 + allowanceShare)) return span
            }
            span--
        }
        return null
    }

    /**
     * **Which arm charged a kept chord, and where it charged most.** The price's two sides say a chord
     * is dearer; they do not say *who* charged it, and the arms are invisible in a total — the band, a
     * ring, the depth ramp and a marker all read as one number. This walks the chord's own lattice, adds
     * each arm's rate times the interval's width, and remembers the interval whose instantaneous total
     * was dearest, so one line names the arm that dominates the chord beside the place its charge falls.
     *
     * It decides nothing: the sweep's verdicts are its two proofs, and the sum of the arms it reports is
     * the field's own price at each mark ([RouteCostField.armPricesAt]), never a second law. It runs only
     * where the sweep is already reporting a kept-by-price vertex, which is the cheapest place the
     * question can be asked, and its reads are the report's own, counted nowhere.
     */
    private fun traceArms(
        a: LatLng,
        c: LatLng,
        ctx: PullContext,
        trace: (() -> String) -> Unit,
        idx: Int
    ) {
        val marks = MarkLattice(SpatialOperations.haversine(a, c), clearanceStep(ctx.marginM))
        val sums = LinkedHashMap<PriceArm, Double>()
        var worstSec = 0.0
        var worstAt: LatLng? = null
        for (j in 0 until marks.count) {
            val p = chordPoint(a, c, marks.markT(j))
            val fieldArms = ctx.field.armPricesAt(p)
            // The berth rides the grid, not the field, so its arm is added here at the same cell unit the
            // loop below converts to seconds — the report names it [PriceArm.DEPTH] rather than missing it.
            val berthRate = ctx.berthRate(p)
            val arms =
                if (berthRate > 0.0) fieldArms + (PriceArm.DEPTH to berthRate * ctx.field.cellM) else fieldArms
            if (arms.isEmpty()) continue
            var here = 0.0
            var dearest: PriceArm? = null
            var dearestSec = 0.0
            for ((arm, cellPrice) in arms) {
                val sec = cellPrice / ctx.field.cellM * marks.widthM(j)
                sums[arm] = (sums[arm] ?: 0.0) + sec
                here += sec
                if (sec > dearestSec) {
                    dearestSec = sec
                    dearest = arm
                }
            }
            // **Every priced mark, not only the dearest** — the geography is the whole point of the
            // report, so each interval in turn is named where its own charge falls, with the arm that
            // charged it most. The dearest is kept for the summary line; the marks are the map.
            trace {
                "SWEEP mark idx=$idx i=$j at=${atText(p)} arm=${dearest?.name} sec=${fmt(dearestSec, 3)}"
            }
            if (here > worstSec) {
                worstSec = here
                worstAt = p
            }
        }
        if (sums.isEmpty()) return
        val worstArm = sums.maxByOrNull { it.value }?.key?.name
        trace {
            "SWEEP arms idx=$idx " +
                sums.entries.joinToString(" ") { "${it.key.name.lowercase()}=${fmt(it.value, 3)}" } +
                (worstAt?.let { " worst=$worstArm at=${atText(it)}" } ?: "")
        }
    }

    /** The chord's own verdict: the geometry stands, then the price guard alone decides — the same
     *  short circuit keeps a refused chord from costing a walk of its own price. Its two halves are
     *  timed apart where a [timing] is handed in, so the clearance walk and the price walk are told
     *  apart rather than inferred from the gap between two stage lines. */
    private fun chordDecision(
        pathTotalSec: DoubleArray?,
        path: List<LatLng>,
        anchor: Int,
        probe: Int,
        ctx: PullContext
    ): ChordDecision {
        val a = path[anchor]
        val b = path[probe]
        val clearanceStartNs = System.nanoTime()
        val cause = legClearCause(a, b, ctx)
        ctx.timing?.addClearance(System.nanoTime() - clearanceStartNs)
        if (cause == null) {
            val priceStartNs = System.nanoTime()
            val refusal = priceRefusal(pathTotalSec, path, anchor, probe, ctx)
            ctx.timing?.addPrice(System.nanoTime() - priceStartNs)
            return ChordDecision(refusal)
        }
        return ChordDecision(cause)
    }

    /** One evaluation's own verdict: the refusal, or `null` where the chord stands. */
    private data class ChordDecision(val refusal: ChordRefusal?)

    /** The wall walk's own sampling step for [marginM], floored — the density and its floor, one home. */
    internal fun clearanceStep(marginM: Double): Double = (marginM / 2.0).coerceAtLeast(MIN_SAMPLE_STEP_M)

    /**
     * Whether the margin is **waived** at [p]: within a disc of [marginM] around either raw end, or on
     * either carved approach. It is the pull's own exemption, read here and by the curve fitter's wall
     * test from one home, so the two can never disagree about where the shore clearance binds.
     */
    internal fun marginWaived(
        p: LatLng,
        marginM: Double,
        start: LatLng,
        aim: LatLng,
        approaches: EndApproaches,
        toleranceM: Double
    ): Boolean =
        SpatialOperations.haversine(p, start) < marginM ||
            SpatialOperations.haversine(p, aim) < marginM ||
            approachCovers(p, approaches.start, toleranceM) ||
            approachCovers(p, approaches.aim, toleranceM)

    /**
     * Whether [p] stands on one of the approach's own segments — within [toleranceM], the walk's own
     * sampling step, so a sample that cannot be told apart from the stretch reads as on it.
     */
    internal fun approachCovers(p: LatLng, approach: List<LatLng>, toleranceM: Double): Boolean {
        if (approach.size < 2) return false
        for (i in 0 until approach.size - 1) {
            if (SpatialOperations.pointToSegmentDistance(p, approach[i], approach[i + 1]) <= toleranceM) {
                return true
            }
        }
        return false
    }

    /** The walk's [legClearCause] answered with a boolean, from the walk's own context. */
    internal fun legClear(a: LatLng, b: LatLng, ctx: PullContext): Boolean =
        legClearCause(a, b, ctx) == null

    /**
     * The same walk [legClear] answers with a boolean, read for its **cause** — so the instrument can
     * count which of the pull's tests refused a chord instead of leaving the three to guesswork.
     *
     * **The exemption is one rule with two halves**, and it is the margin's own scope: the shore
     * clearance binds the **route**, never the ends. A sample standing within [PullContext.marginM] of
     * either end is skipped, and so is one standing on that end's **carved approach** — the stretch the
     * berth carve opened, which the disc cannot cover once the berth runs deeper than the margin.
     * Without that second half the pull re-closes the very channel it just used, and the drawn line
     * becomes a staircase inside the berth it escaped.
     *
     * The coarse marks stand on a **fixed lattice from the chord's own start**, one per [PullContext.coarseStepM]
     * with the last interval sized to the chord's remainder, each at its own interval's midpoint; each
     * pays the distance read, and a fine mark pays it only where the coarse mark covering it read under
     * `marginM + coarseStepM / 2`, so a step at or under the fine one spares nothing at all.
     */
    internal fun legClearCause(a: LatLng, b: LatLng, ctx: PullContext): ChordRefusal? {
        val dist = SpatialOperations.haversine(a, b)
        val sampleStep = clearanceStep(ctx.marginM)
        val steps = ceil(dist / sampleStep).toInt().coerceAtLeast(2)
        // The coarse marks, on a **fixed lattice from the chord's own start**: one per coarse step, its
        // last interval sized to the chord's remainder, each standing at its interval's own midpoint —
        // so every point of the chord, its two ends included, is within half a coarse step of one, no
        // boundary lattice can test the chord's own ends, and the mark set is the same for every attempt
        // that shares an anchor. The step is floored at the fine one — a finer "coarse" pass could only
        // pay more reads than it saves, and a degenerate zero would never terminate.
        val coarse = MarkLattice(dist, ctx.coarseStepM.coerceAtLeast(sampleStep))
        val coarseStep = coarse.stepM
        val coarseCount = coarse.count
        // The trigger the 1-Lipschitz bound gives: a reading `d` proves every point within half a
        // coarse step stands at least `d − coarseStep / 2` off the wall, so a mark at or above it
        // proves its own half-step clear — and the fine mark it covers along with it.
        val provedClearM = ctx.marginM + coarseStep / 2.0
        val coarseClear = BooleanArray(coarseCount)
        for (k in 0 until coarseCount) {
            coarseClear[k] = readHard(ctx, chordPoint(a, b, coarse.markT(k))) >= provedClearM
        }
        for (i in 1 until steps) {
            val t = i.toDouble() / steps
            val p = chordPoint(a, b, t)
            // The end-disc and the carved approach, one rule: the margin binds the path, never the ends.
            if (marginWaived(p, ctx.marginM, ctx.start, ctx.aim, ctx.approaches, sampleStep)) continue
            // The rastered walls are a **step**, so every mark tests them: no bound could prove a
            // half-step holds no shallow cell, and a coarse-only walk would sail one.
            if (ctx.field.hardBlocked(p)) return ChordRefusal.LAND
            // The gate's own standoff, read off the grid's distance field: a mark at or inside the gate's
            // margin is refused — the cheap pre-filter, on the cell centres.
            if (ctx.gateMarginM > 0.0 && ctx.gateDistanceMAt(p) <= ctx.gateMarginM) return ChordRefusal.LAND
            // **The water ring — the binding 20 m.** A mark whose ring at the gate's margin touches shallow
            // water is refused, so the line keeps its margin from the water itself, not from a sounding,
            // which is the only thing that reads a true 20 m to the edge.
            if (ctx.gateMinDepthM > 0.0 &&
                !gateStandoffClears(p, ctx.gateMarginM, ctx.gateMinDepthM, ctx.gateDepthAt)
            ) {
                return ChordRefusal.LAND
            }
            // The materialized distance is the one read the proof may spare, and it is skipped exactly
            // where the coarse mark covering this fine mark has already answered for its half-step.
            val covering = coarse.indexOf(t)
            if (!coarseClear[covering] && readHard(ctx, p) < ctx.marginM) return ChordRefusal.LAND
        }
        return null
    }

    /**
     * The point [t] of the way from [a] to [b] — the chord's own parameterisation, one home for the
     * coarse marks, the fine ones and the price walk's interval midpoints, so no two of them can be
     * placed off the same line.
     */
    private fun chordPoint(a: LatLng, b: LatLng, t: Double): LatLng = LatLng(
        a.latitude + (b.latitude - a.latitude) * t,
        a.longitude + (b.longitude - a.longitude) * t
    )

    /**
     * The price guard's own cause: `PRICE` where the chord costs more than the span it would replace.
     * Both sides are read on the **same step and the same grouping rule** — the prefix's per-segment
     * walks and the chord's own one-line walk obey one law — so the guard compares like with like. They
     * are not one partition: a segment is anchored at its own start and the chord at its own, so the two
     * coincide mark for mark on a straight run and diverge where the path bends.
     */
    private fun priceRefusal(
        pathTotalSec: DoubleArray?,
        path: List<LatLng>,
        anchor: Int,
        probe: Int,
        ctx: PullContext
    ): ChordRefusal? {
        val replacedTotalSec = pathTotalSec?.let { it[probe] - it[anchor] } ?: return null
        return if (totalSec(path[anchor], path[probe], ctx) > replacedTotalSec) {
            ChordRefusal.PRICE
        } else {
            null
        }
    }

    /**
     * The field's prices summed along one straight segment, in **seconds** — the price is the written
     * law's own quantity, a cell's own cost **turned into a rate** by the cell that priced it
     * ([RouteCostField.softPriceSecPerMAt]), multiplied by the interval's **metres**, read at the
     * **midpoint of each interval**. The intervals are a [MarkLattice] on the walk's own sampling
     * step `clearanceStep(marginM)`, its last interval sized to the chord's remainder, so the whole
     * segment is covered exactly and the midpoints — and every group's own midpoint — stand at the same
     * points for every attempt that shares an anchor. That step is the one home the clearance walk
     * shares, so the two walks can never disagree about the fine grid's own length; the price step below
     * coarsens the **grouping** alone and never the interval the sum is taken over.
     *
     * **The mark set does not change; which marks pay a read of their own does.** The walk is read as a
     * recursion over the chord's own fine intervals, from the whole chord down: one clearance read at a
     * span's midpoint proves the span where it is at least the span's half-length — no boundary of any
     * soft source can then lie inside, so the arm is constant and the span's product is **identically**
     * the fine sum it replaces, the same value added the same number of times in the same order, not a
     * near miss — an unproved span splits in half and each half is tested in turn, and a span at or
     * under the **floor** falls to the group path below. So the walk settles on the largest provable
     * spans and keeps the group and fine marks only where a boundary actually lives.
     *
     * The proof's own read is not a price read and is never counted in [PullTiming.priceReads]: a proved
     * span pays one price read whatever its length, beside the clearance read that proved it. Where the
     * walk cannot group at all — a price step at or under the fine one — the span proof is skipped and
     * the walk reads exactly as today, so only a coarse pass changes.
     *
     * The midpoint is what makes two segments comparable: an end-excluding walk discounts a short
     * segment by half a sample and a long one by almost nothing, so a chord would have looked dearer
     * than the cell path it replaces and no line would ever be pulled taut. A field with no price
     * reads 0 and the guard is inert.
     *
     * The price step is the context's own [PullContext.priceStepM] — a required value, the walk's
     * interior cell and never its fine one: a defaulted step would re-arm the collapse it exists to close.
     */
    internal fun softPriceSec(a: LatLng, b: LatLng, ctx: PullContext): Double {
        val dist = SpatialOperations.haversine(a, b)
        val sampleStep = clearanceStep(ctx.marginM)
        // The interval partition is a [MarkLattice] on the walk's own sampling step, its last interval
        // sized to the chord's remainder — so the midpoints, and every group's own midpoint, stand at
        // the same points whatever the chord's length, which is what a memo of the reads needs to hit.
        val marks = MarkLattice(dist, sampleStep)
        val steps = marks.count
        // The one home of the mark count: the partition's own interval count, so the tally equals the
        // model's marks and no reader has to re-derive them.
        ctx.timing?.addMarks(steps)
        val stepM = marks.stepM
        // The price step is floored at the fine one, exactly as the clearance walk floors its own, so
        // a coarse pass can only ever save reads. The group step is the walk's own lattice and step
        // read once, here, and handed to the grouping rather than recomputed on every call.
        val priceStep = ctx.priceStepM.coerceAtLeast(sampleStep)
        val groupStep = maxOf(1, floor(priceStep / stepM).toInt())
        // A walk whose step cannot group reads as today: the span proof exists to spare the reads a
        // group pays, so a step at or under the fine one leaves the fine midpoints alone.
        if (groupStep <= 1) {
            val acc = doubleArrayOf(0.0)
            groupPriceSec(a, b, marks, 0, steps, groupStep, ctx, acc)
            return acc[0]
        }
        // The recursion's floor: twice the price step, the length the group walk prices in two reads,
        // so a span at or under it goes straight to the group path and a short chord never pays more.
        val floorCount = maxOf(groupStep, floor(2.0 * priceStep / stepM).toInt())
        val acc = doubleArrayOf(0.0)
        spanPriceSec(a, b, marks, 0, steps, floorCount, groupStep, ctx, acc)
        return acc[0]
    }

    /**
     * **The chord's own seconds** — the travel it costs at the walk's pace plus the water it pays for.
     * This is the number every proof compares: a chord that is dearer in soft price but shorter in
     * distance can still be cheaper in total, and the guard must see that, or it keeps a longer, slower
     * line. A walk without a pace (`paceMps <= 0`, a fixture) reads the soft price alone, as before.
     */
    internal fun totalSec(a: LatLng, b: LatLng, ctx: PullContext): Double {
        val travel = if (ctx.paceMps > 0.0) SpatialOperations.haversine(a, b) / ctx.paceMps else 0.0
        return travel + softPriceSec(a, b, ctx)
    }

    /**
     * [softPriceSec] read as a recursion over the chord's own intervals `[i0, i1)`. A span at or under
     * [floorCount] intervals is the group walk's own; a longer span is tested by one clearance read at
     * its midpoint and, proved, priced from one price read there. An unproved span splits in half and
     * each half is tested in turn, so the walk finds the largest provable spans.
     *
     * **The sum is one accumulator, visited left to right**, never a tree of `left + right` pairs: a
     * proved span adds its reading once per interval in the same places the fine walk would, so the
     * total is bit-identical to the fine sum — the same double, not a near miss — which a floating
     * point re-association across a split would break.
     */
    private fun spanPriceSec(
        a: LatLng,
        b: LatLng,
        marks: MarkLattice,
        i0: Int,
        i1: Int,
        floorCount: Int,
        groupStep: Int,
        ctx: PullContext,
        acc: DoubleArray
    ) {
        val count = i1 - i0
        if (count <= floorCount) {
            groupPriceSec(a, b, marks, i0, i1, groupStep, ctx, acc)
            return
        }
        val halfM = marks.spanLengthM(i0, i1) / 2.0
        val mid = chordPoint(a, b, marks.spanMidT(i0, i1))
        val clearance = ctx.softClearanceM(mid)
        if (clearance < Double.MAX_VALUE && clearance >= halfM) {
            val price = ctx.field.softPriceSecPerMAt(mid) + ctx.berthRate(mid)
            ctx.timing?.addPriceReads(1)
            for (j in i0 until i1) acc[0] += price * marks.widthM(j)
            return
        }
        val midIndex = i0 + count / 2
        spanPriceSec(a, b, marks, i0, midIndex, floorCount, groupStep, ctx, acc)
        spanPriceSec(a, b, marks, midIndex, i1, floorCount, groupStep, ctx, acc)
    }

    /**
     * The group walk over the interval run `[i0, i1)`, adding into [acc]: successive intervals of the
     * price step's worth are grouped, a group is priced from **one** read where the field's declaration
     * proves no boundary stands inside it, and every unproved group keeps the fine midpoints, at their
     * lattice positions, in order. A group of one interval is its own fine interval and is never asked
     * for a proof, so a walk handed the fine step reads exactly as today. [groupStep] is the walk's own
     * lattice and step read once — the grouping is the same arithmetic, computed where it belongs.
     */
    private fun groupPriceSec(
        a: LatLng,
        b: LatLng,
        marks: MarkLattice,
        i0: Int,
        i1: Int,
        groupStep: Int,
        ctx: PullContext,
        acc: DoubleArray
    ) {
        var i = i0
        while (i < i1) {
            val k = minOf(groupStep, i1 - i)
            val halfM = marks.spanLengthM(i, i + k) / 2.0
            val proved = if (k > 1) {
                // `MAX_VALUE` is the declaration's own "no boundary named": a source that names none
                // can never be proved, and a group of one interval is never asked.
                val clearance = ctx.softClearanceM(chordPoint(a, b, marks.spanMidT(i, i + k)))
                clearance < Double.MAX_VALUE && clearance >= halfM
            } else {
                false
            }
            if (proved) {
                val price = readPrice(ctx, chordPoint(a, b, marks.spanMidT(i, i + k)))
                for (j in i until i + k) acc[0] += price * marks.widthM(j)
            } else {
                for (j in i until i + k) {
                    acc[0] += readPrice(ctx, chordPoint(a, b, marks.markT(j))) * marks.widthM(j)
                }
            }
            i += k
        }
    }

    /** [softPriceSec] accumulated along [path], so one span's price is a single subtraction. */
    internal fun softPricePrefix(path: List<LatLng>, ctx: PullContext): DoubleArray {
        val prefix = DoubleArray(path.size)
        for (i in 1 until path.size) {
            prefix[i] = prefix[i - 1] + softPriceSec(path[i - 1], path[i], ctx)
        }
        return prefix
    }

    /** [totalSec] accumulated along [path], so one span's total is a single subtraction. */
    internal fun totalPrefix(path: List<LatLng>, ctx: PullContext): DoubleArray {
        val prefix = DoubleArray(path.size)
        for (i in 1 until path.size) {
            prefix[i] = prefix[i - 1] + totalSec(path[i - 1], path[i], ctx)
        }
        return prefix
    }

    /**
     * The materialized walls' distance at [p], from the walk's memo where it holds the point and from
     * the field otherwise — one home for the read the clearance walk pays, so the memo is consulted at
     * every mark and no caller can forget it.
     */
    private fun readHard(ctx: PullContext, p: LatLng): Double =
        ctx.memo?.hardDistance(ctx.field, p) ?: ctx.field.hardDistanceM(p)

    /**
     * The soft price **rate** at [p] — seconds per metre, which is what the sums above need: a per-cell
     * cost multiplied by metres would carry the cell along with it and report seconds times metres. The
     * rate is the field's own cell price over the cell that priced it, so the walk prices a stretch of
     * line while the A\* keeps pricing cells, off one law.
     *
     * It reads from the walk's memo where that holds the point and from the field otherwise — and it is
     * the **one** home that decides whether a price read was paid, incrementing [PullTiming.priceReads]
     * on a miss alone, so a memo hit lowers the count without touching the sum. The memo is probed
     * **once**: a held point answers with its own double, a miss reads the field and stores the answer,
     * so a hit and a miss each cost one lookup and never two.
     */
    private fun readPrice(ctx: PullContext, p: LatLng): Double {
        val memo = ctx.memo
        val raw = if (memo == null) {
            ctx.timing?.addPriceReads(1)
            ctx.field.softPriceSecPerMAt(p)
        } else {
            val held = memo.priceOrNull(ctx.field, p)
            if (held != null) {
                held
            } else {
                val price = ctx.field.softPriceSecPerMAt(p)
                memo.putPrice(ctx.field, p, price)
                ctx.timing?.addPriceReads(1)
                price
            }
        }
        return raw + ctx.berthRate(p)
    }
}

/**
 * **The two ends' carved approaches, as the pull reads them** — the stretch each end may travel on
 * beside the margin-radius disc around it. One value rather than two lists, because the exemption is
 * one rule read twice: a berth channel longer than the margin is not covered by the disc, so a chord
 * standing on the stretch must be exempt exactly as a sample within the disc already is.
 *
 * [NONE] is what a solve without a carve passes — no stretch exempt, which is the shipped behaviour.
 */
data class EndApproaches(
    val start: List<LatLng> = emptyList(),
    val aim: List<LatLng> = emptyList()
) {
    companion object {
        val NONE = EndApproaches()
    }
}

/** The pull's own causes of refusal: the land margin and the price guard. */
internal enum class ChordRefusal { LAND, PRICE }

/**
 * **Which of the pull's two refusals fired, counted per answer** — the land margin and the price — so
 * a line that collapses nothing names the test that refused it instead of leaving candidates to
 * guesswork.
 *
 * Counters are updated where [MultipassPull.pull] decides and read once per answer: nothing is emitted per
 * chord, nothing is measured twice, and a chord re-walked from a new anchor counts once per evaluation.
 */
class PullRefusals {

    /** Chords refused because a sample stood nearer than `marginM` to a hard wall. */
    var land = 0
        private set

    /** Chords refused because their own price exceeded the cell path's over the span replaced. */
    var price = 0
        private set

    internal fun record(cause: ChordRefusal) {
        when (cause) {
            ChordRefusal.LAND -> land++
            ChordRefusal.PRICE -> price++
        }
    }
}

/**
 * **What the walk spent, and where** — the clearance walk and the price walk, in nanoseconds, told
 * apart because the two answer different questions: the clearance half is the live coastline read a
 * sampled walk exists to cut down, and the price half is the priced water's own read, which no
 * clearance proof covers — so a win must be read on the first and never on their sum.
 *
 * Handed in where a reader wants it and left `null` where none does, exactly like [PullRefusals]: the
 * walk's verdicts do not depend on carrying one, and nothing is emitted per chord.
 */
class PullTiming {

    /** Nanoseconds spent in the clearance walk — the marks' own hard readings. */
    var clearanceNanos: Long = 0L
        private set

    /** Nanoseconds spent in the price walk — the prefix sum and each chord's own price. */
    var priceNanos: Long = 0L
        private set

    /**
     * The price reads the price walk actually paid — one per proved group and one per fine midpoint a
     * refused group keeps. Beside [priceMs] it is how the coarsening's saving is read rather than
     * inferred from the clock.
     */
    var priceReads: Long = 0L
        private set

    /**
     * **The intervals the price walk walked** — one per whole fine step of a chord's own lattice, its
     * last interval sized to the chord's remainder, summed over every `softPriceSec` partition the walk
     * makes. It is the model's own mark count, so `priceReads / marks` reads the density the memo leaves
     * behind and `1 − it` the memo's own hit rate — a fall no clock and no sum can show. It is
     * incremented where the partition is made and nowhere else, and it reads no verdict, sum or step.
     */
    var marks: Long = 0L
        private set

    /**
     * The price reads the walk answered from its own [MarkMemo] rather than the field — the price half
     * of the memo's tally, copied from the memo at the walk's own end.
     */
    var memoPriceHits: Long = 0L
        private set

    /**
     * The hard-wall distance reads the walk answered from its own [MarkMemo] rather than the field — the
     * clearance half of the memo's tally, copied from the memo at the walk's own end.
     */
    var memoHardHits: Long = 0L
        private set

    /** The clearance walk's own sampling step (m), read from the [PullSetup] the walk was handed. */
    var stepM: Double = 0.0
        private set

    /** The price walk's own grouping step (m), read from the [PullSetup] the walk was handed. */
    var priceStepM: Double = 0.0
        private set

    /** [clearanceNanos] in the log's own unit. */
    val clearanceMs: Double get() = clearanceNanos / NANOS_PER_MS

    /** [priceNanos] in the log's own unit. */
    val priceMs: Double get() = priceNanos / NANOS_PER_MS

    internal fun addClearance(nanos: Long) {
        clearanceNanos += nanos
    }

    internal fun addPrice(nanos: Long) {
        priceNanos += nanos
    }

    internal fun addPriceReads(count: Int) {
        priceReads += count
    }

    internal fun addMarks(count: Int) {
        marks += count
    }

    /**
     * Copies the walk's two steps and its memo's two hit counts into the tally at the walk's own end,
     * read from the [PullSetup] and [MarkMemo] the walk was handed — so the line proves the steps in
     * force and prices the memo on the water it actually walked.
     */
    internal fun recordWalk(setup: PullSetup, memo: MarkMemo?) {
        stepM = setup.coarseStepM
        priceStepM = setup.priceStepM
        memoPriceHits = memo?.priceHits ?: 0L
        memoHardHits = memo?.hardHits ?: 0L
    }
}
