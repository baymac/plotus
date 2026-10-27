package com.plotus

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AccountScreen(user: AuthUser, passkeys: List<SavedPasskey>, busy: Boolean,
    onAddPasskey: () -> Unit, onRemovePasskey: (String) -> Unit, onSignOut: () -> Unit, showTitle: Boolean = true) {
    val colors = LocalPlotusColors.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        if (showTitle) Text("Account", fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold)
        Row(settingsGroup().padding(20.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(48.dp).background(colors.active, RoundedCornerShape(24.dp)), contentAlignment = Alignment.Center) {
                Text(user.name.firstOrNull()?.uppercaseChar()?.toString().orEmpty(), color = colors.primary, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(user.name, fontSize = 18.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold)
                if (user.email.isNotBlank()) Text(user.email, color = colors.muted, fontSize = 14.sp, lineHeight = 21.sp)
            }
        }
        Column(Modifier.widthIn(max = 660.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Passkeys", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Column(settingsGroup()) {
                passkeys.forEach { key ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PlotusIcon(AppIcon.PASSKEY)
                        Text(key.label.ifBlank { "Passkey" }, Modifier.weight(1f))
                        TextButton(onClick = { onRemovePasskey(key.id) }, enabled = !busy) { Text("Remove") }
                    }
                    HorizontalDivider(Modifier.padding(start = 16.dp), color = colors.line)
                }
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(enabled = !busy, role = Role.Button, onClick = onAddPasskey)
                    .padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PlotusIcon(AppIcon.PASSKEY)
                    Text("Add a passkey", color = colors.primary, fontSize = 16.sp)
                }
            }
        }
        Row(settingsGroup().heightIn(min = 56.dp).clickable(enabled = !busy, role = Role.Button, onClick = onSignOut)
            .padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Sign out", color = MaterialTheme.colorScheme.error, fontSize = 16.sp)
        }
    }
}
