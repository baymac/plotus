@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
package com.plotus

import com.plotus.shared.PlanExport

internal actual val planDownloadsUseBrowser=true

@JsFun("""(path,name)=>{
 const url=new URL(path,window.location.origin);
 if(url.origin!==window.location.origin || !url.pathname.startsWith('/v1/projects/') || url.search!=='?download=1')throw new Error('Invalid download address.');
 window.location.assign(url.href);
}""")
private external fun startHttpDownload(path:String,name:String)

// A normal navigation receives an authenticated HTTP attachment. It needs no
// popup, file picker, Blob URL or asynchronous browser activation.
internal actual fun downloadPlanFromUrl(download:ReadyPlanDownload){startHttpDownload(download.path,download.filename)}
internal actual suspend fun savePlanExport(file:PlanExport){throw AuthFailure(0,"Choose Download to save this file.")}
