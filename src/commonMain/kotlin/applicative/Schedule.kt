package applicative

import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO

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
