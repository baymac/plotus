package com.plotus

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.plotus.shared.*

@Composable
fun ProjectFormDialog(project: Project?, compact: Boolean, onDismiss: () -> Unit, onSave: (ProjectDraft) -> Unit) {
    val colors = LocalPlotusColors.current
    var draft by remember(project?.id) { mutableStateOf(project?.let(ProjectDraft::from) ?: ProjectDraft()) }
    var submitted by remember { mutableStateOf(false) }
    val errors = if (submitted) draft.errors() else emptyMap()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = if (compact) Alignment.BottomCenter else Alignment.CenterEnd) {
            val maxHeight = maxHeight
            Column(Modifier.width(if (compact) maxWidth else 440.dp).heightIn(max = maxHeight)
                .then(if (compact) Modifier else Modifier.fillMaxHeight())
                .background(colors.surface, RoundedCornerShape(topStart = if (compact) 20.dp else 0.dp, topEnd = if (compact) 20.dp else 0.dp))
                .verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (project == null) "New project" else "Edit project", Modifier.weight(1f), fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold)
                    IconAction(AppIcon.CLOSE, "Close dialog", onDismiss)
                }
                LabeledInput("Name", draft.name, { draft = draft.copy(name = it.take(120)) }, errors["name"])
                LabeledInput("Location", draft.location, { draft = draft.copy(location = it.take(200)) }, errors["location"])
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Client project?", Modifier.weight(1f))
                    Switch(draft.isClient, { draft = draft.copy(isClient = it) })
                }
                if (draft.isClient) Column(Modifier.fillMaxWidth().background(colors.quiet, RoundedCornerShape(12.dp))
                    .border(1.dp, colors.line, RoundedCornerShape(12.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Client information", color = colors.muted, fontSize = 12.sp)
                    LabeledInput("Client name", draft.clientName, { draft = draft.copy(clientName = it.take(120)) }, errors["clientName"])
                    LabeledInput("Email", draft.email, { draft = draft.copy(email = it.take(254)) }, errors["email"], KeyboardType.Email)
                    LabeledInput("Phone no.", draft.phone, { draft = draft.copy(phone = it.take(40)) }, type = KeyboardType.Phone)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PlotusButton("Cancel", onDismiss, Modifier.weight(1f).height(48.dp), outline = true)
                    PlotusButton(if (project == null) "Create project" else "Save changes", {
                        submitted = true
                        if (draft.errors().isEmpty()) onSave(draft)
                    }, Modifier.weight(2f).height(48.dp))
                }
            }
        }
    }
}
