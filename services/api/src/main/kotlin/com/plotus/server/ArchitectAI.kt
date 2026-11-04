package com.plotus.server

import com.plotus.shared.*
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

fun interface ArchitectAI { suspend fun explain(report:PlanReviewReport):AIPlanReview }
@Serializable internal data class ArchitectExplanation(val summary:String,val recommendations:List<AIReviewRecommendation>)

/** The model explains existing findings; it has no score, geometry or executable-operation fields. */
internal fun checkedExplanation(value:ArchitectExplanation,report:PlanReviewReport,model:String):AIPlanReview {
    val allowed=report.findings.map {it.id}.toSet()
    val prohibited=Regex("\\d|https?://|\\b(compliant|certified|approved|guaranteed|legally|code-compliance|feet|foot|metres?|meters?|percent|inches|sqft)\\b|\\bwill (improve|increase|fix)\\b",RegexOption.IGNORE_CASE)
    fun valid(text:String)=text.isNotBlank()&&text.length<=800&&text.none {it<' '&&it!='\n'}&&!prohibited.containsMatchIn(text)
    val critical=report.findings.filter {it.priority=="critical"}.map {it.id}.toSet()
    if(!valid(value.summary)||value.summary.length>320||report.findings.isNotEmpty()&&value.recommendations.isEmpty()||
        value.recommendations.size>80||value.recommendations.map {it.findingID}.distinct().size!=value.recommendations.size||
        !value.recommendations.map {it.findingID}.containsAll(critical)||
        value.recommendations.size>maxOf(3,critical.size)||
        value.recommendations.any {it.findingID !in allowed||!valid(it.explanation)||!valid(it.nextStep)})
        throw ApiFailure(502,"AI_REVIEW_INVALID","AI recommendations are unavailable. The measured review is still available.")
    return AIPlanReview("ready",value.summary,value.recommendations.sortedBy {if(it.findingID in critical)0 else 1},model,Instant.now().toString())
}

class MetaArchitectAI(private val endpoint:URI,private val key:String,private val model:String):ArchitectAI {
    private val client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).followRedirects(HttpClient.Redirect.NEVER).build()
    private val json=Json
    override suspend fun explain(report:PlanReviewReport):AIPlanReview=withContext(Dispatchers.IO) {
        val schema=buildJsonObject {
            put("type","object");put("additionalProperties",false)
            putJsonArray("required"){add("summary");add("recommendations")}
            putJsonObject("properties"){
                putJsonObject("summary"){put("type","string")}
                putJsonObject("recommendations"){put("type","array");putJsonObject("items"){
                    put("type","object");put("additionalProperties",false)
                    putJsonArray("required"){add("findingID");add("explanation");add("nextStep")}
                    putJsonObject("properties"){
                        putJsonObject("findingID"){put("type","string")}
                        putJsonObject("explanation"){put("type","string")}
                        putJsonObject("nextStep"){put("type","string")}
                    }
                }}
            }
        }
        val evidence=buildJsonObject {
            put("findings",Json.encodeToJsonElement(report.findings))
            put("gates",Json.encodeToJsonElement(report.gates))
            put("profile",report.profile);put("rulesVersion",report.rulesVersion)
        }
        val prompt=javaClass.getResourceAsStream("/architect-review-prompt.txt")!!.bufferedReader().use {it.readText()}
        val body=buildJsonObject {
            put("model",model);put("store",false);put("reasoning_effort","low");put("max_completion_tokens",4096)
            putJsonObject("response_format"){put("type","json_schema");putJsonObject("json_schema"){
                put("name","architect_explanation");put("strict",true);put("schema",schema)
            }}
            putJsonArray("messages"){
                addJsonObject {put("role","system");put("content",prompt)}
                addJsonObject {put("role","user");put("content",evidence.toString())}
            }
        }
        val request=HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(45))
            .header("Authorization","Bearer "+key).header("Content-Type","application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build()
        val response=runInterruptible {client.send(request,HttpResponse.BodyHandlers.ofInputStream())}
        val bytes=response.body().use {it.readNBytes(100001)}
        if(response.statusCode()!=200||bytes.size>100000)throw ApiFailure(503,"AI_REVIEW_UNAVAILABLE","AI recommendations are temporarily unavailable.")
        val choice=json.parseToJsonElement(bytes.decodeToString()).jsonObject["choices"]?.jsonArray?.firstOrNull()?.jsonObject
        if(choice?.get("finish_reason")?.jsonPrimitive?.contentOrNull=="length")throw ApiFailure(503,"AI_REVIEW_UNAVAILABLE","AI recommendations didn’t finish. Try again.")
        val content=choice?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull
            ?: throw ApiFailure(502,"AI_REVIEW_INVALID","AI recommendations are unavailable.")
        checkedExplanation(json.decodeFromString<ArchitectExplanation>(content),report,model)
    }
    companion object {
        fun configured():ArchitectAI? {
            val key=(System.getenv("PLOTUS_REVIEW_MODEL_API_KEY") ?: System.getenv("PLOTUS_SKETCH_MODEL_API_KEY")).orEmpty().trim()
            if(key.isBlank())return null
            val endpoint=URI(System.getenv("PLOTUS_REVIEW_MODEL_ENDPOINT") ?: System.getenv("PLOTUS_SKETCH_MODEL_ENDPOINT") ?: "https://api.meta.ai/v1/chat/completions")
            require(endpoint.scheme=="https"&&endpoint.host!=null&&endpoint.userInfo==null&&endpoint.fragment==null)
            return MetaArchitectAI(endpoint,key,System.getenv("PLOTUS_REVIEW_MODEL") ?: System.getenv("PLOTUS_SKETCH_MODEL") ?: "muse-spark-1.3")
        }
    }
}
