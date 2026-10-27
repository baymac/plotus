package com.plotus

import androidx.compose.runtime.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

internal val authJson = Json { ignoreUnknownKeys = true }
@Serializable data class AuthUser(val id: String, val email: String, val name: String, val picture: String = "")
@Serializable data class SavedPasskey(val id: String, val label: String = "Passkey")
@Serializable internal data class AuthConfig(@SerialName("google_enabled") val google: Boolean = false,
    @SerialName("passkeys_enabled") val passkeys: Boolean = false, @SerialName("rp_id") val rpID: String = "")
@Serializable internal data class AuthTokens(
    @SerialName("access_token") val access: String = "", @SerialName("refresh_token") val refresh: String = "",
    @SerialName("csrf_token") val csrf: String = "", @SerialName("user_id") val userID: String = "",
)
@Serializable internal data class StoredSession(val refresh: String, val csrf: String)
@Serializable internal data class AuthWireResponse(val status: Int, val body: String)
@Serializable internal data class AuthProof(val verifier: String, val challenge: String)
internal class AuthFailure(val status: Int, override val message: String) : Exception(message)
internal class AuthCancelled : Exception()

internal expect fun authIsBrowser(): Boolean
internal expect suspend fun authTransport(method: String, path: String, body: String? = null, bearer: String? = null, csrf: String? = null): AuthWireResponse
internal expect suspend fun authCreateProof(): AuthProof
internal expect suspend fun authGoogleIdentity(nonce: String): String
internal expect suspend fun authFacebookBrowser(url: String): String
internal expect fun authFacebookNavigate(url: String, pending: String)
internal expect fun authFacebookReturn(): String?
internal expect suspend fun authPasskey(operation: String, options: String): String
internal expect fun authNavigateGoogle(url: String)
internal expect fun authReadSession(): String?
internal expect fun authWriteSession(value: String?)
internal expect fun authProviderSignOut()
internal expect fun authCallbackError(): String?

/** Shared session state. The demo never enters this authentication boundary. */
class AuthSession(private val scope: CoroutineScope) {
    var user by mutableStateOf<AuthUser?>(null); private set
    var passkeys by mutableStateOf<List<SavedPasskey>>(emptyList()); private set
    var initializing by mutableStateOf(true); private set
    var busy by mutableStateOf(false); private set
    var action by mutableStateOf(""); private set
    var error by mutableStateOf<String?>(null); private set
    var notice by mutableStateOf<String?>(null); private set
    private var access = ""
    private var csrf = ""
    private var nativeSession: StoredSession? = null
    private val lock = Mutex()

    fun dismissError() { error = null }
    fun dismissNotice() { notice = null }

