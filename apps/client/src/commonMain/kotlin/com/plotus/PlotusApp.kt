package com.plotus

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.plotus.shared.*
import kotlinx.coroutines.delay
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.decodeFromJsonElement

@Composable
fun PlotusApp() {
    val scope = rememberCoroutineScope()
    val navigation = remember { AppNavigation(readAppLocation()) }
    DisposableEffect(navigation) {
        val unsubscribe = observeAppLocation { navigation.receive(readAppLocation()) }
        onDispose { unsubscribe() }
    }
    val auth = remember { AuthSession(scope) }
    LaunchedEffect(auth) { auth.restore() }
    LaunchedEffect(auth.user?.id, auth.initializing, navigation.destination) {
        navigation.reconcileSession(auth.user != null, auth.initializing)
    }
    LaunchedEffect(auth.user?.id) {
        if (auth.user != null) while (true) { delay(12 * 60 * 1000L); auth.refreshIfSignedIn() }
    }
    var appearance by remember { mutableStateOf(Appearance.entries.firstOrNull { it.name == readPreference("appearance") } ?: Appearance.SYSTEM) }
    var collapsed by remember { mutableStateOf(readPreference("sidebar") == "collapsed") }
    var preview by remember { mutableStateOf(false) }
    val destination = navigation.destination
    var projects by remember { mutableStateOf(sampleProjects()) }
    var editing by remember { mutableStateOf<Project?>(null) }
    var showForm by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Project?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var nextId by remember { mutableStateOf(1) }
    val previewPlots = remember { mutableMapOf<String, PlotWorkspace>() }
    val account = auth.user
    val measurements=remember(account?.id) {
        MeasurementPreferences(scope,account?.id ?: "preview",{method,value->
            if(account==null)value ?: AccountPreferences(PlotUnits.entries.firstOrNull {it.name==readPreference("measurement-units:preview")} ?: PlotUnits.FEET_INCHES)
            else {
                if(auth.user?.id!=account.id)throw AuthFailure(401,"Your account changed.")
                val result=auth.projectRequest(method,"/v1/preferences",value?.let {authJson.encodeToString(it)})
                if(auth.user?.id!=account.id)throw AuthFailure(401,"Your account changed.")
                authJson.decodeFromJsonElement<AccountPreferences>(result)
            }
        })
    }
    LaunchedEffect(measurements,account?.id) {
        if(account!=null)while(true){measurements.refresh();delay(15_000L)}
    }
    val workspace = remember(account?.id) { account?.let { ProjectWorkspace(auth, scope, it.id){measurements.units} } }
    LaunchedEffect(workspace,preview,navigation.projectID,destination) {
        if(workspace!=null&&!preview)navigation.projectID?.let {id->when(destination) {
            Destination.PLOT->workspace.rememberSection(id,PlanningSection.PLOT)
            Destination.FLOOR_PLAN->workspace.rememberSection(id,PlanningSection.FLOOR_PLAN)
            else->Unit
        }}
    }
    LaunchedEffect(account?.id, destination) { showForm = false; editing = null; deleting = null }
    LaunchedEffect(workspace, preview, destination) {
        if (workspace != null && !preview && destination == Destination.PROJECTS) {
            while (true) { workspace.refresh(); delay(30_000L) }
        }
    }

    PlotusTheme(appearance) {
        val colors = LocalPlotusColors.current
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 44.dp,LocalMeasurementUnits provides measurements.units,LocalSetMeasurementUnits provides measurements::select,
            LocalEmbeddedViewHidden provides (auth.error != null || auth.notice != null || message != null)) {
            BoxWithConstraints(Modifier.fillMaxSize().background(colors.background).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))) {
                val compact = maxWidth < 1000.dp
                val exit = { if (preview) preview = false else auth.signOut() }
                if (navigation.browser && (auth.initializing || (account != null && (destination == Destination.LOGIN||measurements.loading)))) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
                else if(!navigation.browser&&account!=null&&measurements.loading)Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){CircularProgressIndicator()}
                else if (!preview && account == null) Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))) {
                    SignInScreen(compact,
                        onGoogle = { if (!navigation.browser) navigation.navigate(Destination.PROJECTS); auth.google(navigation.loginCallbackPath) },
                        onFacebook = { if (!navigation.browser) navigation.navigate(Destination.PROJECTS); auth.facebook(navigation.loginCallbackPath) },
                        onPasskey = { if (!navigation.browser) navigation.navigate(Destination.PROJECTS); auth.passkey() },
                        onPreview = if (navigation.browser) null else { { navigation.navigate(Destination.PROJECTS); preview = true } },
                        busy = auth.busy || auth.initializing, action = auth.action)
                }
                else Row(Modifier.fillMaxSize()) {
                    if (!compact) Sidebar(destination, collapsed, onCollapse = {
                        collapsed = !collapsed; writePreference("sidebar", if (collapsed) "collapsed" else "open")
                    }, onNavigate = { navigation.navigate(it) })
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        if (destination in listOf(Destination.GENERAL,Destination.ACCOUNT) || (compact && destination == Destination.PROJECTS)) AppHeader(compact, destination) { navigation.navigate(Destination.SETTINGS) }
                        if (destination == Destination.PLOT) {
                            val id = navigation.projectID!!
                            val plot = if (preview) previewPlots.getOrPut(id) {
                                PlotWorkspace(scope, { projects.first { it.id == id } }, { project, setup ->
                                    project.copy(plotSetup=setup, areaSqFt=setup.areaSquareFeet, revision=project.revision+1, lastEdited="Just now")
                                }, { project -> projects=saveProject(projects,project) })
                            } else workspace!!.plot(id)
                            Box(Modifier.weight(1f).fillMaxWidth()) {
                                PlotSetupScreen(plot,navigation.plotStep,compact,{navigation.openProject(id,it,replace=compact)},{navigation.navigate(Destination.PROJECTS)},{if(preview)message="Sign in to configure a floor plan." else navigation.openFloor(id)})
                            }
                        } else if(destination==Destination.FLOOR_PLAN&&workspace!=null) {
                            val id=navigation.projectID!!
                            Box(Modifier.weight(1f).fillMaxWidth()) {
                                FloorPlanScreen(workspace.floor(id),id,navigation.floorStep,compact,{navigation.openFloor(id,it,replace=compact)},{navigation.openProject(id)},{navigation.navigate(Destination.PROJECTS)},navigation.generationID,{generation->navigation.openFloor(id,FloorStep.HISTORY,generation=generation)})
                            }
                        } else {
                        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = if (compact) 24.dp else 32.dp,
                            vertical = if (compact) 12.dp else 32.dp), contentAlignment=Alignment.TopCenter) {
                            Box(Modifier.widthIn(max=if(destination==Destination.PROJECTS)1120.dp else 660.dp).fillMaxSize()) {
                            if (destination == Destination.PROJECTS) ProjectsScreen(if (preview) projects else workspace?.projects.orEmpty(), compact,
                                onNew = { editing = null; workspace?.clearMutationError(); showForm = true },
                                onEdit = { editing = it; workspace?.clearMutationError(); showForm = true },
                                onDelete = { deleting = it; workspace?.clearMutationError() },
                                loading = !preview && workspace?.loading == true, busy = !preview && workspace?.busy == true,
                                error = if (preview) null else workspace?.error,
                                onRetry = if (preview) null else { { workspace?.reload() } },
                                onOpen = { navigation.openOverview(it.id,workspace?.sectionFor(it) ?: it.lastOpenedSection) })
                            else if (destination == Destination.SETTINGS) SettingsHomeScreen { navigation.navigate(it) }
                            else if (destination == Destination.GENERAL) GeneralSettingsScreen(compact, appearance) {
                                appearance = it; writePreference("appearance", it.name)
                            }
                            else if (!preview && account != null) AccountScreen(account, auth.passkeys, auth.busy,
                                onAddPasskey = { auth.addPasskey() }, onRemovePasskey = { auth.removePasskey(it) }, onSignOut = { auth.signOut() },
                                showTitle = false)
                            else PreviewAccountScreen(true, onSignIn = exit)
                            }
                        }
                        if (compact) MobileNavigation(destination) { navigation.navigate(it) }
                        }
                    }
                }
                if (destination == Destination.PROJECTS && showForm && (preview || account != null)) ProjectFormDialog(editing, compact, onDismiss = { showForm = false },
                    busy = !preview && workspace?.busy == true, error = if (preview) null else workspace?.mutationError,
                    onSave = { draft ->
                        val creating = editing == null
                        if (preview) {
                            val project = draft.toProject(editing?.id ?: "preview-new-${nextId++}", editing)
                            projects = saveProject(projects, project); showForm = false
                            if(creating) navigation.openProject(project.id)
                        } else workspace?.save(editing, draft) { saved ->
                            showForm = false
                            if(creating && navigation.destination == Destination.PROJECTS) navigation.openProject(saved.id)
                        }
                    })
                if (destination == Destination.PROJECTS && (preview || account != null)) deleting?.let { project ->
                    val busy = !preview && workspace?.busy == true
                    val dismiss = { if (!busy) deleting = null }
                    val failed=!preview&&workspace?.mutationError!=null
                    val changed=!preview&&workspace?.mutationErrorCode=="PROJECT_CHANGED"
                    val name=workspace?.projects?.firstOrNull {it.id==project.id}?.name ?: project.name
                    AlertDialog(onDismissRequest = dismiss, title = {Text(when {
                        changed->"Delete updated project?"
                        failed->"Couldn’t delete project"
                        else->"Delete “$name”?"
                    })},
                        confirmButton = { TextButton(enabled = !busy, onClick = {
                            if (preview) { projects = projects.filterNot { it.id == project.id }; deleting = null }
                            else workspace?.delete(project) { deleting = null }
                        }) { Text(if (busy) "Deleting…" else if(changed)"Delete updated project" else if(failed)"Try again" else "Delete project") } },
                        dismissButton = { TextButton(enabled = !busy, onClick = dismiss) { Text("Cancel") } })
                }
                (auth.error ?: auth.notice ?: message ?: measurements.error)?.let { text ->
                    val dismiss = { message = null; auth.dismissError(); auth.dismissNotice();measurements.dismissError() }
                    AlertDialog(onDismissRequest = dismiss, title = { Text("Plotus") }, text = { Text(text) },
                        confirmButton = { TextButton(onClick = dismiss) { Text("OK") } })
                }
            }
        }
    }
}

