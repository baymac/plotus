package com.plotus

import com.plotus.shared.PlanExport
import androidx.compose.runtime.Composable

internal expect suspend fun savePlanExport(file:PlanExport)

internal data class ReadyPlanDownload(val filename:String,val format:String,val path:String)
internal expect val planDownloadsUseBrowser:Boolean
internal expect fun downloadPlanFromUrl(download:ReadyPlanDownload)
internal expect fun prepareBrowserPlanDownload(file:PlanExport):ReadyPlanDownload
internal expect fun releaseBrowserPlanDownload(download:ReadyPlanDownload)

// Browsers need a real link's default action for file downloads. Compose owns
// the dialog/state; this platform bridge supplies that native browser action.
@Composable
internal expect fun PlanDownloadAction(download:ReadyPlanDownload?,enabled:Boolean,label:String,onClick:()->Unit)
