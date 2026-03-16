package applicative

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration

// ── timeout ──────────────────────────────────────────────────────────────

/**
 * Fails with [kotlinx.coroutines.TimeoutCancellationException] if this
 * computation does not complete within [duration].
 */
fun <A> Computation<A>.timeout(duration: Duration): Computation<A> = Computation {
    withTimeout(duration) { with(this@timeout) { execute() } }
}

/**
 * Returns [default] if this computation does not complete within [duration].
 *
 * **Null-safety:** Unlike raw [withTimeoutOrNull], this correctly handles
 * computations that return `null` as a valid value — a `null` result is never
 * confused with a timeout.
 */
fun <A> Computation<A>.timeout(duration: Duration, default: A): Computation<A> = Computation {
    var completed = false
    val result = withTimeoutOrNull(duration) {
        with(this@timeout) { execute() }.also { completed = true }
    }
    if (completed) {
        @Suppress("UNCHECKED_CAST")
        result as A
    } else {
        default
    }
}

/**
 * Runs [fallback] if this computation does not complete within [duration].
 *
 * **Null-safety:** Unlike raw [withTimeoutOrNull], this correctly handles
 * computations that return `null` as a valid value — a `null` result is never
 * confused with a timeout.
 */
fun <A> Computation<A>.timeout(duration: Duration, fallback: Computation<A>): Computation<A> = Computation {
    var completed = false
    val result = withTimeoutOrNull(duration) {
        with(this@timeout) { execute() }.also { completed = true }
    }
    if (completed) {
        @Suppress("UNCHECKED_CAST")
        result as A
    } else {
        with(fallback) { execute() }
    }
}

// ── recover ──────────────────────────────────────────────────────────────

/**
 * Catches non-cancellation exceptions and maps them to a recovery value.
 *
 * [CancellationException] is never caught — structured concurrency
 * cancellation always propagates.
 */
inline fun <A> Computation<A>.recover(crossinline f: suspend (Throwable) -> A): Computation<A> = Computation {
    try {
        with(this@recover) { execute() }
    } catch (e: Throwable) {
        if (e is CancellationException) throw e
        f(e)
    }
}

/**
 * Catches non-cancellation exceptions and switches to a recovery computation.
 */
inline fun <A> Computation<A>.recoverWith(crossinline f: suspend (Throwable) -> Computation<A>): Computation<A> = Computation {
    try {
        with(this@recoverWith) { execute() }
    } catch (e: Throwable) {
        if (e is CancellationException) throw e
        with(f(e)) { execute() }
    }
}

// ── fallback ─────────────────────────────────────────────────────────────

/**
 * On failure, runs [other] instead. Shorthand for `recoverWith { other }`.
 */
infix fun <A> Computation<A>.fallback(other: Computation<A>): Computation<A> =
    recoverWith { other }

// ── retry ────────────────────────────────────────────────────────────────

/**
 * Retries this computation up to [maxAttempts] times on non-cancellation failure.
 *
 * @param maxAttempts total attempts (including the first)
 * @param delay initial delay between retries
 * @param backoff transforms the delay for each subsequent retry (e.g. [exponential])
 * @param shouldRetry predicate to decide whether to retry a given exception.
 *        Defaults to `{ true }` (retry all non-cancellation exceptions).
 *        If `false`, the exception is rethrown immediately.
 * @param onRetry callback invoked before each retry with the attempt number (1-based),
 *        the exception, and the delay before the next attempt.
 *        Useful for logging and metrics.
 */
fun <A> Computation<A>.retry(
    maxAttempts: Int,
    delay: Duration = Duration.ZERO,
    backoff: (Duration) -> Duration = { it },
    shouldRetry: (Throwable) -> Boolean = { true },
    onRetry: suspend (attempt: Int, error: Throwable, nextDelay: Duration) -> Unit = { _, _, _ -> },
): Computation<A> {
    require(maxAttempts >= 1) { "maxAttempts must be >= 1, was $maxAttempts" }
    return Computation {
        var currentDelay = delay
        var lastException: Throwable? = null
        repeat(maxAttempts) { attempt ->
            try {
                return@Computation with(this@retry) { execute() }
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                if (!shouldRetry(e)) throw e
                lastException = e
                if (attempt < maxAttempts - 1) {
                    onRetry(attempt + 1, e, currentDelay)
                    delay(currentDelay)
                    currentDelay = backoff(currentDelay)
                }
            }
        }
        throw lastException!!
    }
}

// ── timeoutRace: parallel timeout with eager fallback ───────────────────

/**
 * Races this computation against a [fallback] that starts immediately.
 * If this computation does not complete within [duration], the fallback
 * result is used. Unlike [timeout] with a fallback computation, the
 * fallback runs **from the start** in parallel — so total time is
 * `max(min(this, duration), fallback)` instead of `duration + fallback`.
 *
 * ```
 * Computation { fetchFromSlowService() }
 *     .timeoutRace(200.milliseconds, Computation { fetchFromCache() })
 * ```
 */
fun <A> Computation<A>.timeoutRace(duration: Duration, fallback: Computation<A>): Computation<A> = Computation {
    val primary = this@timeoutRace
    with(race(
        primary.timeout(duration),
        fallback,
    )) { execute() }
}

// ── backoff strategies ───────────────────────────────────────────────────

/** Doubles the delay on each retry. */
val exponential: (Duration) -> Duration = { it * 2 }

/** Doubles the delay on each retry, capped at [max]. */
fun exponential(max: Duration): (Duration) -> Duration = { minOf(it * 2, max) }
