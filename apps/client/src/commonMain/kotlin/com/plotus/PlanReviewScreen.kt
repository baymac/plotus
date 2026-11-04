package com.plotus

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.plotus.resources.*
import com.plotus.shared.*
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import kotlin.math.abs
import kotlin.math.roundToInt

internal fun reviewHeading(route:PlanReviewRoute,workspace:PlanReviewWorkspace):String=when(route.view) {
    ReviewView.FINDING->workspace.report?.findings?.find {it.id==route.itemID}?.let {
        workspace.report?.metrics?.find {m->m.id==it.metricID}?.title
    } ?: "Design recommendation"
    ReviewView.METRIC->workspace.report?.metrics?.find {it.id==route.itemID}?.title ?: "Measured evidence"
    ReviewView.PROGRESS->if(workspace.optimization?.running==false)"Optimization" else "Optimizing"
    else->route.view.title
}

@Composable internal fun ArchitectReviewHeader(title:String,compact:Boolean,back:Boolean=true,onBack:()->Unit,onClose:()->Unit={}) {
    val c=LocalPlotusColors.current
    Row(Modifier.fillMaxWidth().heightIn(min=if(compact)56.dp else 60.dp).padding(horizontal=if(compact)16.dp else 24.dp),verticalAlignment=Alignment.CenterVertically) {
        if(back)Row(Modifier.weight(1f).heightIn(min=48.dp).clickable(role=Role.Button,onClickLabel="Back",onClick=onBack),verticalAlignment=Alignment.CenterVertically) {
            Image(painterResource(Res.drawable.review_back),null,Modifier.size(20.dp),colorFilter=ColorFilter.tint(c.muted))
            Spacer(Modifier.width(8.dp))
            Text(title,color=c.ink,fontSize=16.sp,lineHeight=24.sp,fontWeight=FontWeight.SemiBold)
        }else Text(title,Modifier.weight(1f),color=c.ink,fontSize=16.sp,lineHeight=24.sp,fontWeight=FontWeight.SemiBold)
        if(!compact)IconButton(onClose,Modifier.size(44.dp)) {
            Image(painterResource(Res.drawable.review_close),"Close review",Modifier.size(20.dp),colorFilter=ColorFilter.tint(c.muted))
        }
    }
}

