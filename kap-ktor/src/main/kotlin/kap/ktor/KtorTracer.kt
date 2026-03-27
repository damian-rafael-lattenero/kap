package kap.ktor

import io.ktor.server.application.*
import kap.KapTracer
import kap.TraceEvent

/**
 * Create a KapTracer that logs to Ktor's application logger.
 *
 * ```kotlin
 * install(Kap) {
 *     tracer = application.ktorTracer()
 * }
 * ```
 */
fun Application.ktorTracer(): KapTracer = KapTracer { event ->
    when (event) {
        is TraceEvent.Started ->
            log.debug("KAP [{}] started", event.name)
        is TraceEvent.Succeeded ->
            log.info("KAP [{}] completed in {}ms", event.name, event.duration.inWholeMilliseconds)
        is TraceEvent.Failed ->
            log.error("KAP [{}] failed after {}ms: {}", event.name, event.duration.inWholeMilliseconds, event.error.message)
    }
}

/**
 * Create a KapTracer with structured key-value output for JSON logging.
 *
 * ```kotlin
 * install(Kap) {
 *     tracer = application.structuredTracer()
 * }
 * ```
 */
fun Application.structuredTracer(): KapTracer = KapTracer { event ->
    when (event) {
        is TraceEvent.Started ->
            log.info("kap.event=started kap.name={}", event.name)
        is TraceEvent.Succeeded ->
            log.info("kap.event=succeeded kap.name={} kap.duration_ms={}", event.name, event.duration.inWholeMilliseconds)
        is TraceEvent.Failed ->
            log.error("kap.event=failed kap.name={} kap.duration_ms={} kap.error={}", event.name, event.duration.inWholeMilliseconds, event.error.message)
    }
}
