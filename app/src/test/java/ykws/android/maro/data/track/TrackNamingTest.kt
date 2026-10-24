package ykws.android.maro.data.track

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pure half of a trip's name (settled 2026-10-10): the time-weighted ranking, the shape the
 * name composes, and the trimming that keeps it within the cap.
 */
class TrackNamingTest {

    private fun point(
        ms: Long,
        lat: Double = 0.0,
        lon: Double = 0.0,
        type: PointType = PointType.NORMAL
    ) = TrackPoint(lat = lat, lon = lon, timeOffsetMs = ms, type = type)

    private fun contact(id: String, label: String, score: Double) = MarkerContact(id, label, score)

    @Test
    fun `the timed score sums seconds times the match score, lowest first`() {
        val points = listOf(point(0), point(10_000), point(20_000))
        val contacts = mapOf(
            0L to listOf(contact("a", "A", 0.5), contact("b", "B", 0.2)),
            10_000L to listOf(contact("a", "A", 0.5))
        )
        val ranked = TrackNaming.timedScores(points) { contacts[it.timeOffsetMs] ?: emptyList() }

        // B holds 10 s at 0.2 = 2.0; A holds 20 s at 0.5 = 10.0 — so the deepest leads.
        assertEquals(listOf("b", "a"), ranked.map { it.markerId })
        assertEquals(2.0, ranked[0].timedScore, 1e-9)
        assertEquals(10.0, ranked[1].timedScore, 1e-9)
        assertEquals("B", ranked[0].label)
    }

    @Test
    fun `a GAP interval credits nothing`() {
        val points = listOf(point(0), point(10_000, type = PointType.GAP), point(20_000))
        val ranked = TrackNaming.timedScores(points) { listOf(contact("a", "A", 0.1)) }

        assertTrue(ranked.isEmpty())
    }

    @Test
    fun `both ends leave the body, the origin leads and a distinct destination closes`() {
        val ranked = listOf(
            TimedMarker("o", "Origin", 1.0),
            TimedMarker("a", "A", 2.0),
            TimedMarker("d", "Dest", 3.0)
        )
        val shape = TrackNaming.select(ranked, TripEnd("o", "Origin"), TripEnd("d", "Dest"))

        assertEquals("Origin", shape.origin)
        assertEquals(listOf("A"), shape.body)
        assertEquals("Dest", shape.destination)
    }

    @Test
    fun `a shared loop endpoint is dropped from the body, not repeated`() {
        val ranked = listOf(TimedMarker("o", "Origin", 1.0), TimedMarker("a", "A", 2.0))
        val shape = TrackNaming.select(ranked, TripEnd("o", "Origin"), destination = null)

        assertEquals("Origin", shape.origin)
        assertEquals(listOf("A"), shape.body)
        assertEquals(null, shape.destination)
    }

    @Test
    fun `a registered stop takes the first slot by rule, not by score`() {
        val ranked = listOf(
            TimedMarker("a", "A", 0.1),
            TimedMarker("b", "B", 0.2),
            TimedMarker("c", "C", 0.3)
        )
        val shape = TrackNaming.select(
            ranked = ranked,
            origin = TripEnd(null, ""),
            destination = null,
            leading = TimedMarker("s", "Stop", 99.0)
        )

        assertEquals(listOf("Stop", "A"), shape.body)
    }

    @Test
    fun `the name chains the origin, the body and the destination in the caller's own word`() {
        assertEquals(
            "Ori to #1, #2 to Dest",
            TrackNaming.compose("Ori", listOf("#1", "#2"), "Dest", "to", 200)
        )
        assertEquals(
            "#1, #2 to Dest",
            TrackNaming.compose(null, listOf("#1", "#2"), "Dest", "to", 200)
        )
        assertEquals(
            "Ori to #1, #2",
            TrackNaming.compose("Ori", listOf("#1", "#2"), null, "to", 200)
        )
        assertEquals("#1, #2", TrackNaming.compose(null, listOf("#1", "#2"), null, "to", 200))
        assertEquals("Ori to Dest", TrackNaming.compose("Ori", emptyList(), "Dest", "to", 200))
        assertEquals("Ori \u00e0 Dest", TrackNaming.compose("Ori", emptyList(), "Dest", "\u00e0", 200))
        assertEquals("Dest", TrackNaming.compose(null, emptyList(), "Dest", "to", 200))
        assertEquals("", TrackNaming.compose(null, emptyList(), null, "to", 200))
    }

