@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package com.plotus

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.plotus.resources.*
import com.plotus.shared.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.compose.resources.painterResource
import kotlin.io.encoding.Base64
import kotlin.uuid.Uuid

@Composable
fun FloorPlanScreen(workspace:FloorWorkspace,id:String,step:FloorStep,compact:Boolean,onStep:(FloorStep)->Unit,onPlot:()->Unit,onExit:()->Unit) {
    val c=LocalPlotusColors.current
    val scope=rememberCoroutineScope()
    val focus=LocalFocusManager.current
    val keyboard=LocalSoftwareKeyboardController.current
    val keyboardVisible=WindowInsets.ime.getBottom(LocalDensity.current)>0
    var leaving by remember {mutableStateOf<(() -> Unit)?>(null)}
    var catalogue by remember {mutableStateOf<String?>(null)}
    var editing by remember {mutableStateOf<Pair<String?,PlanRoom>?>(null)}
    val close:(()->Unit)->Unit={action->focus.clearFocus();keyboard?.hide();if(workspace.dirty)leaving=action else action()}
    val busy=workspace.saving||workspace.generating
    val canSave=workspace.dirty||workspace.needsReview
    LaunchedEffect(workspace,id) {workspace.load(id)}
    LaunchedEffect(workspace.draft,workspace.project?.revision,workspace.loading,workspace.generating,workspace.generation?.id) {delay(700);workspace.refreshPreview()}
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))) {
        Row(Modifier.fillMaxWidth().height(if(keyboardVisible)48.dp else 64.dp).padding(start=if(compact)8.dp else 20.dp,end=16.dp),verticalAlignment=Alignment.CenterVertically) {
            if(compact)PlotToolButton(PlotSymbol.BACK,"Back to projects",enabled=!workspace.saving){close(onExit)}
            Text(workspace.project?.name ?: "Floor plan",Modifier.weight(1f),fontSize=16.sp,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis)
            if(keyboardVisible)PlotToolButton(PlotSymbol.KEYBOARD_HIDE,"Hide keyboard") {focus.clearFocus();keyboard?.hide()}
            TextButton({focus.clearFocus();keyboard?.hide();workspace.save()},enabled=!busy&&canSave) {Text(if(workspace.saving)"Saving…" else "Save",color=if(!busy&&canSave)c.primary else c.muted)}
        }
        if(workspace.loading)Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){CircularProgressIndicator()}
        else if(workspace.project?.plotSetup==null)Column(Modifier.fillMaxSize().padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Text(workspace.error ?: "Complete Plot setup first.")
            PlotusButton("Plot setup",onPlot)
            TextButton({scope.launch {workspace.load(id)}}) {Text("Try again")}
        } else {
            val result=workspace.generation
            if(result!=null||workspace.starting) {
                PlanningStages(Modifier.padding(horizontal=if(compact)16.dp else 20.dp,vertical=8.dp),1,{close(onPlot)})
                GenerationView(workspace,compact,Modifier.weight(1f))
            }else {
                if(compact&&!keyboardVisible)PlanningStages(Modifier.padding(horizontal=16.dp,vertical=8.dp),1,{close(onPlot)})
                val inspector:@Composable (Modifier)->Unit={modifier->
                    val scroll=rememberScrollState()
                    LaunchedEffect(step,catalogue,editing?.second?.id) {scroll.scrollTo(0)}
                    LaunchedEffect(workspace.validationAttempt,workspace.error) {
                        if(workspace.validation.isNotEmpty()||workspace.error!=null)scroll.animateScrollTo(0)
                    }
                    Column(modifier.background(c.surface)) {
                        if(editing==null&&catalogue==null)Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=8.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            FloorStep.entries.forEach {item->FloorChip(item.segment.replaceFirstChar {it.uppercase()},step==item,!busy,Modifier.weight(1f)) {
                                focus.clearFocus();keyboard?.hide()
                                if(item==FloorStep.REQUIREMENTS)workspace.save(setbacks=true) {onStep(item)} else onStep(item)
                            }}
                        } else Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically) {
                            Text(if(editing!=null)editing!!.second.title else "Add room",Modifier.weight(1f).padding(start=8.dp),fontWeight=FontWeight.SemiBold)
                            TextButton({focus.clearFocus();keyboard?.hide();editing=null;catalogue=null}) {Text("Done")}
                        }
                        HorizontalDivider(color=c.line)
                        Column(Modifier.weight(1f).verticalScroll(scroll).padding(20.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
                            workspace.error?.let {FloorError(it)}
                            if(workspace.conflict)TextButton({workspace.discard();scope.launch {workspace.load(id)}}) {Text("Discard and reload")}
                            workspace.validation.values.distinct().forEach {FloorError(it)}
                            if(workspace.project?.floorPlan?.plot?.let {it!=workspace.project?.plotSetup}==true)
                                FloorError("The plot changed. Review and save setbacks before generating.")
                            when {
                                editing!=null->{
                                    val (homeID,selected)=editing!!
                                    val room=if(homeID==null)workspace.draft.sharedRooms.find {it.id==selected.id} else workspace.draft.homes.find {it.id==homeID}?.rooms?.find {it.id==selected.id}
                                    if(room!=null)RoomControls(room,workspace.draft,homeID,!busy,{next->workspace.change(workspace.draft.withRoom(homeID,next));editing=homeID to next}) {
                                        workspace.change(workspace.draft.withoutRoom(homeID,room.id));editing=null
                                    }
                                }
                                catalogue!=null->RoomCatalogue(!busy,catalogue=="shared") {kind->
                                    val homeID=catalogue!!.takeUnless {kind.shared||it=="shared"}
                                    val room=PlanRoom("room-${Uuid.random()}",kind)
                                    workspace.change(workspace.draft.withRoom(homeID,room));catalogue=null;editing=homeID to room
                                }
                                step==FloorStep.SETBACKS->SetbackControls(workspace,!busy)
                                else->RequirementControls(workspace,!busy,{catalogue=it},{homeID,room->editing=homeID to room})
                            }
                        }
                        if(editing==null&&catalogue==null) {
                            HorizontalDivider(color=c.line)
                            Row(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=12.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                                PlotusButton("Back",{focus.clearFocus();keyboard?.hide();if(step==FloorStep.SETBACKS)close(onPlot) else onStep(FloorStep.SETBACKS)},Modifier.width(90.dp),outline=true,enabled=!busy)
                                PlotusButton(if(workspace.saving)"Saving…" else if(step==FloorStep.SETBACKS)"Next" else "Generate plan",{
                                    focus.clearFocus();keyboard?.hide()
                                    if(step==FloorStep.SETBACKS)workspace.save(setbacks=true) {onStep(FloorStep.REQUIREMENTS)} else workspace.generate()
                                },Modifier.weight(1f),enabled=!busy)
                            }
                        }
                    }
                }
                if(compact)BoxWithConstraints(Modifier.weight(1f)) {
                    val availableHeight=maxHeight
                    Column(Modifier.fillMaxSize()) {
                        val canvasHeight=minOf(if(keyboardVisible)140.dp else 270.dp,availableHeight*if(keyboardVisible).28f else .40f)
                        FloorPreview(workspace,true,Modifier.fillMaxWidth().height(canvasHeight))
                        HorizontalDivider(color=c.line)
                        inspector(Modifier.weight(1f).fillMaxWidth())
                    }
                }else Row(Modifier.fillMaxWidth().weight(1f)) {
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        PlanningStages(Modifier.padding(horizontal=20.dp,vertical=8.dp),1,{close(onPlot)})
                        FloorPreview(workspace,false,Modifier.fillMaxWidth().weight(1f))
                    }
                    VerticalDivider(color=c.line)
                    inspector(Modifier.width(408.dp).fillMaxHeight())
                }
            }
        }
    }
    leaving?.let {action->AlertDialog(onDismissRequest={leaving=null},title={Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        Text("Save floor-plan changes?",Modifier.weight(1f),fontSize=20.sp,fontWeight=FontWeight.SemiBold)
        PlotToolButton(PlotSymbol.CLOSE,"Cancel"){leaving=null}
    }},confirmButton={TextButton({leaving=null;workspace.save {action()}}){Text("Save")}},dismissButton={TextButton({workspace.discard();leaving=null;action()}){Text("Discard")}})}
}

