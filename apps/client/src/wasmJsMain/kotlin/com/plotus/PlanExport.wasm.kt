@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
package com.plotus

import com.plotus.shared.PlanExport
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

internal actual val planDownloadsUseBrowser=true

@JsFun("""(base64,mime)=>{
 const types=['application/pdf','image/png','image/vnd.dwg','application/acad','application/x-acad','application/dwg'];
 if(!types.includes(mime)||base64.length>32*1024*1024)throw new Error('This file could not be prepared.');
 const raw=atob(base64),bytes=Uint8Array.from(raw,c=>c.charCodeAt(0));
 const valid=mime==='application/pdf'?raw.startsWith('%PDF-'):mime==='image/png'?bytes[0]===137&&raw.slice(1,4)==='PNG':/^AC10[0-9]{2}/.test(raw);
 if(!valid)throw new Error('The export did not contain a valid drawing.');
}""")
private external fun validateFile(base64:String,mime:String)

@JsFun("""(base64,mime,name)=>{
 const bytes=Uint8Array.from(atob(base64),c=>c.charCodeAt(0));
 const url=URL.createObjectURL(new Blob([bytes],{type:mime}));
 const link=document.createElement('a');
 link.href=url;
 link.download=name;
 link.style.display='none';
 document.body.appendChild(link);
 try {
  // Called synchronously by the Compose button's original user gesture.
  // Keep the browser download outside the Compose dialog's HTML interop layer.
  link.click();
 } catch(error) {
  link.remove();URL.revokeObjectURL(url);throw error;
 }
 // Browser navigation consumes the URL asynchronously, especially in Safari.
 setTimeout(()=>{link.remove();URL.revokeObjectURL(url);},60000);
}""")
private external fun downloadBrowserFile(base64:String,mime:String,name:String)

internal actual fun prepareBrowserPlanDownload(file:PlanExport,path:String):ReadyPlanDownload {
    validateFile(file.base64,file.mimeType)
    require(path.startsWith("/v1/projects/")&&!path.contains('?')&&!path.contains('#'))
    return ReadyPlanDownload(file.filename,file.filename.substringAfterLast('.'),path)
}

internal actual suspend fun savePlanExport(file:PlanExport) {
    validateFile(file.base64,file.mimeType)
    downloadBrowserFile(file.base64,file.mimeType,file.filename)
}

@Composable
internal actual fun PlanDownloadAction(download:ReadyPlanDownload?,enabled:Boolean,label:String,onClick:()->Unit) {
    TextButton(onClick=onClick,enabled=enabled){Text(label)}
}
