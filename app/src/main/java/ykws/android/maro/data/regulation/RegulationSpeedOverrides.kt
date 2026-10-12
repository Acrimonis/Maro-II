package ykws.android.maro.data.regulation

import ykws.android.maro.data.model.LatLng
import ykws.android.maro.spatial.SpatialOperations
import java.io.File
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.text.Normalizer
import java.util.Locale

/**
 * App-side control over the regulated-zone set, driven by the
 * `zones.properties` asset that ships next to `maro.properties`.
 *
 * Three key families, one block per zone:
 *
 * - `regulatedZone.speedOverride.<key>=<kn>` — replaces the zone's speed limit;
 *   setting a value on a zone that carries none turns it into a speed zone at
 *   that value;
 * - `regulatedZone.speedRoute.<key>=<kn>` — a routing-only speed: the route engine
 *   prices the zone at this value while every other surface keeps the limit above;
 *   blank for none;
 * - `regulatedZone.ignore.<key>=true` — drops the zone from the set, so it is
 *   never drawn, listed or priced.
 *
 * [RegulatedZonesRepository] reads the asset at load time and [apply]s it to the
 * baked `.bin`, so the file ships in the APK and the values are handled app side.
 *
 * The bake seeds the file (see `RegulatedZonePrebakeTest`): one entry per zone,
 * add-only — a zone already present keeps its speed value, its ignore flag and
 * its comment text, so a manual edit survives every re-bake, and a zone that
 * vanished from the sources is kept as a `# stale` line.
 *
 * Every key and comment is folded to ASCII (`é` → `e`, `ç` → `c`) so the file
 * stays pure basic charset.
 */
object RegulationSpeedOverrides {

    const val SPEED_PREFIX = "regulatedZone.speedOverride."
    const val SPEED_ROUTE_PREFIX = "regulatedZone.speedRoute."
    const val IGNORE_PREFIX = "regulatedZone.ignore."

    /** Asset name, resolved at the assets root next to `maro.properties`. */
    const val FILE_NAME = "zones.properties"

    /** The hand-edited values read back from the file. */
    data class Overrides(
        val speeds: Map<String, Double>,
        val ignores: Map<String, Boolean>,
        val routeSpeeds: Map<String, Double> = emptyMap()
    )

    /** Resolve the override file for a repo root directory. */
    fun file(repoDir: File): File =
        File(repoDir, "app/src/main/assets").resolve(FILE_NAME)

    // ── Folding and keys ────────────────────────────────────────────────────────

    /**
     * Fold a string to ASCII: NFD-normalise, strip combining marks, then turn
     * any character outside `[A-Za-z0-9._-]` into `_`. Used for keys.
     */
    fun asciiFold(value: String): String {
        val decomposed = Normalizer.normalize(value, Normalizer.Form.NFD)
        val stripped = decomposed.replace(Regex("\\p{M}"), "")
        return stripped.map { ch ->
            when {
                ch in 'a'..'z' || ch in 'A'..'Z' || ch in '0'..'9' ||
                    ch == '.' || ch == '_' || ch == '-' -> ch
                else -> '_'
            }
        }.joinToString("").trim('_', '-')
    }

    /**
     * The stable key a zone is addressed by. Chain: SHOM inspireid
     * ([RegulatedZone.sourceRef]), else the legal decree ref, else the folded
     * name plus a centroid slug; a fully blank zone falls back to `zone` plus
     * the centroid slug.
     */
    fun keyFor(zone: RegulatedZone): String {
        val sourceRef = zone.sourceRef.trim().takeIf { it.isNotEmpty() }
        val decreeRef = zone.legalDecreeRef?.trim()?.takeIf { it.isNotEmpty() }
        val name = zone.name.trim().takeIf { it.isNotEmpty() }

        val folded = when {
            sourceRef != null -> asciiFold(sourceRef)
            decreeRef != null -> asciiFold(decreeRef)
            name != null -> asciiFold(name) + centroidSuffix(zone)
            else -> "zone" + centroidSuffix(zone)
        }
        return folded.ifBlank { "zone" + centroidSuffix(zone) }
    }

