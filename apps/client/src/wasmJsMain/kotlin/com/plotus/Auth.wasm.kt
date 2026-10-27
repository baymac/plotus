@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.plotus

import kotlinx.coroutines.await
import kotlinx.serialization.json.*
import kotlin.JsFun
import kotlin.js.JsString
import kotlin.js.Promise

@JsFun("""(method,path,body,bearer,csrf) => {
 const configured=document.querySelector('meta[name="plotus-auth-url"]')?.content;
 const base=configured || (['localhost','127.0.0.1','[::1]'].includes(location.hostname) ? 'http://localhost:4001' : location.origin);
 const url=new URL(path,base);
 if(url.protocol!=='https:' && !['localhost','127.0.0.1','[::1]'].includes(url.hostname)) return Promise.reject(new Error('HTTPS required'));
 const headers={'Accept':'application/json','Content-Type':'application/json'};
 if(bearer)headers.Authorization='Bearer '+bearer;
 if(csrf)headers['X-CSRF-Token']=csrf;
 const controller=new AbortController();const timer=setTimeout(()=>controller.abort(),25000);
 return fetch(url,{method,credentials:'include',headers,body:body||undefined,signal:controller.signal})
  .then(async response=>JSON.stringify({status:response.status,body:await response.text()})).finally(()=>clearTimeout(timer));
}""")
private external fun browserRequest(method: String,path: String,body: String?,bearer: String?,csrf: String?): Promise<JsString>

@JsFun("""(operation,options) => {
 const fail=message=>JSON.stringify({_platformError:message});
 if(!window.PublicKeyCredential || !navigator.credentials || !window.isSecureContext) return Promise.resolve(fail('Passkeys are not available in this browser. Please use Google.'));
 const decode=value=>{const text=value.replace(/-/g,'+').replace(/_/g,'/');return Uint8Array.from(atob(text+'='.repeat((4-text.length%4)%4)),c=>c.charCodeAt(0));};
 const encode=value=>btoa(String.fromCharCode(...new Uint8Array(value))).replace(/\+/g,'-').replace(/\//g,'_').replace(/=+$/,'');
 const publicKey=JSON.parse(options).publicKey;
 publicKey.challenge=decode(publicKey.challenge);
 if(publicKey.user)publicKey.user.id=decode(publicKey.user.id);
 for(const key of ['allowCredentials','excludeCredentials'])if(publicKey[key])publicKey[key]=publicKey[key].map(c=>({...c,id:decode(c.id)}));
 const promise=operation==='register' ? navigator.credentials.create({publicKey}) : navigator.credentials.get({publicKey});
 return promise.then(credential=>{
  if(!credential)return fail('cancelled');
  const response={clientDataJSON:encode(credential.response.clientDataJSON)};
  if(operation==='register'){
   response.attestationObject=encode(credential.response.attestationObject);
   if(credential.response.getTransports)response.transports=credential.response.getTransports();
  }else{
   response.authenticatorData=encode(credential.response.authenticatorData);
   response.signature=encode(credential.response.signature);
   response.userHandle=credential.response.userHandle ? encode(credential.response.userHandle) : null;
  }
  return JSON.stringify({id:credential.id,rawId:encode(credential.rawId),type:'public-key',response,
   authenticatorAttachment:credential.authenticatorAttachment,clientExtensionResults:credential.getClientExtensionResults()});
 }).catch(error=>fail(['NotAllowedError','AbortError'].includes(error.name) ? 'cancelled' :
  error.name==='InvalidStateError' ? 'This account already has a passkey on this device.' :
  'Couldn’t use this passkey. Please try Google sign-in.'));
}""")
private external fun browserPasskey(operation: String,options: String): Promise<JsString>

@JsFun("""url => {const target=new URL(url);if(target.protocol!=='https:' || target.hostname!=='accounts.google.com')throw new Error('Invalid Google sign-in address');location.assign(target.href);}""")
private external fun navigate(url: String)
@JsFun("""() => {const url=new URL(location.href);if(!url.searchParams.has('auth_error'))return null;url.searchParams.delete('auth_error');history.replaceState(null,'',url);return 'Google sign-in was cancelled or expired. Please try again.';}""")
private external fun callbackError(): String?

internal actual fun authIsBrowser() = true
internal actual suspend fun authTransport(method: String,path: String,body: String?,bearer: String?,csrf: String?) =
    authJson.decodeFromString<AuthWireResponse>(browserRequest(method,path,body,bearer,csrf).await<JsString>().toString())
internal actual suspend fun authCreateProof(): AuthProof = error("Browser OAuth proof is held by the auth service")
internal actual suspend fun authGoogleIdentity(nonce: String): String = error("Browser Google sign-in uses a redirect")
internal actual suspend fun authPasskey(operation: String,options: String): String {
    val value=browserPasskey(operation,options).await<JsString>().toString()
    val error=authJson.parseToJsonElement(value).jsonObject["_platformError"]?.jsonPrimitive?.content
    if(error=="cancelled")throw AuthCancelled()
    if(error!=null)throw AuthFailure(0,error)
    return value
}
internal actual fun authNavigateGoogle(url: String) = navigate(url)
internal actual fun authReadSession(): String? = null
internal actual fun authWriteSession(value: String?) { /* Browser refresh credentials remain HttpOnly cookies. */ }
internal actual fun authProviderSignOut() { }
internal actual fun authCallbackError() = callbackError()
