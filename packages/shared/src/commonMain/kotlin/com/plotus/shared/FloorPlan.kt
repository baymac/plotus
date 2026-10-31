package com.plotus.shared

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable enum class FloorStep(val segment:String) { SETBACKS("setbacks"), REQUIREMENTS("requirements"), HISTORY("history") }
@Serializable enum class PlanMode { AUTO, CUSTOM }
@Serializable enum class RoomSize { SMALL, MEDIUM, LARGE }
@Serializable enum class Closet { NONE, WARDROBE, WALK_IN }
@Serializable enum class RoomCategory { BASIC, SUPPORT, OUTDOOR }
@Serializable enum class RoomKind(val title:String,val category:RoomCategory,val shared:Boolean=false) {
    BEDROOM("Bedroom",RoomCategory.BASIC), LIVING("Living room",RoomCategory.BASIC), DINING("Dining room",RoomCategory.BASIC),
    KITCHEN("Kitchen",RoomCategory.BASIC), BATHROOM("Bathroom",RoomCategory.BASIC), UTILITY("Utility / laundry",RoomCategory.SUPPORT),
    OFFICE("Office / study",RoomCategory.SUPPORT), GYM("Home gym",RoomCategory.SUPPORT), GAMES("Games room",RoomCategory.SUPPORT),
    PRAYER("Prayer room",RoomCategory.SUPPORT), STORAGE("Storage / pantry",RoomCategory.SUPPORT), LIFT("Lift",RoomCategory.SUPPORT,true),
    STAIRCASE("Staircase",RoomCategory.SUPPORT,true), CUSTOM("Custom space",RoomCategory.SUPPORT), BALCONY("Balcony",RoomCategory.OUTDOOR),
    TERRACE("Terrace",RoomCategory.OUTDOOR,true), POOL("Swimming pool",RoomCategory.OUTDOOR,true), GARDEN("Garden",RoomCategory.OUTDOOR,true),
    PARKING("Parking",RoomCategory.OUTDOOR,true)
}
@Serializable data class PlanRoom(
    val id:String,
    val kind:RoomKind,
    val size:RoomSize=RoomSize.MEDIUM,
    val name:String="",
    val floor:Int?=null,
    val attachedBathroom:Boolean=false,
    val closet:Closet=Closet.NONE,
    val balcony:Boolean=false,
    val utility:Boolean=false,
    val open:Boolean=false,
    val cars:Int=1,
    val bikes:Int=0,
    val covered:Boolean=true,
) {
    val title get()=name.ifBlank {kind.title}
    fun summary()=buildList {
        if(kind==RoomKind.PARKING&&cars+bikes==0)add("Disabled")
        else if(kind==RoomKind.PARKING) {add("$cars ${if(cars==1)"car" else "cars"}");if(bikes>0)add("$bikes ${if(bikes==1)"bike" else "bikes"}");add(if(covered)"covered" else "open")}
        else {add(if(this@PlanRoom.size==RoomSize.MEDIUM)"Automatic sizing" else this@PlanRoom.size.name.lowercase().replaceFirstChar {it.uppercase()});if(attachedBathroom)add("attached bathroom");if(closet!=Closet.NONE)add(if(closet==Closet.WALK_IN)"walk-in closet" else "wardrobe");if(balcony)add("balcony");if(kind==RoomKind.KITCHEN)add(if(open)"open" else "closed");if(utility)add("utility")}
        if(kind!=RoomKind.PARKING||cars+bikes>0)floor?.let {add(floorName(it))}
    }.joinToString(" · ")
}
@Serializable data class PlanHome(
    val id:String,
    val bhk:Int=2,
    val startFloor:Int=0,
    val floorsOccupied:Int=1,
    val vastu:Boolean=true,
    val mode:PlanMode=PlanMode.AUTO,
    val rooms:List<PlanRoom> = emptyList(),
    val notes:String="",
) {
    fun custom()=if(rooms.isEmpty())copy(mode=PlanMode.CUSTOM,rooms=seedRooms(id,bhk)) else copy(mode=PlanMode.CUSTOM)
}
@Serializable data class FloorPlanSetup(
    val units:PlotUnits=PlotUnits.FEET_INCHES,
    val setbackMode:PlanMode=PlanMode.AUTO,
    val sameSetback:Boolean=true,
    val commonSetbackMetres:Double?=null,
    val edgeSetbacksMetres:List<Double?> = emptyList(),
    val buildingFloors:Int=1,
    val homes:List<PlanHome> = listOf(PlanHome("home-1")),
    val sharedRooms:List<PlanRoom> = emptyList(),
    val archivedHomes:List<PlanHome> = emptyList(),
) {
    /** One residence spans the building. Keep earlier residences available in saved data. */
    fun singleResidence():FloorPlanSetup {
        val primary=(homes.firstOrNull() ?: PlanHome("home-1")).copy(startFloor=0,floorsOccupied=buildingFloors)
        val archived=(homes.drop(1)+archivedHomes).filter {it.id!=primary.id}.distinctBy {it.id}
        return copy(homes=listOf(primary),archivedHomes=archived)
    }
    fun withHomeCount(count:Int):FloorPlanSetup {
        require(count in 1..5)
        val pool=(archivedHomes+homes).associateBy {it.id}
        val next=(1..count).map {i->pool["home-$i"] ?: PlanHome("home-$i",startFloor=if(i<=buildingFloors)i-1 else 0)}
        return copy(homes=next,archivedHomes=pool.values.filter {old->next.none {it.id==old.id}})
    }
    fun withHome(home:PlanHome)=copy(homes=homes.map {if(it.id==home.id)home else it})
    fun withRoom(homeID:String?,room:PlanRoom):FloorPlanSetup {
        fun replace(items:List<PlanRoom>)=if(items.any {it.id==room.id})items.map {if(it.id==room.id)room else it} else items+room
        return if(room.kind.shared||homeID==null)copy(sharedRooms=replace(sharedRooms))
        else copy(homes=homes.map {if(it.id==homeID)it.copy(rooms=replace(it.rooms),mode=PlanMode.CUSTOM) else it})
    }
    fun withoutRoom(homeID:String?,id:String):FloorPlanSetup=if(homeID==null) {
        val parking=parkingSelection()
        if(parking?.id==id&&sharedRooms.none {it.kind==RoomKind.PARKING&&it.id!=id&&it.cars+it.bikes>0})withParking(false)
        else copy(sharedRooms=sharedRooms.filterNot {it.id==id})
    }else copy(homes=homes.map {if(it.id==homeID)it.copy(rooms=it.rooms.filterNot {r->r.id==id}) else it})
    fun errors(plot:PlotSetup?,complete:Boolean=false):Map<String,String> = buildMap {
        if(buildingFloors !in 1..6)put("floors","Choose Ground to G+5.")
        if(homes.size !in 1..5||homes.map {it.id}.distinct().size!=homes.size)put("homes","Choose between one and five homes.")
        if(archivedHomes.size>5)put("homes","Check the saved homes.")
        if(complete&&(plot==null||plot.errors(completeBoundary=true,completeRoads=true).isNotEmpty()))put("plot","Complete Plot setup first.")
        fun setback(value:Double?,field:String) {
            if(value!=null&&(!value.isFinite()||value<0||value>35*METRES_PER_FOOT))put(field,"Use a distance between 0 and ${measureLabel(35*METRES_PER_FOOT,units)}.")
            if(complete&&value==null)put(field,"Enter this setback.")
        }
        if(setbackMode==PlanMode.CUSTOM) {
            if(sameSetback)setback(commonSetbackMetres,"setback.all")
            else {
                if(complete&&edgeSetbacksMetres.size!=plot?.lengths?.size)put("setbacks","Enter a setback for every boundary edge.")
                edgeSetbacksMetres.forEachIndexed {i,v->setback(v,"setback.$i")}
            }
        }
        val ids=mutableSetOf<String>()
        fun room(room:PlanRoom,prefix:String,active:Boolean=true) {
            if(!validPlanID(room.id)||!ids.add(room.id))put(prefix,"Check this room’s ID.")
            if(room.name.length>80||room.kind==RoomKind.CUSTOM&&complete&&active&&room.name.isBlank())put(prefix,"Enter a room name, up to 80 characters.")
            if(room.floor!=null&&room.floor !in 0..5)put(prefix,"Choose a valid floor.")
            if(complete&&active&&room.floor!=null&&room.floor>=buildingFloors)put(prefix,"This floor is outside the building.")
            if(room.cars !in 0..8||room.bikes !in 0..16)put(prefix,"Check the number of vehicles.")
            if(room.kind!=RoomKind.BEDROOM&&(room.attachedBathroom||room.closet!=Closet.NONE||room.balcony))put(prefix,"Bedroom features belong to a bedroom.")
            if(room.kind!=RoomKind.KITCHEN&&room.utility)put(prefix,"Add utility space separately for this room.")
        }
        homes.forEachIndexed {i,home->
            val prefix="home.${home.id}"
            if(!validPlanID(home.id)||home.bhk !in 1..6||home.startFloor !in 0..5||home.floorsOccupied !in 1..6)put(prefix,"Check Home ${i+1}.")
            if(complete&&home.startFloor+home.floorsOccupied>buildingFloors)put("$prefix.floors","Choose floors within the building for Home ${i+1}.")
            if(home.notes.length>1000)put("$prefix.notes","Keep additional requirements under 1,000 characters.")
            if(home.rooms.size>40)put(prefix,"Use at most 40 rooms per home.")
            home.rooms.forEach {r->
                room(r,"room.${r.id}",home.mode==PlanMode.CUSTOM)
                if(r.kind.shared)put("room.${r.id}","Move this room to Shared areas.")
                if(complete&&home.mode==PlanMode.CUSTOM&&r.floor!=null&&r.floor !in home.startFloor until home.startFloor+home.floorsOccupied)put("room.${r.id}","Choose one of this home’s floors.")
            }
            if(complete&&home.mode==PlanMode.CUSTOM&&home.rooms.count {it.kind==RoomKind.BEDROOM}!=home.bhk)put("$prefix.bhk",if(homes.size==1)"Add ${home.bhk} bedrooms for ${home.bhk} BHK." else "Home ${i+1} needs ${home.bhk} bedrooms for ${home.bhk} BHK.")
        }
        if(sharedRooms.size>40)put("shared","Use at most 40 shared spaces.")
        sharedRooms.forEach {room(it,"room.${it.id}");if(!it.kind.shared)put("room.${it.id}","Assign this room to a home.")}
    }
}
/** An explicit zero-capacity parking row means disabled; absence keeps the Automatic default. */
fun FloorPlanSetup.parkingSelection():PlanRoom?=sharedRooms.firstOrNull {it.kind==RoomKind.PARKING&&it.cars+it.bikes>0}
    ?: sharedRooms.firstOrNull {it.kind==RoomKind.PARKING}
    ?: if(homes.any {it.mode==PlanMode.AUTO})PlanRoom("plotus-default-parking",RoomKind.PARKING,cars=1,bikes=1,covered=false,floor=0) else null
