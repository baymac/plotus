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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.plotus.resources.*
import com.plotus.shared.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import kotlin.uuid.Uuid

@Composable
fun FloorPlanScreen(workspace:FloorWorkspace,id:String,step:FloorStep?,compact:Boolean,onStep:(FloorStep?)->Unit,onPlot:()->Unit,onExit:()->Unit,generationID:String?=null,onGeneration:(String)->Unit={}) {
    val c=LocalPlotusColors.current
    val scope=rememberCoroutineScope()
    val units=LocalMeasurementUnits.current
    val focus=LocalFocusManager.current
    val keyboard=LocalSoftwareKeyboardController.current
    val keyboardVisible=WindowInsets.ime.getBottom(LocalDensity.current)>0
    var leaving by remember {mutableStateOf<(() -> Unit)?>(null)}
    var catalogue by remember {mutableStateOf(false)}
    var editing by remember {mutableStateOf<Pair<String?,PlanRoom>?>(null)}
    var viewedFloor by remember(generationID) {mutableStateOf(0)}
    var exportOpen by remember(generationID) {mutableStateOf(false)}
    val selected=step ?: FloorStep.SETBACKS
    val close:(()->Unit)->Unit={action->focus.clearFocus();keyboard?.hide();if(workspace.dirty)leaving=action else action()}
    val busy=workspace.saving||workspace.generating
    val canSave=workspace.dirty||workspace.needsReview
    fun hideKeyboard(){focus.clearFocus();keyboard?.hide()}
    val select:(FloorStep)->Unit={item->
        hideKeyboard()
        onStep(item)
    }
    LaunchedEffect(workspace,id) {workspace.load(id)}
    LaunchedEffect(workspace,id,generationID,workspace.loading,units) {if(!workspace.loading&&workspace.project!=null&&generationID!=null)workspace.openPlan(generationID)}
    LaunchedEffect(workspace,step,workspace.loading) {if(!workspace.loading&&step==FloorStep.HISTORY&&generationID==null)workspace.loadHistory()}
    LaunchedEffect(step) {editing=null;catalogue=false}
    LaunchedEffect(workspace.footprintKey,workspace.loading,workspace.generating,workspace.generation?.id) {delay(700);workspace.refreshPreview()}
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))) {
        Row(Modifier.fillMaxWidth().height(64.dp).padding(start=if(compact)8.dp else 20.dp,end=16.dp),verticalAlignment=Alignment.CenterVertically) {
            val viewed=workspace.viewingPlan?.takeIf {it.id==generationID} ?: workspace.generation?.takeIf {it.id==generationID}
            val heading=if(generationID!=null)viewed?.createdAt?.let(::planTimestamp) ?: "Floor plan" else workspace.project?.name ?: "Floor plan"
            EditorBack(heading,if(generationID!=null)"Back to history" else "Back to projects",enabled=!workspace.saving) {if(generationID!=null)onStep(FloorStep.HISTORY) else close(onExit)}
            if(keyboardVisible)PlotToolButton(PlotSymbol.KEYBOARD_HIDE,"Hide keyboard") {hideKeyboard()}
            if(generationID!=null&&viewed?.status=="ready")TextButton({exportOpen=!exportOpen},enabled=!workspace.exporting) {
                Text(if(workspace.exporting)"Exporting…" else "Export")
            }
            if(generationID==null&&step==null)TextButton({
                hideKeyboard()
                if(workspace.generation?.status in listOf("ready","blocked","failed","cancelled"))workspace.newAttempt()
                workspace.generate {onGeneration(it.id)}
            },enabled=!busy&&!workspace.loading&&workspace.draft.errors(workspace.project?.plotSetup,true).isEmpty()) {Text(if(busy)"Generating…" else "Generate") }
        }
        if(workspace.loading)Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){CircularProgressIndicator()}
        else if(workspace.project?.plotSetup==null)Column(Modifier.fillMaxSize().padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Text(workspace.error ?: "Complete Plot setup first.")
            PlotusButton("Plot setup",onPlot)
            TextButton({scope.launch {workspace.load(id)}}) {Text("Try again")}
        } else if(generationID!=null) {
            workspace.exportError?.let {message->Row(Modifier.fillMaxWidth().padding(horizontal=16.dp),verticalAlignment=Alignment.CenterVertically){Text(message,Modifier.weight(1f),fontSize=13.sp,color=MaterialTheme.colorScheme.error);PlotToolButton(PlotSymbol.CLOSE,"Dismiss export error"){workspace.dismissExportError()}}}
            val plan=workspace.viewingPlan?.takeIf {it.id==generationID} ?: workspace.generation?.takeIf {it.id==generationID}
            if(workspace.viewLoading&&plan==null)Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center){CircularProgressIndicator()}
            else GenerationView(workspace,plan,compact,Modifier.weight(1f),{workspace.newAttempt();onStep(FloorStep.REQUIREMENTS)},{scope.launch {workspace.openPlan(generationID)}},viewedFloor,{viewedFloor=it},{onStep(FloorStep.HISTORY)})
        } else {
            val inspector:@Composable (Modifier)->Unit={modifier->
                val scroll=rememberScrollState()
                LaunchedEffect(selected,catalogue,editing?.second?.id) {scroll.scrollTo(0)}
                LaunchedEffect(workspace.validationAttempt,workspace.error) {
                    if(workspace.validation.isNotEmpty()||workspace.error!=null)scroll.animateScrollTo(0)
                }
                Column(modifier.background(c.surface)) {
                    Box(Modifier.padding(horizontal=20.dp)) {
                        InspectorHeader(editing?.second?.kind?.title ?: if(catalogue)"Add room" else when(selected){FloorStep.SETBACKS->"Setbacks";FloorStep.HISTORY->"History";else->"Requirements"},!workspace.saving,canSave&&selected!=FloorStep.HISTORY,{
                            hideKeyboard()
                            when {editing!=null->editing=null;catalogue->catalogue=false;else->close {onStep(null)}}
                        },showSave=selected!=FloorStep.HISTORY) {hideKeyboard();workspace.save()}
                    }
                    Column(Modifier.weight(1f).verticalScroll(scroll).padding(20.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
                        workspace.error?.let {FloorError(it)}
                        if(workspace.conflict)TextButton({workspace.discard();scope.launch {workspace.load(id)}}) {Text("Discard and reload")}
                        workspace.validation.values.distinct().forEach {FloorError(it)}
                        when {
                            editing!=null->{
                                val (homeID,previous)=editing!!
                                val room=if(homeID==null)workspace.draft.sharedRooms.find {it.id==previous.id} else workspace.draft.homes.first().rooms.find {it.id==previous.id}
                                if(room!=null)RoomControls(room,workspace.draft,homeID,!busy,{next->workspace.change(workspace.draft.withRoom(homeID,next));editing=homeID to next}) {
                                    workspace.change(workspace.draft.withoutRoom(homeID,room.id));editing=null
                                }
                            }
                            catalogue->RoomCatalogue(!busy) {kind->
                                val homeID=if(kind.shared)null else workspace.draft.homes.first().id
                                val room=PlanRoom("room-${Uuid.random()}",kind)
                                workspace.change(workspace.draft.withRoom(homeID,room));catalogue=false;editing=homeID to room
                            }
                            selected==FloorStep.HISTORY->PlanHistory(workspace,Modifier.fillMaxWidth(),onGeneration)
                            selected==FloorStep.SETBACKS->SetbackControls(workspace,!busy)
                            else->RequirementControls(workspace,!busy,{catalogue=true},{homeID,room->editing=homeID to room})
                        }
                    }
                }
            }

            if(step==null) {
                EditorOverview(compact,Modifier.weight(1f),content={
                    PlanningStages(Modifier.padding(horizontal=16.dp,vertical=8.dp),1,{close(onPlot)})
                    FloorPreview(workspace,compact,Modifier.fillMaxWidth().weight(1f),!keyboardVisible)
                    workspace.generation?.takeIf {it.status in listOf("running","queued","cancelling")}?.let {job->TextButton({onGeneration(job.id)},Modifier.align(Alignment.CenterHorizontally)){Text("View generation in progress")}}
                },options={divider->
                    Column {
                        workspace.error?.let {FloorError(it)}
                        workspace.validation.values.distinct().forEach {FloorError(it)}
                        FloorOptions(!busy,divider,select)
                    }
                })
            }else if(compact) {
                BoxWithConstraints(Modifier.weight(1f)) {
                    val availableHeight=maxHeight
                    Column(Modifier.fillMaxSize()) {
                        val canvasHeight=minOf(if(keyboardVisible)160.dp else 320.dp,availableHeight*(if(keyboardVisible).36f else .46f))
                        FloorPreview(workspace,true,Modifier.fillMaxWidth().height(canvasHeight),!keyboardVisible)
                        HorizontalDivider(color=c.line)
                        inspector(Modifier.weight(1f).fillMaxWidth())
                    }
                }
            }else Row(Modifier.fillMaxWidth().weight(1f)) {
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    PlanningStages(Modifier.padding(horizontal=16.dp,vertical=8.dp),1,{close(onPlot)})
                    FloorPreview(workspace,false,Modifier.weight(1f).fillMaxWidth())
                }
                VerticalDivider(color=c.line)
                inspector(Modifier.width(408.dp).fillMaxHeight())
            }
        }
    }
    if(exportOpen&&generationID!=null) {
        val plan=workspace.viewingPlan?.takeIf {it.id==generationID} ?: workspace.generation?.takeIf {it.id==generationID}
        val drawing=plan?.drawings?.getOrNull(viewedFloor)
        if(plan?.status=="ready"&&drawing!=null)PlanExportDialog(workspace,plan.id,drawing) {exportOpen=false}
    }
    leaving?.let {action->AlertDialog(onDismissRequest={leaving=null},title={Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        Text("Save floor-plan changes?",Modifier.weight(1f),fontSize=20.sp,fontWeight=FontWeight.SemiBold)
        PlotToolButton(PlotSymbol.CLOSE,"Cancel"){leaving=null}
    }},confirmButton={TextButton({leaving=null;workspace.save {action()}}){Text("Save")}},dismissButton={TextButton({workspace.discard();leaving=null;action()}){Text("Discard")}})}
}

