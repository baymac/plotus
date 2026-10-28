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
fun PlotSetupScreen(workspace: PlotWorkspace, step: PlotStep, compact: Boolean, onStep: (PlotStep) -> Unit, onExit: () -> Unit) {
    val c=LocalPlotusColors.current
    val scope=rememberCoroutineScope()
    LaunchedEffect(workspace) { workspace.load() }
    var leaveOpen by remember { mutableStateOf(false) }
    var reloadOpen by remember { mutableStateOf(false) }
    var shapesOpen by remember { mutableStateOf(false) }
    val exit={if(workspace.dirty)leaveOpen=true else onExit()}
    val keyboardVisible=WindowInsets.ime.getBottom(LocalDensity.current)>0
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)).imePadding()) {
        Row(Modifier.fillMaxWidth().height(72.dp).padding(start=if(compact)12.dp else 20.dp,end=20.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            if(compact) PlotToolButton(PlotSymbol.BACK,"Back to projects",enabled=!workspace.saving) {exit()}
            Column(Modifier.weight(1f)) {
                Text(workspace.project?.name ?: "Plot setup",maxLines=1,overflow=TextOverflow.Ellipsis,fontSize=16.sp,fontWeight=FontWeight.SemiBold)
                if(workspace.project!=null) Text(if(workspace.saving)"Saving…" else if(workspace.dirty)"Unsaved changes" else "Saved",color=c.muted,fontSize=11.sp,lineHeight=15.sp)
            }
            TextButton({workspace.save()},enabled=workspace.project!=null&&!workspace.loading&&!workspace.saving&&workspace.dirty) {Text("Save",fontSize=14.sp)}
        }
        if(workspace.loading) Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) {CircularProgressIndicator()}
        else if(workspace.project==null) Column(Modifier.fillMaxSize().padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp),horizontalAlignment=Alignment.CenterHorizontally) {
            Text(workspace.error ?: "This project is unavailable.")
            PlotusButton("Try again",{scope.launch {workspace.load()}})
            TextButton(onExit) {Text("Back to projects")}
        }
        else if(compact) {
            PlanningStages(Modifier.padding(horizontal=20.dp).padding(top=8.dp,bottom=8.dp))
            BoxWithConstraints(Modifier.weight(1f)) {
                val canvasHeight=minOf(if(keyboardVisible)128.dp else 280.dp,maxOf(0.dp,maxHeight-240.dp))
                Column(Modifier.fillMaxSize()) {
                    if(canvasHeight>0.dp)PlotCanvas(workspace,step,true,Modifier.fillMaxWidth().height(canvasHeight),showToolbar=!keyboardVisible&&canvasHeight>=190.dp) {shapesOpen=true}
                    PlotInspector(workspace,step,onStep,exit,{onExit()},Modifier.weight(1f)) {reloadOpen=true}
                }
            }
        } else Row(Modifier.fillMaxWidth().weight(1f)) {
            Column(Modifier.weight(1f).fillMaxHeight()) {
                PlanningStages(Modifier.padding(horizontal=20.dp).padding(top=8.dp,bottom=8.dp))
                PlotCanvas(workspace,step,false,Modifier.fillMaxWidth().weight(1f)) {shapesOpen=true}
            }
            VerticalDivider(color=c.line)
            PlotInspector(workspace,step,onStep,exit,{onExit()},Modifier.width(408.dp).fillMaxHeight()) {reloadOpen=true}
        }
    }
    if(leaveOpen) AlertDialog(onDismissRequest={if(!workspace.saving)leaveOpen=false},title={Text("Save plot changes?")},
        text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("Save your measurements before returning to Projects.")
            workspace.error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
            workspace.validation.values.distinct().forEach {Text(it,color=MaterialTheme.colorScheme.error,fontSize=12.sp)}
        }},confirmButton={TextButton({workspace.save {leaveOpen=false;onExit()}},enabled=!workspace.saving) {Text(if(workspace.saving)"Saving…" else "Save and leave")}},
        dismissButton={Row {TextButton({leaveOpen=false},enabled=!workspace.saving) {Text("Keep editing")};TextButton({workspace.discard();leaveOpen=false;onExit()},enabled=!workspace.saving) {Text("Discard")}}})
    if(reloadOpen) AlertDialog(onDismissRequest={reloadOpen=false},title={Text("Reload the saved plot?")},text={Text("Your unsaved measurements will be replaced by the latest version saved to your account.")},
        confirmButton={TextButton({reloadOpen=false;scope.launch {workspace.load(force=true)}}) {Text("Reload saved plot")}},dismissButton={TextButton({reloadOpen=false}) {Text("Keep editing")}})
    if(shapesOpen) {
        val first=workspace.draft.rebuild()
        val second=if(first.useDiagonals)first.copy(vertices=mirrorBoundary(first.vertices)) else first.rebuild(variant=1)
        AlertDialog(onDismissRequest={shapesOpen=false},title={Text("Choose shape")},text={
            Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text("Sides alone can describe more than one shape. Choose the outline that matches your plot.",color=c.muted)
                listOf(first,second).forEachIndexed { i,choice ->
                    Column(Modifier.fillMaxWidth().border(1.dp,c.line,RoundedCornerShape(12.dp)).clickable(role=Role.Button) {workspace.change(choice);shapesOpen=false}.padding(12.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                        PlotThumbnail(choice.vertices,Modifier.fillMaxWidth().height(100.dp))
                        Text("Shape ${if(i==0)"A" else "B"}",fontWeight=FontWeight.SemiBold)
                    }
                }
            }
        },confirmButton={TextButton({shapesOpen=false}) {Text("Cancel")}})
    }
}