/** Full-page touch views and a 408-dp inspector around the same live vector canvas. */
@Composable internal fun ArchitectReviewScreen(
    workspace:FloorWorkspace,plan:GeneratedFloorPlan,route:PlanReviewRoute,compact:Boolean,modifier:Modifier,
    floor:Int,onFloor:(Int)->Unit,onView:(PlanReviewRoute?)->Unit,onHistory:()->Unit,
    onOptimization:(PlanOptimization,Boolean)->Unit,onSaved:(GeneratedFloorPlan)->Unit,
) {
    val c=LocalPlotusColors.current
    val review=workspace.reviews
    val report=review.report?.takeIf {review.planID==plan.id}
    val job=review.optimization?.takeIf {it.basePlanID==plan.id&&it.id==route.itemID}
    var preview by remember(route.view,job?.id) {mutableStateOf<OptimizedRevision?>(null)}
    var revisedSelected by remember(job?.id) {mutableStateOf(true)}
    LaunchedEffect(route.view,job?.id,job?.revisionHash) {
        if(route.view==ReviewView.COMPARE&&preview==null)preview=job?.revision
    }
    LaunchedEffect(route.view,job?.status,job?.revisionHash) {
        if(route.view==ReviewView.PROGRESS&&job?.status=="ready"&&job.revision!=null)
            onView(PlanReviewRoute(ReviewView.COMPARE,job.id))
    }
    LaunchedEffect(route.view,report?.sourceHash) {if(route.view==ReviewView.AI&&report!=null)review.explain()}
    val back:()->Unit={if(route.view==ReviewView.REVIEW)onView(null) else if(route.view==ReviewView.PROGRESS)onHistory() else onView(PlanReviewRoute(ReviewView.REVIEW))}
    val selectedFinding=report?.findings?.find {it.id==route.itemID}
    val selectedMetric=report?.metrics?.find {it.id==route.itemID}
    val highlights=when(route.view){
        ReviewView.FINDING->selectedFinding?.roomIDs.orEmpty().toSet()
        ReviewView.METRIC->report?.findings?.filter {it.metricID==selectedMetric?.id}?.flatMap {it.roomIDs}.orEmpty().toSet()
        else->emptySet()
    }
    LaunchedEffect(selectedFinding?.id,selectedMetric?.id) {
        if(highlights.isNotEmpty())plan.drawings.indexOfFirst {drawing->
            drawing.scene3d?.rooms?.any {it.id in highlights}==true
        }.takeIf {it>=0&&it!=floor}?.let(onFloor)
    }
    val canvas:@Composable (Modifier,Boolean)->Unit={m,showNew->
        val drawings=if(showNew)preview?.drawings.orEmpty() else plan.drawings
        val drawing=drawings.getOrNull(floor) ?: drawings.firstOrNull()
        if(drawing!=null)Column(m) {
            if(compact&&drawings.size>1)Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                drawings.forEachIndexed {index,_->TextButton({onFloor(index)}){Text((if(floor==index)"✓ " else "")+floorName(index))}}
            }
            GeneratedPlanCanvas(drawing,compact,Modifier.weight(1f).fillMaxWidth(),plan.roadLabels,plan.requirements,
                highlightRoomIDs=highlights,showMetrics=!compact&&route.view!=ReviewView.COMPARE,showRequirements=false,preview=compact)
        }
        else Box(m,contentAlignment=Alignment.Center){ReviewNote("This drawing is unavailable.")}
    }
    val panel:@Composable (Modifier)->Unit={m->
        Column(m.background(if(compact)c.background else c.surface)) {
            if(!compact)ArchitectReviewHeader(reviewHeading(route,review),false,route.view !in listOf(ReviewView.REVIEW,ReviewView.AI),back) {onView(null)}
            val scroll=rememberScrollState()
            LaunchedEffect(route.view,route.itemID){scroll.scrollTo(0)}
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).padding(if(compact)20.dp else 24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                review.error?.let {message->
                    Text(message,color=MaterialTheme.colorScheme.error,fontSize=13.sp,lineHeight=20.sp)
                    TextButton({review.dismissError();if(route.view in listOf(ReviewView.PROGRESS,ReviewView.COMPARE))route.itemID?.let(review::openOptimization) else review.open(plan.id,true)}){Text("Try again")}
                }
                if(review.loading&&report==null){ReviewScore(null,"Calculating","Reading the plan measurements");LinearProgressIndicator(Modifier.fillMaxWidth())}
                else if(report==null&&route.view !in listOf(ReviewView.PROGRESS,ReviewView.COMPARE)) {
                    ReviewTitle("A review needs a complete plan")
                    ReviewNote("Measured scores appear when the plan evidence is available.")
                    ReviewScore(null,"Not assessed","No complete measured report")
                    if(review.error==null)PlotusButton("Try again",{review.open(plan.id,true)},outline=true)
                }else when(route.view) {
                    ReviewView.REVIEW->if(report!=null)ReviewOverview(report,onView)
                    ReviewView.AI->if(report!=null)ReviewAI(report,review,onView)
                    ReviewView.OPTIMIZE->if(report!=null)ReviewOptimizationSetup(report,review,onView)
                    ReviewView.FINDING->if(report!=null) {
                        if(compact&&highlights.isNotEmpty())canvas(Modifier.fillMaxWidth().height(226.dp),false)
                        selectedFinding?.let {finding->
                            ReviewTag("Proposal · Not tested yet")
                            ReviewTitle(finding.title)
                            ReviewNote(priorityLabel(finding.priority)+" · "+(report.metrics.find {it.id==finding.metricID}?.title ?: "Vastu"))
                            finding.measurements.forEach {ReviewMeasuredRow(it)}
                            ReviewBody(finding.explanation)
                            ReviewSection("Keep in the revision")
                            ReviewStatus("Saved setbacks and plot direction")
                            ReviewStatus("Required openings and private room access")
                            ReviewStatus("Parking and entrance access")
                            ReviewNote("The solver tests feasibility and compares the result. An improvement is not promised.")
                        } ?: ReviewNote("This recommendation is no longer in the measured report.")
                    }
                    ReviewView.METRIC->selectedMetric?.let {metric->
                        if(compact&&highlights.isNotEmpty())canvas(Modifier.fillMaxWidth().height(226.dp),false)
                        ReviewScore(metric.score?.roundToInt(),if(metric.score==null)"Not assessed" else metric.title,"Measured evidence",metric.title)
                        ReviewBody(metric.basis)
                        metric.measurements.forEach {ReviewMeasuredRow(it)}
                        report.findings.filter {it.metricID==metric.id}.forEach {ReviewFindingCard(it,onView=onView)}
                        if(metric.score==null)ReviewNote("The saved plan lacks some evidence for this category. No score is guessed.")
                    }
                    ReviewView.METHOD->if(report!=null)ReviewScoringMethod(report)
                    ReviewView.VASTU->if(report!=null)ReviewVastu(report.vastu)
                    ReviewView.PROGRESS->{
                        ReviewProgress(job,onHistory,{onView(PlanReviewRoute(ReviewView.COMPARE,it))})
                        if(job?.status in listOf("no_improvement","failed","cancelled"))PlotusButton("Try fewer changes",{
                            review.prepare(ReviewOperation.CONSOLIDATE_OPEN_SPACE);onView(PlanReviewRoute(ReviewView.OPTIMIZE))
                        },outline=true)
                    }
                    ReviewView.COMPARE-> {
                        if(preview!=null){
                            if(job?.revisionHash!=preview?.review?.sourceHash) {
                                ReviewTag("A newer checked revision is ready")
                                TextButton({preview=job?.revision}){Text("Review latest revision")}
                            }
                            if(compact){
                                ReviewSwitch(listOf("Current" to false,"Revised" to true),revisedSelected){revisedSelected=it}
                                canvas(Modifier.fillMaxWidth().height(246.dp),revisedSelected)
                            }
                            ReviewComparison(preview!!,job?.baseReview)
                        }else {
                            ReviewTitle(if(job?.running==true)"Checking revisions" else "No checked revision is available")
                            ReviewNote("Your saved plan remains selected.")
                            if(job?.running==true)LinearProgressIndicator(Modifier.fillMaxWidth())
                        }
                    }
                }
            }
            val showActions=route.view !in listOf(ReviewView.METHOD,ReviewView.VASTU)
            if(showActions)Column(Modifier.fillMaxWidth().background(c.surface).padding(horizontal=if(compact)20.dp else 24.dp,vertical=16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                HorizontalDivider(color=c.line)
                when(route.view) {
                    ReviewView.PROGRESS->if(job?.running==true)Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        PlotusButton("View history",onHistory,Modifier.weight(1f).height(48.dp),outline=true)
                        PlotusButton(if(review.cancelling)"Cancelling…" else "Cancel",{review.cancel {onView(null)}},Modifier.weight(1f).height(48.dp),outline=true,enabled=!review.busy)
                    }else PlotusButton("Return to current plan",{onView(null)},Modifier.fillMaxWidth().height(48.dp))
                    ReviewView.COMPARE->Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        PlotusButton("Keep current",{review.keepCurrent {onView(null)}},Modifier.weight(1f).height(48.dp),outline=true,enabled=!review.busy)
                        PlotusButton(if(review.applying)"Saving…" else "Use revision",{
                            preview?.review?.sourceHash?.let {hash->review.apply(hash,onSaved)}
                        },Modifier.weight(1f).height(48.dp),enabled=!review.busy&&preview?.review?.requiredChecksPass==true&&preview?.review?.sourceHash!=null)
                    }
                    ReviewView.OPTIMIZE->PlotusButton(if(review.starting)"Starting…" else "Compare up to 5 layouts",{
                        review.start {onOptimization(it,false)}
                    },Modifier.fillMaxWidth().height(48.dp),enabled=!review.busy&&report?.canOptimize==true&&review.selectedOperations.isNotEmpty())
                    ReviewView.FINDING->PlotusButton("Try this revision",{
                        review.prepare(selectedFinding?.operation);onView(PlanReviewRoute(ReviewView.OPTIMIZE))
                    },Modifier.fillMaxWidth().height(48.dp),enabled=report?.canOptimize==true&&selectedFinding?.operation!=null&&!review.busy)
                    else->PlotusButton("Optimize plan",{
                        review.prepare();onView(PlanReviewRoute(ReviewView.OPTIMIZE))
                    },Modifier.fillMaxWidth().height(48.dp),enabled=report?.canOptimize==true&&!review.busy)
                }
            }
        }
    }
    if(compact)panel(modifier.fillMaxWidth())
    else Column(modifier.fillMaxWidth()) {
        if(plan.drawings.size>1)Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal=16.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            plan.drawings.forEachIndexed {index,_->TextButton({onFloor(index)}){Text((if(floor==index)"✓ " else "")+floorName(index))}}
        }
        Row(Modifier.weight(1f).fillMaxWidth()) {
        if(route.view==ReviewView.COMPARE&&preview!=null)Column(Modifier.weight(1f).fillMaxHeight()) {
            Row(Modifier.weight(1f).fillMaxWidth()) {
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    ReviewSection("Current",Modifier.padding(start=20.dp,top=16.dp));canvas(Modifier.weight(1f).fillMaxWidth(),false)
                }
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    ReviewSection("Revised",Modifier.padding(start=20.dp,top=16.dp));canvas(Modifier.weight(1f).fillMaxWidth(),true)
                }
            }
            (preview?.drawings?.getOrNull(floor) ?: preview?.drawings?.firstOrNull())?.presentation?.let {GeneratedPlanMeasurements(it,false)}
        }else canvas(Modifier.weight(1f).fillMaxHeight(),false)
        VerticalDivider(color=c.line)
        panel(Modifier.width(408.dp).fillMaxHeight())
        }
    }
}

