package com.plotus.shared

import kotlin.test.*

class RoadLabelTest {
    @Test fun oneRoadHasNoPriorityQualifierAndMultipleRoadsKeepTheirOwnPriorities() {
        val plot=PlotSetup(roads=listOf(PlotRoad(2)))
        assertEquals(listOf("Road"),plot.roadCanvasLabels())
        assertEquals(listOf("Primary road · A → B","Secondary road · C → D"),plot.copy(roads=listOf(PlotRoad(2,priority=1),PlotRoad(0,priority=0))).roadCanvasLabels())
        assertEquals("Tertiary road",roadCanvasLabel(2,3))
        assertEquals("Priority 5 road",roadCanvasLabel(4,5))
        assertEquals(emptyList(),plot.copy(roads=emptyList()).roadCanvasLabels())
    }
}
