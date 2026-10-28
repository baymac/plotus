package com.plotus

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.plotus.shared.PlanDrawing
import com.plotus.shared.PlotUnits
import com.plotus.shared.METRES_PER_FOOT
import com.plotus.shared.decimal
import com.plotus.shared.measureLabel
import org.jetbrains.compose.resources.decodeToImageBitmap
import kotlin.io.encoding.Base64
import kotlin.math.pow

/** Read-only drawing on the same canvas as the editor, with independent pan and zoom.
 * Map paper/ink to the active palette without changing the saved engine output.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable internal fun GeneratedPlanCanvas(drawing:PlanDrawing,compact:Boolean,modifier:Modifier=Modifier) {
    val c=LocalPlotusColors.current
    val presentation=drawing.presentation
    val content=presentation?.pngBase64 ?: drawing.pngBase64
    val bitmap=remember(drawing.id,content) {runCatching {Base64.decode(content).decodeToImageBitmap()}.getOrNull()}
    var size by remember {mutableStateOf(IntSize(1,1))}
    var zoom by remember(drawing.id) {mutableStateOf(1f)}
    var pan by remember(drawing.id) {mutableStateOf(Offset.Zero)}
    var grid by remember {mutableStateOf(true)}
    val density=LocalDensity.current.density
    val filter=remember(c.ink) {
        // White paper becomes transparent. Actual strokes keep their luminance
        // as opacity, so even saved older drawings sit directly on the canvas.
        val rows=listOf(c.ink.red,c.ink.green,c.ink.blue).flatMap {channel->listOf(0f,0f,0f,0f,channel*255f)}+
            listOf(-.2126f,-.7152f,-.0722f,0f,255f)
        ColorFilter.colorMatrix(ColorMatrix(rows.toFloatArray()))
    }
    Column(modifier.background(c.background)) {
    Box(Modifier.fillMaxWidth().weight(1f).clipToBounds().onSizeChanged {size=it}) {
        if(bitmap!=null)Canvas(Modifier.fillMaxSize().semantics {contentDescription="Generated floor plan. Read-only canvas. Drag to pan, pinch or use the toolbar to zoom."}
            .pointerInput(drawing.id){detectTransformGestures {centre,delta,factor,_->
                val next=(zoom*factor).coerceIn(.5f,8f)
                val origin=Offset(size.width/2f,(size.height+72*density)/2f)
                pan=(pan+origin-centre)*(next/zoom)+centre-origin+delta
                zoom=next
            }}.onPointerEvent(PointerEventType.Scroll) {event->
                val change=event.changes.firstOrNull() ?: return@onPointerEvent
                val next=(zoom*1.12f.pow(-change.scrollDelta.y)).coerceIn(.5f,8f)
                val origin=Offset(size.width/2f,(size.height+72*density)/2f)
                pan=(pan+origin-change.position)*(next/zoom)+change.position-origin
                zoom=next;change.consume()
            }) {
                if(grid) {
                    val spacing=24*density
                    var x=16*density
                    while(x<size.width) {var y=16*density;while(y<size.height){drawCircle(c.border.copy(alpha=.65f),.65f*density,Offset(x,y));y+=spacing};x+=spacing}
                }
                val top=96*density
                val sideMargin=if(presentation?.roadPosition in listOf("west","east"))80 else 32
                val fit=minOf((size.width-sideMargin*density)/bitmap.width,(size.height-top-24*density)/bitmap.height).coerceAtLeast(.01f)
                val scale=fit*zoom
                val centre=Offset(size.width/2f,(size.height+72*density)/2f)+pan
                withTransform({translate(centre.x-bitmap.width*scale/2,centre.y-bitmap.height*scale/2);scale(scale,scale,pivot=Offset.Zero)}) {
                    drawImage(bitmap,colorFilter=filter)
                }
            }
        else Text("This floor drawing couldn’t be opened.",Modifier.align(Alignment.Center),color=c.muted)
        if(presentation!=null) {
            val side=presentation.roadPosition
            val alignment=when(side){"west"->Alignment.CenterStart;"east"->Alignment.CenterEnd;"south"->Alignment.BottomCenter;else->Alignment.TopCenter}
            val label=Modifier.align(alignment).let {m->when(side){
                "west","east"->m.width(120.dp).graphicsLayer {rotationZ=if(side=="west")-90f else 90f;translationX=if(side=="west")-40*density else 40*density}
                "south"->m.padding(bottom=4.dp)
                else->m.padding(top=if(compact)62.dp else 76.dp)
            }}
            Text("Road (primary)",label.background(c.background,RoundedCornerShape(6.dp)).padding(horizontal=10.dp,vertical=4.dp),fontSize=12.sp,color=c.muted,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
        }
        Row(Modifier.align(Alignment.TopCenter).padding(horizontal=if(compact)12.dp else 20.dp,vertical=if(compact)6.dp else 12.dp)
            .widthIn(max=560.dp).fillMaxWidth().height(if(compact)44.dp else 52.dp).background(c.surface,RoundedCornerShape(12.dp))
            .border(1.dp,c.line,RoundedCornerShape(12.dp)).padding(horizontal=4.dp),verticalAlignment=Alignment.CenterVertically) {
            PlotToolButton(PlotSymbol.HAND,"Pan plan",selected=true) {}
            PlotToolButton(PlotSymbol.GRID,"Toggle guide dots",selected=grid){grid=!grid}
            PlotToolButton(PlotSymbol.ZOOM_IN,"Zoom in"){zoom=(zoom*1.3f).coerceAtMost(8f)}
            PlotToolButton(PlotSymbol.ZOOM_OUT,"Zoom out"){zoom=(zoom/1.3f).coerceAtLeast(.5f)}
            Text("${(zoom*100).toInt()}%",Modifier.weight(1f),fontSize=11.sp,color=c.muted)
            PlotToolButton(PlotSymbol.FIT,"Fit plan to canvas"){zoom=1f;pan=Offset.Zero}
        }
    }
    if(presentation!=null) {
        androidx.compose.material3.HorizontalDivider(color=c.line)
        Row(Modifier.fillMaxWidth().background(c.surface).padding(horizontal=if(compact)16.dp else 24.dp,vertical=16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
            val units=presentation.units
            fun area(value:Double?)=value?.let {if(units==PlotUnits.METRES)"${decimal(kotlin.math.round(it*10)/10)} m²" else "${decimal(kotlin.math.round(it/(METRES_PER_FOOT*METRES_PER_FOOT)*10)/10)} sq ft"} ?: "—"
            val values=listOf("Plot area" to area(presentation.plotAreaSquareMetres),"Perimeter" to measureLabel(presentation.perimeterMetres,units),"Built-up area" to area(presentation.builtUpAreaSquareMetres),"Carpet area" to area(presentation.carpetAreaSquareMetres))
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                values.chunked(if(compact)2 else 4).forEach {row->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    row.forEach {(label,value)->Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        Text(label,fontSize=11.sp,color=c.muted)
                        Text(value,fontSize=if(compact)13.sp else 15.sp,color=c.ink,maxLines=1)
                    }}
                }}
            }
            PlotCompass(presentation.northDegrees,Modifier.size(if(compact)76.dp else 96.dp))
        }
    }
    }
}

internal expect fun planTimestamp(iso:String):String
