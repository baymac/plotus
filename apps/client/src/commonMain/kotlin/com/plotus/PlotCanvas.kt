package com.plotus

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.plotus.shared.*
import kotlin.math.*

internal enum class PlotTool { MOVE, HAND }
internal enum class PlotSymbol { POINTER, HAND, UNDO, REDO, FIT, MORE, GRID, ZOOM_IN, ZOOM_OUT, IMAGE, CHEVRON, BACK, DELETE, COMPASS, ROAD, BOUNDARY, CLOSE, KEYBOARD_HIDE, HISTORY }
internal val provisionalPlot = listOf(PlotPoint(0.0,0.0),PlotPoint(10.0,0.0),PlotPoint(10.0,8.0),PlotPoint(0.0,8.0))

@Composable
internal fun PlotSymbol(symbol: PlotSymbol, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(20.dp)) {
        scale(size.width/20f, size.height/20f, pivot=Offset.Zero) {
            fun line(x1: Float,y1: Float,x2: Float,y2: Float) = drawLine(color,Offset(x1,y1),Offset(x2,y2),1.4f,StrokeCap.Round)
            when(symbol) {
                PlotSymbol.HISTORY -> {drawCircle(color,7f,Offset(11f,10f),style=Stroke(1.3f));line(11f,6f,11f,10f);line(11f,10f,14f,12f);line(2f,3f,2f,8f);line(2f,8f,7f,8f)}
                PlotSymbol.KEYBOARD_HIDE -> {
                    drawRoundRect(color,Offset(2f,2f),androidx.compose.ui.geometry.Size(16f,10f),androidx.compose.ui.geometry.CornerRadius(1.5f),style=Stroke(1.3f))
                    for(x in listOf(5f,8f,11f,14f))for(y in listOf(5f,8f))drawCircle(color,.55f,Offset(x,y))
                    line(7f,15f,10f,18f);line(10f,18f,13f,15f)
                }
                PlotSymbol.CLOSE -> {line(5f,5f,15f,15f);line(5f,15f,15f,5f)}
                PlotSymbol.DELETE -> {line(4f,5f,16f,5f);line(8f,2f,12f,2f);line(6f,5f,7f,17f);line(7f,17f,13f,17f);line(13f,17f,14f,5f);line(9f,8f,9f,14f);line(11f,8f,11f,14f)}
                PlotSymbol.COMPASS -> {drawCircle(color,8f,Offset(10f,10f),style=Stroke(1.3f));line(13f,6f,11f,11f);line(11f,11f,6f,14f);line(6f,14f,8f,9f);line(8f,9f,13f,6f)}
                PlotSymbol.ROAD -> {line(5f,2f,5f,18f);line(15f,2f,15f,18f);line(10f,3f,10f,6f);line(10f,9f,10f,12f);line(10f,15f,10f,18f)}
                PlotSymbol.BOUNDARY -> drawPath(Path().apply {moveTo(3f,5f);lineTo(16f,3f);lineTo(17f,16f);lineTo(4f,17f);close()},color,style=Stroke(1.4f))
                PlotSymbol.CHEVRON -> {line(6f,8f,10f,12f);line(10f,12f,14f,8f)}
                PlotSymbol.BACK -> {line(12f,4f,6f,10f);line(6f,10f,12f,16f)}
                PlotSymbol.POINTER -> drawPath(Path().apply { moveTo(4f,3f);lineTo(16f,9f);lineTo(10f,11f);lineTo(8f,17f);close() },color,style=Stroke(1.4f))
                PlotSymbol.HAND -> drawPath(Path().apply { moveTo(5f,10f);lineTo(5f,7f);quadraticTo(5f,5f,7f,7f);lineTo(7f,4f);quadraticTo(8f,2f,9f,4f);lineTo(9f,7f);lineTo(9f,3f);quadraticTo(10f,1f,11f,3f);lineTo(11f,7f);lineTo(11f,4f);quadraticTo(12f,2f,13f,4f);lineTo(13f,8f);quadraticTo(16f,6f,16f,9f);lineTo(15f,14f);quadraticTo(14f,18f,11f,18f);lineTo(9f,18f);quadraticTo(7f,18f,5f,14f);lineTo(3f,11f);quadraticTo(2f,8f,5f,10f) },color,style=Stroke(1.3f))
                PlotSymbol.UNDO, PlotSymbol.REDO -> {
                    fun x(v: Float) = if(symbol==PlotSymbol.REDO) 20f-v else v
                    drawPath(Path().apply { moveTo(x(14f),16f);cubicTo(x(20f),10f,x(12f),5f,x(5f),8f) },color,style=Stroke(1.4f))
                    line(x(8f),4f,x(4f),8f);line(x(4f),8f,x(8f),11f)
                }
                PlotSymbol.FIT -> { line(3f,7f,3f,3f);line(3f,3f,7f,3f);line(13f,3f,17f,3f);line(17f,3f,17f,7f);line(17f,13f,17f,17f);line(17f,17f,13f,17f);line(7f,17f,3f,17f);line(3f,17f,3f,13f) }
                PlotSymbol.MORE -> listOf(4f,10f,16f).forEach { drawCircle(color,1.25f,Offset(it,10f)) }
                PlotSymbol.GRID -> { for (p in listOf(4f,10f,16f)) { line(p,2f,p,18f);line(2f,p,18f,p) } }
                PlotSymbol.ZOOM_IN, PlotSymbol.ZOOM_OUT -> { drawCircle(color,5.2f,Offset(8f,8f),style=Stroke(1.3f));line(12f,12f,17f,17f);line(5f,8f,11f,8f);if(symbol==PlotSymbol.ZOOM_IN)line(8f,5f,8f,11f) }
                PlotSymbol.IMAGE -> { drawRoundRect(color,Offset(2f,3f),androidx.compose.ui.geometry.Size(16f,14f),androidx.compose.ui.geometry.CornerRadius(2f),style=Stroke(1.3f));drawCircle(color,1.5f,Offset(7f,7f));line(3f,15f,9f,10f);line(9f,10f,12f,13f);line(12f,13f,16f,9f) }
            }
        }
    }
}

