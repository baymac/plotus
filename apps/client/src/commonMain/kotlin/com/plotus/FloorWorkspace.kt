@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package com.plotus

import androidx.compose.runtime.*
import com.plotus.shared.*
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlin.uuid.Uuid

/** One retained draft per account and project; only verified API responses become saved state. */
class FloorWorkspace(
    private val scope:CoroutineScope,
    private val request:suspend (String,String,String?)->JsonElement,
    private val accepted:(Project)->Unit,
) {
    var project by mutableStateOf<Project?>(null);private set
    var draft by mutableStateOf(FloorPlanSetup());private set
    private var saved by mutableStateOf(FloorPlanSetup())
    var loading by mutableStateOf(true);private set
    var saving by mutableStateOf(false);private set
    var error by mutableStateOf<String?>(null);private set
    var validation by mutableStateOf<Map<String,String>>(emptyMap());private set
    var validationAttempt by mutableStateOf(0);private set
    var conflict by mutableStateOf(false);private set
    var preview by mutableStateOf<FloorPlanPreview?>(null);private set
    var previewError by mutableStateOf<String?>(null);private set
    var previewing by mutableStateOf(false);private set
    var generation by mutableStateOf<GeneratedFloorPlan?>(null);private set
    var starting by mutableStateOf(false);private set
    private var requestID:String?=null
    private var polling:Job?=null
    val dirty get()=draft!=saved
    val needsReview get()=project?.floorPlan?.let {it.plot!=project?.plotSetup}==true
    val generating get()=starting||generation?.status in listOf("queued","running")
    private val base get()="/v1/projects/${project!!.id}/floor-plan"
    suspend fun load(id:String) {
        if(saving||dirty)return
        loading=true;error=null
        try {
            val p=authJson.decodeFromJsonElement<Project>(request("GET","/v1/projects/$id",null))
            project=p;saved=p.floorPlan?.setup ?: FloorPlanSetup(units=p.plotSetup?.units ?: PlotUnits.FEET_INCHES);draft=saved;accepted(p)
            validation=emptyMap();conflict=false
            generation=authJson.decodeFromJsonElement<PlanGenerations>(request("GET","$base/generations",null)).plans.firstOrNull()
            generation?.takeIf {it.status in listOf("running","queued")}?.let {poll(it)}
        }catch(cancelled:CancellationException){throw cancelled}
        catch(failure:Exception){error=failure.message ?: "Couldn’t open Floor plan."}
        finally {loading=false}
    }
    fun change(next:FloorPlanSetup) {
        if(saving||generating||next==draft)return
        draft=next;preview=null;previewError=null;error=null
        requestID=null;validation=next.errors(project?.plotSetup)
    }
    fun discard(){draft=saved;validation=emptyMap();error=null;preview=null;requestID=null;conflict=false}
    suspend fun refreshPreview() {
        val p=project ?: return
        if(loading||generating||generation!=null||draft.errors(p.plotSetup,true).isNotEmpty())return
        val snapshot=draft;val revision=p.revision
        previewing=true
        try {
            val result=authJson.decodeFromJsonElement<FloorPlanPreview>(request("POST","$base/preview",authJson.encodeToString(PreviewFloorPlanRequest(snapshot,revision))))
            if(draft==snapshot&&project?.revision==revision){preview=result;previewError=null}
        }catch(cancelled:CancellationException){throw cancelled}
        catch(failure:Exception){if(draft==snapshot)previewError=failure.message ?: "Couldn’t preview the buildable area."}
        finally {previewing=false}
    }
    fun save(complete:Boolean=false,setbacks:Boolean=false,onSaved:()->Unit={}) {
        val current=project ?: return
        if(saving||generating)return
        validationAttempt++
        validation=if(setbacks&&!complete)draft.errors(current.plotSetup,true).filterKeys {it=="plot"||it=="floors"||it=="homes"||it.startsWith("setback")} else draft.errors(current.plotSetup,complete)
        if(validation.isNotEmpty())return
        if(!dirty&&current.floorPlan!=null&&current.floorPlan?.plot==current.plotSetup){onSaved();return}
        val snapshot=draft;saving=true;error=null
        scope.launch {
            try {
                val updated=try {authJson.decodeFromJsonElement<Project>(request("PUT",base,authJson.encodeToString(SaveFloorPlanRequest(snapshot,current.revision))))}
                catch(failure:Exception){
                    if(failure is CancellationException)throw failure
                    val confirmed=try {authJson.decodeFromJsonElement<Project>(request("GET","/v1/projects/${current.id}",null))}catch(cancelled:CancellationException){throw cancelled}catch(_:Exception){null}
                    if(confirmed?.floorPlan==SavedFloorPlan(snapshot,current.plotSetup!!))confirmed else throw failure
                }
                project=updated;saved=snapshot;conflict=false;accepted(updated);onSaved()
            }catch(cancelled:CancellationException){throw cancelled}
            catch(failure:Exception){conflict=(failure as? AuthFailure)?.code=="PROJECT_CHANGED";error=if(conflict)"Project changed on another device." else failure.message ?: "Couldn’t save Floor plan."}
            finally {saving=false}
        }
    }
    fun generate()=save(complete=true) {
        if(starting||generating)return@save
        starting=true;error=null
        val current=project!!
        val key=requestID ?: Uuid.random().toString().also {requestID=it}
        scope.launch {
            try {
                val job=try {authJson.decodeFromJsonElement<GeneratedFloorPlan>(request("POST","$base/generations",authJson.encodeToString(StartPlanRequest(current.revision,key))))}
                catch(failure:Exception){
                    if(failure is CancellationException)throw failure
                    try {authJson.decodeFromJsonElement<GeneratedFloorPlan>(request("GET","$base/generations/$key?by=request",null))}catch(cancelled:CancellationException){throw cancelled}catch(_:Exception){throw failure}
                }
                generation=job;poll(job)
            }catch(cancelled:CancellationException){throw cancelled}
            catch(failure:Exception){conflict=(failure as? AuthFailure)?.code in listOf("PROJECT_CHANGED","PLOT_CHANGED");error=failure.message ?: "Couldn’t start generation."}
            finally {starting=false}
        }
    }
    fun newAttempt(){requestID=null;generation=null;error=null}
    private fun poll(job:GeneratedFloorPlan) {
        polling?.cancel()
        if(job.status !in listOf("running","queued"))return
        polling=scope.launch {
            try {while(isActive) {
                delay(2000)
                val next=authJson.decodeFromJsonElement<GeneratedFloorPlan>(request("GET","$base/generations/${job.id}",null));generation=next;error=null
                if(next.status !in listOf("running","queued"))break
            }}catch(cancelled:CancellationException){throw cancelled}
            catch(failure:Exception){error="Couldn’t check generation. Reopen Floor plan to continue."}
        }
    }
}

internal fun liveFloorWorkspace(auth:AuthSession,scope:CoroutineScope,owner:String,accepted:(Project)->Unit)=FloorWorkspace(scope,{method,path,body->
    if(auth.user?.id!=owner)throw AuthFailure(401,"Sign in to open Floor plan.")
    val response=auth.projectRequest(method,path,body)
    if(auth.user?.id!=owner)throw AuthFailure(401,"Your account changed. Reopen the project.")
    response
},accepted)
