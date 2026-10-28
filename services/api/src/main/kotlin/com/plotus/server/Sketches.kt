package com.plotus.server

import com.plotus.shared.*
import java.io.ByteArrayInputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO
import javax.imageio.stream.MemoryCacheImageInputStream
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import kotlin.math.*

fun interface SketchReader { suspend fun read(image:SketchImage):SketchSuggestion }

/** Short-lived analysis jobs keep a slow model call out of the browser's HTTP request. Images are never stored. */
class SketchImports(private val reader:SketchReader?,private val scope:CoroutineScope=CoroutineScope(SupervisorJob()+Dispatchers.IO)):AutoCloseable {
    private data class Entry(val owner:UUID,val project:UUID,val created:Instant,val job:SketchImport)
    private val jobs=ConcurrentHashMap<String,Entry>()
    private val capacity=Semaphore(2)
    init {scope.launch {while(isActive) {delay(15000);jobs.entries.removeIf {it.value.created.isBefore(Instant.now().minusSeconds(600))}}}}
    @Synchronized fun start(owner:UUID,project:UUID,image:SketchImage):SketchImport {
        val model=reader ?: throw ApiFailure(503,"SKETCH_UNAVAILABLE","Sketch reading isn’t connected yet. You can enter measurements manually.")
        validateSketchImage(image)
        jobs.entries.removeIf {it.value.created.isBefore(Instant.now().minusSeconds(600))}
        if(jobs.size>=64||jobs.values.any {it.owner==owner&&it.job.status=="reading"}||!capacity.tryAcquire())
            throw ApiFailure(429,"SKETCH_BUSY","A sketch is already being read. Please try again shortly.")
        val id=UUID.randomUUID().toString()
        val entry=Entry(owner,project,Instant.now(),SketchImport(id,"reading"))
        jobs[id]=entry
        scope.launch {
            try {
                val suggestion=withTimeout(70000) {model.read(image)}
                jobs[id]=entry.copy(job=SketchImport(id,"ready",suggestion))
            } catch(timeout:TimeoutCancellationException) {
                jobs[id]=entry.copy(job=SketchImport(id,"failed",error="This sketch took too long to read. Try a clearer photo."))
            } catch(cancelled:CancellationException) {throw cancelled}
            catch(failure:Exception) {
                jobs[id]=entry.copy(job=SketchImport(id,"failed",error=(failure as? SketchReadFailure)?.message ?: "Couldn’t read this sketch. Try a clearer photo showing the outline and measurements."))
            } finally {capacity.release()}
        }
        return entry.job
    }
    fun get(owner:UUID,project:UUID,id:String):SketchImport {
        val entry=jobs[id]?.takeIf {it.owner==owner&&it.project==project&&!it.created.isBefore(Instant.now().minusSeconds(600))}
            ?: throw ApiFailure(404,"SKETCH_NOT_FOUND","This sketch analysis has expired. Upload the image again.")
        return entry.job
    }
    override fun close() {scope.cancel();jobs.clear()}
    companion object {
        fun configured():SketchImports {
            val key=System.getenv("PLOTUS_SKETCH_MODEL_API_KEY")?.trim().orEmpty()
            if(key.isEmpty())return SketchImports(null)
            val endpoint=URI(System.getenv("PLOTUS_SKETCH_MODEL_ENDPOINT") ?: "https://api.meta.ai/v1/chat/completions")
            require(endpoint.scheme=="https"&&endpoint.host!=null&&endpoint.userInfo==null&&endpoint.query==null&&endpoint.fragment==null) {"Sketch model endpoint must use HTTPS"}
            return SketchImports(MetaSketchReader(endpoint,key,System.getenv("PLOTUS_SKETCH_MODEL") ?: "muse-spark-1.3"))
        }
    }
}

internal class SketchReadFailure(message:String):Exception(message)
internal fun validateSketchImage(image:SketchImage) {
    if(image.mimeType !in setOf("image/jpeg","image/png")||image.base64.length>3_500_000)
        throw ApiFailure(400,"INVALID_SKETCH_IMAGE","Choose a JPEG or PNG image smaller than 2.5 MB.")
    val bytes=try {Base64.getDecoder().decode(image.base64)}catch(_:IllegalArgumentException){throw ApiFailure(400,"INVALID_SKETCH_IMAGE","This image couldn’t be read. Choose another photo.")}
    val jpeg=bytes.size>3&&bytes[0]==0xff.toByte()&&bytes[1]==0xd8.toByte()&&bytes[2]==0xff.toByte()
    val png=bytes.size>8&&bytes.copyOfRange(0,8).contentEquals(byteArrayOf(0x89.toByte(),0x50,0x4e,0x47,0x0d,0x0a,0x1a,0x0a))
    if((image.mimeType=="image/jpeg"&&!jpeg)||(image.mimeType=="image/png"&&!png)||bytes.size>2_600_000)
        throw ApiFailure(400,"INVALID_SKETCH_IMAGE","This image couldn’t be read. Choose another photo.")
    val dimensions=try {
        MemoryCacheImageInputStream(ByteArrayInputStream(bytes)).use { stream ->
            val readers=ImageIO.getImageReaders(stream)
            if(!readers.hasNext())null else readers.next().let { decoder ->
                try {decoder.input=stream;decoder.getWidth(0) to decoder.getHeight(0)}finally {decoder.dispose()}
            }
        }
    }catch(_:Exception){null}
    if(dimensions==null||dimensions.first !in 32..4096||dimensions.second !in 32..4096||dimensions.first.toLong()*dimensions.second>16_000_000)
        throw ApiFailure(400,"INVALID_SKETCH_IMAGE","Choose a clear photo with dimensions between 32 and 4,096 pixels.")
}

