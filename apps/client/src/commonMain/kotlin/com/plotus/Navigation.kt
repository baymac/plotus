package com.plotus

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.Serializable

enum class Destination(val path: String) {
    PROJECTS("/projects"), SETTINGS("/settings"), GENERAL("/settings/general"), ACCOUNT("/settings/account"), LOGIN("/login");
}

@Serializable
internal data class AppLocation(val path: String, val next: String? = null)

internal expect fun readAppLocation(): AppLocation?
internal expect fun observeAppLocation(onChange: () -> Unit): () -> Unit
internal expect fun writeAppLocation(path: String, replace: Boolean)

/** Only known app destinations can be used as sign-in return addresses. */
internal fun destinationForPath(path: String?): Destination? = Destination.entries.firstOrNull { it.path == path?.trimEnd('/') }

internal class AppNavigation(initial: AppLocation?, private val updateLocation: (String, Boolean) -> Unit = ::writeAppLocation) {
    val browser = initial != null
    var destination by mutableStateOf(destinationForPath(initial?.path) ?: Destination.PROJECTS); private set
    private var returnTo = returnDestination(initial?.next)

    val loginCallbackPath: String get() = "/login?next=${returnTo.path}"

    fun receive(location: AppLocation?) {
        if (location == null) return
        destination = destinationForPath(location.path) ?: Destination.PROJECTS
        if (destination == Destination.LOGIN) returnTo = returnDestination(location.next)
    }

    fun navigate(target: Destination, replace: Boolean = false) {
        if (destination == target && !replace) return
        destination = target
        if (browser) updateLocation(target.path, replace)
    }

    fun reconcileSession(signedIn: Boolean, initializing: Boolean) {
        if (!browser || initializing) return
        if (signedIn) {
            if (destination == Destination.LOGIN) navigate(returnTo, replace = true)
        } else if (destination != Destination.LOGIN) {
            returnTo = destination
            destination = Destination.LOGIN
            updateLocation(loginCallbackPath, true)
        }
    }

    private fun returnDestination(path: String?): Destination = destinationForPath(path)?.takeUnless { it == Destination.LOGIN }
        ?: Destination.PROJECTS
}