@Composable
internal fun PlotToolButton(symbol: PlotSymbol, label: String, enabled: Boolean = true, selected: Boolean = false, onClick: () -> Unit) {
    val c = LocalPlotusColors.current
    IconButton(onClick,Modifier.size(44.dp)
        .semantics { contentDescription=label },enabled=enabled) {
        Box(Modifier.size(32.dp).background(if(selected)c.active else Color.Transparent,RoundedCornerShape(8.dp)),contentAlignment=Alignment.Center) {
            PlotSymbol(symbol,if(selected)c.primary else if(enabled)c.muted else c.border)
        }
    }
}

/** The title and chevron share one generous hit area; Save remains a separate action. */
@Composable internal fun RowScope.EditorBack(title:String,label:String,enabled:Boolean=true,onBack:()->Unit) {
    val c=LocalPlotusColors.current
    Row(Modifier.weight(1f).heightIn(min=44.dp).clickable(enabled,role=Role.Button,onClickLabel=label,onClick=onBack)
        .semantics(mergeDescendants=true) {contentDescription=label},verticalAlignment=Alignment.CenterVertically) {
        Box(Modifier.size(44.dp),contentAlignment=Alignment.Center){PlotSymbol(PlotSymbol.BACK,c.muted)}
        Text(title,Modifier.weight(1f),fontSize=16.sp,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis)
    }
}

private data class PlotTransform(val scale: Float, val centre: Offset, val x: Double, val y: Double) {
    fun screen(p: PlotPoint) = Offset(centre.x+((p.x-x)*scale).toFloat(),centre.y+((p.y-y)*scale).toFloat())
    fun model(p: Offset) = PlotPoint(x+(p.x-centre.x)/scale,y+(p.y-centre.y)/scale)
}
private fun plotTransform(points: List<PlotPoint>, size: IntSize, density: Float, zoom: Float, pan: Offset, showToolbar: Boolean, compact: Boolean, details:Boolean=false,showHint:Boolean=false,hasRoads:Boolean=false): PlotTransform {
    val minX=points.minOf { it.x };val maxX=points.maxOf { it.x };val minY=points.minOf { it.y };val maxY=points.maxOf { it.y }
    val width=(size.width-92*density).coerceAtLeast(20*density)
    val top=(if(details)140 else if(showToolbar) {if(compact)80 else 84} else if(compact)28 else 12)*density
    val bottom=(if(compact) {if(size.height/density<180)32 else if(showHint)102 else if(hasRoads)86 else 68} else if(showHint)112 else 64)*density
    val height=(size.height-top-bottom).coerceAtLeast(20*density)
    return PlotTransform(min(width/(maxX-minX).coerceAtLeast(.01),height/(maxY-minY).coerceAtLeast(.01)).toFloat()*zoom*(if(compact)1f else .84f),
        Offset(size.width/2f,top+height/2+(if(compact)0 else 10)*density)+pan,(minX+maxX)/2,(minY+maxY)/2)
}

