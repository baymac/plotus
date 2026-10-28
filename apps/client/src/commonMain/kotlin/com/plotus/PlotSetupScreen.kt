package com.plotus

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.plotus.shared.*
import kotlinx.coroutines.launch
import kotlin.math.*

@Composable
fun PlotSetupScreen(workspace: PlotWorkspace, step: PlotStep?, compact: Boolean, onStep: (PlotStep?) -> Unit, onExit: () -> Unit, onFloor:()->Unit={}) {
    val c=LocalPlotusColors.current
    val scope=rememberCoroutineScope()
    LaunchedEffect(workspace) { workspace.load() }
    var leaving by remember { mutableStateOf<(() -> Unit)?>(null) }
    var reloadOpen by remember { mutableStateOf(false) }
    val close: (() -> Unit) -> Unit = { action -> if(workspace.dirty)leaving=action else action() }
    val selected=step ?: PlotStep.BOUNDARY
    val editing=compact&&step!=null
    val focus=LocalFocusManager.current
    val keyboardController=LocalSoftwareKeyboardController.current
    val keyboard=WindowInsets.ime.getBottom(LocalDensity.current)>0
    // safeDrawing includes the keyboard. Applying imePadding as well duplicates it on iOS.
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))) {
        Row(Modifier.fillMaxWidth().height(if(editing)48.dp else 64.dp).padding(start=if(compact)8.dp else 20.dp,end=16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            EditorBack(step?.title() ?: workspace.project?.name ?: "Plot setup",if(step==null)"Back to projects" else "Back to plot",enabled=!workspace.saving) {
                focus.clearFocus();keyboardController?.hide()
                if(step!=null)onStep(null) else close(onExit)
            }
            if(keyboard)PlotToolButton(PlotSymbol.KEYBOARD_HIDE,"Hide keyboard",enabled=!workspace.saving) {focus.clearFocus();keyboardController?.hide()}
            TextButton({focus.clearFocus();keyboardController?.hide();workspace.save(requireBoundary=step==PlotStep.BOUNDARY,requireRoads=step==PlotStep.ROADS)},enabled=workspace.project!=null&&!workspace.loading&&!workspace.saving&&workspace.dirty) {
                Text(if(workspace.saving)"Saving…" else "Save",fontSize=14.sp,color=if(workspace.dirty&&!workspace.saving)c.primary else c.muted)
            }
        }
        if(workspace.loading)Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) {CircularProgressIndicator()}
        else if(workspace.project==null)Column(Modifier.fillMaxSize().padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp),horizontalAlignment=Alignment.CenterHorizontally) {
            Text(workspace.error ?: "This project is unavailable.")
            PlotusButton("Try again",{scope.launch {workspace.load()}})
            TextButton(onExit) {Text("Back to projects")}
        }
        else if(compact) {
            MobilePlotEditor(workspace,step,onStep,Modifier.weight(1f),{workspace.save(requireBoundary=true,requireRoads=true,onSaved=onFloor)}) {reloadOpen=true}
        } else Row(Modifier.fillMaxWidth().weight(1f)) {
            Column(Modifier.weight(1f).fillMaxHeight()) {
                PlanningStages(Modifier.padding(horizontal=20.dp).padding(vertical=8.dp),onFloor={workspace.save(requireBoundary=true,requireRoads=true,onSaved=onFloor)})
                PlotCanvas(workspace,selected,false,Modifier.fillMaxWidth().weight(1f))
            }
            VerticalDivider(color=c.line)
            PlotInspector(workspace,selected,{onStep(it)},{close(onExit)},onFloor,Modifier.width(408.dp).fillMaxHeight(),{reloadOpen=true})
        }
    }
    leaving?.let { action ->
        AlertDialog(onDismissRequest={if(!workspace.saving)leaving=null},title={
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text("Save plot changes?",Modifier.weight(1f),fontSize=20.sp,fontWeight=FontWeight.SemiBold)
                PlotToolButton(PlotSymbol.CLOSE,"Cancel",enabled=!workspace.saving) {leaving=null}
            }
        },confirmButton={TextButton({leaving=null;workspace.save {action()}},enabled=!workspace.saving) {Text("Save")}},
            dismissButton={TextButton({workspace.discard();leaving=null;action()},enabled=!workspace.saving) {Text("Discard")}})
    }
    if(reloadOpen)AlertDialog(onDismissRequest={reloadOpen=false},title={Text("Reload saved plot?")},text={Text("Your unsaved measurements will be replaced by the latest saved plot.")},
        confirmButton={TextButton({reloadOpen=false;scope.launch {workspace.load(force=true)}}) {Text("Reload")}},dismissButton={TextButton({reloadOpen=false}) {Text("Cancel")}})
}

