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

class PlotIntegrationTest {
    @Test fun plotsPersistAcrossDevicesAndRejectForeignOwnersAndStaleRevisions() {
        val url=System.getenv("PLOTUS_TEST_DATABASE_URL")
        assumeTrue("Run services/api/scripts/check.sh for PostgreSQL integration tests",url!=null)
        val source=PGSimpleDataSource().apply {setURL(url!!);user="plotus";password=System.getenv("PLOTUS_TEST_DATABASE_PASSWORD")}
        val owner=UUID.randomUUID();val other=UUID.randomUUID()
        val store=PostgresProjects(source).also {it.migrate()}
        try { testApplication {
            application {plotusModule(store,IdentityVerifier {if(it=="plot-owner")owner else if(it=="plot-other")other else throw ApiFailure(401,"UNAUTHENTICATED","Sign in to continue.")})}
            val created=client.post("/v1/projects") {bearerAuth("plot-owner");contentType(ContentType.Application.Json);setBody(Json.encodeToString(SaveProjectRequest(ProjectDraft("Plot fixture","Test city"))))}
            var project=Json.decodeFromString<Project>(created.bodyAsText())
            val path="/v1/projects/${project.id}/plot"
            suspend fun save(token: String,setup: PlotSetup,revision: Long)=client.put(path) {bearerAuth(token);contentType(ContentType.Application.Json);setBody(Json.encodeToString(SavePlotRequest(setup,revision)))}
            val partial=PlotSetup().withLength(0,12.0)
            project=Json.decodeFromString<Project>(save("plot-owner",partial,project.revision).bodyAsText())
            assertEquals(partial,project.plotSetup);assertNull(project.areaSqFt)
            val setup=PlotSetup(lengths=listOf(12.0,9.0,12.0,9.0),useDiagonals=true,diagonals=listOf(15.0),northDegrees=-45.0,
                roads=listOf(PlotRoad(1,"Access road",9.144,0),PlotRoad(2,"Side road",6.096,1))).rebuild()
            assertEquals(HttpStatusCode.NotFound,save("plot-other",setup,project.revision).status)
            val saved=save("plot-owner",setup,project.revision)
            assertEquals(HttpStatusCode.OK,saved.status)
            val updated=Json.decodeFromString<Project>(saved.bodyAsText())
            assertEquals(setup,updated.plotSetup);assertEquals(1163,updated.areaSqFt)
            assertEquals(project.revision+1,updated.revision)
            val persisted=runBlocking {PostgresProjects(source).get(owner,UUID.fromString(project.id))}
            assertEquals(updated.plotSetup,persisted.plotSetup)
            assertEquals(updated.revision,persisted.revision)
            assertEquals(HttpStatusCode.Conflict,save("plot-owner",setup.copy(northDegrees=90.0),project.revision).status)
            assertEquals(HttpStatusCode.BadRequest,save("plot-owner",setup.copy(northDegrees=181.0),updated.revision).status)
            assertEquals(HttpStatusCode.BadRequest,save("plot-owner",setup.copy(vertices=setup.vertices.dropLast(1)),updated.revision).status)
            assertEquals(HttpStatusCode.BadRequest,save("plot-owner",setup.copy(roads=setup.roads+setup.roads[0]),updated.revision).status)
            val fresh=Json.decodeFromString<Project>(client.get("/v1/projects/${project.id}") {bearerAuth("plot-owner")}.bodyAsText())
            assertEquals(setup,fresh.plotSetup)
            assertEquals(HttpStatusCode.NotFound,client.get("/v1/projects/${project.id}") {bearerAuth("plot-other")}.status)
            assertEquals(HttpStatusCode.Unauthorized,client.put(path) {contentType(ContentType.Application.Json);setBody(Json.encodeToString(SavePlotRequest(setup,updated.revision)))}.status)
        } } finally {
            source.connection.use {c->c.prepareStatement("DELETE FROM plotus.projects WHERE owner_id IN (?,?)").use {it.setObject(1,owner);it.setObject(2,other);it.executeUpdate()}}
        }
    }
}
