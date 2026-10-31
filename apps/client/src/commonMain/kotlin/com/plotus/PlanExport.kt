package com.plotus

import com.plotus.shared.PlanExport

internal expect suspend fun savePlanExport(file:PlanExport)

internal data class ReadyPlanDownload(val filename:String,val format:String,val path:String)
internal expect val planDownloadsUseBrowser:Boolean
internal expect fun downloadPlanFromUrl(download:ReadyPlanDownload)
internal expect fun prepareBrowserPlanDownload(file:PlanExport):ReadyPlanDownload
internal expect fun releaseBrowserPlanDownload(download:ReadyPlanDownload)