fun FloorPlanSetup.withParking(enabled:Boolean):FloorPlanSetup {
    val current=parkingSelection()
    return if(enabled) {
        val remaining=sharedRooms.filterNot {it.kind==RoomKind.PARKING&&it.cars+it.bikes==0}
        copy(sharedRooms=if(homes.none {it.mode==PlanMode.AUTO}&&remaining.none {it.kind==RoomKind.PARKING})
            remaining+PlanRoom("plotus-default-parking",RoomKind.PARKING,cars=1,bikes=1,covered=false,floor=0) else remaining)
    }else copy(sharedRooms=sharedRooms.filterNot {it.kind==RoomKind.PARKING}+
        (current ?: PlanRoom("plotus-default-parking",RoomKind.PARKING,covered=false,floor=0)).copy(cars=0,bikes=0))
}
fun validPlanID(value:String)=value.length in 1..80&&value.all {it in 'a'..'z'||it in 'A'..'Z'||it in '0'..'9'||it=='-'||it=='_'}
fun floorName(level:Int)=when(level){0->"Ground";1->"First floor";2->"Second floor";3->"Third floor";4->"Fourth floor";else->"Fifth floor"}
fun buildingLabel(count:Int)=if(count==1)"G" else "G+${count-1}"
fun seedRooms(homeID:String,bhk:Int)=List(bhk) {i->PlanRoom("$homeID-bedroom-${i+1}",RoomKind.BEDROOM,attachedBathroom=i==0)}+listOf(
    PlanRoom("$homeID-living",RoomKind.LIVING),PlanRoom("$homeID-kitchen",RoomKind.KITCHEN,utility=true),
    PlanRoom("$homeID-dining",RoomKind.DINING),PlanRoom("$homeID-bathroom",RoomKind.BATHROOM))
