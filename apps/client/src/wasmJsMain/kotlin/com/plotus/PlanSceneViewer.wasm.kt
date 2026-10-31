@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, kotlin.js.ExperimentalWasmJsInterop::class)

package com.plotus

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.WebElementView
import androidx.compose.ui.unit.dp
import com.plotus.shared.PlanScene3D
import kotlinx.browser.document
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.w3c.dom.HTMLElement
import kotlin.JsFun

private val sceneJson = Json { encodeDefaults = true }

@JsFun("(element,payload) => window.PlotusPlanViewerBridge.mount(element,payload)")
private external fun mountScene(element: HTMLElement, payload: String)

@JsFun("(element,payload) => window.PlotusPlanViewerBridge.update(element,payload)")
private external fun updateScene(element: HTMLElement, payload: String)

@JsFun("element => window.PlotusPlanViewerBridge.dispose(element)")
private external fun disposeScene(element: HTMLElement)

internal actual fun supportsPlanScene3D() = true

@Composable
internal actual fun PlanSceneViewer(scene: PlanScene3D, modifier: Modifier, hidden: Boolean) {
    val dark = LocalPlotusColors.current.dark
    val units = LocalMeasurementUnits.current
    val obscured = hidden || LocalEmbeddedViewHidden.current
    val payload = remember(scene, dark, units) {
        buildJsonObject {
            put("scene", sceneJson.encodeToJsonElement(PlanScene3D.serializer(), scene))
            put("theme", if (dark) "dark" else "light")
            put("units", units.name)
        }.toString()
    }
    // Hide the interop layout itself: hiding only its child leaves a transparent
    // HTML wrapper above Compose dialogs that can still consume pointer input.
    Box(modifier) {
    WebElementView(
        factory = {
            (document.createElement("div") as HTMLElement).apply {
                setAttribute("aria-label", "3D floor plan")
                style.width = "100%"
                style.height = "100%"
                style.setProperty("overflow", "hidden")
                style.visibility = if (obscured) "hidden" else "visible"
                mountScene(this, payload)
            }
        },
        modifier = if (obscured) Modifier.size(0.dp) else Modifier.matchParentSize(),
        update = { element ->
            element.style.visibility = if (obscured) "hidden" else "visible"
            element.style.setProperty("pointer-events", if (obscured) "none" else "auto")
            updateScene(element, payload)
        },
        onRelease = { disposeScene(it) },
    )
    }
}
