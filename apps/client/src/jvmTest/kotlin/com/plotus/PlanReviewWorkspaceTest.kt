package com.plotus

import com.plotus.shared.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import kotlin.test.*

class PlanReviewWorkspaceTest {
    private fun report(hash:Char)=PlanReviewReport(rulesVersion="test",sourceHash=hash.toString().repeat(64),
        canOptimize=true,findings=listOf(ReviewFinding("f","test","space","high","Test arrangement","Measured evidence.",ReviewOperation.CONSOLIDATE_OPEN_SPACE)))
    @Test fun switchingGenerationsCannotPublishAnOldReportOrLeaveTheLoaderRunning()=runBlocking<Unit> {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        val first=CompletableDeferred<JsonElement>();val second=CompletableDeferred<JsonElement>()
        val model=PlanReviewWorkspace(scope,{_,path,_->when {
            path.endsWith("/optimizations")->authJson.encodeToJsonElement(CurrentOptimization())
            path.contains("/generations/first/")->first.await()
            else->second.await()
        }},{})
        try {
            model.attachProject("project");model.open("first");assertTrue(model.loading)
            model.open("second")
            second.complete(authJson.encodeToJsonElement(report('b')))
            withTimeout(2000){while(model.loading)yield()}
            first.complete(authJson.encodeToJsonElement(report('a')))
            assertEquals("second",model.planID);assertEquals(report('b'),model.report)
            assertFalse(model.loading)
        }finally {scope.cancel()}
    }
    @Test fun deselectedGoalsStayDeselectedWhenReturningFromScoring()=runBlocking<Unit> {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        val model=PlanReviewWorkspace(scope,{_,path,_->if(path.endsWith("/optimizations"))
            authJson.encodeToJsonElement(CurrentOptimization()) else authJson.encodeToJsonElement(report('a'))},{})
        try {
            model.attachProject("project");model.open("first")
            model.selectedOperations.toList().forEach(model::choose)
            assertTrue(model.selectedOperations.isEmpty())
            model.open("first")
            assertTrue(model.selectedOperations.isEmpty())
            model.prepare(ReviewOperation.IMPROVE_PRIVACY)
            assertEquals(setOf(ReviewOperation.IMPROVE_PRIVACY),model.selectedOperations)
        }finally {scope.cancel()}
    }
    @Test fun lostStartResponseRecoversTheSameOwnedOptimizationByRequestID()=runBlocking<Unit> {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        var posted:String?=null;var started:String?=null;var lookups=0
        val job=PlanOptimization("owned-job","first","no_improvement","2026-11-01T10:00:00Z",updatedAt="2026-11-01T10:00:01Z")
        val model=PlanReviewWorkspace(scope,{method,path,body->when {
            path.endsWith("/generations/first/optimizations")&&method=="POST"->{
                posted=authJson.decodeFromString<StartOptimizationRequest>(body!!).requestID
                throw AuthFailure(0,"Lost response")
            }
            path.contains("?by=request")->{lookups++;assertTrue(path.contains(posted!!));authJson.encodeToJsonElement(job)}
            path.contains("?summary=true")->authJson.encodeToJsonElement(job)
            path.endsWith("/optimizations")->authJson.encodeToJsonElement(CurrentOptimization())
            else->authJson.encodeToJsonElement(report('a'))
        }},{})
        try {
            model.attachProject("project");model.open("first");model.start {started=it.id}
            withTimeout(2000){while(model.starting)yield()}
            assertEquals("owned-job",started);assertEquals(1,lookups);assertNull(model.error)
            assertEquals("no_improvement",model.optimization?.status)
        }finally {scope.cancel()}
    }
    @Test fun oneSelectedGoalIsSentWithABoundedThreeAttemptRequest()=runBlocking<Unit> {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        var submitted:StartOptimizationRequest?=null
        val evidence=report('a').let {it.copy(findings=it.findings+it.findings.single().copy(
            id="passage",operation=ReviewOperation.SHORTEN_PASSAGE))}
        val job=PlanOptimization("job","plan","no_improvement","2026-11-04T10:00:00Z")
        val model=PlanReviewWorkspace(scope,{method,path,body->when {
            method=="POST"->{submitted=authJson.decodeFromString<StartOptimizationRequest>(body!!);authJson.encodeToJsonElement(job)}
            path.contains("?summary=true")->authJson.encodeToJsonElement(job)
            path.endsWith("/optimizations")->authJson.encodeToJsonElement(CurrentOptimization())
            else->authJson.encodeToJsonElement(evidence)
        }},{})
        try {
            model.attachProject("project");model.open("plan")
            assertEquals(setOf(ReviewOperation.CONSOLIDATE_OPEN_SPACE),model.selectedOperations)
            model.choose(ReviewOperation.SHORTEN_PASSAGE)
            model.start {}
            withTimeout(2000){while(model.starting)yield()}
            assertEquals(listOf(ReviewOperation.SHORTEN_PASSAGE),submitted?.operations)
            assertEquals(3,submitted?.maxCandidates)
        }finally {scope.cancel()}
    }
    @Test fun anEmptyReviewDoesNotInventAnOptimizationGoal()=runBlocking<Unit> {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        val model=PlanReviewWorkspace(scope,{_,path,_->if(path.endsWith("/optimizations"))
            authJson.encodeToJsonElement(CurrentOptimization()) else authJson.encodeToJsonElement(report('a').copy(findings=emptyList()))},{})
        try {
            model.attachProject("project");model.open("plan")
            assertTrue(model.selectedOperations.isEmpty())
        }finally {scope.cancel()}
    }
}