@Composable
private fun MobilePlotEditor(workspace:PlotWorkspace,step:PlotStep?,onStep:(PlotStep?)->Unit,modifier:Modifier,onFloor:()->Unit,onReload:()->Unit) {
    val c=LocalPlotusColors.current
    val keyboard=WindowInsets.ime.getBottom(LocalDensity.current)>0
    val focus=LocalFocusManager.current
    val keyboardController=LocalSoftwareKeyboardController.current
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val availableHeight=maxHeight
        Column(Modifier.fillMaxSize()) {
            if(step==null) {
                PlanningStages(Modifier.padding(horizontal=16.dp,vertical=8.dp),onFloor=onFloor)
                PlotCanvas(workspace,null,true,Modifier.fillMaxWidth().weight(1f))
                PlotProblems(workspace,Modifier.padding(horizontal=16.dp,vertical=4.dp),onReload)
            } else {
                val canvasHeight=minOf(if(keyboard)160.dp else 280.dp,availableHeight*(if(keyboard).40f else .43f))
                PlotCanvas(workspace,step,true,Modifier.fillMaxWidth().height(canvasHeight),showToolbar=!keyboard)
                HorizontalDivider(color=c.line)
                PlotFields(workspace,step,Modifier.weight(1f),onReload)
            }
            if(step==null) {
                HorizontalDivider(color=c.line)
                Row(Modifier.fillMaxWidth().background(c.surface).padding(horizontal=16.dp,vertical=6.dp).height(60.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    PlotStep.entries.forEach { item ->
                        Column(Modifier.weight(1f).fillMaxHeight().background(c.surface,RoundedCornerShape(10.dp))
                            .border(1.dp,c.line,RoundedCornerShape(10.dp)).clickable(enabled=!workspace.saving,role=Role.Button) {onStep(item)},
                            horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                            PlotSymbol(when(item){PlotStep.BOUNDARY->PlotSymbol.BOUNDARY;PlotStep.DIRECTION->PlotSymbol.COMPASS;PlotStep.ROADS->PlotSymbol.ROAD},c.muted,Modifier.size(18.dp))
                            Text(item.title(),fontSize=12.sp,color=c.muted)
                        }
                    }
                }
                if(!keyboard)SketchUpload(workspace,Modifier.background(c.surface).padding(horizontal=16.dp).padding(bottom=12.dp)) {onStep(PlotStep.BOUNDARY)}
            }
        }
    }
}

private fun PlotStep.title()=segment.replaceFirstChar {it.uppercase()}