@Composable
private fun PlanningStages(modifier: Modifier) {
    val c=LocalPlotusColors.current
    Row(modifier.fillMaxWidth().height(44.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        listOf("Plot setup","Floor plan").forEachIndexed { i,label ->
            Row(Modifier.weight(1f).fillMaxHeight().background(if(i==0)c.active else c.background,RoundedCornerShape(8.dp)).padding(8.dp),
                verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(24.dp).background(if(i==0)c.primary else c.quiet,CircleShape),contentAlignment=Alignment.Center) {Text("${i+1}",fontSize=12.sp,color=if(i==0)c.onPrimary else c.muted)}
                Text(label,fontSize=14.sp,fontWeight=if(i==0)FontWeight.SemiBold else FontWeight.Normal,color=if(i==0)c.primary else c.muted)
            }
        }
    }
}

@Composable
private fun PlotInspector(workspace: PlotWorkspace, step: PlotStep, onStep: (PlotStep) -> Unit, onBack: () -> Unit, onComplete: () -> Unit, modifier: Modifier, onReload: () -> Unit) {
    val c=LocalPlotusColors.current
    Column(modifier.background(c.surface)) {
        Row(Modifier.fillMaxWidth().padding(horizontal=20.dp).padding(top=8.dp,bottom=4.dp).height(44.dp),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
            PlotStep.entries.forEachIndexed { i,item ->
                Box(Modifier.weight(1f).fillMaxHeight().background(if(step==item)c.quiet else c.surface,RoundedCornerShape(8.dp))
                    .selectable(step==item,enabled=!workspace.saving,role=Role.Tab) {
                        onStep(item)
                    },contentAlignment=Alignment.Center) {
                    Text("${listOf("i.","ii.","iii.")[i]} ${item.segment.replaceFirstChar {it.uppercase()}}",fontSize=12.sp,color=if(step==item)c.ink else c.muted)
                }
            }
        }
        OutlinedButton({},Modifier.padding(horizontal=20.dp).padding(bottom=4.dp).fillMaxWidth().height(44.dp),enabled=false,
            shape=RoundedCornerShape(8.dp),border=BorderStroke(0.dp,Color.Transparent),colors=ButtonDefaults.outlinedButtonColors(disabledContainerColor=c.quiet,disabledContentColor=c.muted),contentPadding=PaddingValues(8.dp)) {
            PlotSymbol(PlotSymbol.IMAGE,c.muted)
            Spacer(Modifier.width(8.dp));Text("Upload plot image",color=c.muted,fontSize=14.sp,fontWeight=FontWeight.SemiBold)
        }
        HorizontalDivider(color=c.line)
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=20.dp,vertical=16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            if(workspace.error!=null) Column(Modifier.fillMaxWidth().background(c.quiet,RoundedCornerShape(10.dp)).padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(workspace.error!!,color=MaterialTheme.colorScheme.error,fontSize=13.sp)
                if(workspace.conflict)TextButton(onReload) {Text("Reload saved plot")}
            }
            workspace.validation.filterKeys { !it.startsWith("side.")&&!it.startsWith("diagonal.")&&!it.startsWith("road.") }.values.distinct().forEach {Text(it,color=MaterialTheme.colorScheme.error,fontSize=12.sp)}
            if(step!=PlotStep.BOUNDARY && workspace.validation.keys.any {it.startsWith("side.")||it.startsWith("diagonal.")}) {
                Text("Complete the boundary measurements before continuing.",color=MaterialTheme.colorScheme.error,fontSize=12.sp)
                TextButton({onStep(PlotStep.BOUNDARY)}) {Text("Go to Boundary")}
            }
            when(step) {
                PlotStep.BOUNDARY -> BoundaryControls(workspace)
                PlotStep.DIRECTION -> DirectionControls(workspace)
                PlotStep.ROADS -> RoadControls(workspace)
            }
        }
        HorizontalDivider(color=c.line)
        Row(Modifier.fillMaxWidth().padding(20.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            PlotusButton("Back",{if(step==PlotStep.BOUNDARY)onBack() else onStep(PlotStep.entries[step.ordinal-1])},Modifier.width(100.dp).height(48.dp),outline=true,enabled=!workspace.saving)
            PlotusButton(if(workspace.saving)"Saving…" else if(step==PlotStep.ROADS)"Save plot" else "Next",{
                workspace.save(requireBoundary=true,requireRoads=step==PlotStep.ROADS) {
                    if(step==PlotStep.ROADS)onComplete() else onStep(PlotStep.entries[step.ordinal+1])
                }
            },Modifier.weight(1f).height(48.dp),enabled=!workspace.saving)
        }
    }
}