@Composable private fun ReviewOverview(report:PlanReviewReport,onView:(PlanReviewRoute?)->Unit) {
    val c=LocalPlotusColors.current
    ReviewTabs(false,onView)
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        ReviewScore(report.designScore,report.designLevel,if(report.designScore==null)"Some measurement evidence is unavailable" else "Calculated from six measured categories")
        Row(Modifier.fillMaxWidth().background(c.quiet,RoundedCornerShape(12.dp)).clickable(role=Role.Button){onView(PlanReviewRoute(ReviewView.VASTU))}.padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
            Text("Vastu alignment",Modifier.weight(1f),fontSize=14.sp,fontWeight=FontWeight.SemiBold)
            Text(report.vastu.score?.let {it.roundToInt().toString()+" / 100"} ?: if(report.vastu.requested)"Not assessed" else "Not requested",fontSize=14.sp,color=c.primary)
        }
        val gate=report.gates.firstOrNull {it.id=="configured_layout"}
        ReviewStatus(when(gate?.status){"pass"->"Layout checks passed";"fail"->"Required layout checks failed";else->"Layout checks not assessed"},gate?.status ?: "not_assessed")
        ReviewNote("Structural and local-code approval not assessed.")
    }
    HorizontalDivider(color=c.line)
    ReviewSection("Recommended next")
    if(report.findings.isEmpty())ReviewNote("No measured optimization opportunity is flagged by this review.")
    report.findings.forEachIndexed {index,finding->if(index>0)HorizontalDivider(color=c.line);ReviewFindingCard(finding,onView=onView)}
    ReviewNote("Recommendations are proposals until a revision passes the checks.")
    HorizontalDivider(color=c.line)
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        ReviewSection("Design breakdown",Modifier.weight(1f))
        TextButton({onView(PlanReviewRoute(ReviewView.METHOD))}){Text("How scored",fontSize=12.sp)}
    }
    Column {report.metrics.forEach {metric->
        Row(Modifier.fillMaxWidth().heightIn(min=44.dp).clickable(role=Role.Button){onView(PlanReviewRoute(ReviewView.METRIC,metric.id))},verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(metric.title,Modifier.weight(1f),fontSize=14.sp,lineHeight=21.sp)
            Text(metric.score?.roundToInt()?.toString() ?: "—",fontSize=14.sp,fontWeight=FontWeight.SemiBold)
            Text("›",fontSize=18.sp,color=c.muted)
        }
    }}
}

