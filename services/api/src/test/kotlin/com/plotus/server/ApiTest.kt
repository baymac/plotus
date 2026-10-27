package com.plotus.server

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.*

class ApiTest {
    @Test fun onlyHealthIsImplementedAndPrivateRoutesNeverReturnFixtures() = testApplication {
        application { plotusModule() }
        assertEquals(HttpStatusCode.OK, client.get("/health").status)
        for (path in listOf("/v1/projects", "/v1/session")) {
            val response = client.get(path)
            assertEquals(HttpStatusCode.NotImplemented, response.status)
            assertTrue(response.bodyAsText().contains("NOT_IMPLEMENTED"))
            assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
        }
        assertEquals(HttpStatusCode.NotFound, client.get("/missing").status)
    }
}
