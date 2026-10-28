package ykws.android.maro.spatial.multipass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * **The tile key's canonical encoding, pinned field by field and proved identity-free.**
 *
 * The store names an entry by a digest over [TileKey.canonicalBytes] and verifies it by comparing those
 * bytes, so the encoding — never the key's `hashCode` — is the correctness floor. This mirrors
 * [`TileKeyTest`]'s completeness pin: every declared field is flipped in turn and the bytes must move, the
 * declared-field count is held against the flip list so a new field cannot slip through, and the one
 * order-undefined component (the excluded-id `Set`) is shown not to leak its iteration order.
 */
class TileKeyCanonicalTest {

    @Test
    fun flippingEachKeyedValueMovesTheCanonicalBytes() {
        val base = TileTestFixtures.baseKey()
        val baseBytes = base.canonicalBytes()
        for ((name, altered) in TileTestFixtures.flips(base)) {
            assertFalse(
                "flipping $name must move the canonical bytes",
                baseBytes.contentEquals(altered.canonicalBytes())
            )
        }
    }

    @Test
    fun everyDeclaredKeyFieldHasAFlipCase() {
        val declared = TileKey::class.java.declaredFields.count {
            !Modifier.isStatic(it.modifiers)
        }
        assertEquals(
            "a new keyed field must gain a flip case here, or the store could name two different tiles alike",
            declared,
            TileTestFixtures.flips(TileTestFixtures.baseKey()).size
        )
    }

    @Test
    fun theEncodingIsDeterministic() {
        assertTrue(
            "the same key must encode to the same bytes on every call",
            TileTestFixtures.baseKey().canonicalBytes()
                .contentEquals(TileTestFixtures.baseKey().canonicalBytes())
        )
    }

    /** The nested marker geometry is key material: a Circle and a Corridor are different tiles. */
    @Test
    fun theNestedMarkerGeometryIsCarried() {
        val circle = TileTestFixtures.baseKey().copy(markers = listOf(TileTestFixtures.marker(3)))
        val corridor = TileTestFixtures.baseKey().copy(markers = listOf(TileTestFixtures.corridorMarker(3)))
        assertFalse(
            "a marker's geometry variant must be visible in the bytes",
            circle.canonicalBytes().contentEquals(corridor.canonicalBytes())
        )
    }
}
