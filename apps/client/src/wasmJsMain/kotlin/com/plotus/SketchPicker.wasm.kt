@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
package com.plotus

import com.plotus.shared.SketchImage
import kotlinx.coroutines.await
import kotlin.JsFun
import kotlin.js.JsString
import kotlin.js.Promise

@JsFun("""() => new Promise((resolve,reject) => {
 const input=document.createElement('input');input.type='file';input.accept='image/*';
 const finish=value=>{input.remove();resolve(value);};
 input.oncancel=()=>finish('null');
 input.onchange=async()=>{
  const file=input.files?.[0];if(!file){finish('null');return;}
  if(file.size>20*1024*1024){input.remove();reject(Error('Choose an image smaller than 20 MB.'));return;}
  try {
   const bitmap=await createImageBitmap(file,{imageOrientation:'from-image'});
   const ratio=Math.min(1,1600/Math.max(bitmap.width,bitmap.height));
   const canvas=document.createElement('canvas');canvas.width=Math.max(1,Math.round(bitmap.width*ratio));canvas.height=Math.max(1,Math.round(bitmap.height*ratio));
   const context=canvas.getContext('2d');context.fillStyle='#ffffff';context.fillRect(0,0,canvas.width,canvas.height);context.drawImage(bitmap,0,0,canvas.width,canvas.height);bitmap.close();
   const data=canvas.toDataURL('image/jpeg',.85).split(',')[1];
   if(data.length>3500000)throw Error('Choose a smaller image.');
   finish(JSON.stringify({mimeType:'image/jpeg',base64:data}));
  }catch(error){input.remove();reject(Error('Could not open this image. Choose a JPEG, PNG or WebP photo.'));}
 };
 input.style.display='none';document.body.appendChild(input);input.click();
})""")
private external fun chooseImage():Promise<JsString>
internal actual suspend fun pickSketchImage():SketchImage? {
    val value=chooseImage().await<JsString>().toString()
    return if(value=="null")null else authJson.decodeFromString<SketchImage>(value)
}
