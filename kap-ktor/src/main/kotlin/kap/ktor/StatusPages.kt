package kap.ktor

import io.ktor.http.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import kap.CircuitBreakerOpenException
import kotlinx.coroutines.TimeoutCancellationException

/**
 * Install KAP-aware exception handlers into StatusPages.
 *
 * Handles:
 * - `CircuitBreakerOpenException` → 503 Service Unavailable
 * - `TimeoutCancellationException` → 504 Gateway Timeout
 * - `IllegalArgumentException` → 400 Bad Request
 *
 * ```kotlin
 * install(StatusPages) {
 *     kapExceptionHandlers()
 *     // your custom handlers here
 * }
 * ```
 */
fun StatusPagesConfig.kapExceptionHandlers() {
    exception<CircuitBreakerOpenException> { call, _ ->
        call.respond(HttpStatusCode.ServiceUnavailable, mapOf("error" to "Service temporarily unavailable"))
    }
    exception<TimeoutCancellationException> { call, _ ->
        call.respond(HttpStatusCode.GatewayTimeout, mapOf("error" to "Request timed out"))
    }
    exception<IllegalArgumentException> { call, cause ->
        call.respond(HttpStatusCode.BadRequest, mapOf("error" to (cause.message ?: "Bad request")))
    }
}
