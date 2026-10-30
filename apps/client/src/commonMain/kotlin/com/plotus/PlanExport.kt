package com.plotus

import com.plotus.shared.PlanExport

internal expect suspend fun savePlanExport(file:PlanExport)

/** Reserve browser user activation before the authenticated export request can suspend. */
internal class PlanExportDestination(
    val ready:suspend ()->Unit={},
    val save:suspend (PlanExport)->Unit,
)
internal expect fun preparePlanExport(format:String,filename:String):PlanExportDestination
