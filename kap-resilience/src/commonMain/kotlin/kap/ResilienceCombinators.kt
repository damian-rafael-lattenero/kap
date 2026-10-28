package kap
import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.TimeSource
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.CancellationException

// ── timeoutRace: parallel timeout with eager fallback ───────────────────

/**
 * Races this computation against a [fallback] that starts immediately.
 * If this computation does not complete within [duration], the fallback
 * result is used. Unlike [timeout] with a fallback computation, the
 * fallback runs **from the start** in parallel — so total time is
 * `max(min(this, duration), fallback)` instead of `duration + fallback`.
 *
 * ```
 * Kap { fetchFromSlowService() }
 *     .timeoutRace(200.milliseconds, Kap { fetchFromCache() })
 * ```
 */
fun <A> Kap<A>.timeoutRace(duration: Duration, fallback: Kap<A>): Kap<A> = Kap {
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
 * Kap { fetchUser() }.retry(policy)
 * ```
 *
 * @param schedule composable retry policy
 * @param onRetry optional callback before each retry
 */
fun <A> Kap<A>.retry(
    schedule: Schedule<Throwable>,
    onRetry: suspend (attempt: Int, error: Throwable, nextDelay: Duration) -> Unit = { _, _, _ -> },
): Kap<A> = Kap {
    var attempt = 0
    lateinit var lastError: Throwable
    while (true) {
        try {
            return@Kap with(this@retry.isolated()) { execute() }
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
fun <A> Kap<A>.retry(
    scheduleFactory: () -> Schedule<Throwable>,
    onRetry: suspend (attempt: Int, error: Throwable, nextDelay: Duration) -> Unit = { _, _, _ -> },
): Kap<A> = retry(scheduleFactory(), onRetry)

// ── retryOrElse: fallback instead of throw on exhaustion ────────────

/**
 * Retries this computation according to the given [schedule].
 * When retries are exhausted, calls [orElse] with the last error instead of throwing.
 *
 * ```
 * val policy = Schedule.times<Throwable>(3) and Schedule.exponential(100.milliseconds)
 * Kap { fetchUser() }
 *     .retryOrElse(policy) { err -> User.cached() }
 * ```
 *
 * @param schedule composable retry policy
 * @param orElse fallback invoked with the last error when the schedule says [Schedule.Decision.Done]
 */
fun <A> Kap<A>.retryOrElse(
    schedule: Schedule<Throwable>,
    orElse: suspend (Throwable) -> A,
): Kap<A> = Kap {
    var attempt = 0
    lateinit var lastError: Throwable
    while (true) {
        try {
            return@Kap with(this@retryOrElse.isolated()) { execute() }
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
 * val (user, attempts, totalDelay) = Kap { fetchUser() }
 *     .retryWithResult(Schedule.times<Throwable>(3) and Schedule.exponential(100.milliseconds))
 *     .evalGraph()
 * logger.info("Fetched user after $attempts retries (${totalDelay} delay)")
 * ```
 */
fun <A> Kap<A>.retryWithResult(
    schedule: Schedule<Throwable>,
): Kap<RetryResult<A>> = Kap {
    var attempt = 0
    var totalDelay = Duration.ZERO
    lateinit var lastError: Throwable
    while (true) {
        try {
            val value = with(this@retryWithResult.isolated()) { execute() }
            return@Kap RetryResult(value, attempt, totalDelay)
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

/**
 * Repeats this computation according to [schedule], returning the value of
 * the LAST completed run. The schedule decides, from the attempt number and
 * the last value, whether to continue (and after what delay) or to stop:
 * cadence, jitter, backoff, `withMaxDuration` and the stop condition are all
 * policy, not code.
 *
 * `retry` composes per iteration: `.retry(policy).repeat(schedule)` retries
 * each run; `.repeat(schedule).retry(policy)` retries the whole loop's final
 * failure. Cancellation between iterations is respected by law.
 *
 * ```
 * Kap { poll() }
 *     .retry(Schedule.times(2))
 *     .repeat(Schedule.spaced(500.milliseconds))   // forever, every 500 ms
 * ```
 */
fun <A> Kap<A>.repeat(schedule: Schedule<A>): Kap<A> = Kap {
    var attempt = 0
    var value = with(this@repeat.isolated()) { execute() }
    while (currentCoroutineContext().isActive) {
        val decision = schedule.decide(attempt, value)
        when (decision) {
            is Schedule.Decision.Continue -> {
                if (decision.delay > Duration.ZERO) delay(decision.delay)
                attempt += 1
                value = with(this@repeat.isolated()) { execute() }
            }
            is Schedule.Decision.Done -> return@Kap value
        }
    }
    throw CancellationException("repeat cancelled between iterations")
}

/** Repeats while the last value satisfies [predicate], with no delay between runs. */
fun <A> Kap<A>.repeatWhile(predicate: (A) -> Boolean): Kap<A> =
    repeat(Schedule.doWhile(predicate))

/** Repeats until the last value satisfies [predicate], with no delay between runs. */
fun <A> Kap<A>.repeatUntil(predicate: (A) -> Boolean): Kap<A> =
    repeat(Schedule.doUntil(predicate))

/**
 * Polls [condition] every [every] until it holds or [upTo] elapses; the
 * result is the LAST observation, so `true` means "it happened" and `false`
 * means "time ran out". Nothing but schedule algebra: `doUntil { it } and
 * spaced(every) and withMaxDuration(upTo)`. The test-suite idiom
 * ("await until the counter reaches N") as a denotation.
 *
 * ```
 * val landed = Kap.awaitUntil(every = 250.milliseconds, upTo = 30.seconds) { status().dbEvents >= 6 }
 *     .evalGraph()
 * check(landed) { "events never landed" }
 * ```
 */
fun Kap.Companion.awaitUntil(
    every: Duration,
    upTo: Duration,
    timeSource: TimeSource = TimeSource.Monotonic,
    condition: suspend () -> Boolean,
): Kap<Boolean> = Kap { condition() }.repeat(
    Schedule.doUntil<Boolean> { it } and Schedule.spaced<Boolean>(every).withMaxDuration(upTo, timeSource),
)
