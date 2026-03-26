import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlin.test.*

/**
 * Integration tests for the comprehensive Ktor example.
 *
 * Each test exercises a different endpoint, validating that the kap APIs
 * (kap-core, kap-resilience, kap-arrow) integrate correctly with Ktor.
 */
class KtorIntegrationTest {

    private fun ApplicationTestBuilder.jsonClient() = createClient {
        install(ContentNegotiation) { json() }
    }

    // ═════════════════════════════════════════════════════════════════════
    //  kap-core routes
    // ═════════════════════════════════════════════════════════════════════

    @Test
    fun `GET dashboard - multi-phase parallel aggregation`() = testApplication {
        application { module() }
        val client = jsonClient()

        val response = client.get("/dashboard/user-42")
        assertEquals(HttpStatusCode.OK, response.status)

        val body = response.body<Dashboard>()
        assertEquals("user-42", body.user.id)
        assertEquals("Alice", body.user.name)
        assertEquals("Gold", body.user.tier)
        assertEquals(3, body.cart.items)
        assertEquals(149.99, body.cart.total)
        assertEquals(2, body.recentOrders.size)
        assertTrue(body.authToken.startsWith("tok_user-42_"))
        assertEquals(2, body.recommendations.size)
        assertEquals(7, body.notificationCount)
    }

    @Test
    fun `GET products - traverse all`() = testApplication {
        application { module() }
        val client = jsonClient()

        val response = client.get("/products?ids=P1,P2,P3")
        assertEquals(HttpStatusCode.OK, response.status)

        val body = response.body<ProductList>()
        assertEquals(3, body.products.size)
        assertTrue(body.failures.isEmpty())
    }

    @Test
    fun `GET products - traverse with concurrency`() = testApplication {
        application { module() }
        val client = jsonClient()

        val response = client.get("/products?ids=P1,P2,P3,P4,P5&concurrency=2")
        assertEquals(HttpStatusCode.OK, response.status)

        val body = response.body<ProductList>()
        assertEquals(5, body.products.size)
    }

    @Test
    fun `GET products - traverseSettled tolerates failures`() = testApplication {
        application { module() }
        val client = jsonClient()

        val response = client.get("/products?ids=P1,FAIL,P3&settled=true")
        assertEquals(HttpStatusCode.OK, response.status)

        val body = response.body<ProductList>()
        assertEquals(2, body.products.size)
        assertEquals(1, body.failures.size)
        assertTrue(body.failures[0].contains("FAIL"))
    }

    @Test
    fun `GET products - default ids when none provided`() = testApplication {
        application { module() }
        val client = jsonClient()

        val response = client.get("/products")
        assertEquals(HttpStatusCode.OK, response.status)

        val body = response.body<ProductList>()
        assertEquals(5, body.products.size)
    }

    @Test
    fun `GET search - computation DSL with valid query`() = testApplication {
        application { module() }
        val client = jsonClient()

        val response = client.get("/search?q=laptop")
        assertEquals(HttpStatusCode.OK, response.status)

        val body = response.body<SearchResult>()
        assertEquals("laptop", body.query)
        assertEquals(5, body.results.size)
        assertEquals(5, body.totalCount)
    }

    @Test
    fun `GET search - with minPrice filter`() = testApplication {
        application { module() }
        val client = jsonClient()

        val response = client.get("/search?q=laptop&minPrice=40.0")
        assertEquals(HttpStatusCode.OK, response.status)

        val body = response.body<SearchResult>()
        assertTrue(body.results.all { it.price >= 40.0 })
    }

