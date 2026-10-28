package com.plotus

import androidx.compose.ui.input.pointer.PointerEvent
import kotlinx.browser.window
import org.w3c.dom.events.WheelEvent

internal actual fun canvasWheelPixels(event:PointerEvent,density:Float):Float {
    if(event.changes.firstOrNull()?.scrollDelta?.y==0f)return 0f
    val wheel=event.nativeEvent as? WheelEvent ?: return (event.changes.firstOrNull()?.scrollDelta?.y ?: 0f)*density
    val unit=when(wheel.deltaMode.toInt()) {1->40f;2->window.innerHeight.toFloat();else->1f}
    return wheel.deltaY.toFloat()*unit
}
