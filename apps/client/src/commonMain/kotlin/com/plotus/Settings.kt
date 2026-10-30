package com.plotus

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import com.plotus.shared.PlotUnits
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsHomeScreen(onNavigate: (Destination) -> Unit) {
    val colors = LocalPlotusColors.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text("Settings", fontSize = 30.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp)
        Column(settingsGroup()) {
            SettingsItem("General", AppIcon.SETTINGS) { onNavigate(Destination.GENERAL) }
            HorizontalDivider(Modifier.padding(start = 64.dp), color = colors.line)
            SettingsItem("Account", AppIcon.ACCOUNT) { onNavigate(Destination.ACCOUNT) }
        }
    }
}

@Composable
private fun SettingsItem(label: String, icon: AppIcon, onClick: () -> Unit) {
    val colors = LocalPlotusColors.current
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(role = Role.Button, onClick = onClick)
        .padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).background(colors.quiet, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
            PlotusIcon(icon)
        }
        Spacer(Modifier.width(12.dp))
        Text(label, Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.Medium)
        SettingsChevron(Modifier.clearAndSetSemantics { })
    }
}

@Composable
fun SettingsPageHeader(title: String, onBack: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 12.dp)) {
        IconButton(onBack, Modifier.align(Alignment.CenterStart).size(44.dp).semantics { contentDescription = "Back to Settings" }) {
            SettingsChevron(Modifier.rotate(180f))
        }
        Text(title, Modifier.align(Alignment.Center), fontSize = 18.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SettingsChevron(modifier: Modifier = Modifier) {
    val color = LocalPlotusColors.current.muted
    Canvas(modifier.size(18.dp)) {
        val stroke = 1.7.dp.toPx()
        drawLine(color, Offset(size.width * .36f, size.height * .22f), Offset(size.width * .64f, size.height * .5f), stroke, StrokeCap.Round)
        drawLine(color, Offset(size.width * .64f, size.height * .5f), Offset(size.width * .36f, size.height * .78f), stroke, StrokeCap.Round)
    }
}

@Composable
fun GeneralSettingsScreen(compact: Boolean, appearance: Appearance, onAppearance: (Appearance) -> Unit) {
    val colors = LocalPlotusColors.current
    Column(Modifier.widthIn(max=660.dp).fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val units=LocalMeasurementUnits.current
        val selectUnits=LocalSetMeasurementUnits.current
        Text("Measurement units",fontSize=16.sp,lineHeight=24.sp,fontWeight=FontWeight.SemiBold)
        Column(settingsGroup().selectableGroup()) {
            PlotUnits.entries.forEachIndexed {index,unit->
                if(index>0)HorizontalDivider(Modifier.padding(start=16.dp),color=colors.line)
                Row(Modifier.fillMaxWidth().heightIn(min=56.dp).selectable(units==unit,role=Role.RadioButton,onClick={selectUnits(unit)}).padding(horizontal=16.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically) {
                    Text(if(unit==PlotUnits.METRES)"Metres" else "Feet & inches",Modifier.weight(1f),fontSize=16.sp)
                    RadioButton(units==unit,onClick=null)
                }
            }
        }
        Text("Appearance", fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold)
        Column(settingsGroup().selectableGroup()) {
            listOf(Appearance.SYSTEM, Appearance.LIGHT, Appearance.DARK).forEachIndexed { index, option ->
                if (index > 0) HorizontalDivider(Modifier.padding(start = 16.dp), color = colors.line)
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp)
                    .selectable(selected = appearance == option, onClick = { onAppearance(option) }, role = Role.RadioButton)
                    .padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(option.name.lowercase().replaceFirstChar { it.uppercase() }, Modifier.weight(1f), fontSize = 16.sp)
                    RadioButton(selected = appearance == option, onClick = null)
                }
            }
        }
    }
}

@Composable
fun PreviewAccountScreen(compact: Boolean, onSignIn: () -> Unit) {
    val colors = LocalPlotusColors.current
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        if (!compact) Text("Account", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text("Sign in to manage your account.", color = colors.muted)
        PlotusButton("Sign in", onSignIn)
    }
}

@Composable
internal fun settingsGroup(): Modifier {
    val colors = LocalPlotusColors.current
    val shape = RoundedCornerShape(16.dp)
    return Modifier.widthIn(max = 660.dp).fillMaxWidth().clip(shape).background(colors.surface).border(1.dp, colors.line, shape)
}
