package com.plotus

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.plotus.resources.*
import org.jetbrains.compose.resources.painterResource

@Composable
fun PlotusButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, outline: Boolean = false, enabled: Boolean = true, icon: AppIcon? = null) {
    val colors = LocalPlotusColors.current
    val content: @Composable RowScope.() -> Unit = {
        if (icon != null) { PlotusIcon(icon); Spacer(Modifier.width(8.dp)) }
        Text(text, color = if (outline) colors.ink else colors.onPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
    if (outline) OutlinedButton(onClick, modifier.heightIn(min = 44.dp), enabled,
        shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, colors.line),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = colors.surface, contentColor = colors.ink),
        contentPadding = PaddingValues(12.dp), content = content)
    else Button(onClick, modifier.heightIn(min = 44.dp), enabled,
        shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(12.dp), content = content)
}

@Composable
fun IconAction(icon: AppIcon, label: String, onClick: () -> Unit, border: Boolean = false) {
    val colors = LocalPlotusColors.current
    IconButton(onClick, Modifier.size(44.dp).then(if (border) Modifier.background(colors.surface, RoundedCornerShape(12.dp)).border(1.dp, colors.line, RoundedCornerShape(12.dp)) else Modifier)) {
        PlotusIcon(icon, label)
    }
}

@Composable
fun PlotusInput(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier,
                placeholder: String = "", type: KeyboardType = KeyboardType.Text, error: String? = null) {
    val colors = LocalPlotusColors.current
    var focused by remember { mutableStateOf(false) }
    BasicTextField(value, onChange,
        modifier.fillMaxWidth().heightIn(min = 44.dp)
            .background(colors.surface, RoundedCornerShape(12.dp))
            .border(if (focused) 2.dp else 1.dp, if (focused || error != null) colors.primary else colors.line, RoundedCornerShape(12.dp))
            .onFocusChanged { focused = it.isFocused }.semantics { contentDescription = label },
        textStyle = LocalTextStyle.current.copy(color = colors.ink, fontSize = 14.sp),
        cursorBrush = SolidColor(colors.primary), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = type),
        decorationBox = { inner ->
            Box(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) Text(placeholder, color = colors.muted)
                inner()
            }
        })
}

@Composable
fun LabeledInput(label: String, value: String, onChange: (String) -> Unit, error: String? = null, type: KeyboardType = KeyboardType.Text) {
    val colors = LocalPlotusColors.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, fontSize = 12.sp, color = colors.muted)
        PlotusInput(value, onChange, label, type = type, error = error)
        if (error != null) Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
    }
}

@Composable
fun GeometryBadge() {
    val colors = LocalPlotusColors.current
    Box(Modifier.size(48.dp).background(colors.surface, RoundedCornerShape(12.dp))
        .border(1.dp, colors.border, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
        Image(painterResource(if (colors.dark) Res.drawable.boundary_dark else Res.drawable.boundary), null, Modifier.size(34.dp))
        Text("PLOT", fontSize = 7.5.sp, color = colors.muted, modifier = Modifier.offset(x = (-3).dp))
    }
}