    @Test
    fun `GET search - missing query returns 400`() = testApplication {
        application { module() }
        val client = jsonClient()

        val response = client.get("/search")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `GET search - short query returns 400`() = testApplication {
        application { module() }
        val client = jsonClient()

        val response = client.get("/search?q=x")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `GET compare - settled results with race`() = testApplication {
        application { module() }
        val client = jsonClient()

        val response = client.get("/compare?ids=P1,P2,P3")
        assertEquals(HttpStatusCode.OK, response.status)

        val body = response.body<CompareResponse>()
        assertEquals(3, body.products.size)
        assertEquals(3, body.settled.size)
        assertNotNull(body.winner)
    }

    @Test
    fun `GET compare - missing ids returns 400`() = testApplication {
        application { module() }
        val client = jsonClient()

        val response = client.get("/compare")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    // ═════════════════════════════════════════════════════════════════════
    //  kap-resilience routes
    // ═════════════════════════════════════════════════════════════════════

    @Test
    fun `GET pricing - quorum strategy`() = testApplication {
        application { module() }
        val client = jsonClient()

        val response = client.get("/pricing/ITEM-001?strategy=quorum")
        assertEquals(HttpStatusCode.OK, response.status)

        val body = response.body<PricingResponse>()
        assertEquals("ITEM-001", body.itemId)
        assertEquals(99.99, body.pricing.price)
        assertTrue(body.strategy.contains("quorum"))
    }

    @Test
    fun `GET pricing - timeout-race strategy uses cache on slow primary`() = testApplication {
        application { module() }
        val client = jsonClient()

        val response = client.get("/pricing/ITEM-002?strategy=timeout-race")
        assertEquals(HttpStatusCode.OK, response.status)

        val body = response.body<PricingResponse>()
        assertEquals("cache", body.pricing.source)
        assertTrue(body.strategy.contains("timeout-race"))
    }

    @Test
    fun `GET pricing - fallback-chain strategy cascades to last`() = testApplication {
        application { module() }
        val client = jsonClient()

        val response = client.get("/pricing/ITEM-003?strategy=fallback-chain")
        assertEquals(HttpStatusCode.OK, response.status)

        val body = response.body<PricingResponse>()
        assertEquals("static-fallback", body.pricing.source)
        assertTrue(body.strategy.contains("fallback-chain"))
    }

    @Test
    fun `GET pricing - orElse strategy cascades to last`() = testApplication {
        application { module() }
        val client = jsonClient()

        val response = client.get("/pricing/ITEM-004?strategy=orElse")
        assertEquals(HttpStatusCode.OK, response.status)

        val body = response.body<PricingResponse>()
        assertEquals("static-fallback", body.pricing.source)
    }

    @Test
    fun `GET pricing - unknown strategy returns 400`() = testApplication {
        application { module() }
        val client = jsonClient()

        val response = client.get("/pricing/ITEM-005?strategy=unknown")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `GET users - retry mode with retryWithResult`() = testApplication {
        application { module() }
        val client = jsonClient()

        val response = client.get("/users/u-1?mode=retry")
        assertEquals(HttpStatusCode.OK, response.status)

        val body = response.body<UserResponse>()
        assertEquals("u-1", body.user.id)
        assertEquals("premium", body.user.tier)
        assertTrue(body.retryAttempts >= 1)
    }

    @Test
    fun `GET users - fallback mode returns cached user`() = testApplication {
        application { module() }
        val client = jsonClient()

        val response = client.get("/users/u-2?mode=fallback")
        assertEquals(HttpStatusCode.OK, response.status)

        val body = response.body<UserResponse>()
        assertEquals("u-2", body.user.id)
    }

    @Test
    fun `GET users - attempt mode returns Either`() = testApplication {
        application { module() }
        val client = jsonClient()

        val response = client.get("/users/u-3?mode=attempt")
        assertTrue(response.status == HttpStatusCode.OK || response.status == HttpStatusCode.ServiceUnavailable)
    }

    @Test
    fun `GET health - Resource zip + bracket + guaranteeCase`() = testApplication {
        application { module() }
        val client = jsonClient()

        val response = client.get("/health")
        assertEquals(HttpStatusCode.OK, response.status)

        val body = response.body<HealthResponse>()
        assertEquals("healthy", body.status)
        assertEquals(3, body.checks.size)
        assertTrue(body.checks.any { it.service == "database" })
        assertTrue(body.checks.any { it.service == "cache" })
        assertTrue(body.checks.any { it.service == "external-api" })
        assertEquals("completed", body.exitCase)
    }

    // ═════════════════════════════════════════════════════════════════════
    //  kap-arrow routes
    // ═════════════════════════════════════════════════════════════════════

    @Test
    fun `POST register - valid input creates user`() = testApplication {
        application { module() }
        val client = jsonClient()

        val response = client.post("/register") {
            contentType(ContentType.Application.Json)
            setBody(RegistrationRequest("Alice", "alice@example.com", 25, listOf("kotlin", "coroutines")))
        }
        assertEquals(HttpStatusCode.Created, response.status)

        val body = response.body<RegistrationResult>()
        assertEquals("Alice", body.name)
        assertEquals("alice@example.com", body.email)
        assertEquals(25, body.age)
        assertEquals(listOf("kotlin", "coroutines"), body.interests)
        assertTrue(body.id.startsWith("USR-"))
    }

    @Test
    fun `POST register - invalid input accumulates all errors`() = testApplication {
        application { module() }
        val client = jsonClient()

        val response = client.post("/register") {
            contentType(ContentType.Application.Json)
            setBody(RegistrationRequest("A", "bad-email", 15, listOf("ok", "x")))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)

        val body = response.body<ErrorResponse>()
        assertTrue(body.errors.size >= 3, "Expected at least 3 errors, got ${body.errors.size}: ${body.errors}")
        assertTrue(body.errors.any { it.contains("Name") })
        assertTrue(body.errors.any { it.contains("Email") || it.contains("email") })
        assertTrue(body.errors.any { it.contains("Age") || it.contains("age") || it.contains("18") })
    }

    @Test
    fun `POST register - duplicate email rejected via andThenV`() = testApplication {
        application { module() }
        val client = jsonClient()

        val response = client.post("/register") {
            contentType(ContentType.Application.Json)
            setBody(RegistrationRequest("Bob", "taken@example.com", 30, listOf("java")))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)

        val body = response.body<ErrorResponse>()
        assertTrue(body.errors.any { it.contains("already registered") })
    }

    @Test
    fun `POST validate-batch - all valid`() = testApplication {
        application { module() }
        val client = jsonClient()

        val response = client.post("/validate-batch") {
            contentType(ContentType.Application.Json)
            setBody(BatchValidationRequest(listOf("a@example.com", "b@test.io")))
        }
        assertEquals(HttpStatusCode.OK, response.status)

        val body = response.body<BatchValidationResponse>()
        assertNotNull(body.valid)
        assertEquals(2, body.valid.size)
        assertNull(body.errors)
    }

    @Test
    fun `POST validate-batch - mixed valid and invalid accumulates errors`() = testApplication {
        application { module() }
        val client = jsonClient()

        val response = client.post("/validate-batch") {
            contentType(ContentType.Application.Json)
            setBody(BatchValidationRequest(listOf("a@example.com", "bad-email", "also-bad")))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)

        val body = response.body<BatchValidationResponse>()
        assertNull(body.valid)
        assertNotNull(body.errors)
        assertEquals(2, body.errors.size)
    }

    // ═════════════════════════════════════════════════════════════════════
    //  Combined routes: Full pipeline
    // ═════════════════════════════════════════════════════════════════════

    @Test
    fun `POST orders - valid order goes through full pipeline`() = testApplication {
        application { module() }
        val client = jsonClient()

        val response = client.post("/orders") {
            contentType(ContentType.Application.Json)
            setBody(OrderRequest("ITEM-12345", 2, "123 Main St", "Springfield", "62701"))
        }
        assertEquals(HttpStatusCode.Created, response.status)

        val body = response.body<PlacedOrder>()
        assertEquals("ITEM-12345", body.itemId)
        assertEquals(2, body.quantity)
        assertTrue(body.inventory.available)
        assertEquals("warehouse-east", body.inventory.warehouse)
        assertTrue(body.pricing.unitPrice > 0)
        assertTrue(body.payment.txId.startsWith("TX-"))
        assertTrue(body.payment.amount > 0)
        assertTrue(body.confirmation.orderId.startsWith("ORD-"))
        assertEquals("3-5 business days", body.confirmation.estimatedDelivery)
    }

    @Test
    fun `POST orders - invalid input returns accumulated validation errors`() = testApplication {
        application { module() }
        val client = jsonClient()

        val response = client.post("/orders") {
            contentType(ContentType.Application.Json)
            setBody(OrderRequest("bad", 0, "", "", "abc"))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)

        val body = response.body<ErrorResponse>()
        assertTrue(body.errors.size >= 3, "Expected at least 3 validation errors, got: ${body.errors}")
    }

    @Test
    fun `POST orders - invalid item only`() = testApplication {
        application { module() }
        val client = jsonClient()

        val response = client.post("/orders") {
            contentType(ContentType.Application.Json)
            setBody(OrderRequest("bad", 1, "123 St", "City", "12345"))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)

        val body = response.body<ErrorResponse>()
        assertTrue(body.errors.any { it.contains("Item") || it.contains("ITEM-") })
    }
}