@Composable
internal fun PlanningStages(modifier: Modifier, selected:Int=0,onPlot:()->Unit={},onFloor:()->Unit={}) {
    val c=LocalPlotusColors.current
    Row(modifier.fillMaxWidth().height(44.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        listOf("Plot setup","Floor plan").forEachIndexed { i,label ->
            Row(Modifier.weight(1f).fillMaxHeight().background(if(i==selected)c.active else c.background,RoundedCornerShape(8.dp)).selectable(i==selected,role=Role.Tab,onClick=if(i==0)onPlot else onFloor).padding(8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(24.dp).background(if(i==selected)c.primary else c.quiet,CircleShape),contentAlignment=Alignment.Center) {Text("${i+1}",fontSize=12.sp,color=if(i==selected)c.onPrimary else c.muted)}
                Text(label,fontSize=14.sp,fontWeight=if(i==selected)FontWeight.SemiBold else FontWeight.Normal,color=if(i==selected)c.primary else c.muted)
            }
        }
    }
}

@Composable
private fun PlotInspector(workspace: PlotWorkspace,step: PlotStep,onStep:(PlotStep)->Unit,onBack:()->Unit,onComplete:()->Unit,modifier:Modifier,onReload:()->Unit) {
    val c=LocalPlotusColors.current
    Column(modifier.background(c.surface)) {
        Row(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=8.dp).height(44.dp),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
            PlotStep.entries.forEach { item ->
                Box(Modifier.weight(1f).fillMaxHeight().background(if(step==item)c.quiet else c.surface,RoundedCornerShape(8.dp)).selectable(step==item,enabled=!workspace.saving,role=Role.Tab) {onStep(item)},contentAlignment=Alignment.Center) {
                    Text(item.title(),fontSize=13.sp,color=if(step==item)c.ink else c.muted)
                }
            }
        }
        SketchUpload(workspace,Modifier.padding(horizontal=20.dp).padding(bottom=8.dp)) {onStep(PlotStep.BOUNDARY)}
        HorizontalDivider(color=c.line)
        PlotProblems(workspace,Modifier.padding(horizontal=20.dp,vertical=8.dp),onReload)
        PlotFields(workspace,step,Modifier.weight(1f))
        HorizontalDivider(color=c.line)
        Row(Modifier.fillMaxWidth().padding(20.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            PlotusButton("Back",{if(step==PlotStep.BOUNDARY)onBack() else onStep(PlotStep.entries[step.ordinal-1])},Modifier.width(100.dp).height(48.dp),outline=true,enabled=!workspace.saving)
            PlotusButton(if(workspace.saving)"Saving…" else if(step==PlotStep.ROADS)"Floor plan" else "Next",{
                workspace.save(requireBoundary=true,requireRoads=step==PlotStep.ROADS) {if(step==PlotStep.ROADS)onComplete() else onStep(PlotStep.entries[step.ordinal+1])}
            },Modifier.weight(1f).height(48.dp),enabled=!workspace.saving)
        }
    }
}

@Composable
private fun PlotFields(workspace:PlotWorkspace,step:PlotStep,modifier:Modifier,onReload:(()->Unit)?=null) {
    val c=LocalPlotusColors.current
    val scroll=remember(step) {ScrollState(0)}
    LaunchedEffect(workspace.validationAttempt,workspace.error,workspace.validation["boundary"]) {if(workspace.validation.isNotEmpty()||workspace.error!=null)scroll.scrollTo(0)}
    Column(modifier.fillMaxWidth().background(c.surface).verticalScroll(scroll).padding(horizontal=20.dp,vertical=16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        if(onReload!=null)PlotProblems(workspace,onReload=onReload)
        when(step) {
            PlotStep.BOUNDARY->BoundaryControls(workspace)
            PlotStep.DIRECTION->DirectionControls(workspace)
            PlotStep.ROADS->RoadControls(workspace)
        }
    }
}

@Composable
private fun PlotProblems(workspace:PlotWorkspace,modifier:Modifier=Modifier,onReload:()->Unit) {
    val c=LocalPlotusColors.current
    val errors=workspace.validation.map { (field,message)->when {
        field.startsWith("side.")->"${sideName(field.substringAfter('.').toInt(),workspace.draft.lengths.size)}: $message"
        field.startsWith("diagonal.")->"A → ${vertexName(field.substringAfter('.').toInt()+2)}: $message"
        else->message
    }}.distinct()
    val message=workspace.error ?: errors.firstOrNull()
    if(message!=null)Column(modifier.fillMaxWidth().background(MaterialTheme.colorScheme.errorContainer,RoundedCornerShape(10.dp)).padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Text(message,fontSize=13.sp,lineHeight=18.sp,color=MaterialTheme.colorScheme.onErrorContainer)
        if(errors.size>1)Text("${errors.size} fields need attention.",fontSize=12.sp,color=MaterialTheme.colorScheme.onErrorContainer)
        if(workspace.conflict)TextButton(onReload) {Text("Reload saved plot",color=c.primary)}
    }
}

@Composable
private fun SketchUpload(workspace:PlotWorkspace,modifier:Modifier=Modifier,onReview:()->Unit) {
    val c=LocalPlotusColors.current
    Column(modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        OutlinedButton({workspace.readSketch()},Modifier.fillMaxWidth().height(48.dp),enabled=!workspace.saving&&!workspace.choosingSketch&&!workspace.readingSketch,
            shape=RoundedCornerShape(10.dp),border=BorderStroke(1.dp,c.line),colors=ButtonDefaults.outlinedButtonColors(containerColor=c.quiet),contentPadding=PaddingValues(12.dp)) {
            if(workspace.readingSketch)CircularProgressIndicator(Modifier.size(18.dp),strokeWidth=2.dp,color=c.primary) else PlotSymbol(PlotSymbol.IMAGE,c.muted)
            Spacer(Modifier.width(8.dp));Text(if(workspace.readingSketch)"Reading sketch…" else "Upload plot image",color=c.ink,fontSize=14.sp,fontWeight=FontWeight.SemiBold)
        }
        if(workspace.readingSketch)TextButton({workspace.cancelSketchRead()},Modifier.align(Alignment.End)) {Text("Cancel",color=c.muted)}
        workspace.sketchError?.let {Text(it,fontSize=13.sp,color=MaterialTheme.colorScheme.error)}
        workspace.sketchSuggestion?.let { suggestion ->
            Column(Modifier.fillMaxWidth().background(c.active,RoundedCornerShape(12.dp)).padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("Your sketch is ready to review",fontSize=14.sp,fontWeight=FontWeight.SemiBold)
                Text("${suggestion.setup.lengths.size} sides found. Check the outline and measurements before saving.",fontSize=12.sp,color=c.muted)
                PlotusButton("Review measurements",{workspace.applySketch();onReview()},Modifier.fillMaxWidth(),enabled=!workspace.saving&&!workspace.readingSketch)
                TextButton({workspace.dismissSketch()}) {Text("Dismiss",color=c.muted)}
            }
        }
    }
}

@Composable
private fun BoundaryControls(workspace: PlotWorkspace) {
    val c=LocalPlotusColors.current;val draft=workspace.draft
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        Text("Lengths",Modifier.weight(1f),fontSize=16.sp,fontWeight=FontWeight.SemiBold)
        Text("${draft.lengths.size} sides",color=c.muted,fontSize=12.sp)
    }
    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {draft.lengths.forEachIndexed { i,length ->
        Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min=56.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(sideName(i,draft.lengths.size),Modifier.width(56.dp),fontSize=14.sp)
                MeasurementInput(length,draft.units,sideName(i,draft.lengths.size),Modifier.weight(1f),!workspace.saving,workspace.validation["side.$i"]) {workspace.change(draft.withLength(i,it))}
                if(i>=4)IconButton({workspace.change(draft.withoutSide(i))},Modifier.size(44.dp).semantics {contentDescription="Delete ${sideName(i,draft.lengths.size)}"},enabled=!workspace.saving) {PlotSymbol(PlotSymbol.DELETE,c.muted,Modifier.size(18.dp))}
            }
            workspace.validation["side.$i"]?.let {Text(it,fontSize=12.sp,color=MaterialTheme.colorScheme.error)}
        }
    }}
    PlotusButton("Add side",{workspace.change(draft.withSideCount(draft.lengths.size+1))},Modifier.fillMaxWidth(),outline=true,enabled=draft.lengths.size<12&&!workspace.saving)
    if(draft.lengths.size>3) {
        HorizontalDivider(color=c.line)
        Row(Modifier.fillMaxWidth().heightIn(min=44.dp),verticalAlignment=Alignment.CenterVertically) {
            Text("Do you have diagonals?",Modifier.weight(1f),fontSize=14.sp)
            Row(Modifier.background(c.quiet,RoundedCornerShape(8.dp)).padding(4.dp)) {
                listOf(true,false).forEach { yes ->
                    Box(Modifier.width(50.dp).height(36.dp).background(if(draft.useDiagonals==yes)c.surface else Color.Transparent,RoundedCornerShape(6.dp)).selectable(draft.useDiagonals==yes,enabled=!workspace.saving,role=Role.RadioButton) {workspace.change(draft.copy(useDiagonals=yes).rebuild())},contentAlignment=Alignment.Center) {Text(if(yes)"Yes" else "No",fontSize=12.sp,color=if(draft.useDiagonals==yes)c.ink else c.muted)}
                }
            }
        }
        if(draft.useDiagonals)draft.diagonals.forEachIndexed { i,length ->
            Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    Text("A → ${vertexName(i+2)}",Modifier.width(56.dp),fontSize=14.sp)
                    MeasurementInput(length,draft.units,"A → ${vertexName(i+2)}",Modifier.weight(1f),!workspace.saving,workspace.validation["diagonal.$i"]) {workspace.change(draft.withDiagonal(i,it))}
                }
                workspace.validation["diagonal.$i"]?.let {Text(it,fontSize=12.sp,color=MaterialTheme.colorScheme.error)}
            }
        }
    }
    Text("Drag corners to adjust angles while keeping side lengths fixed.",fontSize=12.sp,color=c.muted)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DirectionControls(workspace: PlotWorkspace) {
    val c=LocalPlotusColors.current
    var sliding by remember {mutableStateOf(false)}
    Text("Check the compass for the direction.",color=c.muted,fontSize=14.sp)
    Column(Modifier.fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally) {
        BasicTextField(workspace.directionText,workspace::editDirection,Modifier.width(80.dp).height(44.dp).semantics {contentDescription="North direction degrees"},
            enabled=!workspace.saving,singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Ascii),
            textStyle=LocalTextStyle.current.copy(fontSize=14.sp,lineHeight=20.sp,textAlign=TextAlign.Center,color=c.ink),cursorBrush=SolidColor(c.primary),
            visualTransformation=VisualTransformation { value -> TransformedText(AnnotatedString(value.text+"°"),object:OffsetMapping {
                override fun originalToTransformed(offset: Int)=offset
                override fun transformedToOriginal(offset: Int)=offset.coerceAtMost(value.length)
            }) },decorationBox={inner->Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){inner()}})
        Slider(workspace.draft.northDegrees.toFloat(),{
            if(!sliding){workspace.markGestureStart();sliding=true}
            workspace.change(workspace.draft.copy(northDegrees=it.roundToInt().toDouble()),remember=false)
        },
            Modifier.fillMaxWidth().semantics {contentDescription="North direction in degrees"},enabled=!workspace.saving,valueRange=-180f..180f,
            onValueChangeFinished={sliding=false},colors=SliderDefaults.colors(thumbColor=c.primary,activeTrackColor=c.line,inactiveTrackColor=c.line),
            thumb={Box(Modifier.size(22.dp).background(c.primary,CircleShape).border(3.dp,c.surface,CircleShape))},
            track={androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(4.dp)) {
                drawLine(c.line,Offset(0f,size.height/2),Offset(size.width,size.height/2),4.dp.toPx(),StrokeCap.Round)
                repeat(5) {i->drawCircle(c.border,3.dp.toPx(),Offset(size.width*i/4,size.height/2))}
            }})
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {listOf("−180°","−90°","0°","90°","180°").forEach {Text(it,color=c.muted,fontSize=12.sp)}}
    }
}