@Composable private fun FloorPreview(workspace:FloorWorkspace,compact:Boolean,modifier:Modifier) {
    val c=LocalPlotusColors.current
    val scope=rememberCoroutineScope()
    val p=workspace.project!!
    val snapshot=remember(p.plotSetup,workspace.draft.units) {p.copy(plotSetup=p.plotSetup!!.copy(units=workspace.draft.units))}
    val plot=remember(snapshot) {PlotWorkspace(scope,{snapshot},{_,_->error("Read-only preview")},{})}
    LaunchedEffect(plot){plot.load()}
    Column(modifier.background(c.background)) {
        val envelope=workspace.preview?.envelope
        val area=envelope?.areaSquareMetres
        val label=when {
            workspace.previewing->"Checking buildable area…"
            envelope?.empty==true->"No buildable area"
            area!=null->"Buildable · "+if(workspace.draft.units==PlotUnits.METRES)"${decimal(area)} m²" else areaLabel((area/(METRES_PER_FOOT*METRES_PER_FOOT)).toInt())
            else->"Buildable area preview"
        }
        Box(Modifier.fillMaxWidth().height(36.dp),contentAlignment=Alignment.Center) {
            Text(label,Modifier.background(c.surface,RoundedCornerShape(8.dp)).padding(horizontal=12.dp,vertical=6.dp),fontSize=12.sp,color=if(envelope?.empty==true)MaterialTheme.colorScheme.error else c.muted)
        }
        Box(Modifier.fillMaxWidth().weight(1f)) {
            if(!plot.loading)PlotCanvas(plot,PlotStep.DIRECTION,compact,Modifier.fillMaxSize(),showToolbar=false,readOnly=true,envelope=workspace.preview?.envelope)
        }
    }
}

