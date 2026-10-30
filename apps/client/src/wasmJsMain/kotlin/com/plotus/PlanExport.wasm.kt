@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
package com.plotus

import com.plotus.shared.PlanExport

internal actual val planDownloadsUseBrowser=true

@JsFun("""(path,name)=>{
 const url=new URL(path,window.location.origin);
 if(url.origin!==window.location.origin || !url.pathname.startsWith('/v1/projects/') || url.search!=='?download=1')throw new Error('Invalid download address.');
 const link=document.createElement('a');link.href=url.href;link.download=name;
 link.target='_blank';link.rel='noopener';document.body.appendChild(link);
 try{link.click();}finally{link.remove();}
}""")
private external fun startHttpDownload(path:String,name:String)

// This call stays in the visible Download button's click handler. The server
// sends a genuine HTTP attachment after rechecking the user's session/ownership.
internal actual fun downloadPlanFromUrl(download:ReadyPlanDownload){startHttpDownload(download.path,download.filename)}
internal actual suspend fun savePlanExport(file:PlanExport){throw AuthFailure(0,"Choose Download to save this file.")}
