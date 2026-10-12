package ykws.android.maro.data.regulation

import org.junit.Test
import java.io.File

/**
 * Read-only audit of the baked `nice-menton.bin`: report every regulated zone whose
 * baked [RegulatedZone.zoneType] disagrees with its own signals (speed value, codes,
 * description). Changes no production code and re-bakes nothing.
 *
 * Run:
 *   gradlew testDebugUnitTest --tests "ykws.android.maro.data.regulation.RegulatedZoneTypeAudit" --rerun-tasks
 */
class RegulatedZoneTypeAudit {

    @Test
    fun `audit baked zone types against their own signals`() {
        val repoDir = File(System.getProperty("maro.repoDir") ?: "..")
        val binFile = File(repoDir, "data/app-assets/regulated-zones/nice-menton.bin")
        if (!binFile.exists()) {
            println("[AUDIT] Prebaked .bin not found at ${binFile.absolutePath}")
            return
        }
        val zoneSet = RegulatedZoneSerializer.deserialize(binFile.readBytes())
        val zones = zoneSet.zones

        val bucket1 = zones.filter {
            it.speedLimitKn != null && it.zoneType != RegulatedZoneType.SPEED_LIMIT
        }
        val bucket2 = zones.filter {
            descHasSpeed(it.description) && it.speedLimitKn == null &&
                it.zoneType != RegulatedZoneType.SPEED_LIMIT
        }
        val bucket3 = zones.filter {
            (it.zoneType == RegulatedZoneType.ACCESS_PROHIBITED ||
                it.zoneType == RegulatedZoneType.ANCHORING_PROHIBITED) &&
                descHasSpeed(it.description) &&
                !descHasAnchor(it.description) && !descHasAccess(it.description)
        }
        val bucket4 = zones.filter {
            it.zoneType == RegulatedZoneType.OTHER && descActionable(it.description)
        }

        println("=".repeat(110))
        println("  REGULATED ZONE TYPE AUDIT — nice-menton.bin (read-only)")
        println("  File: ${binFile.absolutePath}")
        println("=".repeat(110))

        println("\nTOTAL ZONES: ${zones.size}")
        println("PER-BUCKET COUNTS")
        println("  1 SPEED MISCLASSIFIED     (speedLimitKn != null, type != SPEED_LIMIT)  : ${bucket1.size}")
        println("  2 SPEED TEXT NO TYPE      (speed text, speedLimitKn == null, type != SPEED_LIMIT): ${bucket2.size}")
        println("  3 WRONG DIRECTION         (access/anchoring type but speed text)        : ${bucket3.size}")
        println("  4 UNDER-CLASSIFIED OTHER  (OTHER type with an actionable keyword)       : ${bucket4.size}")
        println("WOULD CHANGE TYPE under \"speedLimitKn != null -> SPEED_LIMIT\": ${bucket1.size}")

        printBucket(
            1,
            "SPEED MISCLASSIFIED — speedLimitKn != null but zoneType != SPEED_LIMIT",
            bucket1
        )
        printBucket(
            2,
            "SPEED TEXT NO TYPE — speed in description, no speedLimitKn, type != SPEED_LIMIT",
            bucket2
        )
        printBucket(
            3,
            "WRONG DIRECTION — access/anchoring typed zone whose text is a speed limit",
            bucket3
        )
        printBucket(
            4,
            "UNDER-CLASSIFIED OTHER — OTHER type with an actionable keyword",
            bucket4
        )

        printCodeTable(zones)

        val flagged = (bucket1 + bucket2 + bucket3 + bucket4).toSet()
        println("\nALL FLAGGED ZONES (deduplicated, ${flagged.size}) — " +
            "name | sourceRef | sourceLayer | type | speedLimitKn | restrictionCodes")
        for (z in flagged.sortedBy { it.name }) {
            printZone(z)
        }
        println()
    }

    private fun printBucket(index: Int, title: String, list: List<RegulatedZone>) {
        println("\n[$index] $title (${list.size})")
        if (list.isEmpty()) {
            println("    (none)")
            return
        }
        for (z in list.sortedBy { it.name }) printZone(z)
    }

    private fun printZone(z: RegulatedZone) {
        val codes = zoneCodes(z)
        val desc = z.description.replace("\n", " ").trim()
        println(
            "    - name=\"${z.name}\" ref=${z.sourceRef} layer=${z.sourceLayer} " +
                "type=${z.zoneType.name} speed=${z.speedLimitKn} codes=$codes"
        )
        if (desc.isNotBlank()) println("        desc=\"${desc.take(120)}\"")
    }

    private fun printCodeTable(zones: List<RegulatedZone>) {
        val allCodes = zones.flatMap(::zoneCodes).distinct().sorted()
        println("\n[5] PER-CODE TABLE — code | zone count | zone types observed | zones carrying a speed limit")
        if (allCodes.isEmpty()) {
            println("    (no restriction codes)")
            return
        }
        for (code in allCodes) {
            val carriers = zones.filter { code in zoneCodes(it) }
            val types = carriers.map { it.zoneType }.distinct().sortedBy { it.name }
            val withSpeed = carriers.count { it.speedLimitKn != null }
            println(
                "    code=%-4d zones=%-4d types=%s withSpeed=%d".format(
                    code,
                    carriers.size,
                    types.joinToString(", ") { it.name },
                    withSpeed
                )
            )
        }
    }

    private fun zoneCodes(zone: RegulatedZone): List<Int> =
        zone.restrictionCodes.ifEmpty { listOfNotNull(zone.restrictionCode) }

    private fun descHasSpeed(desc: String): Boolean =
        SPEED_TEXT.containsMatchIn(desc.lowercase())

    private fun descHasAnchor(desc: String): Boolean =
        ANCHOR_TEXT.containsMatchIn(desc.lowercase())

    private fun descHasAccess(desc: String): Boolean =
        ACCESS_TEXT.containsMatchIn(desc.lowercase())

    private fun descActionable(desc: String): Boolean {
        val d = desc.lowercase()
        return SPEED_TEXT.containsMatchIn(d) ||
            FISHING_TEXT.containsMatchIn(d) ||
            ANCHOR_TEXT.containsMatchIn(d) ||
            DIVE_TEXT.containsMatchIn(d)
    }

    private companion object {
        val SPEED_TEXT = Regex(
            """speed|vitesse|knots?|noeuds?|nœuds?|\bkts\b|\bkn\b|\bnds?\b""",
            RegexOption.IGNORE_CASE
        )
        val FISHING_TEXT = Regex(
            """fishing|\bfish\b|p[êe]che|pêch""",
            RegexOption.IGNORE_CASE
        )
        val ANCHOR_TEXT = Regex(
            """anchor|mouillage|mouiller|ancre|anchorage""",
            RegexOption.IGNORE_CASE
        )
        val DIVE_TEXT = Regex(
            """diving|\bdive\b|plong""",
            RegexOption.IGNORE_CASE
        )
        val ACCESS_TEXT = Regex(
            """acc[èe]s|access|entry|entr[ée]e""",
            RegexOption.IGNORE_CASE
        )
    }
}
