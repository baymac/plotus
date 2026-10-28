package com.plotus

import com.plotus.shared.*
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import kotlin.test.*

class FloorWorkspaceTest {
    private val plot=PlotSetup(lengths=listOf(18.0,12.0,18.0,12.0),roads=listOf(PlotRoad(0))).rebuild()
    private val initial=Project("c4f872f5-7c67-4f4e-aef9-b5e67f01ca2a","Home","Test city",revision=2,plotSetup=plot,floorPlan=SavedFloorPlan(FloorPlanSetup(),plot))
    @Test fun lostSaveReplyRequiresTheExactSavedBriefBeforeClearingTheDraft()=runBlocking<Unit> {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        var server=initial;var accepted:Project?=null
        val workspace=FloorWorkspace(scope,{method,path,body->when {
            path.endsWith("/generations")->authJson.encodeToJsonElement(PlanGenerations(emptyList()))
            method=="PUT"->{val requested=authJson.decodeFromString<SaveFloorPlanRequest>(body!!);server=server.copy(revision=3,floorPlan=SavedFloorPlan(requested.setup,plot));throw AuthFailure(0,"Reply lost")}
            else->authJson.encodeToJsonElement(server)
        }},{accepted=it})
        try {
            workspace.load(initial.id)
            val draft=workspace.draft.copy(setbackMode=PlanMode.CUSTOM,commonSetbackMetres=.9)
            workspace.change(draft);workspace.save()
            assertFalse(workspace.saving);assertFalse(workspace.dirty)
            assertEquals(draft,accepted?.floorPlan?.setup);assertEquals(3L,workspace.project?.revision)
        }finally {scope.cancel()}
    }
    @Test fun aLostStartReplyRecoversTheSameJobAndOnlyANewAttemptChangesTheKey()=runBlocking<Unit> {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        val keys=mutableListOf<String>();var recovered=0
        var job=GeneratedFloorPlan("e4fc581d-5337-466f-83d2-51e96c2a03f1","blocked",error="No checked layout found.")
        val workspace=FloorWorkspace(scope,{method,path,body->when {
            method=="POST"->{keys+=authJson.decodeFromString<StartPlanRequest>(body!!).requestID;throw AuthFailure(0,"Reply lost")}
            path.contains("?by=request")->{recovered++;authJson.encodeToJsonElement(job)}
            path.endsWith("/generations")->authJson.encodeToJsonElement(PlanGenerations(emptyList()))
            else->authJson.encodeToJsonElement(initial)
        }},{})
        try {
            workspace.load(initial.id);workspace.generate()
            assertEquals(job,workspace.generation);assertFalse(workspace.starting);assertEquals(1,recovered)
            workspace.generate();assertEquals(keys[0],keys[1])
            workspace.newAttempt();workspace.generate();assertNotEquals(keys[0],keys[2])
        }finally {scope.cancel()}
    }
}
