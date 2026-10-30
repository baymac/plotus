package com.plotus.server

import com.plotus.shared.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import org.junit.Assume.assumeTrue
import org.postgresql.ds.PGSimpleDataSource
import kotlin.test.*

class GenerationCancellationTest {
    @Test fun ownedCancellationStopsWorkIsIdempotentAndCannotBeOverwrittenByALateResult() {
        val url=System.getenv("PLOTUS_TEST_DATABASE_URL");assumeTrue(url!=null)
        val source=PGSimpleDataSource().apply {setURL(url!!);user="plotus";password=System.getenv("PLOTUS_TEST_DATABASE_PASSWORD")}
        val owner=UUID.randomUUID();val other=UUID.randomUUID();val projects=PostgresProjects(source).also {it.migrate()};val jobs=PostgresPlans(source)
        val entered=CompletableDeferred<String>();val stopped=CompletableDeferred<Unit>();val cancellations=mutableListOf<String>()
        val engine=object:PlanEngine {
            override suspend fun preview(project:Project,setup:FloorPlanSetup)=error("Unused")
            override suspend fun generate(project:Project,setup:FloorPlanSetup):List<PlanDrawing> = error("A cancellable job ID is required")
            override suspend fun generate(project:Project,setup:FloorPlanSetup,jobID:String):List<PlanDrawing> {
                entered.complete(jobID)
                try {awaitCancellation()}finally {stopped.complete(Unit)}
            }
            override suspend fun cancel(jobID:String){cancellations+=jobID}
        }
        val service=FloorPlans(jobs,engine)
        try {testApplication {
            application {plotusModule(projects,IdentityVerifier {if(it=="cancel-owner")owner else other},floorPlans=service)}
            val project=runBlocking {
                var p=projects.create(owner,ProjectDraft("Cancellation fixture","Test city"))
                val plot=PlotSetup(lengths=listOf(18.0,12.0,18.0,12.0),roads=listOf(PlotRoad(0))).rebuild()
                p=projects.savePlot(owner,UUID.fromString(p.id),p.revision,plot)
                projects.saveFloorPlan(owner,UUID.fromString(p.id),p.revision,FloorPlanSetup())
            }
            val job=runBlocking {service.start(owner,project,StartPlanRequest(project.revision,UUID.randomUUID().toString()))}
            runBlocking {withTimeout(5000){assertEquals(job.id,entered.await())}}
            val path="/v1/projects/${project.id}/floor-plan/generations/${job.id}/cancel"
            assertEquals(HttpStatusCode.Unauthorized,client.post(path).status)
            assertEquals(HttpStatusCode.NotFound,client.post(path){bearerAuth("cancel-other")}.status)
            assertTrue(cancellations.isEmpty())
            val response=client.post(path){bearerAuth("cancel-owner")}
            assertEquals(HttpStatusCode.OK,response.status)
            val cancelled=Json.decodeFromString<GeneratedFloorPlan>(response.bodyAsText())
            assertEquals("cancelled",cancelled.status);assertNotNull(cancelled.durationMillis)
            runBlocking {withTimeout(5000){stopped.await()}}
            assertEquals(HttpStatusCode.OK,client.post(path){bearerAuth("cancel-owner")}.status)
            assertEquals(listOf(job.id),cancellations)
            runBlocking {
                jobs.finish(owner,UUID.fromString(project.id),job.copy(status="ready",drawings=listOf(PlanDrawing("g","Ground","late",100,100))))
                assertEquals("cancelled",jobs.find(owner,UUID.fromString(project.id),UUID.fromString(job.id))?.status)
                val next=jobs.start(owner,project,StartPlanRequest(project.revision,UUID.randomUUID().toString())).plan
                jobs.finish(owner,UUID.fromString(project.id),next.copy(status="ready"))
                assertEquals("ready",service.cancel(owner,UUID.fromString(project.id),UUID.fromString(next.id)).status)
            }
            assertEquals(listOf(job.id),cancellations)
        }}finally {service.close();source.connection.use {c->c.prepareStatement("DELETE FROM plotus.projects WHERE owner_id=?").use {it.setObject(1,owner);it.executeUpdate()}}}
    }
}
