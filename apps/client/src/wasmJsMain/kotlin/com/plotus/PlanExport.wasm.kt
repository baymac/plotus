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
import org.w3c.dom.HTMLAnchorElement

internal actual val planDownloadsUseBrowser=true

@JsFun("""(base64,mime,name)=>{
 const types=['application/pdf','image/png','image/vnd.dwg','application/acad','application/x-acad','application/dwg'];
 if(!types.includes(mime)||base64.length>32*1024*1024)throw new Error('This file could not be prepared.');
 const raw=atob(base64),bytes=Uint8Array.from(raw,c=>c.charCodeAt(0));
 const valid=mime==='application/pdf'?raw.startsWith('%PDF-'):mime==='image/png'?bytes[0]===137&&raw.slice(1,4)==='PNG':/^AC10[0-9]{2}/.test(raw);
 if(!valid)throw new Error('The export did not contain a valid drawing.');
 return URL.createObjectURL(new Blob([bytes],{type:mime}));
}""")
private external fun prepareBlob(base64:String,mime:String,name:String):String

@JsFun("""(path,name)=>{
 const url=new URL(path);
 if(url.protocol!=='blob:'||url.origin!==window.location.origin)throw new Error('Prepare this file again.');
 const link=document.createElement('a');link.href=url.href;link.download=name;link.rel='noopener';
 document.body.appendChild(link);link.click();link.remove();
}""")
private external fun startPreparedDownload(path:String,name:String)

@JsFun("""path=>URL.revokeObjectURL(path)""")
private external fun releaseBlob(path:String)

// Fetch and validate through the existing authenticated API before enabling
// Download. Its click performs no asynchronous work or page navigation.
internal actual fun prepareBrowserPlanDownload(file:PlanExport):ReadyPlanDownload =
    ReadyPlanDownload(file.filename,file.filename.substringAfterLast('.'),prepareBlob(file.base64,file.mimeType,file.filename))
internal actual fun releaseBrowserPlanDownload(download:ReadyPlanDownload){releaseBlob(download.path)}
internal actual fun downloadPlanFromUrl(download:ReadyPlanDownload){startPreparedDownload(download.path,download.filename)}
internal actual suspend fun savePlanExport(file:PlanExport){throw AuthFailure(0,"Choose Download to save this file.")}

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
        })
}
