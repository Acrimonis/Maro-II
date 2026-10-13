package ykws.android.maro.spatial.multipass

import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.spatial.SpatialOperations
import ykws.android.maro.spatial.Units
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.time.Instant
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tan

/**
 * **The missed-turn listing — read-only, no behaviour.**
 *
 * It reads the route captures the user saved under `_routes_missed_turn_/` and lists every bend the
 * corner pass left as it stands. Its premise is what those files are: a route's stored points are the
 * clock's line laid over the pass's own output, so a **rounded** corner arrives as a run of arc samples
 * while a **refused** one arrives as its bare vertex: carrying exactly `route.turn.minSpeedKn` where the
 * refusal was about the turn, and no corner ceiling at all where the bend was too shallow or too
 * degenerate to have asked a radius question. Which of the two it carries is part of the fingerprint
 * this listing reads. Each row carries the vertex's own `at=`, its carried `speed=`, and **both** gaps
 * to its neighbours, `dtIn=`/`dtOut=`: a clock sample's gap is a fraction of a second while the pass's
 * own vertex sits a whole sample period away, which is how the two are told apart.
 *
 * Per bend it prints what the pass itself would have needed: the turn angle, both legs, the room
 * `route.turn.reachFraction × min(legIn, legOut)` — the whole shorter leg — and `r_floor · tan(δ/2)`, a
 * **lower bound** on the tangent a curve at the floor's radius demands, the spirals a real curve carries
 * lengthening it further. Where that bound already exceeds the room, no curve at the floor's own radius
 * fits and the pass draws below the floor, down to its own 1 kn bottom — so the verdict reads `ROOM`, a
 * statement about the turn's room, never a proof of refusal. Every other flagged bend reads `OPEN`: a
 * curve at the floor's radius fits, so the water-side clearance test — coast, depth or a speed zone — is
 * what must have refused it, and only the pass's own tally can say which of the three.
 *
 * No bend is left invisible. After the flagged rows, each capture prints its **three strongest bends
 * whatever their angle**, in the same fields; a bend under the pass's own `MIN_TURN_DEG` reads
 * `SHALLOW`, the verdict its `CornerOutcome` gives it, so a bend the threshold hides still shows here.
 *
 * The values come from `app/src/main/assets/maro.properties`, the source of truth for every value, with
 * [`AppConfig`]'s code defaults as the fallback, so the listing reads the law the app ships rather than
 * the fallback a JVM test would otherwise see.
 *
 * Two limits are stated rather than papered over. A bend whose kink happens to fall across a clock
 * sample can split into two sub-threshold steps and stay unseen here; and a bend the pass never
 * *considered*, because the search line carried no vertex there at all, is invisible to a reader of the
 * drawn line. The pass's own tally is what closes both.
 */
class RouteMissedTurnListingTest {

