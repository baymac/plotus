package com.plotus

import androidx.compose.runtime.*
import com.plotus.shared.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.decodeFromJsonElement

/** Account-owned projects. Design fixtures never enter this state. */
class ProjectWorkspace(private val auth: AuthSession, private val scope: CoroutineScope, private val owner: String,private val displayUnits:()->PlotUnits={PlotUnits.FEET_INCHES}) {
    var projects by mutableStateOf<List<Project>>(emptyList()); private set
    var loading by mutableStateOf(true); private set
    var busy by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var mutationError by mutableStateOf<String?>(null); private set
    var mutationErrorCode by mutableStateOf<String?>(null); private set
    private val lock = Mutex()
    private val plotEditors = mutableMapOf<String, PlotWorkspace>()
    private val floorEditors = mutableMapOf<String, FloorWorkspace>()
    fun floor(id:String):FloorWorkspace=floorEditors.getOrPut(id) {
        liveFloorWorkspace(auth,scope,owner,displayUnits) {project->if(auth.user?.id==owner)projects=saveProject(projects,project)}
    }

    fun plot(id: String): PlotWorkspace = plotEditors.getOrPut(id) {
        livePlotWorkspace(auth, scope, owner, id) { project ->
            if (auth.user?.id == owner) projects = saveProject(projects, project)
        }
    }

    suspend fun refresh() = lock.withLock {
        if (auth.user?.id != owner) return@withLock
        loading = true
        try {
            val result = auth.projectRequest("GET","/v1/projects")
            if (auth.user?.id == owner) { projects = authJson.decodeFromJsonElement<ProjectList>(result).projects; error = null }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { error = failureMessage(failure) }
        finally { loading = false }
    }
    fun reload() { scope.launch { refresh() } }
    fun clearMutationError() { mutationError = null;mutationErrorCode=null }

    fun save(existing: Project?, draft: ProjectDraft, onSaved: (Project) -> Unit) = mutate {
        val path = if (existing == null) "/v1/projects" else "/v1/projects/${existing.id}"
        val result = auth.projectRequest(if(existing==null) "POST" else "PUT",path,
            authJson.encodeToString(SaveProjectRequest(draft,existing?.revision)))
        val project = authJson.decodeFromJsonElement<Project>(result)
        if (auth.user?.id == owner) { projects = saveProject(projects,project); error = null; onSaved(project) }
    }
    fun delete(project: Project, onDeleted: () -> Unit) = mutate {
        val selected=projects.firstOrNull {it.id==project.id} ?: project
        val changed=confirmProjectDeletion(
            {auth.projectRequest("DELETE","/v1/projects/${project.id}",authJson.encodeToString(DeleteProjectRequest(selected.revision)))},
            {authJson.decodeFromJsonElement<Project>(auth.projectRequest("GET","/v1/projects/${project.id}"))})
        if(auth.user?.id!=owner)return@mutate
        if(changed!=null) {
            projects=saveProject(projects,changed)
            throw AuthFailure(409,"Project changed.","PROJECT_CHANGED")
        }
        projects = projects.filterNot { it.id==project.id }; onDeleted()
    }
    private fun mutate(operation: suspend () -> Unit) {
        if (busy || auth.user?.id != owner) return
        busy = true; mutationError = null;mutationErrorCode=null
        scope.launch {
            try { lock.withLock { operation() } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { mutationError = failureMessage(failure);mutationErrorCode=(failure as? AuthFailure)?.code }
            finally { busy = false }
        }
    }
    private fun failureMessage(failure: Exception) = (failure as? AuthFailure)?.message
        ?: "Couldn’t connect to your projects. Please try again."
}
