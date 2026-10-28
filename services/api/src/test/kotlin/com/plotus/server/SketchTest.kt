package com.plotus.server

import com.plotus.shared.*
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import javax.imageio.ImageIO
import kotlinx.coroutines.*
import kotlin.test.*

class SketchTest {
    @Test fun mixedUnitsAndConcaveAnnotationsProduceReviewableGeometryWithoutInventedLengths() {
        val ft=ReadSketch(listOf(SketchLength(feet=40.0,inches=6.0),SketchLength(feet=30.0),SketchLength(feet=40.0,inches=6.0),SketchLength(feet=30.0)),northDegrees=-45.0,roadSides=listOf(1,1,9),edgeBearingsDegrees=listOf(0.0,90.0,180.0,270.0)).suggestion()
        assertEquals(40.5*METRES_PER_FOOT,ft.setup.lengths[0])
        assertTrue(ft.setup.errors(completeBoundary=true,completeRoads=true).isEmpty())
        assertEquals(listOf(1),ft.setup.roads.map {it.side})
        assertEquals(-45.0,ft.setup.northDegrees)
        val lengths=listOf(12.0,4.0,8.0,8.0,4.0,12.0)
        val concave=ReadSketch(lengths.map {SketchLength(metres=it)},edgeBearingsDegrees=listOf(0.0,90.0,180.0,90.0,180.0,270.0)).suggestion()
        assertTrue(concave.setup.errors(completeBoundary=true).isEmpty())
        assertEquals(80.0,concave.setup.areaSquareMetres!!,.001)
        val partial=ReadSketch(listOf(SketchLength(),SketchLength(feet=30.0),SketchLength(feet=-4.0),SketchLength())).suggestion()
        assertNull(partial.setup.lengths[0]);assertNull(partial.setup.lengths[2]);assertTrue(partial.setup.vertices.isEmpty())
        assertTrue(partial.notes.any {it.contains("weren’t readable")})
        assertFailsWith<SketchReadFailure> {ReadSketch(emptyList()).suggestion()}
    }
    @Test fun imagesAreValidatedAndJobsAreOwnedBoundedAndAsynchronous() = runBlocking {
        val image=validImage();validateSketchImage(image)
        assertFailsWith<ApiFailure> {validateSketchImage(SketchImage("image/jpeg",image.base64))}
        assertFailsWith<ApiFailure> {validateSketchImage(SketchImage("image/png","invalid"))}
        val gate=CompletableDeferred<Unit>();val calls=AtomicInteger()
        val imports=SketchImports(SketchReader {calls.incrementAndGet();gate.await();ReadSketch(List(4){SketchLength(metres=10.0)}).suggestion()})
        val owner=UUID.randomUUID();val project=UUID.randomUUID()
        try {
            val job=imports.start(owner,project,image)
            assertEquals("reading",job.status)
            assertEquals(429,assertFailsWith<ApiFailure> {imports.start(owner,project,image)}.status)
            assertEquals(404,assertFailsWith<ApiFailure> {imports.get(UUID.randomUUID(),project,job.id)}.status)
            assertEquals(404,assertFailsWith<ApiFailure> {imports.get(owner,UUID.randomUUID(),job.id)}.status)
            gate.complete(Unit)
            withTimeout(3000) {while(imports.get(owner,project,job.id).status=="reading")delay(10)}
            assertEquals(1,calls.get());assertNotNull(imports.get(owner,project,job.id).suggestion)
        } finally {imports.close()}
        assertEquals(503,assertFailsWith<ApiFailure> {SketchImports(null).use {it.start(owner,project,image)}}.status)
    }
    companion object {
        fun validImage():SketchImage {
            val output=ByteArrayOutputStream();ImageIO.write(BufferedImage(128,128,BufferedImage.TYPE_INT_RGB),"png",output)
            return SketchImage("image/png",Base64.getEncoder().encodeToString(output.toByteArray()))
        }
    }
}
