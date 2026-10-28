package com.plotus.server

import com.plotus.shared.*
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URI
import java.util.concurrent.atomic.AtomicInteger
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
}
