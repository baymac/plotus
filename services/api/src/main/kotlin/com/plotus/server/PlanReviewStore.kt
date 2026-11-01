package com.plotus.server

import com.plotus.shared.*
import java.sql.Connection
import java.sql.ResultSet
import java.time.Instant
import java.time.Duration
import java.util.UUID
import javax.sql.DataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal data class OptimizationContext(val job:PlanOptimization,val brief:SavedFloorPlan,val operations:List<ReviewOperation>)
internal data class StartedOptimization(val job:PlanOptimization,val created:Boolean)

/** Every read and mutation is scoped to the verified owner, project and generation. */
class PostgresPlanReviews(private val source:DataSource) {
    suspend fun review(owner:UUID,project:UUID,plan:UUID):PlanReviewReport?=db {c->
        c.prepareStatement("SELECT report FROM plotus.plan_reviews WHERE owner_id=? AND project_id=? AND generation_id=?").use {
            it.setObject(1,owner);it.setObject(2,project);it.setObject(3,plan)
            it.executeQuery().use {r->if(r.next())Json.decodeFromString<PlanReviewReport>(r.getString(1)) else null}
        }
    }
    suspend fun saveReview(owner:UUID,project:UUID,plan:UUID,report:PlanReviewReport)=db {c->
        c.prepareStatement("""INSERT INTO plotus.plan_reviews(generation_id,owner_id,project_id,source_hash,report)
            SELECT id,owner_id,project_id,?,?::jsonb FROM plotus.plan_generations
            WHERE owner_id=? AND project_id=? AND id=? AND status='ready'
            ON CONFLICT(generation_id) DO UPDATE SET source_hash=EXCLUDED.source_hash,report=EXCLUDED.report,
            ai_review=CASE WHEN plan_reviews.source_hash=EXCLUDED.source_hash AND plan_reviews.report->>'rulesVersion'=EXCLUDED.report->>'rulesVersion'
                THEN plan_reviews.ai_review ELSE NULL END,updated_at=clock_timestamp()""").use {
            it.setString(1,report.sourceHash ?: "");it.setString(2,Json.encodeToString(report))
            it.setObject(3,owner);it.setObject(4,project);it.setObject(5,plan)
            if(it.executeUpdate()!=1)throw missingPlan()
        };Unit
    }
    suspend fun ai(owner:UUID,project:UUID,plan:UUID,hash:String):AIPlanReview?=db {c->
        c.prepareStatement("SELECT ai_review FROM plotus.plan_reviews WHERE owner_id=? AND project_id=? AND generation_id=? AND source_hash=? AND ai_review IS NOT NULL").use {
            it.setObject(1,owner);it.setObject(2,project);it.setObject(3,plan);it.setString(4,hash)
            it.executeQuery().use {r->if(r.next())Json.decodeFromString<AIPlanReview>(r.getString(1)) else null}
        }
    }
    suspend fun saveAI(owner:UUID,project:UUID,plan:UUID,hash:String,review:AIPlanReview)=db {c->
        c.prepareStatement("UPDATE plotus.plan_reviews SET ai_review=?::jsonb,updated_at=clock_timestamp() WHERE owner_id=? AND project_id=? AND generation_id=? AND source_hash=?").use {
            it.setString(1,Json.encodeToString(review));it.setObject(2,owner);it.setObject(3,project);it.setObject(4,plan);it.setString(5,hash);it.executeUpdate()
        };Unit
    }
    suspend fun find(owner:UUID,project:UUID,id:UUID,byRequest:Boolean=false):PlanOptimization?=db {c->find(c,owner,project,id,byRequest)}
    private fun find(c:Connection,owner:UUID,project:UUID,id:UUID,byRequest:Boolean=false,lock:Boolean=false):PlanOptimization? {
        val column=if(byRequest)"request_id" else "id"
        val suffix=if(lock)" FOR UPDATE" else ""
        return c.prepareStatement("SELECT * FROM plotus.plan_optimizations WHERE owner_id=? AND project_id=? AND "+column+"=?"+suffix).use {
            it.setObject(1,owner);it.setObject(2,project);it.setObject(3,id)
            it.executeQuery().use {r->if(r.next())r.optimization() else null}
        }
    }
    suspend fun current(owner:UUID,project:UUID):PlanOptimization?=db {c->
        c.prepareStatement("""SELECT * FROM plotus.plan_optimizations WHERE owner_id=? AND project_id=? AND
            (status IN ('queued','running','cancelling','ready') OR
                (status='cancelled' AND result->'revision' IS NOT NULL AND result->'revision'<>'null'::jsonb))
            ORDER BY created_at DESC LIMIT 1""").use {
            it.setObject(1,owner);it.setObject(2,project);it.executeQuery().use {r->if(r.next())r.optimization() else null}
        }
    }
    internal suspend fun context(owner:UUID,project:UUID,id:UUID):OptimizationContext=db {c->
        c.prepareStatement("SELECT *,snapshot->'floorPlan' AS brief FROM plotus.plan_optimizations WHERE owner_id=? AND project_id=? AND id=?").use {
            it.setObject(1,owner);it.setObject(2,project);it.setObject(3,id)
            it.executeQuery().use {r->if(!r.next())throw missingOptimization()
                OptimizationContext(r.optimization(),Json.decodeFromString(r.getString("brief")),Json.decodeFromString(r.getString("operations")))}
        }
    }
    internal suspend fun start(owner:UUID,project:UUID,base:UUID,request:StartOptimizationRequest,report:PlanReviewReport):StartedOptimization=transaction {c->
        globalLock(c)
        find(c,owner,project,UUID.fromString(request.requestID),true)?.let {return@transaction StartedOptimization(it,false)}
        c.createStatement().use {s->s.executeQuery("""SELECT 1 FROM
            (SELECT status FROM plotus.plan_generations UNION ALL SELECT status FROM plotus.plan_optimizations) jobs
            WHERE status IN ('queued','running','cancelling') LIMIT 1""").use {r->
            if(r.next())throw ApiFailure(429,"ENGINE_BUSY","A plan is being generated or optimized. Try again shortly.")
        }}
        val id=UUID.randomUUID()
        val job=PlanOptimization(id.toString(),base.toString(),"running","",requested=request.maxCandidates,baseReview=report)
        c.prepareStatement("""INSERT INTO plotus.plan_optimizations(id,owner_id,project_id,base_generation_id,
            request_id,source_revision,snapshot,operations,status,result)
            SELECT ?,owner_id,project_id,id,?,source_revision,snapshot,?::jsonb,'running',?::jsonb
            FROM plotus.plan_generations WHERE owner_id=? AND project_id=? AND id=? AND status='ready' RETURNING *""").use {
            it.setObject(1,id);it.setObject(2,UUID.fromString(request.requestID));it.setString(3,Json.encodeToString(request.operations))
            it.setString(4,Json.encodeToString(job));it.setObject(5,owner);it.setObject(6,project);it.setObject(7,base)
            it.executeQuery().use {r->if(!r.next())throw missingPlan();StartedOptimization(r.optimization(),true)}
        }
    }
    internal suspend fun progress(owner:UUID,project:UUID,id:UUID,next:EngineOptimizationProgress)=transaction {c->
        val old=find(c,owner,project,id,lock=true) ?: return@transaction
        if(!old.running)return@transaction
        if(next.requested!=old.requested||next.checked !in 0..old.requested||next.valid !in 0..next.checked||next.distinct !in 0..next.valid)
            throw ApiFailure(502,"OPTIMIZATION_INVALID","Optimization progress couldn’t be read.")
        if(next.checked<old.checked)return@transaction
        if(next.checked==old.checked&&next.valid==old.valid&&next.distinct==old.distinct&&
            (next.revisionHash==null||next.revisionHash==old.revisionHash))return@transaction
        next.revision?.validateRevision()
        val updated=old.copy(checked=next.checked,valid=next.valid,distinct=next.distinct,
            revision=next.revision ?: old.revision,revisionHash=next.revisionHash ?: old.revisionHash)
        update(c,owner,project,updated)
    }
    internal suspend fun finish(owner:UUID,project:UUID,id:UUID,result:EngineOptimizationResult)=transaction {c->
        val old=find(c,owner,project,id,lock=true) ?: return@transaction
        if(old.status!="running")return@transaction
        result.revision?.validateRevision()
        update(c,owner,project,old.copy(status=result.status,checked=result.checked,valid=result.valid,distinct=result.distinct,
            revision=result.revision,revisionHash=result.revision?.review?.sourceHash))
    }
    internal suspend fun fail(owner:UUID,project:UUID,id:UUID,message:String)=transaction {c->
        val old=find(c,owner,project,id,lock=true) ?: return@transaction
        if(old.status=="running")update(c,owner,project,old.copy(status=if(old.revision!=null)"ready" else "failed",error=message))
    }
    internal suspend fun markCancelling(owner:UUID,project:UUID,id:UUID):PlanOptimization=transaction {c->
        val old=find(c,owner,project,id,lock=true) ?: throw missingOptimization()
        if(old.status in listOf("running","queued"))old.copy(status="cancelling").also {update(c,owner,project,it)} else old
    }
    internal suspend fun cancelled(owner:UUID,project:UUID,id:UUID):PlanOptimization=transaction {c->
        val old=find(c,owner,project,id,lock=true) ?: throw missingOptimization()
        if(old.status=="cancelling")old.copy(status="cancelled").also {update(c,owner,project,it)} else old
    }
    internal suspend fun dismiss(owner:UUID,project:UUID,id:UUID):PlanOptimization=transaction {c->
        val old=find(c,owner,project,id,lock=true) ?: throw missingOptimization()
        if(old.running)throw ApiFailure(409,"OPTIMIZATION_RUNNING","Stop the remaining attempts first.")
        if(old.status=="applied")old else old.copy(status="dismissed",revision=null).also {update(c,owner,project,it)}
    }
    internal suspend fun apply(owner:UUID,project:UUID,id:UUID,expectedHash:String):UUID=transaction {c->
        globalLock(c)
        val old=find(c,owner,project,id,lock=true) ?: throw missingOptimization()
        old.appliedPlanID?.let {return@transaction UUID.fromString(it)}
        if(old.running)throw ApiFailure(409,"OPTIMIZATION_RUNNING","Stop the remaining attempts before applying.")
        val revision=old.revision?.takeIf {old.status in listOf("ready","cancelled")} ?: throw ApiFailure(409,"REVISION_UNAVAILABLE","No checked revision is available.")
        if(old.revisionHash!=expectedHash||revision.review.sourceHash!=expectedHash)
            throw ApiFailure(409,"REVISION_CHANGED","A newer checked revision is ready. Review it before applying.")
        revision.validateRevision()
        val newID=UUID.randomUUID()
        val elapsed=Duration.between(Instant.parse(old.createdAt),Instant.parse(old.updatedAt)).toMillis().coerceAtLeast(0)
        val plan=GeneratedFloorPlan(newID.toString(),"ready",drawings=revision.drawings,createdAt=Instant.now().toString(),durationMillis=elapsed,optimizedFromPlanID=old.basePlanID)
        c.prepareStatement("""INSERT INTO plotus.plan_generations(id,owner_id,project_id,request_id,
            source_revision,snapshot,status,result)
            SELECT ?,owner_id,project_id,id,source_revision,snapshot,'ready',
                jsonb_set(?::jsonb,'{sourceRevision}',to_jsonb(source_revision))
            FROM plotus.plan_optimizations WHERE owner_id=? AND project_id=? AND id=?""").use {
            it.setObject(1,newID);it.setString(2,Json.encodeToString(plan));it.setObject(3,owner);it.setObject(4,project);it.setObject(5,id)
            if(it.executeUpdate()!=1)throw missingOptimization()
        }
        update(c,owner,project,old.copy(status="applied",appliedPlanID=newID.toString(),revision=null))
        newID
    }
    fun recover() {source.connection.use {c->c.createStatement().use {it.executeUpdate("""UPDATE plotus.plan_optimizations
        SET status=CASE WHEN status='cancelling' THEN 'cancelled'
            WHEN result->'revision' IS NOT NULL AND result->'revision'<>'null'::jsonb THEN 'ready' ELSE 'failed' END,
        result=jsonb_set(result,'{error}','"Optimization was interrupted. The saved plan is unchanged."'::jsonb),
        updated_at=clock_timestamp() WHERE status IN ('queued','running','cancelling')""")}}}
    private fun update(c:Connection,owner:UUID,project:UUID,job:PlanOptimization) {
        c.prepareStatement("UPDATE plotus.plan_optimizations SET status=?,result=?::jsonb,applied_plan_id=?,updated_at=clock_timestamp() WHERE owner_id=? AND project_id=? AND id=?").use {
            it.setString(1,job.status);it.setString(2,Json.encodeToString(job));it.setObject(3,job.appliedPlanID?.let(UUID::fromString))
            it.setObject(4,owner);it.setObject(5,project);it.setObject(6,UUID.fromString(job.id));it.executeUpdate()
        }
    }
    private fun ResultSet.optimization()=Json.decodeFromString<PlanOptimization>(getString("result")).copy(
        id=getObject("id").toString(),basePlanID=getObject("base_generation_id").toString(),status=getString("status"),
        createdAt=getTimestamp("created_at").toInstant().toString(),updatedAt=getTimestamp("updated_at").toInstant().toString(),
        appliedPlanID=getString("applied_plan_id"))
    private fun globalLock(c:Connection){c.createStatement().use {it.execute("SELECT pg_advisory_xact_lock(738291614)")}}
    private suspend fun <T> db(action:(Connection)->T):T=withContext(Dispatchers.IO){source.connection.use(action)}
    private suspend fun <T> transaction(action:(Connection)->T):T=db {c->c.autoCommit=false
        try {val result=action(c);c.commit();result}catch(e:Exception){c.rollback();throw e}finally{c.autoCommit=true}}
    private fun missingPlan()=ApiFailure(404,"PLAN_NOT_FOUND","This plan could not be found.")
    private fun missingOptimization()=ApiFailure(404,"OPTIMIZATION_NOT_FOUND","This optimization could not be found.")
}