@Composable private fun PlanExportDialog(workspace:FloorWorkspace,planID:String,drawing:PlanDrawing,onClose:()->Unit) {
    val c=LocalPlotusColors.current
    var selected by remember(drawing.id) {mutableStateOf("pdf")}
    LaunchedEffect(planID,drawing.id,selected) {workspace.prepareExport(planID,drawing.id,selected)}
    DisposableEffect(workspace,planID,drawing.id) {onDispose {workspace.clearExport()}}
    AlertDialog(onDismissRequest=onClose,containerColor=c.surface,title={
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text("Export floor plan",Modifier.weight(1f),fontSize=20.sp,fontWeight=FontWeight.SemiBold)
            PlotToolButton(PlotSymbol.CLOSE,"Close export options",onClick=onClose)
        }
    },text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        listOf("pdf" to "PDF document","png" to "PNG image","dwg" to "DWG drawing").forEach {(format,title)->
            val available=format!="dwg"||drawing.vectorSvgBase64!=null||drawing.presentation?.vectorAvailable==true
            Row(Modifier.fillMaxWidth().selectable(selected==format,enabled=available,role=Role.RadioButton) {selected=format;workspace.dismissExportError()}.padding(vertical=4.dp),verticalAlignment=Alignment.CenterVertically) {
                RadioButton(selected==format,onClick=null,enabled=available)
                Spacer(Modifier.width(12.dp))
                Column {Text(title,color=if(available)c.ink else c.muted);if(!available)Text("Generate a new plan for DWG",fontSize=12.sp,color=c.muted)}
            }
        }
        workspace.exportError?.let {Text(it,color=MaterialTheme.colorScheme.error,fontSize=13.sp)}
        if(workspace.exportError!=null)TextButton({workspace.prepareExport(planID,drawing.id,selected)}){Text("Try again")}
    }},confirmButton={TextButton({workspace.downloadExport()},enabled=workspace.preparedExportFormat==selected&&!workspace.exporting) {
        Text(if(workspace.exporting)"Preparing…" else "Download ${selected.uppercase()}")
    }})
}

