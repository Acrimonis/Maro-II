package ykws.android.maro.data.regulation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.model.LatLng
import java.io.File

class RegulationSpeedOverridesTest {

    @Test
    fun `asciiFold folds accents and separators`() {
        assertEquals("Lerins", RegulationSpeedOverrides.asciiFold("Lérins"))
        assertEquals("c", RegulationSpeedOverrides.asciiFold("ç"))
        assertEquals("FR_123", RegulationSpeedOverrides.asciiFold("FR:123"))
    }

    @Test
    fun `keyFor prefers sourceRef then decree then name plus centroid`() {
        assertEquals("FR.42", RegulationSpeedOverrides.keyFor(zone(sourceRef = "FR.42")))
        assertEquals(
            "FR_PREMAR_MED_134_2021",
            RegulationSpeedOverrides.keyFor(zone(sourceRef = "", legalDecreeRef = "FR_PREMAR_MED_134_2021"))
        )
        assertTrue(
            RegulationSpeedOverrides.keyFor(zone(sourceRef = "", name = "Lérins")).startsWith("Lerins_")
        )
    }

    @Test
    fun `apply replaces a keyed zone speed and leaves others alone`() {
        val zones = listOf(
            zone(name = "A", sourceRef = "ref-a", speedLimitKn = 10.0),
            zone(name = "B", sourceRef = "ref-b", speedLimitKn = 6.0)
        )
        val result = RegulationSpeedOverrides.apply(
            zones,
            RegulationSpeedOverrides.Overrides(speeds = mapOf("ref-a" to 4.0), ignores = emptyMap())
        )
        assertEquals(4.0, result[0].speedLimitKn!!, 0.0)
        assertEquals(6.0, result[1].speedLimitKn!!, 0.0)
    }

    @Test
    fun `apply turns a zone with no speed into a speed zone`() {
        val env = zone(name = "Env", sourceRef = "ref-env", speedLimitKn = null, type = RegulatedZoneType.ENVIRONMENTAL)
        val result = RegulationSpeedOverrides.apply(
            listOf(env),
            RegulationSpeedOverrides.Overrides(speeds = mapOf("ref-env" to 8.0), ignores = emptyMap())
        )
        assertEquals(8.0, result.single().speedLimitKn!!, 0.0)
    }

    @Test
    fun `apply drops an ignored zone and keeps the rest`() {
        val zones = listOf(
            zone(name = "A", sourceRef = "ref-a"),
            zone(name = "B", sourceRef = "ref-b"),
            zone(name = "C", sourceRef = "ref-c")
        )
        val result = RegulationSpeedOverrides.apply(
            zones,
            RegulationSpeedOverrides.Overrides(speeds = emptyMap(), ignores = mapOf("ref-b" to true))
        )
        assertEquals(listOf("ref-a", "ref-c"), result.map { it.sourceRef })
    }

    @Test
    fun `apply is inert on empty overrides`() {
        val zones = listOf(zone(name = "A", sourceRef = "ref-a"))
        assertEquals(
            zones,
            RegulationSpeedOverrides.apply(zones, RegulationSpeedOverrides.Overrides(emptyMap(), emptyMap()))
        )
    }

    @Test
    fun `seed writes both key lines for every zone, blank speed for a zone without one`() {
        val dir = tempDir()
        try {
            val file = RegulationSpeedOverrides.file(dir)
            val speed = zone(name = "Speed", sourceRef = "ref-speed", speedLimitKn = 5.0)
            val env = zone(name = "Env", sourceRef = "ref-env", speedLimitKn = null, type = RegulatedZoneType.ENVIRONMENTAL)

            RegulationSpeedOverrides.seed(file, listOf(speed, env))
            val text = file.readText()

            assertTrue(text.contains("regulatedZone.speedOverride.ref-speed=5"))
            assertTrue(text.contains("regulatedZone.ignore.ref-speed=false"))
            assertTrue(text.contains("regulatedZone.speedOverride.ref-env=\n"))
            assertTrue(text.contains("regulatedZone.ignore.ref-env=false"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `seed preserves an edited comment, value and ignore flag across a re-run`() {
        val dir = tempDir()
        try {
            val file = RegulationSpeedOverrides.file(dir)
            val a = zone(name = "Alpha", sourceRef = "ref-a", speedLimitKn = 10.0)
            val b = zone(name = "Beta", sourceRef = "ref-b", speedLimitKn = 6.0)

            RegulationSpeedOverrides.seed(file, listOf(a, b))
            file.writeText(
                file.readText()
                    .replace("regulatedZone.speedOverride.ref-a=10", "regulatedZone.speedOverride.ref-a=4")
                    .replace(": Alpha", ": Alpha custom")
                    .replace("regulatedZone.ignore.ref-b=false", "regulatedZone.ignore.ref-b=true")
            )
            RegulationSpeedOverrides.seed(file, listOf(a, b))

            val overrides = RegulationSpeedOverrides.load(file)
            assertEquals(4.0, overrides.speeds["ref-a"]!!, 0.0)
            assertEquals(true, overrides.ignores["ref-b"])
            assertTrue(file.readText().contains("Alpha custom"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `seed marks a vanished zone stale`() {
        val dir = tempDir()
        try {
            val file = RegulationSpeedOverrides.file(dir)
            val a = zone(name = "A", sourceRef = "ref-a")
            val b = zone(name = "B", sourceRef = "ref-b", speedLimitKn = 6.0)

            RegulationSpeedOverrides.seed(file, listOf(a, b))
            RegulationSpeedOverrides.seed(file, listOf(a))

            assertTrue(file.readText().contains("# stale"))
            assertFalse(RegulationSpeedOverrides.load(file).speeds.containsKey("ref-b"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `load skips a malformed speed value`() {
        val dir = tempDir()
        try {
            val file = RegulationSpeedOverrides.file(dir)
            file.parentFile?.mkdirs()
            file.writeText(
                "regulatedZone.speedOverride.ref-x=abc\n" +
                    "regulatedZone.speedOverride.ref-y=7.5\n"
            )
            val overrides = RegulationSpeedOverrides.load(file)
            assertFalse(overrides.speeds.containsKey("ref-x"))
            assertEquals(7.5, overrides.speeds["ref-y"]!!, 0.0)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `load reads both families from a stream`() {
        val stream =
            ("regulatedZone.speedOverride.ref-x=7.5\n" + "regulatedZone.ignore.ref-y=true\n").byteInputStream()
        val overrides = RegulationSpeedOverrides.load(stream)
        assertEquals(7.5, overrides.speeds["ref-x"]!!, 0.0)
        assertEquals(true, overrides.ignores["ref-y"])
    }

    private fun zone(
        name: String = "Test",
        speedLimitKn: Double? = 10.0,
        sourceRef: String = "",
        legalDecreeRef: String? = null,
        type: RegulatedZoneType = RegulatedZoneType.SPEED_LIMIT,
        lat: Double = 43.5,
        lon: Double = 7.0
    ) = RegulatedZone(
        outerRing = listOf(
            LatLng(lat, lon),
            LatLng(lat + 0.01, lon),
            LatLng(lat, lon + 0.01),
            LatLng(lat, lon)
        ),
        zoneType = type,
        speedLimitKn = speedLimitKn,
        name = name,
        source = "SHOM",
        sourceRef = sourceRef,
        legalDecreeRef = legalDecreeRef
    )

    private fun tempDir(): File =
        File(System.getProperty("java.io.tmpdir"), "speedovr_${System.nanoTime()}").apply { mkdirs() }
}
