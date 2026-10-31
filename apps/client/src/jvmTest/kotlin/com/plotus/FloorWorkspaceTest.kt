package com.plotus

import com.plotus.shared.*
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import kotlin.test.*

class FloorWorkspaceTest {
    @Test fun exportsArePreparedThroughTheOwnedApiAndFailuresCanBeRetriedBeforeDownloading()=runBlocking<Unit> {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        val requested=mutableListOf<String>();var fail=true
        val workspace=FloorWorkspace(scope,{method,path,_->when {
            path.contains("/export/")->{
                assertEquals("GET",method);assertFalse(path.contains("download="));requested+=path
                if(fail)throw AuthFailure(503,"Couldn’t prepare this file.")
                val format=path.substringAfterLast('/');authJson.encodeToJsonElement(PlanExport("floor.$format","application/octet-stream","Zml4dHVyZQ=="))
            }
            path.contains("/generations?")->authJson.encodeToJsonElement(PlanGenerations(emptyList()))
            else->authJson.encodeToJsonElement(initial)
        }},{})
        try {
            workspace.load(initial.id);workspace.prepareExport("plan","ground","pdf")
            assertNull(workspace.preparedExport);assertNotNull(workspace.exportError);assertFalse(workspace.exporting)
            fail=false;workspace.prepareExport("plan","ground","pdf")
            assertNull(workspace.exportError);assertEquals("pdf",workspace.preparedExportFormat)
            workspace.prepareExport("plan","ground","pdf");assertEquals(2,requested.size)
            for(format in listOf("png","dwg")) {
                workspace.prepareExport("plan","ground",format)
                assertEquals(format,workspace.preparedExportFormat);assertEquals("floor.$format",workspace.preparedExport?.filename)
            }
            workspace.clearExport();assertNull(workspace.preparedExport);assertNull(workspace.preparedExportFormat)
        }finally {scope.cancel()}
    }
    @Test fun selectingAnotherFormatOrClosingTheModalDiscardsPendingExportWork()=runBlocking<Unit> {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        val pdf=CompletableDeferred<Unit>();val png=CompletableDeferred<Unit>()
        val workspace=FloorWorkspace(scope,{_,path,_->when {
            path.contains("/export/")->{
                val format=path.substringAfterLast('/');if(format=="pdf")pdf.await()else png.await()
                authJson.encodeToJsonElement(PlanExport("floor.$format","application/octet-stream","Zml4dHVyZQ=="))
            }
            path.contains("/generations?")->authJson.encodeToJsonElement(PlanGenerations(emptyList()))
            else->authJson.encodeToJsonElement(initial)
        }},{})
        try {
            workspace.load(initial.id);workspace.prepareExport("plan","ground","pdf");assertTrue(workspace.exporting)
            workspace.prepareExport("plan","ground","png");pdf.complete(Unit)
            assertNull(workspace.preparedExport);assertTrue(workspace.exporting)
            workspace.clearExport();png.complete(Unit);yield()
            assertNull(workspace.preparedExport);assertNull(workspace.exportError);assertFalse(workspace.exporting)
        }finally {scope.cancel()}
    }
    @Test fun historySummariesStaySeparateFromTheOpenedDrawingAndFromNewAttempts()=runBlocking<Unit> {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        val latest=GeneratedFloorPlan("e4fc581d-5337-466f-83d2-51e96c2a03f1","ready",createdAt="2026-10-28T10:00:00Z")
        val earlier=latest.copy(id="b4fc581d-5337-466f-83d2-51e96c2a03f1",createdAt="2026-10-27T10:00:00Z")
        val drawing=earlier.copy(drawings=listOf(PlanDrawing("ground","Ground","actual-stored-drawing",100,100)))
        var failNextPage=true
        val workspace=FloorWorkspace(scope,{_,path,_->when {
            path.endsWith("&before=${latest.id}")->{if(failNextPage)throw AuthFailure(503,"History unavailable");authJson.encodeToJsonElement(PlanGenerations(listOf(earlier)))}
            path.contains("/generations?")->authJson.encodeToJsonElement(PlanGenerations(listOf(latest),latest.id))
            path.endsWith("/${earlier.id}")->authJson.encodeToJsonElement(drawing)
            else->authJson.encodeToJsonElement(initial)
        }},{})
        try {
            workspace.load(initial.id)
            assertNull(workspace.generation);assertEquals(listOf(latest),workspace.history)
            workspace.loadHistory(more=true)
            assertNotNull(workspace.historyError);assertEquals(listOf(latest),workspace.history)
            failNextPage=false;workspace.loadHistory(more=true)
            assertEquals(listOf(latest,earlier),workspace.history);assertNull(workspace.nextHistoryBefore)
            workspace.openPlan(earlier.id);assertEquals(drawing,workspace.viewingPlan)
            workspace.newAttempt();assertEquals(listOf(latest,earlier),workspace.history)
            assertFalse(workspace.dirty);assertFalse(workspace.generating)
        }finally {scope.cancel()}
    }
    @Test fun savedEnvelopeAppearsImmediatelyAndFloorOrRoomEditsDoNotReloadIt()=runBlocking<Unit> {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        val area=SavedBuildableArea(FloorPlanSetup().buildableKey(plot),FloorEnvelope(areaSquareMetres=100.0))
        val loaded=initial.copy(buildableArea=area);var posts=0
        val workspace=FloorWorkspace(scope,{method,path,_->
            if(method=="POST")posts++
            if(path.contains("/generations?"))authJson.encodeToJsonElement(PlanGenerations(emptyList())) else authJson.encodeToJsonElement(loaded)
        },{})
        try {
            workspace.load(initial.id)
            assertEquals(area.envelope,workspace.preview?.envelope)
            workspace.change(workspace.draft.copy(buildingFloors=3,units=PlotUnits.METRES))
            workspace.change(workspace.draft.withHome(workspace.draft.homes.first().copy(bhk=4,vastu=false)))
            workspace.refreshPreview()
            assertEquals(area.envelope,workspace.preview?.envelope)
            assertFalse(workspace.previewing);assertEquals(0,posts)
            workspace.change(workspace.draft.copy(setbackMode=PlanMode.CUSTOM,commonSetbackMetres=.9))
            assertNull(workspace.preview)
            workspace.discard();assertEquals(area.envelope,workspace.preview?.envelope)
        }finally {scope.cancel()}
    }
    @Test fun cancellingAnActiveRunDoesNotCloseAnOlderPlanAndRecoversALostReply()=runBlocking<Unit> {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        var current=GeneratedFloorPlan("active","running",createdAt="2026-10-30T10:00:00Z")
        val older=current.copy(id="earlier",status="ready",drawings=listOf(PlanDrawing("ground","Ground","fixture",100,100)))
        var cancellations=0
        val workspace=FloorWorkspace(scope,{method,path,_->when {
            path.endsWith("/cancel")->{assertEquals("POST",method);cancellations++;current=current.copy(status="cancelled",durationMillis=5000);throw AuthFailure(0,"Reply lost")}
            path.contains("/generations?")->authJson.encodeToJsonElement(PlanGenerations(listOf(current,older.copy(drawings=emptyList()))))
            path.endsWith("/earlier")->authJson.encodeToJsonElement(older)
            path.endsWith("/active")->authJson.encodeToJsonElement(current)
            else->authJson.encodeToJsonElement(initial)
        }},{})
        try {
            workspace.load(initial.id);assertTrue(workspace.generating)
            current=current.copy(status="cancelling");workspace.openPlan(current.id);assertTrue(workspace.generating)
            workspace.openPlan(older.id);assertEquals(older,workspace.viewingPlan)
            workspace.newAttempt();assertTrue(workspace.generating)
            workspace.cancelGeneration(current.id)
            assertFalse(workspace.generating);assertFalse(workspace.cancelling);assertNull(workspace.error)
            assertEquals("cancelled",workspace.history.first().status);assertEquals(older,workspace.viewingPlan);assertEquals(1,cancellations)
        }finally {scope.cancel()}
    }
    private val plot=PlotSetup(lengths=listOf(18.0,12.0,18.0,12.0),roads=listOf(PlotRoad(0))).rebuild()
    private val initial=Project("c4f872f5-7c67-4f4e-aef9-b5e67f01ca2a","Home","Test city",revision=2,plotSetup=plot,floorPlan=SavedFloorPlan(FloorPlanSetup(),plot))
    @Test fun lostSaveReplyRequiresTheExactSavedBriefBeforeClearingTheDraft()=runBlocking<Unit> {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        var server=initial;var accepted:Project?=null
        val workspace=FloorWorkspace(scope,{method,path,body->when {
            path.contains("/generations?")->authJson.encodeToJsonElement(PlanGenerations(emptyList()))
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
            path.contains("/generations?")->authJson.encodeToJsonElement(PlanGenerations(emptyList()))
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