@Composable private fun FloorOptions(enabled:Boolean,showDivider:Boolean,onSelect:(FloorStep)->Unit) {
    val c=LocalPlotusColors.current
    EditorOptions(showDivider) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            EditorOption("Setback",PlotSymbol.BOUNDARY,enabled,Modifier.weight(1f)) {onSelect(FloorStep.SETBACKS)}
            EditorOption("Requirements",PlotSymbol.GRID,enabled,Modifier.weight(1f)) {onSelect(FloorStep.REQUIREMENTS)}
        }
        Row(Modifier.fillMaxWidth().height(48.dp).border(1.dp,c.line,RoundedCornerShape(10.dp)).clickable(role=Role.Button){onSelect(FloorStep.HISTORY)},horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically) {
            PlotSymbol(PlotSymbol.HISTORY,c.muted,Modifier.size(18.dp));Spacer(Modifier.width(8.dp));Text("History",fontSize=13.sp,color=c.muted)
        }
    }
}

@Composable private fun FloorPreview(workspace:FloorWorkspace,compact:Boolean,modifier:Modifier,showToolbar:Boolean=true) {
    val c=LocalPlotusColors.current
    val scope=rememberCoroutineScope()
    val p=workspace.project!!
    val snapshot=remember(p.plotSetup) {p}
    val plot=remember(snapshot) {PlotWorkspace(scope,{snapshot},{_,_->error("Read-only preview")},{})}
    LaunchedEffect(plot){plot.load()}
    Box(modifier.background(c.background)) {
        if(!plot.loading)PlotCanvas(plot,PlotStep.DIRECTION,compact,Modifier.fillMaxSize(),showToolbar=showToolbar,readOnly=true,envelope=workspace.preview?.envelope,showBuildable=true,checkingBuildable=workspace.previewing)
        val home=workspace.draft.homes.first()
        if(showToolbar)Column(Modifier.align(Alignment.TopStart).padding(start=16.dp,end=16.dp,top=64.dp).widthIn(max=360.dp).background(c.surface,RoundedCornerShape(6.dp)).padding(8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text("Requirements: ${if(home.mode==PlanMode.AUTO)"Auto" else "Custom"} · ${home.bhk} BHK · ${buildingLabel(workspace.draft.buildingFloors)} · ${if(home.vastu)"Vastu" else "No Vastu"}",fontSize=11.sp,lineHeight=14.sp,color=c.muted,maxLines=1,overflow=TextOverflow.Ellipsis)
            if(home.mode==PlanMode.CUSTOM)Text((home.rooms+workspace.draft.sharedRooms).groupingBy {it.kind.title}.eachCount().entries.joinToString(" · ") {"${it.value} ${it.key}"},fontSize=11.sp,lineHeight=14.sp,color=c.muted,maxLines=1,overflow=TextOverflow.Ellipsis)
        }
    }
}

@Composable private fun SetbackControls(workspace:FloorWorkspace,enabled:Boolean) {
    val setup=workspace.draft
    val count=workspace.project!!.plotSetup!!.lengths.size
    ModeLine("Setbacks",setup.setbackMode,enabled){mode->
        val auto=workspace.preview?.envelope?.edgeSetbacksMetres.orEmpty()
        workspace.change(if(mode==PlanMode.CUSTOM&&setup.setbackMode==PlanMode.AUTO&&auto.size==count)setup.copy(setbackMode=mode,sameSetback=auto.distinct().size==1,commonSetbackMetres=auto.firstOrNull(),edgeSetbacksMetres=auto) else setup.copy(setbackMode=mode))
    }
    if(setup.setbackMode==PlanMode.CUSTOM) {
        YesNo("Setback same for all sides?",setup.sameSetback,enabled) {same->
            workspace.change(setup.copy(sameSetback=same,
                commonSetbackMetres=setup.commonSetbackMetres ?: setup.edgeSetbacksMetres.firstOrNull(),
                edgeSetbacksMetres=List(count){i->setup.edgeSetbacksMetres.getOrNull(i) ?: setup.commonSetbackMetres}))
        }
        if(setup.sameSetback)Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("All sides",Modifier.width(70.dp),fontSize=14.sp)
            MeasurementInput(setup.commonSetbackMetres,LocalMeasurementUnits.current,"All sides setback",Modifier.weight(1f),enabled,workspace.validation["setback.all"]) {value->
                workspace.change(setup.copy(commonSetbackMetres=value))
            }
        }
        if(!setup.sameSetback)repeat(count) {i->Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(sideName(i,count),Modifier.width(70.dp),fontSize=14.sp)
            MeasurementInput(setup.edgeSetbacksMetres.getOrNull(i),LocalMeasurementUnits.current,"${sideName(i,count)} setback",Modifier.weight(1f),enabled,workspace.validation["setback.$i"]) {value->
                workspace.change(setup.copy(edgeSetbacksMetres=List(count){if(it==i)value else setup.edgeSetbacksMetres.getOrNull(it)}))
            }
        }}
    }
    workspace.previewError?.let {FloorError(it)}
    workspace.preview?.findings?.filter {it.field=="setbacks"}?.forEach {FloorError(it.message)}
}

