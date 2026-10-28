package com.plotus

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.plotus.resources.*
import com.plotus.shared.*
import org.jetbrains.compose.resources.*

@Composable
fun ProjectsScreen(projects: List<Project>, compact: Boolean, onNew: () -> Unit, onEdit: (Project) -> Unit, onDelete: (Project) -> Unit,
    loading: Boolean = false, busy: Boolean = false, error: String? = null, onRetry: (() -> Unit)? = null,
    onOpen: (Project) -> Unit) {
    val colors = LocalPlotusColors.current
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(ProjectSort.RECENT) }
    var sortOpen by remember { mutableStateOf(false) }
    val visible = visibleProjects(projects, query, sort)
    val panel = if (compact) Modifier.fillMaxSize() else Modifier.fillMaxWidth().heightIn(min = 540.dp)
        .background(colors.surface, RoundedCornerShape(18.dp)).border(1.dp, colors.line, RoundedCornerShape(18.dp)).padding(24.dp)
    Column(panel) {
        if (compact) Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Projects", Modifier.weight(1f), fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.7).sp)
            PlotusButton("New project", onNew, enabled = !busy)
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
        val searchWidth = minOf(360.dp, maxOf(160.dp, maxWidth - 208.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.then(if (compact) Modifier.weight(1f) else Modifier.width(searchWidth)).heightIn(min = 44.dp)
                .background(colors.surface, RoundedCornerShape(12.dp)).border(1.dp, colors.line, RoundedCornerShape(12.dp))
                .padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                PlotusIcon(AppIcon.SEARCH)
                androidx.compose.foundation.text.BasicTextField(query, { query = it }, Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 10.dp),
                    textStyle = LocalTextStyle.current.copy(color = colors.ink), singleLine = true,
                    decorationBox = { inner -> if (query.isEmpty()) Box { Text("Search projects", color = colors.muted); inner() } else inner() })
            }
            Box {
                IconAction(AppIcon.SORT, "Sort projects", { sortOpen = true }, border = true)
                DropdownMenu(sortOpen, { sortOpen = false }) {
                    DropdownMenuItem(text = { Text("Last edited") }, onClick = { sort = ProjectSort.RECENT; sortOpen = false })
                    DropdownMenuItem(text = { Text("Name") }, onClick = { sort = ProjectSort.NAME; sortOpen = false })
                }
            }
            if (!compact) { Spacer(Modifier.weight(1f)); PlotusButton("New project", onNew, enabled = !busy) }
        }
        }
        Spacer(Modifier.height(20.dp))
        if (!compact) Row(Modifier.fillMaxWidth().height(44.dp).background(colors.quiet).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Geometry", Modifier.width(88.dp), fontSize = 12.sp, color = colors.muted)
            Text("Name & location", Modifier.weight(1f), fontSize = 12.sp, color = colors.muted)
            Text("Area", Modifier.width(176.dp), fontSize = 12.sp, color = colors.muted)
            Text("Last edited", Modifier.width(166.dp), fontSize = 12.sp, color = colors.muted)
            Spacer(Modifier.width(44.dp))
        }
        if (error != null) Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(error, color = MaterialTheme.colorScheme.error)
            if (onRetry != null) PlotusButton("Try again", onRetry, outline = true, enabled = !loading)
        }
        if (loading && projects.isEmpty()) Column(Modifier.fillMaxWidth().padding(vertical = 64.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            Text("Loading projects…", color = colors.muted)
        }
        if (!loading && error == null && visible.isEmpty()) Column(Modifier.fillMaxWidth().padding(vertical = 64.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (query.isEmpty()) "Your next plan starts here" else "No matching projects", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text(if (query.isEmpty()) "Create a project to start planning." else "Try a different name or location.", color = colors.muted, modifier = Modifier.padding(vertical = 12.dp))
            PlotusButton(if (query.isEmpty()) "New project" else "Clear search", { if (query.isEmpty()) onNew() else query = "" }, outline = true)
        }
        LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false)) {
            items(visible, key = { it.id }) { project ->
                ProjectRow(project, compact, { onOpen(project) }, { onEdit(project) }, { onDelete(project) })
                HorizontalDivider(color = colors.line)
            }
        }
    }
}

@Composable
private fun ProjectRow(project: Project, compact: Boolean, onOpen: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    val colors = LocalPlotusColors.current
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().heightIn(min = if (compact) 96.dp else 88.dp)
        .padding(horizontal = if (compact) 0.dp else 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).heightIn(min = if (compact) 96.dp else 88.dp).clickable(onClickLabel="Open ${project.name}",role=androidx.compose.ui.semantics.Role.Button,onClick = onOpen), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(if (compact) 62.dp else 88.dp), contentAlignment = if (compact) Alignment.CenterStart else Alignment.Center) { GeometryBadge(project.plotSetup?.vertices.orEmpty()) }
            Column(Modifier.weight(1f)) {
                Text(project.name, fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold)
                Text(project.location, fontSize = if (compact) 13.sp else 14.sp, color = colors.muted)
                if (compact) Text("${areaLabel(project.areaSqFt)} · ${project.lastEdited}", fontSize = 12.sp, color = colors.subtle, lineHeight = 18.sp)
            }
            if (!compact) {
                Text(areaLabel(project.areaSqFt), Modifier.width(176.dp))
                Text(project.lastEdited, Modifier.width(166.dp), color = colors.muted)
            }
        }
        Box {
            IconAction(AppIcon.MORE, "Actions for ${project.name}", { menu = true })
            DropdownMenu(menu, { menu = false }) {
                DropdownMenuItem(text = { Text("Open project") }, onClick = { menu = false; onOpen() })
                DropdownMenuItem(text = { Text("Edit project") }, onClick = { menu = false; onEdit() })
                DropdownMenuItem(text = { Text("Delete project", color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; onDelete() })
            }
        }
    }
}
