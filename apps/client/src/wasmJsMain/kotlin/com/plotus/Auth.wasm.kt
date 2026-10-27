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

@JsFun("""async () => {
 const encode=value=>btoa(String.fromCharCode(...new Uint8Array(value))).replace(/\+/g,'-').replace(/\//g,'_').replace(/=+$/,'');
 const verifier=encode(crypto.getRandomValues(new Uint8Array(32)));
 const challenge=encode(await crypto.subtle.digest('SHA-256',new TextEncoder().encode(verifier)));
 return JSON.stringify({verifier,challenge});
}""")
private external fun browserProof(): Promise<JsString>

@JsFun("""(url,pending) => {
 const target=new URL(url);
 if(target.protocol!=='https:' || target.username || target.password || target.pathname!=='/api/auth/login/facebook/authorize') throw new Error('Invalid Facebook sign-in address');
 sessionStorage.setItem('plotus.facebook.pending',JSON.stringify({...JSON.parse(pending),createdAt:Date.now()}));
 location.assign(target.href);
}""")
private external fun facebookNavigate(url: String,pending: String)

@JsFun("""() => {
 const url=new URL(location.href);const result=new URLSearchParams(url.hash.slice(1));
 if(!result.has('auth_code') && !result.has('auth_error')) return null;
 const raw=sessionStorage.getItem('plotus.facebook.pending');sessionStorage.removeItem('plotus.facebook.pending');
 const state=result.get('auth_state');const code=result.get('auth_code');const declined=result.has('auth_error');
 for(const name of ['auth_code','auth_state','auth_error'])result.delete(name);
 url.hash=result.toString();history.replaceState(null,'',url);
 const fail=()=>JSON.stringify({error:'Facebook sign-in expired or could not be verified. Please try again.'});
 try {
  const saved=JSON.parse(raw);
  if(!saved || saved.state!==state || !/^[A-Za-z0-9_-]{43}$/.test(saved.verifier) || !Number.isFinite(saved.createdAt) || Date.now()-saved.createdAt>600000 || Date.now()<saved.createdAt) return fail();
  if(declined)return JSON.stringify({error:'Facebook sign-in was cancelled or declined. Please try again.'});
  if(!/^[A-Za-z0-9_-]{43}$/.test(code || ''))return fail();
  return JSON.stringify({code,verifier:saved.verifier});
 }catch(_){return fail();}
}""")
private external fun facebookReturn(): String?

internal actual fun authIsBrowser() = true
internal actual suspend fun authTransport(method: String,path: String,body: String?,bearer: String?,csrf: String?) =
    authJson.decodeFromString<AuthWireResponse>(browserRequest(method,path,body,bearer,csrf).await<JsString>().toString())
internal actual suspend fun authCreateProof(): AuthProof = authJson.decodeFromString<AuthProof>(browserProof().await<JsString>().toString())
internal actual suspend fun authGoogleIdentity(nonce: String): String = error("Browser Google sign-in uses a redirect")
internal actual suspend fun authFacebookBrowser(url: String): String = error("Browser Facebook sign-in uses a redirect")
internal actual fun authFacebookNavigate(url: String,pending: String) = facebookNavigate(url,pending)
internal actual fun authFacebookReturn() = facebookReturn()
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