    // ── Loading ─────────────────────────────────────────────────────────────────

    /** Read the hand-edited speed overrides and ignore flags from [file]. */
    fun load(file: File): Overrides =
        if (file.exists()) fromParsed(parse(file.readLines())) else EMPTY

    /** Read the hand-edited values from an asset [input] stream. */
    fun load(input: InputStream): Overrides =
        fromParsed(parse(input.bufferedReader(StandardCharsets.UTF_8).readLines()))

    private fun fromParsed(parsed: Parsed): Overrides {
        val speeds = LinkedHashMap<String, Double>()
        val routeSpeeds = LinkedHashMap<String, Double>()
        val ignores = LinkedHashMap<String, Boolean>()
        for ((key, block) in parsed.blocks) {
            block.speed?.let { speeds[key] = it }
            block.route?.let { routeSpeeds[key] = it }
            block.ignore?.let { ignores[key] = it }
        }
        return Overrides(speeds, ignores, routeSpeeds)
    }

    private val EMPTY = Overrides(emptyMap(), emptyMap())

    // ── Applying ────────────────────────────────────────────────────────────────

    /**
     * Apply [overrides] to [zones]: an ignored zone is dropped, every survivor
     * whose key carries a speed override gets its [RegulatedZone.speedLimitKn]
     * replaced (a zone with none thereby becomes a speed zone), a `speedRoute`
     * value sets the routing-only [RegulatedZone.speedRouteKn], and the rest are
     * returned untouched.
     */
    fun apply(zones: List<RegulatedZone>, overrides: Overrides): List<RegulatedZone> {
        if (overrides.speeds.isEmpty() && overrides.routeSpeeds.isEmpty() &&
            overrides.ignores.isEmpty()
        ) return zones
        return zones.asSequence()
            .filter { overrides.ignores[keyFor(it)] != true }
            .map { zone ->
                val key = keyFor(zone)
                val speed = overrides.speeds[key]
                val route = overrides.routeSpeeds[key]
                if (speed == null && route == null) {
                    zone
                } else {
                    zone.copy(
                        speedLimitKn = speed ?: zone.speedLimitKn,
                        speedRouteKn = route ?: zone.speedRouteKn
                    )
                }
            }
            .toList()
    }

    // ── Seeding ─────────────────────────────────────────────────────────────────

    /**
     * Seed the file at [file] from [zones] (already deduplicated and sorted).
     *
     * One block per zone: a fresh full-info comment block, whose `name` field
     * keeps the previous entry's label when one was set (so a manual renaming
     * survives), then a `speedOverride` line (empty when the zone has no speed
     * and no edit) and an `ignore` line (the previous flag or `false`). A key in
     * the file that no longer matches any zone is kept as a `# stale` line. The
     * write is atomic.
     */
    fun seed(file: File, zones: List<RegulatedZone>) {
        val parsed = parse(if (file.exists()) file.readLines() else emptyList())
        val previous = parsed.blocks

        val sb = StringBuilder(HEADER)
        val emitted = LinkedHashSet<String>()

        for (zone in zones) {
            val key = keyFor(zone)
            emitted += key
            val prev = previous[key]

            for (line in generatedComments(zone, prev?.label, prev?.route)) sb.append(line).append('\n')

            val speed = prev?.speed ?: zone.speedLimitKn
            sb.append(SPEED_PREFIX).append(key).append('=')
                .append(speed?.let { formatKn(it) } ?: "").append('\n')

            val route = prev?.route ?: zone.speedRouteKn
            sb.append(SPEED_ROUTE_PREFIX).append(key).append('=')
                .append(route?.let { formatKn(it) } ?: "").append('\n')

            val ignore = prev?.ignore ?: false
            sb.append(IGNORE_PREFIX).append(key).append('=').append(ignore).append('\n')

            sb.append('\n')
        }

        for ((key, block) in previous) {
            if (key in emitted) continue
            sb.append("# stale (not in current bake): ")
                .append(SPEED_PREFIX).append(key).append('=')
                .append(block.speed?.let { formatKn(it) } ?: "").append('\n')
            sb.append("# stale (not in current bake): ")
                .append(SPEED_ROUTE_PREFIX).append(key).append('=')
                .append(block.route?.let { formatKn(it) } ?: "").append('\n')
            sb.append("# stale (not in current bake): ")
                .append(IGNORE_PREFIX).append(key).append('=')
                .append(block.ignore ?: false).append('\n')
        }

        for (line in parsed.staleLines) sb.append(line).append('\n')

        writeAtomically(file, sb.toString())
    }

