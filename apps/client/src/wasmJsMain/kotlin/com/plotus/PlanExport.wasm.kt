@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
package com.plotus

import com.plotus.shared.PlanExport
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@JsFun("""(format,name) => {
 const mime={png:'image/png',pdf:'application/pdf',dwg:'application/acad'}[format];
 const selection=window.showSaveFilePicker && mime ? window.showSaveFilePicker({
   suggestedName:name,types:[{description:format.toUpperCase()+' floor plan',accept:{[mime]:['.'+format]}}]
 }) : Promise.resolve(null);
 selection.catch(()=>{});
 return {selection,handle:null};
}""")
private external fun selectPlanDestination(format:String,name:String):JsAny

@JsFun("""(target,done,failed) => {
 target.selection.then(handle=>{target.handle=handle;done();},error=>failed(
   error && error.name==='AbortError' ? 'cancelled' : 'Could not choose a file. Try exporting again.'));
}""")
private external fun awaitPlanDestination(target:JsAny,done:()->Unit,failed:(String)->Unit)

@JsFun("""(target,name,mime,encoded,done,failed) => {
 (async()=>{
   const raw=atob(encoded), bytes=new Uint8Array(raw.length);
   for(let i=0;i<raw.length;i++)bytes[i]=raw.charCodeAt(i);
   const blob=new Blob([bytes],{type:mime});
   if(target.handle){
     const writer=await target.handle.createWritable();
     try{await writer.write(blob);await writer.close();}
     catch(error){await writer.abort().catch(()=>{});throw error;}
   }else{
     const url=URL.createObjectURL(blob),link=document.createElement('a');
     link.href=url;link.download=name;document.body.appendChild(link);
     try{link.click();}finally{link.remove();setTimeout(()=>URL.revokeObjectURL(url),60000);}
   }
 })().then(done,error=>failed(error && error.name==='AbortError' ? 'cancelled' : 'Could not save the file. Try exporting again.'));
}""")
private external fun writePlanDestination(target:JsAny,name:String,mime:String,encoded:String,done:()->Unit,failed:(String)->Unit)

internal actual fun preparePlanExport(format:String,filename:String):PlanExportDestination {
    // Called directly by the format's click handler, before starting the network coroutine.
    val target=selectPlanDestination(format,filename)
    return PlanExportDestination(ready={suspendCancellableCoroutine {continuation->
        awaitPlanDestination(target,{if(continuation.isActive)continuation.resume(Unit)},{error->
            if(continuation.isActive)continuation.resumeWithException(if(error=="cancelled")AuthCancelled() else AuthFailure(0,error))
        })
    }},save={file->suspendCancellableCoroutine {continuation->
        writePlanDestination(target,file.filename,file.mimeType,file.base64,
            {if(continuation.isActive)continuation.resume(Unit)},{error->
                if(continuation.isActive)continuation.resumeWithException(if(error=="cancelled")AuthCancelled() else AuthFailure(0,error))
            })
    }})
}

@JsFun("""(name,mime,encoded) => {
 const raw=atob(encoded), bytes=new Uint8Array(raw.length);
 for(let i=0;i<raw.length;i++)bytes[i]=raw.charCodeAt(i);
 const url=URL.createObjectURL(new Blob([bytes],{type:mime}));
 const link=document.createElement('a');link.href=url;link.download=name;document.body.appendChild(link);
 try{link.click();}finally{link.remove();setTimeout(()=>URL.revokeObjectURL(url),30000);}
}""")
private external fun downloadPlan(name:String,mime:String,encoded:String)
internal actual suspend fun savePlanExport(file:PlanExport) {downloadPlan(file.filename,file.mimeType,file.base64)}