@Composable private fun SetbackControls(workspace:FloorWorkspace,enabled:Boolean) {
    val setup=workspace.draft
    FloorTitle("Setbacks")
    ModePicker(setup.setbackMode,enabled){workspace.change(setup.copy(setbackMode=it))}
    if(setup.setbackMode==PlanMode.CUSTOM) {
        FloorLabel("Units")
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {PlotUnits.entries.forEach {unit->FloorChip(if(unit==PlotUnits.FEET_INCHES)"Feet & inches" else "Metres",setup.units==unit,enabled,Modifier.weight(1f)){workspace.change(setup.copy(units=unit))}}}
        YesNo("Same on every side?",setup.sameSetback,enabled){workspace.change(setup.copy(sameSetback=it,edgeSetbacksMetres=List(workspace.project!!.plotSetup!!.lengths.size){i->setup.edgeSetbacksMetres.getOrNull(i)}))}
        if(setup.sameSetback) {
            FloorLabel("All sides")
            MeasurementInput(setup.commonSetbackMetres,setup.units,"All sides setback",Modifier.fillMaxWidth(),enabled,workspace.validation["setback.all"]) {workspace.change(setup.copy(commonSetbackMetres=it))}
        }else {
            val count=workspace.project!!.plotSetup!!.lengths.size
            repeat(count) {i->Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(sideName(i,count),Modifier.width(56.dp),fontSize=14.sp)
                MeasurementInput(setup.edgeSetbacksMetres.getOrNull(i),setup.units,"${sideName(i,count)} setback",Modifier.weight(1f),enabled,workspace.validation["setback.$i"]) {value->
                    workspace.change(setup.copy(edgeSetbacksMetres=List(count){if(it==i)value else setup.edgeSetbacksMetres.getOrNull(it)}))
                }
            }}
        }
    } else Text("Setbacks are calculated from the plot and road-facing sides.",fontSize=13.sp,color=LocalPlotusColors.current.muted)
    workspace.previewError?.let {FloorError(it)}
    workspace.preview?.findings?.filter {it.field=="setbacks"}?.forEach {FloorError(it.message)}
    HorizontalDivider(color=LocalPlotusColors.current.line)
    FloorTitle("Building")
    FloorLabel("Floors")
    NumberChoices((1..6).toList(),setup.buildingFloors,enabled,::buildingLabel) {workspace.change(setup.copy(buildingFloors=it))}
    FloorLabel("Homes")
    NumberChoices((1..5).toList(),setup.homes.size,enabled){workspace.change(setup.withHomeCount(it))}
}

