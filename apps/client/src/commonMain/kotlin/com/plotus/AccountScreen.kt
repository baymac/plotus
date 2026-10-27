package com.plotus

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AccountScreen(user: AuthUser, passkeys: List<SavedPasskey>, busy: Boolean,
    onAddPasskey: () -> Unit, onRemovePasskey: (String) -> Unit, onSignOut: () -> Unit) {
    val colors = LocalPlotusColors.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text("Account", fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold)
        Column(Modifier.widthIn(max = 660.dp).fillMaxWidth().background(colors.surface, RoundedCornerShape(16.dp)).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(user.name, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            if (user.email.isNotBlank()) Text(user.email, color = colors.muted)
        }
        Column(Modifier.widthIn(max = 660.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Passkeys", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text("Sign in with your device’s fingerprint, face or screen lock.", color = colors.muted)
            passkeys.forEach { key ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(key.label.ifBlank { "Passkey" }, Modifier.weight(1f))
                    TextButton(onClick = { onRemovePasskey(key.id) }, enabled = !busy) { Text("Remove passkey") }
                }
            }
            OutlinedButton(onClick = onAddPasskey, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) { Text("Add a passkey") }
        }
        HorizontalDivider(color = colors.line)
        TextButton(onClick = onSignOut, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) { Text("Sign out") }
    }
}
