package com.plotus.server

import com.plotus.shared.*
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URI
import java.util.concurrent.atomic.AtomicInteger
import java.util.UUID
import java.io.File
import kotlinx.serialization.json.*
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class PlanEngineTransportTest {
    private val project=ProjectDraft("Transport fixture","Test city").toProject("c4f872f5-7c67-4f4e-aef9-b5e67f01ca2a").copy(
        plotSetup=PlotSetup(lengths=listOf(18.0,12.0,18.0,12.0),roads=listOf(PlotRoad(0))).rebuild())
    @Test fun previewRetriesOnlyAnExplicitWorkerBusyResponse()=runBlocking<Unit> {
        val calls=AtomicInteger();val protocols=mutableListOf<String>()
        val server=HttpServer.create(InetSocketAddress("127.0.0.1",0),0)
        server.createContext("/v1/programmes/resolve") {exchange->
            protocols+=exchange.protocol
            assertEquals("Bearer transport-unit-fixture",exchange.requestHeaders.getFirst("Authorization"))
            exchange.requestBody.use {it.readAllBytes()}
            val busy=calls.incrementAndGet()==1
            val bytes=(if(busy)"{}" else """{"programme":{"site_attempts":[]},"generation_available":true}""").toByteArray()
            exchange.sendResponseHeaders(if(busy)429 else 200,bytes.size.toLong())
            exchange.responseBody.use {it.write(bytes)}
        }
        server.start()
        try {
            val engine=HttpPlanEngine(URI("http://127.0.0.1:${server.address.port}"),"transport-unit-fixture")
            assertTrue(engine.preview(project,FloorPlanSetup()).canGenerate)
            assertEquals(2,calls.get());assertTrue(protocols.all {it=="HTTP/1.1"})
        }finally {server.stop(0)}
    }
    @Test fun anUpstreamFailureIsReportedWithoutRepeatingAnUncertainRequest()=runBlocking<Unit> {
        val calls=AtomicInteger();val server=HttpServer.create(InetSocketAddress("127.0.0.1",0),0)
        server.createContext("/v1/programmes/resolve") {exchange->
            calls.incrementAndGet();exchange.requestBody.use {it.readAllBytes()}
            exchange.sendResponseHeaders(503,2);exchange.responseBody.use {it.write("{}".toByteArray())}
        }
        server.start()
        try {
            val engine=HttpPlanEngine(URI("http://127.0.0.1:${server.address.port}"),"transport-unit-fixture")
            assertEquals(503,assertFailsWith<ApiFailure> {engine.preview(project,FloorPlanSetup())}.status)
            assertEquals(1,calls.get())
        }finally {server.stop(0)}
    }
    @Test fun thePrivateTransportAcceptsAFocusedThreeAttemptResult()=runBlocking<Unit> {
        val server=HttpServer.create(InetSocketAddress("127.0.0.1",0),0)
        server.createContext("/v1/plans/optimize") {exchange->
            val request=Json.parseToJsonElement(exchange.requestBody.use {it.readAllBytes()}.decodeToString()).jsonObject
            assertEquals(3,request["max_candidates"]!!.jsonPrimitive.int)
            assertEquals("SHORTEN_PASSAGE",request["operations"]!!.jsonArray.single().jsonPrimitive.content)
            val bytes="""{"status":"no_improvement","requested":3,"checked":3,"valid":1,"distinct":1,"revision":null}""".toByteArray()
            exchange.sendResponseHeaders(200,bytes.size.toLong());exchange.responseBody.use {it.write(bytes)}
        }
        server.start()
        try {
            val engine=HttpPlanEngine(URI("http://127.0.0.1:${server.address.port}"),"transport-unit-fixture")
            val request=StartOptimizationRequest(UUID.randomUUID().toString(),operations=listOf(ReviewOperation.SHORTEN_PASSAGE),maxCandidates=3)
            val result=engine.optimize(project,FloorPlanSetup(),listOf(PlanDrawing("ground","Ground","",100,100)),request,UUID.randomUUID().toString())
            assertEquals("no_improvement",result.status);assertEquals(3,result.checked)
        }finally {server.stop(0)}
    }
    @Test fun anActualPrivateWorkerResultMatchesThePublicRevisionContract() {
        val path=System.getenv("PLOTUS_REVIEW_RESULT_FIXTURE") ?: return
        // Local private evidence is optional and is never checked into this repository.
        val result=Json.decodeFromString<EngineOptimizationResult>(File(path).readText())
        assertEquals("ready",result.status)
        val revision=assertNotNull(result.revision)
        revision.validateRevision()
        assertTrue(revision.goals.isNotEmpty())
        assertTrue(revision.goals.all {it.revised<it.current})
    }
}
