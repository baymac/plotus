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
import org.jetbrains.compose.resources.decodeToImageBitmap
import kotlin.io.encoding.Base64
import kotlin.math.pow

/** Read-only drawing on the same canvas as the editor, with independent pan and zoom.
 * Map paper/ink to the active palette without changing the saved engine output.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable internal fun GeneratedPlanCanvas(drawing:PlanDrawing,compact:Boolean,modifier:Modifier=Modifier) {
    val c=LocalPlotusColors.current
    val bitmap=remember(drawing.id,drawing.pngBase64) {runCatching {Base64.decode(drawing.pngBase64).decodeToImageBitmap()}.getOrNull()}
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
    Box(modifier.background(c.background).clipToBounds().onSizeChanged {size=it}) {
        if(bitmap!=null)Canvas(Modifier.fillMaxSize().semantics {contentDescription="Generated floor plan. Read-only canvas. Drag to pan, pinch or use the toolbar to zoom."}
            .pointerInput(drawing.id){detectTransformGestures {centre,delta,factor,_->
                val next=(zoom*factor).coerceIn(.5f,8f)
                val origin=Offset(size.width/2f,(size.height+60*density)/2f)
                pan=(pan+origin-centre)*(next/zoom)+centre-origin+delta
                zoom=next
            }}.onPointerEvent(PointerEventType.Scroll) {event->
                val change=event.changes.firstOrNull() ?: return@onPointerEvent
                val next=(zoom*1.12f.pow(-change.scrollDelta.y)).coerceIn(.5f,8f)
                val origin=Offset(size.width/2f,(size.height+60*density)/2f)
                pan=(pan+origin-change.position)*(next/zoom)+change.position-origin
                zoom=next;change.consume()
            }) {
                if(grid) {
                    val spacing=24*density
                    var x=16*density
                    while(x<size.width) {var y=16*density;while(y<size.height){drawCircle(c.border.copy(alpha=.65f),.65f*density,Offset(x,y));y+=spacing};x+=spacing}
                }
                val top=68*density
                val fit=minOf((size.width-24*density)/bitmap.width,(size.height-top-16*density)/bitmap.height).coerceAtLeast(.01f)
                val scale=fit*zoom
                val centre=Offset(size.width/2f,(size.height+60*density)/2f)+pan
                withTransform({translate(centre.x-bitmap.width*scale/2,centre.y-bitmap.height*scale/2);scale(scale,scale,pivot=Offset.Zero)}) {
                    drawImage(bitmap,colorFilter=filter)
                }
            }
        else Text("This floor drawing couldn’t be opened.",Modifier.align(Alignment.Center),color=c.muted)
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
}

internal expect fun planTimestamp(iso:String):String
