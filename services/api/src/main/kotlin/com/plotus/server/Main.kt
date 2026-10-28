package com.plotus.server

import com.plotus.shared.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.bodylimit.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.AttributeKey
import java.sql.SQLException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException

private val Owner = AttributeKey<UUID>("verified-owner")

fun Application.plotusModule(store: ProjectStore, identity: IdentityVerifier) {
    install(ContentNegotiation) { json() }
    install(RequestBodyLimit) { bodyLimit { 16 * 1024L } }
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            if (cause is CancellationException) throw cause
            val failure = when(cause) {
                is ApiFailure -> cause
                is PayloadTooLargeException -> ApiFailure(413,"REQUEST_TOO_LARGE","Project details are too large.")
                is UnsupportedMediaTypeException -> ApiFailure(415,"INVALID_CONTENT_TYPE","Send project details as JSON.")
                is BadRequestException, is SerializationException -> ApiFailure(400,"INVALID_REQUEST","Check the project details and try again.")
                is SQLException -> ApiFailure(503,"STORAGE_UNAVAILABLE","Projects are temporarily unavailable. Please try again.")
                else -> ApiFailure(500,"INTERNAL_ERROR","This request could not be completed. Please try again.")
            }
            call.respond(HttpStatusCode.fromValue(failure.status),ApiError(ErrorDetail(failure.code,failure.message,failure.fields)))
        }
    }
    intercept(ApplicationCallPipeline.Call) { call.response.header(HttpHeaders.CacheControl,"no-store") }
    routing {
        get("/health") {
            if (!store.healthy()) throw ApiFailure(503,"NOT_READY","Project storage is not ready.")
            call.respond(Health())
        }
        route("/v1") {
            intercept(ApplicationCallPipeline.Call) {
                val header = call.request.header(HttpHeaders.Authorization).orEmpty()
                if (!header.startsWith("Bearer ")) throw ApiFailure(401,"UNAUTHENTICATED","Sign in to continue.")
                call.attributes.put(Owner,identity.user(header.removePrefix("Bearer ")))
            }
            get("/projects") { call.respond(ProjectList(store.list(call.attributes[Owner]))) }
            get("/projects/{id}") { call.respond(store.get(call.attributes[Owner],call.projectID())) }
            post("/projects") {
                val request = call.receive<SaveProjectRequest>()
                request.details.validate()
                call.respond(HttpStatusCode.Created,store.create(call.attributes[Owner],request.details))
            }
            put("/projects/{id}") {
                val request = call.receive<SaveProjectRequest>()
                request.details.validate()
                val revision = request.revision?.takeIf { it>0 } ?: throw ApiFailure(400,"INVALID_REVISION","Refresh the project before saving changes.")
                call.respond(store.update(call.attributes[Owner],call.projectID(),revision,request.details))
            }
            put("/projects/{id}/plot") {
                val request = call.receive<SavePlotRequest>()
                val errors = request.setup.errors()
                if (errors.isNotEmpty()) throw ApiFailure(400,"VALIDATION_FAILED","Check the plot details.",errors)
                if (request.revision <= 0) throw ApiFailure(400,"INVALID_REVISION","Reopen this project before saving changes.")
                call.respond(store.savePlot(call.attributes[Owner],call.projectID(),request.revision,request.setup))
            }
            delete("/projects/{id}") {
                val request = call.receive<DeleteProjectRequest>()
                if (request.revision<=0) throw ApiFailure(400,"INVALID_REVISION","Refresh the project before deleting it.")
                store.delete(call.attributes[Owner],call.projectID(),request.revision)
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}

private fun ProjectDraft.validate() {
    val errors = errors()
    if (errors.isNotEmpty()) throw ApiFailure(400,"VALIDATION_FAILED","Check the project details.",errors)
}
private fun ApplicationCall.projectID(): UUID = try { UUID.fromString(parameters["id"]) }
    catch (_: IllegalArgumentException) { throw ApiFailure(400,"INVALID_PROJECT_ID","This project address is invalid.") }

fun main() {
    val store = PostgresProjects.configured()
    val identity = AuthServiceIdentity(System.getenv("AUTH_INTERNAL_URL") ?: "http://127.0.0.1:4001")
    val port = System.getenv("PORT")?.toIntOrNull() ?: 4000
    embeddedServer(Netty,port=port,host=System.getenv("HOST") ?: "127.0.0.1") { plotusModule(store,identity) }.start(wait=true)
}
