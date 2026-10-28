package com.plotus.server

import com.plotus.shared.*
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.UUID
import javax.sql.DataSource
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import kotlin.math.roundToInt

interface PlanEngine {
    suspend fun preview(project:Project,setup:FloorPlanSetup):FloorPlanPreview
    suspend fun generate(project:Project,setup:FloorPlanSetup):List<PlanDrawing>
}

/** The private service URL and bearer credential never leave the API process. */
class HttpPlanEngine(private val endpoint:URI,private val token:String):PlanEngine {
    private val client=HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build()
    private suspend fun request(path:String,body:JsonObject):JsonObject=try {withContext(Dispatchers.IO) {
        val request=HttpRequest.newBuilder(endpoint.resolve(path)).timeout(Duration.ofSeconds(if(path=="/v1/programmes/resolve")15 else 260))
            .header("Authorization","Bearer $token").header("Content-Type","application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build()
        var response=client.send(request,HttpResponse.BodyHandlers.ofInputStream())
        // The service returns 429 before acquiring its worker. A just-finished
        // preview can still be releasing that worker; retry only this safe case.
        var retry=0
        while(response.statusCode()==429&&retry++<3) {
            response.body().close();delay(750L*retry)
            response=client.send(request,HttpResponse.BodyHandlers.ofInputStream())
        }
        val bytes=response.body().use {it.readNBytes(24*1024*1024+1)}
        if(bytes.size>24*1024*1024)throw ApiFailure(502,"PLAN_TOO_LARGE","The plan preview is too large. Try fewer floors.")
        if(response.statusCode()==429)throw ApiFailure(429,"ENGINE_BUSY","A plan is being generated. Try again shortly.")
        if(response.statusCode()==422) {
            val code=runCatching {Json.parseToJsonElement(bytes.decodeToString()).jsonObject["error"]?.jsonObject?.get("code")?.jsonPrimitive?.contentOrNull}.getOrNull()
            throw when(code) {
                "search_budget_exhausted"->ApiFailure(422,"PLAN_SEARCH_LIMIT","Search limit reached. Try again or simplify the requirements.")
                "generation_resource_limit"->ApiFailure(503,"PLAN_RESOURCE_LIMIT","Generation resources are busy. Try again shortly.")
                "solver_model_error","architectural_detail_error","graph_drafting_error"->ApiFailure(503,"PLAN_ENGINE_ERROR","Couldn’t complete generation. Please try again.")
                else->ApiFailure(422,"PLAN_NO_FIT","No plan passed the layout checks. Adjust setbacks or requirements and try again.")
            }
        }
        if(response.statusCode()==504)throw ApiFailure(504,"PLAN_TIMED_OUT","Generation reached its time limit. Try again.")
        if(response.statusCode()!=200)throw ApiFailure(503,"ENGINE_UNAVAILABLE","Plan generation is temporarily unavailable.")
        try {Json.parseToJsonElement(bytes.decodeToString()).jsonObject}catch(_:Exception){throw ApiFailure(502,"ENGINE_INVALID_RESPONSE","The plan preview couldn’t be opened.")}
    }}catch(failure:IOException){throw ApiFailure(503,"ENGINE_UNAVAILABLE","Plan generation is temporarily unavailable.")}
    override suspend fun preview(project:Project,setup:FloorPlanSetup):FloorPlanPreview {
        val local=setup.capabilityFindings()
        val response=request("/v1/programmes/resolve",buildJsonObject {put("programme",programme(project,setup))})
        val resolved=response["programme"]?.jsonObject
        val attempt=resolved?.get("site_attempts")?.jsonArray?.firstOrNull()?.jsonObject
        val plot=project.plotSetup!!
        val origin=plot.vertices.first()
        val outlines=attempt?.get("envelope_geometry")?.jsonArray.orEmpty().map {part->
            part.jsonObject["outer"]!!.jsonArray.map {xy->val pair=xy.jsonArray;PlotPoint(pair[0].jsonPrimitive.double/1000+origin.x,-pair[1].jsonPrimitive.double/1000+origin.y)}
        }
        val envelope=attempt?.let {FloorEnvelope(outlines,it["buildable_area_sqft"]?.jsonPrimitive?.doubleOrNull?.times(METRES_PER_FOOT*METRES_PER_FOOT),
            it["edge_setbacks"]?.jsonArray.orEmpty().map {edge->edge.jsonObject["distance_mm"]!!.jsonPrimitive.double/1000},it["buildable_empty"]?.jsonPrimitive?.booleanOrNull ?: outlines.isEmpty())}
        val issues=buildList {
            addAll(local)
            if(envelope?.empty==true)add(PlanFinding("These setbacks leave no buildable area.","setbacks"))
            val findings=resolved?.get("issues")?.jsonArray.orEmpty()+response["geometry_capability_issues"]?.jsonArray.orEmpty()
            findings.forEach {item->val issue=item.jsonObject;val message=issue["message"]?.jsonPrimitive?.contentOrNull
                if(message!=null)add(PlanFinding(message.take(360),issue["requirement_id"]?.jsonPrimitive?.contentOrNull))}
        }.distinctBy {it.message}.take(20)
        return FloorPlanPreview(envelope,issues,response["generation_available"]?.jsonPrimitive?.booleanOrNull==true&&issues.isEmpty())
    }
    override suspend fun generate(project:Project,setup:FloorPlanSetup):List<PlanDrawing> {
        val preview=preview(project,setup)
        if(!preview.canGenerate)throw ApiFailure(422,"PLAN_NO_FIT",preview.findings.firstOrNull()?.message ?: "Check the floor-plan requirements.")
        val result=request("/v1/plans/generate",buildJsonObject {
            put("programme",programme(project,setup));put("search_seconds",60);put("deterministic_limit",10);put("output_stage","architectural");put("seed",0)
        })
        val drawings=Json.decodeFromJsonElement<List<PlanDrawing>>(result["drawings"] ?: throw ApiFailure(502,"PLAN_INVALID","No floor drawing was returned."))
        if(drawings.isEmpty()||drawings.size>6||drawings.any {it.width !in 1..4096||it.height !in 1..4096||it.width.toLong()*it.height>16_000_000||it.pngBase64.length>6_000_000})
            throw ApiFailure(502,"PLAN_INVALID","The plan preview couldn’t be opened.")
        return drawings
    }
    companion object {
        fun configured():PlanEngine? {
            val url=System.getenv("PLOTUS_ENGINE_URL")?.trim().orEmpty();val token=System.getenv("PLOTUS_ENGINE_SERVICE_TOKEN")?.trim().orEmpty()
            if(url.isBlank()||token.isBlank())return null
            val uri=URI(url);require(uri.scheme in listOf("http","https")&&uri.host!=null&&uri.userInfo==null&&uri.query==null&&uri.fragment==null&&uri.path in listOf("","/"))
            return HttpPlanEngine(uri,token)
        }
    }
}

internal fun FloorPlanSetup.capabilityFindings():List<PlanFinding> = buildList {
    if(homes.map {it.vastu}.distinct().size>1)add(PlanFinding("Use the same Vastu preference for every home in this building.","vastu"))
    homes.forEach {home->
        if(home.notes.isNotBlank())add(PlanFinding("Additional notes are saved for review. Remove them to generate with the supported room controls.","home.${home.id}.notes"))
        if(home.mode==PlanMode.CUSTOM)home.rooms.filter {it.size!=RoomSize.MEDIUM}.forEach {add(PlanFinding("Custom room sizes aren’t available yet. Use automatic sizing for ${it.title}.","room.${it.id}"))}
    }
    sharedRooms.filter {it.size!=RoomSize.MEDIUM}.forEach {add(PlanFinding("Use automatic sizing for ${it.title}.","room.${it.id}"))}
    (homes.filter {it.mode==PlanMode.CUSTOM}.flatMap {it.rooms}+sharedRooms).forEach {
        if(it.open)add(PlanFinding("Open room layouts aren’t available yet.","room.${it.id}"))
        if(it.closet==Closet.WARDROBE)add(PlanFinding("Use a walk-in closet or remove the wardrobe request.","room.${it.id}"))
    }
}

/** Fresh boundary adapter: measured metres become millimetres; ownership never comes from this payload. */
internal fun programme(project:Project,setup:FloorPlanSetup):JsonObject {
    val plot=project.plotSetup ?: throw ApiFailure(400,"PLOT_REQUIRED","Complete Plot setup first.")
    val origin=plot.vertices.first()
    if(plot.vertices.any {kotlin.math.abs(it.x-origin.x)>1000||kotlin.math.abs(it.y-origin.y)>1000})throw ApiFailure(400,"PLOT_TOO_LARGE","The plot is too large for floor-plan generation.")
    fun edge(i:Int)="${vertexName(i)}-${vertexName((i+1)%plot.lengths.size)}"
    fun rooms(items:List<PlanRoom>,shared:Boolean=false):JsonArray=buildJsonArray {
        fun requirement(id:String,kind:String,room:PlanRoom,attributes:JsonObject?=null) {
            addJsonObject {put("id",id);put("kind",kind);put("text",room.title);put("count",1);put("confirmed",true);put("necessity","required")
                put("placement",if(room.floor!=null)"specified_floors" else if(shared)"any_building_floor" else "any_occupied_floor")
                putJsonArray("floor_ids") {room.floor?.let {add("floor-$it")}}
                if(attributes!=null)put("attributes",attributes)}
        }
        items.forEach {room->
            val kind=when(room.kind) {
                RoomKind.BEDROOM->"bedroom";RoomKind.LIVING->"living";RoomKind.DINING->"dining";RoomKind.KITCHEN->"kitchen";RoomKind.BATHROOM->"common_bathroom"
                RoomKind.UTILITY->"utility";RoomKind.OFFICE->"study";RoomKind.PRAYER->"puja";RoomKind.PARKING->"parking";RoomKind.LIFT->"lift";RoomKind.STAIRCASE->"staircase"
                RoomKind.BALCONY->"balcony";RoomKind.GARDEN->"garden";else->"custom"
            }
            requirement(room.id,kind,room,if(room.kind==RoomKind.PARKING)buildJsonObject {put("cars",room.cars);put("bikes",room.bikes);put("type",if(room.covered)"covered" else "open")} else null)
            val bedroomID="${room.id}:bedroom:1"
            if(room.attachedBathroom)requirement("${room.id}-bath","attached_bathroom",room,buildJsonObject {putJsonArray("bedroom_ids") {add(bedroomID)}})
            if(room.closet!=Closet.NONE)requirement("${room.id}-closet","dressing",room,buildJsonObject {putJsonArray("bedroom_ids") {add(bedroomID)}})
            if(room.balcony)requirement("${room.id}-balcony","balcony",room,buildJsonObject {putJsonArray("bedroom_ids") {add(bedroomID)}})
            if(room.utility)requirement("${room.id}-utility","utility",room)
        }
    }
    return buildJsonObject {
        put("schema_version","naksha.app-plan-setup.v1");put("id","plotus-${project.id}")
        putJsonObject("plot") {
            put("confirmed",true);put("coordinate_system","x_right_y_down");put("north_angle_deg",(plot.northDegrees+360)%360);put("road_status","known")
            putJsonArray("vertices") {plot.vertices.forEachIndexed {i,p->addJsonObject {put("id",vertexName(i));put("x_mm",((p.x-origin.x)*1000).roundToInt());put("y_mm",((p.y-origin.y)*1000).roundToInt())}}}
            putJsonArray("roads") {plot.roads.sortedBy {it.priority}.forEachIndexed {i,r->addJsonObject {put("edge_id",edge(r.side));put("priority",listOf("primary","secondary","tertiary").getOrElse(i){"others"})}}}
        }
        putJsonObject("setbacks") {
            put("mode",if(setup.setbackMode==PlanMode.AUTO)"automatic" else "custom")
            if(setup.setbackMode==PlanMode.CUSTOM) {
                put("distribution",if(setup.sameSetback)"common" else "by_edge")
                if(setup.sameSetback)put("common_distance_mm",((setup.commonSetbackMetres ?: 0.0)*1000).roundToInt())
                else putJsonArray("by_edge") {setup.edgeSetbacksMetres.forEachIndexed {i,v->addJsonObject {put("edge_id",edge(i));put("distance_mm",((v ?: 0.0)*1000).roundToInt())}}}
            }
        }
        putJsonArray("floors") {repeat(setup.buildingFloors) {addJsonObject {put("id","floor-$it");put("level",it);put("name",floorName(it))}}}
        putJsonArray("units") {setup.homes.forEach {home->addJsonObject {
            put("id",home.id);put("bhk",home.bhk);put("start_floor_id","floor-${home.startFloor}");put("floors_occupied",home.floorsOccupied)
            put("requirements_mode",if(home.mode==PlanMode.AUTO)"automatic" else "custom")
            if(home.mode==PlanMode.CUSTOM)put("requirements",rooms(home.rooms))
        }}}
        put("shared_requirements",rooms(setup.sharedRooms,true));put("vastu",setup.homes.all {it.vastu})
    }
}

/** Durable, owner-scoped jobs. The unique request ID makes a lost HTTP response safe to recover. */
data class StartedPlan(val plan:GeneratedFloorPlan,val created:Boolean)
class PostgresPlans(private val source:DataSource) {
    /** List lightweight summaries; fetch a drawing only when its history entry is opened. */
    suspend fun history(owner:UUID,project:UUID,before:UUID?=null):PlanGenerations=db {c->
        val cursor=if(before==null)"" else " AND (created_at,id)<(SELECT created_at,id FROM plotus.plan_generations WHERE owner_id=? AND project_id=? AND id=?)"
        c.prepareStatement("SELECT id,status,created_at,source_revision,result->>'error' AS error FROM plotus.plan_generations WHERE owner_id=? AND project_id=?$cursor ORDER BY created_at DESC,id DESC LIMIT 31").use {
            it.setObject(1,owner);it.setObject(2,project)
            if(before!=null){it.setObject(3,owner);it.setObject(4,project);it.setObject(5,before)}
            val rows=it.executeQuery().use {r->buildList {while(r.next())add(GeneratedFloorPlan(r.getObject("id").toString(),r.getString("status"),error=r.getString("error"),createdAt=r.getTimestamp("created_at").toInstant().toString(),sourceRevision=r.getLong("source_revision")))}}
            PlanGenerations(rows.take(30),if(rows.size>30)rows[29].id else null)
        }
    }
    suspend fun find(owner:UUID,project:UUID,id:UUID,byRequest:Boolean=false):GeneratedFloorPlan?=db {c->
        c.prepareStatement("SELECT * FROM plotus.plan_generations WHERE owner_id=? AND project_id=? AND ${if(byRequest)"request_id" else "id"}=?").use {
            it.setObject(1,owner);it.setObject(2,project);it.setObject(3,id);it.executeQuery().use {r->if(r.next())r.plan() else null}
        }
    }
    suspend fun latest(owner:UUID,project:UUID):GeneratedFloorPlan?=db {c->c.prepareStatement("SELECT * FROM plotus.plan_generations WHERE owner_id=? AND project_id=? ORDER BY created_at DESC LIMIT 1").use {
        it.setObject(1,owner);it.setObject(2,project);it.executeQuery().use {r->if(r.next())r.plan() else null}
    }}
    suspend fun start(owner:UUID,project:Project,request:StartPlanRequest):StartedPlan= db {c->
        c.autoCommit=false
        try {
            c.createStatement().use {it.execute("SELECT pg_advisory_xact_lock(738291614)")}
            c.prepareStatement("SELECT * FROM plotus.plan_generations WHERE owner_id=? AND project_id=? AND request_id=?").use {
                it.setObject(1,owner);it.setObject(2,UUID.fromString(project.id));it.setObject(3,UUID.fromString(request.requestID));it.executeQuery().use {r->if(r.next()){val found=r.plan();c.commit();return@db StartedPlan(found,false)}}
            }
            c.createStatement().use {it.executeQuery("SELECT 1 FROM plotus.plan_generations WHERE status IN ('queued','running') LIMIT 1").use {r->if(r.next())throw ApiFailure(429,"ENGINE_BUSY","A plan is being generated. Try again shortly.")}}
            val id=UUID.randomUUID()
            c.prepareStatement("""INSERT INTO plotus.plan_generations(id,owner_id,project_id,request_id,source_revision,snapshot,status)
                SELECT ?,owner_id,id,?,revision,jsonb_build_object('plot',plot_setup,'floorPlan',floor_plan),'running'
                FROM plotus.projects WHERE owner_id=? AND id=? AND revision=? AND floor_plan->'plot'=plot_setup RETURNING *""").use {
                it.setObject(1,id);it.setObject(2,UUID.fromString(request.requestID));it.setObject(3,owner);it.setObject(4,UUID.fromString(project.id));it.setLong(5,request.revision)
                it.executeQuery().use {r->if(!r.next())throw ApiFailure(409,"PROJECT_CHANGED","The project changed. Reopen Floor plan before generating.");val result=r.plan();c.commit();StartedPlan(result,true)}
            }
        }catch(e:Exception){c.rollback();throw e}finally {c.autoCommit=true}
    }
    suspend fun finish(owner:UUID,project:UUID,plan:GeneratedFloorPlan)=db {c->c.prepareStatement("UPDATE plotus.plan_generations SET status=?,result=?::jsonb,updated_at=clock_timestamp() WHERE id=? AND owner_id=? AND project_id=? AND status='running'").use {
        it.setString(1,plan.status);it.setString(2,Json.encodeToString(plan));it.setObject(3,UUID.fromString(plan.id));it.setObject(4,owner);it.setObject(5,project);it.executeUpdate()
    };Unit}
    fun recover() {source.connection.use {c->c.createStatement().use {it.executeUpdate("UPDATE plotus.plan_generations SET status='failed',updated_at=clock_timestamp() WHERE status IN ('queued','running')")}}}
    private fun java.sql.ResultSet.plan():GeneratedFloorPlan = getString("result")?.let {Json.decodeFromString<GeneratedFloorPlan>(it)}
        ?: GeneratedFloorPlan(getObject("id").toString(),getString("status"),error=if(getString("status")=="failed")"Generation was interrupted. Try again." else null,createdAt=getTimestamp("created_at").toInstant().toString(),sourceRevision=getLong("source_revision"))
    private suspend fun <T> db(action:(java.sql.Connection)->T):T=withContext(Dispatchers.IO) {source.connection.use(action)}
}

class FloorPlans(private val jobs:PostgresPlans?=null,private val engine:PlanEngine?=null,private val scope:CoroutineScope=CoroutineScope(SupervisorJob()+Dispatchers.IO)):AutoCloseable {
    suspend fun preview(project:Project,setup:FloorPlanSetup):FloorPlanPreview {
        val errors=setup.errors(project.plotSetup,complete=true)
        if(errors.isNotEmpty())throw ApiFailure(400,"VALIDATION_FAILED","Check the floor-plan details.",errors)
        return connected().preview(project,setup)
    }
    suspend fun latest(owner:UUID,project:UUID)=jobs?.latest(owner,project)
    suspend fun history(owner:UUID,project:UUID,before:UUID?=null)=jobs?.history(owner,project,before) ?: PlanGenerations(emptyList())
    suspend fun get(owner:UUID,project:UUID,id:UUID,byRequest:Boolean=false)=jobs?.find(owner,project,id,byRequest)
        ?: throw ApiFailure(404,"PLAN_NOT_FOUND","This plan could not be found.")
    suspend fun start(owner:UUID,project:Project,request:StartPlanRequest):GeneratedFloorPlan {
        val store=jobs ?: throw unavailable()
        val requestID=try {UUID.fromString(request.requestID)}catch(_:IllegalArgumentException){throw ApiFailure(400,"INVALID_REQUEST_ID","Reopen Floor plan and try again.")}
        store.find(owner,UUID.fromString(project.id),requestID,true)?.let {return it}
        val planner=connected()
        if(request.revision!=project.revision)throw ApiFailure(409,"PROJECT_CHANGED","The project changed. Reopen Floor plan before generating.")
        val saved=project.floorPlan ?: throw ApiFailure(400,"FLOOR_PLAN_REQUIRED","Save the floor-plan requirements first.")
        if(saved.plot!=project.plotSetup)throw ApiFailure(409,"PLOT_CHANGED","The plot changed. Review and save setbacks before generating.")
        val errors=saved.setup.errors(project.plotSetup,true)
        if(errors.isNotEmpty())throw ApiFailure(400,"VALIDATION_FAILED","Check the floor-plan details.",errors)
        val findings=saved.setup.capabilityFindings()
        if(findings.isNotEmpty())throw ApiFailure(422,"PLAN_REQUIREMENTS_UNSUPPORTED",findings.first().message)
        val started=store.start(owner,project,request)
        val job=started.plan
        if(!started.created)return job
        scope.launch {
            // Include preflight and transport time beyond the engine's 240-second worker ceiling.
            val finished=try {job.copy(status="ready",drawings=withTimeout(280000) {planner.generate(project,saved.setup)})}
                catch(cancelled:CancellationException){if(cancelled !is TimeoutCancellationException)throw cancelled;job.copy(status="failed",error="Generation took too long. Try again.")}
                catch(failure:Exception){job.copy(status=if((failure as? ApiFailure)?.status==422)"blocked" else "failed",error=(failure as? ApiFailure)?.message ?: "Couldn’t generate this plan. Try again.")}
            store.finish(owner,UUID.fromString(project.id),finished)
        }
        return job
    }
    private fun connected()=engine ?: throw unavailable()
    private fun unavailable()=ApiFailure(503,"ENGINE_UNAVAILABLE","Plan generation isn’t connected. You can save the requirements and return later.")
    override fun close(){scope.cancel()}
}