@Composable private fun RequirementControls(workspace:FloorWorkspace,enabled:Boolean,onAdd:(String)->Unit,onRoom:(String?,PlanRoom)->Unit) {
    val c=LocalPlotusColors.current
    val setup=workspace.draft
    FloorTitle("Requirements")
    setup.homes.forEachIndexed {i,home->
        Column(Modifier.fillMaxWidth().border(1.dp,c.line,RoundedCornerShape(12.dp)).padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Text("Home ${i+1}",fontSize=16.sp,fontWeight=FontWeight.SemiBold)
            if(setup.buildingFloors>1||setup.homes.size>1) {
                FloorLabel("Starts on")
                NumberChoices((0 until setup.buildingFloors).toList(),home.startFloor,enabled,{if(it==0)"G" else "F$it"}) {workspace.change(setup.withHome(home.copy(startFloor=it)))}
                FloorLabel("Floors occupied")
                NumberChoices((1..setup.buildingFloors).toList(),home.floorsOccupied,enabled) {workspace.change(setup.withHome(home.copy(floorsOccupied=it)))}
            }
            FloorLabel("BHK")
            NumberChoices((1..6).toList(),home.bhk,enabled) {workspace.change(setup.withHome(home.copy(bhk=it)))}
            YesNo("Vastu",home.vastu,enabled){workspace.change(setup.withHome(home.copy(vastu=it)))}
            FloorLabel("Requirements")
            ModePicker(home.mode,enabled){mode->workspace.change(setup.withHome(if(mode==PlanMode.CUSTOM)home.custom() else home.copy(mode=mode)))}
            if(home.mode==PlanMode.CUSTOM) {
                home.rooms.forEach {room->RoomRow(room,enabled){onRoom(home.id,room)}}
                PlotusButton("Add room",{onAdd(home.id)},Modifier.fillMaxWidth(),outline=true,enabled=enabled&&home.rooms.size<40)
            }
        }
    }
    FloorTitle("Shared areas")
    setup.sharedRooms.forEach {room->RoomRow(room,enabled){onRoom(null,room)}}
    PlotusButton("Add shared space",{onAdd("shared")},Modifier.fillMaxWidth(),outline=true,enabled=enabled&&setup.sharedRooms.size<40)
    workspace.previewError?.let {FloorError(it)}
    workspace.preview?.findings?.forEach {FloorError(it.message)}
}

@Composable private fun RoomRow(room:PlanRoom,enabled:Boolean,onClick:()->Unit) {
    val c=LocalPlotusColors.current
    Row(Modifier.fillMaxWidth().heightIn(min=64.dp).border(1.dp,c.line,RoundedCornerShape(10.dp)).clickable(enabled,role=Role.Button,onClick=onClick).padding(12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        RoomIcon(room.kind)
        Column(Modifier.weight(1f)) {Text(room.title,fontSize=14.sp,fontWeight=FontWeight.Medium);Text(room.summary(),fontSize=11.sp,color=c.muted)}
        PlotSymbol(PlotSymbol.CHEVRON,c.muted,Modifier.size(12.dp))
    }
}

@Composable private fun RoomCatalogue(enabled:Boolean,sharedOnly:Boolean,onChoose:(RoomKind)->Unit) {
    var category by remember {mutableStateOf(if(sharedOnly)RoomCategory.OUTDOOR else RoomCategory.BASIC)}
    Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) {RoomCategory.entries.filter {item->!sharedOnly||RoomKind.entries.any {it.category==item&&it.shared}}.forEach {item->FloorChip(item.name.lowercase().replaceFirstChar {it.uppercase()},category==item,enabled,Modifier.weight(1f)){category=item}}}
    val available=setOf(RoomKind.BEDROOM,RoomKind.LIVING,RoomKind.DINING,RoomKind.KITCHEN,RoomKind.BATHROOM,RoomKind.UTILITY,RoomKind.OFFICE,RoomKind.PRAYER,RoomKind.STAIRCASE,RoomKind.LIFT,RoomKind.BALCONY,RoomKind.PARKING,RoomKind.GARDEN)
    RoomKind.entries.filter {it.category==category&&(!sharedOnly||it.shared)}.forEach {kind->
        val supported=kind in available
        Row(Modifier.fillMaxWidth().heightIn(min=56.dp).clickable(enabled&&supported,role=Role.Button){onChoose(kind)},verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            RoomIcon(kind);Text(kind.title,Modifier.weight(1f),fontSize=14.sp,color=if(supported)LocalPlotusColors.current.ink else LocalPlotusColors.current.muted)
            if(!supported)Text("Coming later",fontSize=11.sp,color=LocalPlotusColors.current.muted) else PlotSymbol(PlotSymbol.BOUNDARY,LocalPlotusColors.current.muted,Modifier.size(16.dp))
        }
    }
}

