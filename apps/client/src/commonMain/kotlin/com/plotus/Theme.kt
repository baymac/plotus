package com.plotus

import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.size
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.plotus.resources.*
import org.jetbrains.compose.resources.*

enum class Appearance { SYSTEM, LIGHT, DARK }

data class PlotusColors(
    val background: Color, val surface: Color, val ink: Color, val muted: Color,
    val subtle: Color, val line: Color, val border: Color, val primary: Color,
    val onPrimary: Color, val active: Color, val quiet: Color, val dark: Boolean,
)

val LightColors = PlotusColors(Color(0xFFFBF9F2), Color.White, Color(0xFF22221E), Color(0xFF77756E),
    Color(0xFF9E9B91), Color(0xFFEAE6DD), Color(0xFFDEDAD0), Color(0xFFC25E2E), Color.White,
    Color(0xFFFAE9E0), Color(0xFFF3F0E6), false)
val DarkColors = PlotusColors(Color(0xFF141513), Color(0xFF1C1E1B), Color(0xFFEEEAE2), Color(0xFFB1AFA5),
    Color(0xFFAAA79D), Color(0xFF353731), Color(0xFF45473E), Color(0xFFDF895E), Color(0xFF1C1E1B),
    Color(0xFF35261F), Color(0xFF22251F), true)
val LocalPlotusColors = staticCompositionLocalOf { LightColors }

@Composable
fun PlotusTheme(appearance: Appearance, content: @Composable () -> Unit) {
    val dark = when (appearance) { Appearance.SYSTEM -> isSystemInDarkTheme(); Appearance.DARK -> true; Appearance.LIGHT -> false }
    val colors = if (dark) DarkColors else LightColors
    val family = FontFamily(Font(Res.font.jakarta_regular, FontWeight.Normal), Font(Res.font.jakarta_semibold, FontWeight.SemiBold), Font(Res.font.jakarta_bold, FontWeight.Bold))
    val style = TextStyle(fontFamily = family, color = colors.ink, fontSize = 14.sp, lineHeight = 21.sp)
    val scheme = if (dark) darkColorScheme() else lightColorScheme()
    CompositionLocalProvider(LocalPlotusColors provides colors) {
        MaterialTheme(
            colorScheme = scheme.copy(primary = colors.primary, onPrimary = colors.onPrimary, background = colors.background,
                surface = colors.surface, onSurface = colors.ink, onBackground = colors.ink, outline = colors.line),
            typography = Typography(bodyLarge = style, bodyMedium = style, bodySmall = style.copy(fontSize = 12.sp, lineHeight = 18.sp),
                labelLarge = style.copy(fontWeight = FontWeight.SemiBold), titleLarge = style.copy(fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold)),
            content = content,
        )
    }
}

enum class AppIcon { SEARCH, SIDEBAR, PROJECTS, MOBILE_PROJECTS, SETTINGS, ACCOUNT, SORT, MORE, CLOSE, PASSKEY, GOOGLE, FACEBOOK, ARROW }

@Composable
fun BrandMark(size: Dp, modifier: Modifier = Modifier) {
    val resource = if (LocalPlotusColors.current.dark) Res.drawable.plotus_symbol_dark else Res.drawable.plotus_symbol
    Image(painterResource(resource), "Plotus", modifier.size(size))
}

@Composable
fun PlotusIcon(icon: AppIcon, description: String? = null) {
    val dark = LocalPlotusColors.current.dark
    val resource = when (icon) {
        AppIcon.SEARCH -> Res.drawable.search
        AppIcon.SIDEBAR -> if (dark) Res.drawable.sidebar_dark else Res.drawable.sidebar
        AppIcon.PROJECTS -> if (dark) Res.drawable.projects_dark else Res.drawable.projects
        AppIcon.MOBILE_PROJECTS -> if (dark) Res.drawable.mobile_projects_dark else Res.drawable.mobile_projects
        AppIcon.SETTINGS -> if (dark) Res.drawable.settings_dark else Res.drawable.settings
        AppIcon.ACCOUNT -> if (dark) Res.drawable.account_dark else Res.drawable.account
        AppIcon.SORT -> if (dark) Res.drawable.sort_dark else Res.drawable.sort
        AppIcon.MORE -> if (dark) Res.drawable.more_dark else Res.drawable.more
        AppIcon.CLOSE -> Res.drawable.close
        AppIcon.PASSKEY -> if (dark) Res.drawable.passkey_dark else Res.drawable.passkey
        AppIcon.GOOGLE -> Res.drawable.google
        AppIcon.FACEBOOK -> Res.drawable.facebook
        AppIcon.ARROW -> Res.drawable.arrow
    }
    Image(painterResource(resource), description, Modifier.size(if (icon == AppIcon.SEARCH) 18.dp else 20.dp))
}
