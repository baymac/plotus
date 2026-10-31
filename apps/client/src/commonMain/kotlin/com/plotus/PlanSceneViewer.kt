package com.plotus

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import com.plotus.shared.PlanScene3D

/** HTML rendering stays inside the web adapter; account and plan state stay in Kotlin. */
internal expect fun supportsPlanScene3D(): Boolean

internal val LocalEmbeddedViewHidden = staticCompositionLocalOf { false }

@Composable
internal expect fun PlanSceneViewer(scene: PlanScene3D, modifier: Modifier = Modifier, hidden: Boolean = false)
