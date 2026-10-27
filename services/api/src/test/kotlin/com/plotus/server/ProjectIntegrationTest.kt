package com.plotus.server

import com.plotus.shared.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.junit.Assume.assumeTrue
import org.postgresql.ds.PGSimpleDataSource
import kotlin.test.*

class ProjectIntegrationTest {
    @Test fun projectsPersistAndEveryOperationEnforcesOwnershipAndRevision() {
        val url = System.getenv("PLOTUS_TEST_DATABASE_URL")
        assumeTrue("Run services/api/scripts/check.sh for PostgreSQL integration tests",url!=null)
        val source = PGSimpleDataSource().apply {
            setURL(url!!);user="plotus";password=System.getenv("PLOTUS_TEST_DATABASE_PASSWORD")
        }
        val owner = UUID.randomUUID(); val other = UUID.randomUUID()
        val store = PostgresProjects(source).also { it.migrate() }
        try { testApplication {
            application { plotusModule(store,IdentityVerifier { when(it) {
                "owner-fixture" -> owner; "other-fixture" -> other
                else -> throw ApiFailure(401,"UNAUTHENTICATED","Sign in to continue.")
            } }) }
            suspend fun list(token: String) = Json.decodeFromString<ProjectList>(client.get("/v1/projects") { bearerAuth(token) }.bodyAsText()).projects
            assertTrue(list("owner-fixture").isEmpty())
            val draft = ProjectDraft("  Courtyard Home  ","Bengaluru",true,"Client","client@example.invalid","1234")
            val created = client.post("/v1/projects") {
                bearerAuth("owner-fixture"); contentType(ContentType.Application.Json)
                setBody(Json.encodeToString(SaveProjectRequest(draft)))
            }
            assertEquals(HttpStatusCode.Created,created.status)
            val project = Json.decodeFromString<Project>(created.bodyAsText())
            assertEquals("Courtyard Home",project.name);assertEquals(1L,project.revision)
            assertEquals("Client",project.client?.name)
            assertFalse(project.id.startsWith("preview-"));assertTrue(project.updatedAt.isNotBlank())
            assertTrue(list("other-fixture").isEmpty())
            assertEquals(project.id,list("owner-fixture").single().id)
            assertEquals(project.id,runBlocking { PostgresProjects(source).get(owner,UUID.fromString(project.id)) }.id)
            val path="/v1/projects/${project.id}"
            assertEquals(HttpStatusCode.NotFound,client.get(path) { bearerAuth("other-fixture") }.status)
            suspend fun update(token: String, revision: Long) = client.put(path) {
                bearerAuth(token);contentType(ContentType.Application.Json)
                setBody(Json.encodeToString(SaveProjectRequest(draft.copy(name="Updated home",isClient=false),revision)))
            }
            suspend fun delete(token: String, revision: Long) = client.delete(path) {
                bearerAuth(token);contentType(ContentType.Application.Json);setBody(Json.encodeToString(DeleteProjectRequest(revision)))
            }
            assertEquals(HttpStatusCode.NotFound,update("other-fixture",1).status)
            assertEquals(HttpStatusCode.NotFound,delete("other-fixture",1).status)
            val updated = update("owner-fixture",1)
            assertEquals(HttpStatusCode.OK,updated.status)
            val latest = Json.decodeFromString<Project>(updated.bodyAsText())
            assertEquals(2L,latest.revision);assertEquals("Updated home",latest.name);assertNull(latest.client)
            assertEquals(HttpStatusCode.Conflict,update("owner-fixture",1).status)
            assertEquals(HttpStatusCode.Conflict,delete("owner-fixture",1).status)
            val invalid = client.post("/v1/projects") {
                bearerAuth("owner-fixture");contentType(ContentType.Application.Json)
                setBody(Json.encodeToString(SaveProjectRequest(ProjectDraft())))
            }
            assertEquals(HttpStatusCode.BadRequest,invalid.status)
            assertTrue(invalid.bodyAsText().contains("VALIDATION_FAILED"))
            assertEquals(HttpStatusCode.BadRequest,client.get("/v1/projects/invalid-id") { bearerAuth("owner-fixture") }.status)
            val oversized = client.post("/v1/projects") {
                bearerAuth("owner-fixture");contentType(ContentType.Application.Json);setBody("{\"details\":\""+"x".repeat(20000)+"\"}")
            }
            assertEquals(HttpStatusCode.PayloadTooLarge,oversized.status)
            val foreignOwner = client.post("/v1/projects") {
                bearerAuth("owner-fixture");contentType(ContentType.Application.Json)
                setBody("{\"details\":{\"name\":\"Injected\",\"location\":\"Test\"},\"owner_id\":\"$other\"}")
            }
            assertEquals(HttpStatusCode.BadRequest,foreignOwner.status)
            assertEquals(HttpStatusCode.NoContent,delete("owner-fixture",2).status)
            assertTrue(list("owner-fixture").isEmpty())
            assertEquals(HttpStatusCode.NotFound,client.get(path) { bearerAuth("owner-fixture") }.status)
        } } finally {
            source.connection.use { c -> c.prepareStatement("DELETE FROM plotus.projects WHERE owner_id IN (?,?)").use {
                it.setObject(1,owner);it.setObject(2,other);it.executeUpdate()
            } }
        }
    }
}
