package com.plotus

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.test.*

class CanvasZoomTest {
    private fun settle(state:CanvasZoom) {repeat(120) {state.advance(16f)}}
    @Test fun `a wheel notch is gentle and progresses across frames`() {
        val state=CanvasZoom()
        state.scroll(-120f,Offset.Zero,Offset.Zero,32f)
        assertEquals(1f,state.view.zoom)
        assertTrue(state.target.zoom in 1.1f..1.15f)
        state.advance(16f)
        assertTrue(state.view.zoom>1f&&state.view.zoom<state.target.zoom)
        val first=state.view.zoom;state.advance(16f);assertTrue(state.view.zoom>first)
        settle(state);assertEquals(state.target,state.view)
    }
    @Test fun `zoom stays anchored to the pointer during the transition`() {
        val state=CanvasZoom();val anchor=Offset(180f,90f);val origin=Offset(400f,300f)
        state.scroll(-120f,anchor,origin,32f)
        repeat(30) {
            state.advance(16f)
            val model=(anchor-origin-state.view.pan)/state.view.zoom
            assertTrue((model-(anchor-origin)).getDistance()<.001f)
        }
    }
    @Test fun `wheel bursts accumulate and reverse without overshooting`() {
        val state=CanvasZoom()
        repeat(50) {state.scroll(-2f,Offset.Zero,Offset.Zero,32f);state.advance(4f)}
        assertTrue(state.target.zoom in 1.1f..1.11f)
        repeat(50) {state.scroll(2f,Offset.Zero,Offset.Zero,32f)}
        settle(state);assertTrue(abs(state.view.zoom-1f)<.0001f)
        state.scroll(-100000f,Offset.Zero,Offset.Zero,32f)
        assertTrue(state.target.zoom<1.15f)
        repeat(100) {state.scroll(-120f,Offset.Zero,Offset.Zero,32f)}
        settle(state);assertEquals(32f,state.view.zoom)
    }
    @Test fun `fit and direct gestures interrupt pending wheel motion`() {
        val state=CanvasZoom();state.scroll(-120f,Offset.Zero,Offset.Zero,32f);state.advance(16f)
        val before=state.view.zoom
        state.gesture(Offset.Zero,Offset(20f,30f),1f,Offset.Zero,32f)
        assertEquals(before,state.view.zoom);assertEquals(Offset(20f,30f),state.view.pan)
        assertFalse(state.moving)
        state.scroll(-120f,Offset.Zero,Offset.Zero,32f);state.reset();settle(state)
        assertEquals(CanvasView(),state.view)
    }
    @Test fun `reduced motion finishes in the next frame`() {
        val state=CanvasZoom();state.step(1.15f,32f);state.advance(16f,0f)
        assertEquals(state.target,state.view);assertFalse(state.moving)
    }
}
