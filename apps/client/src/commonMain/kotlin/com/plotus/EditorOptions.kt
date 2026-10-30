package com.plotus

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun EditorOptions(showDivider:Boolean=true,content: @Composable ColumnScope.() -> Unit) {
    val c=LocalPlotusColors.current
    if(showDivider)HorizontalDivider(color=c.line)
    Box(Modifier.fillMaxWidth().background(c.surface).padding(horizontal=16.dp,vertical=10.dp),contentAlignment=Alignment.Center) {
        Column(Modifier.widthIn(max=560.dp).fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp),content=content)
    }
}

@Composable
internal fun EditorOption(label:String,symbol:PlotSymbol,enabled:Boolean,modifier:Modifier=Modifier,highlighted:Boolean=false,onClick:()->Unit) {
    val c=LocalPlotusColors.current
    Column(modifier.height(60.dp).background(c.surface,RoundedCornerShape(10.dp))
        .border(1.dp,if(highlighted)c.primary else c.line,RoundedCornerShape(10.dp)).clickable(enabled=enabled,role=Role.Button,onClick=onClick),
        horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
        PlotSymbol(symbol,c.muted,Modifier.size(18.dp))
        Text(label,fontSize=13.sp,color=c.muted)
    }
}

/** Keep the mobile controls together; place them beside the canvas on wide screens. */
@Composable
internal fun EditorOverview(compact:Boolean,modifier:Modifier,content:@Composable ColumnScope.()->Unit,options:@Composable (Boolean)->Unit) {
    val c=LocalPlotusColors.current
    if(compact)Column(modifier) {
        content()
        options(true)
    } else Row(modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f).fillMaxHeight(),content=content)
        VerticalDivider(color=c.line)
        Column(Modifier.width(408.dp).fillMaxHeight().background(c.surface).verticalScroll(rememberScrollState())) {options(false)}
    }
}

@Composable
internal fun InspectorHeader(title:String,enabled:Boolean,canSave:Boolean,onBack:()->Unit,showSave:Boolean=true,onSave:()->Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min=48.dp),verticalAlignment=Alignment.CenterVertically) {
        EditorBack(title,"Back",enabled=enabled,onBack=onBack)
        if(showSave)androidx.compose.material3.TextButton(onSave,enabled=enabled&&canSave) {Text("Save")}
    }
}
