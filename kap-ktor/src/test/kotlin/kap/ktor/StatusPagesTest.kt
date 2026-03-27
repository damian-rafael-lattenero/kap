package kap.ktor

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
import kap.CircuitBreakerOpenException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StatusPagesTest {

    @Test
    fun `handles CircuitBreakerOpenException as 503`() = testApplication {
        install(ContentNegotiation) { json() }
        install(StatusPages) { kapExceptionHandlers() }

        routing {
            get("/test") {
                throw CircuitBreakerOpenException("test breaker is open")
            }
        }

        val response = client.get("/test")
        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
    }

    @Test
    fun `handles IllegalArgumentException as 400`() = testApplication {
        install(ContentNegotiation) { json() }
        install(StatusPages) { kapExceptionHandlers() }

        routing {
            get("/test") {
                throw IllegalArgumentException("invalid input")
            }
        }

        val response = client.get("/test")
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("invalid input"))
    }

    @Test
    fun `handles TimeoutCancellationException as 504`() = testApplication {
        install(ContentNegotiation) { json() }
        install(StatusPages) { kapExceptionHandlers() }

        routing {
            get("/test") {
                withTimeout(1) {
                    kotlinx.coroutines.delay(1000)
                }
            }
        }

        val response = client.get("/test")
        assertEquals(HttpStatusCode.GatewayTimeout, response.status)
    }

    @Test
    fun `503 response body contains error message`() = testApplication {
        install(ContentNegotiation) { json() }
        install(StatusPages) { kapExceptionHandlers() }

        routing {
            get("/test") {
                throw CircuitBreakerOpenException("user-api breaker is open")
            }
        }

        val response = client.get("/test")
        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertTrue(response.bodyAsText().contains("Service temporarily unavailable"))
    }

    @Test
    fun `custom handlers work alongside kap handlers`() = testApplication {
        install(ContentNegotiation) { json() }
        install(StatusPages) {
            kapExceptionHandlers()
            exception<UnsupportedOperationException> { call, cause ->
                call.respondText("custom: ${cause.message}", status = HttpStatusCode.NotImplemented)
            }
        }

        routing {
            get("/kap-error") {
                throw CircuitBreakerOpenException()
            }
            get("/custom-error") {
                throw UnsupportedOperationException("not yet")
            }
        }

        val r1 = client.get("/kap-error")
        assertEquals(HttpStatusCode.ServiceUnavailable, r1.status)

        val r2 = client.get("/custom-error")
        assertEquals(HttpStatusCode.NotImplemented, r2.status)
        assertTrue(r2.bodyAsText().contains("not yet"))
    }
}
