package com.plotus

import androidx.compose.runtime.Composable
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton

import com.plotus.shared.PlanExport
import java.awt.FileDialog
import java.awt.Frame
import java.awt.EventQueue
import java.io.File
import java.util.Base64
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Composable
internal actual fun PlanDownloadAction(download:ReadyPlanDownload?,enabled:Boolean,label:String,onClick:()->Unit) {
    TextButton(onClick,enabled=enabled){Text(label)}
}

internal actual val planDownloadsUseBrowser=false
internal actual fun prepareBrowserPlanDownload(file:PlanExport,path:String):ReadyPlanDownload=error("Desktop exports use the file dialog.")

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
