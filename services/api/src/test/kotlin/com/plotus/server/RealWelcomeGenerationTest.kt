package com.plotus.server

import com.plotus.shared.*
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.Assume.assumeTrue
import org.postgresql.ds.PGSimpleDataSource
import kotlin.test.*

/** Explicit opt-in: exercise the real private worker, with isolated database owners. */
class RealWelcomeGenerationTest {
    @Test fun checkedDemoAndFreshNativeGenerationBothExportRealClientPDFs()=runBlocking {
        val file=System.getenv("PLOTUS_TEST_DEMO_SOURCE_FILE")
        val endpoint=System.getenv("PLOTUS_TEST_ENGINE_URL")
        val token=System.getenv("PLOTUS_TEST_ENGINE_TOKEN")
        val database=System.getenv("PLOTUS_TEST_DATABASE_URL")
        assumeTrue("Real-engine verification is opt-in",file!=null&&endpoint!=null&&token!=null&&database!=null)
        val input=Json.parseToJsonElement(Files.readString(Path.of(file!!))).jsonObject
        val brief=Json.decodeFromJsonElement<SavedFloorPlan>(input.getValue("brief"))
        val original=Json.decodeFromJsonElement<GeneratedFloorPlan>(input.getValue("generation"))
        val source=PGSimpleDataSource().apply {setURL(database!!);user="plotus";password=System.getenv("PLOTUS_TEST_DATABASE_PASSWORD")}
        val key="real-test-${UUID.randomUUID()}";val sourceOwner=UUID.randomUUID();val owner=UUID.randomUUID()
        val projects=PostgresProjects(source,key).also {it.migrate()};val jobs=PostgresPlans(source)
        val engine=HttpPlanEngine(URI(endpoint!!),token!!);val plans=FloorPlans(jobs,engine)
        val output=Path.of(System.getenv("PLOTUS_TEST_DEMO_OUTPUT") ?: "/tmp/plotus-welcome-verification")
        Files.createDirectories(output)
        fun record(status:String,extra:JsonObject=buildJsonObject {}) {
            Files.writeString(output.resolve("result.json"),JsonObject(buildJsonObject {put("status",status)}+extra).toString())
        }
        try {
            record("preparing")
            var p=projects.create(sourceOwner,ProjectDraft("Verification source","Sample corner plot"))
            p=projects.savePlot(sourceOwner,UUID.fromString(p.id),p.revision,brief.plot)
            p=projects.saveFloorPlan(sourceOwner,UUID.fromString(p.id),p.revision,brief.setup)
            val sourceJob=jobs.start(sourceOwner,p,StartPlanRequest(p.revision,UUID.randomUUID().toString())).plan
            jobs.finish(sourceOwner,UUID.fromString(p.id),original.copy(id=sourceJob.id,createdAt=sourceJob.createdAt,sourceRevision=p.revision))
            WelcomeProjects(source,key).publish(UUID.fromString(p.id),UUID.fromString(sourceJob.id))
            val demo=projects.list(owner).single();val projectID=UUID.fromString(demo.id)
            val units=projects.preferences(owner).units
            val saved=plans.get(owner,projectID,UUID.fromString(jobs.latest(owner,projectID)!!.id),units=units)
            assertEquals(brief.plot,demo.plotSetup);assertEquals(demo.plotSetup,demo.floorPlan!!.plot)
            val review=engine.review(demo,brief.setup,saved.drawings)
            assertTrue(review.isValid());assertTrue(review.requiredChecksPass)
            suspend fun pdf(plan:GeneratedFloorPlan,name:String) {
                val report=plans.export(owner,projectID,UUID.fromString(plan.id),plan.drawings.first().id,"pdf",units,demo,plan.drawings.first().review)
                assertEquals("application/pdf",report.mimeType)
                val bytes=Base64.getDecoder().decode(report.base64)
                assertTrue(bytes.size>10000);assertEquals("%PDF-",bytes.take(5).toByteArray().decodeToString())
                Files.write(output.resolve(name),bytes)
            }
            pdf(saved,"demo-client-report.pdf")
            Files.writeString(output.resolve("programme.json"),programme(demo,brief.setup).toString())
            val started=plans.start(owner,demo,StartPlanRequest(demo.revision,UUID.randomUUID().toString()),units)
            assertNotEquals(saved.id,started.id)
            record("generating",buildJsonObject {put("generation",started.id)})
            val fresh=withTimeout(295000) {
                var result=started
                while(result.status in listOf("running","queued")) {
                    delay(1000);result=plans.get(owner,projectID,UUID.fromString(started.id),units=units)
                }
                result
            }
            assertEquals("ready",fresh.status,fresh.error)
            assertFalse(fresh.draft);validateGeneratedDrawings(fresh.drawings)
            assertTrue(fresh.drawings.all {it.review?.requiredChecksPass==true})
            assertEquals(brief.setup.buildingFloors,fresh.drawings.size)
            assertEquals(demo.floorPlan,jobs.snapshot(owner,projectID,UUID.fromString(fresh.id)))
            pdf(fresh,"fresh-client-report.pdf")
            record("passed",buildJsonObject {
                put("demoGeneration",saved.id);put("freshGeneration",fresh.id)
                put("freshDurationMillis",fresh.durationMillis);put("floors",fresh.drawings.size)
                put("requiredChecksPass",true);put("demoReport",true);put("freshReport",true)
            })
        } catch(failure:Throwable) {
            record("failed",buildJsonObject {put("error",failure.message ?: failure.javaClass.simpleName)})
            throw failure
        } finally {
            plans.close()
            source.connection.use {c->
                for(id in listOf(sourceOwner,owner)) {
                    c.prepareStatement("DELETE FROM plotus.projects WHERE owner_id=?").use {it.setObject(1,id);it.executeUpdate()}
                    c.prepareStatement("DELETE FROM plotus.project_onboarding WHERE owner_id=?").use {it.setObject(1,id);it.executeUpdate()}
                }
                c.prepareStatement("DELETE FROM plotus.welcome_templates WHERE template_key=?").use {it.setString(1,key);it.executeUpdate()}
            }
        }
    }
}
