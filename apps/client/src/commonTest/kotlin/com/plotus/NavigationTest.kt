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

class PlotNavigationTest {
    private val id = "c4f872f5-7c67-4f4e-aef9-b5e67f01ca2a"
    @Test fun historyDrawingDeepLinkSurvivesSignInAndBackReturnsToItsList() {
        val plan="e4fc581d-5337-466f-83d2-51e96c2a03f1"
        val route="/projects/$id/floor-plan/history/$plan"
        val navigation=AppNavigation(AppLocation(route)) {_,_->}
        navigation.reconcileSession(false,false);navigation.reconcileSession(true,false)
        assertEquals(route,navigation.path);assertEquals(plan,navigation.generationID)
        assertEquals(com.plotus.shared.FloorStep.HISTORY,navigation.floorStep)
        navigation.openFloor(id,com.plotus.shared.FloorStep.HISTORY)
        assertNull(navigation.generationID);assertEquals("/projects/$id/floor-plan/history",navigation.path)
        navigation.openFloor(id)
        assertNull(navigation.floorStep);assertNull(navigation.generationID)
        assertNull(destinationForPath("/projects/$id/floor-plan/setbacks/$plan"))
        assertNull(destinationForPath("/projects/$id/floor-plan/history/../../settings"))
    }
    @Test fun floorOverviewAndInlineControlsKeepTheirOwnLocations() {
        val navigation=AppNavigation(AppLocation("/projects")) {_,_->}
        navigation.openFloor(id)
        assertNull(navigation.floorStep)
        assertEquals("/projects/$id/floor-plan",navigation.path)
        navigation.openFloor(id,com.plotus.shared.FloorStep.SETBACKS,replace=true)
        assertEquals("/projects/$id/floor-plan/setbacks",navigation.path)
        navigation.openFloor(id,replace=true)
        navigation.reconcileSession(false,false);navigation.reconcileSession(true,false)
        assertNull(navigation.floorStep)
        assertEquals("/projects/$id/floor-plan",navigation.path)
    }
    @Test fun floorPlanDeepLinksReturnToTheSameSectionAfterSignIn() {
        val route="/projects/$id/floor-plan/requirements"
        val navigation=AppNavigation(AppLocation(route)) {_,_->}
        assertEquals(Destination.FLOOR_PLAN,navigation.destination)
        navigation.reconcileSession(false,false)
        assertEquals("/login?next=$route",navigation.loginCallbackPath)
        navigation.reconcileSession(true,false)
        assertEquals(com.plotus.shared.FloorStep.REQUIREMENTS,navigation.floorStep)
        assertEquals(route,navigation.path)
        assertNull(destinationForPath("/projects/$id/floor-plan/../../outside"))
    }
    @Test fun projectDeepLinksReturnToTheSameStepAfterSignIn() {
        val writes = mutableListOf<String>()
        val route = "/projects/$id/plot/roads"
        val navigation = AppNavigation(AppLocation(route)) { path, _ -> writes += path }
        navigation.reconcileSession(false,false)
        assertEquals("/login?next=$route", writes.last())
        navigation.reconcileSession(true,false)
        assertEquals(Destination.PLOT,navigation.destination)
        assertEquals(id,navigation.projectID)
        assertEquals(com.plotus.shared.PlotStep.ROADS,navigation.plotStep)
        assertEquals(route,writes.last())
        navigation.receive(AppLocation("/projects/$id/plot/direction"))
        assertEquals(com.plotus.shared.PlotStep.DIRECTION,navigation.plotStep)
    }
    @Test fun projectOverviewAndSheetsHaveDistinctProtectedLocations() {
        val writes=mutableListOf<String>()
        val navigation=AppNavigation(AppLocation("/projects")) {path,_->writes+=path}
        navigation.openProject(id)
        assertNull(navigation.plotStep);assertEquals("/projects/$id/plot",navigation.path)
        navigation.openProject(id,com.plotus.shared.PlotStep.BOUNDARY,replace=true)
        assertEquals("/projects/$id/plot/boundary",navigation.path)
        navigation.openProject(id,replace=true)
        navigation.reconcileSession(false,false)
        navigation.reconcileSession(true,false)
        assertEquals("/projects/$id/plot",navigation.path);assertNull(navigation.plotStep)
    }
    @Test fun invalidProjectReturnPathsStayInsideKnownRoutes() {
        for (path in listOf("/projects/other/plot/roads","/projects/$id/plot/../account","//example.invalid/projects/$id/plot/roads")) {
            val navigation=AppNavigation(AppLocation("/login",path)) {_,_->}
            navigation.reconcileSession(true,false)
            assertEquals(Destination.PROJECTS,navigation.destination)
        }
        val native=AppNavigation(null) {_,_->fail("Native routes do not update browser history")}
        native.openProject("preview-jaipur",com.plotus.shared.PlotStep.DIRECTION)
        assertEquals("preview-jaipur",native.projectID)
    }
}
