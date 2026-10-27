package com.plotus

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SignInScreen(compact: Boolean, onGoogle: () -> Unit, onPasskey: () -> Unit, onPreview: () -> Unit) {
    val colors = LocalPlotusColors.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val viewportHeight = maxHeight
        WelcomeAtmosphere(Modifier.fillMaxSize())
        if (compact) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                    .heightIn(min = maxOf(viewportHeight, 620.dp)).padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                WelcomeBrand(Modifier.fillMaxWidth().padding(top = 20.dp))
                Spacer(Modifier.weight(0.65f))
                Box(Modifier.fillMaxWidth().height(minOf(280.dp, viewportHeight * 0.34f)), contentAlignment = Alignment.Center) {
                    WelcomeModel(Modifier.fillMaxHeight().aspectRatio(1.25f))
                }
                Spacer(Modifier.height(8.dp))
                Text("Make room for\nyour next idea.", fontSize = 34.sp, lineHeight = 40.sp,
                    letterSpacing = (-1.2).sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Spacer(Modifier.height(24.dp))
                Spacer(Modifier.weight(1f))
                GoogleAction(onGoogle, Modifier.widthIn(max = 400.dp).fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                PasskeyAction(onPasskey, Modifier.widthIn(max = 400.dp).fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                DemoAction(onPreview)
                Spacer(Modifier.height(16.dp))
            }
        } else {
            Row(Modifier.fillMaxSize()) {
                Column(Modifier.weight(1.15f).fillMaxHeight().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    WelcomeBrand(Modifier.fillMaxWidth())
                    Spacer(Modifier.weight(1f))
                    WelcomeModel(Modifier.widthIn(max = 480.dp).fillMaxWidth().aspectRatio(1.25f))
                    Text("Make room for\nyour next idea.", fontSize = 48.sp, lineHeight = 56.sp,
                        letterSpacing = (-1.8).sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Spacer(Modifier.weight(1.3f))
                }
                Box(Modifier.width(1.dp).fillMaxHeight().background(colors.border.copy(alpha = 0.5f)))
                Column(Modifier.weight(0.85f).fillMaxHeight().background(colors.surface.copy(alpha = if (colors.dark) 0.3f else 0.5f))
                    .padding(horizontal = 48.dp, vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.weight(1f))
                    Column(Modifier.widthIn(max = 360.dp).fillMaxWidth()) {
                        Text("Welcome to Plotus", fontSize = 28.sp, lineHeight = 36.sp, letterSpacing = (-0.7).sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(12.dp))
                        Text("A new space starts here.", color = colors.muted, fontSize = 15.sp)
                        Spacer(Modifier.height(32.dp))
                        GoogleAction(onGoogle, Modifier.fillMaxWidth())
                        Spacer(Modifier.height(12.dp))
                        PasskeyAction(onPasskey, Modifier.fillMaxWidth())
                    }
                    Spacer(Modifier.weight(1f))
                    DemoAction(onPreview)
                }
            }
        }
    }
}

@Composable
private fun WelcomeBrand(modifier: Modifier = Modifier) {
    val colors = LocalPlotusColors.current
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        BrandMark(26.dp, Modifier.clearAndSetSemantics { })
        Text("Plotus", color = colors.ink, fontSize = 21.sp, lineHeight = 28.sp, letterSpacing = (-0.7).sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun GoogleAction(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalPlotusColors.current
    OutlinedButton(onClick, modifier.heightIn(min = 56.dp), shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, colors.border),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = colors.surface, contentColor = colors.ink),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp)) {
        PlotusIcon(AppIcon.GOOGLE)
        Spacer(Modifier.width(12.dp))
        Text("Continue with Google", fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold, color = colors.ink)
    }
}

@Composable
private fun PasskeyAction(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalPlotusColors.current
    OutlinedButton(onClick, modifier.heightIn(min = 52.dp), shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, colors.border),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.ink),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)) {
        PlotusIcon(AppIcon.PASSKEY)
        Spacer(Modifier.width(12.dp))
        Text("Use a passkey", fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, color = colors.ink)
    }
}

@Composable
private fun DemoAction(onClick: () -> Unit) {
    TextButton(onClick, Modifier.heightIn(min = 44.dp)) {
        Text("View demo", fontSize = 12.sp, color = LocalPlotusColors.current.muted)
    }
}

@Composable
private fun WelcomeAtmosphere(modifier: Modifier) {
    val colors = LocalPlotusColors.current
    Canvas(modifier) {
        drawRect(colors.background)
        drawRect(Brush.radialGradient(
            listOf(colors.primary.copy(alpha = if (colors.dark) 0.14f else 0.13f), Color.Transparent),
            center = Offset(size.width * 0.8f, size.height * 0.22f), radius = size.maxDimension * 0.62f))
        drawRect(Brush.radialGradient(
            listOf(Color(0xFF9BAF8F).copy(alpha = if (colors.dark) 0.07f else 0.12f), Color.Transparent),
            center = Offset(size.width * 0.02f, size.height * 0.79f), radius = size.maxDimension * 0.55f))
    }
}