@Serializable internal data class SketchLength(val feet:Double?=null,val inches:Double?=null,val metres:Double?=null) {
    fun value():Double? {
        if(metres!=null&&(feet!=null||inches!=null))return null
        val amount=metres ?: if(feet!=null||inches!=null)((feet ?: 0.0)+(inches ?: 0.0)/12)*METRES_PER_FOOT else return null
        if(listOfNotNull(feet,inches,metres).any {!it.isFinite()||it<0})return null
        return amount.takeIf {it.isFinite()&&it>0&&it<=10000}
    }
}
@Serializable internal data class ReadSketch(
    val sides:List<SketchLength>,val diagonals:List<SketchLength> = emptyList(),
    val northDegrees:Double?=null,val roadSides:List<Int> = emptyList(),
    val edgeBearingsDegrees:List<Double?> = emptyList(),val notes:List<String> = emptyList(),
) {
    fun suggestion():SketchSuggestion {
        if(sides.size !in 3..12)throw SketchReadFailure("Couldn’t find a clear plot boundary. Include the whole outline in your photo.")
        val lengths=sides.map {it.value()}
        val diagonalLengths=List(sides.size-3) {diagonals.getOrNull(it)?.value()}
        val north=northDegrees?.takeIf {it.isFinite()&&it in -180.0..180.0}
        val units=if(sides.any {it.feet!=null||it.inches!=null})PlotUnits.FEET_INCHES else if(sides.any {it.metres!=null})PlotUnits.METRES else PlotUnits.FEET_INCHES
        var setup=PlotSetup(lengths=lengths,diagonals=diagonalLengths,useDiagonals=diagonalLengths.any {it!=null},units=units,northDegrees=north ?: 0.0,
            roads=roadSides.distinct().filter {it in sides.indices}.mapIndexed {i,side->PlotRoad(side,priority=i)})
        var measuredOutline=false
        if(lengths.all {it!=null}&&edgeBearingsDegrees.size==sides.size&&edgeBearingsDegrees.all {it!=null&&it.isFinite()}) {
            var cursor=PlotPoint(0.0,0.0)
            val vertices=lengths.indices.map {i->cursor.also {
                val radians=edgeBearingsDegrees[i]!!*PI/180
                cursor=PlotPoint(cursor.x+lengths[i]!!*cos(radians),cursor.y+lengths[i]!!*sin(radians))
            }}
            val candidate=setup.copy(vertices=vertices)
            if(distance(cursor,vertices[0])<.002&&candidate.errors(completeBoundary=true).isEmpty()) {setup=candidate;measuredOutline=true}
        }
        if(!measuredOutline)setup=setup.rebuild()
        val review=buildList {
            add("Check the extracted dimensions and outline before saving.")
            if(lengths.any {it==null})add("Some side lengths weren’t readable. Fill in the highlighted measurements.")
            if(!measuredOutline&&!setup.useDiagonals)add("Adjust the corners to match the sketch; side lengths alone can fit several outlines.")
            if(north==null)add("North wasn’t clear. Check Direction.")
            if(setup.roads.isEmpty())add("Check the road-facing sides in Roads.")
            addAll(notes.take(4).map {it.take(240)})
        }.distinct().take(8)
        return SketchSuggestion(setup,review)
    }
}

internal class MetaSketchReader(private val endpoint:URI,private val key:String,private val model:String):SketchReader {
    private val client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build()
    private val json=Json {ignoreUnknownKeys=true}
    override suspend fun read(image:SketchImage):SketchSuggestion = withContext(Dispatchers.IO) {
        val prompt=javaClass.getResourceAsStream("/sketch-prompt.txt")!!.bufferedReader().use {it.readText()}
        val schema=javaClass.getResourceAsStream("/sketch-schema.json")!!.bufferedReader().use {json.parseToJsonElement(it.readText())}
        val body=buildJsonObject {
            put("model",model);put("store",false);put("max_completion_tokens",3500)
            putJsonObject("response_format") {put("type","json_schema");putJsonObject("json_schema") {put("name","plot_sketch");put("strict",true);put("schema",schema)}}
            putJsonArray("messages") {
                addJsonObject {put("role","system");put("content",prompt)}
                addJsonObject {put("role","user");putJsonArray("content") {
                    addJsonObject {put("type","text");put("text","Read this plot sketch and extract its measurements. Return only the JSON object, keeping unreadable values null.")}
                    addJsonObject {put("type","image_url");putJsonObject("image_url") {put("url","data:${image.mimeType};base64,${image.base64}")}}
                }}
            }
        }
        val request=HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(65)).header("Authorization","Bearer $key").header("Content-Type","application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build()
        val response=client.send(request,HttpResponse.BodyHandlers.ofString())
        if(response.statusCode()!=200)throw SketchReadFailure(if(response.statusCode()==429)"Sketch reading is busy. Try again shortly." else "Sketch reading is temporarily unavailable. You can enter measurements manually.")
        if(response.body().length>100_000)throw SketchReadFailure("The sketch couldn’t be read. Try a clearer photo.")
        val content=json.parseToJsonElement(response.body()).jsonObject["choices"]?.jsonArray?.firstOrNull()?.jsonObject?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull
            ?: throw SketchReadFailure("Couldn’t find a plot boundary in this image.")
        json.decodeFromString<ReadSketch>(content).suggestion()
    }
}