@Composable
private fun BoundaryControls(workspace: PlotWorkspace) {
    val c=LocalPlotusColors.current;val draft=workspace.draft
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        Text("Sides",Modifier.weight(1f),fontSize=16.sp,fontWeight=FontWeight.SemiBold)
        Text("${draft.lengths.size} sides",color=c.muted,fontSize=12.sp)
    }
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) { draft.lengths.forEachIndexed { i,length ->
        Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min=56.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Column(Modifier.width(64.dp)) {Text("Side ${i+1}",fontSize=14.sp);Text(sideName(i,draft.lengths.size),fontSize=12.sp,color=c.muted)}
                MeasurementInput(length,draft.units,"Side ${i+1}",Modifier.weight(1f),!workspace.saving) {workspace.change(draft.withLength(i,it))}
            }
            workspace.validation["side.$i"]?.let {Text(it,fontSize=12.sp,color=MaterialTheme.colorScheme.error)}
        }
    } }
    PlotusButton("Add side",{workspace.change(draft.withSideCount(draft.lengths.size+1))},Modifier.fillMaxWidth(),outline=true,enabled=draft.lengths.size<12&&!workspace.saving)
    if(draft.lengths.size>3) {
        HorizontalDivider(color=c.line)
        Row(Modifier.fillMaxWidth().heightIn(min=44.dp),verticalAlignment=Alignment.CenterVertically) {
            Text("Do you have diagonals?",Modifier.weight(1f),fontSize=14.sp)
            Row(Modifier.background(c.quiet,RoundedCornerShape(8.dp)).padding(4.dp)) {
                listOf(true,false).forEach { selected ->
                    Box(Modifier.width(50.dp).height(36.dp).background(if(draft.useDiagonals==selected)c.surface else Color.Transparent,RoundedCornerShape(6.dp))
                        .selectable(draft.useDiagonals==selected,enabled=!workspace.saving,role=Role.RadioButton) {workspace.change(draft.copy(useDiagonals=selected).rebuild())},contentAlignment=Alignment.Center) {Text(if(selected)"Yes" else "No",fontSize=12.sp,color=if(draft.useDiagonals==selected)c.ink else c.muted)}
                }
            }
        }
        if(draft.useDiagonals)draft.diagonals.forEachIndexed { i,length ->
            Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.width(64.dp)) {Text("Diagonal",fontSize=14.sp);Text("A → ${vertexName(i+2)}",fontSize=12.sp,color=c.muted)}
                    MeasurementInput(length,draft.units,"Diagonal A to ${vertexName(i+2)}",Modifier.weight(1f),!workspace.saving) {workspace.change(draft.withDiagonal(i,it))}
                }
                workspace.validation["diagonal.$i"]?.let {Text(it,fontSize=12.sp,color=MaterialTheme.colorScheme.error)}
            }
        }
    }
    Text("Add measured diagonals or move the corners to match your plot.",fontSize=12.sp,color=c.muted)
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
private fun RoadControls(workspace: PlotWorkspace) {
    val c=LocalPlotusColors.current;val draft=workspace.draft
    Text("Select each side that faces a road.",color=c.muted,fontSize=14.sp)
    draft.lengths.indices.forEach { i ->
        val road=draft.roads.firstOrNull {it.side==i}
        Column(Modifier.fillMaxWidth().border(1.dp,if(road==null)c.line else c.border,RoundedCornerShape(12.dp)).padding(12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min=44.dp).toggleable(road!=null,enabled=!workspace.saving,role=Role.Checkbox) {workspace.change(draft.toggleRoad(i))},verticalAlignment=Alignment.CenterVertically) {
                Checkbox(road!=null,null,Modifier.size(26.dp),enabled=!workspace.saving,colors=CheckboxDefaults.colors(checkedColor=c.primary,uncheckedColor=c.border))
                Spacer(Modifier.width(4.dp));Text("Side ${i+1} · ${sideName(i,draft.lengths.size)}",Modifier.weight(1f),fontSize=14.sp)
                if(road!=null)Text(roadPriority(road.priority),color=c.muted,fontSize=12.sp)
            }
            if(road!=null) {
                LabeledInput("Road name",road.name,{workspace.change(draft.withRoad(road.copy(name=it.take(120))))},enabled=!workspace.saving)
                Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Text("Width",color=c.muted,fontSize=12.sp)
                    MeasurementInput(road.widthMetres,draft.units,"Road ${i+1} width",Modifier.fillMaxWidth(),!workspace.saving) {workspace.change(draft.withRoad(road.copy(widthMetres=it)))}
                    workspace.validation["road.$i.width"]?.let {Text(it,color=MaterialTheme.colorScheme.error,fontSize=12.sp)}
                }
                var prioritiesOpen by remember {mutableStateOf(false)}
                Box {
                    Row(Modifier.fillMaxWidth().height(44.dp).background(c.quiet,RoundedCornerShape(8.dp)).clickable(enabled=!workspace.saving,role=Role.Button) {prioritiesOpen=true}.padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically) {
                        Text(roadPriority(road.priority),Modifier.weight(1f),fontSize=14.sp);PlotSymbol(PlotSymbol.CHEVRON,c.muted,Modifier.size(14.dp))
                    }
                    DropdownMenu(prioritiesOpen,{prioritiesOpen=false}) {draft.roads.indices.forEach {priority ->
                        DropdownMenuItem(text={Text(roadPriority(priority))},onClick={workspace.change(draft.prioritizeRoad(i,priority));prioritiesOpen=false})
                    }}
                }
            }
        }
    }
}

@Composable
private fun MeasurementInput(metres: Double?, units: PlotUnits, label: String, modifier: Modifier, enabled: Boolean, onChange: (Double?) -> Unit) {
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
        PlotusInput(major,{if(pattern.matches(it)){major=it;onChange(value(first=it))}},"$label ${if(units==PlotUnits.METRES)"metres" else "feet"}",Modifier.weight(1f),type=KeyboardType.Decimal,enabled=enabled)
        Text(if(units==PlotUnits.METRES)"m" else "ft",fontSize=12.sp,color=c.muted)
        if(units==PlotUnits.FEET_INCHES) {
            PlotusInput(minor,{if(pattern.matches(it)){minor=it;onChange(value(second=it))}},"$label inches",Modifier.weight(1f),type=KeyboardType.Decimal,enabled=enabled)
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
