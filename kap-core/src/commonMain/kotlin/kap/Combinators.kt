package kap

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
fun <A> Kap<A>.timeout(duration: Duration): Kap<A> = Kap {
    withTimeout(duration) { with(this@timeout) { execute() } }
}

/**
 * Returns [default] if this computation does not complete within [duration].
 *
 * **Null-safety:** Unlike raw [withTimeoutOrNull], this correctly handles
 * computations that return `null` as a valid value — a `null` result is never
 * confused with a timeout.
 */
fun <A> Kap<A>.timeout(duration: Duration, default: A): Kap<A> = Kap {
    var holder: ValueHolder<A>? = null
    withTimeoutOrNull(duration) {
        holder = ValueHolder(with(this@timeout) { execute() })
    }
    val h = holder
    if (h != null) h.value else default
}

/**
 * Runs [fallback] if this computation does not complete within [duration].
 *
 * **Null-safety:** Unlike raw [withTimeoutOrNull], this correctly handles
 * computations that return `null` as a valid value — a `null` result is never
 * confused with a timeout.
 */
fun <A> Kap<A>.timeout(duration: Duration, fallback: Kap<A>): Kap<A> = Kap {
    var holder: ValueHolder<A>? = null
    withTimeoutOrNull(duration) {
        holder = ValueHolder(with(this@timeout) { execute() })
    }
    val h = holder
    if (h != null) h.value else with(fallback) { execute() }
}

// ── recover ──────────────────────────────────────────────────────────────

/**
 * Catches non-cancellation exceptions and maps them to a recovery value.
 *
 * [CancellationException] is never caught — structured concurrency
 * cancellation always propagates.
 */
inline fun <A> Kap<A>.recover(crossinline f: suspend (Throwable) -> A): Kap<A> = Kap {
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
inline fun <A> Kap<A>.recoverWith(crossinline f: suspend (Throwable) -> Kap<A>): Kap<A> = Kap {
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
infix fun <A> Kap<A>.fallback(other: Kap<A>): Kap<A> =
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
fun <A> Kap<A>.retry(
    maxAttempts: Int,
    delay: Duration = Duration.ZERO,
    backoff: (Duration) -> Duration = { it },
    shouldRetry: (Throwable) -> Boolean = { true },
    onRetry: suspend (attempt: Int, error: Throwable, nextDelay: Duration) -> Unit = { _, _, _ -> },
): Kap<A> {
    require(maxAttempts >= 1) { "maxAttempts must be >= 1, was $maxAttempts" }
    return Kap {
        var currentDelay = delay
        var lastException: Throwable? = null
        repeat(maxAttempts) { attempt ->
            try {
                return@Kap with(this@retry) { execute() }
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
 * Kap { fetchUser(id) }
 *     .ensure({ InactiveUserException(id) }) { it.isActive }
 *     .andThen { user -> buildDashboard(user) }
 * ```
 */
fun <A> Kap<A>.ensure(
    error: () -> Throwable,
    predicate: (A) -> Boolean,
): Kap<A> = Kap {
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
 * Kap { fetchUser(id) }
 *     .ensureNotNull({ ProfileMissing(id) }) { it.profile }
 *     .andThen { profile -> loadPreferences(profile) }
 * ```
 */
fun <A, B : Any> Kap<A>.ensureNotNull(
    error: () -> Throwable,
    extract: (A) -> B?,
): Kap<B> = Kap {
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
 *     Kap { fetchFromPrimary() }
 *         .orElse(Kap { fetchFromReplica() })
 *         .orElse(Kap { User.cached() })
 * }
 * ```
 */
infix fun <A> Kap<A>.orElse(other: Kap<A>): Kap<A> = Kap {
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
 *         Kap { fetchFromPrimary() },
 *         Kap { fetchFromSecondary() },
 *         Kap { fetchFromCache() },
 *     )
 * }
 * ```
 */
fun <A> firstSuccessOf(vararg computations: Kap<A>): Kap<A> {
    require(computations.isNotEmpty()) { "firstSuccessOf requires at least one computation" }
    if (computations.size == 1) return computations[0]
    return Kap {
        val errors = mutableListOf<Throwable>()
        for (c in computations) {
            try {
                return@Kap with(c) { execute() }
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
fun <A> Iterable<Kap<A>>.firstSuccess(): Kap<A> =
    firstSuccessOf(*toList().toTypedArray())

// ── backoff strategies ───────────────────────────────────────────────────

/** Doubles the delay on each retry. */
val exponential: (Duration) -> Duration = { it * 2 }

/** Doubles the delay on each retry, capped at [max]. */
fun exponential(max: Duration): (Duration) -> Duration = { minOf(it * 2, max) }