@Composable private fun RoomControls(room:PlanRoom,setup:FloorPlanSetup,homeID:String?,enabled:Boolean,onChange:(PlanRoom)->Unit,onDelete:()->Unit) {
    val c=LocalPlotusColors.current
    LabeledInput("Name",room.name,{onChange(room.copy(name=it.take(80)))},enabled=enabled,capitalization=KeyboardCapitalization.Words)
    Text("Room dimensions are sized automatically to fit the plot.",color=c.muted,fontSize=13.sp)
    val home=setup.homes.find {it.id==homeID}
    val floors=if(home==null)(0 until setup.buildingFloors).toList() else (home.startFloor until minOf(setup.buildingFloors,home.startFloor+home.floorsOccupied)).toList()
    FloorLabel("Floor")
    Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
        FloorChip("Auto",room.floor==null,enabled,Modifier.weight(1f)){onChange(room.copy(floor=null))}
        floors.forEach {level->FloorChip(if(level==0)"G" else "F$level",room.floor==level,enabled,Modifier.weight(1f)){onChange(room.copy(floor=level))}}
    }
    if(room.kind==RoomKind.BEDROOM) {
        YesNo("Attached bathroom",room.attachedBathroom,enabled){onChange(room.copy(attachedBathroom=it))}
        YesNo("Walk-in closet",room.closet!=Closet.NONE,enabled){onChange(room.copy(closet=if(it)Closet.WALK_IN else Closet.NONE))}
        YesNo("Balcony",room.balcony,enabled){onChange(room.copy(balcony=it))}
    }
    if(room.kind==RoomKind.KITCHEN)YesNo("Utility / laundry",room.utility,enabled){onChange(room.copy(utility=it))}
    if(room.kind==RoomKind.PARKING) {
        FloorLabel("Cars");NumberChoices((0..4).toList(),room.cars,enabled){onChange(room.copy(cars=it))}
        FloorLabel("Bikes");NumberChoices((0..4).toList(),room.bikes,enabled){onChange(room.copy(bikes=it))}
        YesNo("Covered",room.covered,enabled){onChange(room.copy(covered=it))}
    }
    TextButton(onDelete,enabled=enabled) {Text("Delete ${room.kind.title.lowercase()}",color=MaterialTheme.colorScheme.error)}
}

