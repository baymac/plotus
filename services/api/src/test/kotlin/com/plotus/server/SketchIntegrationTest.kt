package com.plotus.server

import com.plotus.shared.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assume.assumeTrue
import org.postgresql.ds.PGSimpleDataSource
import kotlin.test.*

class SketchIntegrationTest {
    @Test fun sketchAnalysisChecksProjectOwnershipAndNeverSavesUnreviewedMeasurements() {
        val url=System.getenv("PLOTUS_TEST_DATABASE_URL");assumeTrue("Run services/api/scripts/check.sh",url!=null)
        val source=PGSimpleDataSource().apply {setURL(url!!);user="plotus";password=System.getenv("PLOTUS_TEST_DATABASE_PASSWORD")}
        val owner=UUID.randomUUID();val other=UUID.randomUUID();val store=PostgresProjects(source).also {it.migrate()}
        val gate=CompletableDeferred<Unit>();val calls=AtomicInteger()
        val imports=SketchImports(SketchReader {calls.incrementAndGet();gate.await();ReadSketch(List(4){SketchLength(metres=10.0)}).suggestion()})
        try {testApplication {
            application {plotusModule(store,IdentityVerifier {if(it=="owner")owner else if(it=="other")other else throw ApiFailure(401,"UNAUTHENTICATED","Sign in.")},imports)}
            val created=client.post("/v1/projects") {bearerAuth("owner");contentType(ContentType.Application.Json);setBody(Json.encodeToString(SaveProjectRequest(ProjectDraft("Sketch import fixture","Test city"))))}
            val project=Json.decodeFromString<Project>(created.bodyAsText());val path="/v1/projects/${project.id}/sketch"
            suspend fun post(token:String?,image:SketchImage=SketchTest.validImage())=client.post(path) {if(token!=null)bearerAuth(token);contentType(ContentType.Application.Json);setBody(Json.encodeToString(image))}
            assertEquals(HttpStatusCode.Unauthorized,post(null).status)
            assertEquals(HttpStatusCode.NotFound,post("other").status)
            assertEquals(0,calls.get())
            assertEquals(HttpStatusCode.BadRequest,post("owner",SketchImage("image/png","not-an-image")).status)
            val response=post("owner");assertEquals(HttpStatusCode.Accepted,response.status)
            val job=Json.decodeFromString<SketchImport>(response.bodyAsText())
            assertEquals(HttpStatusCode.TooManyRequests,post("owner").status)
            assertEquals(HttpStatusCode.NotFound,client.get("$path/${job.id}") {bearerAuth("other")}.status)
            gate.complete(Unit)
            val ready=withTimeout(5000) {
                var result=job
                while(result.status=="reading") {delay(20);result=Json.decodeFromString<SketchImport>(client.get("$path/${job.id}") {bearerAuth("owner")}.bodyAsText())}
                result
            }
            assertEquals("ready",ready.status);assertNotNull(ready.suggestion);assertEquals(1,calls.get())
            val untouched=Json.decodeFromString<Project>(client.get("/v1/projects/${project.id}") {bearerAuth("owner")}.bodyAsText())
            assertNull(untouched.plotSetup);assertEquals(project.revision,untouched.revision)
        }} finally {
            imports.close()
            source.connection.use {c->c.prepareStatement("DELETE FROM plotus.projects WHERE owner_id=?").use {it.setObject(1,owner);it.executeUpdate()}}
        }
    }
}
