package com.plotus.server

import com.plotus.shared.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.plotusModule() {
    install(ContentNegotiation) { json() }
    intercept(ApplicationCallPipeline.Call) {
        call.response.header(HttpHeaders.CacheControl, "no-store")
    }
    routing {
        get("/health") { call.respond(Health()) }
        route("/v1/{path...}") {
            handle { call.respond(HttpStatusCode.NotImplemented, ApiError(ErrorDetail("NOT_IMPLEMENTED", "This API is reserved for the next milestone."))) }
        }
    }
}

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 4000
    embeddedServer(Netty, port = port, host = "127.0.0.1", module = Application::plotusModule).start(wait = true)
}
