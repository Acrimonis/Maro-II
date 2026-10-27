package ykws.android.maro.ui.map

import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import kotlin.math.*

class VisibleChevronBufferTest {
    private data class Draw(val pass: Int, val anchor: ArrowAnchor, val x: Float, val y: Float)
    @Volatile private var sink = 0f

    // Retained two-pass reference, including the original float interpolation and strict cull.
    private fun reference(points: List<RenderPoint>, anchors: List<ArrowAnchor>,
                          project: (RenderPoint, MutableScreenPoint)->Unit): List<Draw> {
        val screen=MutableScreenPoint()
        val result=mutableListOf<Draw>()
        repeat(2) { pass -> for(anchor in anchors) {
            project(points[anchor.segmentIndex],screen); val ax=screen.x; val ay=screen.y
            project(points[anchor.segmentIndex+1],screen)
            val x=ax+(screen.x-ax)*anchor.t; val y=ay+(screen.y-ay)*anchor.t
            if(x < -48 || x > 1048 || y < -48 || y > 1048) continue
            result+=Draw(pass,anchor,x,y)
        } }
        return result
    }

    @Test fun bothPassesMatchReferenceThroughPanZoomAndEdgeCrossings() {
        val points=listOf(RenderPoint(0.0,-100.0),RenderPoint(0.0,1200.0),RenderPoint(2000.0,1200.0),RenderPoint(0.0,500.0))
        val anchors=List(33) { ArrowAnchor(it%3,(it%11)/10f,it*13f) }
        val buffer=VisibleChevronBuffer()
        for(scale in listOf(.5f,1f,2f)) for(shift in listOf(-500f,0f,2000f)) {
            var reads=0
            val project: (RenderPoint,MutableScreenPoint)->Unit = { p,out -> reads++; out.x=p.lon.toFloat()*scale+shift; out.y=p.lat.toFloat()*scale }
            val expected=reference(points,anchors,project)
            assertEquals(anchors.size*4,reads)
            reads=0
            buffer.prepare(points,anchors,1000f,1000f,48f,project)
            val actual=buildList { repeat(2) { pass -> repeat(buffer.count) { i -> add(Draw(pass,anchors[buffer.anchorIndex(i)],buffer.x(i),buffer.y(i))) } } }
            assertEquals(expected,actual)
            assertEquals(anchors.size*2,reads)
            assertEquals(anchors.size,buffer.capacity)
        }
        buffer.prepare(points,emptyList(),1000f,1000f,48f) { _,_->fail() }
        assertEquals(0,buffer.count)
        assertEquals(anchors.size,buffer.capacity)
    }

    @Test fun measureTwoPassProjectionWork() {
        assumeTrue(System.getenv("MARO_ARROW_PERF")=="1")
        for(count in listOf(8,600,2000)) for(offscreen in listOf(false,true)) repeat(24) { round ->
            val points=List(count+1) { RenderPoint(if(offscreen) 20.0 else 43.5,7.0+it*.000001) }
            val anchors=List(count) { ArrowAnchor(it,.37f,it%360f) }
            for(candidate in if(round%2==0) listOf(false,true) else listOf(true,false)) {
                val buffer=VisibleChevronBuffer()
                var reads=0
                // Mercator arithmetic, not Android Projection/JNI; the device cost remains unverified.
                val project: (RenderPoint,MutableScreenPoint)->Unit = { p,out ->
                    reads++
                    out.x=((p.lon-7.0)*100000).roundToInt().toFloat()
                    out.y=((ln(tan(PI/4+Math.toRadians(43.5)/2))-ln(tan(PI/4+Math.toRadians(p.lat)/2)))*100000).roundToInt().toFloat()
                }
                val screen=MutableScreenPoint()
                val start=System.nanoTime()
                repeat(100) {
                    if(candidate) {
                        buffer.prepare(points,anchors,1000f,1000f,48f,project)
                        repeat(2) { repeat(buffer.count) { i -> sink=buffer.x(i)+buffer.y(i) } }
                    } else repeat(2) { for(anchor in anchors) {
                        project(points[anchor.segmentIndex],screen); val ax=screen.x; val ay=screen.y
                        project(points[anchor.segmentIndex+1],screen)
                        val x=ax+(screen.x-ax)*anchor.t; val y=ay+(screen.y-ay)*anchor.t
                        if(x < -48 || x > 1048 || y < -48 || y > 1048) continue
                        sink=x+y
                    } }
                }
                println("ARROW-PERF count=$count offscreen=$offscreen candidate=$candidate round=$round ns=${System.nanoTime()-start} reads=$reads capacity=${buffer.capacity}")
            }
        }
    }
}
