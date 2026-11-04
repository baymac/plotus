package com.plotus.server

import com.plotus.shared.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assume.assumeTrue
import org.postgresql.ds.PGSimpleDataSource
import kotlin.test.*

class WelcomeProjectsIntegrationTest {
    private fun database(): PGSimpleDataSource {
        val url=System.getenv("PLOTUS_TEST_DATABASE_URL")
        assumeTrue("Run services/api/scripts/check.sh",url!=null)
        return PGSimpleDataSource().apply {
            setURL(url!!); user="plotus"; password=System.getenv("PLOTUS_TEST_DATABASE_PASSWORD")
        }
    }

    private val plot=PlotSetup(lengths=listOf(18.0,12.0,18.0,12.0),
        vertices=listOf(PlotPoint(0.0,0.0),PlotPoint(18.0,0.0),PlotPoint(18.0,12.0),PlotPoint(0.0,12.0)),
        roads=listOf(PlotRoad(0)),northDegrees=-6.0)
    private val setup=FloorPlanSetup(units=PlotUnits.METRES)
    private val review=PlanReviewReport(rulesVersion="test-v1",sourceHash="a".repeat(64),designScore=90,
        metrics=mapOf("space" to 25,"circulation" to 20,"function" to 20,"daylight" to 15,"privacy" to 10,"access" to 10)
            .map {(id,weight)->ReviewMetric(id,id,weight,90.0)},
        gates=listOf(ReviewCheck("layout","Native layout checks","pass")))
    private val drawing=PlanDrawing("floor-0","Ground floor","Zml4dHVyZQ==",100,100,
        presentation=PlanPresentation(version=2,units=PlotUnits.METRES),review=review)

    private suspend fun example(store:PostgresProjects,jobs:PostgresPlans,owner:UUID):Pair<Project,GeneratedFloorPlan> {
        var p=store.create(owner,ProjectDraft("Private client home","Private address",true,"Private client","private@example.invalid","12345"))
        p=store.savePlot(owner,UUID.fromString(p.id),p.revision,plot)
        p=store.saveFloorPlan(owner,UUID.fromString(p.id),p.revision,setup)
        val job=jobs.start(owner,p,StartPlanRequest(p.revision,UUID.randomUUID().toString())).plan
        val ready=job.copy(status="ready",drawings=listOf(drawing),durationMillis=61000)
        jobs.finish(owner,UUID.fromString(p.id),ready)
        return p to ready
    }

    private fun cleanup(source:PGSimpleDataSource,key:String,owners:List<UUID>) {
        source.connection.use {c->
            for(owner in owners) {
                c.prepareStatement("DELETE FROM plotus.projects WHERE owner_id=?").use {it.setObject(1,owner);it.executeUpdate()}
                c.prepareStatement("DELETE FROM plotus.project_onboarding WHERE owner_id=?").use {it.setObject(1,owner);it.executeUpdate()}
            }
            c.prepareStatement("DELETE FROM plotus.welcome_templates WHERE template_key=?").use {it.setString(1,key);it.executeUpdate()}
        }
    }

