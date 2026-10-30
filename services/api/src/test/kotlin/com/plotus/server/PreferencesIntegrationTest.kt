package com.plotus.server

import com.plotus.shared.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assume.assumeTrue
import org.postgresql.ds.PGSimpleDataSource
import java.util.UUID
import kotlin.test.*

class PreferencesIntegrationTest {
    @Test fun preferencesPersistWithoutProjectsAndOnlyBelongToTheVerifiedAccount() {
        val url=System.getenv("PLOTUS_TEST_DATABASE_URL");assumeTrue(url!=null)
        val source=PGSimpleDataSource().apply {setURL(url!!);user="plotus";password=System.getenv("PLOTUS_TEST_DATABASE_PASSWORD")}
        val owner=UUID.randomUUID();val other=UUID.randomUUID()
        val store=PostgresProjects(source).also {it.migrate()}
        try {testApplication {
            application {plotusModule(store,IdentityVerifier {if(it=="owner")owner else other})}
            suspend fun get(token:String="owner")=client.get("/v1/preferences"){bearerAuth(token)}
            suspend fun put(body:String,token:String="owner")=client.put("/v1/preferences") {bearerAuth(token);contentType(ContentType.Application.Json);setBody(body)}
            assertEquals(AccountPreferences(),Json.decodeFromString<AccountPreferences>(get().bodyAsText()))
            assertEquals(HttpStatusCode.OK,put("""{"units":"METRES"}""").status)
            assertEquals(PlotUnits.METRES,Json.decodeFromString<AccountPreferences>(get().bodyAsText()).units)
            assertEquals(PlotUnits.METRES,runBlocking {PostgresProjects(source).preferences(owner)}.units)
            assertEquals(AccountPreferences(),Json.decodeFromString<AccountPreferences>(get("other").bodyAsText()))
            assertEquals(HttpStatusCode.BadRequest,put("""{"units":"YARDS"}""").status)
            assertEquals(HttpStatusCode.BadRequest,put("""{"units":"FEET_INCHES","owner_id":"$other"}""").status)
            assertEquals(HttpStatusCode.Unauthorized,client.put("/v1/preferences"){contentType(ContentType.Application.Json);setBody("""{"units":"METRES"}""")}.status)
            assertEquals(HttpStatusCode.Unauthorized,client.get("/v1/preferences").status)
            assertEquals(PlotUnits.METRES,Json.decodeFromString<AccountPreferences>(get().bodyAsText()).units)
            assertEquals(HttpStatusCode.OK,put("""{"units":"FEET_INCHES"}""").status)
            assertEquals(PlotUnits.FEET_INCHES,Json.decodeFromString<AccountPreferences>(get().bodyAsText()).units)
        }} finally {source.connection.use {c->c.prepareStatement("DELETE FROM plotus.account_preferences WHERE owner_id IN (?,?)").use {it.setObject(1,owner);it.setObject(2,other);it.executeUpdate()}}}
    }
}
