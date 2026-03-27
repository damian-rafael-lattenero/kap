package kap.ktor

import io.ktor.server.application.*
import io.ktor.util.*
import kap.KapTracer
import kap.TraceEvent
import kap.CircuitBreaker
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * KAP plugin configuration for Ktor.
 */
class KapConfig {
    /**
     * Default tracer attached to all KAP operations in routes.
     * Override to integrate with your observability stack.
     */
    var tracer: KapTracer = KapTracer { }

    /**
     * Named circuit breakers shared across routes.
     */
    internal val circuitBreakers = mutableMapOf<String, CircuitBreaker>()

    /**
     * Register a named circuit breaker.
     */
    fun circuitBreaker(
        name: String,
        maxFailures: Int = 5,
        resetTimeout: Duration = 30.seconds,
        onStateChange: (CircuitBreaker.State, CircuitBreaker.State) -> Unit = { _, _ -> }
    ) {
        circuitBreakers[name] = CircuitBreaker(
            maxFailures = maxFailures,
            resetTimeout = resetTimeout,
            onStateChange = onStateChange
        )
    }
}

/**
 * KAP Ktor plugin. Install to get shared configuration, circuit breakers, and tracing.
 *
 * ```kotlin
 * install(Kap) {
 *     tracer = KapTracer { event ->
 *         when (event) {
 *             is TraceEvent.Started -> logger.info("${event.name} started")
 *             is TraceEvent.Succeeded -> logger.info("${event.name} in ${event.duration}")
 *             is TraceEvent.Failed -> logger.error("${event.name} failed", event.error)
 *         }
 *     }
 *     circuitBreaker("user-api", maxFailures = 5, resetTimeout = 30.seconds)
 *     circuitBreaker("payment-api", maxFailures = 3, resetTimeout = 60.seconds)
 * }
 * ```
 */
val Kap = createApplicationPlugin(name = "Kap", createConfiguration = ::KapConfig) {
    val config = pluginConfig
    application.attributes.put(KapConfigKey, config)
}

internal val KapConfigKey = AttributeKey<KapConfig>("KapConfig")

/**
 * Access the installed KAP configuration.
 */
val ApplicationCall.kapConfig: KapConfig
    get() = application.attributes[KapConfigKey]

/**
 * Access the default tracer from the KAP plugin.
 */
val ApplicationCall.kapTracer: KapTracer
    get() = kapConfig.tracer

/**
 * Get a named circuit breaker registered in the KAP plugin.
 *
 * @throws IllegalArgumentException if the circuit breaker is not registered.
 */
fun ApplicationCall.circuitBreaker(name: String): CircuitBreaker =
    kapConfig.circuitBreakers[name]
        ?: throw IllegalArgumentException(
            "CircuitBreaker '$name' not registered. Available: ${kapConfig.circuitBreakers.keys}"
        )