    @Test fun eachNewAccountGetsOneIndependentExampleFromTheFrozenGenerationSnapshot()=runBlocking {
        val source=database();val key="test-${UUID.randomUUID()}"
        val originalOwner=UUID.randomUUID();val alice=UUID.randomUUID();val bob=UUID.randomUUID()
        val store=PostgresProjects(source,key).also {it.migrate()};val jobs=PostgresPlans(source)
        try {
            val (original,ready)=example(store,jobs,originalOwner)
            val publisher=WelcomeProjects(source,key)
            publisher.publish(UUID.fromString(original.id),UUID.fromString(ready.id))
            // Later source edits must not make the example inconsistent or change its geometry.
            store.savePlot(originalOwner,UUID.fromString(original.id),original.revision,plot.copy(northDegrees=50.0))
            publisher.publish(UUID.fromString(original.id),UUID.fromString(ready.id))
            val lists=coroutineScope {(1..8).map {async(Dispatchers.IO){store.list(alice)}}.awaitAll()}
            val a=lists.first().single();val b=store.list(bob).single()
            assertTrue(lists.all {it.single().id==a.id});assertNotEquals(a.id,b.id)
            assertEquals(WelcomeProjects.PROJECT_NAME,a.name);assertEquals(WelcomeProjects.PROJECT_LOCATION,a.location)
            assertNull(a.client);assertEquals(1L,a.revision)
            assertEquals(plot,a.plotSetup);assertEquals(a.plotSetup,a.floorPlan?.plot)
            assertEquals(PlanningSection.FLOOR_PLAN,a.lastOpenedSection)
            val aPlan=jobs.latest(alice,UUID.fromString(a.id))!!
            val bPlan=jobs.latest(bob,UUID.fromString(b.id))!!
            assertNotEquals(ready.id,aPlan.id);assertNotEquals(aPlan.id,bPlan.id)
            assertEquals("ready",aPlan.status);assertEquals(1L,aPlan.sourceRevision)
            assertEquals(61000L,aPlan.durationMillis);assertEquals(listOf(drawing),aPlan.drawings)
            assertEquals(SavedFloorPlan(setup,plot),jobs.snapshot(alice,UUID.fromString(a.id),UUID.fromString(aPlan.id)))
            assertNull(jobs.find(bob,UUID.fromString(a.id),UUID.fromString(aPlan.id)))
            val failure=assertFailsWith<ApiFailure>{store.get(bob,UUID.fromString(a.id))}
            assertEquals(404,failure.status)
            store.delete(alice,UUID.fromString(a.id),a.revision)
            assertTrue(store.list(alice).isEmpty());assertEquals(b.id,store.list(bob).single().id)
        } finally {cleanup(source,key,listOf(originalOwner,alice,bob))}
    }

    @Test fun unavailableOrUncheckedTemplatesDoNotClaimAccountsAndExistingProjectsAreKept()=runBlocking {
        val source=database();val key="test-${UUID.randomUUID()}"
        val originalOwner=UUID.randomUUID();val fresh=UUID.randomUUID();val existing=UUID.randomUUID()
        val store=PostgresProjects(source,key).also {it.migrate()};val jobs=PostgresPlans(source)
        try {
            assertTrue(store.list(fresh).isEmpty())
            val (original,ready)=example(store,jobs,originalOwner)
            source.connection.use {c->c.prepareStatement("UPDATE plotus.plan_generations SET result=?::jsonb WHERE id=?").use {
                it.setString(1,Json.encodeToString(ready.copy(draft=true)));it.setObject(2,UUID.fromString(ready.id));it.executeUpdate()
            }}
            assertFailsWith<IllegalArgumentException>{WelcomeProjects(source,key).publish(UUID.fromString(original.id),UUID.fromString(ready.id))}
            assertTrue(store.list(fresh).isEmpty())
            source.connection.use {c->c.prepareStatement("UPDATE plotus.plan_generations SET result=?::jsonb WHERE id=?").use {
                it.setString(1,Json.encodeToString(ready));it.setObject(2,UUID.fromString(ready.id));it.executeUpdate()
            }}
            val own=store.create(existing,ProjectDraft("My existing home","Test city"))
            WelcomeProjects(source,key).publish(UUID.fromString(original.id),UUID.fromString(ready.id))
            assertEquals(WelcomeProjects.PROJECT_NAME,store.list(fresh).single().name)
            assertEquals(own.id,store.list(existing).single().id)
        } finally {cleanup(source,key,listOf(originalOwner,fresh,existing))}
    }