@Composable private fun ReviewAI(report:PlanReviewReport,workspace:PlanReviewWorkspace,onView:(PlanReviewRoute?)->Unit) {
    val c=LocalPlotusColors.current
    ReviewTabs(true,onView)
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
        Image(painterResource(Res.drawable.review_spark),null,Modifier.size(18.dp),colorFilter=ColorFilter.tint(c.primary))
        Text("AI explanation · From measured findings",fontSize=12.sp,lineHeight=18.sp,color=c.primary)
    }
    when {
        workspace.aiLoading->{ReviewTitle("Reviewing the measured findings");LinearProgressIndicator(Modifier.fillMaxWidth())}
        workspace.ai?.status=="ready"->{
            ReviewTitle(workspace.ai!!.summary)
            ReviewNote("Measurements and scores come from the deterministic plan review.")
            workspace.ai!!.recommendations.forEach {r->report.findings.find {it.id==r.findingID}?.let {ReviewFindingCard(it,r.explanation,onView)}}
        }
        workspace.ai?.status=="not_needed"->ReviewBody(workspace.ai!!.summary)
        else->{
            ReviewTitle("AI recommendations are unavailable")
            ReviewBody("Your measured review and solver optimization are still available.")
            workspace.aiError?.let {ReviewNote(it)}
            PlotusButton("Retry AI review",{workspace.explain(true)},outline=true)
        }
    }
    ReviewNote("Each suggestion needs a checked solver revision before it can be applied.")
}

