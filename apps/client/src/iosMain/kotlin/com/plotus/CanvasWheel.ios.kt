package com.plotus

import androidx.compose.ui.input.pointer.PointerEvent

internal actual fun canvasWheelPixels(event:PointerEvent,density:Float):Float = (event.changes.firstOrNull()?.scrollDelta?.y ?: 0f)*density