@Composable private fun RequirementControls(workspace:FloorWorkspace,enabled:Boolean,onAdd:()->Unit,onRoom:(String?,PlanRoom)->Unit) {
    val setup=workspace.draft
    val home=setup.homes.first()
    FloorLabel("Floors")
    NumberChoices((1..6).toList(),setup.buildingFloors,enabled,::buildingLabel) {workspace.change(setup.copy(buildingFloors=it))}
    FloorLabel("BHK")
    NumberChoices((1..6).toList(),home.bhk,enabled) {workspace.change(setup.withHome(home.copy(bhk=it)))}
    YesNo("Vastu compliant",home.vastu,enabled){workspace.change(setup.withHome(home.copy(vastu=it)))}
    ModeLine("Requirements",home.mode,enabled){mode->
        val next=setup.withHome(if(mode==PlanMode.CUSTOM)home.custom() else home.copy(mode=mode))
        workspace.change(if(home.mode==PlanMode.AUTO&&mode==PlanMode.CUSTOM&&setup.sharedRooms.none {it.kind==RoomKind.PARKING})next.withParking(true) else next)
    }
    if(home.mode==PlanMode.CUSTOM) {
        val parking=setup.parkingSelection()
        val parkingEnabled=parking!=null&&parking.cars+parking.bikes>0
        YesNo("Parking",parkingEnabled,enabled){workspace.change(setup.withParking(it))}
        if(parkingEnabled) {
            val supplied=setup.sharedRooms.filter {it.kind==RoomKind.PARKING&&it.cars+it.bikes>0}
            if(supplied.isEmpty())RoomRow(parking,enabled,"1 car · 1 bike if space · open"){onRoom(null,parking)}
            else supplied.forEach {room->RoomRow(room,enabled){onRoom(null,room)}}
        }
        home.rooms.forEach {room->RoomRow(room,enabled){onRoom(home.id,room)}}
        setup.sharedRooms.filter {it.kind!=RoomKind.PARKING}.forEach {room->RoomRow(room,enabled){onRoom(null,room)}}
        PlotusButton("Add room",onAdd,Modifier.fillMaxWidth(),outline=true,enabled=enabled&&home.rooms.size+setup.sharedRooms.size<40)
    }else setup.sharedRooms.filter {it.kind!=RoomKind.PARKING}.forEach {room->RoomRow(room,enabled){onRoom(null,room)}}
    workspace.previewError?.let {FloorError(it)}
    workspace.preview?.findings?.forEach {FloorError(it.message)}
}

