package applicative

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.time.Duration

// ── timeoutRace: parallel timeout with eager fallback ───────────────────

/**
 * Races this computation against a [fallback] that starts immediately.
 * If this computation does not complete within [duration], the fallback
 * result is used. Unlike [timeout] with a fallback computation, the
 * fallback runs **from the start** in parallel — so total time is
 * `max(min(this, duration), fallback)` instead of `duration + fallback`.
 *
 * ```
 * Effect { fetchFromSlowService() }
 *     .timeoutRace(200.milliseconds, Effect { fetchFromCache() })
 * ```
 */
fun <A> Effect<A>.timeoutRace(duration: Duration, fallback: Effect<A>): Effect<A> = Effect {
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
 * val policy = Schedule.times<Throwable>(3) and Schedule.exponential(100.milliseconds)
 * Effect { fetchUser() }.retry(policy)
 * ```
 *
 * @param schedule composable retry policy
 * @param onRetry optional callback before each retry
 */
fun <A> Effect<A>.retry(
    schedule: Schedule<Throwable>,
    onRetry: suspend (attempt: Int, error: Throwable, nextDelay: Duration) -> Unit = { _, _, _ -> },
): Effect<A> = Effect {
    var attempt = 0
    lateinit var lastError: Throwable
    while (true) {
        try {
            return@Effect with(this@retry) { execute() }
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            lastError = e
            when (val decision = schedule.decide(attempt, e)) {
                is Schedule.Decision.Continue -> {
                    onRetry(attempt + 1, e, decision.delay)
                    if (decision.delay > Duration.ZERO) delay(decision.delay)
                    attempt++
                }
                is Schedule.Decision.Done -> break
            }
        }
    }
    throw lastError
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
fun <A> Effect<A>.retry(
    scheduleFactory: () -> Schedule<Throwable>,
    onRetry: suspend (attempt: Int, error: Throwable, nextDelay: Duration) -> Unit = { _, _, _ -> },
): Effect<A> = retry(scheduleFactory(), onRetry)

// ── retryOrElse: fallback instead of throw on exhaustion ────────────

/**
 * Retries this computation according to the given [schedule].
 * When retries are exhausted, calls [orElse] with the last error instead of throwing.
 *
 * ```
 * val policy = Schedule.times<Throwable>(3) and Schedule.exponential(100.milliseconds)
 * Effect { fetchUser() }
 *     .retryOrElse(policy) { err -> User.cached() }
 * ```
 *
 * @param schedule composable retry policy
 * @param orElse fallback invoked with the last error when the schedule says [Schedule.Decision.Done]
 */
fun <A> Effect<A>.retryOrElse(
    schedule: Schedule<Throwable>,
    orElse: suspend (Throwable) -> A,
): Effect<A> = Effect {
    var attempt = 0
    lateinit var lastError: Throwable
    while (true) {
        try {
            return@Effect with(this@retryOrElse) { execute() }
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            lastError = e
            when (val decision = schedule.decide(attempt, e)) {
                is Schedule.Decision.Continue -> {
                    if (decision.delay > Duration.ZERO) delay(decision.delay)
                    attempt++
                }
                is Schedule.Decision.Done -> break
            }
        }
    }
    orElse(lastError)
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
 *     Effect { fetchUser() }
 *         .retryWithResult(Schedule.times<Throwable>(3) and Schedule.exponential(100.milliseconds))
 * }
 * logger.info("Fetched user after $attempts retries (${totalDelay} delay)")
 * ```
 */
fun <A> Effect<A>.retryWithResult(
    schedule: Schedule<Throwable>,
): Effect<RetryResult<A>> = Effect {
    var attempt = 0
    var totalDelay = Duration.ZERO
    lateinit var lastError: Throwable
    while (true) {
        try {
            val value = with(this@retryWithResult) { execute() }
            return@Effect RetryResult(value, attempt, totalDelay)
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            lastError = e
            when (val decision = schedule.decide(attempt, e)) {
                is Schedule.Decision.Continue -> {
                    totalDelay += decision.delay
                    if (decision.delay > Duration.ZERO) delay(decision.delay)
                    attempt++
                }
                is Schedule.Decision.Done -> break
            }
        }
    }
    throw lastError
}