@Composable private fun ReviewOptimizationSetup(report:PlanReviewReport,workspace:PlanReviewWorkspace,onView:(PlanReviewRoute?)->Unit) {
    val c=LocalPlotusColors.current
    ReviewTitle("Find a better arrangement")
    ReviewBody("Try alternative room arrangements and compare only those that pass the required checks.")
    Column(Modifier.fillMaxWidth().background(c.quiet,RoundedCornerShape(12.dp)).padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        ReviewSection("Balanced")
        ReviewNote("Space, circulation, function, daylight, privacy and access.")
        Text("View scoring weights",Modifier.clickable(role=Role.Button){onView(PlanReviewRoute(ReviewView.METHOD))}.padding(vertical=6.dp),fontSize=14.sp,fontWeight=FontWeight.SemiBold,color=c.primary)
    }
    ReviewSection("Explore these improvements")
    val options=(listOf(ReviewOperation.CONSOLIDATE_OPEN_SPACE,ReviewOperation.SHORTEN_PASSAGE,ReviewOperation.BALANCE_ROOM_PROPORTIONS)+report.findings.mapNotNull {it.operation}+workspace.selectedOperations).distinct()
    Column {options.forEach {operation->
        val selected=operation in workspace.selectedOperations
        Row(Modifier.fillMaxWidth().heightIn(min=48.dp).toggleable(selected,role=Role.Checkbox){workspace.choose(operation)},verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Checkbox(selected,onCheckedChange=null,modifier=Modifier.size(24.dp))
            Text(operation.title,fontSize=14.sp,lineHeight=21.sp)
        }
    }}
    HorizontalDivider(color=c.line)
    ReviewSection("Always preserved")
    ReviewNote("Room requirements, saved setbacks, plot direction, required openings, staircase geometry and vehicle access.")
    if(report.vastu.requested)ReviewStatus("Selected Vastu hard rules remain required")
    ReviewNote("Up to five distinct candidate arrangements are attempted. The bounded search may find fewer valid alternatives.")
}

