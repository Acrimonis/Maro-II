package ykws.android.maro.ui.map

import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import ykws.android.maro.data.track.TrackPoint
import ykws.android.maro.data.track.PointType
import ykws.android.maro.data.track.TrackSummary
import ykws.android.maro.data.settings.AppSettings

class OverlayPerformanceTest {
    @Volatile private var sink = 0
    private data class Plan(val style: StoredOverlayStyle, val points: List<RenderPoint>, val segments: List<TrackSegment>)

    @Test fun styleRetainsOriginalRoleAxesAndFadeInputs() {
        val settings = AppSettings()
        for (route in listOf(false,true)) for (pinned in listOf(false,true))
            for (arrows in listOf(false,true)) for (colours in listOf(false,true)) {
                val summary = TrackSummary("id","name",startTimeMs=0,route=route,pinned=pinned)
                val actual = storedOverlayStyle(summary,1,4,pinned,route && !pinned,false,3f,arrows,colours,settings)
                val expectedPlan = if (pinned) pinnedLineRenderPlan(summary,arrows,colours,settings.routeSpeedArrows,settings.routeSpeedColor,false,false,false)
                    else if(route) routeLineRenderPlan(settings.routeSpeedArrows,settings.routeSpeedColor,false)
                    else lineRenderPlan(arrows,colours,false,null)
                assertEquals(expectedPlan,actual.plan)
                val newest = if(pinned) { if(route) settings.trackingTransparencyPinnedRouteNewest else settings.trackingTransparencyPinnedNewest }
                    else if(route) settings.trackingTransparencyRouteNewest else settings.trackingTransparencyNewest
                val oldest = if(pinned) { if(route) settings.trackingTransparencyPinnedRouteOldest else settings.trackingTransparencyPinnedOldest }
                    else if(route) settings.trackingTransparencyRouteOldest else settings.trackingTransparencyOldest
                assertEquals(trackFadeAlpha(1,4,newest,oldest),actual.fade,0f)
                assertEquals(storedTrackWidth(false,route && !pinned,pinned,false),actual.width,0f)
                assertEquals(route,actual.plan.dashed)
            }
    }

    @Test fun inactiveColourAxisDoesNotInvalidateButRoleAndFadeDo() {
        val settings=AppSettings()
        val summary=TrackSummary("id","name",startTimeMs=0)
        fun style(s: AppSettings, total: Int=4, pinned: Boolean=false) =
            storedOverlayStyle(summary,1,total,pinned,false,false,3f,true,true,s)
        assertEquals(style(settings),style(settings.copy(trackingColorPastFrom=0xFF112233.toInt())))
        assertNotEquals(style(settings),style(settings,8))
        assertNotEquals(style(settings),style(settings,pinned=true))
    }

