package kap.ktor

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kap.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class KapPluginTest {

    @Test
    fun `plugin installs and provides config`() = testApplication {
        install(Kap) {
            circuitBreaker("test-api", maxFailures = 3, resetTimeout = 10.seconds)
        }
        install(ContentNegotiation) { json() }

        routing {
            get("/test") {
                val breaker = call.circuitBreaker("test-api")
                assertNotNull(breaker)
                call.respondText("ok")
            }
        }

        val response = client.get("/test")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("ok", response.bodyAsText())
    }

    @Test
    fun `circuitBreaker throws on unknown name`() = testApplication {
        install(Kap)
        install(ContentNegotiation) { json() }

        routing {
            get("/test") {
                try {
                    call.circuitBreaker("nonexistent")
                    call.respondText("should not reach")
                } catch (e: IllegalArgumentException) {
                    call.respondText(e.message ?: "error", status = HttpStatusCode.InternalServerError)
                }
            }
        }

        val response = client.get("/test")
        assertEquals(HttpStatusCode.InternalServerError, response.status)
        assertTrue(response.bodyAsText().contains("nonexistent"))
    }

    @Test
    fun `tracer is accessible and invokable from call`() = testApplication {
        val events = mutableListOf<String>()
        install(Kap) {
            tracer = KapTracer { event ->
                when (event) {
                    is TraceEvent.Started -> events.add("started:${event.name}")
                    is TraceEvent.Succeeded -> events.add("succeeded:${event.name}")
                    is TraceEvent.Failed -> events.add("failed:${event.name}")
                }
            }
        }
        install(ContentNegotiation) { json() }

        routing {
            get("/test") {
                val result = Async {
                    Kap { "hello" }.traced("my-op", call.kapTracer)
                }
                call.respondText(result)
            }
        }

        val response = client.get("/test")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("hello", response.bodyAsText())
        assertTrue(events.contains("started:my-op"), "Expected started event, got: $events")
        assertTrue(events.contains("succeeded:my-op"), "Expected succeeded event, got: $events")
    }

    @Test
    fun `multiple circuit breakers can be registered and retrieved independently`() = testApplication {
        install(Kap) {
            circuitBreaker("user-api", maxFailures = 5, resetTimeout = 30.seconds)
            circuitBreaker("payment-api", maxFailures = 3, resetTimeout = 60.seconds)
        }
        install(ContentNegotiation) { json() }

        routing {
            get("/test") {
                val userBreaker = call.circuitBreaker("user-api")
                val paymentBreaker = call.circuitBreaker("payment-api")
                assertNotNull(userBreaker)
                assertNotNull(paymentBreaker)
                assertTrue(userBreaker !== paymentBreaker, "Should be different instances")
                call.respondText("ok")
            }
        }

        val response = client.get("/test")
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `circuit breaker state persists across requests`() = testApplication {
        install(Kap) {
            circuitBreaker("flaky-api", maxFailures = 2, resetTimeout = 60.seconds)
        }
        install(ContentNegotiation) { json() }

        routing {
            get("/fail") {
                val breaker = call.circuitBreaker("flaky-api")
                try {
                    Async {
                        Kap<String> { throw RuntimeException("fail") }
                            .withCircuitBreaker(breaker)
                    }
                } catch (_: Exception) { }
                call.respondText(breaker.currentState.name)
            }
        }

        val r1 = client.get("/fail")
        assertEquals("Closed", r1.bodyAsText())

        val r2 = client.get("/fail")
        assertEquals("Open", r2.bodyAsText())
    }

    @Test
    fun `default tracer is a no-op and does not throw`() = testApplication {
        install(Kap)
        install(ContentNegotiation) { json() }

        routing {
            get("/test") {
                val result = Async {
                    Kap { "value" }.traced("op", call.kapTracer)
                }
                call.respondText(result)
            }
        }

        val response = client.get("/test")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("value", response.bodyAsText())
    }
}
