package com.plotus

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Only OS services cross this bridge. UI and session state remain shared Kotlin. */
interface NativeAuthHost {
    fun request(method: String, path: String, body: String?, bearer: String?, csrf: String?, completion: (String?, String?) -> Unit)
    fun createProof(): String
    fun googleIdentity(nonce: String, completion: (String?, String?) -> Unit)
    fun facebookBrowser(url: String, completion: (String?, String?) -> Unit)
    fun passkey(operation: String, options: String, completion: (String?, String?) -> Unit)
    fun forgetPasskey(rpID: String, credentialID: String, completion: (String?, String?) -> Unit)
    fun readSession(): String?
    fun writeSession(value: String?): Boolean
    fun signOutProvider()
}
object NativeAuthBridge { var host: NativeAuthHost? = null }
private fun host() = NativeAuthBridge.host ?: throw AuthFailure(503, "Sign-in isn’t configured on this device.")
private suspend fun result(block: ((String?, String?) -> Unit) -> Unit): String = suspendCancellableCoroutine { continuation ->
    block { value, error -> if (continuation.isActive) {
        when { error == "cancelled" -> continuation.resumeWithException(AuthCancelled())
            error != null -> continuation.resumeWithException(AuthFailure(0, error))
            value != null -> continuation.resume(value)
            else -> continuation.resumeWithException(AuthFailure(0, "Couldn’t complete sign-in.")) }
    } }
}
internal actual fun authIsBrowser() = false
internal actual suspend fun authTransport(method: String, path: String, body: String?, bearer: String?, csrf: String?) =
    authJson.decodeFromString<AuthWireResponse>(result { host().request(method,path,body,bearer,csrf,it) })
internal actual suspend fun authCreateProof() = authJson.decodeFromString<AuthProof>(host().createProof())
internal actual suspend fun authGoogleIdentity(nonce: String) = result { host().googleIdentity(nonce,it) }
internal actual suspend fun authFacebookBrowser(url: String) = result { host().facebookBrowser(url,it) }
internal actual fun authFacebookNavigate(url: String, pending: String) { error("Native Facebook sign-in uses the system browser") }
internal actual fun authFacebookReturn(): String? = null
internal actual suspend fun authPasskey(operation: String, options: String) = result { host().passkey(operation,options,it) }
internal actual suspend fun authForgetPasskey(rpID: String, credentialID: String) { result { host().forgetPasskey(rpID,credentialID,it) } }
internal actual fun authNavigateGoogle(url: String) { error("Native Google sign-in uses the OS adapter") }
internal actual fun authReadSession() = host().readSession()
internal actual fun authWriteSession(value: String?) { if (!host().writeSession(value)) throw AuthFailure(0, "Couldn’t save your session securely. Please try again.") }
internal actual fun authProviderSignOut() = host().signOutProvider()
internal actual fun authCallbackError(): String? = null