@Composable
private fun RoadControls(workspace:PlotWorkspace) {
    val c=LocalPlotusColors.current;val draft=workspace.draft
    Text("Select each side that faces a road.",color=c.muted,fontSize=14.sp)
    draft.lengths.indices.forEach { i ->
        val road=draft.roads.firstOrNull {it.side==i}
        Column(Modifier.fillMaxWidth().border(1.dp,if(road==null)c.line else c.primary,RoundedCornerShape(12.dp)).padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min=44.dp).toggleable(road!=null,enabled=!workspace.saving,role=Role.Checkbox) {workspace.change(draft.toggleRoad(i))},verticalAlignment=Alignment.CenterVertically) {
                Checkbox(road!=null,null,Modifier.size(26.dp),enabled=!workspace.saving,colors=CheckboxDefaults.colors(checkedColor=c.primary,uncheckedColor=c.border))
                Spacer(Modifier.width(8.dp));Text(sideName(i,draft.lengths.size),Modifier.weight(1f),fontSize=14.sp)
            }
            if(road!=null&&draft.roads.size>1) {
                var prioritiesOpen by remember {mutableStateOf(false)}
                Box {
                    Row(Modifier.fillMaxWidth().height(44.dp).background(c.quiet,RoundedCornerShape(8.dp)).clickable(enabled=!workspace.saving,role=Role.Button) {prioritiesOpen=true}.padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically) {
                        Text(roadPriority(road.priority),Modifier.weight(1f),fontSize=14.sp);PlotSymbol(PlotSymbol.CHEVRON,c.muted,Modifier.size(14.dp))
                    }
                    DropdownMenu(prioritiesOpen,{prioritiesOpen=false}) {draft.roads.indices.forEach { priority ->
                        DropdownMenuItem(text={Text(roadPriority(priority))},onClick={workspace.change(draft.prioritizeRoad(i,priority));prioritiesOpen=false})
                    }}
                }
            }
        }
    }
}

