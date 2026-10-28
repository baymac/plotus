package com.plotus

import com.plotus.shared.*
import kotlinx.coroutines.*
import kotlin.test.*

class SketchCancellationTest {
    private val project=Project("sketch-test","Plot","Test city")
    private val image=SketchImage("image/jpeg","test-image")
    private val suggestion=SketchSuggestion(PlotSetup())

    @Test fun cancellingThePickerNeverStartsReadingAndAllowsRetry()=runBlocking<Unit> {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        val picked=CompletableDeferred<SketchImage?>();var picks=0;var reads=0
        val workspace=PlotWorkspace(scope,{project},{p,_->p},{},
            extract={reads++;suggestion},pickImage={if(picks++==0)picked.await() else image})
        try {
            workspace.readSketch()
            assertTrue(workspace.choosingSketch);assertFalse(workspace.readingSketch)
            workspace.readSketch();assertEquals(1,picks)
            picked.complete(null);yield()
            assertFalse(workspace.choosingSketch);assertFalse(workspace.readingSketch)
            assertEquals(0,reads);assertNull(workspace.sketchError)
            workspace.readSketch()
            assertEquals(1,reads);assertEquals(suggestion,workspace.sketchSuggestion)
            assertFalse(workspace.choosingSketch);assertFalse(workspace.readingSketch)
        }finally {scope.cancel()}
    }

    @Test fun cancellingAReadIgnoresItsLateResultAndAllowsANewRead()=runBlocking<Unit> {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        val first=CompletableDeferred<SketchSuggestion>();val second=CompletableDeferred<SketchSuggestion>();var reads=0
        val workspace=PlotWorkspace(scope,{project},{p,_->p},{},
            extract={if(reads++==0)withContext(NonCancellable){first.await()} else second.await()},pickImage={image})
        try {
            workspace.readSketch();assertTrue(workspace.readingSketch)
            workspace.cancelSketchRead()
            assertFalse(workspace.readingSketch);assertNull(workspace.sketchError)
            workspace.readSketch();assertTrue(workspace.readingSketch)
            first.complete(suggestion);yield()
            assertTrue(workspace.readingSketch);assertNull(workspace.sketchSuggestion)
            val newest=suggestion.copy(notes=listOf("Current read"))
            second.complete(newest);yield()
            assertEquals(newest,workspace.sketchSuggestion);assertFalse(workspace.readingSketch)
        }finally {first.complete(suggestion);scope.cancel()}
    }
}
