package com.plotus

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.plotus.shared.PlanDrawing
import com.plotus.shared.PlotUnits
import com.plotus.shared.METRES_PER_FOOT
import com.plotus.shared.decimal
import com.plotus.shared.measureLabel
import com.plotus.shared.FloorPlanSetup
import com.plotus.shared.PlanMode
import com.plotus.shared.buildingLabel
import org.jetbrains.compose.resources.decodeToImageBitmap
import kotlin.io.encoding.Base64
import kotlin.math.roundToInt
import org.jetbrains.skia.Data
import org.jetbrains.skia.svg.SVGDOM
import org.jetbrains.skia.svg.SVGLengthContext

/** Read-only drawing on the same canvas as the editor, with independent pan and zoom.
 * Map paper/ink to the active palette without changing the saved engine output.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable internal fun GeneratedPlanCanvas(drawing:PlanDrawing,compact:Boolean,modifier:Modifier=Modifier,roadLabels:List<String> = emptyList(),requirements:FloorPlanSetup?=null) {
    val c=LocalPlotusColors.current
    val presentation=drawing.presentation
    val units=LocalMeasurementUnits.current
    val selectUnits=LocalSetMeasurementUnits.current
    var optionsOpen by remember {mutableStateOf(false)}
    val content=presentation?.pngBase64 ?: drawing.pngBase64
    val fonts by produceState<PlanVectorFonts?>(null,drawing.vectorSvgBase64!=null) {
        if(drawing.vectorSvgBase64!=null) {
            val loaded=runCatching {PlanVectorFonts.load()}.getOrNull()
            value=loaded
            awaitDispose {loaded?.close()}
        }
    }
    val vector=remember(drawing.id,drawing.vectorSvgBase64,fonts,units) {VectorPlan.decode(drawing.vectorSvgBase64,fonts,units)}
    DisposableEffect(vector) {onDispose {vector?.close()}}
    val bitmap=remember(drawing.id,content,vector) {if(vector==null)runCatching {Base64.decode(content).decodeToImageBitmap()}.getOrNull() else null}
    val drawingWidth=vector?.width ?: bitmap?.width?.toFloat() ?: 1f
    val drawingHeight=vector?.height ?: bitmap?.height?.toFloat() ?: 1f
    var size by remember {mutableStateOf(IntSize(1,1))}
    val viewport=rememberCanvasZoom(drawing.id to (drawing.vectorSvgBase64 ?: content))
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
        if(vector!=null||bitmap!=null)Canvas(Modifier.fillMaxSize().semantics {contentDescription="Generated floor plan. Read-only canvas. Drag to pan, pinch or use the toolbar to zoom."+(if(vector!=null)" Vector drawing." else " Image drawing.")}
            .canvasNavigation(viewport,Offset(size.width/2f,(size.height+72*density)/2f))) {
                if(grid) {
                    val spacing=24*density
                    var x=16*density
                    while(x<size.width) {var y=16*density;while(y<size.height){drawCircle(c.border.copy(alpha=.65f),.65f*density,Offset(x,y));y+=spacing};x+=spacing}
                }
                val top=96*density
                val sideMargin=if(presentation?.roadPosition in listOf("west","east"))80 else 32
                val fit=minOf((size.width-sideMargin*density)/drawingWidth,(size.height-top-24*density)/drawingHeight).coerceAtLeast(.01f)
                val view=viewport.view
                val scale=fit*view.zoom
                val centre=Offset(size.width/2f,(size.height+72*density)/2f)+view.pan
                withTransform({translate(centre.x-drawingWidth*scale/2,centre.y-drawingHeight*scale/2);scale(scale,scale,pivot=Offset.Zero)}) {
                    if(vector!=null) {
                        vector.render(drawContext.canvas.nativeCanvas,c.ink.toArgb())
                    } else if(bitmap!=null)drawImage(bitmap,colorFilter=filter)
                }
            }
        else Text("This floor drawing couldn’t be opened.",Modifier.align(Alignment.Center),color=c.muted)
        if(requirements!=null)Column(Modifier.align(Alignment.TopStart).padding(start=16.dp,end=16.dp,top=if(compact)60.dp else 76.dp).widthIn(max=248.dp).background(c.surface,RoundedCornerShape(10.dp)).padding(horizontal=12.dp,vertical=10.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
            var expanded by remember(drawing.id,compact) {mutableStateOf(!compact)}
            requirements?.let {setup->
                val home=setup.homes.firstOrNull()
                Row(Modifier.fillMaxWidth().clickable(role=Role.Button,onClickLabel=if(expanded)"Collapse requirements" else "Expand requirements"){expanded=!expanded},verticalAlignment=Alignment.CenterVertically) {
                    Text("Requirements",Modifier.weight(1f),fontSize=12.sp,fontWeight=FontWeight.SemiBold,color=c.ink)
                    Text(if(expanded)"−" else "+",fontSize=16.sp,color=c.muted)
                }
                Text(listOfNotNull(home?.let {"${it.bhk} BHK"},if(setup.buildingFloors==1)"Ground floor" else buildingLabel(setup.buildingFloors)).joinToString(" · "),fontSize=11.sp,lineHeight=15.sp,color=c.ink)
                if(expanded) {
                    home?.let {
                        Text("${if(it.mode==PlanMode.AUTO)"Auto" else "Custom"} · Vastu: ${if(it.vastu)"Yes" else "No"}",fontSize=11.sp,lineHeight=15.sp,color=c.muted)
                        if(it.mode==PlanMode.CUSTOM&&it.rooms.isNotEmpty())Text(it.rooms.groupBy {room->room.kind}.entries.joinToString(" · ") {entry->"${entry.value.size} ${entry.key.title}"},fontSize=11.sp,lineHeight=15.sp,color=c.muted)
                    }
                    val setback=if(setup.setbackMode==PlanMode.AUTO)"Auto setback" else if(setup.sameSetback)setup.commonSetbackMetres?.let {"Setback ${measureLabel(it,units)}"} else "Custom setbacks"
                    setback?.let {Text(it,fontSize=11.sp,lineHeight=15.sp,color=c.muted)}
                }
            }
        }
        if(presentation!=null&&presentation.version<2) {
            val side=presentation.roadPosition
            val alignment=when(side){"west"->Alignment.CenterStart;"east"->Alignment.CenterEnd;"south"->Alignment.BottomCenter;else->Alignment.TopCenter}
            val label=Modifier.align(alignment).let {m->when(side){
                "west","east"->m.width(120.dp).graphicsLayer {rotationZ=if(side=="west")-90f else 90f;translationX=if(side=="west")-40*density else 40*density}
                "south"->m.padding(bottom=4.dp)
                else->m.padding(top=if(compact)62.dp else 76.dp)
            }}
            Text(roadLabels.firstOrNull()?.substringBefore(" · ") ?: "Road",label.background(c.background,RoundedCornerShape(6.dp)).padding(horizontal=10.dp,vertical=4.dp),fontSize=12.sp,color=c.muted,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
        }
        Row(Modifier.align(Alignment.TopCenter).padding(horizontal=if(compact)12.dp else 20.dp,vertical=if(compact)6.dp else 12.dp)
            .widthIn(max=560.dp).fillMaxWidth().height(if(compact)44.dp else 52.dp).background(c.surface,RoundedCornerShape(12.dp))
            .border(1.dp,c.line,RoundedCornerShape(12.dp)).padding(horizontal=4.dp),verticalAlignment=Alignment.CenterVertically) {
            PlotToolButton(PlotSymbol.HAND,"Pan plan",selected=true) {}
            PlotToolButton(PlotSymbol.GRID,"Toggle guide dots",selected=grid){grid=!grid}
            PlotToolButton(PlotSymbol.ZOOM_IN,"Zoom in"){viewport.step(CanvasZoom.STEP,CanvasZoom.MAXIMUM)}
            PlotToolButton(PlotSymbol.ZOOM_OUT,"Zoom out"){viewport.step(1f/CanvasZoom.STEP,CanvasZoom.MAXIMUM)}
            Text("${(viewport.view.zoom*100).roundToInt()}%",Modifier.weight(1f),fontSize=11.sp,color=c.muted)
            PlotToolButton(PlotSymbol.FIT,"Fit plan to canvas"){viewport.reset()}
            Box {
                PlotToolButton(PlotSymbol.MORE,"Plan canvas options") {optionsOpen=true}
                DropdownMenu(optionsOpen,{optionsOpen=false},containerColor=c.surface,tonalElevation=0.dp) {
                    Text("Units",Modifier.padding(horizontal=12.dp,vertical=8.dp),fontSize=12.sp,color=c.muted)
                    PlotUnits.entries.forEach {unit->DropdownMenuItem(text={Text((if(units==unit)"✓  " else "")+if(unit==PlotUnits.METRES)"Metres" else "Feet & inches")},onClick={selectUnits(unit);optionsOpen=false})}
                }
            }
        }
    }
    if(presentation!=null) {
        androidx.compose.material3.HorizontalDivider(color=c.line)
        Row(Modifier.fillMaxWidth().background(c.surface).padding(horizontal=if(compact)16.dp else 24.dp,vertical=16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
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

/** A retained SVG document; each draw replays real geometry at the canvas transform. */
internal class VectorPlan private constructor(private val document:SVGDOM,val width:Float,val height:Float) {
    private val bounds=org.jetbrains.skia.Rect.makeWH(width,height)
    private val paper=org.jetbrains.skia.Paint().apply {color=-1}
    private val ink=org.jetbrains.skia.Paint()
    private var inkColor:Int?=null

