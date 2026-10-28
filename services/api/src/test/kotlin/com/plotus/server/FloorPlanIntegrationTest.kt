package com.plotus.server

import com.plotus.shared.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.junit.Assume.assumeTrue
import org.postgresql.ds.PGSimpleDataSource
import kotlin.test.*

class FloorPlanIntegrationTest {
    @Test fun generationHistoryIsPagedOwnedAndDoesNotDownloadDrawings() {
        val url=System.getenv("PLOTUS_TEST_DATABASE_URL");assumeTrue(url!=null)
        val source=PGSimpleDataSource().apply {setURL(url!!);user="plotus";password=System.getenv("PLOTUS_TEST_DATABASE_PASSWORD")}
        val owner=UUID.randomUUID();val other=UUID.randomUUID();val projects=PostgresProjects(source).also {it.migrate()};val jobs=PostgresPlans(source)
        val service=FloorPlans(jobs)
        try {testApplication {
            application {plotusModule(projects,IdentityVerifier {if(it=="history-owner")owner else other},floorPlans=service)}
            val project=runBlocking {
                var p=projects.create(owner,ProjectDraft("History fixture","Test city"))
                val plot=PlotSetup(lengths=listOf(18.0,12.0,18.0,12.0),roads=listOf(PlotRoad(0))).rebuild()
                p=projects.savePlot(owner,UUID.fromString(p.id),p.revision,plot)
                projects.saveFloorPlan(owner,UUID.fromString(p.id),p.revision,FloorPlanSetup())
            }
            val ids=runBlocking {(1..32).map {
                val job=jobs.start(owner,project,StartPlanRequest(project.revision,UUID.randomUUID().toString())).plan
                jobs.finish(owner,UUID.fromString(project.id),job.copy(status="ready",drawings=listOf(PlanDrawing("ground","Ground","test-drawing-payload",100,100))))
                job.id
            }}
            val path="/v1/projects/${project.id}/floor-plan/generations"
            suspend fun list(suffix:String="",token:String="history-owner")=client.get(path+"?summaries=true"+suffix){bearerAuth(token)}
            val firstResponse=list();assertEquals(HttpStatusCode.OK,firstResponse.status)
            val first=Json.decodeFromString<PlanGenerations>(firstResponse.bodyAsText())
            assertEquals(ids.reversed().take(30),first.plans.map {it.id});assertTrue(first.plans.all {it.drawings.isEmpty()})
            assertFalse(firstResponse.bodyAsText().contains("test-drawing-payload"))
            val legacy=Json.decodeFromString<PlanGenerations>(client.get(path){bearerAuth("history-owner")}.bodyAsText())
            assertEquals(ids.last(),legacy.plans.single().id);assertEquals("test-drawing-payload",legacy.plans.single().drawings.single().pngBase64)
            val second=Json.decodeFromString<PlanGenerations>(list("&before=${first.nextBefore}").bodyAsText())
            assertEquals(ids.reversed().drop(30),second.plans.map {it.id});assertNull(second.nextBefore)
            val exact=client.get("$path/${ids.first()}"){bearerAuth("history-owner")}
            assertEquals("test-drawing-payload",Json.decodeFromString<GeneratedFloorPlan>(exact.bodyAsText()).drawings.single().pngBase64)
            assertEquals(HttpStatusCode.NotFound,list(token="history-other").status)
            assertEquals(HttpStatusCode.Unauthorized,client.get(path).status)
            assertEquals(HttpStatusCode.BadRequest,list("&before=not-an-id").status)
            assertTrue(runBlocking {jobs.history(other,UUID.fromString(project.id))}.plans.isEmpty())
        }} finally {service.close();source.connection.use {c->c.prepareStatement("DELETE FROM plotus.projects WHERE owner_id IN (?,?)").use {it.setObject(1,owner);it.setObject(2,other);it.executeUpdate()}}}
    }
    @Test fun savedBriefIsOwnedRevisionCheckedAndGenerationUsesAnImmutablePlotSnapshot() {
        val url=System.getenv("PLOTUS_TEST_DATABASE_URL");assumeTrue(url!=null)
        val source=PGSimpleDataSource().apply {setURL(url!!);user="plotus";password=System.getenv("PLOTUS_TEST_DATABASE_PASSWORD")}
        val owner=UUID.randomUUID();val other=UUID.randomUUID();val projects=PostgresProjects(source).also {it.migrate()}
        val jobs=PostgresPlans(source)
        val release=CompletableDeferred<Unit>();var calls=0
        val engine=object:PlanEngine {
            override suspend fun preview(project:Project,setup:FloorPlanSetup)=FloorPlanPreview(canGenerate=true)
            override suspend fun generate(project:Project,setup:FloorPlanSetup):List<PlanDrawing>{calls++;release.await();return emptyList()}
        }
        val service=FloorPlans(jobs,engine)
        try {testApplication {
            application {plotusModule(projects,IdentityVerifier {when(it){"floor-owner"->owner;"floor-other"->other;else->throw ApiFailure(401,"UNAUTHENTICATED","Sign in.")}},floorPlans=service)}
            var project=runBlocking {projects.create(owner,ProjectDraft("Floor fixture","Test city"))}
            val path="/v1/projects/${project.id}/floor-plan"
            suspend fun save(setup:FloorPlanSetup,revision:Long=project.revision,token:String="floor-owner")=client.put(path) {
                bearerAuth(token);contentType(ContentType.Application.Json);setBody(Json.encodeToString(SaveFloorPlanRequest(setup,revision)))
            }
            assertEquals(HttpStatusCode.BadRequest,save(FloorPlanSetup()).status)
            val plot=PlotSetup(lengths=listOf(18.0,12.0,18.0,12.0),vertices=listOf(PlotPoint(0.0,0.0),PlotPoint(18.0,0.0),PlotPoint(18.0,12.0),PlotPoint(0.0,12.0)),roads=listOf(PlotRoad(1)))
            project=runBlocking {projects.savePlot(owner,UUID.fromString(project.id),project.revision,plot)}
            val partial=FloorPlanSetup(setbackMode=PlanMode.CUSTOM)
            assertEquals(HttpStatusCode.NotFound,save(partial,token="floor-other").status)
            project=Json.decodeFromString<Project>(save(partial).bodyAsText())
            assertEquals(partial,project.floorPlan?.setup)
            assertEquals(plot,project.floorPlan?.plot)
            val setup=partial.copy(commonSetbackMetres=.6)
            assertEquals(HttpStatusCode.Conflict,save(setup,revision=project.revision-1).status)
            project=Json.decodeFromString<Project>(save(setup).bodyAsText())
            val persisted=runBlocking {projects.get(owner,UUID.fromString(project.id))};assertEquals(project.floorPlan,persisted.floorPlan)
            val key=UUID.randomUUID().toString()
            suspend fun start(requestID:String=key,revision:Long=project.revision)=client.post("$path/generations") {
                bearerAuth("floor-owner");contentType(ContentType.Application.Json);setBody(Json.encodeToString(StartPlanRequest(revision,requestID)))
            }
            assertEquals(HttpStatusCode.Conflict,start(revision=project.revision-1).status)
            val first=start();assertEquals(HttpStatusCode.Accepted,first.status)
            val job=Json.decodeFromString<GeneratedFloorPlan>(first.bodyAsText())
            assertEquals(job.id,Json.decodeFromString<GeneratedFloorPlan>(start().bodyAsText()).id)
            assertEquals(HttpStatusCode.TooManyRequests,start(UUID.randomUUID().toString()).status)
            assertEquals(HttpStatusCode.NotFound,client.get("$path/generations/${job.id}"){bearerAuth("floor-other")}.status)
            assertEquals(HttpStatusCode.Unauthorized,client.get("$path/generations/${job.id}").status)
            release.complete(Unit)
            withTimeout(5000) {while(service.get(owner,UUID.fromString(project.id),UUID.fromString(job.id)).status=="running")delay(20)}
            assertEquals(1,calls)
            assertEquals(job.id,Json.decodeFromString<GeneratedFloorPlan>(start().bodyAsText()).id);assertEquals(1,calls)
            val moved=plot.copy(northDegrees=45.0)
            project=runBlocking {projects.savePlot(owner,UUID.fromString(project.id),project.revision,moved)}
            assertEquals(plot,project.floorPlan?.plot)
            assertEquals(HttpStatusCode.Conflict,start(UUID.randomUUID().toString()).status)
            project=Json.decodeFromString<Project>(save(setup).bodyAsText())
            assertEquals(moved,project.floorPlan?.plot)
        }} finally {
            service.close()
            source.connection.use {c->c.prepareStatement("DELETE FROM plotus.projects WHERE owner_id IN (?,?)").use {it.setObject(1,owner);it.setObject(2,other);it.executeUpdate()}}
        }
    }
    @Test fun adapterPreservesCoordinatesRoadPriorityAndBedroomFeatures() {
        val plot=PlotSetup(lengths=listOf(18.0,12.0,18.0,12.0),roads=listOf(PlotRoad(1),PlotRoad(2,priority=1)),northDegrees=-45.0).rebuild()
        val shifted=plot.copy(vertices=plot.vertices.map {PlotPoint(it.x+100,it.y+200)})
        val project=ProjectDraft("Adapter fixture","Test city").toProject(UUID.randomUUID().toString()).copy(plotSetup=shifted)
        val home=PlanHome("home-1",mode=PlanMode.CUSTOM,rooms=listOf(PlanRoom("bed-a",RoomKind.BEDROOM,attachedBathroom=true,closet=Closet.WALK_IN)))
        val result=programme(project,FloorPlanSetup(homes=listOf(home),setbackMode=PlanMode.CUSTOM,sameSetback=false,edgeSetbacksMetres=listOf(.3,.6,.9,1.2)))
        val boundary=result["plot"]!!.jsonObject
        assertEquals(0,boundary["vertices"]!!.jsonArray.first().jsonObject["x_mm"]!!.jsonPrimitive.int)
        assertEquals(315.0,boundary["north_angle_deg"]!!.jsonPrimitive.double)
        assertEquals("B-C",boundary["roads"]!!.jsonArray.first().jsonObject["edge_id"]!!.jsonPrimitive.content)
        val requirements=result["units"]!!.jsonArray.first().jsonObject["requirements"]!!.jsonArray
        assertEquals(listOf("bedroom","attached_bathroom","dressing"),requirements.map {it.jsonObject["kind"]!!.jsonPrimitive.content})
        assertEquals(listOf("bed-a:bedroom:1"),requirements[1].jsonObject["attributes"]!!.jsonObject["bedroom_ids"]!!.jsonArray.map {it.jsonPrimitive.content})
        assertTrue(FloorPlanSetup(homes=listOf(home.copy(notes="Don’t ignore this"))).capabilityFindings().isNotEmpty())
    }
}