    @Test fun repeatedGeometryWorkAndColdCost() {
        assumeTrue(System.getenv("MARO_OVERLAY_PERF") == "1")
        val settings = AppSettings()
        val original = List(20) { track -> List(2000) { point -> TrackPoint(43.5 + track*.0001,
            7.0+point*.00001,speedMps=2f,timeOffsetMs=point*1000L,
            type=if(point%257==0) PointType.GAP else PointType.NORMAL) } }
        val summaries = List(20) { TrackSummary("$it","Track $it",startTimeMs=it.toLong()) }
        for(workload in listOf("cold","unchanged","edit","fade")) repeat(20) { round ->
            for(candidate in if(round%2==0) listOf(false,true) else listOf(true,false)) {
                val cache = RetainedRenderCache<List<TrackPoint>,List<RenderPoint>,StoredOverlayStyle,Plan>()
                val sources = original.toMutableList()
                var prepared=0; var built=0; var elapsed=0L
                val frames=if(workload=="cold") 1 else 20
                repeat(frames) { frame ->
                    if(workload=="edit" && frame==10) sources[3]=sources[3].toMutableList().also { it[100]=it[100].copy(lat=43.52) }
                    val total=if(workload=="fade" && frame>=10) 21 else 20
                    val start=System.nanoTime()
                    val plans=sources.mapIndexed { i, points ->
                        val style=storedOverlayStyle(summaries[i],i,total,false,false,i==19,3f,true,false,settings)
                        val prepare: (List<TrackPoint>)->List<RenderPoint> = { prepared++; it.toRenderPoints() }
                        val build: (List<RenderPoint>)->Plan = { built++; Plan(style,it,splitTrackSegments(it)) }
                        if(candidate) cache.render("$i",points,style,prepare,build) else build(prepare(points))
                    }
                    elapsed+=System.nanoTime()-start
                    // Differential plans are checked outside timing, including GAPs, speed and interior edits.
                    plans.forEachIndexed { i, plan ->
                        val expected=sources[i].toRenderPoints()
                        assertEquals(expected,plan.points); assertEquals(splitTrackSegments(expected),plan.segments)
                    }
                    sink=plans.sumOf { it.segments.size }
                    cache.retain(summaries.map { it.id }.toSet())
                }
                println("OVERLAY-PERF workload=$workload candidate=$candidate round=$round ns=$elapsed prepared=$prepared built=$built")
            }
        }
    }

    @Test fun boundsPreserveVisibleAnchorPhaseAndAvoidOffscreenProjection() {
        val visible = List(2000) { RenderPoint(43.5, 7.0 + it*.000002, speedKn=4f) }
        val view=ArrowBounds(43.49,43.51,6.99,7.01)
        val project: (RenderPoint)->ScreenPt = { ScreenPt(((it.lon-7)*100000).toFloat(),((43.5-it.lat)*100000).toFloat()) }
        val expected=sampleArrowAnchors(visible,project,{20f},10000)
        assertFalse(ArrowBounds.of(visible)!!.outside(view))
        assertEquals(expected,sampleArrowAnchors(visible,project,{20f},10000))
        val crossing=listOf(RenderPoint(43.5,6.98),RenderPoint(43.5,7.02))
        assertFalse(ArrowBounds.of(crossing)!!.outside(view))
    }

    @Test fun measureBoundsBeforeDrawingProjection() {
        assumeTrue(System.getenv("MARO_OVERLAY_PERF") == "1")
        val view=ArrowBounds(43.49,43.51,6.99,7.01)
        for(count in listOf(4,4000)) for(offscreen in listOf(false,true)) {
            val points=List(count) { RenderPoint(if(offscreen) 20.0 else 43.5,7.0+it*.000002,speedKn=4f) }
            val bounds=ArrowBounds.of(points)!!
            // Keep the original first draw/zoom sampling pass. Only later drawing projections are gated.
            val project: (RenderPoint)->ScreenPt = {
                ScreenPt(((it.lon-7)*100000).toFloat(),((43.5-it.lat)*100000).toFloat())
            }
            val anchors=sampleArrowAnchors(points,project,{20f},10000)
            repeat(20) { round ->
                for(candidate in if(round%2==0) listOf(false,true) else listOf(true,false)) {
                    var projections=0
                    var visible=0
                    val start=System.nanoTime()
                    repeat(100) {
                        if(anchors.isNotEmpty() && !(candidate && bounds.outside(view))) {
                            for(anchor in anchors) {
                                val a=project(points[anchor.segmentIndex]); val b=project(points[anchor.segmentIndex+1])
                                projections+=2
                                val x=a.x+(b.x-a.x)*anchor.t; val y=a.y+(b.y-a.y)*anchor.t
                                if(x in -48f..1048f && y in -48f..1048f) visible++
                            }
                        }
                    }
                    val elapsed=System.nanoTime()-start
                    assertEquals(if(offscreen) 0 else anchors.size*100,visible)
                    sink=visible
                    println("BOUNDS-PERF count=$count offscreen=$offscreen candidate=$candidate round=$round ns=$elapsed projections=$projections")
                }
            }
        }
    }
}
