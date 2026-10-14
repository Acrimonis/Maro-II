package ykws.android.maro.data.regulation

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ykws.android.maro.data.model.LatLng
import java.io.ByteArrayInputStream
import java.io.FileNotFoundException
import java.io.InputStream

/**
 * The repository's load, off the device: the injected opener stands in for `Context.assets`.
 *
 * Two claims are pinned here. The load yields the same set the bytes carry, over an override file
 * that is simply absent. And a missing or corrupt asset leaves the set absent rather than failing —
 * the app simply draws no regulated-zones overlay.
 */
class RegulatedZonesRepositoryTest {

    private fun sampleSet(): RegulatedZoneSet = RegulatedZoneSet(
        zones = listOf(
            RegulatedZone(
                outerRing = listOf(LatLng(43.5, 7.1), LatLng(43.6, 7.2), LatLng(43.5, 7.3), LatLng(43.5, 7.1)),
                zoneType = RegulatedZoneType.SPEED_LIMIT,
                speedLimitKn = 10.0,
                name = "Test Zone",
                source = "SHOM",
                sourceRef = "REF-001"
            )
        ),
        metadata = RegulationMetadata(
            regionId = "test",
            fetchTimestampMs = 1000L,
            sourceCount = 1,
            totalZones = 1
        )
    )

    /** An opener that serves [bytes] for the region `.bin` and nothing else, as the APK asset would. */
    private fun asset(bytes: ByteArray): (String) -> InputStream = { path ->
        if (path == "regulated-zones/test.bin") ByteArrayInputStream(bytes)
        else throw FileNotFoundException(path)
    }

    @Test
    fun theLoadYieldsTheSetTheBytesCarry() = runTest {
        val original = sampleSet()
        val repository = RegulatedZonesRepository(regionId = "test")

        repository.loadFrom(asset(RegulatedZoneSerializer.serialize(original)))

        assertEquals(original, repository.zoneSet.value)
    }

    @Test
    fun aMissingAssetLeavesTheSetAbsent() = runTest {
        val repository = RegulatedZonesRepository(regionId = "test")

        repository.loadFrom { path -> throw FileNotFoundException(path) }

        assertNull(repository.zoneSet.value)
    }

    @Test
    fun aCorruptAssetLeavesTheSetAbsent() = runTest {
        val repository = RegulatedZonesRepository(regionId = "test")
        // 0x0F is field 1, wire type 7 — not a valid protobuf wire type, so the deserialize throws.
        val corrupt = byteArrayOf(0x0F)

        repository.loadFrom(asset(corrupt))

        assertNull(repository.zoneSet.value)
    }
}
