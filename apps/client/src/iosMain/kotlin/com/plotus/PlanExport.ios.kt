package com.plotus

import com.plotus.shared.PlanExport
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import androidx.compose.runtime.Composable
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton

@Composable
internal actual fun PlanDownloadAction(download:ReadyPlanDownload?,enabled:Boolean,label:String,onClick:()->Unit) {
    TextButton(onClick,enabled=enabled){Text(label)}
}

internal actual val planDownloadsUseBrowser=false
internal actual fun prepareBrowserPlanDownload(file:PlanExport,path:String):ReadyPlanDownload=error("Native exports use the share sheet.")

internal actual suspend fun savePlanExport(file:PlanExport):Unit=suspendCancellableCoroutine {continuation->
    val host=NativeAuthBridge.host
    if(host==null)continuation.resumeWithException(AuthFailure(0,"Export is unavailable."))
    else host.savePlanFile(file.filename,file.mimeType,file.base64){_,error->if(continuation.isActive) {
        when(error){"cancelled"->continuation.resumeWithException(AuthCancelled());null->continuation.resume(Unit);else->continuation.resumeWithException(AuthFailure(0,error))}
    }}
}