    suspend fun restore() = lock.withLock {
        var finishingFacebook = false
        try {
            if (authIsBrowser()) {
                val returned = authFacebookReturn()
                if (returned != null) {
                    finishingFacebook = true
                    val result = authJson.parseToJsonElement(returned).jsonObject
                    result["error"]?.jsonPrimitive?.contentOrNull?.let { throw AuthFailure(400, it) }
                    adopt(tokens(request("POST", "/api/auth/login/facebook/exchange", jsonObject(
                        "code" to result["code"]!!.jsonPrimitive.content, "code_verifier" to result["verifier"]!!.jsonPrimitive.content))))
                    return@withLock
                }
                val response = authTransport("GET", "/api/auth/token/csrf")
                if (response.status == 401 || response.status == 403) return@withLock
                csrf = checked(response).jsonObject["csrf_token"]!!.jsonPrimitive.content
                adopt(tokens(request("GET", "/api/auth/token/access", csrf = csrf)))
            } else {
                val saved = authReadSession() ?: return@withLock
                nativeSession = try { authJson.decodeFromString<StoredSession>(saved) } catch (_: Exception) {
                    authWriteSession(null); return@withLock
                }
                renew()
                loadUser()
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: AuthFailure) {
            if (failure.status == 401) clearLocal()
            if (finishingFacebook) error = failure.message
        }
        catch (_: Exception) { if (finishingFacebook) error = "Couldn’t finish Facebook sign-in. Please try again." }
        finally { initializing = false; authCallbackError()?.let { error = it } }
    }

    fun google() = perform("google") {
        if (authIsBrowser()) {
            val result = request("POST", "/api/auth/login/google", jsonObject("redirect" to "/"))
            authNavigateGoogle(result.jsonObject["authUrl"]!!.jsonPrimitive.content)
        } else {
            val proof = authCreateProof()
            val pending = request("POST", "/api/auth/native/google/challenge", jsonObject("code_challenge" to proof.challenge, "code_challenge_method" to "S256"))
            val identity = authGoogleIdentity(pending.jsonObject["nonce"]!!.jsonPrimitive.content)
            val result = request("POST", "/api/auth/native/google/token", jsonObject(
                "attempt" to pending.jsonObject["attempt"]!!.jsonPrimitive.content,
                "id_token" to identity, "code_verifier" to proof.verifier))
            adopt(tokens(result))
        }
    }

    fun passkey() = perform("passkey") {
        val prefix = if (authIsBrowser()) "/api/auth" else "/api/auth/native"
        val options = request("POST", "$prefix/passkey/login/begin", "{}")
        val assertion = authPasskey("login", options.toString())
        adopt(tokens(request("POST", "$prefix/passkey/login/finish", assertion)))
    }

    fun facebook() = perform("facebook") {
        val proof = authCreateProof()
        val state = authCreateProof().verifier
        val redirect = "plotus://oauth/callback"
        val browser = authIsBrowser()
        val result = request("POST", if (browser) "/api/auth/login/facebook" else "/api/auth/native/facebook",
            if (browser) jsonObject("redirect" to "/", "state" to state, "code_challenge" to proof.challenge, "code_challenge_method" to "S256")
            else jsonObject("redirect_uri" to redirect, "state" to state, "code_challenge" to proof.challenge, "code_challenge_method" to "S256"))
        val target = result.jsonObject["authUrl"]!!.jsonPrimitive.content
        if (browser) {
            authFacebookNavigate(target, jsonObject("state" to state, "verifier" to proof.verifier))
        } else {
            val callback = authJson.parseToJsonElement(authFacebookBrowser(target)).jsonObject
            if (callback["state"]?.jsonPrimitive?.content != state) throw AuthFailure(400, "Facebook sign-in could not be verified. Please try again.")
            if (callback["error"] != null) throw AuthFailure(400, "Facebook sign-in was cancelled or declined. Please try again.")
            val code = callback["code"]?.jsonPrimitive?.content ?: throw AuthFailure(400, "Facebook sign-in could not be completed.")
            adopt(tokens(request("POST", "/api/auth/native/token/exchange", jsonObject("code" to code, "code_verifier" to proof.verifier, "redirect_uri" to redirect))))
        }
    }

    fun addPasskey() = perform("register") {
        val options = authenticated("POST", "/api/auth/passkey/register/begin", "{}")
        val credential = authPasskey("register", options.toString())
        authenticated("POST", "/api/auth/passkey/register/finish", credential)
        loadPasskeys()
        notice = "Passkey added. You can use it next time you sign in."
    }

    fun removePasskey(id: String) = perform("remove") {
        require(Regex("^[A-Za-z0-9_-]+$").matches(id))
        authenticated("DELETE", "/api/auth/passkey/credentials/$id")
        loadPasskeys()
    }

    fun signOut() = perform("logout") {
        if (authIsBrowser()) request("DELETE", "/api/auth/login/user/logout", csrf = csrf)
        else nativeSession?.let { request("DELETE", "/api/auth/native/session", nativeBody(it)) }
        clearLocal()
        authProviderSignOut()
    }

    suspend fun refreshIfSignedIn() {
        if (busy || user == null) return
        lock.withLock {
            if (user == null) return@withLock
            try { renew() } catch (failure: AuthFailure) {
                if (failure.status == 401) { clearLocal(); error = "Your session expired. Please sign in again." }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* The next authenticated action can retry after a network failure. */ }
        }
    }

    private fun perform(label: String, operation: suspend () -> Unit) {
        if (busy || initializing) return
        busy = true; action = label; error = null; notice = null
        scope.launch {
            try { lock.withLock { operation() } }
            catch (_: AuthCancelled) { }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: AuthFailure) {
                if (failure.status == 401 && user != null) clearLocal()
                error = failure.message
            }
            catch (_: Exception) { error = "Couldn’t connect to Plotus. Please try again." }
            finally { busy = false; action = "" }
        }
    }

