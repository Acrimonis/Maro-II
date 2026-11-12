package ykws.android.maro.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** The controller publishes the pure lane's rows to the list the snackbar host reads, changing only what moved. */
class MapDashboardControllerTest {

    private fun delete(name: String) = ActiveSnack.TrackDelete(name, name)

    private fun MapDashboardController.names() = activeSnacks.map { it.name }

    @Test
    fun aFullStackQueuesTheFourthRowAndRemovingARowPromotesIt() {
        val controller = MapDashboardController(maxVisible = 3)
        val rows = listOf("a", "b", "c", "d").map(::delete)
        rows.forEach(controller::enqueue)

        assertEquals(listOf("a", "b", "c"), controller.names())

        controller.remove(rows[1])

        assertEquals(listOf("a", "c", "d"), controller.names())
    }

    @Test
    fun theRowsThatDidNotMoveKeepTheirInstances() {
        val controller = MapDashboardController(maxVisible = 3)
        val rows = listOf("a", "b", "c", "d").map(::delete)
        rows.forEach(controller::enqueue)

        controller.remove(rows[0])

        assertSame(rows[1], controller.activeSnacks[0])
        assertSame(rows[2], controller.activeSnacks[1])
        assertSame(rows[3], controller.activeSnacks[2])
    }

    @Test
    fun theRouteEndingTakesTheFrontAndTheLastListedRowWaits() {
        val controller = MapDashboardController(maxVisible = 3)
        val rows = listOf("a", "b", "c").map(::delete)
        rows.forEach(controller::enqueue)
        val route = ActiveSnack.RouteEnd(RouteEndKind.LEAVE)

        controller.enqueue(route)

        assertSame(route, controller.activeSnacks[0])
        assertEquals(listOf("", "a", "b"), controller.names())

        controller.remove(route)

        assertEquals(listOf("a", "b", "c"), controller.names())
    }
}
