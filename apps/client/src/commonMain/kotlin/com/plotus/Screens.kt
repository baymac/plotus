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
fun ProjectsScreen(projects: List<Project>, compact: Boolean, onNew: () -> Unit, onEdit: (Project) -> Unit, onDelete: (Project) -> Unit) {
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
            PlotusButton("New project", onNew)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.then(if (compact) Modifier.weight(1f) else Modifier.width(360.dp)).heightIn(min = 44.dp)
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
            if (!compact) { Spacer(Modifier.weight(1f)); PlotusButton("New project", onNew) }
        }
        Spacer(Modifier.height(20.dp))
        if (!compact) Row(Modifier.fillMaxWidth().height(44.dp).background(colors.quiet).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Geometry", Modifier.width(88.dp), fontSize = 12.sp, color = colors.muted)
            Text("Name & location", Modifier.weight(1f), fontSize = 12.sp, color = colors.muted)
            Text("Area", Modifier.width(176.dp), fontSize = 12.sp, color = colors.muted)
            Text("Last edited", Modifier.width(166.dp), fontSize = 12.sp, color = colors.muted)
            Spacer(Modifier.width(44.dp))
        }
        if (visible.isEmpty()) Column(Modifier.fillMaxWidth().padding(vertical = 64.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (query.isEmpty()) "Your next plan starts here" else "No matching projects", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text(if (query.isEmpty()) "Create a project to start planning." else "Try a different name or location.", color = colors.muted, modifier = Modifier.padding(vertical = 12.dp))
            PlotusButton(if (query.isEmpty()) "New project" else "Clear search", { if (query.isEmpty()) onNew() else query = "" }, outline = true)
        }
        LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false)) {
            items(visible, key = { it.id }) { project ->
                ProjectRow(project, compact, { onEdit(project) }, { onDelete(project) })
                HorizontalDivider(color = colors.line)
            }
        }
    }
}

@Composable
private fun ProjectRow(project: Project, compact: Boolean, onEdit: () -> Unit, onDelete: () -> Unit) {
    val colors = LocalPlotusColors.current
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().heightIn(min = if (compact) 96.dp else 88.dp)
        .padding(horizontal = if (compact) 0.dp else 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).heightIn(min = if (compact) 96.dp else 88.dp).clickable(onClick = onEdit), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(if (compact) 62.dp else 88.dp), contentAlignment = if (compact) Alignment.CenterStart else Alignment.Center) { GeometryBadge() }
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
                DropdownMenuItem(text = { Text("Edit project") }, onClick = { menu = false; onEdit() })
                DropdownMenuItem(text = { Text("Delete project", color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; onDelete() })
            }
        }
    }
}

@Composable
fun SettingsScreen(destination: Destination, compact: Boolean, onExit: () -> Unit, accountEmail: String? = null, onAccount: () -> Unit = {}) {
    val colors = LocalPlotusColors.current
    val panel = if (compact) Modifier.fillMaxSize() else Modifier.fillMaxWidth().heightIn(min = 540.dp)
        .background(colors.surface, RoundedCornerShape(18.dp)).border(1.dp, colors.line, RoundedCornerShape(18.dp)).padding(28.dp)
    Column(panel.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        if (compact) Text("General", fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold)
        if (destination == Destination.GENERAL) {
            if (accountEmail != null) {
                Text("Account", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                TextButton(onAccount) { Text(accountEmail) }
            }
            Text("About", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            listOf("App" to "Plotus", "Version" to "0.1.0").forEach { (label, value) ->
                Column(Modifier.widthIn(max = 660.dp)) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp)) { Text(label, Modifier.weight(1f), color = colors.muted); Text(value) }
                    Spacer(Modifier.height(14.dp)); HorizontalDivider(color = colors.line)
                }
            }
            if (compact) TextButton(onExit) { Text("Leave design preview") }
        } else {
            Text("Design preview", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text("Local sample projects. You are not signed in.", color = colors.muted)
            PlotusButton("Back to sign in", onExit, outline = true)
        }
    }
}
