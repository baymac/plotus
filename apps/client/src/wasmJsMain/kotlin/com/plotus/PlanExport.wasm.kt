@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
package com.plotus

import com.plotus.shared.PlanExport
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.WebElementView
import kotlinx.browser.document
import kotlinx.coroutines.await
import org.w3c.dom.HTMLAnchorElement
import kotlin.js.JsString
import kotlin.js.Promise

internal actual val planDownloadsUseBrowser=true

@JsFun("""(base64,mime)=>{
 const types=['application/pdf','image/png','image/vnd.dwg','application/acad','application/x-acad','application/dwg'];
 if(!types.includes(mime)||base64.length>32*1024*1024)throw new Error('This file could not be prepared.');
 const raw=atob(base64),bytes=Uint8Array.from(raw,c=>c.charCodeAt(0));
 const valid=mime==='application/pdf'?raw.startsWith('%PDF-'):mime==='image/png'?bytes[0]===137&&raw.slice(1,4)==='PNG':/^AC10[0-9]{2}/.test(raw);
 if(!valid)throw new Error('The export did not contain a valid drawing.');
}""")
private external fun validateFile(base64:String,mime:String)

@JsFun("""(element,action)=>{
 // Keep the picker invocation in the actual native link click. A supported
 // browser saves to a user-selected handle; other browsers follow the owned
 // HTTPS attachment URL through their normal download manager.
 element.onclick=typeof window.showSaveFilePicker==='function'?event=>{event.preventDefault();action();}:null;
}""")
private external fun bindSaveAction(element:HTMLAnchorElement,action:()->Unit)

@JsFun("""(base64,mime,name)=>new Promise(resolve=>{
 if(typeof window.showSaveFilePicker!=='function'){resolve('This browser could not open the Save dialog.');return;}
 const format=name.split('.').pop().toLowerCase();
 let picked;
 try {
  // Invoke before any await: transient user activation belongs to this click.
  picked=window.showSaveFilePicker({suggestedName:name,types:[{description:format.toUpperCase()+' drawing',accept:{[mime]:['.'+format]}}]});
 }catch(error){resolve('Could not open the Save dialog. Please try again.');return;}
 picked.then(async handle=>{
  let stream;
  try {
   const bytes=Uint8Array.from(atob(base64),c=>c.charCodeAt(0));
   stream=await handle.createWritable();
   await stream.write(new Blob([bytes],{type:mime}));
   await stream.close();
   resolve('saved');
  }catch(error){
   if(stream)try{await stream.abort();}catch(_){}
   resolve('Could not save this file. Please try again.');
  }
 }).catch(error=>resolve(error?.name==='AbortError'?'cancelled':'Could not open the Save dialog. Please try again.'));
})""")
private external fun writeSelectedFile(base64:String,mime:String,name:String):Promise<JsString>

internal actual fun prepareBrowserPlanDownload(file:PlanExport,path:String):ReadyPlanDownload {
    validateFile(file.base64,file.mimeType)
    require(path.startsWith("/v1/projects/")&&!path.contains('?')&&!path.contains('#'))
    return ReadyPlanDownload(file.filename,file.filename.substringAfterLast('.'),"$path?download=1")
}

internal actual suspend fun savePlanExport(file:PlanExport) {
    when(val result=writeSelectedFile(file.base64,file.mimeType,file.filename).await<JsString>().toString()) {
        "saved"->Unit
        "cancelled"->throw AuthCancelled()
        else->throw AuthFailure(0,result)
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal actual fun PlanDownloadAction(download:ReadyPlanDownload?,enabled:Boolean,label:String,onClick:()->Unit) {
    if(download==null||!enabled) {
        TextButton(onClick,enabled=enabled){Text(label)}
        return
    }
    val color="#"+(LocalPlotusColors.current.primary.toArgb() and 0xFFFFFF).toString(16).padStart(6,'0')
    WebElementView(factory={document.createElement("a") as HTMLAnchorElement},
        modifier=Modifier.width(152.dp).height(48.dp),update={link->
            link.href=download.path
            link.download=download.filename
            link.rel="noopener"
            link.textContent=label
            link.setAttribute("aria-label",label)
            link.style.cssText="display:flex;align-items:center;justify-content:center;box-sizing:border-box;width:100%;height:100%;padding:0 12px;border-radius:24px;color:$color;background:transparent;font:600 14px system-ui,sans-serif;text-decoration:none;cursor:pointer;"
            bindSaveAction(link,onClick)
        })
}
