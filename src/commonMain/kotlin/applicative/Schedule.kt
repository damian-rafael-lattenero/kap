package applicative

import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * A composable retry policy that decides whether to continue and how long to wait.
 *
 * Schedules are descriptions -- they don't execute anything. Combine them with
 * [and] (both must agree) or [or] (either can continue), then pass to
 * [Computation.retry].
 *
 * ```
 * val policy = Schedule.recurs<Throwable>(5) and
 *     Schedule.exponential(100.milliseconds) and
 *     Schedule.doWhile { it is IOException }
 *
 * Computation { fetchUser() }.retry(policy)
 * ```
 */
class Schedule<A>(
    @PublishedApi internal val decide: (attempt: Int, value: A) -> Decision,
) {
    sealed class Decision {
        data class Continue(val delay: Duration) : Decision()
        data object Done : Decision()
    }

    companion object {
        /** Retry up to [n] times (total attempts = n + 1 including the first). */
        fun <A> recurs(n: Int): Schedule<A> = Schedule { attempt, _ ->
            if (attempt < n) Decision.Continue(ZERO) else Decision.Done
        }

        /** Retry with a fixed delay between attempts, unlimited count. */
        fun <A> spaced(duration: Duration): Schedule<A> = Schedule { _, _ ->
            Decision.Continue(duration)
        }

        /** Exponential backoff starting from [base], multiplied by [factor] each retry, capped at [max]. */
        fun <A> exponential(
            base: Duration,
            factor: Double = 2.0,
            max: Duration = Duration.INFINITE,
        ): Schedule<A> = Schedule { attempt, _ ->
            var delay = base
            repeat(attempt) { delay = minOf(delay * factor, max) }
            Decision.Continue(delay)
        }

        /** Continue while the predicate holds. */
        fun <A> doWhile(predicate: (A) -> Boolean): Schedule<A> = Schedule { _, a ->
            if (predicate(a)) Decision.Continue(ZERO) else Decision.Done
        }

        /**
         * Retry forever with no delay. Combine with other schedules for
         * backoff or time limits:
         *
         * ```
         * // Infinite retries with exponential backoff, capped at 30 seconds total
         * Schedule.forever<Throwable>()
         *     .and(Schedule.exponential(100.milliseconds))
         *     .withMaxDuration(30.seconds)
         * ```
         */
        fun <A> forever(): Schedule<A> = Schedule { _, _ ->
            Decision.Continue(ZERO)
        }

        /**
         * Linear backoff starting from [base], capped at [max].
         *
         * Produces delays: base, 2*base, 3*base, 4*base, ...
         *
         * Linear backoff grows steadily, making it a good middle ground
         * between fixed spacing and exponential growth.
         */
        fun <A> linear(
            base: Duration,
            max: Duration = Duration.INFINITE,
        ): Schedule<A> = Schedule { attempt, _ ->
            val delay = minOf(base * (attempt + 1), max)
            Decision.Continue(delay)
        }

        /**
         * Fibonacci backoff starting from [base], capped at [max].
         *
         * Produces delays: base, base, 2*base, 3*base, 5*base, 8*base, ...
         *
         * Fibonacci backoff ramps up more gently than exponential, making it
         * suitable for services that recover gradually.
         */
        fun <A> fibonacci(
            base: Duration,
            max: Duration = Duration.INFINITE,
        ): Schedule<A> {
            return Schedule { attempt, _ ->
                var a = base
                var b = base
                repeat(attempt) {
                    val next = a + b
                    a = b
                    b = next
                }
                Decision.Continue(minOf(a, max))
            }
        }
    }

    /**
     * Both schedules must agree to continue. Uses the maximum delay.
     * If either says [Decision.Done], the result is [Decision.Done].
     */
    infix fun and(other: Schedule<A>): Schedule<A> = Schedule { attempt, a ->
        val d1 = this@Schedule.decide(attempt, a)
        val d2 = other.decide(attempt, a)
        if (d1 is Decision.Continue && d2 is Decision.Continue)
            Decision.Continue(maxOf(d1.delay, d2.delay))
        else Decision.Done
    }

    /**
     * Adds random jitter to delays, spreading retry storms across time.
     *
     * Each delay is multiplied by a random factor in the range
     * `[1 - factor, 1 + factor]`. The default [factor] of `0.5` gives ±50% spread.
     *
     * Essential for production retry policies to prevent thundering herd:
     * ```
     * Schedule.exponential<Throwable>(100.milliseconds).jittered()
     * // Delays: ~100ms, ~200ms, ~400ms... each ±50% random
     * ```
     *
     * @param factor jitter spread factor in `[0.0, 1.0]`. Default `0.5`.
     * @param random random source, injectable for testing.
     */
    fun jittered(factor: Double = 0.5, random: Random = Random): Schedule<A> {
        require(factor in 0.0..1.0) { "jitter factor must be in [0.0, 1.0], was $factor" }
        return Schedule { attempt, a ->
            when (val d = this@Schedule.decide(attempt, a)) {
                is Decision.Continue -> {
                    val jitter = 1.0 - factor + random.nextDouble() * factor * 2.0
                    Decision.Continue(d.delay * jitter)
                }
                is Decision.Done -> Decision.Done
            }
        }
    }

    /**
     * Limits the total time across all retries. Once the elapsed time since
     * the first [decide] call exceeds [maxDuration], the schedule stops.
     *
     * If the next delay would push the total past [maxDuration], the schedule
     * returns [Decision.Done] instead of overshooting.
     *
     * **Note:** This creates a stateful schedule — each instance tracks its own
     * start time. Create a new instance for each retry invocation if reuse is needed.
     *
     * ```
     * val policy = Schedule.exponential<Throwable>(100.milliseconds)
     *     .withMaxDuration(5.seconds)
     * // Stops retrying after 5 seconds total, regardless of attempt count.
     * ```
     */
    fun withMaxDuration(
        maxDuration: Duration,
        timeSource: TimeSource = TimeSource.Monotonic,
    ): Schedule<A> {
        var startMark: TimeMark? = null
        return Schedule { attempt, a ->
            val mark = startMark ?: timeSource.markNow().also { startMark = it }
            val elapsed = mark.elapsedNow()
            if (elapsed >= maxDuration) Decision.Done
            else when (val d = this@Schedule.decide(attempt, a)) {
                is Decision.Continue -> {
                    val remaining = maxDuration - elapsed
                    if (d.delay > remaining) Decision.Done else d
                }
                is Decision.Done -> Decision.Done
            }
        }
    }

    /**
     * Accumulates a value across retry attempts by folding each input with [combine].
     *
     * The accumulated value is available via the [Schedule.Decision] but does not
     * change retry behavior — this schedule delegates decisions to the original.
     * Useful for collecting all errors, building a retry log, or counting specific
     * failure types.
     *
     * ```
     * val countIOErrors = Schedule.recurs<Throwable>(5)
     *     .fold(0) { count, err -> if (err is IOException) count + 1 else count }
     * ```
     *
     * @param initial starting accumulator value
     * @param combine function to merge each input into the accumulator
     */
    fun <B> fold(initial: B, combine: (B, A) -> B): Schedule<A> {
        var acc = initial
        return Schedule { attempt, a ->
            acc = combine(acc, a)
            this@Schedule.decide(attempt, a)
        }
    }

    /**
     * Either schedule can continue. Uses the minimum delay.
     * Only [Decision.Done] when both say done.
     */
    infix fun or(other: Schedule<A>): Schedule<A> = Schedule { attempt, a ->
        val d1 = this@Schedule.decide(attempt, a)
        val d2 = other.decide(attempt, a)
        when {
            d1 is Decision.Continue && d2 is Decision.Continue ->
                Decision.Continue(minOf(d1.delay, d2.delay))
            d1 is Decision.Continue -> d1
            d2 is Decision.Continue -> d2
            else -> Decision.Done
        }
    }
}
