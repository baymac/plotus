package com.plotus.server

import com.plotus.shared.*
import java.security.MessageDigest
import java.util.HexFormat
import java.util.UUID
import javax.sql.DataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class PostgresBuildableAreas(private val source:DataSource) {
    private fun fingerprint(key:BuildableAreaKey)=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Json.encodeToString(key).toByteArray()))
    suspend fun find(owner:UUID,project:UUID,key:BuildableAreaKey):SavedBuildableArea?=withContext(Dispatchers.IO) {
        source.connection.use {c->c.prepareStatement("SELECT area FROM plotus.buildable_areas WHERE project_id=? AND owner_id=? AND fingerprint=?").use {
            it.setObject(1,project);it.setObject(2,owner);it.setString(3,fingerprint(key))
            it.executeQuery().use {r->if(r.next())Json.decodeFromString<SavedBuildableArea>(r.getString(1)) else null}
        }}
    }
    suspend fun save(owner:UUID,project:UUID,area:SavedBuildableArea)=withContext(Dispatchers.IO) {
        source.connection.use {c->c.prepareStatement("""INSERT INTO plotus.buildable_areas(project_id,owner_id,fingerprint,area)
            SELECT id,owner_id,?,?::jsonb FROM plotus.projects WHERE id=? AND owner_id=?
            ON CONFLICT (project_id,fingerprint) DO NOTHING""").use {
            it.setString(1,fingerprint(area.key));it.setString(2,Json.encodeToString(area));it.setObject(3,project);it.setObject(4,owner)
            if(it.executeUpdate()==0&&findProject(c,owner,project).not())throw ApiFailure(404,"PROJECT_NOT_FOUND","This project could not be found.")
        }}
    }
    private fun findProject(c:java.sql.Connection,owner:UUID,project:UUID)=c.prepareStatement("SELECT 1 FROM plotus.projects WHERE id=? AND owner_id=?").use {
        it.setObject(1,project);it.setObject(2,owner);it.executeQuery().use {r->r.next()}
    }
}

/** Only the plot and effective setbacks select a saved envelope; no user edit revision is changed. */
class BuildableAreas(private val store:PostgresBuildableAreas?=null,private val engine:PlanEngine?=null) {
    private val calculation=Mutex()
    suspend fun enrich(owner:UUID,project:Project):Project {
        val plot=project.plotSetup ?: return project
        val key=(project.floorPlan?.setup ?: FloorPlanSetup()).buildableKey(plot)
        return project.copy(buildableArea=store?.find(owner,UUID.fromString(project.id),key))
    }
    suspend fun get(owner:UUID,project:Project,setup:FloorPlanSetup):SavedBuildableArea {
        val plot=project.plotSetup ?: throw ApiFailure(400,"PLOT_REQUIRED","Complete Plot setup first.")
        val geometry=setup.geometrySetup()
        val errors=geometry.errors(plot,complete=true)
        if(errors.isNotEmpty())throw ApiFailure(400,"VALIDATION_FAILED","Check the plot and setbacks.",errors)
        val saved=store ?: throw ApiFailure(503,"AREA_UNAVAILABLE","The buildable area is temporarily unavailable.")
        val key=setup.buildableKey(plot);val id=UUID.fromString(project.id)
        saved.find(owner,id,key)?.let {return it}
        return calculation.withLock {
            saved.find(owner,id,key) ?: run {
                val planner=engine ?: throw ApiFailure(503,"ENGINE_UNAVAILABLE","The buildable area isn’t connected yet.")
                val envelope=planner.preview(project,geometry).envelope ?: throw ApiFailure(502,"AREA_UNAVAILABLE","Couldn’t calculate the buildable area.")
                SavedBuildableArea(key,envelope).also {saved.save(owner,id,it)}
            }
        }
    }
}
