package kap.ktor

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kap.*
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals

@Serializable
data class TestDashboard(val user: String, val cart: String)

@Serializable
data class TestUser(val name: String, val age: Int)

class RespondAsyncTest {

    private fun ApplicationTestBuilder.jsonClient() = createClient {
        install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() }
    }

    @Test
    fun `respondKap returns JSON from parallel KAP computation`() = testApplication {
        install(ContentNegotiation) { json() }
        install(Kap)

        routing {
            get("/dashboard") {
                call.respondKap {
                    Async {
                        kap(::TestDashboard)
                            .with { "Alice" }
                            .with { "3 items" }
                    }
                }
            }
        }

        val client = jsonClient()
        val response = client.get("/dashboard")
        assertEquals(HttpStatusCode.OK, response.status)
        val dashboard = response.body<TestDashboard>()
        assertEquals("Alice", dashboard.user)
        assertEquals("3 items", dashboard.cart)
    }

    @Test
    fun `respondKap with custom status code`() = testApplication {
        install(ContentNegotiation) { json() }
        install(Kap)

        routing {
            post("/users") {
                call.respondKap(HttpStatusCode.Created) {
                    Async {
                        kap(::TestUser)
                            .with { "Bob" }
                            .with { 30 }
                    }
                }
            }
        }

        val client = jsonClient()
        val response = client.post("/users")
        assertEquals(HttpStatusCode.Created, response.status)
        val user = response.body<TestUser>()
        assertEquals("Bob", user.name)
        assertEquals(30, user.age)
    }

    @Test
    fun `respondKap propagates exceptions to StatusPages`() = testApplication {
        install(ContentNegotiation) { json() }
        install(Kap)
        install(StatusPages) { kapExceptionHandlers() }

        routing {
            get("/fail") {
                call.respondKap<String> {
                    Async {
                        Kap<String> { throw IllegalArgumentException("bad param") }
                    }
                }
            }
        }

        val response = client.get("/fail")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `respondAsync executes KAP and returns JSON`() = testApplication {
        install(ContentNegotiation) { json() }
        install(Kap)

        routing {
            get("/user") {
                call.respondAsync {
                    kap(::TestUser)
                        .with { "Charlie" }
                        .with { 25 }
                }
            }
        }

        val client = jsonClient()
        val response = client.get("/user")
        assertEquals(HttpStatusCode.OK, response.status)
        val user = response.body<TestUser>()
        assertEquals("Charlie", user.name)
        assertEquals(25, user.age)
    }

    @Test
    fun `respondAsync with failure propagates to StatusPages`() = testApplication {
        install(ContentNegotiation) { json() }
        install(Kap)
        install(StatusPages) { kapExceptionHandlers() }

        routing {
            get("/fail") {
                call.respondAsync<String> {
                    Kap { throw IllegalArgumentException("nope") }
                }
            }
        }

        val response = client.get("/fail")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }
}
