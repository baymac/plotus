package com.plotus

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.plotus.shared.PlotStep
import com.plotus.shared.FloorStep
import com.plotus.shared.PlanningSection
import kotlinx.serialization.Serializable

enum class Destination(val path: String) {
    PROJECTS("/projects"), SETTINGS("/settings"), GENERAL("/settings/general"), ACCOUNT("/settings/account"), LOGIN("/login"), PLOT("/projects/:id/plot/:step"), FLOOR_PLAN("/projects/:id/floor-plan/:step");
}

@Serializable internal data class AppLocation(val path: String, val next: String? = null)
internal expect fun readAppLocation(): AppLocation?
internal expect fun observeAppLocation(onChange: () -> Unit): () -> Unit
internal expect fun writeAppLocation(path: String, replace: Boolean)

private val plotPathPattern = Regex("^/projects/([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})/plot(?:/(boundary|direction|roads))?$")
internal fun plotLocation(path: String?): Pair<String, PlotStep?>? = plotPathPattern.matchEntire(path?.trimEnd('/').orEmpty())?.let { match ->
    match.groupValues[1] to PlotStep.entries.firstOrNull { it.segment == match.groupValues[2] }
}
private const val routeUUID="[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"
private val floorPathPattern=Regex("^/projects/($routeUUID)/floor-plan(?:/(setbacks|requirements|history)(?:/($routeUUID))?)?$")
private fun floorMatch(path:String?)=floorPathPattern.matchEntire(path?.trimEnd('/').orEmpty())?.takeIf {it.groupValues[3].isEmpty()||it.groupValues[2]=="history"}
internal fun floorLocation(path:String?):Pair<String,FloorStep?>?=floorMatch(path)?.let {match->match.groupValues[1] to FloorStep.entries.firstOrNull {it.segment==match.groupValues[2]}}
internal fun floorGeneration(path:String?):String?=floorMatch(path)?.groupValues?.get(3)?.ifEmpty {null}
internal fun destinationForPath(path: String?): Destination? = Destination.entries.firstOrNull { it !in listOf(Destination.PLOT,Destination.FLOOR_PLAN) && it.path == path?.trimEnd('/') }
    ?: plotLocation(path)?.let { Destination.PLOT } ?: floorLocation(path)?.let {Destination.FLOOR_PLAN}
private fun overviewPath(path:String):String = plotLocation(path)?.let {"/projects/${it.first}/plot"}
    ?: floorLocation(path)?.let {"/projects/${it.first}/floor-plan"} ?: path
private fun safeAppPath(path: String?): String = path?.trimEnd('/')?.takeIf { destinationForPath(it)?.let { d -> d != Destination.LOGIN } == true }?.let(::overviewPath) ?: Destination.PROJECTS.path

/** Browser return paths are limited to real app routes, including a specific project's plot step. */
internal class AppNavigation(initial: AppLocation?, private val updateLocation: (String, Boolean) -> Unit = ::writeAppLocation) {
    val browser = initial != null
    var destination by mutableStateOf(destinationForPath(initial?.path) ?: Destination.PROJECTS); private set
    var projectID by mutableStateOf(plotLocation(initial?.path)?.first ?: floorLocation(initial?.path)?.first); private set
    var plotStep by mutableStateOf<PlotStep?>(null); private set
    var floorStep by mutableStateOf<FloorStep?>(null);private set
    var generationID by mutableStateOf<String?>(null);private set
    private var returnTo = safeAppPath(initial?.next)
    val path: String get() = when(destination) {Destination.PLOT->"/projects/$projectID/plot" + (plotStep?.let { "/${it.segment}" } ?: "");Destination.FLOOR_PLAN->"/projects/$projectID/floor-plan"+(floorStep?.let {"/${it.segment}"} ?: "")+(generationID?.let {"/$it"} ?: "");else->destination.path}
    init {
        if(browser&&projectID!=null&&initial?.path?.trimEnd('/')!=path)updateLocation(path,true)
    }
    fun openOverview(id:String,section:PlanningSection) {
        when(section){PlanningSection.PLOT->openProject(id);PlanningSection.FLOOR_PLAN->openFloor(id)}
    }
    val loginCallbackPath: String get() = "/login?next=$returnTo"

    fun receive(location: AppLocation?) {
        if (location == null) return
        val target=destinationForPath(location.path) ?: Destination.PROJECTS
        val plot = plotLocation(location.path)
        val floor=floorLocation(location.path)
        val id=plot?.first ?: floor?.first
        val reopening=id!=null&&(destination!=target||projectID!=id)
        destination=target;projectID=id
        plotStep=if(reopening)null else plot?.second
        floorStep=if(reopening)null else floor?.second
        generationID=if(reopening)null else floorGeneration(location.path)
        if(reopening&&location.path.trimEnd('/')!=path)updateLocation(path,true)
        if (destination == Destination.LOGIN) returnTo = safeAppPath(location.next)
    }
    fun navigate(target: Destination, replace: Boolean = false) {
        require(target !in listOf(Destination.PLOT,Destination.FLOOR_PLAN)) { "Open a project with its ID and planning step." }
        if (destination == target && !replace) return
        destination = target; projectID = null
        if (browser) updateLocation(target.path, replace)
    }
    fun openProject(id: String, step: PlotStep? = null, replace: Boolean = false) {
        val target = "/projects/$id/plot" + (step?.let { "/${it.segment}" } ?: "")
        require(!browser || plotLocation(target) != null) { "Invalid project route" }
        if (destination == Destination.PLOT && projectID == id && plotStep == step && !replace) return
        destination = Destination.PLOT; projectID = id; plotStep = step;floorStep=null;generationID=null
        if (browser) updateLocation(target, replace)
    }
    fun openFloor(id:String,step:FloorStep?=null,replace:Boolean=false,generation:String?=null) {
        require(generation==null||step==FloorStep.HISTORY)
        val target="/projects/$id/floor-plan"+(step?.let {"/${it.segment}"} ?: "")+(generation?.let {"/$it"} ?: "")
        require(!browser||floorLocation(target)!=null) {"Invalid floor-plan route"}
        if(destination==Destination.FLOOR_PLAN&&projectID==id&&floorStep==step&&generationID==generation&&!replace)return
        destination=Destination.FLOOR_PLAN;projectID=id;floorStep=step;generationID=generation;plotStep=null
        if(browser)updateLocation(target,replace)
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
