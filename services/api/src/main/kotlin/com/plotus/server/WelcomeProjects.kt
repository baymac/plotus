package com.plotus.server

import com.plotus.shared.*
import java.sql.Connection
import java.time.Duration
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

/** Provision ordinary, independently owned projects, never shared authenticated data. */
internal class WelcomeProjects(private val source: DataSource, private val templateKey: String) {
    companion object {
        const val DEFAULT_KEY = "welcome-v1"
        const val PROJECT_NAME = "Demo project"
        const val PROJECT_LOCATION = "Sample corner plot"
    }

    /** Operator-only configuration. The source is read once; later edits cannot change the example. */
    fun publishConfigured() {
        val project = System.getenv("PLOTUS_DEMO_SOURCE_PROJECT_ID")?.trim().orEmpty()
        val generation = System.getenv("PLOTUS_DEMO_SOURCE_GENERATION_ID")?.trim().orEmpty()
        if (project.isEmpty() && generation.isEmpty()) return
        require(project.isNotEmpty() && generation.isNotEmpty()) { "Set both demo source identifiers" }
        publish(UUID.fromString(project), UUID.fromString(generation))
    }

    fun publish(project: UUID, generation: UUID) {
        source.connection.use { c ->
            if (template(c) != null) return
            c.prepareStatement("""SELECT g.snapshot->'floorPlan' AS brief, g.result,
                g.created_at, g.updated_at FROM plotus.plan_generations g
                JOIN plotus.projects p ON p.id=g.project_id AND p.owner_id=g.owner_id
                WHERE g.project_id=? AND g.id=? AND g.status='ready'
                AND g.snapshot->'plot'=g.snapshot->'floorPlan'->'plot'""").use { statement ->
                statement.setObject(1, project); statement.setObject(2, generation)
                statement.executeQuery().use { rows ->
                    require(rows.next()) { "The demo source must be a completed generation with matching saved inputs" }
                    val brief = Json.decodeFromString<SavedFloorPlan>(rows.getString("brief"))
                    val saved = Json.decodeFromString<GeneratedFloorPlan>(rows.getString("result"))
                    require(brief.setup.errors(brief.plot, complete=true).isEmpty()) { "Invalid demo brief" }
                    require(saved.status=="ready" && !saved.draft) { "A demo cannot be a draft" }
                    validateGeneratedDrawings(saved.drawings)
                    require(saved.drawings.size==brief.setup.buildingFloors && saved.drawings.all {
                        it.review?.let { review -> review.isValid() && review.requiredChecksPass } == true
                    }) { "The demo must include every floor and pass its measured required checks" }
                    val duration = saved.durationMillis ?: Duration.between(
                        rows.getTimestamp("created_at").toInstant(), rows.getTimestamp("updated_at").toInstant()
                    ).toMillis().coerceAtLeast(0)
                    // Store only geometry, requirements and checked drawings. Project/client identity is never copied.
                    val example = saved.copy(error=null, findings=emptyList(), optimizedFromPlanID=null, durationMillis=duration)
                    c.prepareStatement("""INSERT INTO plotus.welcome_templates(template_key,brief,generation)
                        VALUES (?,?::jsonb,?::jsonb) ON CONFLICT(template_key) DO NOTHING""").use {
                        it.setString(1,templateKey); it.setString(2,Json.encodeToString(brief))
                        it.setString(3,Json.encodeToString(example)); it.executeUpdate()
                    }
                }
            }
        }
    }

    /** Called only after the API has verified the account, before its first project list is returned. */
    fun provision(c: Connection, owner: UUID) {
        c.autoCommit = false
        try {
            c.prepareStatement("SELECT 1 FROM plotus.project_onboarding WHERE owner_id=?").use {
                it.setObject(1,owner); it.executeQuery().use { rows -> if(rows.next()) { c.commit(); return } }
            }
            // Also protect an existing workspace created between migration and this first request.
            c.prepareStatement("""INSERT INTO plotus.project_onboarding(owner_id)
                SELECT ? WHERE EXISTS(SELECT 1 FROM plotus.projects WHERE owner_id=?)
                ON CONFLICT(owner_id) DO NOTHING""").use {
                it.setObject(1,owner); it.setObject(2,owner)
                if(it.executeUpdate()>0) { c.commit(); return }
            }
            val example = template(c)
            if (example == null) { c.commit(); return }
            val project = UUID.randomUUID()
            val generation = UUID.randomUUID()
            // The unique owner claim serializes concurrent first requests across API instances.
            val claimed = c.prepareStatement("""INSERT INTO plotus.project_onboarding(owner_id,template_key,project_id)
                VALUES (?,?,?) ON CONFLICT(owner_id) DO NOTHING""").use {
                it.setObject(1,owner); it.setString(2,templateKey); it.setObject(3,project); it.executeUpdate()==1
            }
            if(!claimed) { c.commit(); return }
            val (brief, saved) = example
            val now = Instant.now()
            val plan = saved.copy(id=generation.toString(), status="ready", sourceRevision=1,
                createdAt=now.toString(), optimizedFromPlanID=null,
                requirements=brief.setup.singleResidence(), roadLabels=brief.plot.roadCanvasLabels())
            c.prepareStatement("""INSERT INTO plotus.projects(id,owner_id,name,location,area_sq_ft,
                plot_setup,floor_plan,last_opened_section) VALUES (?,?,?,?,?,?::jsonb,?::jsonb,'FLOOR_PLAN')""").use {
                it.setObject(1,project); it.setObject(2,owner); it.setString(3,PROJECT_NAME)
                it.setString(4,PROJECT_LOCATION); it.setObject(5,brief.plot.areaSquareFeet)
                it.setString(6,Json.encodeToString(brief.plot)); it.setString(7,Json.encodeToString(brief)); it.executeUpdate()
            }
            val snapshot = buildJsonObject {
                put("plot",Json.encodeToJsonElement(brief.plot)); put("floorPlan",Json.encodeToJsonElement(brief))
            }
            c.prepareStatement("""INSERT INTO plotus.plan_generations(id,owner_id,project_id,request_id,
                source_revision,snapshot,status,result,created_at,updated_at)
                VALUES (?,?,?,?,1,?::jsonb,'ready',?::jsonb,?::timestamptz,?::timestamptz)""").use {
                it.setObject(1,generation); it.setObject(2,owner); it.setObject(3,project); it.setObject(4,UUID.randomUUID())
                it.setString(5,snapshot.toString()); it.setString(6,Json.encodeToString(plan))
                it.setString(7,now.toString()); it.setString(8,now.toString()); it.executeUpdate()
            }
            c.commit()
        } catch (failure: Exception) {
            c.rollback(); throw failure
        } finally { c.autoCommit = true }
    }

    private fun template(c: Connection): Pair<SavedFloorPlan,GeneratedFloorPlan>? =
        c.prepareStatement("SELECT brief,generation FROM plotus.welcome_templates WHERE template_key=?").use {
            it.setString(1,templateKey)
            it.executeQuery().use { rows -> if(rows.next()) Pair(
                Json.decodeFromString<SavedFloorPlan>(rows.getString("brief")),
                Json.decodeFromString<GeneratedFloorPlan>(rows.getString("generation"))
            ) else null }
        }
}
