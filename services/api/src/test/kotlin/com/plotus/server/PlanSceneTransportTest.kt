package com.plotus.server

import com.plotus.shared.*
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URI
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlin.test.*

class PlanSceneTransportTest {
    private val project=ProjectDraft("Scene fixture","Test city").toProject("c4f872f5-7c67-4f4e-aef9-b5e67f01ca2a").copy(
        plotSetup=PlotSetup(lengths=listOf(18.0,12.0,18.0,12.0),roads=listOf(PlotRoad(0))).rebuild())
    private val outline=PlanScenePolygon(listOf(PlanScenePoint(0.0,0.0),PlanScenePoint(8.0,0.0),PlanScenePoint(8.0,6.0),PlanScenePoint(0.0,6.0)))
    private val scene=PlanScene3D(label="Ground",floorHeightMetres=3.2,wallHeightMetres=3.2,wallThicknessMetres=.254,
        footprint=listOf(outline),walls=listOf(outline))

    private suspend fun generate(scene:PlanScene3D?):List<PlanDrawing> {
        val server=HttpServer.create(InetSocketAddress("127.0.0.1",0),0)
        fun route(path:String,result:JsonElement) {
            server.createContext(path) {exchange->
                exchange.requestBody.use {it.readAllBytes()}
                val bytes=result.toString().toByteArray()
                exchange.sendResponseHeaders(200,bytes.size.toLong())
                exchange.responseBody.use {it.write(bytes)}
            }
        }
        route("/v1/programmes/resolve",buildJsonObject {put("programme",buildJsonObject {put("site_attempts",buildJsonArray {})});put("generation_available",true)})
        route("/v1/plans/generate",buildJsonObject {put("drawings",Json.encodeToJsonElement(listOf(PlanDrawing("ground","Ground","image",800,600,scene3d=scene))))})
        server.start()
        try {return HttpPlanEngine(URI("http://127.0.0.1:${server.address.port}"),"scene-fixture").generate(project,FloorPlanSetup())}
        finally {server.stop(0)}
    }

    @Test fun acceptedSceneIsRetainedAlongsideItsActualDrawing()=runBlocking<Unit> {
        assertEquals(scene,generate(scene).single().scene3d)
        assertNull(generate(null).single().scene3d)
    }

    @Test fun invalidSceneIsRejectedAtTheEngineBoundary()=runBlocking<Unit> {
        val failure=assertFailsWith<ApiFailure> {generate(scene.copy(footprint=listOf(PlanScenePolygon(outline.outer+PlanScenePoint(2001.0,0.0)))))}
        assertEquals(502,failure.status)
        assertEquals("PLAN_INVALID",failure.code)
    }
}