@Serializable data class SavedFloorPlan(val setup:FloorPlanSetup,val plot:PlotSetup)
@Serializable data class SaveFloorPlanRequest(val setup:FloorPlanSetup,val revision:Long)
@Serializable data class PreviewFloorPlanRequest(val setup:FloorPlanSetup,val revision:Long)
@Serializable data class FloorEnvelope(val outlines:List<List<PlotPoint>> = emptyList(),val areaSquareMetres:Double?=null,val edgeSetbacksMetres:List<Double> = emptyList(),val empty:Boolean=false)
@Serializable data class SetbackInput(val mode:PlanMode=PlanMode.AUTO,val same:Boolean=true,val commonMetres:Double?=null,val edgesMetres:List<Double?> = emptyList())
@Serializable data class BuildableAreaKey(val plot:PlotSetup,val setbacks:SetbackInput,val version:Int=1)
@Serializable data class SavedBuildableArea(val key:BuildableAreaKey,val envelope:FloorEnvelope)
/** Display units, storeys and room programmes do not change a site's buildable footprint. */
fun FloorPlanSetup.buildableKey(plot:PlotSetup)=BuildableAreaKey(plot.copy(units=PlotUnits.FEET_INCHES),when {
    setbackMode==PlanMode.AUTO->SetbackInput()
    sameSetback->SetbackInput(PlanMode.CUSTOM,commonMetres=commonSetbackMetres)
    else->SetbackInput(PlanMode.CUSTOM,same=false,edgesMetres=edgeSetbacksMetres)
})
fun FloorPlanSetup.geometrySetup()=FloorPlanSetup(setbackMode=setbackMode,sameSetback=sameSetback,commonSetbackMetres=commonSetbackMetres,edgeSetbacksMetres=edgeSetbacksMetres,homes=listOf(PlanHome("home-1",vastu=false)))
@Serializable data class PlanFinding(val message:String,val field:String?=null)
@Serializable data class FloorPlanPreview(val envelope:FloorEnvelope?=null,val findings:List<PlanFinding> = emptyList(),val canGenerate:Boolean=false)
@Serializable data class StartPlanRequest(val revision:Long,val requestID:String)
@Serializable data class PlanPresentation(val version:Int=1,val pngBase64:String?=null,val width:Int?=null,val height:Int?=null,
    val plotAreaSquareMetres:Double?=null,val perimeterMetres:Double?=null,val builtUpAreaSquareMetres:Double?=null,val carpetAreaSquareMetres:Double?=null,
    val northDegrees:Double=0.0,val roadPosition:String="north",val units:PlotUnits=PlotUnits.FEET_INCHES,val vectorAvailable:Boolean=false)
/** Structured engine measurements survive saved history; absent on older drawings. */
@Serializable data class PlanDrawing(val id:String,val label:String,val pngBase64:String,val width:Int,val height:Int,val presentation:PlanPresentation?=null,val vectorSvgBase64:String?=null,val spaceEfficiency:JsonObject?=null)
@Serializable data class PlanExport(val filename:String,val mimeType:String,val base64:String)
@Serializable data class GeneratedFloorPlan(val id:String,val status:String,val drawings:List<PlanDrawing> = emptyList(),val error:String?=null,val createdAt:String="",val sourceRevision:Long=0,val findings:List<PlanFinding> = emptyList(),val durationMillis:Long?=null,val roadLabels:List<String> = emptyList(),val requirements:FloorPlanSetup?=null)
@Serializable data class PlanGenerations(val plans:List<GeneratedFloorPlan>,val nextBefore:String?=null)