    @Test
    fun listEveryBendThePassLeftSharp() {
        val root = repoRoot()
        assumeTrue("no repository root above the test's working directory", root != null)
        val dir = File(root!!, CAPTURE_DIR)
        assumeTrue("no capture folder at ${dir.path}", dir.isDirectory)
        val files = dir.listFiles { f: File -> f.isFile && f.name.endsWith(".gpx") }
            ?.sortedBy { it.name }
            .orEmpty()
        assumeTrue("no capture to list", files.isNotEmpty())

        val law = TurnLaw.read(File(root, "app/src/main/assets/maro.properties"))
        val out = StringBuilder()
        out.appendLine("=== missed-turn listing — ${files.size} captured route(s) ===")
        out.appendLine(
            "law  lateralAccel=${fmt(law.aLatMps2, 2)} m/s^2  floor=${fmt(law.floorKn)} kn  " +
                "transition=${fmt(law.transitionSec)} s  reachFraction=${fmt(law.reachFraction, 2)}  " +
                "floorRadius=${fmt(law.floorRadiusM)} m"
        )
        out.appendLine(
            "row  at=<lat>,<lon> 6dp · speed=<kn> · dtIn/dtOut=<s> = the gap to each neighbour;  " +
                "flagged = the drawn line still turns by ${fmt(MIN_TURN_DEG, 0)} degrees or more there;  " +
                "ROOM = the floor's own tangent exceeds the room, so a curve there falls below the floor;  " +
                "OPEN = geometry permits a curve, so coast, depth or a speed zone refused it"
        )
        out.appendLine(
            "after the flagged rows, the strongest ${STRONGEST_SHOWN} bends whatever their angle — " +
                "SHALLOW = under the pass's ${fmt(MIN_TURN_DEG, 0)}-degree threshold, so it never tried"
        )

        var flagged = 0
        for (file in files) {
            val points = readRoute(file)
            val bends = (1 until points.size - 1).map { i -> classify(points, i, law) }
            val rows = bends.filter { it.flagged }
            flagged += rows.size
            val tally = rows.groupingBy { it.verdict }.eachCount()
                .entries.joinToString(" ") { "${it.key}=${it.value}" }
            out.appendLine("")
            out.appendLine("${file.name}  points=${points.size}  flagged=${rows.size}  ${tally.ifEmpty { "-" }}")
            for (row in rows) out.appendLine("  ${row.asLine()}")
            val strongest = bends.sortedByDescending { it.turnDeg }.take(STRONGEST_SHOWN)
            out.appendLine("  strongest ${strongest.size} whatever their angle:")
            for (row in strongest) out.appendLine("    ${row.asLine()}")
        }
        out.appendLine("")
        out.appendLine("total flagged bends: $flagged")

        val log = File(root, LOG_NAME)
        log.writeText(out.toString())
        println(out)
        assertTrue("the listing names each capture", out.contains(files.first().name))
    }

    /** One bend: the geometry the pass would have read, its own clock, and the verdict the file alone can give. */
    private data class Row(
        val idx: Int,
        val at: LatLng,
        val turnDeg: Double,
        val legInM: Double,
        val legOutM: Double,
        val roomM: Double,
        val floorNeedM: Double,
        val speedKn: Double?,
        val dtInS: Double?,
        val dtOutS: Double?,
        val verdict: String,
        val flagged: Boolean
    ) {
        fun asLine(): String =
            "idx=$idx at=${fmt(at.latitude, 6)},${fmt(at.longitude, 6)} turn=${fmt(turnDeg)}deg " +
                "in=${fmt(legInM)}m out=${fmt(legOutM)}m " +
                "room=${fmt(roomM)}m floorNeed=${fmt(floorNeedM)}m " +
                "speed=${speedKn?.let { fmt(it, 2) } ?: "?"}kn " +
                "dtIn=${gap(dtInS)}s dtOut=${gap(dtOutS)}s why=$verdict"

        /** A gap in seconds, three decimals, `?` where the capture carries no clock for one side. */
        private fun gap(seconds: Double?): String = seconds?.let { fmt(it, 3) } ?: "?"
    }

    private fun classify(points: List<CapturedPoint>, i: Int, law: TurnLaw): Row {
        val prev = points[i - 1]
        val at = points[i]
        val next = points[i + 1]
        val turnDeg = abs(
            turnBetween(
                SpatialOperations.initialBearing(prev.at, at.at),
                SpatialOperations.initialBearing(at.at, next.at)
            )
        )
        val legInM = SpatialOperations.haversine(prev.at, at.at)
        val legOutM = SpatialOperations.haversine(at.at, next.at)
        val roomM = law.reachFraction * min(legInM, legOutM)
        val floorNeedM = law.floorRadiusM * tan(Math.toRadians(turnDeg) / 2.0)
        val flagged = turnDeg >= MIN_TURN_DEG
        // The pass's own reversal guard, read exactly: `sin(delta)` vanishing is what refuses the fit.
        val reversal = sin(Math.toRadians(turnDeg)) < 1e-6
        val verdict = when {
            !flagged -> "SHALLOW"
            reversal -> "REVERSAL"
            // The floor's own tangent already exceeds the room, so no curve at the floor fits and the
            // pass draws below it — a reading of the turn's room, never a proof of refusal.
            floorNeedM > roomM -> "ROOM"
            else -> "OPEN"
        }
        return Row(
            i, at.at, turnDeg, legInM, legOutM, roomM, floorNeedM, at.speedKn,
            gapSeconds(prev.timeMs, at.timeMs), gapSeconds(at.timeMs, next.timeMs), verdict, flagged
        )
    }