@Composable private fun ModeLine(label:String,mode:PlanMode,enabled:Boolean,onChange:(PlanMode)->Unit) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(label,Modifier.weight(1f),fontSize=15.sp,fontWeight=FontWeight.SemiBold)
        ModePicker(mode,enabled,Modifier.width(164.dp),onChange)
    }
}

@Composable private fun RoomRow(room:PlanRoom,enabled:Boolean,description:String=room.summary(),onClick:()->Unit) {
    val c=LocalPlotusColors.current
    Row(Modifier.fillMaxWidth().heightIn(min=64.dp).border(1.dp,c.line,RoundedCornerShape(10.dp)).clickable(enabled,role=Role.Button,onClick=onClick).padding(12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        RoomIcon(room.kind)
        Column(Modifier.weight(1f)) {Text(room.kind.title,fontSize=14.sp,fontWeight=FontWeight.Medium);Text(description,fontSize=11.sp,color=c.muted)}
        PlotSymbol(PlotSymbol.CHEVRON,c.muted,Modifier.size(12.dp))
    }
}

@Composable private fun RoomCatalogue(enabled:Boolean,onChoose:(RoomKind)->Unit) {
    var category by remember {mutableStateOf(RoomCategory.BASIC)}
    Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) {RoomCategory.entries.forEach {item->FloorChip(item.name.lowercase().replaceFirstChar {it.uppercase()},category==item,enabled,Modifier.weight(1f)){category=item}}}
    val available=setOf(RoomKind.BEDROOM,RoomKind.LIVING,RoomKind.DINING,RoomKind.KITCHEN,RoomKind.BATHROOM,RoomKind.UTILITY,RoomKind.OFFICE,RoomKind.PRAYER,RoomKind.STAIRCASE,RoomKind.LIFT,RoomKind.BALCONY,RoomKind.PARKING,RoomKind.GARDEN)
    RoomKind.entries.filter {it.category==category}.forEach {kind->
        val supported=kind in available
        Row(Modifier.fillMaxWidth().heightIn(min=56.dp).clickable(enabled&&supported,role=Role.Button){onChoose(kind)},verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            RoomIcon(kind);Text(kind.title,Modifier.weight(1f),fontSize=14.sp,color=if(supported)LocalPlotusColors.current.ink else LocalPlotusColors.current.muted)
            if(!supported)Text("Coming later",fontSize=11.sp,color=LocalPlotusColors.current.muted) else PlotSymbol(PlotSymbol.BOUNDARY,LocalPlotusColors.current.muted,Modifier.size(16.dp))
        }
    }
}

