package com.plotus.server

import com.plotus.shared.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assume.assumeTrue
import org.postgresql.ds.PGSimpleDataSource
import kotlin.test.*

/** Synthetic account identities and plan data are isolated to the local test server. */
class PlanReviewIntegrationTest {
    private fun report(score:Int,hash:Char)=PlanReviewReport(rulesVersion="plotus-architect-review.1",
        sourceHash=hash.toString().repeat(64),designScore=score,designLevel=if(score>=90)"Excellent" else "Good",
        metrics=listOf("space" to 25,"circulation" to 20,"function" to 20,"daylight" to 15,"privacy" to 10,"access" to 10)
            .map {ReviewMetric(it.first,it.first,it.second,score.toDouble())},
        gates=listOf(ReviewCheck("configured_layout","Native layout","pass")),canOptimize=true)
    @Test fun ownedRevisionCanBePreviewedCancelledAndAppliedExactlyOnceWithoutEditingTheBase() {
        val url=System.getenv("PLOTUS_TEST_DATABASE_URL");assumeTrue(url!=null)
        val source=PGSimpleDataSource().apply {setURL(url!!);user="plotus";password=System.getenv("PLOTUS_TEST_DATABASE_PASSWORD")}
        val owner=UUID.randomUUID();val other=UUID.randomUUID()
        val projects=PostgresProjects(source).also {it.migrate()};val jobs=PostgresPlans(source);val reviews=PostgresPlanReviews(source)
        val baseline=report(80,'a');val revised=report(90,'b')
        val oldDrawing=PlanDrawing("floor-0","Ground","synthetic",100,100,review=baseline)
        val newDrawing=oldDrawing.copy(pngBase64="revised synthetic",review=revised)
        val revision=OptimizedRevision(listOf(newDrawing),revised,listOf(ReviewComparison("design","Design quality","score",80.0,90.0)))
        val entered=CompletableDeferred<Unit>();val stopped=CompletableDeferred<Unit>();val cancellations=mutableListOf<String>()
        val lateFailure=java.util.concurrent.atomic.AtomicBoolean(false)
        val engine=object:PlanEngine {
            override suspend fun preview(project:Project,setup:FloorPlanSetup)=FloorPlanPreview(canGenerate=true)
            override suspend fun generate(project:Project,setup:FloorPlanSetup)=error("Unused")
            override suspend fun optimize(project:Project,setup:FloorPlanSetup,drawings:List<PlanDrawing>,request:StartOptimizationRequest,jobID:String):EngineOptimizationResult {
                assertEquals(oldDrawing,drawings.single());assertEquals(5,request.maxCandidates)
                entered.complete(Unit)
                try {
                    if(lateFailure.get()){delay(2400);error("Transport failed after a checked checkpoint")}
                    awaitCancellation()
                }finally {stopped.complete(Unit)}
            }
            override suspend fun optimizationStatus(jobID:String,knownRevision:String?)=EngineOptimizationProgress(
                requested=5,checked=2,valid=1,distinct=1,revisionHash=revised.sourceHash,
                revision=revision.takeUnless {knownRevision==revised.sourceHash})
            override suspend fun cancel(jobID:String){cancellations+=jobID}
        }
        val service=PlanReviews(jobs,reviews,engine,ai=null);val floors=FloorPlans(jobs,engine)
        try {testApplication {
            application {plotusModule(projects,IdentityVerifier {if(it=="review-owner")owner else other},floorPlans=floors,planReviews=service)}
            val project=runBlocking {
                var p=projects.create(owner,ProjectDraft("Review fixture","Test city"))
                val plot=PlotSetup(lengths=listOf(18.0,12.0,18.0,12.0),roads=listOf(PlotRoad(0))).rebuild()
                p=projects.savePlot(owner,UUID.fromString(p.id),p.revision,plot)
                projects.saveFloorPlan(owner,UUID.fromString(p.id),p.revision,FloorPlanSetup())
            }
            val projectID=UUID.fromString(project.id)
            val original=runBlocking {
                val base=jobs.start(owner,project,StartPlanRequest(project.revision,UUID.randomUUID().toString())).plan
                jobs.finish(owner,projectID,base.copy(status="ready",drawings=listOf(oldDrawing)))
                jobs.find(owner,projectID,UUID.fromString(base.id))!!
            }
            val path="/v1/projects/"+project.id+"/floor-plan"
            val reportPath=path+"/generations/"+original.id+"/review"
            assertEquals(HttpStatusCode.Unauthorized,client.get(reportPath).status)
            assertEquals(HttpStatusCode.NotFound,client.get(reportPath){bearerAuth("review-other")}.status)
            val measured=client.get(reportPath){bearerAuth("review-owner")}
            assertEquals(HttpStatusCode.OK,measured.status)
            assertEquals(80,Json.decodeFromString<PlanReviewReport>(measured.bodyAsText()).designScore)
            val request=StartOptimizationRequest(UUID.randomUUID().toString(),operations=listOf(ReviewOperation.SHORTEN_PASSAGE))
            suspend fun start()=client.post(path+"/generations/"+original.id+"/optimizations"){
                bearerAuth("review-owner");contentType(ContentType.Application.Json);setBody(Json.encodeToString(request))
            }
            val first=start();assertEquals(HttpStatusCode.Accepted,first.status)
            val job=Json.decodeFromString<PlanOptimization>(first.bodyAsText())
            runBlocking {withTimeout(5000){entered.await()}}
            assertEquals(job.id,Json.decodeFromString<PlanOptimization>(start().bodyAsText()).id)
            val jobPath=path+"/optimizations/"+job.id
            assertEquals(HttpStatusCode.NotFound,client.get(jobPath){bearerAuth("review-other")}.status)
            assertEquals(HttpStatusCode.NotFound,client.post(jobPath+"/cancel"){bearerAuth("review-other")}.status)
            assertTrue(cancellations.isEmpty())
            runBlocking {withTimeout(6000){while(reviews.find(owner,projectID,UUID.fromString(job.id))?.revision==null)delay(20)}}
            val summary=client.get(jobPath+"?summary=true"){bearerAuth("review-owner")}
            assertFalse(summary.bodyAsText().contains("pngBase64"))
            val preview=Json.decodeFromString<PlanOptimization>(client.get(jobPath){bearerAuth("review-owner")}.bodyAsText())
            assertEquals(2,preview.checked);assertEquals(90,preview.revision?.review?.designScore)
            val stale=client.post(jobPath+"/apply"){bearerAuth("review-owner");contentType(ContentType.Application.Json);setBody(Json.encodeToString(ApplyOptimizationRequest("c".repeat(64))))}
            assertEquals(HttpStatusCode.Conflict,stale.status);assertTrue(cancellations.isEmpty())
            suspend fun apply()=client.post(jobPath+"/apply"){
                bearerAuth("review-owner");contentType(ContentType.Application.Json);setBody(Json.encodeToString(ApplyOptimizationRequest(revised.sourceHash!!)))
            }
            val applied=apply();assertEquals(HttpStatusCode.OK,applied.status)
            val saved=Json.decodeFromString<GeneratedFloorPlan>(applied.bodyAsText())
            assertNotEquals(original.id,saved.id);assertEquals(original.id,saved.optimizedFromPlanID)
            assertEquals(90,saved.drawings.single().review?.designScore)
            assertEquals(saved.id,Json.decodeFromString<GeneratedFloorPlan>(apply().bodyAsText()).id)
            runBlocking {withTimeout(5000){stopped.await()}}
            assertEquals(listOf(job.id),cancellations)
            runBlocking {
                assertEquals(oldDrawing,jobs.find(owner,projectID,UUID.fromString(original.id))!!.drawings.single())
                assertEquals(jobs.snapshot(owner,projectID,UUID.fromString(original.id)),jobs.snapshot(owner,projectID,UUID.fromString(saved.id)))
                assertEquals(project,projects.get(owner,projectID))
                assertEquals("applied",reviews.find(owner,projectID,UUID.fromString(job.id))?.status)
            }
            lateFailure.set(true)
            val retry=client.post(path+"/generations/"+original.id+"/optimizations") {
                bearerAuth("review-owner");contentType(ContentType.Application.Json)
                setBody(Json.encodeToString(request.copy(requestID=UUID.randomUUID().toString())))
            }
            assertEquals(HttpStatusCode.Accepted,retry.status)
            val second=Json.decodeFromString<PlanOptimization>(retry.bodyAsText())
            runBlocking {withTimeout(6000){while(reviews.find(owner,projectID,UUID.fromString(second.id))?.status!="ready")delay(20)}}
            val recovered=Json.decodeFromString<PlanOptimization>(client.get(path+"/optimizations/"+second.id){bearerAuth("review-owner")}.bodyAsText())
            assertNotNull(recovered.revision)
            val acceptedCheckpoint=client.post(path+"/optimizations/"+second.id+"/apply") {
                bearerAuth("review-owner");contentType(ContentType.Application.Json)
                setBody(Json.encodeToString(ApplyOptimizationRequest(revised.sourceHash!!)))
            }
            assertEquals(HttpStatusCode.OK,acceptedCheckpoint.status)
            assertNotEquals(saved.id,Json.decodeFromString<GeneratedFloorPlan>(acceptedCheckpoint.bodyAsText()).id)
            lateFailure.set(false)
            val thirdResponse=client.post(path+"/generations/"+original.id+"/optimizations") {
                bearerAuth("review-owner");contentType(ContentType.Application.Json)
                setBody(Json.encodeToString(request.copy(requestID=UUID.randomUUID().toString())))
            }
            val third=Json.decodeFromString<PlanOptimization>(thirdResponse.bodyAsText())
            runBlocking {withTimeout(6000){while(reviews.find(owner,projectID,UUID.fromString(third.id))?.revision==null)delay(20)}}
            assertEquals(HttpStatusCode.OK,client.post(path+"/optimizations/"+third.id+"/cancel"){bearerAuth("review-owner")}.status)
            val resumable=Json.decodeFromString<CurrentOptimization>(client.get(path+"/optimizations"){bearerAuth("review-owner")}.bodyAsText()).optimization
            assertEquals(third.id,resumable?.id);assertEquals("cancelled",resumable?.status);assertEquals(revised.sourceHash,resumable?.revisionHash)
            assertEquals(HttpStatusCode.OK,client.post(path+"/optimizations/"+third.id+"/dismiss"){bearerAuth("review-owner")}.status)
            assertNull(Json.decodeFromString<CurrentOptimization>(client.get(path+"/optimizations"){bearerAuth("review-owner")}.bodyAsText()).optimization)
        }}finally {
            service.close();floors.close()
            source.connection.use {c->c.prepareStatement("DELETE FROM plotus.projects WHERE owner_id=?").use {it.setObject(1,owner);it.executeUpdate()}}
        }
    }
}
