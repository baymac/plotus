package com.plotus.shared

import kotlinx.serialization.Serializable

@Serializable
data class Client(val name: String, val email: String, val phone: String = "")

@Serializable
data class Project(
    val id: String,
    val name: String,
    val location: String,
    val areaSqFt: Int? = null,
    val lastEdited: String = "Just now",
    val client: Client? = null,
    val revision: Long = 0,
    val updatedAt: String = "",
)

@Serializable
data class Health(val status: String = "ok", val service: String = "plotus-api", val version: String = "0.1.0")

@Serializable
data class ErrorDetail(val code: String, val message: String, val fields: Map<String, String> = emptyMap())

@Serializable
data class ApiError(val error: ErrorDetail)

@Serializable
data class ProjectDraft(
    val name: String = "",
    val location: String = "",
    val isClient: Boolean = false,
    val clientName: String = "",
    val email: String = "",
    val phone: String = "",
) {
    fun errors(): Map<String, String> = buildMap {
        if (name.isBlank() || name.length > 120) put("name", "Enter a project name (up to 120 characters).")
        if (location.isBlank() || location.length > 200) put("location", "Enter a location (up to 200 characters).")
        if (isClient) {
            if (clientName.isBlank() || clientName.length > 120) put("clientName", "Enter the client’s name (up to 120 characters).")
            if (phone.length > 40) put("phone", "Enter a phone number up to 40 characters.")
            if (email.length > 254 || !Regex("""^[^\s@]+@[^\s@]+\.[^\s@]+$""").matches(email.trim())) put("email", "Enter a valid email address.")
        }
    }

    fun toProject(id: String, existing: Project? = null): Project {
        require(errors().isEmpty()) { "Project details are invalid." }
        return Project(id, name.trim(), location.trim(), existing?.areaSqFt, "Just now",
            if (isClient) Client(clientName.trim(), email.trim(), phone.trim()) else null,
            existing?.revision ?: 0, existing?.updatedAt.orEmpty())
    }

    companion object {
        fun from(project: Project) = ProjectDraft(project.name, project.location, project.client != null,
            project.client?.name.orEmpty(), project.client?.email.orEmpty(), project.client?.phone.orEmpty())
    }
}

enum class ProjectSort { RECENT, NAME }

@Serializable data class ProjectList(val projects: List<Project>)
@Serializable data class SaveProjectRequest(val details: ProjectDraft, val revision: Long? = null)
@Serializable data class DeleteProjectRequest(val revision: Long)

// Local design fixtures only. Never exposed as authenticated API data.
fun sampleProjects() = listOf(
    Project("preview-jaipur", "Jaipur Corner Plot", "C-Scheme, Jaipur", 1500, "2 hr ago"),
    Project("preview-bengaluru", "Koramangala Plot #402", "4th Block, Bengaluru", 3400, "Yesterday"),
    Project("preview-alwar", "Alwar Bypass Farmstead", "Bhiwadi Corridor", 10890, "3 days ago"),
)

fun visibleProjects(projects: List<Project>, query: String, sort: ProjectSort): List<Project> {
    val matches = projects.filter { "${it.name} ${it.location}".contains(query.trim(), ignoreCase = true) }
    return if (sort == ProjectSort.NAME) matches.sortedBy { it.name.lowercase() } else matches
}

fun saveProject(projects: List<Project>, project: Project) = listOf(project) + projects.filterNot { it.id == project.id }

fun areaLabel(value: Int?): String {
    if (value == null) return "Not set"
    return value.toString().reversed().chunked(3).joinToString(",").reversed() + " sq ft"
}