    private suspend fun adopt(value: AuthTokens) {
        if (value.access.isBlank()) throw AuthFailure(502, "Sign-in could not be completed. Please try again.")
        access = value.access
        if (value.csrf.isNotEmpty()) csrf = value.csrf
        if (!authIsBrowser()) {
            if (value.refresh.length != 43 || value.csrf.isBlank()) throw AuthFailure(502, "Sign-in could not be completed.")
            val stored = StoredSession(value.refresh, value.csrf)
            try { authWriteSession(authJson.encodeToString(stored)) }
            catch (failure: Exception) {
                try { request("DELETE", "/api/auth/native/session", nativeBody(stored)) } catch (_: Exception) { }
                access = ""; throw failure
            }
            nativeSession = stored
        }
        loadUser()
    }

    private suspend fun renew() {
        val response = if (authIsBrowser()) request("POST", "/api/auth/token/refresh", csrf = csrf)
        else request("POST", "/api/auth/native/token/refresh", nativeBody(nativeSession ?: throw AuthFailure(401, "Please sign in again.")))
        val value = tokens(response)
        if (value.access.isBlank()) throw AuthFailure(502, "Session refresh failed.")
        if (!authIsBrowser()) {
            val stored = StoredSession(value.refresh, value.csrf)
            if (stored.refresh.length != 43 || stored.csrf.isEmpty()) throw AuthFailure(502, "Session refresh failed.")
            authWriteSession(authJson.encodeToString(stored)); nativeSession = stored
        }
        access = value.access
        if (value.csrf.isNotEmpty()) csrf = value.csrf
    }

    private suspend fun loadUser() {
        val profile = authJson.decodeFromJsonElement<AuthUser>(request("GET", "/api/auth/user/me", bearer = access))
        if (profile.id.isBlank()) throw AuthFailure(502, "Account could not be verified.")
        user = profile
        try { loadPasskeys() } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { passkeys = emptyList() }
    }
    private suspend fun loadPasskeys() {
        val result = authenticated("GET", "/api/auth/passkey/credentials")
        passkeys = authJson.decodeFromJsonElement(result.jsonObject["credentials"] ?: JsonArray(emptyList()))
    }
    private suspend fun authenticated(method: String, path: String, body: String? = null): JsonElement {
        var response = authTransport(method, path, body, access)
        if (response.status == 401 && user != null) { renew(); response = authTransport(method, path, body, access) }
        return checked(response)
    }
    private fun clearLocal() {
        authWriteSession(null); access = ""; csrf = ""; nativeSession = null; user = null; passkeys = emptyList()
    }
    private suspend fun request(method: String, path: String, body: String? = null, bearer: String? = null, csrf: String? = null) =
        checked(authTransport(method, path, body, bearer, csrf))
    private fun tokens(value: JsonElement) = authJson.decodeFromJsonElement<AuthTokens>(value)
    private fun nativeBody(value: StoredSession) = jsonObject("refresh_token" to value.refresh, "csrf_token" to value.csrf)
}

internal fun jsonObject(vararg values: Pair<String, String>): String = buildJsonObject { values.forEach { (key,value) -> put(key,value) } }.toString()
internal fun checked(response: AuthWireResponse): JsonElement {
    val body = try { authJson.parseToJsonElement(response.body.ifBlank { "{}" }) } catch (_: Exception) { JsonObject(emptyMap()) }
    if (response.status !in 200..299) {
        val message = (body as? JsonObject)?.get("error")?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
        throw AuthFailure(response.status, message ?: if (response.status == 401) "Please sign in again." else "Couldn’t complete this request. Please try again.")
    }
    return body
}