@Composable
internal fun MeasurementInput(metres: Double?, units: PlotUnits, label: String, modifier: Modifier, enabled: Boolean, error: String? = null, onChange: (Double?) -> Unit) {
    val c=LocalPlotusColors.current
    fun parts(value: Double?): Pair<String,String> {
        if(value==null)return "" to ""
        if(units==PlotUnits.METRES)return decimal(value,3) to ""
        val inches=round(value/METRES_PER_FOOT*12*100)/100
        return (inches/12).toInt().toString() to decimal(inches%12,2)
    }
    val initial=remember(units,label) {parts(metres)}
    var major by remember(units,label) {mutableStateOf(initial.first)}
    var minor by remember(units,label) {mutableStateOf(initial.second)}
    var observedMetres by remember(units,label) {mutableStateOf(metres)}
    fun value(first: String=major, second: String=minor): Double? {
        if(first.isBlank()&&second.isBlank())return null
        return if(units==PlotUnits.METRES)first.toDoubleOrNull() else ((first.toDoubleOrNull()?:0.0)+(second.toDoubleOrNull()?:0.0)/12)*METRES_PER_FOOT
    }
    // Reconcile external changes in the same frame. A delayed effect can restore an
    // older measurement after the user has already entered the next character.
    if(metres != observedMetres) {
        observedMetres = metres
        val local=value()
        if((metres==null)!=(local==null) || (metres!=null&&local!=null&&abs(metres-local)>.00001)) {val next=parts(metres);major=next.first;minor=next.second}
    }
    val pattern=remember {Regex("^\\d{0,6}(\\.\\d{0,4})?$")}
    Row(modifier,verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        PlotusInput(major,{if(pattern.matches(it)){major=it;onChange(value(first=it))}},"$label ${if(units==PlotUnits.METRES)"metres" else "feet"}",Modifier.weight(1f),type=KeyboardType.Decimal,enabled=enabled,error=error)
        Text(if(units==PlotUnits.METRES)"m" else "ft",fontSize=12.sp,color=c.muted)
        if(units==PlotUnits.FEET_INCHES) {
            PlotusInput(minor,{if(pattern.matches(it)){minor=it;onChange(value(second=it))}},"$label inches",Modifier.weight(1f),type=KeyboardType.Decimal,enabled=enabled,error=error)
            Text("in",fontSize=12.sp,color=c.muted)
        }
    }
}

@Composable
internal fun PlotThumbnail(points: List<PlotPoint>, modifier: Modifier, inset: androidx.compose.ui.unit.Dp = 12.dp) {
    val c=LocalPlotusColors.current
    androidx.compose.foundation.Canvas(modifier) {
        if(points.isEmpty())return@Canvas
        val minX=points.minOf {it.x};val minY=points.minOf {it.y};val width=(points.maxOf {it.x}-minX).coerceAtLeast(.01);val height=(points.maxOf {it.y}-minY).coerceAtLeast(.01)
        val padding=inset.toPx()
        val scale=min((size.width-2*padding)/width,(size.height-2*padding)/height)
        val left=(size.width-width*scale)/2;val top=(size.height-height*scale)/2
        val path=androidx.compose.ui.graphics.Path().apply {points.forEachIndexed {i,p -> val x=(left+(p.x-minX)*scale).toFloat();val y=(top+(p.y-minY)*scale).toFloat();if(i==0)moveTo(x,y) else lineTo(x,y)};close()}
        drawPath(path,c.active);drawPath(path,c.primary,style=androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
    }
}
