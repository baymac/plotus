package com.plotus

import kotlin.test.*

class NavigationTest {
    @Test fun protectedDeepLinkWaitsForSessionThenRequiresLogin() {
        val writes = mutableListOf<Pair<String, Boolean>>()
        val navigation = AppNavigation(AppLocation("/settings/account")) { path, replace -> writes += path to replace }
        navigation.reconcileSession(signedIn = false, initializing = true)
        assertEquals(Destination.ACCOUNT, navigation.destination)
        assertTrue(writes.isEmpty())
        navigation.reconcileSession(signedIn = false, initializing = false)
        assertEquals(Destination.LOGIN, navigation.destination)
        assertEquals("/login?next=/settings/account" to true, writes.last())
        navigation.reconcileSession(signedIn = true, initializing = false)
        assertEquals(Destination.ACCOUNT, navigation.destination)
        assertEquals("/settings/account" to true, writes.last())
    }

    @Test fun untrustedReturnAddressesCannotLeaveTheApp() {
        listOf("https://example.invalid", "//example.invalid", "/login", "/settings/../outside", "/projects?redirect=https://example.invalid").forEach { next ->
            val navigation = AppNavigation(AppLocation("/login", next)) { _, _ -> }
            navigation.reconcileSession(signedIn = true, initializing = false)
            assertEquals(Destination.PROJECTS, navigation.destination, next)
        }
    }

    @Test fun browserHistoryChangesDestinationAndRevokedSessionsReturnToLogin() {
        val writes = mutableListOf<Pair<String, Boolean>>()
        val navigation = AppNavigation(AppLocation("/projects")) { path, replace -> writes += path to replace }
        navigation.navigate(Destination.GENERAL)
        assertEquals("/settings/general" to false, writes.last())
        navigation.receive(AppLocation("/projects"))
        assertEquals(Destination.PROJECTS, navigation.destination)
        navigation.reconcileSession(signedIn = false, initializing = false)
        assertEquals(Destination.LOGIN, navigation.destination)
        assertEquals("/login?next=/projects" to true, writes.last())
    }

    @Test fun nativeNavigationDoesNotUseBrowserHistory() {
        val navigation = AppNavigation(null) { _, _ -> fail("Native navigation must not write a URL") }
        navigation.navigate(Destination.ACCOUNT)
        navigation.reconcileSession(signedIn = false, initializing = false)
        assertEquals(Destination.ACCOUNT, navigation.destination)
    }
}