    // ── Parsing ─────────────────────────────────────────────────────────────────

    private class Block {
        var label: String? = null
        var speed: Double? = null
        var route: Double? = null
        var ignore: Boolean? = null
    }

    private class Parsed(
        val blocks: LinkedHashMap<String, Block>,
        val staleLines: List<String>
    )

    private class KeyLine(val key: String, val kind: Kind, val value: String)

    private enum class Kind { SPEED, ROUTE, IGNORE }

    private fun parse(lines: List<String>): Parsed {
        val blocks = LinkedHashMap<String, Block>()
        val stale = mutableListOf<String>()
        var pendingLabel: String? = null

        for (raw in lines) {
            val trimmed = raw.trim()
            when {
                trimmed.isEmpty() -> pendingLabel = null // header / entry separator
                trimmed.startsWith("#") ->
                    if (trimmed.startsWith("# stale")) {
                        stale += raw
                    } else {
                        labelFromComment(trimmed)?.let { pendingLabel = it }
                    }
                else -> {
                    val keyLine = parseKeyLine(trimmed)
                    if (keyLine == null) {
                        pendingLabel = null
                    } else {
                        val block = blocks.getOrPut(keyLine.key) { Block() }
                        if (block.label == null) block.label = pendingLabel
                        pendingLabel = null
                        when (keyLine.kind) {
                            Kind.SPEED -> block.speed = keyLine.value.toDoubleOrNull()
                            Kind.ROUTE -> block.route = keyLine.value.toDoubleOrNull()
                            Kind.IGNORE -> block.ignore = keyLine.value.toBooleanOrNull()
                        }
                    }
                }
            }
        }
        return Parsed(blocks, stale)
    }

    /** The `# name : X` label of a comment line, when X is a real override. */
    private fun labelFromComment(line: String): String? {
        val body = line.removePrefix("#").trim()
        val idx = body.indexOf(':')
        if (idx < 0) return null
        if (!body.substring(0, idx).trim().equals("name", ignoreCase = true)) return null
        return body.substring(idx + 1).trim().takeIf { it.isNotEmpty() && it != "(unnamed)" }
    }

    private fun parseKeyLine(line: String): KeyLine? {
        val eq = line.indexOf('=')
        if (eq < 0) return null
        val key = line.substring(0, eq).trim()
        val value = line.substring(eq + 1).trim()
        return when {
            key.startsWith(SPEED_ROUTE_PREFIX) ->
                KeyLine(key.removePrefix(SPEED_ROUTE_PREFIX), Kind.ROUTE, value)
            key.startsWith(SPEED_PREFIX) ->
                KeyLine(key.removePrefix(SPEED_PREFIX), Kind.SPEED, value)
            key.startsWith(IGNORE_PREFIX) ->
                KeyLine(key.removePrefix(IGNORE_PREFIX), Kind.IGNORE, value)
            else -> null
        }
    }

