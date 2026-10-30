package com.plotus.server

import com.plotus.shared.*
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.time.Duration
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.postgresql.ds.PGSimpleDataSource
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

interface ProjectStore {
    suspend fun saveSection(owner:UUID,id:UUID,section:PlanningSection):ProjectSection = throw ApiFailure(503,"STORAGE_UNAVAILABLE","Project navigation is temporarily unavailable.")
    suspend fun preferences(owner:UUID):AccountPreferences = throw ApiFailure(503,"STORAGE_UNAVAILABLE","Preferences are temporarily unavailable.")
    suspend fun savePreferences(owner:UUID,preferences:AccountPreferences):AccountPreferences = throw ApiFailure(503,"STORAGE_UNAVAILABLE","Preferences are temporarily unavailable.")
    suspend fun healthy(): Boolean
    suspend fun list(owner: UUID): List<Project>
    suspend fun get(owner: UUID, id: UUID): Project
    suspend fun create(owner: UUID, draft: ProjectDraft): Project
    suspend fun update(owner: UUID, id: UUID, revision: Long, draft: ProjectDraft): Project
    suspend fun delete(owner: UUID, id: UUID, revision: Long)
    suspend fun saveFloorPlan(owner: UUID, id: UUID, revision: Long, setup: FloorPlanSetup): Project
    suspend fun savePlot(owner: UUID, id: UUID, revision: Long, setup: PlotSetup): Project
}

