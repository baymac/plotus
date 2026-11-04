@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
package com.plotus

import androidx.compose.runtime.*
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import com.plotus.shared.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.*

/** Click the real panel controls; no browser or account session is simulated. */
class EditorPanelActionsTest {
    private val plot=PlotSetup(lengths=listOf(18.0,12.0,18.0,12.0),roads=listOf(PlotRoad(0))).rebuild()
    private val initial=Project("c4f872f5-7c67-4f4e-aef9-b5e67f01ca2a","Panel fixture","Test city",revision=2,
        plotSetup=plot,floorPlan=SavedFloorPlan(FloorPlanSetup(),plot))

    private fun ImageComposeScene.nodes():List<SemanticsNode> {
        fun descendants(node:SemanticsNode):List<SemanticsNode> = listOf(node)+node.children.flatMap(::descendants)
        return semanticsOwners.flatMap {descendants(it.rootSemanticsNode)}
    }
    private fun SemanticsNode.named(label:String)=
        config.getOrNull(SemanticsProperties.ContentDescription)?.contains(label)==true||
            config.getOrNull(SemanticsProperties.Text)?.any {it.text==label}==true
    private fun ImageComposeScene.click(label:String) {
        val node=nodes().firstOrNull {it.named(label)&&it.config.getOrNull(SemanticsActions.OnClick)!=null}
        assertNotNull(node,"Missing control: $label")
        assertFalse(node.config.contains(SemanticsProperties.Disabled),"Disabled control: $label")
        val bounds=node.boundsInRoot
        val point=if(label=="Back")Offset(bounds.left+22f,bounds.center.y)else bounds.center
        sendPointerEvent(PointerEventType.Press,point)
        render().close()
        sendPointerEvent(PointerEventType.Release,point)
    }
    private suspend fun ImageComposeScene.settle() {repeat(3){render().close();delay(20)}}
    private fun ImageComposeScene.snapshot(name:String) {
        val output=System.getenv("PLOTUS_EDITOR_RENDER_DIR") ?: return
        File(output).mkdirs()
        val image=render()
        try {File(output,"$name.png").writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)}finally {image.close()}
    }

    @Test fun everyPlotPanelRetainsEditsOnBackAndClosesAfterConfirmedSave()=runBlocking<Unit> {
        for(panel in PlotStep.entries)for(compact in listOf(true,false)) {
            val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
            var stored=initial;var writes=0;val finish=CompletableDeferred<Unit>()
            val workspace=PlotWorkspace(scope,{stored},{project,setup->
                writes++;finish.await();project.copy(revision=project.revision+1,plotSetup=setup).also {stored=it}
            },{})
            workspace.load()
            when(panel) {
                PlotStep.BOUNDARY->workspace.change(plot.withLength(0,19.0).withLength(2,19.0))
                PlotStep.DIRECTION->workspace.editDirection("35")
                PlotStep.ROADS->workspace.change(plot.toggleRoad(1))
            }
            val expected=workspace.draft
            var step:PlotStep? by mutableStateOf(panel)
            val scene=ImageComposeScene(if(compact)390 else 1280,900,density=Density(1f),coroutineContext=Dispatchers.Unconfined) {
                PlotusTheme(Appearance.LIGHT) {PlotSetupScreen(workspace,step,compact,{step=it},{error("Unexpected project exit")})}
            }
            try {
                scene.settle();scene.snapshot("${panel.segment}-${if(compact)"mobile" else "desktop"}")
                scene.click("Back");scene.settle()
                assertNull(step);assertTrue(workspace.dirty);assertEquals(0,writes)
                step=panel;scene.settle();scene.click("Save");scene.settle()
                assertEquals(panel,step);assertTrue(workspace.saving)
                assertTrue(scene.nodes().any {it.named("Saving…")&&it.config.contains(SemanticsProperties.Disabled)})
                finish.complete(Unit);scene.settle()
                assertNull(step);assertFalse(workspace.saving);assertFalse(workspace.dirty);assertEquals(1,writes)
                assertEquals(expected,stored.plotSetup)
            }finally {scene.close();scope.cancel()}
        }
    }

    @Test fun everyFloorPanelRetainsEditsOnBackAndClosesAfterConfirmedSave()=runBlocking<Unit> {
        for(panel in listOf(FloorStep.SETBACKS,FloorStep.REQUIREMENTS))for(compact in listOf(true,false)) {
            val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
            var stored=initial;var writes=0;val finish=CompletableDeferred<Unit>()
            val workspace=FloorWorkspace(scope,{method,path,body->when {
                method=="PUT"->{
                    writes++;finish.await()
                    val request=authJson.decodeFromString<SaveFloorPlanRequest>(body!!)
                    stored=stored.copy(revision=stored.revision+1,floorPlan=SavedFloorPlan(request.setup,plot))
                    authJson.encodeToJsonElement(stored)
                }
                path.contains("/generations?")->authJson.encodeToJsonElement(PlanGenerations(emptyList()))
                else->authJson.encodeToJsonElement(stored)
            }},{})
            workspace.load(initial.id)
            workspace.change(if(panel==FloorStep.SETBACKS)workspace.draft.copy(setbackMode=PlanMode.CUSTOM,commonSetbackMetres=.9)
                else workspace.draft.withHome(workspace.draft.homes.first().copy(bhk=3)))
            val expected=workspace.draft
            var step:FloorStep? by mutableStateOf(panel)
            val scene=ImageComposeScene(if(compact)390 else 1280,900,density=Density(1f),coroutineContext=Dispatchers.Unconfined) {
                PlotusTheme(Appearance.LIGHT) {FloorPlanScreen(workspace,initial.id,step,compact,{step=it},
                    {error("Unexpected plot navigation")},{error("Unexpected project exit")})}
            }
            try {
                scene.settle();scene.snapshot("${panel.segment}-${if(compact)"mobile" else "desktop"}")
                scene.click("Back");scene.settle()
                assertNull(step);assertTrue(workspace.dirty);assertEquals(0,writes)
                step=panel;scene.settle();scene.click("Save");scene.settle()
                assertEquals(panel,step);assertTrue(workspace.saving)
                assertTrue(scene.nodes().any {it.named("Saving…")&&it.config.contains(SemanticsProperties.Disabled)})
                finish.complete(Unit);scene.settle()
                assertNull(step);assertFalse(workspace.saving);assertFalse(workspace.dirty);assertEquals(1,writes)
                assertEquals(expected,stored.floorPlan?.setup)
            }finally {scene.close();scope.cancel()}
        }
    }

    @Test fun defaultFloorSettingsCanBeSavedBeforeAnyFieldIsChanged()=runBlocking<Unit> {
        for(compact in listOf(true,false)) {
            val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
            var stored=initial.copy(floorPlan=null);var writes=0
            val workspace=FloorWorkspace(scope,{method,path,body->when {
                method=="PUT"->{
                    writes++
                    val request=authJson.decodeFromString<SaveFloorPlanRequest>(body!!)
                    stored=stored.copy(revision=stored.revision+1,floorPlan=SavedFloorPlan(request.setup,plot))
                    authJson.encodeToJsonElement(stored)
                }
                path.contains("/generations?")->authJson.encodeToJsonElement(PlanGenerations(emptyList()))
                else->authJson.encodeToJsonElement(stored)
            }},{})
            workspace.load(initial.id)
            assertFalse(workspace.dirty)
            var step:FloorStep? by mutableStateOf(FloorStep.SETBACKS)
            val scene=ImageComposeScene(if(compact)390 else 1280,900,density=Density(1f),coroutineContext=Dispatchers.Unconfined) {
                PlotusTheme(Appearance.LIGHT) {FloorPlanScreen(workspace,initial.id,step,compact,{step=it},{},{})}
            }
            try {
                scene.settle();scene.click("Save");scene.settle()
                assertNull(step);assertEquals(1,writes);assertNotNull(stored.floorPlan)
                assertFalse(workspace.dirty)
            }finally {scene.close();scope.cancel()}
        }
    }
}
