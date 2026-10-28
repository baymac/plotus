package com.plotus

import androidx.compose.runtime.*
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEvent
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.first
import kotlin.math.abs
import kotlin.math.exp

internal data class CanvasView(val zoom:Float=1f,val pan:Offset=Offset.Zero)

/** Coalesce wheel input into a short, cursor-anchored transition. Direct gestures snap. */
@Stable
internal class CanvasZoom {
    var view by mutableStateOf(CanvasView())
        private set
    var target by mutableStateOf(CanvasView())
        private set
    val moving get()=view!=target

    fun scroll(pixels:Float,anchor:Offset,origin:Offset,maximum:Float) {
        if(!pixels.isFinite()||pixels==0f)return
        val factor=exp(-pixels.coerceIn(-120f,120f)*.001f)
        zoomTo(target.zoom*factor,anchor,origin,maximum)
    }
    fun step(factor:Float,maximum:Float) {zoomTo(target.zoom*factor,Offset.Zero,Offset.Zero,maximum)}
    private fun zoomTo(zoom:Float,anchor:Offset,origin:Offset,maximum:Float) {
        val next=zoom.coerceIn(.5f,maximum)
        target=CanvasView(next,(target.pan+origin-anchor)*(next/target.zoom)+anchor-origin)
    }
    fun gesture(anchor:Offset,delta:Offset,factor:Float,origin:Offset,maximum:Float) {
        val next=(view.zoom*factor).coerceIn(.5f,maximum)
        view=CanvasView(next,(view.pan+origin-anchor)*(next/view.zoom)+anchor-origin+delta)
        target=view
    }
    fun reset() {view=CanvasView();target=view}

    fun advance(milliseconds:Float,durationScale:Float=1f) {
        if(!moving)return
        if(durationScale<=0f) {view=target;return}
        val amount=1f-exp(-milliseconds.coerceIn(0f,50f)/(45f*durationScale))
        val next=CanvasView(view.zoom+(target.zoom-view.zoom)*amount,view.pan+(target.pan-view.pan)*amount)
        view=if(abs(next.zoom-target.zoom)<target.zoom*.0001f&&(next.pan-target.pan).getDistance()<.1f)target else next
    }
}

@Composable
internal fun rememberCanvasZoom(key:Any):CanvasZoom {
    val state=remember(key) {CanvasZoom()}
    LaunchedEffect(state) {
        val motionScale=currentCoroutineContext()[MotionDurationScale]
        while(true) {
            snapshotFlow {state.moving}.first {it}
            var previous=0L
            while(state.moving)withFrameNanos {now->
                val elapsed=if(previous==0L)16f else (now-previous)/1_000_000f
                previous=now
                state.advance(elapsed,motionScale?.scaleFactor ?: 1f)
            }
        }
    }
    return state
}

internal expect fun canvasWheelPixels(event:PointerEvent,density:Float):Float
