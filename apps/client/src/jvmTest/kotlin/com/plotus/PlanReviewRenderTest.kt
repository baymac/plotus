@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
package com.plotus

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.unit.Density
import com.plotus.shared.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import java.util.Base64
import java.io.ByteArrayOutputStream
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import kotlin.test.*

/** Optional visual check of the actual Compose screens, with isolated synthetic data. */
class PlanReviewRenderTest {
    @Test fun renderReviewAndFocusedImprovementAtBothSizesAndAppearances()=runBlocking<Unit> {
        val output=System.getenv("PLOTUS_REVIEW_RENDER_DIR") ?: return@runBlocking
        File(output).mkdirs()
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        val finding=ReviewFinding("bath","bathroom_above_compact_reference","space","medium",
            "Review generous service-room space","The common bathroom is above its comfort target.",
            ReviewOperation.BALANCE_ROOM_PROPORTIONS,measurements=listOf(ReviewMeasurement("area","Measured bathroom area",8.54,"square_metres")))
        val report=PlanReviewReport(rulesVersion="plotus-architect-review.2",sourceHash="a".repeat(64),designScore=95,designLevel="Excellent",
            gates=listOf(ReviewCheck("configured_layout","Layout checks","pass")),findings=listOf(finding),canOptimize=true,
            measurements=listOf(ReviewMeasurement("serviceExcess","Service area above comfort targets",2.96,"square_metres")),
            metrics=listOf("space" to 25,"circulation" to 20,"function" to 20,"daylight" to 15,"privacy" to 10,"access" to 10)
                .map {ReviewMetric(it.first,it.first.replaceFirstChar(Char::uppercase),it.second,95.0)})
        val ai=AIPlanReview("ready","Test a more compact common bathroom while keeping fixtures and access.",listOf(
            AIReviewRecommendation("bath","An oversized bathroom can take useful space away from everyday family areas.",
                "Test a compact arrangement that preserves the original fixtures and clear entry.")))
        val workspace=FloorWorkspace(scope,{_,path,_->when {
            path.endsWith("/optimizations")->authJson.encodeToJsonElement(CurrentOptimization())
            path.endsWith("/ai-review")->authJson.encodeToJsonElement(ai)
            else->authJson.encodeToJsonElement(report)
        }},{})
        workspace.reviews.attachProject("visual-fixture");workspace.reviews.open("plan")
        val svg="""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 60 40" data-plotus-frame="designed"><rect x="3" y="3" width="54" height="34" fill="#fbf9f2" stroke="#77756e"/><path d="M30 3V37M3 23H57" stroke="#77756e"/><text x="8" y="13" font-size="2">Synthetic layout</text></svg>"""
        val png=ByteArrayOutputStream().also {ImageIO.write(BufferedImage(900,600,BufferedImage.TYPE_INT_ARGB),"png",it)}.toByteArray()
        val drawing=PlanDrawing("ground","Ground",Base64.getEncoder().encodeToString(png),900,600,vectorSvgBase64=Base64.getEncoder().encodeToString(svg.toByteArray()))
        val plan=GeneratedFloorPlan("plan","ready",drawings=listOf(drawing))
        try {
            for(appearance in listOf(Appearance.LIGHT,Appearance.DARK))for(compact in listOf(true,false))
                for(view in listOf(ReviewView.REVIEW,ReviewView.AI,ReviewView.OPTIMIZE)) {
                    val scene=ImageComposeScene(if(compact)390 else 1280,if(compact)844 else 900,density=Density(1f),coroutineContext=Dispatchers.Unconfined) {
                        PlotusTheme(appearance) {ArchitectReviewScreen(workspace,plan,PlanReviewRoute(view),compact,
                            Modifier.fillMaxSize(),0,{},{},{},{_,_->},{})}
                    }
                    try {
                        repeat(3){scene.render().close();delay(50)}
                        val image=scene.render()
                        try {
                            val bytes=image.encodeToData(EncodedImageFormat.PNG)!!.bytes
                            assertTrue(bytes.size>1000)
                            File(output,"${view.name.lowercase()}-${if(compact)"mobile" else "desktop"}-${appearance.name.lowercase()}.png").writeBytes(bytes)
                        }finally {image.close()}
                    }finally {scene.close()}
                }
        }finally {scope.cancel()}
    }
}