    private fun String.toBooleanOrNull(): Boolean? = when (lowercase()) {
        "true", "1", "yes" -> true
        "false", "0", "no" -> false
        else -> null
    }

    // ── Comment block ───────────────────────────────────────────────────────────

    /**
     * Comment-only fold: accents become their ASCII base and any remaining
     * non-ASCII character becomes `_`, while spaces and punctuation stay as
     * written so a description reads naturally.
     */
    private fun foldForComment(value: String): String {
        val decomposed = Normalizer.normalize(value, Normalizer.Form.NFD)
        val stripped = decomposed.replace(Regex("\\p{M}"), "")
        return stripped.map { ch -> if (ch.code < 128) ch else '_' }.joinToString("")
    }

    private fun generatedComments(
        zone: RegulatedZone,
        label: String? = null,
        route: Double? = null
    ): List<String> {
        val c = centroid(zone)
        return listOf(
            field("name", label ?: foldForComment(zone.name.ifBlank { "(unnamed)" })),
            field("nameWs", foldForComment(zone.name).ifBlank { "-" }),
            field("type", zone.zoneType.name),
            field("speed", zone.speedLimitKn?.let { "${formatKn(it)} kn" } ?: "-"),
            field("route", route?.let { "${formatKn(it)} kn" } ?: "-"),
            field("source", foldForComment(zone.source).ifBlank { "-" }),
            field("layer", foldForComment(zone.sourceLayer.orEmpty()).ifBlank { "-" }),
            field("inspireid", foldForComment(zone.sourceRef).ifBlank { "-" }),
            field("decree", foldForComment(zone.legalDecreeRef.orEmpty()).ifBlank { "-" }),
            field("speedSrc", zone.speedSource?.name ?: "-"),
            field("restriction", zone.restrictionCode?.toString() ?: "-"),
            field("codes", zone.restrictionCodes.joinToString(",").ifBlank { "-" }),
            field("class", zone.classification?.let { foldForComment(it.toString()) } ?: "-"),
            field("vessel", vesselText(zone)),
            field("holes", zone.holes.size.toString()),
            field("vertices", zone.outerRing.size.toString()),
            field(
                "desc",
                foldForComment(zone.description.replace('\n', ' ').replace('\r', ' ')).ifBlank { "-" }
            ),
            field(
                "centroid",
                String.format(Locale.US, "%.4f", c.latitude) + "," + String.format(Locale.US, "%.4f", c.longitude)
            ),
            field("geometry", geometryWkt(zone))
        )
    }

    private fun field(label: String, value: String): String = "# " + label.padEnd(11) + ": " + value

    private fun vesselText(zone: RegulatedZone): String {
        val v = zone.vesselSizeRestriction ?: return "-"
        val min = v.minLengthM?.let { "${formatKn(it)} m" } ?: "-"
        val max = v.maxLengthM?.let { "${formatKn(it)} m" } ?: "-"
        return "min=$min max=$max"
    }

    // ── Geometry / small helpers ────────────────────────────────────────────────

    private fun centroidSuffix(zone: RegulatedZone): String {
        val c = centroid(zone)
        return "_${asciiFold(String.format(Locale.US, "%.4f", c.latitude))}" +
            "_${asciiFold(String.format(Locale.US, "%.4f", c.longitude))}"
    }

    private fun centroid(zone: RegulatedZone): LatLng {
        val ring = zone.outerRing
        val points = if (ring.isNotEmpty() && ring.first() == ring.last()) ring.dropLast(1) else ring
        if (points.isEmpty()) return LatLng(0.0, 0.0)
        return LatLng(
            latitude = points.sumOf { it.latitude } / points.size,
            longitude = points.sumOf { it.longitude } / points.size
        )
    }

    /** Cap on the simplified outer-ring vertex count the `# geometry` line carries. */
    private const val GEOMETRY_MAX_VERTICES = 8