    /** The signed heading change, folded into (-180, 180]. */
    private fun turnBetween(inBearing: Double, outBearing: Double): Double {
        var d = (outBearing - inBearing) % 360.0
        if (d > 180.0) d -= 360.0
        if (d < -180.0) d += 360.0
        return d
    }

    /** One captured point: where it stands, the speed the file carries (null where absent), its clock. */
    private data class CapturedPoint(val at: LatLng, val speedKn: Double?, val timeMs: Long?)

    /**
     * The drawn line out of one capture: every `<trkpt lat lon>`, its `<speed>` and its `<time>`, closed
     * by `</trkpt>`. The whole file is scanned as one text, so a capture written with no line break at all
     * reads the same as one carrying a trkpt per line.
     */
    private fun readRoute(file: File): List<CapturedPoint> {
        val out = ArrayList<CapturedPoint>()
        val text = file.readText()
        var from = 0
        while (true) {
            val open = text.indexOf("<trkpt", from)
            if (open < 0) break
            val close = text.indexOf("</trkpt>", open)
            if (close < 0) break
            val body = text.substring(open, close)
            val lat = attribute(body, "lat")
            val lon = attribute(body, "lon")
            if (lat != null && lon != null) {
                out.add(CapturedPoint(LatLng(lat, lon), tagValue(body, "speed")?.toDoubleOrNull(), timeMs(body)))
            }
            from = close + "</trkpt>".length
        }
        return out
    }

    private fun attribute(tag: String, name: String): Double? =
        Regex("""\b$name="([-\d.]+)"""").find(tag)?.groupValues?.get(1)?.toDoubleOrNull()

    /** The text of the first `<name>…</name>` of one trkpt, or null where the capture omits it. */
    private fun tagValue(body: String, name: String): String? =
        Regex("""<$name>([^<]*)</$name>""").find(body)?.groupValues?.get(1)?.trim()

    /** The point's clock in epoch milliseconds, or null where the capture carries no parsable time. */
    private fun timeMs(body: String): Long? =
        tagValue(body, "time")?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }

    /** The gap between two clocks, in seconds, or null where either side carries none. */
    private fun gapSeconds(from: Long?, to: Long?): Double? =
        if (from == null || to == null) null else (to - from) / 1000.0

    /** The turn law the app ships, read from the properties file with the code defaults behind it. */
    private data class TurnLaw(
        val aLatMps2: Double,
        val floorKn: Double,
        val transitionSec: Double,
        val reachFraction: Double
    ) {
        /** `r = v² / a_lat` at the floor: the tightest curve the pass will draw. */
        val floorRadiusM: Double get() = Units.knotsToMps(floorKn).let { it * it } / aLatMps2

        companion object {
            fun read(props: File): TurnLaw {
                val map = if (props.isFile) {
                    props.readLines()
                        .map { it.trim() }
                        .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains('=') }
                        .associate { it.substringBefore('=').trim() to it.substringAfter('=').trim() }
                } else {
                    emptyMap()
                }
                fun value(key: String, fallback: Double): Double =
                    map[key]?.toDoubleOrNull() ?: fallback
                return TurnLaw(
                    aLatMps2 = value("route.turn.lateralAccelMps2", AppConfig.routeTurnLateralAccelMps2),
                    floorKn = value("route.turn.minSpeedKn", AppConfig.routeTurnMinSpeedKn),
                    transitionSec = value("route.turn.transitionSec", AppConfig.routeTurnTransitionSec),
                    reachFraction = value("route.turn.reachFraction", AppConfig.routeTurnReachFraction)
                )
            }
        }
    }

    /** The repository root, found by walking up until the capture folder's own directory is met. */
    private fun repoRoot(): File? {
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            if (File(dir, CAPTURE_DIR).isDirectory) return dir
            dir = dir.parentFile
        }
        return null
    }

    private companion object {
        const val CAPTURE_DIR = "_routes_missed_turn_"
        const val LOG_NAME = "test-missed-turn-listing.log"

        /** The pass's own `MIN_TURN_DEG`, private there — repeated here, and said so. */
        const val MIN_TURN_DEG = 10.0

        /** How many of a capture's strongest bends are printed whatever their angle. */
        const val STRONGEST_SHOWN = 3
    }
}
