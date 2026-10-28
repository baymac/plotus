package com.plotus.server

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URI
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import kotlin.test.*

class SketchProviderTest {
    @Test fun modelAdapterSendsTheImageWithStrictSchemaAndDecodesAnnotatedMeasurements() = runBlocking {
        var received:JsonObject?=null
        val server=HttpServer.create(InetSocketAddress("127.0.0.1",0),0)
        server.createContext("/chat/completions") {exchange->
            assertEquals("Bearer test-key",exchange.requestHeaders.getFirst("Authorization"))
            received=Json.parseToJsonElement(exchange.requestBody.bufferedReader().readText()).jsonObject
            val extraction=Json.encodeToString(ReadSketch(listOf(SketchLength(feet=40.0),SketchLength(feet=30.0),SketchLength(feet=40.0),SketchLength(feet=30.0)),northDegrees=90.0,roadSides=listOf(1)))
            val response=buildJsonObject {putJsonArray("choices") {addJsonObject {putJsonObject("message") {put("content",extraction)}}}}.toString().toByteArray()
            exchange.responseHeaders.add("Content-Type","application/json");exchange.sendResponseHeaders(200,response.size.toLong());exchange.responseBody.use {it.write(response)}
        }
        server.start()
        try {
            val result=MetaSketchReader(URI("http://127.0.0.1:${server.address.port}/chat/completions"),"test-key","test-model").read(SketchTest.validImage())
            val request=assertNotNull(received)
            assertEquals(false,request["store"]!!.jsonPrimitive.boolean)
            assertEquals("json_schema",request["response_format"]!!.jsonObject["type"]!!.jsonPrimitive.content)
            val image=request["messages"]!!.jsonArray[1].jsonObject["content"]!!.jsonArray[1].jsonObject["image_url"]!!.jsonObject["url"]!!.jsonPrimitive.content
            assertTrue(image.startsWith("data:image/png;base64,"))
            assertEquals(40*com.plotus.shared.METRES_PER_FOOT,result.setup.lengths[0])
            assertEquals(90.0,result.setup.northDegrees);assertEquals(1,result.setup.roads.single().side)
            assertTrue(result.setup.errors(completeBoundary=true,completeRoads=true).isEmpty())
        } finally {server.stop(0)}
    }
}
