@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
package com.plotus

import com.plotus.shared.PlanExport

@JsFun("""(name,mime,encoded) => {
 const raw=atob(encoded), bytes=new Uint8Array(raw.length);
 for(let i=0;i<raw.length;i++)bytes[i]=raw.charCodeAt(i);
 const url=URL.createObjectURL(new Blob([bytes],{type:mime}));
 const link=document.createElement('a');link.href=url;link.download=name;document.body.appendChild(link);
 try{link.click();}finally{link.remove();setTimeout(()=>URL.revokeObjectURL(url),30000);}
}""")
private external fun downloadPlan(name:String,mime:String,encoded:String)
internal actual suspend fun savePlanExport(file:PlanExport) {downloadPlan(file.filename,file.mimeType,file.base64)}