@Composable private fun ReviewProgress(job:PlanOptimization?,onHistory:()->Unit,onCompare:(String)->Unit) {
    val c=LocalPlotusColors.current
    if(job==null){ReviewTitle("Opening optimization");LinearProgressIndicator(Modifier.fillMaxWidth());return}
    var now by remember(job.id){mutableStateOf(kotlin.time.Clock.System.now().toEpochMilliseconds())}
    LaunchedEffect(job.id,job.running){while(job.running){now=kotlin.time.Clock.System.now().toEpochMilliseconds();delay(1000)}}
    val elapsed=runCatching {
        val end=if(job.running)now else kotlin.time.Instant.parse(job.updatedAt).toEpochMilliseconds()
        (end-kotlin.time.Instant.parse(job.createdAt).toEpochMilliseconds()).coerceAtLeast(0)
    }.getOrDefault(0)
    Row(Modifier.fillMaxWidth().background(c.quiet,RoundedCornerShape(12.dp)).padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
        Image(painterResource(Res.drawable.review_clock),null,Modifier.size(28.dp),colorFilter=ColorFilter.tint(c.primary))
        val seconds=elapsed/1000
        Text((seconds/60).toString().padStart(2,'0')+":"+(seconds%60).toString().padStart(2,'0'),fontSize=40.sp,lineHeight=48.sp,fontWeight=FontWeight.Bold,
            modifier=Modifier.semantics {contentDescription="Elapsed "+runDurationLabel(elapsed)})
    }
    ReviewTitle(when {job.status=="no_improvement"->"No better validated layout found";job.status=="failed"->"Optimization couldn’t finish";job.status=="cancelled"->"Optimization cancelled";else->"Checking alternative layouts"})
    ReviewBody(if(job.running)"Finding layouts that preserve your requirements and pass the checks." else job.error ?: "Your current plan remains selected.")
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        for((value,label) in listOf(job.requested to "Requested",job.checked to "Checked",job.valid to "Valid"))Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)){
            Text(value.toString(),fontSize=26.sp,fontWeight=FontWeight.Bold);ReviewNote(label)
        }
    }
    ReviewNote(job.distinct.toString()+" distinct valid arrangements checked.")
    HorizontalDivider(color=c.line)
    ReviewStatus("Current plan remains available")
    if(job.revision!=null){
        ReviewStatus("A checked revision is ready to preview")
        PlotusButton("Preview checked revision",{onCompare(job.id)},Modifier.fillMaxWidth().height(48.dp),outline=true)
    }
    if(job.running)ReviewNote("The search can continue while you view History. Cancel stops the remaining attempts.")
    else if(job.status=="no_improvement")ReviewNote("The checked alternatives did not improve the measured result. This does not prove that a better design is impossible.")
}

@Composable private fun ReviewComparison(revision:OptimizedRevision,baseline:PlanReviewReport?) {
    val c=LocalPlotusColors.current
    val units=LocalMeasurementUnits.current
    ReviewTag(if(baseline?.designScore==null)"Checked alternative · Balanced" else "Best checked revision · Balanced")
    ReviewSection("Compare the measured changes")
    ReviewStatus("Required layout checks passed")
    ReviewNote("Structural and local-code approval not assessed.")
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Spacer(Modifier.weight(1f))
        for((label,width)in listOf("Current" to 58.dp,"Revised" to 58.dp,"Change" to 48.dp))
            Text(label,Modifier.width(width),fontSize=12.sp,lineHeight=18.sp,color=c.muted,textAlign=TextAlign.End)
    }
    Column {revision.comparisons.forEach {row->
        val conversion=if(row.unit=="square_metres"&&units==PlotUnits.FEET_INCHES)1/(METRES_PER_FOOT*METRES_PER_FOOT) else 1.0
        fun value(v:Double?)=v?.let {decimal(kotlin.math.round(it*conversion*10)/10)} ?: "—"
        val before=row.current;val after=row.revised
        val delta=if(before!=null&&after!=null)after-before else null
        val label=row.title+if(row.unit=="square_metres")if(units==PlotUnits.METRES)" (m²)" else " (sq ft)" else ""
        Row(Modifier.fillMaxWidth().heightIn(min=48.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(label,Modifier.weight(1f),fontSize=14.sp,lineHeight=21.sp)
            Text(value(row.current),Modifier.width(58.dp),fontSize=14.sp,color=c.muted,textAlign=TextAlign.End)
            Text(value(row.revised),Modifier.width(58.dp),fontSize=14.sp,fontWeight=FontWeight.SemiBold,textAlign=TextAlign.End)
            Text(if(delta==null)"—" else if(abs(delta)<.000001)"—" else (if(delta>0)"+" else "")+value(delta),Modifier.width(48.dp),fontSize=12.sp,color=c.primary,textAlign=TextAlign.End)
        }
    }}
    HorizontalDivider(color=c.line)
    ReviewSection("Trade-offs")
    if(revision.tradeoffs.isEmpty())ReviewNote("Review the measured differences before choosing a revision.") else revision.tradeoffs.forEach {ReviewNote(it)}
    ReviewNote("Applying creates a new history entry and stops any remaining optimization attempts. The original is retained.")
}

