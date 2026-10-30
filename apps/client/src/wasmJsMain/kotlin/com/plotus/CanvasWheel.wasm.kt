package com.plotus

import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.geometry.Offset
import kotlinx.browser.window
import org.w3c.dom.events.WheelEvent

internal actual fun canvasWheelInput(event:PointerEvent,density:Float):CanvasWheelInput {
    val wheel=event.nativeEvent as? WheelEvent ?: return CanvasWheelInput(
        (event.changes.firstOrNull()?.scrollDelta ?: Offset.Zero)/density,
        event.keyboardModifiers.isCtrlPressed||event.keyboardModifiers.isMetaPressed,event.keyboardModifiers.isShiftPressed)
    val unit=when(wheel.deltaMode.toInt()) {1->40f;2->window.innerHeight.toFloat();else->1f}
    // Consume browser pinch/page-zoom only after Compose hit-tests this canvas.
    // Forms and page scrolling outside the drawing retain their normal behavior.
    wheel.preventDefault()
    return CanvasWheelInput(Offset(wheel.deltaX.toFloat(),wheel.deltaY.toFloat())*unit,
        wheel.ctrlKey||wheel.metaKey,wheel.shiftKey)
}
