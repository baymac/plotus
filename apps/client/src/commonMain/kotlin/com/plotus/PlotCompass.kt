package com.plotus

import androidx.compose.foundation.Canvas
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import kotlin.math.*

@Composable internal fun PlotCompass(northDegrees:Double,modifier:Modifier=Modifier) {
    val c=LocalPlotusColors.current
    val measurer=rememberTextMeasurer();val style=LocalTextStyle.current;val density=LocalDensity.current.density
    Canvas(modifier.semantics {contentDescription="Compass. North ${northDegrees.toInt()} degrees."}) {
        drawPlotCompass(Offset(size.width/2,size.height/2),minOf(size.width,size.height)/2-2*density,northDegrees,density,c,measurer,style)
    }
}

internal fun DrawScope.drawPlotCompass(centre:Offset,r:Float,northDegrees:Double,density:Float,c:PlotusColors,measurer:TextMeasurer,baseStyle:TextStyle) {
                    val compassScale=(r/(43*density)).coerceAtMost(1.5f)
                    val fontSize=(11*compassScale).coerceIn(7f,14f).sp
                    drawCircle(c.surface,r,centre);drawCircle(c.line,r,centre,style=Stroke(density))
                    val north=northDegrees*PI/180-PI/2
                    repeat(if(r>28*density)60 else 24) { i ->
                        val count=if(r>28*density)60 else 24
                        val a=i*2*PI/count;val n=Offset(cos(a).toFloat(),sin(a).toFloat())
                        drawLine(c.border,centre+n*(r-3*density*compassScale),centre+n*r,density)
                    }
                    if(r>=20*density)listOf("N","E","S","W").forEachIndexed { i,label ->
                        val a=north+i*PI/2;val at=centre+Offset(cos(a).toFloat(),sin(a).toFloat())*(r-12*density*compassScale)
                        val layout=measurer.measure(label,baseStyle.copy(fontSize=fontSize,color=if(i==0)c.primary else c.muted))
                        drawText(layout,topLeft=at-Offset(layout.size.width/2f,layout.size.height/2f))
                    }
                    val n=Offset(cos(north).toFloat(),sin(north).toFloat());val cross=Offset(-n.y,n.x)
                    val needle=r*.32f
                    drawLine(c.ink,centre-n*needle,centre+n*needle,1.7f*density*compassScale)
                    val tip=centre+n*needle
                    drawLine(c.primary,tip,tip-n*needle*.5f+cross*needle*.23f,1.6f*density*compassScale)
                    drawLine(c.primary,tip,tip-n*needle*.5f-cross*needle*.23f,1.6f*density*compassScale)
}
