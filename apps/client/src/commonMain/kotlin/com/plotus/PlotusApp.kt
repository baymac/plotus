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

enum class Destination { PROJECTS, GENERAL, ACCOUNT }

@Composable
fun PlotusApp() {
    val scope = rememberCoroutineScope()
    val auth = remember { AuthSession(scope) }
    LaunchedEffect(auth) { auth.restore() }
    LaunchedEffect(auth.user?.id) {
        if (auth.user != null) while (true) { delay(12 * 60 * 1000L); auth.refreshIfSignedIn() }
    }
    var appearance by remember { mutableStateOf(Appearance.entries.firstOrNull { it.name == readPreference("appearance") } ?: Appearance.SYSTEM) }
    var collapsed by remember { mutableStateOf(readPreference("sidebar") == "collapsed") }
    var preview by remember { mutableStateOf(false) }
    var destination by remember { mutableStateOf(Destination.PROJECTS) }
    var projects by remember { mutableStateOf(sampleProjects()) }
    var editing by remember { mutableStateOf<Project?>(null) }
    var showForm by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Project?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var nextId by remember { mutableStateOf(1) }

    PlotusTheme(appearance) {
        val colors = LocalPlotusColors.current
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 44.dp) {
            BoxWithConstraints(Modifier.fillMaxSize().background(colors.background).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))) {
                val compact = maxWidth < 1000.dp
                val account = auth.user
                val exit = { if (preview) preview = false else auth.signOut() }
                if (!preview && account == null) Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))) {
                    SignInScreen(compact,
                        onGoogle = { destination = Destination.PROJECTS; auth.google() },
                        onPasskey = { destination = Destination.PROJECTS; auth.passkey() },
                        onPreview = { destination = Destination.PROJECTS; preview = true },
                        busy = auth.busy || auth.initializing, action = auth.action)
                }
                else Row(Modifier.fillMaxSize()) {
                    if (!compact) Sidebar(destination, collapsed, onCollapse = {
                        collapsed = !collapsed; writePreference("sidebar", if (collapsed) "collapsed" else "open")
                    }, onNavigate = { destination = it })
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        AppHeader(compact, destination, appearance,
                            onAppearance = { appearance = it; writePreference("appearance", it.name) },
                            onExit = exit, account = if (preview) null else account,
                            onAccount = { destination = Destination.ACCOUNT })
                        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = if (compact) 24.dp else 32.dp,
                            vertical = if (compact) 12.dp else 32.dp)) {
                            if (destination == Destination.PROJECTS && preview) ProjectsScreen(projects, compact,
                                onNew = { editing = null; showForm = true },
                                onEdit = { editing = it; showForm = true }, onDelete = { deleting = it })
                            else if (destination == Destination.PROJECTS && account != null) AuthenticatedProjects(account) { destination = Destination.ACCOUNT }
                            else if (destination == Destination.ACCOUNT && !preview && account != null) AccountScreen(account, auth.passkeys, auth.busy,
                                onAddPasskey = { auth.addPasskey() }, onRemovePasskey = { auth.removePasskey(it) }, onSignOut = { auth.signOut() })
                            else SettingsScreen(destination, compact, onExit = exit,
                                accountEmail = if (preview) null else account?.email, onAccount = { destination = Destination.ACCOUNT })
                        }
                        if (compact) MobileNavigation(destination) { destination = it }
                    }
                }
                if (showForm) ProjectFormDialog(editing, compact, onDismiss = { showForm = false }, onSave = { draft ->
                    val project = draft.toProject(editing?.id ?: "preview-new-${nextId++}", editing)
                    projects = saveProject(projects, project); showForm = false
                })
                deleting?.let { project ->
                    AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Delete project?") },
                        text = { Text("Delete “${project.name}” from the local preview? This cannot be undone.") },
                        confirmButton = { TextButton(onClick = { projects = projects.filterNot { it.id == project.id }; deleting = null }) { Text("Delete project") } },
                        dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } })
                }
                (auth.error ?: auth.notice ?: message)?.let { text ->
                    val dismiss = { message = null; auth.dismissError(); auth.dismissNotice() }
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
            if (destination == Destination.PROJECTS) NavItem("Projects", AppIcon.PROJECTS, true, collapsed) { onNavigate(Destination.PROJECTS) }
            else {
                NavItem("Back to app", AppIcon.ARROW, false, collapsed) { onNavigate(Destination.PROJECTS) }
                NavItem("General", AppIcon.SETTINGS, destination == Destination.GENERAL, collapsed) { onNavigate(Destination.GENERAL) }
                NavItem("Account", AppIcon.ACCOUNT, destination == Destination.ACCOUNT, collapsed) { onNavigate(Destination.ACCOUNT) }
            }
        }
        Spacer(Modifier.weight(1f))
        if (destination == Destination.PROJECTS) Column(Modifier.padding(12.dp)) {
            HorizontalDivider(color = colors.line)
            Spacer(Modifier.height(8.dp))
            NavItem("Settings", AppIcon.SETTINGS, false, collapsed) { onNavigate(Destination.GENERAL) }
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
private fun AppHeader(compact: Boolean, destination: Destination, appearance: Appearance, onAppearance: (Appearance) -> Unit, onExit: () -> Unit,
    account: AuthUser? = null, onAccount: () -> Unit = {}) {
    val colors = LocalPlotusColors.current
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().height(if (compact) 76.dp else 72.dp).padding(horizontal = if (compact) 24.dp else 32.dp),
        verticalAlignment = Alignment.CenterVertically) {
        if (compact) Wordmark(Modifier.weight(1f))
        else { Text(when (destination) { Destination.PROJECTS -> "Projects"; Destination.GENERAL -> "General"; Destination.ACCOUNT -> "Account" }, Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Box {
                IconAction(AppIcon.ACCOUNT, "Open account menu", { menu = true })
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text(account?.email ?: "Design preview · local sample data", fontSize = 12.sp) }, enabled = false, onClick = {})
                    if (account != null) DropdownMenuItem(text = { Text("Account") }, onClick = { menu = false; onAccount() })
                    Appearance.entries.forEach { option -> DropdownMenuItem(text = { Text("${if (appearance == option) "✓ " else ""}${option.name.lowercase().replaceFirstChar { it.uppercase() }} theme") },
                        onClick = { onAppearance(option); menu = false }) }
                    DropdownMenuItem(text = { Text(if (account == null) "Back to sign in" else "Sign out") }, onClick = { menu = false; onExit() })
                }
            }
        }
    }
    if (!compact) HorizontalDivider(color = colors.line)
}

@Composable
private fun MobileNavigation(destination: Destination, onNavigate: (Destination) -> Unit) {
    val colors = LocalPlotusColors.current
    Column(Modifier.fillMaxWidth().background(colors.background).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))) {
        HorizontalDivider(color = colors.line)
        Row(Modifier.fillMaxWidth().height(60.dp).padding(vertical = 4.dp)) {
            listOf("Projects" to Destination.PROJECTS, "Settings" to Destination.GENERAL).forEach { (label, target) ->
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
