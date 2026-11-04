package kap

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
 * [Kap.retry].
 *
 * ```
 * val policy = Schedule.times<Throwable>(5) and
 *     Schedule.exponential(100.milliseconds) and
 *     Schedule.doWhile { it is IOException }
 *
 * Kap { fetchUser() }.retry(policy)
 * ```
 *
 * ## The schedule semiring
 *
 * [and] is **multiplication** and [or] is **addition**, with delays merged as
 * `max` and `min` respectively. A [Decision.Done] behaves as the delay `+∞`
 * (an infinite wait is a stop), which makes the algebra total:
 *
 * | Law | Property |
 * |---|---|
 * | `or` associative / commutative | `(a or b) or c == a or (b or c)`, `a or b == b or a` |
 * | `and` associative / commutative | `(a and b) and c == a and (b and c)`, `a and b == b and a` |
 * | Additive identity | `a or Schedule.never() == a` |
 * | Multiplicative identity | `a and Schedule.immediate() == a` |
 * | Multiplicative annihilator | `a and Schedule.never() == Schedule.never()` |
 * | Distributivity | `a and (b or c) == (a and b) or (a and c)` |
 *
 * Because `max`/`min` on the extended non-negative reals form a distributive
 * lattice, `or`/`and` also satisfy the idempotent laws (`a or a == a`,
 * `a and a == a`). The laws are property-tested in `ScheduleLawsTest`.
 *
 * Two boundary notes: the law statements compare underlying decisions, and a
 * [Decision.Continue] carrying [Duration.INFINITE] is observationally
 * [Decision.Done] (a retry that waits forever stops). Schedules produced by
 * the factories only yield finite delays unless an explicit
 * `Duration.INFINITE` cap is passed.
 */
