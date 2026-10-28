package com.plotus

import androidx.compose.runtime.*
import com.plotus.shared.*
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.decodeFromJsonElement
import kotlin.math.abs

/** Retained per project, so a Back/Forward trip does not discard an unsaved boundary. */
class PlotWorkspace(
    private val scope: CoroutineScope,
    private val read: suspend () -> Project,
    private val write: suspend (Project, PlotSetup) -> Project,
    private val accepted: (Project) -> Unit,
    private val extract: (suspend (SketchImage) -> SketchSuggestion)? = null,
) {
    var project by mutableStateOf<Project?>(null); private set
    var draft by mutableStateOf(PlotSetup()); private set
    private var saved by mutableStateOf(PlotSetup())
    var loading by mutableStateOf(true); private set
    var saving by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var conflict by mutableStateOf(false); private set
    var validation by mutableStateOf<Map<String,String>>(emptyMap()); private set
    var directionText by mutableStateOf("0"); private set
    var readingSketch by mutableStateOf(false); private set
    var sketchError by mutableStateOf<String?>(null); private set
    var sketchSuggestion by mutableStateOf<SketchSuggestion?>(null); private set
    var sketchNotes by mutableStateOf<List<String>>(emptyList()); private set
    var validationAttempt by mutableStateOf(0); private set
    private var requireBoundary = false
    private var requireRoads = false
    private var undo by mutableStateOf<List<PlotSetup>>(emptyList())
    private var redo by mutableStateOf<List<PlotSetup>>(emptyList())
    val dirty get() = draft != saved || directionText.toDoubleOrNull()?.let {abs(it-draft.northDegrees)>.005} != false
    val canUndo get() = undo.isNotEmpty()
    val canRedo get() = redo.isNotEmpty()

    suspend fun load(force: Boolean = false) {
        if (saving || (dirty && !force)) return
        loading = true; error = null
        try {
            val loaded = read()
            project = loaded; draft = loaded.plotSetup ?: PlotSetup(); saved = draft
            directionText = decimal(draft.northDegrees)
            requireBoundary=false;requireRoads=false;sketchNotes=emptyList()
            undo = emptyList(); redo = emptyList(); validation = emptyMap(); conflict = false
            accepted(loaded)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { error = failure.message ?: "Couldn’t open this project. Please try again." }
        finally { loading = false }
    }
    fun change(next: PlotSetup, remember: Boolean = true) {
        if (loading || saving || next == draft) return
        if (remember) { undo = (undo + draft).takeLast(60); redo = emptyList() }
        if(next.northDegrees != draft.northDegrees) directionText = decimal(next.northDegrees)
        draft = next
        val complete=next.lengths.all {it!=null} && (!next.useDiagonals||next.diagonals.all {it!=null})
        validation = next.errors(requireBoundary||complete,requireRoads)
    }
    fun boundaryError(message:String) { validation=validation + ("boundary" to message) }
    fun dismissSketch() {sketchSuggestion=null;sketchError=null}
    fun applySketch() {
        val suggestion=sketchSuggestion ?: return
        change(suggestion.setup)
        requireBoundary=true;validation=draft.errors(completeBoundary=true);validationAttempt++
        sketchNotes=suggestion.notes.ifEmpty {listOf("Check the extracted outline and measurements before saving.")}
        sketchSuggestion=null
    }
    fun readSketch() {
        if(readingSketch||saving)return
        if(extract==null) {sketchError="Sign in to read a sketch. You can enter measurements manually in the demo.";return}
        sketchError=null;readingSketch=true
        scope.launch {
            try {
                val image=pickSketchImage() ?: return@launch
                sketchSuggestion=null
                val reader=extract ?: throw AuthFailure(503,"Sign in to read a sketch. You can enter measurements manually in the demo.")
                sketchSuggestion=reader(image)
            } catch(cancelled:CancellationException) {throw cancelled}
            catch(failure:Exception) {sketchError=failure.message ?: "Couldn’t read this sketch. Try a clearer photo."}
            finally {readingSketch=false}
        }
    }
    fun editDirection(text: String) {
        if(saving || !Regex("^-?\\d{0,3}(\\.\\d{0,2})?$").matches(text))return
        text.toDoubleOrNull()?.takeIf { it in -180.0..180.0 }?.let { change(draft.copy(northDegrees=it)) }
        directionText = text
        val number=text.toDoubleOrNull()
        validation=if(number!=null&&number !in -180.0..180.0)validation+("direction" to "Choose a direction between −180° and 180°.") else validation-"direction"
    }
    fun undo() { if (!saving && canUndo) { redo = redo + draft; draft = undo.last(); undo = undo.dropLast(1); directionText=decimal(draft.northDegrees); validation = emptyMap() } }
    fun redo() { if (!saving && canRedo) { undo = undo + draft; draft = redo.last(); redo = redo.dropLast(1); directionText=decimal(draft.northDegrees); validation = emptyMap() } }
    fun markGestureStart() { if (!saving) { undo = (undo + draft).takeLast(60); redo = emptyList() } }
    fun discard() { draft = saved; directionText=decimal(draft.northDegrees); undo = emptyList(); redo = emptyList(); validation = emptyMap(); error = null;conflict=false;requireBoundary=false;requireRoads=false;sketchNotes=emptyList() }
    fun save(requireBoundary: Boolean = false, requireRoads: Boolean = false, onSaved: () -> Unit = {}) {
        val current = project ?: return
        if (loading || saving) return
        this.requireBoundary=requireBoundary;this.requireRoads=requireRoads;validationAttempt++
        validation = draft.errors(requireBoundary, requireRoads)
        if(directionText.toDoubleOrNull()?.let {it in -180.0..180.0} != true) validation = validation + ("direction" to "Choose a direction between −180° and 180°.")
        if (validation.isNotEmpty()) return
        if (!dirty && current.plotSetup != null) { onSaved(); return }
        saving = true; error = null
        val snapshot = draft
        scope.launch {
            try {
                val updated = write(current, snapshot)
                project = updated; saved = snapshot; conflict = false
                accepted(updated); onSaved()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                conflict = (failure as? AuthFailure)?.code == "PROJECT_CHANGED"
                error = failure.message ?: "Couldn’t save this plot. Your measurements are still here."
            } finally { saving = false }
        }
    }
}

internal fun livePlotWorkspace(auth: AuthSession, scope: CoroutineScope, owner: String, id: String, accepted: (Project) -> Unit): PlotWorkspace {
    suspend fun request(method: String, body: String? = null): Project {
        if (auth.user?.id != owner) throw AuthFailure(401,"Sign in to open this project.")
        val result = auth.projectRequest(method,"/v1/projects/$id" + if (method == "PUT") "/plot" else "",body)
        if (auth.user?.id != owner) throw AuthFailure(401,"Your account changed. Reopen the project.")
        return authJson.decodeFromJsonElement<Project>(result)
    }
    return PlotWorkspace(scope, { request("GET") }, { project, setup -> request("PUT",authJson.encodeToString(SavePlotRequest(setup,project.revision))) }, accepted, { image ->
        suspend fun poll(method:String,path:String,body:String?=null):SketchImport {
            if(auth.user?.id!=owner)throw AuthFailure(401,"Sign in to read this sketch.")
            val result=auth.projectRequest(method,path,body)
            if(auth.user?.id!=owner)throw AuthFailure(401,"Your account changed. Reopen the project.")
            return authJson.decodeFromJsonElement<SketchImport>(result)
        }
        var job=poll("POST","/v1/projects/$id/sketch",authJson.encodeToString(image))
        var attempts=0
        while(job.status=="reading"&&attempts++<55) {delay(1500);job=poll("GET","/v1/projects/$id/sketch/${job.id}")}
        job.suggestion ?: throw AuthFailure(0,job.error ?: "This sketch took too long to read. Try a clearer photo.")
    })
}