    @Test
    fun `the longer token gives up more when the budget bites`() {
        // Budget 6 over 12 characters: the eight-character token takes four, the four takes two.
        val trimmed = TrackNaming.trimProportionally(listOf("aaaaaaaa", "bbbb"), 6)

        assertEquals(listOf("aaa\u2026", "b\u2026"), trimmed)
    }

    @Test
    fun `an over-long name is cut in proportion, marked with an ellipsis`() {
        val name = TrackNaming.compose(
            origin = "\u00celes de L\u00e9rins",
            body = listOf("Cap d'Antibes", "Le Village Englouti"),
            destination = "Port de La Salis",
            connector = "to",
            maxLength = 20
        )

        assertTrue("fits the cap: $name", name.length <= 20)
        assertTrue("marked as cut: $name", name.contains(TrackNaming.ELLIPSIS))
    }

    @Test
    fun `a place inside the Port Salis geofence is the home port`() {
        assertTrue(TrackNaming.namesHomePort(43.55, 7.00, 43.55, 7.00, 500.0))
        assertFalse(TrackNaming.namesHomePort(43.60, 7.10, 43.55, 7.00, 500.0))
    }

    @Test
    fun `a label naming Salis no longer counts as the home port`() {
        // Positional-only: the label arm is gone, so a Salis name outside the circle names the end.
        assertFalse(TrackNaming.namesHomePort(0.0, 0.0, 43.5696, 7.1312, 50.0))
        assertTrue(TrackNaming.namesHomePort(43.5696, 7.1312, 43.5696, 7.1312, 50.0))
    }

    @Test
    fun `a marker's own icon leads its name, one space between`() {
        assertEquals("Cap d'Antibes", markerLabel("Cap d'Antibes", null))
        assertEquals("Cap d'Antibes", markerLabel("Cap d'Antibes", ""))
        assertEquals("\uD83E\uDD3F Cap d'Antibes", markerLabel("Cap d'Antibes", "\uD83E\uDD3F"))
    }

    @Test
    fun `a round trip at the port reads departure to return`() {
        assertEquals(
            "la Salis: 09:12 → 17:45",
            TrackNaming.homePortCommentLine(
                startedAtHomePort = true, endedAtHomePort = true,
                startTimeMs = 0L, endTimeMs = 1L, displayName = "la Salis",
                clock = { if (it == 0L) "09:12" else "17:45" }
            )
        )
    }

    @Test
    fun `a live trip shows the departure only`() {
        assertEquals(
            "la Salis: 09:12",
            TrackNaming.homePortCommentLine(
                startedAtHomePort = true, endedAtHomePort = false,
                startTimeMs = 0L, endTimeMs = null, displayName = "la Salis",
                clock = { "09:12" }
            )
        )
    }

    @Test
    fun `a one-way trip ending at the port shows the return only`() {
        assertEquals(
            "la Salis: 17:45",
            TrackNaming.homePortCommentLine(
                startedAtHomePort = false, endedAtHomePort = true,
                startTimeMs = 0L, endTimeMs = 1L, displayName = "la Salis",
                clock = { if (it == 0L) "09:12" else "17:45" }
            )
        )
    }

    @Test
    fun `a trip never touching the port prints no home-port line`() {
        assertEquals(
            null,
            TrackNaming.homePortCommentLine(
                startedAtHomePort = false, endedAtHomePort = false,
                startTimeMs = 0L, endTimeMs = 1L, displayName = "la Salis",
                clock = { "09:12" }
            )
        )
    }
}
