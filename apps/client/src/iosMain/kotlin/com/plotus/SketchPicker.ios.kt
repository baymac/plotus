package com.plotus

import com.plotus.shared.SketchImage
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal actual suspend fun pickSketchImage():SketchImage? = suspendCancellableCoroutine { continuation ->
    val host=NativeAuthBridge.host
    if(host==null) {continuation.resumeWithException(AuthFailure(0,"Couldn’t open your photos."));return@suspendCancellableCoroutine}
    host.pickPlotImage { value,error -> if(continuation.isActive) {
        when {
            error=="cancelled"->continuation.resume(null)
            error!=null->continuation.resumeWithException(AuthFailure(0,error))
            value!=null->try {continuation.resume(authJson.decodeFromString<SketchImage>(value))} catch(failure:Exception){continuation.resumeWithException(failure)}
            else->continuation.resume(null)
        }
    }}
}
