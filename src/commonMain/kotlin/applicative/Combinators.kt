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

// ── retry with Schedule ──────────────────────────────────────────────────

/**
 * Retries this computation according to the given [schedule].
 *
 * ```
 * val policy = Schedule.recurs<Throwable>(3) and Schedule.exponential(100.milliseconds)
 * Computation { fetchUser() }.retry(policy)
 * ```
 *
 * @param schedule composable retry policy
 * @param onRetry optional callback before each retry
 */
fun <A> Computation<A>.retry(
    schedule: Schedule<Throwable>,
    onRetry: suspend (attempt: Int, error: Throwable, nextDelay: Duration) -> Unit = { _, _, _ -> },
): Computation<A> = Computation {
    var attempt = 0
    while (true) {
        try {
            return@Computation with(this@retry) { execute() }
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            when (val decision = schedule.decide(attempt, e)) {
                is Schedule.Decision.Continue -> {
                    onRetry(attempt + 1, e, decision.delay)
                    if (decision.delay > Duration.ZERO) delay(decision.delay)
                    attempt++
                }
                is Schedule.Decision.Done -> throw e
            }
        }
    }
    @Suppress("UNREACHABLE_CODE")
    throw IllegalStateException("unreachable")
}

/**
 * Retries this computation using a fresh [Schedule] from [scheduleFactory] each time.
 *
 * Stateful schedules (e.g., those using [Schedule.withMaxDuration] or [Schedule.fold])
 * capture mutable state. If you reuse the same instance across multiple retries, the
 * state leaks. This overload creates a fresh schedule per invocation:
 *
 * ```
 * val policyFactory = {
 *     Schedule.exponential<Throwable>(100.milliseconds)
 *         .withMaxDuration(5.seconds)
 * }
 * comp.retry(policyFactory)  // fresh timer each time
 * ```
 */
fun <A> Computation<A>.retry(
    scheduleFactory: () -> Schedule<Throwable>,
    onRetry: suspend (attempt: Int, error: Throwable, nextDelay: Duration) -> Unit = { _, _, _ -> },
): Computation<A> = retry(scheduleFactory(), onRetry)

// ── retryOrElse: fallback instead of throw on exhaustion ────────────

/**
 * Retries this computation according to the given [schedule].
 * When retries are exhausted, calls [orElse] with the last error instead of throwing.
 *
 * ```
 * val policy = Schedule.recurs<Throwable>(3) and Schedule.exponential(100.milliseconds)
 * Computation { fetchUser() }
 *     .retryOrElse(policy) { err -> User.cached() }
 * ```
 *
 * @param schedule composable retry policy
 * @param orElse fallback invoked with the last error when the schedule says [Schedule.Decision.Done]
 */
fun <A> Computation<A>.retryOrElse(
    schedule: Schedule<Throwable>,
    orElse: suspend (Throwable) -> A,
): Computation<A> = Computation {
    var attempt = 0
    while (true) {
        try {
            return@Computation with(this@retryOrElse) { execute() }
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            when (val decision = schedule.decide(attempt, e)) {
                is Schedule.Decision.Continue -> {
                    if (decision.delay > Duration.ZERO) delay(decision.delay)
                    attempt++
                }
                is Schedule.Decision.Done -> return@Computation orElse(e)
            }
        }
    }
    @Suppress("UNREACHABLE_CODE")
    throw IllegalStateException("unreachable")
}

// ── retry with result metadata ───────────────────────────────────────────

/**
 * Metadata about a successful retry execution.
 *
 * @param value the successful result
 * @param attempts number of retry attempts (0 = succeeded on first try)
 * @param totalDelay cumulative delay spent waiting between retries
 */
data class RetryResult<out A>(
    val value: A,
    val attempts: Int,
    val totalDelay: Duration,
)

/**
 * Like [retry] with a [Schedule], but returns a [RetryResult] containing
 * the successful value along with retry metadata (attempt count and total delay).
 *
 * Useful for telemetry, logging, and understanding retry behavior:
 *
 * ```
 * val (user, attempts, totalDelay) = Async {
 *     Computation { fetchUser() }
 *         .retryWithResult(Schedule.recurs<Throwable>(3) and Schedule.exponential(100.milliseconds))
 * }
 * logger.info("Fetched user after $attempts retries (${totalDelay} delay)")
 * ```
 */
fun <A> Computation<A>.retryWithResult(
    schedule: Schedule<Throwable>,
): Computation<RetryResult<A>> = Computation {
    var attempt = 0
    var totalDelay = Duration.ZERO
    while (true) {
        try {
            val value = with(this@retryWithResult) { execute() }
            return@Computation RetryResult(value, attempt, totalDelay)
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            when (val decision = schedule.decide(attempt, e)) {
                is Schedule.Decision.Continue -> {
                    totalDelay += decision.delay
                    if (decision.delay > Duration.ZERO) delay(decision.delay)
                    attempt++
                }
                is Schedule.Decision.Done -> throw e
            }
        }
    }
    @Suppress("UNREACHABLE_CODE")
    throw IllegalStateException("unreachable")
}

// ── ensure / ensureNotNull ───────────────────────────────────────────────

/**
 * Validates the result of this computation against [predicate].
 * If the predicate fails, throws the exception produced by [error].
 *
 * Useful for short-circuit guards inside computation chains:
 *
 * ```
 * Computation { fetchUser(id) }
 *     .ensure({ InactiveUserException(id) }) { it.isActive }
 *     .flatMap { user -> buildDashboard(user) }
 * ```
 */
fun <A> Computation<A>.ensure(
    error: () -> Throwable,
    predicate: (A) -> Boolean,
): Computation<A> = Computation {
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
 * Computation { fetchUser(id) }
 *     .ensureNotNull({ ProfileMissing(id) }) { it.profile }
 *     .flatMap { profile -> loadPreferences(profile) }
 * ```
 */
fun <A, B : Any> Computation<A>.ensureNotNull(
    error: () -> Throwable,
    extract: (A) -> B?,
): Computation<B> = Computation {
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
 *     Computation { fetchFromPrimary() }
 *         .orElse(Computation { fetchFromReplica() })
 *         .orElse(Computation { User.cached() })
 * }
 * ```
 */
infix fun <A> Computation<A>.orElse(other: Computation<A>): Computation<A> = Computation {
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
 *         Computation { fetchFromPrimary() },
 *         Computation { fetchFromSecondary() },
 *         Computation { fetchFromCache() },
 *     )
 * }
 * ```
 */
fun <A> firstSuccessOf(vararg computations: Computation<A>): Computation<A> {
    require(computations.isNotEmpty()) { "firstSuccessOf requires at least one computation" }
    if (computations.size == 1) return computations[0]
    return Computation {
        val errors = mutableListOf<Throwable>()
        for (c in computations) {
            try {
                return@Computation with(c) { execute() }
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
fun <A> Iterable<Computation<A>>.firstSuccess(): Computation<A> =
    firstSuccessOf(*toList().toTypedArray())

// ── backoff strategies ───────────────────────────────────────────────────

/** Doubles the delay on each retry. */
val exponential: (Duration) -> Duration = { it * 2 }

/** Doubles the delay on each retry, capped at [max]. */
fun exponential(max: Duration): (Duration) -> Duration = { minOf(it * 2, max) }
