package com.plotus

import com.plotus.shared.PlanExport
import java.awt.FileDialog
import java.awt.Frame
import java.awt.EventQueue
import java.io.File
import java.util.Base64
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal actual val planDownloadsUseBrowser=false
internal actual fun downloadPlanFromUrl(download:ReadyPlanDownload){error("Desktop exports use the file dialog.")}
internal actual fun prepareBrowserPlanDownload(file:PlanExport):ReadyPlanDownload=error("Desktop exports use the file dialog.")
internal actual fun releaseBrowserPlanDownload(download:ReadyPlanDownload){}

internal actual suspend fun savePlanExport(file:PlanExport):Unit=suspendCancellableCoroutine {continuation->
    EventQueue.invokeLater {
        try {
            val dialog=FileDialog(null as Frame?,"Export floor plan",FileDialog.SAVE).apply {this.file=file.filename;isVisible=true}
            val name=dialog.file;val directory=dialog.directory;dialog.dispose()
            if(name==null||directory==null)continuation.resumeWithException(AuthCancelled())
            else {File(directory,name).writeBytes(Base64.getDecoder().decode(file.base64));continuation.resume(Unit)}
        }catch(error:Exception){continuation.resumeWithException(error)}
    }
}
