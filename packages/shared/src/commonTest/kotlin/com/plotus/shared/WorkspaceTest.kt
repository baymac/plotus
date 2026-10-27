package com.plotus.shared

import kotlin.test.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

class WorkspaceTest {
    @Test fun invalidClientCannotBeSaved() {
        val draft = ProjectDraft("Courtyard", "Bengaluru", true, "Test", "invalid-email")
        assertTrue("email" in draft.errors())
        assertFailsWith<IllegalArgumentException> { draft.toProject("new") }
    }

    @Test fun editingPreservesIdentityAndGeometryWhileRemovingDisabledClient() {
        val existing = sampleProjects().first().copy(client = Client("Client", "test@example.com"))
        val saved = ProjectDraft.from(existing).copy(name = "  Courtyard  ", isClient = false).toProject(existing.id, existing)
        val projects = saveProject(sampleProjects(), saved)
        assertEquals(3, projects.size)
        assertEquals("Courtyard", projects.first().name)
        assertEquals(1500, projects.first().areaSqFt)
        assertNull(projects.first().client)
    }

    @Test fun searchAndSortUseBothProjectNameAndLocation() {
        assertEquals("preview-bengaluru", visibleProjects(sampleProjects(), "BENGALURU", ProjectSort.RECENT).single().id)
        assertTrue(visibleProjects(sampleProjects(), "missing", ProjectSort.RECENT).isEmpty())
        assertEquals("Alwar Bypass Farmstead", visibleProjects(sampleProjects(), "", ProjectSort.NAME).first().name)
    }

    @Test fun wireModelRoundTripsWithoutPlatformSpecificTypes() {
        val project = sampleProjects().first()
        assertEquals(project, Json.decodeFromString<Project>(Json.encodeToString(project)))
        assertEquals("10,890 sq ft", areaLabel(10890))
    }
}