@Composable
private fun Sidebar(destination: Destination, collapsed: Boolean, onCollapse: () -> Unit, onNavigate: (Destination) -> Unit) {
    val colors = LocalPlotusColors.current
    val width by animateDpAsState(if (collapsed) 72.dp else 264.dp, tween(180))
    Column(Modifier.width(width).fillMaxHeight().border(BorderStroke(1.dp, colors.line))) {
        Row(Modifier.fillMaxWidth().height(72.dp).padding(horizontal = if (collapsed) 14.dp else 20.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!collapsed) { Wordmark(Modifier.weight(1f)); }
            IconAction(AppIcon.SIDEBAR, if (collapsed) "Expand sidebar" else "Collapse sidebar", onCollapse)
        }
        HorizontalDivider(color = colors.line)
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            NavItem("Projects", AppIcon.PROJECTS, destination in listOf(Destination.PROJECTS,Destination.PLOT,Destination.FLOOR_PLAN), collapsed) { onNavigate(Destination.PROJECTS) }
        }
        Spacer(Modifier.weight(1f))
        Column(Modifier.padding(12.dp)) {
            HorizontalDivider(color = colors.line)
            Spacer(Modifier.height(8.dp))
            NavItem("Settings", AppIcon.SETTINGS, destination in listOf(Destination.SETTINGS,Destination.GENERAL,Destination.ACCOUNT), collapsed) { onNavigate(Destination.SETTINGS) }
        }
    }
}

