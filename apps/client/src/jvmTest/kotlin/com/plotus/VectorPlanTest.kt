package com.plotus

import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.io.encoding.Base64
import kotlin.test.*
import org.jetbrains.skia.Surface

class VectorPlanTest {
    @Test fun `retained geometry resolves subpixel details when zoomed`() {
        // These strokes share one pixel at preview size, but are six pixels
        // apart at 16x. A scaled preview cannot reproduce the white gap.
        val svg="""<svg xmlns="http://www.w3.org/2000/svg" width="32" height="16" viewBox="0 0 32 16"><path d="M16.03125 0V16 M16.40625 0V16" fill="none" stroke="black" stroke-width="0.0625"/></svg>"""
        val vector=assertNotNull(VectorPlan.decode(Base64.encode(svg.encodeToByteArray())))
        val surface=Surface.makeRasterN32Premul(512,256)
        try {
            assertEquals(32f,vector.width)
            assertEquals(16f,vector.height)
            surface.canvas.clear(0)
            surface.canvas.scale(16f,16f)
            vector.render(surface.canvas)
            val image=surface.makeImageSnapshot()
            try {
                val data=assertNotNull(image.encodeToData())
                try {
                    val pixels=ImageIO.read(ByteArrayInputStream(data.bytes))
                    assertEquals(0,pixels.getRGB(256,128) and 0xffffff)
                    assertEquals(0,pixels.getRGB(259,128))
                    assertEquals(255,pixels.getRGB(256,128) ushr 24)
                    assertEquals(0,pixels.getRGB(262,128) and 0xffffff)
                }finally {data.close()}
            }finally {image.close()}
            surface.canvas.clear(0)
            val darkInk=0xffe9e5dd.toInt()
            vector.render(surface.canvas,darkInk)
            val dark=surface.makeImageSnapshot()
            try {
                val data=assertNotNull(dark.encodeToData())
                try {
                    val pixels=ImageIO.read(ByteArrayInputStream(data.bytes))
                    assertEquals(darkInk,pixels.getRGB(256,128))
                    assertEquals(0,pixels.getRGB(259,128))
                }finally {data.close()}
            }finally {dark.close()}
        }finally {surface.close();vector.close()}
    }

    @Test fun `bundled fonts retain room labels and multiline dimensions as vectors`() {
        val base=java.nio.file.Path.of("src/commonMain/composeResources/font")
        val fonts=PlanVectorFonts.decode(java.nio.file.Files.readAllBytes(base.resolve("jakarta_regular.ttf")),java.nio.file.Files.readAllBytes(base.resolve("jakarta_semibold.ttf")))
        val svg="""<svg xmlns="http://www.w3.org/2000/svg" width="32" height="16"><text x="16" y="5" font-size="2" text-anchor="middle"><tspan x="16" dy="0">BEDROOM 1</tspan><tspan x="16" dy="5">12′3″ × 13′6″ &amp; A</tspan></text></svg>"""
        val surface=Surface.makeRasterN32Premul(256,128)
        try {
            val outlined=fonts.outline(svg)
            assertFalse(outlined.contains("<text"))
            assertFalse(outlined.contains("<tspan"))
            assertTrue(outlined.contains("<path"))
            val vector=assertNotNull(VectorPlan.decode(Base64.encode(svg.encodeToByteArray()),fonts))
            try {
                surface.canvas.clear(0);surface.canvas.scale(8f,8f);vector.render(surface.canvas)
                val image=surface.makeImageSnapshot()
                try {
                    val data=assertNotNull(image.encodeToData())
                    try {
                        val pixels=ImageIO.read(ByteArrayInputStream(data.bytes))
                        fun ink(top:Int,bottom:Int)=(top until bottom).sumOf {y->(0 until 256).count {x->pixels.getRGB(x,y) ushr 24>128}}
                        assertTrue(ink(16,48)>100,"Room label is missing")
                        assertTrue(ink(56,88)>100,"Multiline dimensions are missing")
                    }finally {data.close()}
                }finally {image.close()}
            }finally {vector.close()}
        }finally {surface.close();fonts.close()}
    }

    @Test fun `small plan labels retain their spacing and centered anchor at high zoom`() {
        val base=java.nio.file.Path.of("src/commonMain/composeResources/font")
        val bytes=java.nio.file.Files.readAllBytes(base.resolve("jakarta_semibold.ttf"))
        val fonts=PlanVectorFonts.decode(bytes,bytes)
        val svg="""<svg xmlns="http://www.w3.org/2000/svg" width="32" height="16"><text x="16" y="5" font-size="0.7" font-weight="600" text-anchor="middle">BEDROOM 1</text></svg>"""
        val surface=Surface.makeRasterN32Premul(1024,512)
        try {
            val vector=assertNotNull(VectorPlan.decode(Base64.encode(svg.encodeToByteArray()),fonts))
            try {
                surface.canvas.clear(0);surface.canvas.scale(32f,32f);vector.render(surface.canvas)
                val image=surface.makeImageSnapshot()
                try {
                    val data=assertNotNull(image.encodeToData())
                    try {
                        val pixels=ImageIO.read(ByteArrayInputStream(data.bytes))
                        val columns=(0 until 1024).filter {x->(128 until 170).any {y->pixels.getRGB(x,y) ushr 24>128}}
                        assertTrue(columns.isNotEmpty())
                        assertTrue(columns.last()-columns.first() in 90..160,"Glyph advances are distorted")
                        assertTrue((columns.first()+columns.last())/2 in 504..520,"Centered label is misplaced")
                    }finally {data.close()}
                }finally {image.close()}
            }finally {vector.close()}
        }finally {surface.close();fonts.close()}
    }

    @Test fun `measurement labels switch units using the saved dimensions`() {
        val attrs=mapOf("data-space-dimensions" to "bedroom","data-width-mm" to "3048","data-depth-mm" to "3657.6")
        assertEquals("10′ 0″ × 12′ 0″",planMeasurementText(attrs,com.plotus.shared.PlotUnits.FEET_INCHES))
        assertEquals("3.05 m × 3.66 m",planMeasurementText(attrs,com.plotus.shared.PlotUnits.METRES))
        assertNull(planMeasurementText(emptyMap(),com.plotus.shared.PlotUnits.METRES))
    }

    @Test fun `absent or corrupt vector payload permits the image fallback`() {
        assertNull(VectorPlan.decode(null))
        assertNull(VectorPlan.decode(""))
        assertNull(VectorPlan.decode("not base64!"))
    }
}
