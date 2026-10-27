package com.plotus.server

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class IdentityTest {
    @Test fun authenticationUsesTheAuthServiceAndFailsClosed() = runBlocking {
        val owner = UUID.randomUUID()
        val status = AtomicInteger(200)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1",0),0)
        server.createContext("/api/auth/user/me") { exchange ->
            assertEquals("Bearer isolated-test-token",exchange.requestHeaders.getFirst("Authorization"))
            val body = "{\"id\":\"$owner\"}".toByteArray()
            exchange.sendResponseHeaders(status.get(),body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            val identity = AuthServiceIdentity("http://127.0.0.1:${server.address.port}")
            assertEquals(owner,identity.user("isolated-test-token"))
            for (code in listOf(401,403,503,302)) {
                status.set(code)
                val failure = assertFailsWith<ApiFailure> { identity.user("isolated-test-token") }
                assertEquals(if(code in listOf(401,403)) 401 else 503,failure.status)
            }
            assertEquals(401,assertFailsWith<ApiFailure> { identity.user("") }.status)
        } finally { server.stop(0) }
    }
}
