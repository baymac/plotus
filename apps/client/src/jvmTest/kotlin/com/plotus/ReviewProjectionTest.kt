package com.plotus

import com.plotus.shared.*
import kotlin.test.*

class ReviewProjectionTest {
    @Test fun roomMetresUseTheExactSvgFrameIncludingNegativePlotCoordinates() {
        val svg="""<svg viewBox="-10 -5 60 40" data-plotus-frame="designed"></svg>"""
        val projection=PlanCanvasProjection.decode(svg,1200f,800f)!!
        val corner=projection.point(PlanScenePoint(-10*METRES_PER_FOOT,-5*METRES_PER_FOOT))
        val middle=projection.point(PlanScenePoint(20*METRES_PER_FOOT,15*METRES_PER_FOOT))
        assertEquals(0f,corner.x,.001f);assertEquals(0f,corner.y,.001f)
        assertEquals(600f,middle.x,.001f);assertEquals(400f,middle.y,.001f)
    }
    @Test fun oldOrMalformedSvgDoesNotAcquireAnInventedRoomProjection() {
        assertNull(PlanCanvasProjection.decode("""<svg viewBox="0 0 60 40"/>""",1200f,800f))
        assertNull(PlanCanvasProjection.decode("""<svg data-plotus-frame="designed" viewBox="0 0 0 40"/>""",1200f,800f))
    }
    @Test fun intrinsicRoundingPreservesTheSvgMeetAlignment() {
        val projection=PlanCanvasProjection(0f,0f,60f,40f,1200f,1000f)
        val p=projection.point(PlanScenePoint(0.0,0.0))
        assertEquals(0f,p.x,.001f);assertEquals(100f,p.y,.001f)
    }
}
