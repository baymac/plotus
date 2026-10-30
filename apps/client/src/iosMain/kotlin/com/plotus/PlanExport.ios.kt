package com.plotus

import com.plotus.shared.PlanExport
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal actual fun preparePlanExport(format:String,filename:String)=PlanExportDestination(save=::savePlanExport)

internal actual suspend fun savePlanExport(file:PlanExport):Unit=suspendCancellableCoroutine {continuation->
    val host=NativeAuthBridge.host
    if(host==null)continuation.resumeWithException(AuthFailure(0,"Export is unavailable."))
    else host.savePlanFile(file.filename,file.mimeType,file.base64){_,error->if(continuation.isActive) {
        when(error){"cancelled"->continuation.resumeWithException(AuthCancelled());null->continuation.resume(Unit);else->continuation.resumeWithException(AuthFailure(0,error))}
    }}
}