    /** First RDP epsilon (m); doubled until the ring fits [GEOMETRY_MAX_VERTICES]. */
    private const val GEOMETRY_EPSILON_START_M = 20.0

    /** Upper bound on the RDP epsilon (m) — the doubling stops here. */
    private const val GEOMETRY_EPSILON_CAP_M = 20_480.0

    /**
     * The `# geometry` value: the zone's outer ring as a WKT `POLYGON`, adaptively
     * simplified with Ramer-Douglas-Peucker to at most [GEOMETRY_MAX_VERTICES]
     * distinct vertices.
     *
     * The ring is opened (the duplicated closing point dropped), simplified, then
     * re-closed by appending the first point. A ring already at or below the cap is
     * kept as is; otherwise epsilon starts at [GEOMETRY_EPSILON_START_M] and doubles
     * up to [GEOMETRY_EPSILON_CAP_M] until the result fits. A result below three
     * points falls back to the ring's bounding-box rectangle so the WKT stays valid.
     * Holes are dropped — too few vertices to carry them.
     */
    private fun geometryWkt(zone: RegulatedZone): String {
        val ring = zone.outerRing
        val points = if (ring.size > 1 && ring.first() == ring.last()) ring.dropLast(1) else ring
        if (points.isEmpty()) return "-"

        val simplified = if (points.size <= GEOMETRY_MAX_VERTICES) {
            points
        } else {
            var epsilon = GEOMETRY_EPSILON_START_M
            var result = SpatialOperations.douglasPeucker(points, epsilon)
            while (result.size > GEOMETRY_MAX_VERTICES && epsilon < GEOMETRY_EPSILON_CAP_M) {
                epsilon *= 2.0
                result = SpatialOperations.douglasPeucker(points, epsilon)
            }
            result
        }

        val vertices = if (simplified.size < 3) boundingBoxRing(points) else simplified
        val closed = vertices + vertices.first()
        val body = closed.joinToString(", ") { p ->
            String.format(Locale.US, "%.5f %.5f", p.longitude, p.latitude)
        }
        return "POLYGON(($body))"
    }

    /** The ring's bounding-box rectangle — the valid fallback when simplification collapses it. */
    private fun boundingBoxRing(points: List<LatLng>): List<LatLng> {
        val minLat = points.minOf { it.latitude }
        val maxLat = points.maxOf { it.latitude }
        val minLon = points.minOf { it.longitude }
        val maxLon = points.maxOf { it.longitude }
        return listOf(
            LatLng(minLat, minLon),
            LatLng(minLat, maxLon),
            LatLng(maxLat, maxLon),
            LatLng(maxLat, minLon)
        )
    }

    private fun formatKn(value: Double): String =
        if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

    private fun writeAtomically(file: File, content: String) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(content, StandardCharsets.UTF_8)
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    private val HEADER = """
        # ============================================================
        # App-side control over the regulated-zone set.
        # Generated by RegulationSpeedOverrides.seed: the bake ADDS
        # zones it has not seen and NEVER rewrites an existing entry.
        # Read at runtime by RegulatedZonesRepository from the APK asset
        # of this name, next to maro.properties.
        #
        # Three key families, one block per zone:
        #   regulatedZone.speedOverride.<key>=<kn>   replace the limit;
        #     a value on a zone with none makes it a speed zone.
        #     Leave it blank for no override.
        #   regulatedZone.speedRoute.<key>=<kn>      routing-only speed;
        #     the route engine prices the zone at this value, every
        #     other surface keeps the limit above. Blank = none.
        #   regulatedZone.ignore.<key>=true|false    drop the zone.
        # <key> chain: SHOM inspireid, else decree ref, else name +
        #   centroid slug; every string folded to ASCII (e -> e,
        #   c -> c), any other char becomes _.
        # Edit a value; leave the key and the # block. A line starting
        # "# stale" is retired and never applied.
        # ============================================================
    """.trimIndent() + "\n\n"
}
