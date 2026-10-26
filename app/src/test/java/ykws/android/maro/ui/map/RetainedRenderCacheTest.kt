package ykws.android.maro.ui.map

import org.junit.Assert.*
import org.junit.Test

class RetainedRenderCacheTest {
    @Test fun completeGeometryAndStyleControlReuseAndEviction() {
        val cache = RetainedRenderCache<List<Int>, List<Int>, String, Any>()
        var prepares = 0; var builds = 0
        fun render(id: String, points: List<Int>, style: String) = cache.render(id, points, style,
            { prepares++; it.map { p -> p * 2 } }, { builds++; Any() })
        val first = render("a", listOf(1,2,3), "history")
        assertSame(first, render("a", listOf(1,2,3), "history"))
        assertNotSame(first, render("a", listOf(1,2,3), "pinned"))
        assertEquals(1, prepares)
        render("a", listOf(1,4,3), "pinned") // Same ID/size/endpoints; interior changed.
        assertEquals(2, prepares)
        assertEquals(3, builds)
        render("b", listOf(7), "history")
        cache.retain(setOf("a"))
        assertEquals(1, cache.size)
        cache.clear(); assertEquals(0, cache.size)
    }

    @Test fun orderingMatchesFullRebuildAndLeavesForeignObjectsOwned() {
        val foreign = Any(); val a = Any(); val b = Any(); val c = Any()
        val target = mutableListOf(foreign,a,b)
        assertFalse(reconcileOwnedOverlays(target,listOf(a,b),listOf(a,b)))
        assertTrue(reconcileOwnedOverlays(target,listOf(a,b),listOf(c,b,a)))
        assertEquals(listOf(foreign,c,b,a),target)
        assertTrue(reconcileOwnedOverlays(target,listOf(c,b,a),listOf(a,c)))
        assertEquals(listOf(foreign,a,c),target)
        assertTrue(reconcileOwnedOverlays(target,listOf(a,c),emptyList()))
        assertEquals(listOf(foreign),target)
    }

    @Test fun randomizedOwnedDiffPreservesMembershipOrderAndForeignIdentity() {
        val random = kotlin.random.Random(382)
        val pool = List(12) { Any() }; val foreign = Any()
        val target = mutableListOf(foreign)
        var previous = emptyList<Any>()
        repeat(500) {
            val wanted = pool.shuffled(random).take(random.nextInt(13))
            reconcileOwnedOverlays(target,previous,wanted)
            assertEquals(wanted,target.filter { it !== foreign })
            assertEquals(1,target.count { it === foreign })
            previous = wanted
        }
    }

    @Test fun boundsKeepCrossingBoundaryAndUnknownViews() {
        val view = ArrowBounds(0.0,10.0,0.0,10.0)
        assertTrue(ArrowBounds(20.0,30.0,20.0,30.0).outside(view))
        assertFalse(ArrowBounds(5.0,5.0,-20.0,20.0).outside(view)) // Both endpoints outside, line crosses.
        assertFalse(ArrowBounds(10.0,20.0,10.0,20.0).outside(view)) // Touches padded boundary.
        assertFalse(ArrowBounds(0.0,1.0,-179.0,179.0).outside(view))
        assertFalse(ArrowBounds(20.0,30.0,20.0,30.0).outside(ArrowBounds(0.0,10.0,170.0,-170.0)))
        assertFalse(ArrowBounds(Double.NaN,30.0,20.0,30.0).outside(view))
    }
}
