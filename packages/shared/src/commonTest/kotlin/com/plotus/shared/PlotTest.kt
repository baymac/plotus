package com.plotus.shared

import kotlin.math.abs
import kotlin.test.*

class PlotTest {
    @Test fun preciseBearingsKeepTheirSign() {
        assertEquals("-0.25",decimal(-0.25))
        assertEquals("-45",decimal(-45.0))
        assertEquals("0",decimal(-0.001))
    }
    @Test fun theSameSurveyProducesTheSameOutlineRegardlessOfIntermediateTypedDigits() {
        val lengths=listOf((42+1.0/12)*METRES_PER_FOOT,36*METRES_PER_FOOT,40.5*METRES_PER_FOOT,34*METRES_PER_FOOT)
        val direct=PlotSetup(lengths=lengths).rebuild()
        var typed=PlotSetup()
        lengths.forEachIndexed { i,length -> typed=typed.withLength(i,length) }
        typed=typed.withLength(3,3*METRES_PER_FOOT).withLength(3,lengths[3])
        assertEquals(direct.vertices,typed.vertices)
        assertEquals(direct.areaSquareFeet,typed.areaSquareFeet)
        assertEquals(typed.vertices[2].y,typed.vertices[3].y,0.001)
        assertTrue(typed.errors(completeBoundary=true).isEmpty())
    }
    @Test fun surveyedDiagonalsProduceTheMeasuredBoundaryAndArea() {
        val setup = PlotSetup(lengths = listOf(12.0, 9.0, 12.0, 9.0), useDiagonals = true, diagonals = listOf(15.0)).rebuild()
        assertTrue(setup.errors(completeBoundary = true).isEmpty())
        assertEquals(108.0, setup.areaSquareMetres!!, 0.001)
        assertEquals(1163, setup.areaSquareFeet)
        assertEquals(42.0, setup.perimeterMetres)
        assertEquals(setup.vertices, setup.copy(units = PlotUnits.METRES).vertices)
    }
    @Test fun sideOnlyShapesCloseForDifferentSizesAndAnObtuseTriangle() {
        for (sides in listOf(listOf(3.0,4.0,5.0),listOf(2.0,2.0,3.9),listOf(8.0,10.0,9.0,7.0),listOf(8.0,6.0,5.0,7.0,10.0))) {
            val points = assertNotNull(boundaryFromMeasurements(sides))
            assertTrue(simplePolygon(points))
            sides.indices.forEach { assertEquals(sides[it],distance(points[it],points[(it+1)%points.size]),0.001) }
        }
        assertNull(boundaryFromMeasurements(listOf(1.0,1.0,4.0)))
        assertNull(boundaryFromMeasurements(listOf(Double.NaN,2.0,3.0)))
        assertNull(boundaryFromMeasurements(listOf(12.0,9.0,12.0,9.0),listOf(30.0)))
    }
    @Test fun draftMeasurementsArePreservedButCannotAdvanceOrInventAnArea() {
        val draft = PlotSetup().withLength(0,10.0)
        assertTrue(draft.errors().isEmpty())
        assertTrue(draft.errors(completeBoundary = true).isNotEmpty())
        assertNull(draft.areaSquareMetres)
        val impossible = PlotSetup(lengths = listOf(100.0,1.0,1.0,1.0)).rebuild()
        assertTrue(impossible.vertices.isEmpty())
        assertTrue(impossible.errors(completeBoundary = true).isNotEmpty())
        val crossed = draft.copy(lengths=listOf(1.0,1.0,1.0,1.0),vertices=listOf(PlotPoint(0.0,0.0),PlotPoint(1.0,1.0),PlotPoint(0.0,1.0),PlotPoint(1.0,0.0)))
        assertTrue(crossed.errors().isNotEmpty())
        assertTrue(draft.copy(vertices=listOf(PlotPoint(0.0,0.0))).errors().isNotEmpty())
    }
    @Test fun roadSelectionAndReorderingPreserveExactlyOnePrimary() {
        val draft = PlotSetup().toggleRoad(1).toggleRoad(3).toggleRoad(2).prioritizeRoad(2,0)
        assertEquals(2,draft.roads.single { it.priority==0 }.side)
        assertTrue(draft.errors().isEmpty())
        val removed = draft.toggleRoad(2)
        assertEquals(listOf(0,1),removed.roads.map { it.priority })
        assertEquals(1,removed.roads.first().side)
        assertTrue(draft.copy(roads=draft.roads+draft.roads[0]).errors().containsKey("roads"))
        assertTrue(removed.errors(completeRoads=true).isEmpty())
    }
    @Test fun deletingAnySideOfAPentagonKeepsGeometryAndRemapsRoadsWithoutDroppingBelowFour() {
        val pentagon=PlotSetup(lengths=listOf(8.0,6.0,5.0,7.0,10.0),diagonals=List(2){null}).rebuild().let {p->p.copy(roads=p.lengths.indices.map {PlotRoad(it,priority=it)})}
        for(side in pentagon.lengths.indices) {
            val reduced=pentagon.withoutSide(side)
            assertEquals(4,reduced.lengths.size)
            assertEquals(3,reduced.roads.size)
            assertEquals(listOf(0,1,2),reduced.roads.map {it.priority})
            assertTrue(reduced.errors(completeBoundary=true,completeRoads=true).isEmpty())
            assertFailsWith<IllegalArgumentException> {reduced.withoutSide(0)}
        }
        val incomplete=PlotSetup().withSideCount(5).withLength(0,8.0).withoutSide(2)
        assertEquals(4,incomplete.lengths.size);assertTrue(incomplete.vertices.isEmpty());assertTrue(incomplete.errors().isEmpty())
    }
    @Test fun malformedDiagonalArraysAreRejectedWithoutIndexingPastTheDrawing() {
        val setup = PlotSetup(lengths=listOf(3.0,4.0,3.0,4.0)).rebuild()
        assertTrue(setup.copy(useDiagonals=true,diagonals=List(8){1.0}).errors().containsKey("diagonals"))
        val changed = setup.withVertices(setup.vertices.mapIndexed { i,p -> if(i==2) p.copy(x=p.x+1) else p })
        assertTrue(changed.errors(completeBoundary=true).isEmpty())
        assertTrue(abs(changed.lengths[1]!!-setup.lengths[1]!!)>0.01)
    }
}
