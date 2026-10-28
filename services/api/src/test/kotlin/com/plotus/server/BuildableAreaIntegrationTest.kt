package com.plotus.server

import com.plotus.shared.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assume.assumeTrue
import org.postgresql.ds.PGSimpleDataSource
import kotlin.test.*

class BuildableAreaIntegrationTest {
    @Test fun areaSurvivesReloadAndServiceRestartAndOnlyGeometryChangesRecalculateIt() {
        val url=System.getenv("PLOTUS_TEST_DATABASE_URL");assumeTrue(url!=null)
        val source=PGSimpleDataSource().apply {setURL(url!!);user="plotus";password=System.getenv("PLOTUS_TEST_DATABASE_PASSWORD")}
        val owner=UUID.randomUUID();val other=UUID.randomUUID();val projects=PostgresProjects(source).also {it.migrate()}
        var calculations=0
        val engine=object:PlanEngine {
            override suspend fun preview(project:Project,setup:FloorPlanSetup):FloorPlanPreview {
                calculations++;assertEquals(1,setup.buildingFloors);assertEquals(1,setup.homes.size)
                return FloorPlanPreview(FloorEnvelope(areaSquareMetres=100.0-calculations))
            }
            override suspend fun generate(project:Project,setup:FloorPlanSetup):List<PlanDrawing> = error("Envelope calculation must not run a solver")
        }
        val areas=BuildableAreas(PostgresBuildableAreas(source),engine)
        try {testApplication {
            application {plotusModule(projects,IdentityVerifier {if(it=="area-owner")owner else other},buildableAreas=areas)}
            var project=runBlocking {projects.create(owner,ProjectDraft("Area fixture","Test city"))}
            val id=UUID.fromString(project.id);val path="/v1/projects/$id"
            val plot=PlotSetup(lengths=listOf(18.0,12.0,18.0,12.0),vertices=listOf(PlotPoint(0.0,0.0),PlotPoint(18.0,0.0),PlotPoint(18.0,12.0),PlotPoint(0.0,12.0)),roads=listOf(PlotRoad(0)))
            project=runBlocking {projects.savePlot(owner,id,project.revision,plot)}
            suspend fun envelope(setup:FloorPlanSetup,token:String="area-owner")=client.post("$path/floor-plan/envelope") {
                bearerAuth(token);contentType(ContentType.Application.Json);setBody(Json.encodeToString(PreviewFloorPlanRequest(setup,project.revision)))
            }
            val setup=FloorPlanSetup(setbackMode=PlanMode.CUSTOM,commonSetbackMetres=.6)
            assertEquals(HttpStatusCode.NotFound,envelope(setup,"area-other").status)
            val first=Json.decodeFromString<SavedBuildableArea>(envelope(setup).bodyAsText())
            assertEquals(1,calculations);assertEquals(project.revision,runBlocking {projects.get(owner,id)}.revision)
            val changed=setup.copy(buildingFloors=4,units=PlotUnits.METRES,homes=listOf(PlanHome("home-1",bhk=4,vastu=false))).singleResidence()
            assertEquals(first,Json.decodeFromString<SavedBuildableArea>(envelope(changed).bodyAsText()));assertEquals(1,calculations)
            project=runBlocking {projects.saveFloorPlan(owner,id,project.revision,changed)}
            val reopened=Json.decodeFromString<Project>(client.get(path){bearerAuth("area-owner")}.bodyAsText())
            assertEquals(first,reopened.buildableArea)
            val offline=BuildableAreas(PostgresBuildableAreas(source),null)
            assertEquals(first,runBlocking {offline.get(owner,project,changed)})
            assertEquals(first,runBlocking {offline.enrich(owner,project)}.buildableArea)
            val different=changed.copy(commonSetbackMetres=.9)
            assertNotEquals(first.key,Json.decodeFromString<SavedBuildableArea>(envelope(different).bodyAsText()).key);assertEquals(2,calculations)
            assertEquals(first,Json.decodeFromString<SavedBuildableArea>(envelope(setup).bodyAsText()));assertEquals(2,calculations)
            project=runBlocking {projects.savePlot(owner,id,project.revision,plot.copy(northDegrees=45.0))}
            assertNull(runBlocking {offline.enrich(owner,project)}.buildableArea)
            envelope(changed);assertEquals(3,calculations)
            runBlocking {projects.delete(owner,id,project.revision)}
            assertNull(runBlocking {PostgresBuildableAreas(source).find(owner,id,first.key)})
        }}finally {
            source.connection.use {c->c.prepareStatement("DELETE FROM plotus.projects WHERE owner_id IN (?,?)").use {it.setObject(1,owner);it.setObject(2,other);it.executeUpdate()}}
        }
    }
}