@Composable private fun RoomControls(room:PlanRoom,setup:FloorPlanSetup,homeID:String?,enabled:Boolean,onChange:(PlanRoom)->Unit,onDelete:()->Unit) {
    val c=LocalPlotusColors.current
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

@Composable private fun PlanHistory(workspace:FloorWorkspace,modifier:Modifier,onOpen:(String)->Unit) {
    val c=LocalPlotusColors.current
    val scope=rememberCoroutineScope()
    Column(modifier.fillMaxWidth().padding(20.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)) {
        if(workspace.history.isEmpty()&&!workspace.historyLoading&&workspace.historyError==null)Box(Modifier.fillMaxWidth().heightIn(min=240.dp),contentAlignment=Alignment.Center) {
            Text("No generations yet",color=c.muted)
        }
        workspace.history.forEach {plan->
            Row(Modifier.widthIn(max=720.dp).fillMaxWidth().heightIn(min=76.dp).background(c.surface,RoundedCornerShape(12.dp))
                .border(1.dp,c.line,RoundedCornerShape(12.dp)).clickable(role=Role.Button,onClickLabel="Open generated plan"){onOpen(plan.id)}.padding(16.dp),
                verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                PlotSymbol(PlotSymbol.HISTORY,c.muted)
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Text(planTimestamp(plan.createdAt),fontSize=14.sp,fontWeight=FontWeight.Medium)
                    if(plan.status in listOf("running","queued","cancelling"))RunningTime(plan) else Text("Duration: ${runDurationLabel(plan.durationMillis)}",fontSize=12.sp,color=c.muted)
                    if(plan.status!="ready")Text(when(plan.status){"cancelling"->"Cancelling…";"running","queued"->"Generating…";"cancelled"->"Cancelled";"blocked"->"No checked layout found";else->"Couldn’t create this plan"},fontSize=12.sp,color=c.muted)
                }
                PlotSymbol(PlotSymbol.BACK,c.muted,Modifier.size(16.dp).graphicsLayer {rotationZ=180f})
            }
        }
        workspace.historyError?.let {FloorError(it);TextButton({scope.launch {workspace.loadHistory()}}){Text("Try again")}}
        if(workspace.historyLoading)CircularProgressIndicator(Modifier.padding(20.dp).size(24.dp))
        else if(workspace.nextHistoryBefore!=null)TextButton({scope.launch {workspace.loadHistory(more=true)}}){Text("Load more")}
    }
}