@Composable private fun ReviewScoringMethod(report:PlanReviewReport) {
    ReviewTitle("Balanced scoring")
    ReviewBody("Design quality is a weighted score from measured plan evidence. Vastu is scored separately.")
    report.metrics.forEach {metric->
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text(metric.title,Modifier.weight(1f),fontSize=14.sp,lineHeight=21.sp)
            Text(metric.weight.toString()+"%",fontWeight=FontWeight.SemiBold)
        }
        ReviewNote(metric.basis)
    }
    HorizontalDivider()
    ReviewNote("An overall score is shown only when all six categories have measured scores. Missing evidence is not guessed.")
    for((range,label)in listOf("90–100" to "Excellent","75–89" to "Good","60–74" to "Fair","40–59" to "Poor","0–39" to "Very poor"))
        Row(Modifier.fillMaxWidth()){Text(range,Modifier.width(88.dp),fontSize=13.sp);Text(label,fontSize=14.sp)}
    ReviewNote("These are Plotus product defaults, not architectural or legal standards. A high score never compensates for a failed required check.")
}

@Composable private fun ReviewVastu(value:VastuReview) {
    ReviewScore(value.score?.roundToInt(),if(value.requested)"Plotus V1" else "Not requested","Calculated from the saved true-north direction","Vastu alignment")
    ReviewSection("Selected hard rules")
    if(!value.requested)ReviewNote("Vastu was not selected for this saved plan.")
    value.checks.forEach {ReviewStatus(it.label,it.status)}
    value.canonicalOverrides.forEach {ReviewNote(it)}
    ReviewSection("Traditional preferences")
    value.preferences.forEach {Column(verticalArrangement=Arrangement.spacedBy(4.dp)){ReviewStatus(it.label,it.status);ReviewNote(it.detail)}}
    ReviewNote("Preferences do not override daylight, circulation, safe dimensions, saved setbacks or configured local rules.")
}

