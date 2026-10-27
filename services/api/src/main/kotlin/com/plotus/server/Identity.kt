package com.plotus.server

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

class ApiFailure(val status: Int, val code: String, override val message: String,
    val fields: Map<String, String> = emptyMap()) : Exception(message)

fun interface IdentityVerifier { suspend fun user(token: String): UUID }

/** The auth service verifies the signature, audience, expiry and live session. */
class AuthServiceIdentity(baseURL: String) : IdentityVerifier {
    private val endpoint = URI(baseURL.trimEnd('/')+"/api/auth/user/me")
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
        .followRedirects(HttpClient.Redirect.NEVER).build()

    override suspend fun user(token: String): UUID = withContext(Dispatchers.IO) {
        if (token.isBlank() || token.length > 8192 || token.any { it.isWhitespace() })
            throw ApiFailure(401, "UNAUTHENTICATED", "Sign in to continue.")
        val response = try {
            val request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(5))
                .header("Authorization", "Bearer $token").header("Accept", "application/json").GET().build()
            client.send(request, HttpResponse.BodyHandlers.ofString())
        } catch (_: java.io.IOException) { throw ApiFailure(503, "AUTH_UNAVAILABLE", "Sign-in is temporarily unavailable. Please try again.") }
        when (response.statusCode()) {
            401, 403 -> throw ApiFailure(401, "UNAUTHENTICATED", "Your session expired. Please sign in again.")
            200 -> try { UUID.fromString(Json.parseToJsonElement(response.body()).jsonObject["id"]!!.jsonPrimitive.content) }
                catch (_: Exception) { throw ApiFailure(503, "AUTH_UNAVAILABLE", "Your account could not be verified.") }
            else -> throw ApiFailure(503, "AUTH_UNAVAILABLE", "Sign-in is temporarily unavailable. Please try again.")
        }
    }
}
