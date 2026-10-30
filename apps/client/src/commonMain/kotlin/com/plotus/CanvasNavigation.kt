package com.plotus

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.*
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalDensity

/** One camera interaction for editors and generated drawings. Editing is single-pointer only. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun Modifier.canvasNavigation(
    viewport:CanvasZoom,
    origin:Offset,
    onEditStart:(Offset)->Boolean={false},
    onEditDrag:(Offset)->Unit={},
    onEditEnd:()->Unit={},
):Modifier {
    val density=LocalDensity.current.density
    val focus=remember {FocusRequester()}
    var space by remember {mutableStateOf(false)}
    val currentOrigin by rememberUpdatedState(origin)
    val currentSpace by rememberUpdatedState(space)
    val startEdit by rememberUpdatedState(onEditStart)
    val dragEdit by rememberUpdatedState(onEditDrag)
    val endEdit by rememberUpdatedState(onEditEnd)
    return this.onPreviewKeyEvent {event->
        if(event.key==Key.Spacebar) {
            space=event.type==KeyEventType.KeyDown
            true
        } else false
    }.focusRequester(focus).onFocusChanged {if(!it.isFocused)space=false}.focusable()
        .onPointerEvent(PointerEventType.Scroll) {event->
            val change=event.changes.firstOrNull() ?: return@onPointerEvent
            val input=canvasWheelInput(event,density)
            if(input.delta!=Offset.Zero) {
                if(input.zoom)viewport.scroll(input.delta.y,change.position,currentOrigin,CanvasZoom.MAXIMUM)
                else {
                    val delta=if(input.horizontal&&input.delta.x==0f)Offset(input.delta.y,0f) else input.delta
                    viewport.pan(delta*(-density),smooth=true)
                }
                event.changes.forEach {it.consume()}
            }
        }.pointerInput(viewport) {
            awaitEachGesture {
                val first=awaitFirstDown(requireUnconsumed=false)
                focus.requestFocus()
                val forcePan=currentSpace||currentEvent.buttons.isTertiaryPressed
                var captured=false
                var multiple=false
                var editing=false
                try {
                    do {
                        val event=awaitPointerEvent()
                        if(event.type==PointerEventType.Scroll)continue
                        val pressed=event.changes.count {it.pressed}
                        if(pressed>1) {
                            multiple=true
                            if(editing){endEdit();editing=false}
                        }
                        if(multiple) {
                            if(event.changes.any {it.positionChanged()}) {
                                val centre=event.calculateCentroid(useCurrent=false)
                                if(centre!=Offset.Unspecified)viewport.gesture(centre,event.calculatePan(),
                                    event.calculateZoom(),currentOrigin,CanvasZoom.MAXIMUM)
                                event.changes.forEach {it.consume()}
                                captured=true
                            }
                        } else {
                            val change=event.changes.firstOrNull {it.id==first.id} ?: break
                            if(!captured&&(change.position-first.position).getDistance()>viewConfiguration.touchSlop) {
                                captured=true
                                editing=!forcePan&&startEdit(first.position)
                            }
                            if(captured&&change.pressed) {
                                if(editing)dragEdit(change.position)
                                else viewport.pan(change.positionChange())
                                change.consume()
                            }
                        }
                    }while(event.changes.any {it.pressed})
                }finally {if(editing)endEdit()}
            }
        }
}
