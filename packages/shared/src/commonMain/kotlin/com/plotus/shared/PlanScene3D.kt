package com.plotus.shared

import kotlinx.serialization.Serializable
import kotlin.math.abs

/** Metres in the same x-right/y-down plane as the saved 2D drawing. */
@Serializable data class PlanScenePoint(val x:Double,val y:Double)
@Serializable data class PlanScenePolygon(val outer:List<PlanScenePoint>,val holes:List<List<PlanScenePoint>> = emptyList())
@Serializable data class PlanSceneRoom(val id:String,val name:String,val kind:String,val polygons:List<PlanScenePolygon>)
@Serializable data class PlanSceneOpening(val id:String,val kind:String,val center:PlanScenePoint,
    val widthMetres:Double,val thicknessMetres:Double,val heightMetres:Double,val sillMetres:Double=0.0,val rotationDegrees:Double=0.0)
/** Dimensions are local axes before the clockwise planar rotation; center is in world axes. */
@Serializable data class PlanSceneFurniture(val id:String,val kind:String,val center:PlanScenePoint,
    val widthMetres:Double,val depthMetres:Double,val rotationDegrees:Double=0.0,val polygons:List<PlanScenePolygon> = emptyList())
@Serializable data class PlanSceneStairPart(val polygons:List<PlanScenePolygon>,val bottomMetres:Double,val heightMetres:Double)
@Serializable data class PlanSceneStair(val id:String,val polygons:List<PlanScenePolygon>,val steps:Int,val rotationDegrees:Double=0.0,
    val parts:List<PlanSceneStairPart> = emptyList())

/** Portable rendering data only. Solver state, source CAD and internal asset paths stay private. */
@Serializable data class PlanScene3D(val version:Int=1,val label:String,
    val floorHeightMetres:Double,val wallHeightMetres:Double,val wallThicknessMetres:Double,
    val footprint:List<PlanScenePolygon>,val walls:List<PlanScenePolygon>,
    val rooms:List<PlanSceneRoom> = emptyList(),val openings:List<PlanSceneOpening> = emptyList(),
    val furniture:List<PlanSceneFurniture> = emptyList(),val stairs:List<PlanSceneStair> = emptyList(),
    val assumptions:List<String> = emptyList()) {
    /** Bound untrusted engine output before persistence and before allocating browser meshes. */
    fun isValid():Boolean {
        if(version!=1||label.isBlank()||label.length>160||footprint.isEmpty()||
            !distance(floorHeightMetres,20.0)||!distance(wallHeightMetres,20.0)||wallHeightMetres>floorHeightMetres||
            !distance(wallThicknessMetres,3.0)||rooms.size>120||openings.size>512||furniture.size>800||stairs.size>24||
            assumptions.size>12||assumptions.any {it.length>240})return false
        var points=0
        fun ring(values:List<PlanScenePoint>):Boolean {
            points+=values.size
            if(values.size !in 3..8192||points>50_000||values.any {!point(it)})return false
            val twiceArea=values.indices.sumOf {i->val a=values[i];val b=values[(i+1)%values.size];a.x*b.y-b.x*a.y}
            return abs(twiceArea)>1e-8
        }
        fun polygons(values:List<PlanScenePolygon>,required:Boolean=false):Boolean =
            (!required||values.isNotEmpty())&&values.size<=1000&&values.all {p->
                ring(p.outer)&&p.holes.size<=128&&p.holes.all {ring(it)}
            }
        if(!polygons(footprint,true)||!polygons(walls))return false
        if(rooms.map {it.id}.distinct().size!=rooms.size||rooms.any {
            !identifier(it.id)||it.name.length>160||!identifier(it.kind)||!polygons(it.polygons,true)
        })return false
        if(openings.map {it.id}.distinct().size!=openings.size||openings.any {
            !identifier(it.id)||!identifier(it.kind)||!point(it.center)||!distance(it.widthMetres,100.0)||
                !distance(it.thicknessMetres,3.0)||!distance(it.heightMetres,20.0)||
                !it.sillMetres.isFinite()||it.sillMetres<0.0||it.sillMetres+it.heightMetres>wallHeightMetres+.001||!rotation(it.rotationDegrees)
        })return false
        if(furniture.map {it.id}.distinct().size!=furniture.size||furniture.any {
            !identifier(it.id)||!identifier(it.kind)||!point(it.center)||!distance(it.widthMetres,100.0)||
                !distance(it.depthMetres,100.0)||!rotation(it.rotationDegrees)||!polygons(it.polygons)
        })return false
        return stairs.map {it.id}.distinct().size==stairs.size&&stairs.all {
            identifier(it.id)&&it.steps in 1..120&&rotation(it.rotationDegrees)&&polygons(it.polygons,true)&&
                it.parts.size<=122&&it.parts.all {part->part.bottomMetres.isFinite()&&part.bottomMetres>=0.0&&
                    distance(part.heightMetres,20.0)&&part.bottomMetres+part.heightMetres<=floorHeightMetres+.001&&polygons(part.polygons,true)}
        }
    }
    private fun point(value:PlanScenePoint)=value.x.isFinite()&&value.y.isFinite()&&abs(value.x)<=2000.0&&abs(value.y)<=2000.0
    private fun distance(value:Double,maximum:Double)=value.isFinite()&&value>0.0&&value<=maximum
    private fun rotation(value:Double)=value.isFinite()&&value>=0.0&&value<360.0
    private fun identifier(value:String)=value.isNotBlank()&&value.length<=160
}
