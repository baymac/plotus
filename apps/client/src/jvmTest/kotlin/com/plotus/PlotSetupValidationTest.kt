package com.plotus

import com.plotus.shared.*
import kotlinx.coroutines.*
import kotlin.test.*

class PlotSetupValidationTest {
    @Test fun enteringFloorPlanChecksBoundaryBeforeRoads()=runBlocking<Unit> {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        var stored=Project("plot-check","Plot","Test city")
        val workspace=PlotWorkspace(scope,{stored},{p,setup->p.copy(plotSetup=setup).also {stored=it}},{})
        try {
            workspace.load()
            var entered=false
            workspace.save(requireBoundary=true,requireRoads=true) {entered=true}
            assertEquals(mapOf("boundary" to "Set all sides dimensions"),workspace.validation)
            assertFalse(entered);assertFalse(workspace.readyForFloor)
            workspace.change(PlotSetup(lengths=listOf(18.0,12.0,18.0,12.0)).rebuild())
            workspace.save(requireBoundary=true,requireRoads=true) {entered=true}
            assertEquals(setOf("roads"),workspace.validation.keys)
            assertFalse(entered);assertFalse(workspace.readyForFloor)
            workspace.change(workspace.draft.toggleRoad(0))
            assertTrue(workspace.readyForFloor)
            workspace.editDirection("");assertFalse(workspace.readyForFloor)
            workspace.editDirection("0");assertTrue(workspace.readyForFloor)
            workspace.save(requireBoundary=true,requireRoads=true) {entered=true}
            assertTrue(entered);assertTrue(workspace.validation.isEmpty())
        } finally {scope.cancel()}
    }

    @Test fun incompleteBoundaryCanBeSavedWithoutFieldErrors()=runBlocking<Unit> {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        var stored=Project("partial-plot","Plot","Test city")
        val workspace=PlotWorkspace(scope,{stored},{p,setup->p.copy(plotSetup=setup).also {stored=it}},{})
        try {
            workspace.load()
            workspace.change(workspace.draft.withLength(0,12.0))
            workspace.save()
            assertEquals(12.0,stored.plotSetup?.lengths?.first())
            assertTrue(workspace.validation.isEmpty());assertFalse(workspace.dirty)
        } finally {scope.cancel()}
    }
}
