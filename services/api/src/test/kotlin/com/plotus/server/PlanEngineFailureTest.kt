package com.plotus.server

import com.plotus.shared.*
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URI
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class PlanEngineFailureTest {
    @Test fun searchAndResourceLimitsAreNotReportedAsRejectedLayouts()=runBlocking<Unit> {
        val cases=listOf(
            Triple(422,"search_budget_exhausted",422 to "PLAN_SEARCH_LIMIT"),
            Triple(422,"generation_resource_limit",503 to "PLAN_RESOURCE_LIMIT"),
            Triple(422,"solver_model_error",503 to "PLAN_ENGINE_ERROR"),
            Triple(422,"architectural_acceptance_failed",422 to "PLAN_NO_FIT"),
            Triple(504,"WORKER_TIMEOUT",504 to "PLAN_TIMED_OUT"),
        )
        val project=Project(UUID.randomUUID().toString(),"Engine boundary fixture","Test city",plotSetup=
            PlotSetup(lengths=listOf(18.0,12.0,18.0,12.0),roads=listOf(PlotRoad(0))).rebuild())
        for((status,code,expected) in cases) {
            val server=HttpServer.create(InetSocketAddress("127.0.0.1",0),0)
            server.createContext("/v1/programmes/resolve") {exchange->
                val body="""{"error":{"code":"$code"}}""".encodeToByteArray()
                exchange.sendResponseHeaders(status,body.size.toLong());exchange.responseBody.use {it.write(body)}
            }
            server.start()
            try {
                val engine=HttpPlanEngine(URI("http://127.0.0.1:${server.address.port}"),"test-only")
                val failure=try {engine.preview(project,FloorPlanSetup());error("Expected an engine failure for $code")}
                    catch(failure:ApiFailure){failure}
                assertEquals(expected.first,failure.status,code)
                assertEquals(expected.second,failure.code,code)
            }finally {server.stop(0)}
        }
    }
}