@Composable private fun ReviewFindingCard(finding:ReviewFinding,explanation:String?=null,onView:(PlanReviewRoute?)->Unit) {
    val c=LocalPlotusColors.current
    Column(Modifier.fillMaxWidth().clickable(role=Role.Button){onView(PlanReviewRoute(ReviewView.FINDING,finding.id))}.padding(vertical=12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(priorityLabel(finding.priority)+" · "+metricTitle(finding.metricID),fontSize=12.sp,lineHeight=18.sp,color=if(finding.priority=="critical")MaterialTheme.colorScheme.error else c.primary)
        ReviewSection(finding.title)
        explanation?.let {ReviewBody(it)}
        finding.measurements.filter {it.value!=null}.forEach {ReviewNote(it.label+" · "+reviewValue(it))}
        Text("Explore fix ↗",fontSize=14.sp,lineHeight=20.sp,color=c.primary,fontWeight=FontWeight.SemiBold)
    }
}

@Composable private fun ReviewScore(score:Int?,level:String,basis:String,label:String="Design quality") {
    val c=LocalPlotusColors.current
    Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Text(label,fontSize=14.sp,lineHeight=20.sp,fontWeight=FontWeight.SemiBold)
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(score?.toString() ?: "—",fontSize=40.sp,lineHeight=48.sp,fontWeight=FontWeight.Bold)
            Text("/ 100",fontSize=14.sp,color=c.muted)
            Text(level,Modifier.weight(1f),fontSize=14.sp,color=c.primary,fontWeight=FontWeight.SemiBold)
        }
        ReviewNote(basis)
    }
}
@Composable private fun ReviewTabs(ai:Boolean,onView:(PlanReviewRoute?)->Unit)=ReviewSwitch(listOf("Review" to false,"AI recommendations" to true),ai){onView(PlanReviewRoute(if(it)ReviewView.AI else ReviewView.REVIEW))}
@Composable private fun ReviewSwitch(items:List<Pair<String,Boolean>>,selected:Boolean,onSelect:(Boolean)->Unit) {
    val c=LocalPlotusColors.current
    Row(Modifier.fillMaxWidth().background(c.quiet,RoundedCornerShape(12.dp)).padding(4.dp),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
        items.forEach {(label,value)->Box(Modifier.weight(1f).heightIn(min=36.dp).background(if(selected==value)c.surface else Color.Transparent,RoundedCornerShape(8.dp))
            .selectable(selected==value,role=Role.Tab){onSelect(value)}.padding(horizontal=4.dp,vertical=8.dp),contentAlignment=Alignment.Center){
            Text(label,fontSize=14.sp,lineHeight=20.sp,fontWeight=FontWeight.SemiBold,color=if(selected==value)c.primary else c.muted,textAlign=TextAlign.Center)
        }}
    }
}
@Composable private fun ReviewStatus(label:String,status:String="pass") {
    val c=LocalPlotusColors.current
    Row(Modifier.fillMaxWidth().heightIn(min=36.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(if(status=="pass")"✓" else if(status=="fail")"!" else "—",Modifier.width(20.dp),color=if(status=="fail")MaterialTheme.colorScheme.error else if(status=="pass")c.success else c.muted,fontSize=14.sp)
        Text(label,fontSize=12.sp,lineHeight=18.sp,color=c.ink)
    }
}
@Composable private fun ReviewTag(value:String) {
    val c=LocalPlotusColors.current
    Text(value,Modifier.fillMaxWidth().background(c.quiet,RoundedCornerShape(8.dp)).padding(8.dp),fontSize=12.sp,lineHeight=18.sp,color=c.primary)
}
@Composable private fun ReviewMeasuredRow(value:ReviewMeasurement) {
    Row(Modifier.fillMaxWidth().heightIn(min=36.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
        Text(value.label,Modifier.weight(1f),fontSize=14.sp,lineHeight=21.sp)
        Text(reviewValue(value),fontSize=14.sp,fontWeight=FontWeight.SemiBold)
    }
}
@Composable private fun reviewValue(value:ReviewMeasurement):String {
    val units=LocalMeasurementUnits.current
    val number=value.value ?: return "Not assessed"
    val converted=if(value.unit=="square_metres"&&units==PlotUnits.FEET_INCHES)number/(METRES_PER_FOOT*METRES_PER_FOOT) else number
    val suffix=when(value.unit){"square_metres"->if(units==PlotUnits.METRES)" m²" else " sq ft";"percent"->"%";else->""}
    return decimal(kotlin.math.round(converted*10)/10)+suffix
}
private fun priorityLabel(value:String)=when(value){"critical"->"Required change";"high"->"High priority";"optional"->"Optional";else->"Medium priority"}
private fun metricTitle(value:String)=when(value){"space"->"Space utilization";"circulation"->"Circulation";"function"->"Functional planning";"daylight"->"Daylight & ventilation";"privacy"->"Privacy";"access"->"Parking & access";else->"Vastu"}
@Composable private fun ReviewTitle(value:String){Text(value,fontSize=26.sp,lineHeight=34.sp,fontWeight=FontWeight.Bold,color=LocalPlotusColors.current.ink)}
@Composable private fun ReviewSection(value:String,modifier:Modifier=Modifier){Text(value,modifier,fontSize=16.sp,lineHeight=24.sp,fontWeight=FontWeight.SemiBold,color=LocalPlotusColors.current.ink)}
@Composable private fun ReviewBody(value:String){Text(value,fontSize=14.sp,lineHeight=21.sp,color=LocalPlotusColors.current.ink)}
@Composable private fun ReviewNote(value:String){Text(value,fontSize=12.sp,lineHeight=18.sp,color=LocalPlotusColors.current.muted)}
