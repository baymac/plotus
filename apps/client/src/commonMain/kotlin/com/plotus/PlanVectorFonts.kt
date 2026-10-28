package com.plotus

import com.plotus.resources.Res
import com.plotus.shared.PlotUnits
import com.plotus.shared.measureLabel
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.skia.*
import org.jetbrains.skia.svg.SVGCanvas

/** Outline saved plan labels with bundled fonts; SVGDOM has no text font manager.
 * The saved SVG/export remains untouched. Only its display document is prepared.
 */
internal class PlanVectorFonts private constructor(private val regular:Typeface,private val strong:Typeface) {
    fun close() {regular.close();strong.close()}

    fun outline(svg:String,units:PlotUnits?=null):String = textElement.replace(svg) { match ->
        val attrs=attributes(match.groupValues[1])
        val stream=DynamicMemoryWStream()
        try {
            val canvas=SVGCanvas.make(Rect.makeWH(100000f,100000f),stream,true,false)
            val paint=Paint().apply {isAntiAlias=true}
            try {
                val weight=attrs["font-weight"]?.toIntOrNull() ?: 400
                // Work at a full font size before scaling to plan units. Tiny SVG
                // font sizes otherwise quantize glyph advances and overlap dimensions.
                val fontScale=(number(attrs["font-size"]) ?: 12f)/64f
                val font=Font(if(weight>=600)strong else regular,64f).apply {
                    isSubpixel=true;isLinearMetrics=true;hinting=FontHinting.NONE
                }
                try {
                    var x=number(attrs["x"]) ?: 0f
                    var y=number(attrs["y"]) ?: 0f
                    fun line(text:String,spanAttrs:Map<String,String>) {
                        x=number(spanAttrs["x"]) ?: x
                        y=(number(spanAttrs["y"]) ?: y)+(number(spanAttrs["dy"]) ?: 0f)
                        val value=unescape(text)
                        val width=font.measureTextWidth(value)*fontScale
                        val left=x-when(attrs["text-anchor"]) {"middle"->width/2;"end"->width;else->0f}
                        val baseline=y+if(attrs["dominant-baseline"] in listOf("middle","central"))font.metrics.xHeight*fontScale/2 else 0f
                        val saved=canvas.save()
                        try {
                            canvas.translate(left,baseline);canvas.scale(fontScale,fontScale)
                            canvas.drawString(value,0f,0f,font,paint)
                        }finally {canvas.restoreToCount(saved)}
                    }
                    val body=planMeasurementText(attrs,units) ?: match.groupValues[2]
                    val spans=spanElement.findAll(body).toList()
                    if(spans.isEmpty())line(body,emptyMap())
                    else spans.forEach {line(it.groupValues[2],attributes(it.groupValues[1]))}
                }finally {font.close()}
            }finally {paint.close();canvas.close()}
            val bytes=ByteArray(stream.bytesWritten())
            check(stream.read(bytes,0,bytes.size))
            val outlined=bytes.decodeToString()
            val start=outlined.indexOf('>',outlined.indexOf("<svg"))+1
            val end=outlined.lastIndexOf("</svg>")
            require(start>0&&end>=start)
            // Preserve parent transforms, fill and saved label metadata.
            "<g${match.groupValues[1]}>${outlined.substring(start,end)}</g>"
        }finally {stream.close()}
    }

    companion object {
        private val textElement=Regex("<text\\b([^>]*)>([\\s\\S]*?)</text>")
        private val spanElement=Regex("<tspan\\b([^>]*)>([\\s\\S]*?)</tspan>")
        private val attribute=Regex("([\\w:-]+)\\s*=\\s*[\"']([^\"']*)[\"']")
        private val entity=Regex("&(#x[0-9a-fA-F]+|#[0-9]+|amp|lt|gt|quot|apos);")
        private fun attributes(value:String)=attribute.findAll(value).associate {it.groupValues[1] to it.groupValues[2]}
        private fun number(value:String?)=value?.trim()?.substringBefore(' ')?.toFloatOrNull()
        private fun unescape(value:String)=entity.replace(value) {
            when(val token=it.groupValues[1]) {"amp"->"&";"lt"->"<";"gt"->">";"quot"->"\"";"apos"->"'";else->(if(token.startsWith("#x"))token.drop(2).toInt(16) else token.drop(1).toInt()).toChar().toString()}
        }
        fun decode(regular:ByteArray,strong:ByteArray):PlanVectorFonts {
            fun face(bytes:ByteArray):Typeface {val data=Data.makeFromBytes(bytes);return try {requireNotNull(FontMgr.default.makeFromData(data))} finally {data.close()}}
            val first=face(regular)
            return try {PlanVectorFonts(first,face(strong))}catch(error:Throwable){first.close();throw error}
        }
        @OptIn(ExperimentalResourceApi::class)
        suspend fun load()=decode(Res.readBytes("font/jakarta_regular.ttf"),Res.readBytes("font/jakarta_semibold.ttf"))
    }
}

/** Saved millimetre values, rather than rounded printed labels, drive unit changes. */
internal fun planMeasurementText(attributes:Map<String,String>,units:PlotUnits?):String? {
    if(units==null)return null
    val width=attributes["data-width-mm"]?.toDoubleOrNull()
    val depth=attributes["data-depth-mm"]?.toDoubleOrNull()
    if(attributes.containsKey("data-space-dimensions")&&width!=null&&depth!=null)
        return "${measureLabel(width/1000,units)} × ${measureLabel(depth/1000,units)}"
    return attributes["data-value-mm"]?.toDoubleOrNull()?.let {measureLabel(it/1000,units)}
}