    @Test fun demoCanDownloadItsClientReportAndStartANewOwnedGeneration() {
        val source=database();val key="test-${UUID.randomUUID()}"
        val originalOwner=UUID.randomUUID();val owner=UUID.randomUUID();val other=UUID.randomUUID()
        val store=PostgresProjects(source,key).also {it.migrate()};val jobs=PostgresPlans(source)
        var generated=0;var reports=0
        val engine=object:PlanEngine {
            override suspend fun preview(project:Project,setup:FloorPlanSetup)=FloorPlanPreview(canGenerate=true)
            override suspend fun generate(project:Project,setup:FloorPlanSetup):List<PlanDrawing> {
                generated++;assertEquals(plot,project.plotSetup)
                assertEquals(project.floorPlan!!.setup.copy(units=setup.units),setup)
                assertEquals(project.plotSetup,project.floorPlan!!.plot);return listOf(drawing)
            }
            override suspend fun clientReport(project:Project,plan:GeneratedFloorPlan,brief:SavedFloorPlan,review:PlanReviewReport?,ai:AIPlanReview?):PlanExport {
                reports++;assertNull(project.client);assertEquals(WelcomeProjects.PROJECT_NAME,project.name)
                assertEquals(plot,brief.plot);assertEquals(setup,brief.setup);assertEquals(listOf(drawing),plan.drawings)
                return PlanExport("demo-client-report.pdf","application/pdf",Base64.getEncoder().encodeToString("%PDF-1.4\nfixture".toByteArray()))
            }
        }
        val service=FloorPlans(jobs,engine)
        try {
            runBlocking {
                val (p,g)=example(store,jobs,originalOwner)
                WelcomeProjects(source,key).publish(UUID.fromString(p.id),UUID.fromString(g.id))
            }
            testApplication {
                application {plotusModule(store,IdentityVerifier {if(it=="welcome-owner")owner else other},floorPlans=service)}
                fun ownedCount():Int=source.connection.use {c->c.prepareStatement("SELECT count(*) FROM plotus.projects WHERE owner_id=?").use {
                    it.setObject(1,owner);it.executeQuery().use {r->r.next();r.getInt(1)}
                }}
                assertEquals(HttpStatusCode.Unauthorized,client.get("/v1/preferences").status)
                assertEquals(0,ownedCount())
                assertEquals(HttpStatusCode.OK,client.get("/v1/preferences"){bearerAuth("welcome-owner")}.status)
                assertEquals(1,ownedCount()) // Initialization also precedes requests other than project listing.
                val list=client.get("/v1/projects"){bearerAuth("welcome-owner")}
                assertEquals(HttpStatusCode.OK,list.status)
                val p=Json.decodeFromString<ProjectList>(list.bodyAsText()).projects.single()
                val history="/v1/projects/${p.id}/floor-plan/generations"
                val initial=Json.decodeFromString<PlanGenerations>(client.get(history){bearerAuth("welcome-owner")}.bodyAsText()).plans.single()
                assertEquals("ready",initial.status);assertEquals(0,generated)
                suspend fun report(id:String,token:String="welcome-owner")=client.get("$history/$id/drawings/floor-0/export/pdf"){bearerAuth(token)}
                assertEquals(HttpStatusCode.OK,report(initial.id).status);assertEquals(1,reports)
                assertEquals(HttpStatusCode.NotFound,report(initial.id,"other-owner").status)
                val started=client.post(history){bearerAuth("welcome-owner");contentType(ContentType.Application.Json)
                    setBody(Json.encodeToString(StartPlanRequest(p.revision,UUID.randomUUID().toString())))}
                assertEquals(HttpStatusCode.Accepted,started.status)
                val fresh=Json.decodeFromString<GeneratedFloorPlan>(started.bodyAsText())
                assertNotEquals(initial.id,fresh.id)
                runBlocking {withTimeout(10000) {
                    while(jobs.find(owner,UUID.fromString(p.id),UUID.fromString(fresh.id))!!.status in listOf("running","queued"))delay(20)
                }}
                val completed=Json.decodeFromString<GeneratedFloorPlan>(client.get("$history/${fresh.id}"){bearerAuth("welcome-owner")}.bodyAsText())
                assertEquals("ready",completed.status);assertEquals(1,generated)
                assertEquals(HttpStatusCode.OK,report(fresh.id).status);assertEquals(2,reports)
            }
        } finally {service.close();cleanup(source,key,listOf(originalOwner,owner,other))}
    }
}
