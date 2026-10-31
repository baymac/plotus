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
    @Test fun presentationAndExportsUseTheOwnedGenerationSnapshotWithoutRerunningGeneration() {
        val url=System.getenv("PLOTUS_TEST_DATABASE_URL");assumeTrue(url!=null)
        val source=PGSimpleDataSource().apply {setURL(url!!);user="plotus";password=System.getenv("PLOTUS_TEST_DATABASE_PASSWORD")}
        val owner=UUID.randomUUID();val other=UUID.randomUUID();val projects=PostgresProjects(source).also {it.migrate()};val jobs=PostgresPlans(source)
        val plot=PlotSetup(lengths=listOf(18.0,12.0,18.0,12.0),vertices=listOf(PlotPoint(0.0,0.0),PlotPoint(18.0,0.0),PlotPoint(18.0,12.0),PlotPoint(0.0,12.0)),roads=listOf(PlotRoad(0)))
        val efficiency=Json.parseToJsonElement("""{"revision":"plotus-space-efficiency.1","advisory_only":true,"floor":{"floor_id":"ground","bathroom_sqft":96.0,"circulation_sqft":42.0},"opportunities":[{"code":"exterior_recess_review","daylight_revalidation_required":true}],"future_measured_field":{"retained":true}}""").jsonObject
        var presentations=0;var exports=0;var expectedUnits=PlotUnits.METRES
        val engine=object:PlanEngine {
            override suspend fun preview(project:Project,setup:FloorPlanSetup)=error("No preview required")
            override suspend fun generate(project:Project,setup:FloorPlanSetup):List<PlanDrawing> = error("Never regenerate saved drawings")
            override suspend fun present(saved:PlotSetup,units:PlotUnits,drawings:List<PlanDrawing>):List<PlanDrawing> {
                presentations++;assertEquals(plot,saved);assertEquals(expectedUnits,units)
                return drawings.map {it.copy(presentation=PlanPresentation(version=2,plotAreaSquareMetres=saved.areaSquareMetres,perimeterMetres=saved.perimeterMetres,builtUpAreaSquareMetres=100.0,carpetAreaSquareMetres=80.0,units=units,vectorAvailable=true),vectorSvgBase64="dmVjdG9yLWZpeHR1cmU=")}
            }
            override suspend fun export(drawing:PlanDrawing,format:String):PlanExport {exports++;assertEquals(expectedUnits,drawing.presentation?.units);return PlanExport("floor.$format","application/octet-stream","Zml4dHVyZQ==")}
        }
        val service=FloorPlans(jobs,engine)
        try {testApplication {
            application {plotusModule(projects,IdentityVerifier {if(it=="export-owner")owner else other},floorPlans=service)}
            var project=runBlocking {
                var p=projects.create(owner,ProjectDraft("Export fixture","Test city"));p=projects.savePlot(owner,UUID.fromString(p.id),p.revision,plot)
                projects.saveFloorPlan(owner,UUID.fromString(p.id),p.revision,FloorPlanSetup(units=PlotUnits.METRES))
            }
            runBlocking {projects.savePreferences(owner,AccountPreferences(PlotUnits.METRES))}
            val job=runBlocking {jobs.start(owner,project,StartPlanRequest(project.revision,UUID.randomUUID().toString())).plan.also {jobs.finish(owner,UUID.fromString(project.id),it.copy(status="ready",drawings=listOf(PlanDrawing("ground","Ground","original-drawing",100,100,presentation=PlanPresentation(version=1),spaceEfficiency=efficiency))))}}
            project=runBlocking {projects.savePlot(owner,UUID.fromString(project.id),project.revision,plot.copy(northDegrees=45.0,roads=plot.roads+PlotRoad(1,priority=1)))}
            project=runBlocking {projects.saveFloorPlan(owner,UUID.fromString(project.id),project.revision,FloorPlanSetup(homes=listOf(PlanHome("home-1",bhk=5,vastu=false))))}
            runBlocking {service.get(owner,UUID.fromString(project.id),UUID.fromString(job.id))}
            val base="/v1/projects/${project.id}/floor-plan/generations/${job.id}"
            suspend fun get(path:String=base,token:String="export-owner")=client.get(path){bearerAuth(token)}
            val opened=Json.decodeFromString<GeneratedFloorPlan>(get().bodyAsText());assertEquals("original-drawing",opened.drawings.single().pngBase64)
            assertNotNull(opened.durationMillis)
            assertEquals(listOf("Road"),opened.roadLabels) // Use this run's original one-road snapshot.
            assertEquals(FloorPlanSetup(units=PlotUnits.METRES),opened.requirements) // Saved generation requirements, never later project edits.
            assertEquals(80.0,opened.drawings.single().presentation?.carpetAreaSquareMetres)
            assertEquals(2,opened.drawings.single().presentation?.version)
            // PostgreSQL persistence, presentation caching and HTTP encoding must
            // keep nested measured metadata and unknown future report fields.
            assertEquals(efficiency,opened.drawings.single().spaceEfficiency)
            assertEquals(efficiency,runBlocking {jobs.find(owner,UUID.fromString(project.id),UUID.fromString(job.id))}?.drawings?.single()?.spaceEfficiency)
            assertEquals(opened,Json.decodeFromString<GeneratedFloorPlan>(get().bodyAsText()));assertEquals(1,presentations)
            for(format in listOf("png","pdf","dwg"))assertEquals("floor.$format",Json.decodeFromString<PlanExport>(get("$base/drawings/ground/export/$format").bodyAsText()).filename)
            assertEquals(3,exports)
            assertEquals(HttpStatusCode.NotFound,get("$base/drawings/ground/export/pdf",token="export-other").status)
            assertEquals(HttpStatusCode.Unauthorized,client.get("$base/drawings/ground/export/pdf").status)
            assertEquals(HttpStatusCode.BadRequest,get("$base/drawings/ground/export/svg").status)
            assertEquals(HttpStatusCode.NotFound,get("$base/drawings/other/export/pdf").status);assertEquals(3,exports)
            expectedUnits=PlotUnits.FEET_INCHES
            runBlocking {projects.savePreferences(owner,AccountPreferences(expectedUnits))}
            val converted=Json.decodeFromString<GeneratedFloorPlan>(get().bodyAsText())
            assertEquals(expectedUnits,converted.drawings.single().presentation?.units)
            assertEquals(opened.durationMillis,converted.durationMillis)
            assertEquals(2,presentations)
            assertEquals(HttpStatusCode.OK,get("$base/drawings/ground/export/pdf").status)
            assertEquals(4,exports)
            assertEquals(project.revision,runBlocking {projects.get(owner,UUID.fromString(project.id))}.revision)
        }}finally {service.close();source.connection.use {c->c.prepareStatement("DELETE FROM plotus.account_preferences WHERE owner_id IN (?,?)").use {it.setObject(1,owner);it.setObject(2,other);it.executeUpdate()};c.prepareStatement("DELETE FROM plotus.projects WHERE owner_id IN (?,?)").use {it.setObject(1,owner);it.setObject(2,other);it.executeUpdate()}}}
    }
    @Test fun generationHistoryIsPagedOwnedAndDoesNotDownloadDrawings() {
        val url=System.getenv("PLOTUS_TEST_DATABASE_URL");assumeTrue(url!=null)
        val source=PGSimpleDataSource().apply {setURL(url!!);user="plotus";password=System.getenv("PLOTUS_TEST_DATABASE_PASSWORD")}
        val owner=UUID.randomUUID();val other=UUID.randomUUID();val projects=PostgresProjects(source).also {it.migrate()};val jobs=PostgresPlans(source)
        val efficiency=Json.parseToJsonElement("""{"revision":"plotus-space-efficiency.1","floor":{"floor_id":"ground","built_up_sqft":1200.0,"carpet_sqft":940.0},"totals":{"circulation_sqft":42.0},"opportunities":[]}""").jsonObject
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
                jobs.finish(owner,UUID.fromString(project.id),job.copy(status="ready",drawings=listOf(PlanDrawing("ground","Ground","test-drawing-payload",100,100,spaceEfficiency=if(it==1)null else efficiency))))
                job.id
            }}
            // Model existing completed runs whose result JSON has no duration field.
            source.connection.use {c->c.prepareStatement("UPDATE plotus.plan_generations SET created_at=created_at-interval '125 seconds',updated_at=created_at WHERE project_id=? AND owner_id=?").use {
                it.setObject(1,UUID.fromString(project.id));it.setObject(2,owner);it.executeUpdate()
            }}
            val path="/v1/projects/${project.id}/floor-plan/generations"
            suspend fun list(suffix:String="",token:String="history-owner")=client.get(path+"?summaries=true"+suffix){bearerAuth(token)}
            val firstResponse=list();assertEquals(HttpStatusCode.OK,firstResponse.status)
            val first=Json.decodeFromString<PlanGenerations>(firstResponse.bodyAsText())
            assertEquals(ids.reversed().take(30),first.plans.map {it.id});assertTrue(first.plans.all {it.drawings.isEmpty()})
            assertTrue(first.plans.all {it.durationMillis==125000L})
            assertFalse(firstResponse.bodyAsText().contains("test-drawing-payload"))
            val legacy=Json.decodeFromString<PlanGenerations>(client.get(path){bearerAuth("history-owner")}.bodyAsText())
            assertEquals(125000L,legacy.plans.single().durationMillis)
            assertEquals(ids.last(),legacy.plans.single().id);assertEquals("test-drawing-payload",legacy.plans.single().drawings.single().pngBase64)
            assertEquals(efficiency,legacy.plans.single().drawings.single().spaceEfficiency)
            val second=Json.decodeFromString<PlanGenerations>(list("&before=${first.nextBefore}").bodyAsText())
            assertEquals(ids.reversed().drop(30),second.plans.map {it.id});assertNull(second.nextBefore)
            val exact=client.get("$path/${ids.first()}"){bearerAuth("history-owner")}
            val oldest=Json.decodeFromString<GeneratedFloorPlan>(exact.bodyAsText()).drawings.single()
            assertEquals("test-drawing-payload",oldest.pngBase64)
            assertNull(oldest.spaceEfficiency) // Earlier drawings have no report.
            assertEquals(HttpStatusCode.NotFound,list(token="history-other").status)
            assertEquals(HttpStatusCode.Unauthorized,client.get(path).status)
            assertEquals(HttpStatusCode.BadRequest,list("&before=not-an-id").status)
            assertTrue(runBlocking {jobs.history(other,UUID.fromString(project.id))}.plans.isEmpty())
            runBlocking {
                val running=jobs.start(owner,project,StartPlanRequest(project.revision,UUID.randomUUID().toString())).plan
                assertNull(running.durationMillis)
                assertNull(jobs.history(owner,UUID.fromString(project.id)).plans.first().durationMillis)
                jobs.finish(owner,UUID.fromString(project.id),running.copy(status="failed",error="Test interruption"))
                val failed=jobs.find(owner,UUID.fromString(project.id),UUID.fromString(running.id))!!
                assertNotNull(failed.durationMillis)
                assertTrue(failed.durationMillis!!>=0)
                assertEquals(failed.durationMillis,jobs.history(owner,UUID.fromString(project.id)).plans.first().durationMillis)
            }
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
            assertNull(job.durationMillis)
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
    @Test fun disabledParkingIsAnExclusionAndUserSetbacksAreNotReplaced() {
        val plot=PlotSetup(lengths=listOf(18.0,12.0,18.0,12.0),roads=listOf(PlotRoad(0))).rebuild()
        val project=ProjectDraft("Parking fixture","Test city").toProject(UUID.randomUUID().toString()).copy(plotSetup=plot)
        val setup=FloorPlanSetup(setbackMode=PlanMode.CUSTOM,commonSetbackMetres=.9144).withParking(false)
        val input=programme(project,setup)
        val parking=input["shared_requirements"]!!.jsonArray.single().jsonObject
        assertEquals("excluded",parking["necessity"]!!.jsonPrimitive.content)
        assertEquals(0,parking["attributes"]!!.jsonObject["cars"]!!.jsonPrimitive.int)
        assertEquals(914.4,input["setbacks"]!!.jsonObject["common_distance_mm"]!!.jsonPrimitive.double)
        val byEdge=programme(project,setup.copy(sameSetback=false,edgeSetbacksMetres=listOf(.3048,.6096,.9144,1.2192)))
        assertEquals(listOf(304.8,609.6,914.4,1219.2),byEdge["setbacks"]!!.jsonObject["by_edge"]!!.jsonArray.map {it.jsonObject["distance_mm"]!!.jsonPrimitive.double})
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
