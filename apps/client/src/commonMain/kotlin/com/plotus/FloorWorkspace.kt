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
    var history by mutableStateOf<List<GeneratedFloorPlan>>(emptyList());private set
    var historyLoading by mutableStateOf(false);private set
    var historyError by mutableStateOf<String?>(null);private set
    var nextHistoryBefore by mutableStateOf<String?>(null);private set
    var viewingPlan by mutableStateOf<GeneratedFloorPlan?>(null);private set
    var viewLoading by mutableStateOf(false);private set
    var viewError by mutableStateOf<String?>(null);private set
    var exporting by mutableStateOf(false);private set
    var exportError by mutableStateOf<String?>(null);private set
    var starting by mutableStateOf(false);private set
    private var requestID:String?=null
    private var polling:Job?=null
    private val areas=mutableMapOf<BuildableAreaKey,SavedBuildableArea>()
    val footprintKey get()=project?.plotSetup?.let {draft.buildableKey(it)}
    val dirty get()=draft!=saved.singleResidence()
    val needsReview get()=project?.floorPlan?.let {it.plot!=project?.plotSetup||it.setup!=it.setup.singleResidence()}==true
    val generating get()=starting||generation?.status in listOf("queued","running")
    private val base get()="/v1/projects/${project!!.id}/floor-plan"
    suspend fun load(id:String) {
        if(saving||dirty)return
        loading=true;error=null
        try {
            val p=authJson.decodeFromJsonElement<Project>(request("GET","/v1/projects/$id",null))
            project=p;saved=p.floorPlan?.setup ?: FloorPlanSetup(units=p.plotSetup?.units ?: PlotUnits.FEET_INCHES);draft=saved.singleResidence();accepted(p)
            p.buildableArea?.let {areas[it.key]=it};restoreArea()
            validation=emptyMap();conflict=false
            loadHistory()
            generation=history.firstOrNull {it.status in listOf("running","queued")}
            generation?.takeIf {it.status in listOf("running","queued")}?.let {poll(it)}
        }catch(cancelled:CancellationException){throw cancelled}
        catch(failure:Exception){error=failure.message ?: "Couldn’t open Floor plan."}
        finally {loading=false}
    }
    fun change(next:FloorPlanSetup) {
        val updated=next.singleResidence()
        if(saving||generating||updated==draft)return
        val previousKey=footprintKey
        draft=updated;error=null
        if(previousKey!=footprintKey)restoreArea()
        requestID=null;validation=updated.errors(project?.plotSetup)
    }
    fun discard(){draft=saved.singleResidence();validation=emptyMap();error=null;restoreArea();requestID=null;conflict=false}
    private fun restoreArea() {
        preview=footprintKey?.let {areas[it]}?.asPreview();previewError=null
    }
    private fun SavedBuildableArea.asPreview()=FloorPlanPreview(envelope,
        if(envelope.empty)listOf(PlanFinding("These setbacks leave no buildable area.","setbacks")) else emptyList())
    suspend fun refreshPreview() {
        val p=project ?: return
        val key=footprintKey ?: return
        areas[key]?.let {preview=it.asPreview();previewError=null;return}
        if(loading||generating||draft.geometrySetup().errors(p.plotSetup,true).isNotEmpty())return
        val snapshot=draft;val revision=p.revision
        previewing=true
        try {
            val result=authJson.decodeFromJsonElement<SavedBuildableArea>(request("POST","$base/envelope",authJson.encodeToString(PreviewFloorPlanRequest(snapshot,revision))))
            if(result.key==key)areas[key]=result
            if(footprintKey==key&&result.key==key){preview=result.asPreview();previewError=null}
        }catch(cancelled:CancellationException){throw cancelled}
        catch(failure:Exception){if(footprintKey==key)previewError=failure.message ?: "Couldn’t preview the buildable area."}
        finally {previewing=false}
    }
    fun save(complete:Boolean=false,setbacks:Boolean=false,onSaved:()->Unit={}) {
        val current=project ?: return
        if(saving||generating)return
        validationAttempt++
        validation=if(setbacks&&!complete)draft.errors(current.plotSetup,true).filterKeys {it=="plot"||it=="floors"||it=="homes"||it.startsWith("setback")} else draft.errors(current.plotSetup,complete)
        if(validation.isNotEmpty())return
        if(!dirty&&current.floorPlan?.setup==draft&&current.floorPlan?.plot==current.plotSetup){onSaved();return}
        val snapshot=draft;saving=true;error=null
        scope.launch {
            try {
                val updated=try {authJson.decodeFromJsonElement<Project>(request("PUT",base,authJson.encodeToString(SaveFloorPlanRequest(snapshot,current.revision))))}
                catch(failure:Exception){
                    if(failure is CancellationException)throw failure
                    val confirmed=try {authJson.decodeFromJsonElement<Project>(request("GET","/v1/projects/${current.id}",null))}catch(cancelled:CancellationException){throw cancelled}catch(_:Exception){null}
                    if(confirmed?.floorPlan==SavedFloorPlan(snapshot,current.plotSetup!!))confirmed else throw failure
                }
                project=updated;saved=snapshot;conflict=false
                updated.buildableArea?.let {areas[it.key]=it};restoreArea();accepted(updated);onSaved()
            }catch(cancelled:CancellationException){throw cancelled}
            catch(failure:Exception){conflict=(failure as? AuthFailure)?.code=="PROJECT_CHANGED";error=if(conflict)"Project changed on another device." else failure.message ?: "Couldn’t save Floor plan."}
            finally {saving=false}
        }
    }
    fun generate(onStarted:(GeneratedFloorPlan)->Unit={})=save(complete=true) {
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
                generation=job;rememberPlan(job);poll(job);onStarted(job)
            }catch(cancelled:CancellationException){throw cancelled}
            catch(failure:Exception){conflict=(failure as? AuthFailure)?.code in listOf("PROJECT_CHANGED","PLOT_CHANGED");error=failure.message ?: "Couldn’t start generation."}
            finally {starting=false}
        }
    }
    fun newAttempt(){requestID=null;generation=null;error=null}
    fun exportPlan(planID:String,drawingID:String,format:String) {
        if(exporting||project==null)return
        exporting=true;exportError=null
        scope.launch {
            try {
                val file=authJson.decodeFromJsonElement<PlanExport>(request("GET","$base/generations/$planID/drawings/$drawingID/export/$format",null))
                savePlanExport(file)
            }catch(cancelled:CancellationException){throw cancelled}
            catch(_:AuthCancelled){}
            catch(failure:Exception){exportError=failure.message ?: "Couldn’t export this plan."}
            finally {exporting=false}
        }
    }
    fun dismissExportError(){exportError=null}
    suspend fun loadHistory(more:Boolean=false) {
        if(project==null||historyLoading)return
        val before=if(more)nextHistoryBefore ?: return else null
        historyLoading=true;historyError=null
        try {
            val page=authJson.decodeFromJsonElement<PlanGenerations>(request("GET","$base/generations?summaries=true"+(before?.let {"&before=$it"} ?: ""),null))
            history=(if(more)history+page.plans else page.plans).distinctBy {it.id};nextHistoryBefore=page.nextBefore
        }catch(cancelled:CancellationException){throw cancelled}
        catch(failure:Exception){historyError=failure.message ?: "Couldn’t load history."}
        finally {historyLoading=false}
    }
    suspend fun openPlan(id:String) {
        viewingPlan=null;viewError=null;viewLoading=true
        try {
            val plan=authJson.decodeFromJsonElement<GeneratedFloorPlan>(request("GET","$base/generations/$id",null))
            viewingPlan=plan
            if(plan.status in listOf("running","queued")){generation=plan;poll(plan)}
        }catch(cancelled:CancellationException){throw cancelled}
        catch(failure:Exception){viewError=failure.message ?: "Couldn’t open this plan."}
        finally {viewLoading=false}
    }
    private fun rememberPlan(plan:GeneratedFloorPlan) {
        val summary=plan.copy(drawings=emptyList())
        history=if(history.any {it.id==plan.id})history.map {if(it.id==plan.id)summary else it} else listOf(summary)+history
        if(viewingPlan?.id==plan.id)viewingPlan=plan
    }
    private fun poll(job:GeneratedFloorPlan) {
        polling?.cancel()
        if(job.status !in listOf("running","queued"))return
        polling=scope.launch {
            try {while(isActive) {
                delay(2000)
                val next=authJson.decodeFromJsonElement<GeneratedFloorPlan>(request("GET","$base/generations/${job.id}",null));generation=next;rememberPlan(next);error=null
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