@Composable
private fun NavItem(label: String, icon: AppIcon, selected: Boolean, collapsed: Boolean, onClick: () -> Unit) {
    val colors = LocalPlotusColors.current
    Row(Modifier.fillMaxWidth().height(48.dp).background(if (selected) colors.active else colors.background, RoundedCornerShape(12.dp))
        .clickable(onClick = onClick).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (collapsed) Arrangement.Center else Arrangement.Start) {
        Box(if (icon == AppIcon.ARROW) Modifier.rotate(180f) else Modifier) { PlotusIcon(icon, if (collapsed) label else null) }
        if (!collapsed) { Spacer(Modifier.width(12.dp)); Text(label, color = if (selected) colors.ink else colors.muted) }
    }
}

@Composable
private fun AppHeader(compact: Boolean, destination: Destination, onSettingsBack: () -> Unit) {
    val colors = LocalPlotusColors.current
    if (destination in listOf(Destination.GENERAL,Destination.ACCOUNT)) {
        SettingsPageHeader(if (destination == Destination.GENERAL) "General" else "Account", onSettingsBack)
        return
    }
    Row(Modifier.fillMaxWidth().height(if (compact) 76.dp else 72.dp).padding(horizontal = if (compact) 24.dp else 32.dp),
        verticalAlignment = Alignment.CenterVertically) {
        if (compact) Wordmark(Modifier.weight(1f))
        else Text(when (destination) { Destination.PROJECTS -> "Projects"; Destination.SETTINGS -> "Settings"; Destination.GENERAL -> "General"; Destination.ACCOUNT -> "Account"; Destination.LOGIN -> "Sign in"; Destination.PLOT -> "Plot setup"; Destination.FLOOR_PLAN -> "Floor plan" },
            Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
    if (!compact) HorizontalDivider(color = colors.line)
}

@Composable
private fun MobileNavigation(destination: Destination, onNavigate: (Destination) -> Unit) {
    val colors = LocalPlotusColors.current
    Column(Modifier.fillMaxWidth().background(colors.background).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))) {
        HorizontalDivider(color = colors.line)
        Row(Modifier.fillMaxWidth().height(60.dp).padding(vertical = 4.dp)) {
            listOf("Projects" to Destination.PROJECTS, "Settings" to Destination.SETTINGS).forEach { (label, target) ->
                val selected = if (target == Destination.PROJECTS) destination == target else destination != Destination.PROJECTS
                Column(Modifier.weight(1f).fillMaxHeight().clickable { onNavigate(target) }, horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically)) {
                    Box(Modifier.size(64.dp, 28.dp).background(if (selected) colors.active else colors.background, RoundedCornerShape(999.dp)), contentAlignment = Alignment.Center) {
                        PlotusIcon(if (target == Destination.PROJECTS) AppIcon.MOBILE_PROJECTS else AppIcon.SETTINGS)
                    }
                    Text(label, fontSize = 12.sp, lineHeight = 16.sp, color = if (selected) colors.ink else colors.muted)
                }
            }
        }
    }
}

@Composable
fun Wordmark(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        BrandMark(26.dp)
        Text("Plotus", fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.7).sp)
    }
}
