@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package com.plotus

import androidx.compose.runtime.*
import com.plotus.shared.*
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlin.uuid.Uuid

/** Account-bound request closure is shared with FloorWorkspace; no parallel auth state. */
class PlanReviewWorkspace(
    private val scope:CoroutineScope,
    private val request:suspend (String,String,String?)->JsonElement,
    private val applied:(GeneratedFloorPlan)->Unit,
) {
    private var projectID:String?=null
    var planID by mutableStateOf<String?>(null);private set
    private val reports=mutableStateMapOf<String,PlanReviewReport>()
    private val explanations=mutableStateMapOf<String,AIPlanReview>()
    val report get()=reports[planID]
    val ai get()=explanations[planID]
    var loading by mutableStateOf(false);private set
    var aiLoading by mutableStateOf(false);private set
    var error by mutableStateOf<String?>(null);private set
    var aiError by mutableStateOf<String?>(null);private set
    var optimization by mutableStateOf<PlanOptimization?>(null);private set
    var starting by mutableStateOf(false);private set
    var cancelling by mutableStateOf(false);private set
    var applying by mutableStateOf(false);private set
    var selectedOperations by mutableStateOf<Set<ReviewOperation>>(emptySet());private set
    private var requestID:String?=null
    private var intent:String?=null
    private var goalsInitialized=false
    private var epoch=0
    private var polling:Job?=null
    private var reviewJob:Job?=null
    private var explanationJob:Job?=null
    val busy get()=starting||cancelling||applying
    private val base get()="/v1/projects/"+projectID+"/floor-plan"
    fun attachProject(id:String) {
        if(projectID==id)return
        projectID=id;epoch++;planID=null;reports.clear();explanations.clear();loading=false;aiLoading=false;goalsInitialized=false
        optimization=null;polling?.cancel();reviewJob?.cancel();explanationJob?.cancel()
        scope.launch {
            try {
                val found=authJson.decodeFromJsonElement<CurrentOptimization>(request("GET",base+"/optimizations",null)).optimization
                if(projectID==id){optimization=found;found?.let {openOptimization(it.id)}}
            }catch(cancelled:CancellationException){throw cancelled}catch(_:Exception){/* Review availability never blocks the existing editor. */}
        }
    }
    fun open(id:String,force:Boolean=false) {
        if(projectID==null)return
        if(planID!=id){
            reviewJob?.cancel();explanationJob?.cancel();loading=false;aiLoading=false
            planID=id;epoch++;requestID=null;intent=null;selectedOperations=emptySet();goalsInitialized=false;error=null;aiError=null
        }
        if(!force&&reports[id]!=null){if(!goalsInitialized)defaults(reports.getValue(id));return}
        if(loading&&reviewJob?.isActive==true&&!force)return
        reviewJob?.cancel();loading=true;error=null
        val current=epoch
        reviewJob=scope.launch {
            try {
                val value=authJson.decodeFromJsonElement<PlanReviewReport>(request("GET",base+"/generations/"+id+"/review",null))
                if(epoch==current&&planID==id){reports[id]=value;if(!goalsInitialized)defaults(value)}
            }catch(cancelled:CancellationException){throw cancelled}
            catch(failure:Exception){if(epoch==current)error=failure.message ?: "Couldn’t review this plan."}
            finally {if(epoch==current)loading=false}
        }
    }
    private fun defaults(value:PlanReviewReport) {
        goalsInitialized=true
        selectedOperations=value.findings.mapNotNull {it.operation}.toSet().ifEmpty {
            setOf(ReviewOperation.CONSOLIDATE_OPEN_SPACE,ReviewOperation.SHORTEN_PASSAGE,ReviewOperation.BALANCE_ROOM_PROPORTIONS)
        }
    }
    fun explain(force:Boolean=false) {
        val id=planID ?: return
        if(aiLoading||!force&&explanations[id]?.status in listOf("ready","not_needed"))return
        aiLoading=true;aiError=null
        val current=epoch
        explanationJob=scope.launch {
            try {
                val value=authJson.decodeFromJsonElement<AIPlanReview>(request("POST",base+"/generations/"+id+"/ai-review","{}"))
                if(epoch==current&&planID==id)explanations[id]=value
            }catch(cancelled:CancellationException){throw cancelled}
            catch(failure:Exception){if(epoch==current)aiError=failure.message ?: "AI recommendations are unavailable."}
            finally {if(epoch==current)aiLoading=false}
        }
    }
    fun choose(operation:ReviewOperation) {
        goalsInitialized=true
        selectedOperations=if(operation in selectedOperations)selectedOperations-operation else selectedOperations+operation
        requestID=null;intent=null
    }
    fun prepare(operation:ReviewOperation?=null) {
        operation?.let {selectedOperations=setOf(it);goalsInitialized=true}
        requestID=null;intent=null;error=null
    }
    fun start(onStarted:(PlanOptimization)->Unit) {
        val id=planID ?: return
        if(busy||report?.canOptimize!=true||selectedOperations.isEmpty())return
        if(optimization?.running==true){error="An optimization is already running. Open it from History.";return}
        val key=id+"/"+selectedOperations.sortedBy {it.name}.joinToString {it.name}
        if(intent!=key){requestID=null;intent=key}
        val token=requestID ?: Uuid.random().toString().also {requestID=it}
        val body=StartOptimizationRequest(token,operations=selectedOperations.toList())
        starting=true;error=null
        scope.launch {
            try {
                val job=try {authJson.decodeFromJsonElement<PlanOptimization>(request("POST",base+"/generations/"+id+"/optimizations",authJson.encodeToString(body)))}
                catch(failure:Exception) {
                    if(failure is CancellationException)throw failure
                    try {authJson.decodeFromJsonElement<PlanOptimization>(request("GET",base+"/optimizations/"+token+"?by=request",null))}
                    catch(cancelled:CancellationException){throw cancelled}catch(_:Exception){throw failure}
                }
                optimization=job;poll(job.id);onStarted(job)
            }catch(cancelled:CancellationException){throw cancelled}
            catch(failure:Exception){error=failure.message ?: "Couldn’t start optimization."}
            finally {starting=false}
        }
    }
    fun openOptimization(id:String) {
        if(projectID==null||optimization?.id==id&&polling?.isActive==true)return
        poll(id)
    }
    private fun poll(id:String) {
        polling?.cancel()
        val project=projectID
        polling=scope.launch {
            var failures=0
            while(isActive&&projectID==project) {
                try {
                    val summary=authJson.decodeFromJsonElement<PlanOptimization>(request("GET",base+"/optimizations/"+id+"?summary=true",null))
                    val old=optimization?.takeIf {it.id==id}
                    val value=if(summary.revisionHash!=null&&old?.revision?.review?.sourceHash!=summary.revisionHash)
                        authJson.decodeFromJsonElement<PlanOptimization>(request("GET",base+"/optimizations/"+id,null))
                        else summary.copy(revision=old?.revision)
                    if(projectID!=project)return@launch
                    optimization=value;failures=0
                    if(!value.running)return@launch
                }catch(cancelled:CancellationException){throw cancelled}
                catch(failure:Exception){
                    if(++failures>=3){error=failure.message ?: "Couldn’t check optimization. Try again.";return@launch}
                }
                delay(1500)
            }
        }
    }
    fun cancel(onCancelled:()->Unit={}) {
        val job=optimization ?: return
        if(cancelling||applying)return
        cancelling=true;error=null
        scope.launch {
            try {
                val value=authJson.decodeFromJsonElement<PlanOptimization>(request("POST",base+"/optimizations/"+job.id+"/cancel","{}"))
                optimization=value;polling?.cancel();onCancelled()
            }catch(cancelled:CancellationException){throw cancelled}
            catch(failure:Exception){error=failure.message ?: "Couldn’t cancel optimization. Try again.";openOptimization(job.id)}
            finally {cancelling=false}
        }
    }
    fun keepCurrent(onKept:()->Unit) {
        val job=optimization ?: return
        if(busy)return
        cancelling=true;error=null
        scope.launch {
            try {
                optimization=authJson.decodeFromJsonElement<PlanOptimization>(request("POST",base+"/optimizations/"+job.id+"/dismiss","{}"))
                polling?.cancel();requestID=null;intent=null;onKept()
            }catch(cancelled:CancellationException){throw cancelled}
            catch(failure:Exception){error=failure.message ?: "Couldn’t stop the remaining attempts."}
            finally {cancelling=false}
        }
    }
    fun apply(expectedHash:String,onApplied:(GeneratedFloorPlan)->Unit) {
        val job=optimization ?: return
        if(busy)return
        applying=true;error=null
        scope.launch {
            try {
                val value=try {authJson.decodeFromJsonElement<GeneratedFloorPlan>(request("POST",base+"/optimizations/"+job.id+"/apply",authJson.encodeToString(ApplyOptimizationRequest(expectedHash))))}
                catch(failure:Exception) {
                    if(failure is CancellationException)throw failure
                    val confirmed=authJson.decodeFromJsonElement<PlanOptimization>(request("GET",base+"/optimizations/"+job.id+"?summary=true",null))
                    val saved=confirmed.appliedPlanID ?: throw failure
                    authJson.decodeFromJsonElement<GeneratedFloorPlan>(request("GET",base+"/generations/"+saved,null))
                }
                reports[value.id]=value.drawings.firstNotNullOfOrNull {it.review} ?: job.revision!!.review
                optimization=job.copy(status="applied",appliedPlanID=value.id,revision=null)
                polling?.cancel();requestID=null;intent=null;applied(value);onApplied(value)
            }catch(cancelled:CancellationException){throw cancelled}
            catch(failure:Exception){error=failure.message ?: "Couldn’t save this revision.";openOptimization(job.id)}
            finally {applying=false}
        }
    }
    fun dismissError(){error=null}
}
