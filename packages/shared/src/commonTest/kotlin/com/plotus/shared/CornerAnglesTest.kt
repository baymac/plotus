package com.plotus.shared

import kotlin.math.*
import kotlin.test.*

class CornerAnglesTest {
    private val rectangle=PlotSetup(lengths=listOf(12.0,9.0,12.0,9.0)).rebuild()
    private fun assertMeasurementsHeld(before:PlotSetup,after:PlotSetup) {
        assertEquals(before.lengths,after.lengths)
        assertEquals(before.diagonals,after.diagonals)
        assertEquals(before.roads,after.roads)
        assertEquals(before.northDegrees,after.northDegrees)
        assertTrue(after.errors(completeBoundary=true).isEmpty())
        after.vertices.indices.forEach {i->assertEquals(before.lengths[i]!!,distance(after.vertices[i],after.vertices[(i+1)%after.vertices.size]),1e-5)}
    }
    @Test fun movingEveryQuadrilateralCornerChangesAnglesWithoutChangingDimensions() {
        for(index in 0..3) {
            val p=rectangle.vertices[index]
            val changed=assertNotNull(rectangle.withCornerAngle(index,PlotPoint(p.x+1.4,p.y+.6)))
            assertMeasurementsHeld(rectangle,changed)
            assertTrue(abs(changed.areaSquareMetres!!-rectangle.areaSquareMetres!!)>0.01)
            assertEquals(rectangle.vertices[(index+2)%4],changed.vertices[(index+2)%4])
        }
    }
    @Test fun repeatedDragsAndLargerPolygonsKeepAllEdgesFixed() {
        for(count in listOf(4,5,6,9,12)) {
            val start=PlotSetup(lengths=List(count){10.0},diagonals=List(count-3){null},northDegrees=32.0,roads=listOf(PlotRoad(1))).rebuild()
            var changed=start
            repeat(30) {n->
                val index=n%count;val p=changed.vertices[index]
                changed=assertNotNull(changed.withCornerAngle(index,PlotPoint(p.x+.08*cos(n.toDouble()),p.y+.08*sin(n.toDouble()))))
                assertMeasurementsHeld(start,changed)
            }
        }
    }
    @Test fun outOfReachTargetsCannotStretchMeasurementsAndInvalidTargetsCannotCorruptAPlot() {
        val extreme=rectangle.withCornerAngle(1,PlotPoint(100000.0,100000.0))
        if(extreme!=null)assertMeasurementsHeld(rectangle,extreme)
        assertNull(rectangle.withCornerAngle(0,PlotPoint(Double.NaN,1.0)))
        assertNull(PlotSetup().withCornerAngle(0,PlotPoint(2.0,3.0)))
        assertNull(rectangle.copy(useDiagonals=true,diagonals=listOf(15.0)).withCornerAngle(0,PlotPoint(2.0,3.0)))
        assertNull(PlotSetup(lengths=listOf(3.0,4.0,5.0),diagonals=emptyList()).rebuild().withCornerAngle(0,PlotPoint(2.0,3.0)))
    }
}
