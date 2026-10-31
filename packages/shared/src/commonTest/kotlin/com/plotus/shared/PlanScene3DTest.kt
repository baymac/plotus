package com.plotus.shared

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.*

class PlanScene3DTest {
    private val outline=listOf(PlanScenePoint(0.0,0.0),PlanScenePoint(8.0,0.0),PlanScenePoint(8.0,6.0),PlanScenePoint(0.0,6.0))
    private val polygon=PlanScenePolygon(outline)
    private val scene=PlanScene3D(label="Ground",floorHeightMetres=3.2,wallHeightMetres=3.2,wallThicknessMetres=.254,
        footprint=listOf(polygon),walls=listOf(polygon),
        openings=listOf(PlanSceneOpening("window-1","window",PlanScenePoint(3.0,0.0),1.2,.254,1.2,.9)),
        furniture=listOf(PlanSceneFurniture("bed-1","bed",PlanScenePoint(2.0,2.0),2.0,1.5,90.0)))

    @Test fun geometrySurvivesHistorySerializationAndOldDrawingsStillDecode() {
        assertTrue(scene.isValid())
        val drawing=PlanDrawing("ground","Ground","image",800,600,scene3d=scene)
        assertEquals(drawing,Json.decodeFromString<PlanDrawing>(Json.encodeToString(drawing)))
        assertNull(Json.decodeFromString<PlanDrawing>("""{"id":"older","label":"Ground","pngBase64":"image","width":800,"height":600}""").scene3d)
    }

    @Test fun holesAndCaseworkRetainTheirActualFootprints() {
        val hole=listOf(PlanScenePoint(3.0,2.0),PlanScenePoint(4.0,2.0),PlanScenePoint(4.0,3.0),PlanScenePoint(3.0,3.0))
        val counter=PlanScenePolygon(listOf(PlanScenePoint(0.0,0.0),PlanScenePoint(2.0,0.0),PlanScenePoint(2.0,.6),PlanScenePoint(.6,.6),PlanScenePoint(.6,2.0),PlanScenePoint(0.0,2.0)))
        assertTrue(scene.copy(footprint=listOf(polygon.copy(holes=listOf(hole))),
            furniture=listOf(PlanSceneFurniture("counter","counter",PlanScenePoint(1.0,1.0),2.0,2.0,polygons=listOf(counter)))).isValid())
    }

    @Test fun nonFiniteOutOfBoundsAndDegenerateGeometryAreRejected() {
        assertFalse(scene.copy(footprint=listOf(PlanScenePolygon(outline+PlanScenePoint(Double.NaN,0.0)))).isValid())
        assertFalse(scene.copy(walls=listOf(PlanScenePolygon(outline+PlanScenePoint(2000.1,0.0)))).isValid())
        assertFalse(scene.copy(footprint=listOf(PlanScenePolygon(List(3){PlanScenePoint(0.0,0.0)}))).isValid())
        assertFalse(scene.copy(wallHeightMetres=4.0).isValid())
        assertFalse(scene.copy(version=2).isValid())
    }

    @Test fun stairPartsSurviveSerializationAndStayBelowTheNextFloor() {
        val part=PlanSceneStairPart(listOf(polygon),.15,.15)
        val stair=PlanSceneStair("stair",listOf(polygon),21,parts=listOf(part))
        val model=scene.copy(stairs=listOf(stair))
        assertTrue(model.isValid())
        assertEquals(model,Json.decodeFromString<PlanScene3D>(Json.encodeToString(model)))
        assertFalse(scene.copy(stairs=listOf(stair.copy(parts=listOf(part.copy(bottomMetres=-.1))))).isValid())
        assertFalse(scene.copy(stairs=listOf(stair.copy(parts=listOf(part.copy(heightMetres=4.0))))).isValid())
        assertFalse(scene.copy(stairs=listOf(stair.copy(parts=List(123){part}))).isValid())
    }

    @Test fun apertureHeightsAndResourceLimitsAreBounded() {
        assertFalse(scene.copy(openings=scene.openings.map {it.copy(heightMetres=2.5)}).isValid())
        assertFalse(scene.copy(furniture=scene.furniture.map {it.copy(depthMetres=-1.0)}).isValid())
        assertFalse(scene.copy(openings=List(513){scene.openings.single().copy(id="window-$it")}).isValid())
        assertFalse(scene.copy(furniture=List(801){scene.furniture.single().copy(id="furniture-$it")}).isValid())
        assertFalse(scene.copy(walls=List(14){PlanScenePolygon(List(4096){i->val t=i*2*kotlin.math.PI/4096;PlanScenePoint(kotlin.math.cos(t),kotlin.math.sin(t))})}).isValid())
    }
}