@Composable
internal fun PlotCanvas(workspace: PlotWorkspace, step: PlotStep?, compact: Boolean, modifier: Modifier = Modifier, showToolbar: Boolean = true, readOnly:Boolean=false, envelope:FloorEnvelope?=null,showBuildable:Boolean=false,checkingBuildable:Boolean=false) {
    val c=LocalPlotusColors.current
    var tool by remember(readOnly) { mutableStateOf(if(readOnly)PlotTool.HAND else PlotTool.MOVE) }
    var toolOpen by remember { mutableStateOf(false) }
    var moreOpen by remember { mutableStateOf(false) }
    var grid by remember { mutableStateOf(true) }
    val viewport=rememberCanvasZoom(workspace)
    var canvasSize by remember { mutableStateOf(IntSize(1,1)) }
    var dragging by remember { mutableStateOf(false) }
    var dragIndex by remember { mutableStateOf(-1) }
    val setup=workspace.draft.copy(units=LocalMeasurementUnits.current)
    val selectUnits=LocalSetMeasurementUnits.current
    val points=setup.vertices.ifEmpty { provisionalPlotForSides(setup.lengths.size) }
    var fitPoints by remember { mutableStateOf(points) }
    LaunchedEffect(points,dragging) { if(!dragging)fitPoints=points }
    val currentSetup by rememberUpdatedState(setup)
    val currentPoints by rememberUpdatedState(points)
    val anglesEnabled=!readOnly&&(step==null||step==PlotStep.BOUNDARY)
    val currentAnglesEnabled by rememberUpdatedState(anglesEnabled)
    val density=androidx.compose.ui.platform.LocalDensity.current.density
    val transform=plotTransform(fitPoints,canvasSize,density,viewport.view.zoom,viewport.view.pan,showToolbar,compact,showBuildable&&showToolbar,anglesEnabled&&showToolbar,setup.roads.isNotEmpty())
    val currentTransform by rememberUpdatedState(transform)
    var gestureTransform by remember { mutableStateOf(transform) }
    val measurer=rememberTextMeasurer()
    val baseStyle=LocalTextStyle.current
    Box(modifier.background(c.background).clipToBounds().onSizeChanged { canvasSize=it }) {
        Canvas(Modifier.fillMaxSize().semantics { contentDescription="Plot boundary. "+(if(anglesEnabled)"Drag corners to adjust angles with fixed side lengths. " else "Corner editing disabled. ")+"North ${decimal(setup.northDegrees)} degrees." }
            .canvasNavigation(viewport,transform.centre-viewport.view.pan,
                onEditStart={offset->
                    gestureTransform=currentTransform
                    dragIndex=if(tool==PlotTool.MOVE&&currentAnglesEnabled&&!workspace.saving)
                        currentPoints.indices.minByOrNull {i->(gestureTransform.screen(currentPoints[i])-offset).getDistance()}
                            ?.takeIf {(gestureTransform.screen(currentPoints[it])-offset).getDistance()<=28*density} ?: -1
                        else -1
                    if(dragIndex>=0) {
                        val message=when {
                            currentSetup.vertices.isEmpty()->"Set boundary dimension"
                            currentSetup.useDiagonals->"Measured diagonals fix the angles. Turn off diagonals to adjust them."
                            currentSetup.lengths.size<4->"Side lengths fix a triangle’s angles."
                            else->null
                        }
                        if(message!=null){workspace.boundaryError(message);dragIndex=-1}
                        else {dragging=true;workspace.markGestureStart()}
                    }
                    dragIndex>=0
                },onEditDrag={position->
                    if(currentAnglesEnabled&&!workspace.saving) {
                        val edited=currentSetup.withCornerAngle(dragIndex,gestureTransform.model(position))
                        if(edited!=null)workspace.change(edited,remember=false)
                        else workspace.boundaryError("This angle would cross the boundary or change a side length.")
                    }
                },onEditEnd={dragging=false;dragIndex=-1}
            ).pointerInput(workspace,step) {
                if(step==PlotStep.ROADS&&!readOnly)detectTapGestures { position ->
                    if(workspace.saving)return@detectTapGestures
                    val p=currentPoints;val t=currentTransform
                    val side=p.indices.minByOrNull { i -> pointToSegment(position,t.screen(p[i]),t.screen(p[(i+1)%p.size])) }
                    if(side!=null && pointToSegment(position,t.screen(p[side]),t.screen(p[(side+1)%p.size]))<18*density)workspace.change(currentSetup.toggleRoad(side))
                }
            }) {
            if(grid) {
                val spacing=24*density
                var x=16*density
                while(x<size.width) {var y=16*density;while(y<size.height){drawCircle(c.border.copy(alpha=.65f),.65f*density,Offset(x,y));y+=spacing};x+=spacing}
            }
            val screen=points.map(transform::screen)
            val path=Path().apply {moveTo(screen[0].x,screen[0].y);screen.drop(1).forEach {lineTo(it.x,it.y)};close()}
            val winding=points.indices.sumOf { i -> val a=points[i];val b=points[(i+1)%points.size];a.x*b.y-a.y*b.x }.let { if(it>=0)1f else -1f }
            fun outward(i: Int): Offset {val d=screen[(i+1)%screen.size]-screen[i];val length=d.getDistance().coerceAtLeast(1f);return Offset(d.y/length,-d.x/length)*winding}
            setup.roads.forEach { road ->
                if(road.side in screen.indices) { val n=outward(road.side)*8f*density;drawLine(c.quiet,screen[road.side]+n,screen[(road.side+1)%screen.size]+n,12*density);drawLine(c.line,screen[road.side]+n*1.8f,screen[(road.side+1)%screen.size]+n*1.8f,1*density) }
            }
            drawPath(path,if(envelope!=null)c.primary.copy(alpha=.16f) else c.surface)
            drawPath(path,c.ink,style=Stroke(1.5f*density))
            envelope?.outlines?.filter {it.size>=3}?.forEach {outline->
                val inner=outline.map(transform::screen)
                val buildable=Path().apply {moveTo(inner[0].x,inner[0].y);inner.drop(1).forEach {lineTo(it.x,it.y)};close()}
                drawPath(buildable,c.surface)
                drawPath(buildable,c.primary,style=Stroke(2f*density,pathEffect=PathEffect.dashPathEffect(floatArrayOf(5*density,4*density))))
            }
            envelope?.edgeSetbacksMetres?.forEachIndexed {i,distance->
                if(i in screen.indices&&distance>0) {
                    val a=screen[i];val b=screen[(i+1)%screen.size]
                    val middle=(a+b)/2f
                    val normal=outward(i)
                    val inner=middle-normal*(distance*transform.scale).toFloat()
                    val tangent=Offset(-normal.y,normal.x)*3f*density
                    drawLine(c.primary,middle,inner,density)
                    drawLine(c.primary,middle-tangent,middle+tangent,density)
                    drawLine(c.primary,inner-tangent,inner+tangent,density)
                    val text=measurer.measure(measureLabel(distance,setup.units),baseStyle.copy(fontSize=if(compact)9.sp else 11.sp,color=c.primary))
                    val centre=inner-normal*(12*density)
                    val at=centre-Offset(text.size.width/2f,text.size.height/2f)
                    drawRoundRect(c.surface,at-Offset(3*density,density),androidx.compose.ui.geometry.Size(text.size.width+6*density,text.size.height+2*density),androidx.compose.ui.geometry.CornerRadius(3*density))
                    drawText(text,topLeft=at)
                }
            }
            if(setup.useDiagonals && setup.vertices.isNotEmpty()) for(i in 2 until points.lastIndex) drawLine(c.muted.copy(alpha=.6f),screen[0],screen[i],1*density,pathEffect=PathEffect.dashPathEffect(floatArrayOf(4*density,4*density)))
            screen.forEachIndexed { i,p ->
                val road=setup.roads.firstOrNull {it.side==i}
                val label=measureLabel(setup.lengths.getOrNull(i),setup.units)+(road?.let {"\n${roadCanvasLabel(it.priority,setup.roads.size)}"} ?: "")
                val text=measurer.measure(label,baseStyle.copy(fontSize=if(compact)10.sp else 11.sp,lineHeight=if(compact)13.sp else 15.sp,color=c.muted))
                val middle=(p+screen[(i+1)%screen.size])/2f+outward(i)*17f*density
                val origin=Offset((middle.x-text.size.width/2f).coerceIn(0f,(size.width-text.size.width).coerceAtLeast(0f)),(middle.y-text.size.height/2f).coerceIn(0f,(size.height-text.size.height).coerceAtLeast(0f)))
                drawRoundRect(c.background,origin-Offset(4*density,2*density),androidx.compose.ui.geometry.Size(text.size.width+8*density,text.size.height+4*density),androidx.compose.ui.geometry.CornerRadius(4*density))
                drawText(text,topLeft=origin)
                if(anglesEnabled){drawCircle(c.surface,5*density,p);drawCircle(c.primary,5*density,p,style=Stroke(1*density))}
                drawCircle(c.primary,2.1f*density,p)
                val toward=p-transform.centre
                val at=p+Offset(if(toward.x<0)-17*density else 8*density,if(toward.y<0)-23*density else 8*density)
                drawText(measurer,vertexName(i),topLeft=at,style=baseStyle.copy(fontSize=11.sp,color=c.ink))
            }
            if(step!=PlotStep.BOUNDARY) {
                val (centre,space)=compassPlacement(screen,size.width,size.height)
                val r=minOf((if(compact)56 else 92)*density,space*.7f)
                if(r>=8*density) {
                    drawPlotCompass(centre,r,setup.northDegrees,density,c,measurer,baseStyle)
                }
            }
        }
        if(showToolbar) Row(Modifier.align(Alignment.TopCenter).padding(horizontal=if(compact)12.dp else 20.dp,vertical=if(compact)6.dp else 12.dp).fillMaxWidth().height(if(compact)44.dp else 52.dp)
            .background(c.surface,RoundedCornerShape(12.dp)).border(1.dp,c.line,RoundedCornerShape(12.dp)).padding(horizontal=4.dp),verticalAlignment=Alignment.CenterVertically) {
            if(readOnly)PlotToolButton(PlotSymbol.HAND,"Pan canvas",selected=true) {tool=PlotTool.HAND}
            else {
            Box {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    PlotToolButton(if(tool==PlotTool.MOVE)PlotSymbol.POINTER else PlotSymbol.HAND,if(tool==PlotTool.MOVE)"Adjust corner angles" else "Pan canvas",selected=true,enabled=!workspace.saving&&(tool!=PlotTool.MOVE||anglesEnabled)) {toolOpen=!toolOpen}
                    TextButton({toolOpen=true},Modifier.width(24.dp).height(44.dp).semantics {contentDescription="Choose canvas tool"},contentPadding=PaddingValues(0.dp)) {PlotSymbol(PlotSymbol.CHEVRON,c.muted,Modifier.size(12.dp))}
                }
                DropdownMenu(toolOpen,{toolOpen=false}) {
                    DropdownMenuItem(text={Text("Adjust angles")},onClick={tool=PlotTool.MOVE;toolOpen=false},enabled=anglesEnabled)
                    DropdownMenuItem(text={Text("Hand")},onClick={tool=PlotTool.HAND;toolOpen=false})
                }
            }
            PlotToolButton(PlotSymbol.UNDO,"Undo plot change",workspace.canUndo&&!workspace.saving) {workspace.undo()}
            PlotToolButton(PlotSymbol.REDO,"Redo plot change",workspace.canRedo&&!workspace.saving) {workspace.redo()}
            }
            if(readOnly||!compact && canvasSize.width/density>600) {
                PlotToolButton(PlotSymbol.GRID,"Toggle guide dots",selected=grid) {grid=!grid}
                PlotToolButton(PlotSymbol.ZOOM_IN,"Zoom in") {viewport.step(CanvasZoom.STEP,CanvasZoom.MAXIMUM)}
                PlotToolButton(PlotSymbol.ZOOM_OUT,"Zoom out") {viewport.step(1f/CanvasZoom.STEP,CanvasZoom.MAXIMUM)}
            }
            Spacer(Modifier.weight(1f))
            PlotToolButton(PlotSymbol.FIT,"Fit plot to canvas") {viewport.reset();fitPoints=points}
            Box {
                PlotToolButton(PlotSymbol.MORE,"Plot canvas options") {moreOpen=true}
                DropdownMenu(moreOpen,{moreOpen=false}) {
                    Text("Units",Modifier.padding(horizontal=12.dp,vertical=8.dp),fontSize=12.sp,color=c.muted)
                    PlotUnits.entries.forEach { unit -> DropdownMenuItem(text={Text((if(setup.units==unit)"✓  " else "")+if(unit==PlotUnits.METRES)"Metres" else "Feet & inches")},onClick={selectUnits(unit);moreOpen=false},enabled=!workspace.saving) }

                }
            }
        }

        if(envelope!=null&&canvasSize.height/density>=300)Row(Modifier.align(Alignment.BottomStart).padding(start=if(compact)20.dp else 28.dp,bottom=80.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(12.dp).background(c.primary.copy(alpha=.16f),RoundedCornerShape(2.dp)).border(1.dp,c.primary.copy(alpha=.5f),RoundedCornerShape(2.dp)))
            Text("Setback",fontSize=10.sp,color=c.muted)
        }
        val area=if(setup.units==PlotUnits.METRES)setup.areaSquareMetres?.let { "${decimal(it)} m²" } else setup.areaSquareFeet?.let(::areaLabel)
        if(!compact||canvasSize.height/density>=180)Column(Modifier.align(Alignment.BottomCenter).padding(horizontal=12.dp,vertical=if(compact)3.dp else 16.dp)
            .widthIn(max=560.dp).fillMaxWidth().background(c.surface,RoundedCornerShape(10.dp)).border(1.dp,c.line,RoundedCornerShape(10.dp)).padding(horizontal=8.dp,vertical=8.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(4.dp)) {
            if(anglesEnabled&&showToolbar)Text("Move vertices to change the plot shape",fontSize=10.sp,lineHeight=14.sp,color=c.muted)
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                val buildable=when {checkingBuildable->"Checking…";envelope?.empty==true->"0";setup.units==PlotUnits.METRES->envelope?.areaSquareMetres?.let {"${decimal(it)} m²"};else->envelope?.areaSquareMetres?.let {areaLabel((it/(METRES_PER_FOOT*METRES_PER_FOOT)).toInt())}}
                val metrics=listOf("Area" to (area ?: "—"),"Perimeter" to measureLabel(setup.perimeterMetres,setup.units))+(if(showBuildable)listOf("Buildable" to (buildable ?: "—")) else emptyList())
                metrics.forEachIndexed {i,(label,value)->
                    if(i>0)VerticalDivider(Modifier.height(28.dp),color=c.line)
                    Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally) {
                        Text(label,fontSize=10.sp,lineHeight=14.sp,color=c.muted)
                        Text(value,fontSize=if(compact)11.sp else 12.sp,lineHeight=18.sp,color=if(label=="Buildable")c.primary else c.ink,maxLines=1)
                    }
                }
            }
        }
    }
}