@Composable private fun GenerationView(workspace:FloorWorkspace,compact:Boolean,modifier:Modifier) {
    val c=LocalPlotusColors.current
    val plan=workspace.generation
    if(workspace.generating)Column(modifier.fillMaxWidth().padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
        CircularProgressIndicator();Spacer(Modifier.height(24.dp));FloorTitle("Creating your floor plan")
        Text("You can leave this screen and come back.",Modifier.padding(top=8.dp),fontSize=14.sp,color=c.muted)
        workspace.error?.let {FloorError(it)}
    } else if(plan?.status=="ready") {
        var floor by remember(plan.id) {mutableStateOf(0)}
        val drawing=plan.drawings.getOrNull(floor)
        val bitmap=remember(drawing?.pngBase64){drawing?.let {runCatching {Base64.decode(it.pngBase64).decodeToImageBitmap()}.getOrNull()}}
        Column(modifier.fillMaxWidth().padding(if(compact)16.dp else 24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                FloorTitle("Floor plan")
                Spacer(Modifier.weight(1f))
                TextButton({workspace.newAttempt()}) {Text("Requirements")}
            }
            if(plan.sourceRevision!=workspace.project?.revision)Text("This plan uses an earlier saved version of the project.",fontSize=12.sp,color=c.muted)
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {plan.drawings.forEachIndexed {i,item->FloorChip(item.label,floor==i,true){floor=i}}}
            if(bitmap!=null)Image(bitmap,drawing?.label,Modifier.fillMaxWidth().weight(1f).background(Color.White,RoundedCornerShape(12.dp)),contentScale=ContentScale.Fit)
            else Box(Modifier.weight(1f),contentAlignment=Alignment.Center){FloorError("This floor drawing couldn’t be opened.")}
        }
    }else Column(modifier.fillMaxWidth().padding(24.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally) {
        FloorTitle(if(plan?.status=="blocked")"No checked layout found" else "Couldn’t create this plan")
        Text(plan?.error ?: workspace.error ?: "Please try again.",Modifier.padding(vertical=16.dp),fontSize=14.sp,color=c.muted)
        PlotusButton("Edit requirements",{workspace.newAttempt()})
    }
}

@Composable private fun RoomIcon(kind:RoomKind) {
    val resource=when(kind){RoomKind.BEDROOM->Res.drawable.floor_bed;RoomKind.LIVING->Res.drawable.floor_sofa;RoomKind.KITCHEN->Res.drawable.floor_kitchen;RoomKind.DINING->Res.drawable.floor_dining;RoomKind.BATHROOM->Res.drawable.floor_bath;else->null}
    if(resource!=null)Image(painterResource(resource),null,Modifier.size(20.dp),colorFilter=ColorFilter.tint(LocalPlotusColors.current.muted))
    else PlotSymbol(PlotSymbol.BOUNDARY,LocalPlotusColors.current.muted,Modifier.size(20.dp))
}
@Composable private fun ModePicker(mode:PlanMode,enabled:Boolean,onChange:(PlanMode)->Unit) {
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {PlanMode.entries.forEach {item->FloorChip(if(item==PlanMode.AUTO)"Auto" else "Custom",mode==item,enabled,Modifier.weight(1f)){onChange(item)}}}
}
@Composable private fun YesNo(label:String,value:Boolean,enabled:Boolean,onChange:(Boolean)->Unit) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(label,Modifier.weight(1f),fontSize=14.sp)
        FloorChip("Yes",value,enabled){onChange(true)};FloorChip("No",!value,enabled){onChange(false)}
    }
}
@Composable private fun NumberChoices(values:List<Int>,selected:Int,enabled:Boolean,label:(Int)->String={it.toString()},onChange:(Int)->Unit) {
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp)) {values.forEach {i->FloorChip(label(i),i==selected,enabled,Modifier.weight(1f)){onChange(i)}}}
}
@Composable private fun FloorChip(label:String,selected:Boolean,enabled:Boolean,modifier:Modifier=Modifier,onClick:()->Unit) {
    val c=LocalPlotusColors.current
    Box(modifier.heightIn(min=44.dp).background(if(selected)c.active else c.surface,RoundedCornerShape(8.dp)).border(1.dp,if(selected)c.primary else c.line,RoundedCornerShape(8.dp))
        .selectable(selected,enabled,Role.RadioButton,onClick=onClick).padding(horizontal=10.dp,vertical=8.dp),contentAlignment=Alignment.Center) {
        Text(label,fontSize=12.sp,fontWeight=if(selected)FontWeight.SemiBold else FontWeight.Normal,color=if(selected)c.primary else c.muted,maxLines=1)
    }
}
@Composable private fun FloorTitle(text:String){Text(text,fontSize=18.sp,fontWeight=FontWeight.SemiBold)}
@Composable private fun FloorLabel(text:String){Text(text,fontSize=13.sp,color=LocalPlotusColors.current.muted)}
@Composable private fun FloorError(text:String){Text(text,fontSize=13.sp,color=MaterialTheme.colorScheme.error)}
