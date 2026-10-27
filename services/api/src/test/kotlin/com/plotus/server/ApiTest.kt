package com.plotus.server

import com.plotus.shared.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import java.util.UUID
import kotlin.test.*

class ApiTest {
    @Test fun unauthenticatedRequestsNeverReachProjectStorage() = testApplication {
        application { plotusModule(UnusedProjects(),IdentityVerifier { throw ApiFailure(401,"UNAUTHENTICATED","Sign in to continue.") }) }
        assertEquals(HttpStatusCode.OK,client.get("/health").status)
        for (response in listOf(client.get("/v1/projects"),client.post("/v1/projects") {
            bearerAuth("invalid-token");contentType(ContentType.Application.Json);setBody("{}")
        })) {
            assertEquals(HttpStatusCode.Unauthorized,response.status)
            assertTrue(response.bodyAsText().contains("UNAUTHENTICATED"))
            assertFalse(response.bodyAsText().contains("preview-"))
            assertEquals("no-store",response.headers[HttpHeaders.CacheControl])
        }
        assertEquals(HttpStatusCode.NotFound,client.get("/missing").status)
    }
}

private class UnusedProjects : ProjectStore {
    override suspend fun healthy() = true
    override suspend fun list(owner: UUID): List<Project> = error("Storage reached without authentication")
    override suspend fun get(owner: UUID,id: UUID): Project = error("unexpected")
    override suspend fun create(owner: UUID,draft: ProjectDraft): Project = error("unexpected")
    override suspend fun update(owner: UUID,id: UUID,revision: Long,draft: ProjectDraft): Project = error("unexpected")
    override suspend fun delete(owner: UUID,id: UUID,revision: Long): Unit = error("unexpected")
}