internal fun provisionalPlotForSides(count: Int): List<PlotPoint> = if(count==4)provisionalPlot else List(count.coerceIn(3,12)) { i ->
    val angle=2*PI*i/count-PI*.75
    PlotPoint(5+5*cos(angle),5+5*sin(angle))
}
private fun pointToSegment(p: Offset,a: Offset,b: Offset): Float {
    val d=b-a;val denominator=d.x*d.x+d.y*d.y
    val t=if(denominator==0f)0f else (((p.x-a.x)*d.x+(p.y-a.y)*d.y)/denominator).coerceIn(0f,1f)
    return (p-(a+d*t)).getDistance()
}

/** Find interior room for the compass, including on concave and narrow plots. */
internal fun compassPlacement(points:List<Offset>,width:Float,height:Float):Pair<Offset,Float> {
    fun inside(p:Offset):Boolean {
        var result=false
        points.indices.forEach {i->
            val a=points[i];val b=points[(i+1)%points.size]
            if((a.y>p.y)!=(b.y>p.y)&&p.x<(b.x-a.x)*(p.y-a.y)/(b.y-a.y)+a.x)result=!result
        }
        return result
    }
    fun clearance(p:Offset)=minOf(points.indices.minOf {i->pointToSegment(p,points[i],points[(i+1)%points.size])},p.x,width-p.x,p.y,height-p.y).coerceAtLeast(0f)
    val left=points.minOf {it.x};val right=points.maxOf {it.x};val top=points.minOf {it.y};val bottom=points.maxOf {it.y}
    val centre=Offset((left+right)/2,(top+bottom)/2)
    val candidates=listOf(centre,points.reduce {a,b->a+b}/points.size.toFloat())+(1..9).flatMap {y->(1..9).map {x->Offset(left+(right-left)*x/10,top+(bottom-top)*y/10)}}
    val fit=candidates.filter(::inside).maxByOrNull(::clearance) ?: return centre to 0f
    return fit to clearance(fit)
}
