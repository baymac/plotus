package com.plotus.server

import com.plotus.shared.*
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore

/** Durable revisions remain separate from their immutable base until explicitly accepted. */
class PlanReviews(private val jobs:PostgresPlans?=null,private val store:PostgresPlanReviews?=null,
    private val engine:PlanEngine?=null,private val ai:ArchitectAI?=MetaArchitectAI.configured(),
    private val scope:CoroutineScope=CoroutineScope(SupervisorJob()+Dispatchers.IO)):AutoCloseable {
    private val running=ConcurrentHashMap<String,Job>()
    private val aiSlots=Semaphore(2)
    private val aiRunning=ConcurrentHashMap.newKeySet<String>()
    private val rulesVersion="plotus-architect-review.2"
    private fun storage()=store ?: throw ApiFailure(503,"REVIEW_UNAVAILABLE","Plan review is temporarily unavailable.")
    private fun planner()=engine ?: throw ApiFailure(503,"REVIEW_UNAVAILABLE","Plan review is temporarily unavailable.")
    private suspend fun base(owner:UUID,project:UUID,id:UUID):Pair<GeneratedFloorPlan,SavedFloorPlan> {
        val plan=jobs?.find(owner,project,id) ?: throw ApiFailure(404,"PLAN_NOT_FOUND","This plan could not be found.")
        if(plan.status!="ready"||plan.drawings.isEmpty())throw ApiFailure(409,"PLAN_NOT_READY","A review needs a complete generated plan.")
        val brief=jobs.snapshot(owner,project,id) ?: throw ApiFailure(409,"REVIEW_UNAVAILABLE","The saved requirements for this plan are unavailable.")
        return plan to brief
    }
    suspend fun review(owner:UUID,project:Project,id:UUID):PlanReviewReport {
        val projectID=UUID.fromString(project.id)
        val (plan,brief)=base(owner,projectID,id)
        val cached=storage().review(owner,projectID,id)?.takeIf {it.rulesVersion==rulesVersion&&it.isValid()}
            ?: plan.drawings.firstNotNullOfOrNull {it.review}?.takeIf {it.rulesVersion==rulesVersion&&it.isValid()}
        val result=cached ?: planner().review(project.copy(plotSetup=brief.plot,floorPlan=brief),brief.setup,plan.drawings)
        if(!result.isValid())throw ApiFailure(502,"REVIEW_INVALID","The measured review couldn’t be opened.")
        storage().saveReview(owner,projectID,id,result)
        return result
    }
    suspend fun explain(owner:UUID,project:Project,id:UUID):AIPlanReview {
        val report=review(owner,project,id)
        val projectID=UUID.fromString(project.id)
        val hash=report.sourceHash ?: return AIPlanReview("unavailable","The measured review is still available.")
        storage().ai(owner,projectID,id,hash)?.takeIf {it.status=="ready"&&it.recommendations.all {r->r.nextStep.isNotBlank()}}?.let {return it}
        if(report.findings.isEmpty())return AIPlanReview("not_needed","No measured recommendations need an AI explanation.")
        val provider=ai ?: return AIPlanReview("unavailable","The measured review and solver optimization are still available.")
        val key=owner.toString()+"/"+id+"/"+hash
        if(!aiRunning.add(key))throw ApiFailure(429,"AI_REVIEW_BUSY","AI recommendations are being prepared.")
        if(!aiSlots.tryAcquire()){aiRunning.remove(key);throw ApiFailure(429,"AI_REVIEW_BUSY","AI recommendations are busy. Try again shortly.")}
        try {
            val result=provider.explain(report)
            // Provider implementations, including future adapters, cannot change
            // evidence or introduce an unknown finding through this boundary.
            if(result.status!="ready"||result.recommendations.any {r->report.findings.none {it.id==r.findingID}})
                throw ApiFailure(502,"AI_REVIEW_INVALID","AI recommendations are unavailable.")
            storage().saveAI(owner,projectID,id,hash,result)
            return result
        }catch(cancelled:CancellationException){throw cancelled}
        catch(_:Exception){return AIPlanReview("unavailable","The measured review and solver optimization are still available.")}
        finally {aiSlots.release();aiRunning.remove(key)}
    }
    suspend fun current(owner:UUID,project:UUID)=CurrentOptimization(storage().current(owner,project)?.copy(revision=null))
    suspend fun byRequest(owner:UUID,project:UUID,id:UUID):PlanOptimization =
        storage().find(owner,project,id,true)?.copy(revision=null) ?: throw missing()
    suspend fun get(owner:UUID,project:UUID,id:UUID,summary:Boolean=false):PlanOptimization {
        var job=storage().find(owner,project,id) ?: throw missing()
        // A lost final DB write must not leave a forever-running global lock.
        if(job.running&&!running.containsKey(id.toString())) {
            try {
                val progress=planner().optimizationStatus(id.toString(),job.revisionHash)
                storage().progress(owner,project,id,progress)
                if(progress.status in listOf("ready","no_improvement")){
                    val checked=storage().find(owner,project,id) ?: throw missing()
                    storage().finish(owner,project,id,EngineOptimizationResult(progress.status,progress.requested,progress.checked,progress.valid,progress.distinct,progress.revision ?: checked.revision))
                }
            }catch(cancelled:CancellationException){throw cancelled}
            catch(_:Exception){storage().fail(owner,project,id,"Optimization was interrupted. The saved plan is unchanged.")}
            job=storage().find(owner,project,id) ?: throw missing()
        }
        return if(summary)job.copy(revision=null) else job
    }
    suspend fun start(owner:UUID,project:Project,baseID:UUID,request:StartOptimizationRequest):PlanOptimization {
        val projectID=UUID.fromString(project.id)
        val requestID=try {UUID.fromString(request.requestID)}catch(_:IllegalArgumentException){throw ApiFailure(400,"INVALID_REQUEST_ID","Reopen Optimize and try again.")}
        storage().find(owner,projectID,requestID,true)?.let {return it}
        if(request.profile!="balanced_v1"||request.maxCandidates !in 1..10||request.operations.size>8||request.operations.distinct().size!=request.operations.size)
            throw ApiFailure(400,"INVALID_OPTIMIZATION","Choose an improvement and between one and ten attempts.")
        val (original,brief)=base(owner,projectID,baseID)
        val report=review(owner,project,baseID)
        if(!report.canOptimize)throw ApiFailure(409,"OPTIMIZATION_UNAVAILABLE","Generate a new plan with measured geometry before optimizing.")
        val planner=planner()
        val chosen=request.operations.ifEmpty {report.priorities.mapNotNull {it.operation}.take(1)}
        if(chosen.isEmpty())throw ApiFailure(409,"NO_IMPROVEMENT_GOAL","No measured improvement is flagged for this plan.")
        val selected=request.copy(operations=chosen)
        val started=storage().start(owner,projectID,baseID,selected,report)
        if(!started.created)return started.job
        val job=started.job
        val savedProject=project.copy(plotSetup=brief.plot,floorPlan=brief)
        val work=scope.launch(start=CoroutineStart.LAZY) {
            var knownRevision:String?=null
            val watcher=launch {
                while(isActive) {
                    delay(1500)
                    try {
                        val progress=planner.optimizationStatus(job.id,knownRevision)
                        storage().progress(owner,projectID,UUID.fromString(job.id),progress)
                        if(progress.revision!=null)knownRevision=progress.revisionHash
                    }catch(cancelled:CancellationException){throw cancelled}catch(_:Exception){/* A transient poll never invalidates the saved plan. */}
                }
            }
            try {
                val result=withTimeout(280000){planner.optimize(savedProject,brief.setup,original.drawings,selected,job.id)}
                storage().finish(owner,projectID,UUID.fromString(job.id),result)
            }catch(cancelled:CancellationException){
                if(cancelled is TimeoutCancellationException)storage().fail(owner,projectID,UUID.fromString(job.id),"Optimization reached its time limit. The saved plan is unchanged.")
                else throw cancelled
            }catch(failure:Exception){
                storage().fail(owner,projectID,UUID.fromString(job.id),(failure as? ApiFailure)?.message ?: "Couldn’t complete optimization. The saved plan is unchanged.")
            }finally {watcher.cancelAndJoin();running.remove(job.id,currentCoroutineContext()[Job])}
        }
        running[job.id]=work;work.start()
        return job
    }
    suspend fun cancel(owner:UUID,project:UUID,id:UUID):PlanOptimization {
        val job=storage().markCancelling(owner,project,id)
        if(job.status!="cancelling")return job
        running.remove(id.toString())?.cancel()
        planner().cancel(id.toString())
        try {storage().progress(owner,project,id,planner().optimizationStatus(id.toString(),job.revisionHash))}
        catch(cancelled:CancellationException){throw cancelled}catch(_:Exception){}
        return storage().cancelled(owner,project,id)
    }
    suspend fun dismiss(owner:UUID,project:UUID,id:UUID):PlanOptimization {
        val job=get(owner,project,id)
        if(job.running)cancel(owner,project,id)
        return storage().dismiss(owner,project,id)
    }
    suspend fun apply(owner:UUID,project:UUID,id:UUID,request:ApplyOptimizationRequest):UUID {
        if(!request.revisionHash.matches(Regex("[a-f0-9]{64}")))throw ApiFailure(400,"INVALID_REVISION","Reopen the checked revision and try again.")
        val job=get(owner,project,id)
        job.appliedPlanID?.let {return UUID.fromString(it)}
        if(job.revisionHash!=request.revisionHash)throw ApiFailure(409,"REVISION_CHANGED","A newer checked revision is ready. Review it before applying.")
        if(job.running)cancel(owner,project,id)
        return storage().apply(owner,project,id,request.revisionHash)
    }
    private fun missing()=ApiFailure(404,"OPTIMIZATION_NOT_FOUND","This optimization could not be found.")
    override fun close(){scope.cancel()}
}
