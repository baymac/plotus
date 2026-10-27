package com.plotus

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

internal actual fun authIsBrowser() = false
internal actual suspend fun authTransport(method: String, path: String, body: String?, bearer: String?, csrf: String?): AuthWireResponse = withContext(Dispatchers.IO) {
    val request = HttpRequest.newBuilder(URI("http://localhost:4001$path")).header("Content-Type","application/json")
    bearer?.let { request.header("Authorization","Bearer $it") }; csrf?.let { request.header("X-CSRF-Token",it) }
    val response = HttpClient.newHttpClient().send(request.method(method,body?.let(HttpRequest.BodyPublishers::ofString) ?: HttpRequest.BodyPublishers.noBody()).build(),HttpResponse.BodyHandlers.ofString())
    AuthWireResponse(response.statusCode(),response.body())
}
internal actual suspend fun authCreateProof(): AuthProof {
    val encoder = Base64.getUrlEncoder().withoutPadding()
    val verifier = encoder.encodeToString(ByteArray(32).also { SecureRandom().nextBytes(it) })
    return AuthProof(verifier,encoder.encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray())))
}
internal actual suspend fun authGoogleIdentity(nonce: String): String = throw AuthFailure(0,"Use the Plotus web app to sign in on desktop.")
internal actual suspend fun authPasskey(operation: String, options: String): String = throw AuthFailure(0,"Use the Plotus web app for passkeys on desktop.")
internal actual fun authNavigateGoogle(url: String) { java.awt.Desktop.getDesktop().browse(URI(url)) }
internal actual fun authReadSession(): String? = null
internal actual fun authWriteSession(value: String?) { }
internal actual fun authProviderSignOut() { }
internal actual fun authCallbackError(): String? = null
