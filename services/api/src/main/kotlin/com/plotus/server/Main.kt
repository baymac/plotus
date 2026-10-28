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

fun Application.plotusModule(store: ProjectStore, identity: IdentityVerifier, sketches: SketchImports = SketchImports.configured(), floorPlans:FloorPlans=FloorPlans(),buildableAreas:BuildableAreas=BuildableAreas()) {
    monitor.subscribe(ApplicationStopped) {sketches.close();floorPlans.close()}
    install(ContentNegotiation) { json() }
    install(RequestBodyLimit) { bodyLimit { call -> when {
        call.request.path().matches(Regex("^/v1/projects/[^/]+/sketch$"))->4*1024*1024L
        call.request.path().matches(Regex("^/v1/projects/[^/]+/floor-plan(?:/(preview|envelope))?$"))->128*1024L
        else->16*1024L
    } } }
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
            get("/projects/{id}") { val owner=call.attributes[Owner];call.respond(buildableAreas.enrich(owner,store.get(owner,call.projectID()))) }
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
            post("/projects/{id}/sketch") {
                val owner=call.attributes[Owner];val project=call.projectID()
                store.get(owner,project)
                call.respond(HttpStatusCode.Accepted,sketches.start(owner,project,call.receive<SketchImage>()))
            }
            put("/projects/{id}/floor-plan") {
                val owner=call.attributes[Owner];val id=call.projectID()
                val request=call.receive<SaveFloorPlanRequest>();val project=store.get(owner,id)
                if(request.revision<=0)throw ApiFailure(400,"INVALID_REVISION","Reopen this project before saving changes.")
                if(project.plotSetup==null)throw ApiFailure(400,"PLOT_REQUIRED","Complete Plot setup first.")
                val errors=request.setup.errors(project.plotSetup)
                if(errors.isNotEmpty())throw ApiFailure(400,"VALIDATION_FAILED","Check the floor-plan details.",errors)
                call.respond(buildableAreas.enrich(owner,store.saveFloorPlan(owner,id,request.revision,request.setup)))
            }
            post("/projects/{id}/floor-plan/envelope") {
                val owner=call.attributes[Owner];val project=store.get(owner,call.projectID());val request=call.receive<PreviewFloorPlanRequest>()
                if(project.revision!=request.revision)throw ApiFailure(409,"PROJECT_CHANGED","The project changed. Reopen Floor plan.")
                call.respond(buildableAreas.get(owner,project,request.setup))
            }
            post("/projects/{id}/floor-plan/preview") {
                val project=store.get(call.attributes[Owner],call.projectID());val request=call.receive<PreviewFloorPlanRequest>()
                if(project.revision!=request.revision)throw ApiFailure(409,"PROJECT_CHANGED","The project changed. Reopen Floor plan.")
                call.respond(floorPlans.preview(project,request.setup))
            }
            get("/projects/{id}/floor-plan/generations") {
                val owner=call.attributes[Owner];val id=call.projectID();store.get(owner,id)
                val before=call.request.queryParameters["before"]?.let {value->try {UUID.fromString(value)}catch(_:IllegalArgumentException){throw ApiFailure(400,"INVALID_CURSOR","Reopen generation history.")}}
                // Older installed clients expect the latest drawing inline.
                call.respond(if(call.request.queryParameters["summaries"]=="true")floorPlans.history(owner,id,before) else PlanGenerations(listOfNotNull(floorPlans.latest(owner,id))))
            }
            post("/projects/{id}/floor-plan/generations") {
                val owner=call.attributes[Owner];val project=store.get(owner,call.projectID())
                call.respond(HttpStatusCode.Accepted,floorPlans.start(owner,project,call.receive<StartPlanRequest>()))
            }
            get("/projects/{id}/floor-plan/generations/{job}") {
                val owner=call.attributes[Owner];val id=call.projectID();store.get(owner,id)
                val job=try {UUID.fromString(call.parameters["job"])}catch(_:IllegalArgumentException){throw ApiFailure(404,"PLAN_NOT_FOUND","This plan could not be found.")}
                call.respond(floorPlans.get(owner,id,job,call.request.queryParameters["by"]=="request"))
            }
            get("/projects/{id}/sketch/{job}") {
                val owner=call.attributes[Owner];val project=call.projectID()
                store.get(owner,project)
                call.respond(sketches.get(owner,project,call.parameters["job"].orEmpty()))
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
    val jobs=PostgresPlans(store.source).also {it.recover()}
    val engine=HttpPlanEngine.configured()
    val plans=FloorPlans(jobs,engine)
    val areas=BuildableAreas(PostgresBuildableAreas(store.source),engine)
    embeddedServer(Netty,port=port,host=System.getenv("HOST") ?: "127.0.0.1") { plotusModule(store,identity,floorPlans=plans,buildableAreas=areas) }.start(wait=true)
}