@Composable private fun GenerationView(workspace:FloorWorkspace,plan:GeneratedFloorPlan?,compact:Boolean,modifier:Modifier,onEdit:()->Unit,onRetry:()->Unit,floor:Int,onFloor:(Int)->Unit,onHistory:()->Unit) {
    val c=LocalPlotusColors.current
    if(plan?.status in listOf("running","queued","cancelling"))Column(modifier.fillMaxWidth().padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
        CircularProgressIndicator();Spacer(Modifier.height(24.dp));FloorTitle(if(plan?.status=="cancelling"||workspace.cancelling)"Cancelling generation" else "Creating your floor plan")
        Spacer(Modifier.height(12.dp));RunningTime(plan!!)
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            TextButton(onHistory){Text("History")}
            TextButton({workspace.cancelGeneration(plan.id)},enabled=!workspace.cancelling){Text(if(workspace.cancelling)"Cancelling…" else "Cancel generation")}
        }
        workspace.error?.let {FloorError(it)}
    } else if(plan?.status=="ready") {
        Column(modifier.fillMaxWidth()) {
            if(plan.drawings.size>1)Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal=16.dp,vertical=8.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                plan.drawings.forEachIndexed {i,_->FloorChip(floorName(i),floor==i,true){onFloor(i)}}
            }
            val drawing=plan.drawings.getOrNull(floor)
            if(drawing!=null)GeneratedPlanCanvas(drawing,compact,Modifier.fillMaxWidth().weight(1f),plan.roadLabels,plan.requirements)
            else Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center){FloorError("This floor drawing couldn’t be opened.")}
        }
    }else Box(modifier.fillMaxWidth().padding(24.dp),contentAlignment=Alignment.Center) {
        Column(Modifier.widthIn(max=420.dp).fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally) {
            Text(if(plan?.status=="cancelled")"Generation cancelled" else if(plan?.status=="blocked")"No checked layout found" else if(plan==null)"Couldn’t open this plan" else "Couldn’t create this plan",
                Modifier.fillMaxWidth(),fontSize=18.sp,fontWeight=FontWeight.SemiBold,textAlign=TextAlign.Center)
            if(plan?.status!="cancelled")Text(plan?.error ?: workspace.viewError ?: workspace.error ?: "Please try again.",Modifier.fillMaxWidth().padding(vertical=16.dp),fontSize=14.sp,color=c.muted,textAlign=TextAlign.Center)
            else Spacer(Modifier.height(16.dp))
            PlotusButton(if(plan==null)"Try again" else "Edit requirements",if(plan==null)onRetry else onEdit)
        }
    }
}

@Composable private fun RoomIcon(kind:RoomKind) {
    val resource=when(kind){RoomKind.BEDROOM->Res.drawable.floor_bed;RoomKind.LIVING->Res.drawable.floor_sofa;RoomKind.KITCHEN->Res.drawable.floor_kitchen;RoomKind.DINING->Res.drawable.floor_dining;RoomKind.BATHROOM->Res.drawable.floor_bath;else->null}
    if(resource!=null)Image(painterResource(resource),null,Modifier.size(20.dp),colorFilter=ColorFilter.tint(LocalPlotusColors.current.muted))
    else PlotSymbol(PlotSymbol.BOUNDARY,LocalPlotusColors.current.muted,Modifier.size(20.dp))
}
@Composable private fun ModePicker(mode:PlanMode,enabled:Boolean,modifier:Modifier=Modifier,onChange:(PlanMode)->Unit) {
    Row(modifier,horizontalArrangement=Arrangement.spacedBy(8.dp)) {PlanMode.entries.forEach {item->FloorChip(if(item==PlanMode.AUTO)"Auto" else "Custom",mode==item,enabled,Modifier.weight(1f)){onChange(item)}}}
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
    Box(modifier.widthIn(min=44.dp).heightIn(min=44.dp).background(if(selected)c.active else c.surface,RoundedCornerShape(8.dp)).border(1.dp,if(selected)c.primary else c.line,RoundedCornerShape(8.dp))
        .selectable(selected,enabled,Role.RadioButton,onClick=onClick).padding(horizontal=10.dp,vertical=8.dp),contentAlignment=Alignment.Center) {
        Text(label,fontSize=12.sp,fontWeight=if(selected)FontWeight.SemiBold else FontWeight.Normal,color=if(selected)c.primary else c.muted,maxLines=1)
    }
}
@Composable private fun FloorTitle(text:String){Text(text,fontSize=18.sp,fontWeight=FontWeight.SemiBold)}
@Composable private fun FloorLabel(text:String){Text(text,fontSize=13.sp,color=LocalPlotusColors.current.muted)}
@Composable private fun FloorError(text:String){Text(text,fontSize=13.sp,color=MaterialTheme.colorScheme.error)}

@Composable private fun RunningTime(plan:GeneratedFloorPlan) {
    var now by remember(plan.id) {mutableStateOf(kotlin.time.Clock.System.now().toEpochMilliseconds())}
    LaunchedEffect(plan.id,plan.status) {while(plan.status in listOf("running","queued","cancelling")){now=kotlin.time.Clock.System.now().toEpochMilliseconds();delay(1000)}}
    Text("Elapsed: ${runDurationLabel(generationElapsedMillis(plan,now))}",fontSize=14.sp,color=LocalPlotusColors.current.muted)
}
