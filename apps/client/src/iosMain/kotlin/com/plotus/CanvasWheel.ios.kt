package com.plotus

import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.geometry.Offset

internal actual fun canvasWheelInput(event:PointerEvent,density:Float)=CanvasWheelInput(
    (event.changes.firstOrNull()?.scrollDelta ?: Offset.Zero)/density,
    event.keyboardModifiers.isCtrlPressed||event.keyboardModifiers.isMetaPressed,event.keyboardModifiers.isShiftPressed)
