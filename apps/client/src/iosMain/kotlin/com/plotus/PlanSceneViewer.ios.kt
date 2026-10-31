package com.plotus

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.plotus.shared.PlanScene3D

internal actual fun supportsPlanScene3D() = false

@Composable
internal actual fun PlanSceneViewer(scene: PlanScene3D, modifier: Modifier, hidden: Boolean) = Unit