    fun render(canvas:org.jetbrains.skia.Canvas,color:Int=0xff000000.toInt()) {
        if(inkColor!=color) {
            val channels=listOf((color ushr 16 and 255)/255f,(color ushr 8 and 255)/255f,(color and 255)/255f)
            val rows=channels.flatMap {listOf(0f,0f,0f,0f,it)}+listOf(-.2126f,-.7152f,-.0722f,0f,1f)
            val filter=org.jetbrains.skia.ColorFilter.makeMatrix(org.jetbrains.skia.ColorMatrix(*rows.toFloatArray()))
            try {ink.colorFilter=filter} finally {filter.close()}
            inkColor=color
        }
        val saved=canvas.save()
        try {
            canvas.clipRect(bounds)
            canvas.saveLayer(bounds,ink)
            // A white base keeps empty SVG space transparent after the ink filter.
            // The layer uses the current viewport transform, never a cached raster.
            canvas.drawRect(bounds,paper)
            document.render(canvas)
        }finally {canvas.restoreToCount(saved)}
    }
    fun close() {ink.close();paper.close();document.close()}

    companion object {
        fun decode(encoded:String?,fonts:PlanVectorFonts?=null,units:PlotUnits?=null):VectorPlan? {
            if(encoded.isNullOrBlank())return null
            return runCatching {
                val svg=Base64.decode(encoded).decodeToString()
                val display=if(Regex("<text\\b").containsMatchIn(svg))requireNotNull(fonts).outline(svg,units) else svg
                val data=Data.makeFromBytes(display.encodeToByteArray())
                val document=try {SVGDOM(data)} finally {data.close()}
                try {
                    val root=requireNotNull(document.root) {"Missing SVG root"}
                    val size=try {root.getIntrinsicSize(SVGLengthContext(0f,0f))} finally {root.close()}
                    require(size.x.isFinite()&&size.y.isFinite()&&size.x>0&&size.y>0) {"Invalid SVG size"}
                    VectorPlan(document,size.x,size.y)
                }catch(error:Throwable) {document.close();throw error}
            }.getOrNull()
        }
    }
}