class PostgresProjects(internal val source: DataSource) : ProjectStore {
    companion object {
        fun configured(): PostgresProjects {
            val url = System.getenv("PLOTUS_DATABASE_URL") ?: error("PLOTUS_DATABASE_URL is required")
            val source = PGSimpleDataSource().apply {
                setURL(url); user = System.getenv("PLOTUS_DATABASE_USER") ?: "plotus"
                password = System.getenv("PLOTUS_DATABASE_PASSWORD") ?: error("PLOTUS_DATABASE_PASSWORD is required")
                loginTimeout = 5; socketTimeout = 10
            }
            return PostgresProjects(source).also { it.migrate() }
        }
    }
    fun migrate() {
        val sql = listOf("001_projects.sql", "002_plot_setup.sql", "003_floor_plan.sql", "004_buildable_areas.sql", "005_account_preferences.sql", "006_project_last_section.sql").joinToString("\n") { file ->
            javaClass.getResourceAsStream("/migrations/$file")!!.bufferedReader().use { it.readText() }
        }
        source.connection.use { connection -> connection.createStatement().use { it.execute(sql) } }
    }
    override suspend fun preferences(owner:UUID):AccountPreferences=database {c->
        c.prepareStatement("SELECT units FROM plotus.account_preferences WHERE owner_id=?").use {
            it.setObject(1,owner);it.executeQuery().use {r->if(r.next())AccountPreferences(PlotUnits.valueOf(r.getString("units"))) else AccountPreferences()}
        }
    }
    override suspend fun savePreferences(owner:UUID,preferences:AccountPreferences):AccountPreferences=database {c->
        c.prepareStatement("INSERT INTO plotus.account_preferences(owner_id,units) VALUES (?,?) ON CONFLICT(owner_id) DO UPDATE SET units=EXCLUDED.units,updated_at=clock_timestamp()").use {
            it.setObject(1,owner);it.setString(2,preferences.units.name);it.executeUpdate()
        }
        preferences
    }
    override suspend fun saveSection(owner:UUID,id:UUID,section:PlanningSection):ProjectSection=database {c->
        // Opening a section is navigation, not a project edit or a new input revision.
        c.prepareStatement("UPDATE plotus.projects SET last_opened_section=? WHERE owner_id=? AND id=? RETURNING last_opened_section").use {
            it.setString(1,section.name);it.setObject(2,owner);it.setObject(3,id)
            it.executeQuery().use {r->if(r.next())ProjectSection(PlanningSection.valueOf(r.getString("last_opened_section"))) else throw missingProject()}
        }
    }
    override suspend fun healthy(): Boolean = withContext(Dispatchers.IO) {
        source.connection.use { connection -> connection.createStatement().use { it.executeQuery("SELECT 1 FROM plotus.projects LIMIT 1").use { true } } }
    }
    override suspend fun list(owner: UUID): List<Project> = database { connection ->
        connection.prepareStatement("SELECT * FROM plotus.projects WHERE owner_id=? ORDER BY updated_at DESC,id").use {
            it.setObject(1, owner)
            it.executeQuery().use { rows -> buildList { while (rows.next()) add(rows.project()) } }
        }
    }
    override suspend fun get(owner: UUID, id: UUID): Project = database { connection ->
        connection.prepareStatement("SELECT * FROM plotus.projects WHERE owner_id=? AND id=?").use {
            it.setObject(1, owner); it.setObject(2, id)
            it.executeQuery().use { rows -> if (rows.next()) rows.project() else throw missingProject() }
        }
    }
    override suspend fun create(owner: UUID, draft: ProjectDraft): Project = database { connection ->
        connection.prepareStatement("""INSERT INTO plotus.projects(id,owner_id,name,location,client_name,client_email,client_phone)
            VALUES (?,?,?,?,?,?,?) RETURNING *""").use {
            it.setObject(1, UUID.randomUUID()); it.setObject(2, owner); it.details(draft,3)
            it.executeQuery().use { rows -> rows.next(); rows.project() }
        }
    }
    override suspend fun update(owner: UUID, id: UUID, revision: Long, draft: ProjectDraft): Project = database { connection ->
        connection.prepareStatement("""UPDATE plotus.projects SET name=?,location=?,client_name=?,client_email=?,client_phone=?,
            revision=revision+1,updated_at=clock_timestamp() WHERE owner_id=? AND id=? AND revision=? RETURNING *""").use {
            it.details(draft,1); it.setObject(6, owner); it.setObject(7, id); it.setLong(8, revision)
            it.executeQuery().use { rows -> if (rows.next()) rows.project() else changedOrMissing(connection,owner,id) }
        }
    }
    override suspend fun delete(owner: UUID, id: UUID, revision: Long) { database { connection ->
        connection.prepareStatement("DELETE FROM plotus.projects WHERE owner_id=? AND id=? AND revision=?").use {
            it.setObject(1, owner); it.setObject(2, id); it.setLong(3, revision)
            if (it.executeUpdate()!=1) changedOrMissing(connection,owner,id)
        }
    } }
    override suspend fun savePlot(owner: UUID, id: UUID, revision: Long, setup: PlotSetup): Project = database { connection ->
        connection.prepareStatement("""UPDATE plotus.projects SET plot_setup=?::jsonb,area_sq_ft=?,
            revision=revision+1,updated_at=clock_timestamp() WHERE owner_id=? AND id=? AND revision=? RETURNING *""").use {
            it.setString(1, Json.encodeToString(setup)); it.setObject(2, setup.areaSquareFeet)
            it.setObject(3, owner); it.setObject(4, id); it.setLong(5, revision)
            it.executeQuery().use { rows -> if (rows.next()) rows.project() else changedOrMissing(connection,owner,id) }
        }
    }
    override suspend fun saveFloorPlan(owner:UUID,id:UUID,revision:Long,setup:FloorPlanSetup):Project=database {connection->
        connection.prepareStatement("""UPDATE plotus.projects SET floor_plan=jsonb_build_object('setup',?::jsonb,'plot',plot_setup),
            revision=revision+1,updated_at=clock_timestamp() WHERE owner_id=? AND id=? AND revision=? AND plot_setup IS NOT NULL RETURNING *""").use {
            it.setString(1,Json.encodeToString(setup));it.setObject(2,owner);it.setObject(3,id);it.setLong(4,revision)
            it.executeQuery().use {rows->if(rows.next())rows.project() else changedOrMissing(connection,owner,id)}
        }
    }
    private fun changedOrMissing(connection: Connection, owner: UUID, id: UUID): Nothing {
        connection.prepareStatement("SELECT 1 FROM plotus.projects WHERE owner_id=? AND id=?").use {
            it.setObject(1, owner); it.setObject(2, id)
            it.executeQuery().use { rows ->
                if (!rows.next()) throw missingProject()
                throw ApiFailure(409,"PROJECT_CHANGED","This project changed on another device. Refresh projects and reopen it before making changes.")
            }
        }
    }
    private suspend fun <T> database(operation: (Connection) -> T): T = withContext(Dispatchers.IO) {
        source.connection.use { operation(it) }
    }
    private fun PreparedStatement.details(draft: ProjectDraft, start: Int) {
        setString(start,draft.name.trim()); setString(start+1,draft.location.trim())
        setString(start+2,if(draft.isClient) draft.clientName.trim() else null)
        setString(start+3,if(draft.isClient) draft.email.trim() else null)
        setString(start+4,if(draft.isClient) draft.phone.trim() else null)
    }
    private fun ResultSet.project(): Project {
        val edited = getTimestamp("updated_at").toInstant()
        val minutes = Duration.between(edited,Instant.now()).toMinutes().coerceAtLeast(0)
        val label = when { minutes<1 -> "Just now"; minutes<60 -> "$minutes min ago"; minutes<1440 -> "${minutes/60} hr ago"; else -> "${minutes/1440} days ago" }
        return Project(getObject("id").toString(),getString("name"),getString("location"),getObject("area_sq_ft") as Int?,label,
            getString("client_name")?.let { Client(it,getString("client_email"),getString("client_phone").orEmpty()) },getLong("revision"),edited.toString(),
            getString("plot_setup")?.let { Json.decodeFromString<PlotSetup>(it) },
            getString("floor_plan")?.let {Json.decodeFromString<SavedFloorPlan>(it)},lastOpenedSection=PlanningSection.valueOf(getString("last_opened_section")))
    }
}

private fun missingProject() = ApiFailure(404,"PROJECT_NOT_FOUND","This project could not be found.")
