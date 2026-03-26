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
fun <A> Effect<A>.timeout(duration: Duration): Effect<A> = Effect {
    withTimeout(duration) { with(this@timeout) { execute() } }
}

/**
 * Returns [default] if this computation does not complete within [duration].
 *
 * **Null-safety:** Unlike raw [withTimeoutOrNull], this correctly handles
 * computations that return `null` as a valid value — a `null` result is never
 * confused with a timeout.
 */
fun <A> Effect<A>.timeout(duration: Duration, default: A): Effect<A> = Effect {
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
fun <A> Effect<A>.timeout(duration: Duration, fallback: Effect<A>): Effect<A> = Effect {
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
inline fun <A> Effect<A>.recover(crossinline f: suspend (Throwable) -> A): Effect<A> = Effect {
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
inline fun <A> Effect<A>.recoverWith(crossinline f: suspend (Throwable) -> Effect<A>): Effect<A> = Effect {
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
infix fun <A> Effect<A>.fallback(other: Effect<A>): Effect<A> =
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
fun <A> Effect<A>.retry(
    maxAttempts: Int,
    delay: Duration = Duration.ZERO,
    backoff: (Duration) -> Duration = { it },
    shouldRetry: (Throwable) -> Boolean = { true },
    onRetry: suspend (attempt: Int, error: Throwable, nextDelay: Duration) -> Unit = { _, _, _ -> },
): Effect<A> {
    require(maxAttempts >= 1) { "maxAttempts must be >= 1, was $maxAttempts" }
    return Effect {
        var currentDelay = delay
        var lastException: Throwable? = null
        repeat(maxAttempts) { attempt ->
            try {
                return@Effect with(this@retry) { execute() }
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

// ── ensure / ensureNotNull ───────────────────────────────────────────────

/**
 * Validates the result of this computation against [predicate].
 * If the predicate fails, throws the exception produced by [error].
 *
 * Useful for short-circuit guards inside computation chains:
 *
 * ```
 * Effect { fetchUser(id) }
 *     .ensure({ InactiveUserException(id) }) { it.isActive }
 *     .andThen { user -> buildDashboard(user) }
 * ```
 */
fun <A> Effect<A>.ensure(
    error: () -> Throwable,
    predicate: (A) -> Boolean,
): Effect<A> = Effect {
    val a = with(this@ensure) { execute() }
    if (predicate(a)) a else throw error()
}

/**
 * Extracts a non-null value from the result using [extract].
 * If the extracted value is null, throws the exception produced by [error].
 *
 * Avoids nested null checks in computation chains:
 *
 * ```
 * Effect { fetchUser(id) }
 *     .ensureNotNull({ ProfileMissing(id) }) { it.profile }
 *     .andThen { profile -> loadPreferences(profile) }
 * ```
 */
fun <A, B : Any> Effect<A>.ensureNotNull(
    error: () -> Throwable,
    extract: (A) -> B?,
): Effect<B> = Effect {
    val a = with(this@ensureNotNull) { execute() }
    extract(a) ?: throw error()
}

// ── alternative / firstSuccessOf ─────────────────────────────────────────

/**
 * Tries this computation; if it fails (non-cancellation), tries [other] instead.
 *
 * Unlike [race] which runs both concurrently, [orElse] is **sequential** —
 * the fallback only starts if the primary fails. This is useful when the
 * fallback is expensive and should only run as a last resort.
 *
 * [CancellationException] always propagates — never falls through to [other].
 *
 * ```
 * val user = Async {
 *     Effect { fetchFromPrimary() }
 *         .orElse(Effect { fetchFromReplica() })
 *         .orElse(Effect { User.cached() })
 * }
 * ```
 */
infix fun <A> Effect<A>.orElse(other: Effect<A>): Effect<A> = Effect {
    try {
        with(this@orElse) { execute() }
    } catch (e: Throwable) {
        if (e is CancellationException) throw e
        with(other) { execute() }
    }
}

/**
 * Tries each computation sequentially; returns the first to succeed.
 * If all fail, throws the last exception with prior failures as suppressed.
 *
 * Unlike [raceN] which runs all concurrently, [firstSuccessOf] is **sequential** —
 * each fallback only starts after the previous one fails.
 *
 * [CancellationException] always propagates — never falls through to the next.
 *
 * ```
 * val data = Async {
 *     firstSuccessOf(
 *         Effect { fetchFromPrimary() },
 *         Effect { fetchFromSecondary() },
 *         Effect { fetchFromCache() },
 *     )
 * }
 * ```
 */
fun <A> firstSuccessOf(vararg computations: Effect<A>): Effect<A> {
    require(computations.isNotEmpty()) { "firstSuccessOf requires at least one computation" }
    if (computations.size == 1) return computations[0]
    return Effect {
        val errors = mutableListOf<Throwable>()
        for (c in computations) {
            try {
                return@Effect with(c) { execute() }
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                errors.add(e)
            }
        }
        val primary = errors.last()
        errors.dropLast(1).forEach { primary.addSuppressed(it) }
        throw primary
    }
}

/**
 * Tries each computation in this collection sequentially; returns the first to succeed.
 */
fun <A> Iterable<Effect<A>>.firstSuccess(): Effect<A> =
    firstSuccessOf(*toList().toTypedArray())

// ── backoff strategies ───────────────────────────────────────────────────

/** Doubles the delay on each retry. */
val exponential: (Duration) -> Duration = { it * 2 }

/** Doubles the delay on each retry, capped at [max]. */
fun exponential(max: Duration): (Duration) -> Duration = { minOf(it * 2, max) }
