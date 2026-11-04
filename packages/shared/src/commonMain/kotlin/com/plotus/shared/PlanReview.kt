package com.plotus.shared

import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

/** Versioned evidence from the deterministic engine. AI cannot supply these fields. */
@Serializable data class ReviewMeasurement(val id:String,val label:String,val value:Double?=null,val unit:String="count")
@Serializable data class ReviewCheck(val id:String,val label:String,val status:String,val hard:Boolean=true,val detail:String="")
@Serializable data class ReviewMetric(val id:String,val title:String,val weight:Int,val score:Double?=null,val basis:String="",
    val measurements:List<ReviewMeasurement> = emptyList(),val findingIDs:List<String> = emptyList())
@Serializable enum class ReviewOperation(val title:String) {
    CONSOLIDATE_OPEN_SPACE("Consolidate outdoor space"), SHORTEN_PASSAGE("Shorten the passage"),
    BALANCE_ROOM_PROPORTIONS("Improve useful room proportions"), IMPROVE_CONNECTIVITY("Improve room connections"),
    IMPROVE_WINDOW_ACCESS("Improve exterior window access"), IMPROVE_PRIVACY("Improve room privacy"),
    IMPROVE_PARKING_ACCESS("Improve parking and entry access"), IMPROVE_VASTU_ALIGNMENT("Improve Vastu alignment")
}
@Serializable data class ReviewFinding(val id:String,val code:String,val metricID:String,val priority:String,val title:String,val explanation:String,
    val operation:ReviewOperation?=null,val measurements:List<ReviewMeasurement> = emptyList(),val roomIDs:List<String> = emptyList(),val floorIDs:List<String> = emptyList())
@Serializable data class VastuReview(val requested:Boolean=false,val score:Double?=null,val ruleset:String="plotus-vastu-v1",
    val checks:List<ReviewCheck> = emptyList(),val preferences:List<ReviewCheck> = emptyList(),val canonicalOverrides:List<String> = emptyList())
@Serializable data class PlanReviewReport(val version:Int=1,val profile:String="balanced_v1",val rulesVersion:String,
    val generatedAt:String="",val analysisID:String?=null,val sourceHash:String?=null,val designScore:Int?=null,val designLevel:String="Not assessed",
    val metrics:List<ReviewMetric> = emptyList(),val gates:List<ReviewCheck> = emptyList(),val findings:List<ReviewFinding> = emptyList(),
    val vastu:VastuReview=VastuReview(),val measurements:List<ReviewMeasurement> = emptyList(),val canOptimize:Boolean=false) {
    val requiredChecksPass get()=gates.filter {it.hard}.let {it.isNotEmpty()&&it.all {g->g.status=="pass"}}
}
@Serializable data class AIReviewRecommendation(val findingID:String,val explanation:String)
@Serializable data class AIPlanReview(val status:String,val summary:String="",val recommendations:List<AIReviewRecommendation> = emptyList(),
    val model:String?=null,val generatedAt:String="")
@Serializable data class ReviewComparison(val id:String,val title:String,val unit:String,val current:Double?=null,val revised:Double?=null)
@Serializable data class OptimizedRevision(val drawings:List<PlanDrawing>,val review:PlanReviewReport,val comparisons:List<ReviewComparison> = emptyList(),
    val tradeoffs:List<String> = emptyList())
@Serializable data class StartOptimizationRequest(val requestID:String,val profile:String="balanced_v1",val operations:List<ReviewOperation> = emptyList(),val maxCandidates:Int=5)
@Serializable data class PlanOptimization(val id:String,val basePlanID:String,val status:String,val createdAt:String,val updatedAt:String="",
    val requested:Int=5,val checked:Int=0,val valid:Int=0,val distinct:Int=0,val baseReview:PlanReviewReport?=null,
    val revision:OptimizedRevision?=null,val revisionHash:String?=null,val error:String?=null,val appliedPlanID:String?=null) {
    val running get()=status in listOf("queued","running","cancelling")
}
@Serializable data class EngineOptimizationResult(val status:String,val requested:Int,val checked:Int,val valid:Int,val distinct:Int,
    val revision:OptimizedRevision?=null)
@Serializable data class EngineOptimizationProgress(val requested:Int=5,val checked:Int=0,val valid:Int=0,val distinct:Int=0,
    val revisionHash:String?=null,val revision:OptimizedRevision?=null,val status:String="running")
@Serializable data class ApplyOptimizationRequest(val revisionHash:String)
@Serializable data class CurrentOptimization(val optimization:PlanOptimization?=null)

/** Validate the private transport before storing or rendering its report. */
fun PlanReviewReport.isValid():Boolean {
    val expected=mapOf("space" to 25,"circulation" to 20,"function" to 20,"daylight" to 15,"privacy" to 10,"access" to 10)
    fun text(value:String,max:Int)=value.length<=max&&value.none {it<' '&&it!='\n'}
    fun measured(values:List<ReviewMeasurement>)=values.size<=32&&values.all {text(it.id,160)&&text(it.label,160)&&text(it.unit,40)&&(it.value==null||it.value.isFinite()&&kotlin.math.abs(it.value)<=1e12)}
    fun checks(values:List<ReviewCheck>)=values.size<=400&&values.all {text(it.id,240)&&text(it.label,360)&&text(it.detail,1200)&&it.status in listOf("pass","fail","not_assessed","not_applicable","preference")}
    if(version!=1||profile!="balanced_v1"||!text(rulesVersion,120)||designScore!=null&&designScore !in 0..100||
        sourceHash?.matches(Regex("[a-f0-9]{64}"))==false||analysisID?.let {it.length>80}==true||
        metrics.size!=6||metrics.map {it.id}.toSet()!=expected.keys||!measured(measurements)||!checks(gates)||
        !checks(vastu.checks)||!checks(vastu.preferences)||vastu.score?.let {!it.isFinite()||it !in 0.0..100.0}==true)return false
    if(metrics.any {it.weight!=expected[it.id]||it.score?.let {s->!s.isFinite()||s !in 0.0..100.0}==true||!text(it.basis,1200)||!measured(it.measurements)})return false
    val measuredScore=if(metrics.all {it.score!=null})metrics.sumOf {it.score!!*it.weight/100}.roundToInt() else null
    if(designScore!=measuredScore)return false
    return findings.size<=80&&findings.map {it.id}.distinct().size==findings.size&&findings.all {
        text(it.id,160)&&text(it.code,300)&&text(it.title,360)&&text(it.explanation,1600)&&
            it.priority in listOf("critical","high","medium","optional")&&measured(it.measurements)&&
            it.roomIDs.size<=120&&it.floorIDs.size<=6&&it.roomIDs.all {r->text(r,160)}&&it.floorIDs.all {f->text(f,160)}
    }
}