/** An editable Compose drawing: a miniature home on a sheet of planning paper. */
@Composable
private fun WelcomeModel(modifier: Modifier) {
    val colors = LocalPlotusColors.current
    Canvas(modifier.clearAndSetSemantics { }) {
        withTransform({ scale(size.width / 400f, size.height / 320f, pivot = Offset.Zero) }) {
            val paper = if (colors.dark) Color(0xFF33392F) else Color(0xFFFFFCF5)
            val floor = if (colors.dark) Color(0xFF555643) else Color(0xFFF1E6CC)
            val clay = if (colors.dark) Color(0xFFD18A64) else Color(0xFFCC7950)
            val sage = if (colors.dark) Color(0xFF7B9576) else Color(0xFFA6B89C)
            fun polygon(points: List<Offset>, color: Color) {
                val path = Path().apply { moveTo(points[0].x, points[0].y); points.drop(1).forEach { lineTo(it.x, it.y) }; close() }
                drawPath(path, color)
            }
            fun point(x: Float, y: Float, z: Float = 0f) = Offset(202f + (x - y) * 0.78f, 116f + (x + y) * 0.42f - z)
            fun slab(x: Float, y: Float, width: Float, depth: Float, height: Float, color: Color, z: Float = 0f) {
                val a = point(x,y,z); val b = point(x+width,y,z); val c = point(x+width,y+depth,z); val d = point(x,y+depth,z)
                val lift = Offset(0f,-height)
                polygon(listOf(b,c,c+lift,b+lift), lerp(color, Color.Black, 0.13f))
                polygon(listOf(d,c,c+lift,d+lift), lerp(color, Color.Black, 0.05f))
                polygon(listOf(a+lift,b+lift,c+lift,d+lift),color)
            }
            drawCircle(Brush.radialGradient(listOf(paper.copy(alpha=0.7f),paper.copy(alpha=0f)),Offset(202f,150f),145f),145f,Offset(202f,150f))
            drawOval(colors.primary.copy(alpha=0.16f),Offset(30f,28f),Size(340f,250f),style=Stroke(0.8f))
            drawOval(Brush.radialGradient(listOf(colors.ink.copy(alpha=0.12f),Color.Transparent),Offset(207f,270f),145f),Offset(52f,241f),Size(310f,56f))
            slab(-18f,-8f,204f,180f,3f,lerp(paper,clay,0.08f),-12f)
            slab(-23f,-14f,204f,180f,3f,paper,-6f)
            // Fine planning lines stay on the paper rather than becoming UI chrome.
            for (i in 0..8) {
                val axis=i*22f-20f
                drawLine(colors.primary.copy(alpha=0.07f),point(axis,-12f,-2f),point(axis,164f,-2f),0.5f)
                drawLine(colors.primary.copy(alpha=0.07f),point(-20f,axis,-2f),point(178f,axis,-2f),0.5f)
            }
            slab(0f,0f,160f,150f,5f,floor)
            slab(0f,0f,8f,150f,48f,lerp(clay,paper,0.42f),5f)
            slab(8f,0f,152f,8f,48f,clay,5f)
            // A bedroom, a low dividing wall and an open living space.
            slab(87f,18f,48f,56f,9f,paper,5f)
            slab(87f,34f,48f,40f,11f,lerp(clay,paper,0.55f),5f)
            slab(91f,21f,17f,12f,11f,paper,5f)
            slab(113f,21f,17f,12f,11f,paper,5f)
            slab(74f,8f,6f,69f,28f,paper,5f)
            slab(80f,72f,40f,6f,28f,paper,5f)
            slab(18f,100f,59f,27f,12f,sage,5f)
            slab(18f,100f,59f,6f,21f,sage,5f)
            slab(18f,106f,6f,21f,17f,sage,5f)
            slab(71f,106f,6f,21f,17f,sage,5f)
            slab(28f,108f,19f,16f,14f,lerp(sage,paper,0.16f),5f)
            slab(48f,108f,19f,16f,14f,lerp(sage,paper,0.12f),5f)
            slab(96f,104f,28f,25f,12f,lerp(clay,paper,0.4f),5f)
            slab(100f,108f,15f,9f,1f,paper,17f)
            val plant=point(139f,133f,10f)
            drawOval(clay,plant-Offset(7f,0f),Size(14f,10f))
            drawLine(lerp(sage,Color.Black,0.2f),plant,plant-Offset(0f,29f),2f)
            drawOval(sage,plant-Offset(13f,31f),Size(16f,22f))
            drawOval(lerp(sage,paper,0.2f),plant-Offset(0f,35f),Size(14f,24f))
            slab(65f,151f,38f,12f,3f,lerp(paper,clay,0.15f),-1f)
            // A restrained spark and orbit dot echo the welcoming reference.
            val star=Offset(342f,64f)
            polygon(listOf(star+Offset(0f,-11f),star+Offset(3f,-3f),star+Offset(11f,0f),star+Offset(3f,3f),star+Offset(0f,11f),star+Offset(-3f,3f),star+Offset(-11f,0f),star+Offset(-3f,-3f)),colors.primary)
            drawCircle(colors.primary.copy(alpha=0.45f),3.5f,Offset(58f,214f))
            drawCircle(sage,2.5f,Offset(318f,264f))
        }
    }
}
