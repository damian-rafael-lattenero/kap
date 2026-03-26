package kap

import kotlin.time.Duration
import kotlin.time.TimeSource

// ── traced: observability hook ──────────────────────────────────────────

/**
 * Wraps this computation with lifecycle hooks for observability.
 *
 * No logging framework is coupled — callers connect their own metrics,
 * tracing, or logging via the callback parameters.
 *
 * ```
 * kap(::Dashboard)
 *     .with { fetchUser().traced("user", tracer) }
 *     .with { fetchConfig().traced("config", tracer) }
 *     .with { fetchCart().traced("cart", tracer) }
 * ```
 *
 * [kotlinx.coroutines.CancellationException] is reported via [onError]
 * and re-thrown — cancellation always propagates.
 */
fun <A> Kap<A>.traced(
    name: String,
    onStart: (name: String) -> Unit = {},
    onSuccess: (name: String, duration: Duration) -> Unit = { _, _ -> },
    onError: (name: String, duration: Duration, error: Throwable) -> Unit = { _, _, _ -> },
): Kap<A> = Kap {
    onStart(name)
    val mark = TimeSource.Monotonic.markNow()
    try {
        val result = with(this@traced) { execute() }
        onSuccess(name, mark.elapsedNow())
        result
    } catch (e: Throwable) {
        onError(name, mark.elapsedNow(), e)
        throw e
    }
}

// ── KapTracer: structured observability ─────────────────────────

/**
 * Structured observability interface for [Kap] tracing.
 *
 * Implement this to integrate with your metrics/tracing system:
 *
 * ```
 * val tracer = KapTracer { event ->
 *     when (event) {
 *         is TraceEvent.Started -> logger.info("${event.name} started")
 *         is TraceEvent.Succeeded -> metrics.timer(event.name).record(event.duration)
 *         is TraceEvent.Failed -> logger.error("${event.name} failed", event.error)
 *     }
 * }
 *
 * kap(::Result)
 *     .with { fetchUser().traced("user", tracer) }
 *     .with { fetchCart().traced("cart", tracer) }
 * ```
 */
fun interface KapTracer {
    fun onEvent(event: TraceEvent)
}

/**
 * Lifecycle events emitted by [traced].
 */
sealed class TraceEvent {
    abstract val name: String

    data class Started(override val name: String) : TraceEvent()
    data class Succeeded(override val name: String, val duration: Duration) : TraceEvent()
    data class Failed(override val name: String, val duration: Duration, val error: Throwable) : TraceEvent()
}

/**
 * Wraps this computation with a structured [KapTracer].
 *
 * Convenience overload that delegates to the three-callback [traced] variant.
 */
fun <A> Kap<A>.traced(name: String, tracer: KapTracer): Kap<A> =
    traced(
        name = name,
        onStart = { tracer.onEvent(TraceEvent.Started(it)) },
        onSuccess = { n, d -> tracer.onEvent(TraceEvent.Succeeded(n, d)) },
        onError = { n, d, e -> tracer.onEvent(TraceEvent.Failed(n, d, e)) },
    )