class Schedule<A>(
    @PublishedApi internal val decide: (attempt: Int, value: A) -> Decision,
) {
    sealed class Decision {
        data class Continue(val delay: Duration) : Decision()
        data object Done : Decision()
    }

    // A policy algebra is a wide factory surface by nature; the 4.3 delays
    // overloads (array + list) push it past the default object threshold.
    @Suppress("TooManyFunctions")
    companion object {
        /**
         * Retry up to [n] times (total attempts = n + 1 including the first).
         *
         * @param n number of retries allowed after the first attempt.
         * @return a schedule that continues for attempts `0..n-1` and is
         *   [Decision.Done] from attempt `n` on.
         */
        fun <A> times(n: Int): Schedule<A> = Schedule { attempt, _ ->
            if (attempt < n) Decision.Continue(ZERO) else Decision.Done
        }

        /**
         * Retry with a fixed delay between attempts, unlimited count.
         *
         * @param duration the wait between consecutive attempts.
         * @return a schedule that always continues with [duration].
         */
        fun <A> spaced(duration: Duration): Schedule<A> = Schedule { _, _ ->
            Decision.Continue(duration)
        }

        /**
         * A schedule that waits exactly [delays] in order: the first retry waits
         * `delays[0]`, the second `delays[1]`, and so on. Once the sequence is
         * exhausted the schedule is [Decision.Done]; an empty sequence is done
         * immediately (no retries).
         *
         * Kotlin forbids `vararg` parameters of value-class types, and
         * [Duration] is a value class, so the "literal" form takes an array —
         * pass `arrayOf(...)` or spread an existing one:
         * ```
         * Kap { fetch() }.retry(Schedule.delays(arrayOf(100.milliseconds, 2.seconds)))
         * val delays = arrayOf(100.milliseconds, 500.milliseconds)
         * Kap { fetch() }.retry(Schedule.delays(*delays))
         * // retries after 100 ms and 500 ms; the third failure is final.
         * ```
         *
         * @param delays non-negative delays, consumed in order.
         * @throws IllegalArgumentException when any delay is negative.
         */
        fun <A> delays(delays: Array<Duration>): Schedule<A> = fromDelays(delays.asList())

        /**
         * List overload of [delays] — same semantics, for a dynamic sequence of delays.
         *
         * @throws IllegalArgumentException when any delay is negative.
         */
        fun <A> delays(delays: List<Duration>): Schedule<A> = fromDelays(delays)

        private fun <A> fromDelays(delays: List<Duration>): Schedule<A> {
            require(delays.all { it >= ZERO }) { "delays must be non-negative, was $delays" }
            val snapshot = delays.toList()
            return Schedule { attempt, _ ->
                if (attempt >= snapshot.size) Decision.Done
                else Decision.Continue(snapshot[attempt])
            }
        }

        /**
         * Exponential backoff starting from [base], multiplied by [factor] each retry, capped at [max].
         *
         * @param base the delay before the first retry (must be positive to keep
         *   the schedule meaningful; `ZERO` retries immediately).
         * @param factor the multiplier applied per retry; `2.0` doubles each wait.
         * @param max the cap; a computed delay beyond it is clamped. Defaults to
         *   no cap.
         * @return a schedule producing `base, base*factor, base*factor^2, ...`
         *   capped at [max].
         */
        fun <A> exponential(
            base: Duration,
            factor: Double = 2.0,
            max: Duration = Duration.INFINITE,
        ): Schedule<A> = Schedule { attempt, _ ->
            var delay = base
            repeat(attempt) { delay = minOf(delay * factor, max) }
            Decision.Continue(delay)
        }

        /**
         * Continue while the predicate holds.
         *
         * @param predicate applied to the current input (the failure in a
         *   `retry`); continue when it returns `true`.
         * @return a schedule that retries immediately while [predicate] holds.
         */
        fun <A> doWhile(predicate: (A) -> Boolean): Schedule<A> = Schedule { _, a ->
            if (predicate(a)) Decision.Continue(ZERO) else Decision.Done
        }

        /**
         * Continue until the predicate holds. Complement of [doWhile].
         *
         * @param predicate applied to the current input; stop as soon as it
         *   returns `true`.
         * @return a schedule that retries immediately until [predicate] holds.
         */
        fun <A> doUntil(predicate: (A) -> Boolean): Schedule<A> = Schedule { _, a ->
            if (predicate(a)) Decision.Done else Decision.Continue(ZERO)
        }

        /**
         * Synonym for [doWhile] — Arrow naming compatibility.
         *
         * @param predicate applied to the current input.
         * @return the same schedule [doWhile] builds.
         */
        fun <A> whileInput(predicate: (A) -> Boolean): Schedule<A> = doWhile(predicate)

        /**
         * Synonym for [doUntil] — Arrow naming compatibility.
         *
         * @param predicate applied to the current input.
         * @return the same schedule [doUntil] builds.
         */
        fun <A> untilInput(predicate: (A) -> Boolean): Schedule<A> = doUntil(predicate)

        /**
         * The **quiet-window stop condition**: keep going while the work keeps
         * coming, and stop once [n] consecutive inputs have reported quiet.
         *
         * This is the lawful home of the "drain until quiet" loop. A polling
         * body returns what it found (a count, a `Progress`, anything [isQuiet]
         * can read); the schedule ends the loop exactly when the quiet window
         * fills. Compose it with a cadence by [and] — the cadence supplies the
         * delays, the window supplies the stop:
         *
         * ```
         * // Poll every 50 ms until three consecutive polls come back empty
         * Kap { poll() }
         *     .repeat(Schedule.spaced<Int>(50.milliseconds) and Schedule.debounce(3))
         * ```
         *
         * ## Laws
         *
         * For a fresh instance and the input sequence `a0, a1, ...`:
         *
         * | Law | Property |
         * |---|---|
         * | Stop at the filled window | [Decision.Done] exactly when the run of quiet inputs ending at `ai` is `>= n` |
         * | Reset | a non-quiet input resets the run to zero |
         * | Delay neutrality | every [Decision.Continue] carries [ZERO] |
         * | Empty window | `debounce(0, isQuiet)` decides exactly like [never] (Done at attempt 0) |
         * | Singleton window | `debounce(1, isQuiet)` decides exactly like [doUntil]`(isQuiet)` |
         *
         * Because every Continue delay is [ZERO], `cadence and debounce(n)`
         * keeps the cadence's delays exactly (`max(d, 0) == d`) and is Done
         * when either operand is: the window is a stop condition, not a clock.
         * Property-tested in `ScheduleDebounceLawsTest`.
         *
         * **Statefulness.** Unlike the pointwise factories the semiring laws
         * range over, `debounce` remembers its quiet run: the decision depends
         * on the inputs this instance has already seen, exactly like
         * [withMaxDuration]'s clock. Build one fresh instance per run and never
         * share an instance between concurrent drivers.
         *
         * @param n the window: how many consecutive quiet inputs end the loop.
         *   `0` stops immediately — the first decision is [Decision.Done].
         * @param isQuiet reads one input as "this attempt found no work".
         * @return a schedule that continues with no delay until the window fills.
         * @throws IllegalArgumentException when [n] is negative.
         */
        fun <A> debounce(n: Int, isQuiet: (A) -> Boolean): Schedule<A> {
            require(n >= 0) { "debounce needs n >= 0, was $n" }
            var quietRun = 0
            return Schedule { _, value ->
                quietRun = if (isQuiet(value)) quietRun + 1 else 0
                if (quietRun >= n) Decision.Done else Decision.Continue(ZERO)
            }
        }

        /**
         * The count shorthand of [debounce]: a poll that returns how many items
         * it handed over is quiet when it handed over nothing (`0`).
         *
         * ```
         * // Drain until three consecutive polls find nothing, then stop
         * Kap { poll() }.repeat(Schedule.spaced<Int>(50.milliseconds) and Schedule.debounce(3))
         * ```
         *
         * Same laws as [debounce]; the result is a `Schedule<Int>`, so it
         * composes with a polling body that returns an `Int` count.
         *
         * @param n the window: how many consecutive empty polls end the loop.
         * @throws IllegalArgumentException when [n] is negative.
         */
        fun debounce(n: Int): Schedule<Int> = debounce(n) { it == 0 }

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
         *
         * This is the same value as [immediate]: always
         * [Decision.Continue] with a zero delay, the multiplicative identity
         * of the schedule semiring ([and] leaves the other schedule's delays
         * untouched).
         */
        fun <A> forever(): Schedule<A> = Schedule { _, _ ->
            Decision.Continue(ZERO)
        }

        /**
         * Always [Decision.Continue] with a [ZERO] delay — the **multiplicative
         * identity** of the schedule semiring: `s and Schedule.immediate() == s`
         * and `Schedule.immediate() and s == s`.
         *
         * Same value as [forever], named for its algebraic role so a policy
         * reads as an equation: `times(5) and (maybeRetry() or immediate())`.
         */
        fun <A> immediate(): Schedule<A> = forever()

        /**
         * Always [Decision.Done] — the **additive identity** of the schedule
         * semiring: `s or Schedule.never() == s`. Combining with [and] makes
         * the whole policy [Decision.Done] (multiplicative annihilator), which
         * expresses "this branch never retries".
         *
         * ```
         * val policy = Schedule.exponential<Throwable>(100.milliseconds)
         *     .and(Schedule.never<Throwable>())   // never retries at all
         * ```
         */
        fun <A> never(): Schedule<A> = Schedule { _, _ ->
            Decision.Done
        }

        /**
         * Linear backoff starting from [base], capped at [max].
         *
         * Produces delays: base, 2*base, 3*base, 4*base, ...
         *
         * Linear backoff grows steadily, making it a good middle ground
         * between fixed spacing and exponential growth.
         *
         * @param base the delay before the first retry; each subsequent wait
         *   grows by one more [base].
         * @param max the cap; a computed delay beyond it is clamped.
         * @return a schedule producing `base * (attempt + 1)`, capped at [max].
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
         *
         * @param base the delay before the first two retries.
         * @param max the cap; a computed delay beyond it is clamped.
         * @return a schedule producing Fibonacci multiples of [base], capped at [max].
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
     * Evaluates this schedule: the decision a driver ([Kap.retry],
     * `KapStream.emitted(...).every(...)`) takes for the 0-based [attempt]
     * with the current input [value]. A schedule is a value, so inspecting it
     * is lawful — this is exactly the call the drivers make.
     *
     * ```
     * val policy = Schedule.times<Throwable>(3)
     * policy.decideAt(2, error)   // Continue(ZERO)
     * policy.decideAt(3, error)   // Done
     * ```
     *
     * @param attempt 0-based retry attempt number (the first call after the
     *   initial run is attempt `0`).
     * @param value the current input (the failure in a `retry`).
     * @return [Decision.Continue] with the wait before the next attempt, or
     *   [Decision.Done] when the schedule is exhausted.
     */
    fun decideAt(attempt: Int, value: A): Decision = decide(attempt, value)

    /**
     * Accumulates all retry inputs into a list. Specialization of [fold].
     *
     * @return a schedule that behaves exactly like this one, but remembers
     *   every input it has seen (inspectable with a second [fold] or a
     *   [decideAt]-driven test).
     */
    fun collect(): Schedule<A> = fold<List<A>>(emptyList()) { acc, a -> acc + a }


    /**
     * Combines two schedules with a custom delay merge. Both must agree to continue.
     *
     * @param other the schedule composed with this one.
     * @param f merges the two Continue delays into the combined delay.
     * @return a schedule that continues only when both operands continue at the
     *   same attempt, carrying `f(delay1, delay2)`.
     */
    fun zipWith(other: Schedule<A>, f: (Duration, Duration) -> Duration): Schedule<A> = Schedule { attempt, a ->
        val d1 = this@Schedule.decide(attempt, a)
        val d2 = other.decide(attempt, a)
        if (d1 is Decision.Continue && d2 is Decision.Continue)
            Decision.Continue(f(d1.delay, d2.delay))
        else Decision.Done
    }

    /**
     * **Multiplication** of the schedule semiring: both schedules must agree
     * to continue. Delays merge with `max` (the slower schedule sets the pace —
     * a policy is as patient as its most patient constraint). If either says
     * [Decision.Done] the result is [Decision.Done] (a [Decision.Done] is the
     * delay `+∞`, so `max` with it is `+∞`).
     *
     * Laws: associative, commutative, identity `Schedule.immediate()`,
     * annihilator `Schedule.never()`, and distributive over [or]
     * (`a and (b or c) == (a and b) or (a and c)`). Property-tested in
     * `ScheduleLawsTest`.
     *
     * ```
     * // At most 5 retries, each waiting at least 100 ms, growing ×2
     * Schedule.times<Throwable>(5) and Schedule.exponential(100.milliseconds)
     * ```
     *
     * @param other the schedule composed with this one.
     * @return a schedule that continues only while both operands continue,
     *   waiting `max(delay1, delay2)`.
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
     * @return a schedule whose Continue delays are `original * (1 ± factor)`.
     * @throws IllegalArgumentException when [factor] is outside `[0.0, 1.0]`.
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
     * Derives each Continue delay from the schedule's input: the Continue/Done
     * decision and the attempt count are unchanged, but every Continue delay is
     * replaced by `f(input)`.
     *
     * The delay becomes a function of the failure (or value) driving the
     * decision — e.g. longer waits for a rate-limit error than for a transient
     * timeout — without touching the stop condition:
     *
     * ```
     * Schedule.forever<Throwable>()
     *     .delayBy { error -> if (error is RateLimited) 30.seconds else 500.milliseconds }
     * ```
     *
     * [f] is only called when the original schedule says
     * [Decision.Continue]; a [Decision.Done] result is passed through untouched.
     * **A negative result is a programming error and fails fast** with
     * [IllegalArgumentException] instead of being silently coerced to zero.
     *
     * @param f computes the wait for the current input.
     * @return a schedule with the same Continue/Done decisions and `f`-derived delays.
     * @throws IllegalArgumentException when [f] produces a negative delay.
     */
    fun delayBy(f: (A) -> Duration): Schedule<A> = Schedule { attempt, a ->
        when (this@Schedule.decide(attempt, a)) {
            is Decision.Continue -> {
                val delay = f(a)
                require(delay >= ZERO) { "delayBy produced a negative delay: $delay" }
                Decision.Continue(delay)
            }
            is Decision.Done -> Decision.Done
        }
    }

    /**
     * Limits the total time across all retries. Once the elapsed time since
     * the first [decide] call exceeds [maxDuration], the schedule stops.
     *
     * If the next delay would push the total past [maxDuration], the schedule
     * returns [Decision.Done] instead of overshooting.
     *
     * **Statefulness warning:** This creates a stateful schedule — each instance
     * tracks its own start time. If you reuse the same instance across multiple
     * [Kap.retry] calls, the timer carries over from the first invocation.
     * Use the `retry(scheduleFactory)` overload to get a fresh schedule each time:
     * ```
     * comp.retry { Schedule.exponential<Throwable>(100.ms).withMaxDuration(5.seconds) }
     * ```
     *
     * ```
     * val policy = Schedule.exponential<Throwable>(100.milliseconds)
     *     .withMaxDuration(5.seconds)
     * // Stops retrying after 5 seconds total, regardless of attempt count.
     * ```
     *
     * @param maxDuration total wall time budget from the first decide call.
     * @param timeSource injectable time source for testing.
     * @return a schedule that is [Decision.Done] once the budget is spent.
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
     * val countIOErrors = Schedule.times<Throwable>(5)
     *     .fold(0) { count, err -> if (err is IOException) count + 1 else count }
     * ```
     *
     * @param initial starting accumulator value
     * @param combine function to merge each input into the accumulator
     * @return a schedule that behaves exactly like this one while accumulating.
     */
    fun <B> fold(initial: B, combine: (B, A) -> B): Schedule<A> {
        var acc = initial
        return Schedule { attempt, a ->
            acc = combine(acc, a)
            this@Schedule.decide(attempt, a)
        }
    }

    /**
     * **Addition** of the schedule semiring: either schedule can continue,
     * and that is the point — two independent reasons to retry add up. Delays
     * merge with `min` (the earliest retry wins; the policies race and the
     * fastest one sets the cadence). Only [Decision.Done] when both say done
     * (a [Decision.Done] is the delay `+∞`, so `min` ignores it).
     *
     * Laws: associative, commutative, identity `Schedule.never()`, and it is
     * the addition of the distributive semiring ([and] distributes over it).
     * Property-tested in `ScheduleLawsTest`.
     *
     * ```
     * // Retry 5 times *or* while the error is an IOException, whichever lasts longer
     * Schedule.times<Throwable>(5) or Schedule.doWhile { it is IOException }
     * ```
     *
     * @param other the schedule composed with this one.
     * @return a schedule that continues while either operand continues,
     *   waiting `min(delay1, delay2)`.
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
