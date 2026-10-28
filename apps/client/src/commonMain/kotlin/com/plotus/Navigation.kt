package com.plotus

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.plotus.shared.PlotStep
import kotlinx.serialization.Serializable

enum class Destination(val path: String) {
    PROJECTS("/projects"), SETTINGS("/settings"), GENERAL("/settings/general"), ACCOUNT("/settings/account"), LOGIN("/login"), PLOT("/projects/:id/plot/:step");
}

@Serializable internal data class AppLocation(val path: String, val next: String? = null)
internal expect fun readAppLocation(): AppLocation?
internal expect fun observeAppLocation(onChange: () -> Unit): () -> Unit
internal expect fun writeAppLocation(path: String, replace: Boolean)

private val plotPathPattern = Regex("^/projects/([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})/plot/(boundary|direction|roads)$")
internal fun plotLocation(path: String?): Pair<String, PlotStep>? = plotPathPattern.matchEntire(path?.trimEnd('/').orEmpty())?.let { match ->
    match.groupValues[1] to PlotStep.entries.first { it.segment == match.groupValues[2] }
}
internal fun destinationForPath(path: String?): Destination? = Destination.entries.firstOrNull { it != Destination.PLOT && it.path == path?.trimEnd('/') }
    ?: plotLocation(path)?.let { Destination.PLOT }
private fun safeAppPath(path: String?): String = path?.trimEnd('/')?.takeIf { destinationForPath(it)?.let { d -> d != Destination.LOGIN } == true } ?: Destination.PROJECTS.path

/** Browser return paths are limited to real app routes, including a specific project's plot step. */
internal class AppNavigation(initial: AppLocation?, private val updateLocation: (String, Boolean) -> Unit = ::writeAppLocation) {
    val browser = initial != null
    var destination by mutableStateOf(destinationForPath(initial?.path) ?: Destination.PROJECTS); private set
    var projectID by mutableStateOf(plotLocation(initial?.path)?.first); private set
    var plotStep by mutableStateOf(plotLocation(initial?.path)?.second ?: PlotStep.BOUNDARY); private set
    private var returnTo = safeAppPath(initial?.next)
    val path: String get() = if (destination == Destination.PLOT) "/projects/$projectID/plot/${plotStep.segment}" else destination.path
    val loginCallbackPath: String get() = "/login?next=$returnTo"

    fun receive(location: AppLocation?) {
        if (location == null) return
        destination = destinationForPath(location.path) ?: Destination.PROJECTS
        val plot = plotLocation(location.path)
        projectID = plot?.first
        plotStep = plot?.second ?: PlotStep.BOUNDARY
        if (destination == Destination.LOGIN) returnTo = safeAppPath(location.next)
    }
    fun navigate(target: Destination, replace: Boolean = false) {
        require(target != Destination.PLOT) { "Open a project with its ID and plot step." }
        if (destination == target && !replace) return
        destination = target; projectID = null
        if (browser) updateLocation(target.path, replace)
    }
    fun openProject(id: String, step: PlotStep = PlotStep.BOUNDARY, replace: Boolean = false) {
        val target = "/projects/$id/plot/${step.segment}"
        require(!browser || plotLocation(target) != null) { "Invalid project route" }
        if (destination == Destination.PLOT && projectID == id && plotStep == step && !replace) return
        destination = Destination.PLOT; projectID = id; plotStep = step
        if (browser) updateLocation(target, replace)
    }
    fun reconcileSession(signedIn: Boolean, initializing: Boolean) {
        if (!browser || initializing) return
        if (signedIn) {
            if (destination == Destination.LOGIN) {
                val target = returnTo
                receive(AppLocation(target)); updateLocation(target, true)
            }
        } else if (destination != Destination.LOGIN) {
            returnTo = path; destination = Destination.LOGIN; projectID = null
            updateLocation(loginCallbackPath, true)
        }
    }
}
