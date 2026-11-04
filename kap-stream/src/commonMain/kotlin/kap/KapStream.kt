package kap

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlin.time.Duration.Companion.ZERO

/**
 * A backpressured stream of values whose stages are [Kap]s (SPEC-5.0
 * milestone 5.0). `KapStream` is a thin, lawful layer over Kotlin flows — it
 * wraps the runtime, never reimplements it — adding the two things flows leave
 * implicit: **the backpressure policy is a named value at every stage**, and
 * every transformation is a [Kap] with the error and cancellation semantics of
 * the rest of KAP.
 *
 * The default backpressure at every stage is [Backpressure.Suspend]: exact,
 * nothing is ever dropped. Every other policy is explicit and documented for
 * what it drops ([Backpressure]).
 *
 * ```
 * KapStream.emitted(Kap { bus.read() })
 *     .every(Schedule.spaced(500.milliseconds))
 *     .filterKap { event -> Kap { !event.isHeartbeat } }
 *     .mapKap(concurrency = 4, backpressure = Backpressure.Buffer(64)) { event -> Kap { enrich(event) } }
 *     .drainTo(outbox)
 * ```
 *
 * Streams are cold: nothing runs until a sink ([collectAsKap], [drainTo]) or
 * [asFlow] collects them, and collection lives in the caller's structured
 * scope — cancellation propagates to the source, no stream escapes.
 */
// Stream operators are a wide surface by nature; one type keeps the algebra discoverable.
@Suppress("TooManyFunctions")
class KapStream<out A> internal constructor(
    internal val flow: Flow<A>,
) {
    /**
     * The escape hatch: this stream as a plain [Flow]. Values are produced with
     * whatever backpressure the stream was built with; the caller's collection
     * supplies the downstream demand. Use it to plug into existing Flow code
     * without re-wrapping.
     */
    fun asFlow(): Flow<A> = flow

    /**
     * Applies an explicit backpressure policy to everything upstream of this
     * point. Equivalent to the named sugars [buffer], [conflate], [dropOldest],
     * [dropLatest] and [latest].
     */
    fun withBackpressure(strategy: Backpressure): KapStream<A> = KapStream(flow.withBackpressure(strategy))

    /**
     * Decouples producer and consumer with [capacity] slots. **Exact**: nothing
     * is dropped; the producer suspends only when the buffer is full.
     */
    fun buffer(capacity: Int = Channel.BUFFERED): KapStream<A> =
        withBackpressure(Backpressure.Buffer(capacity))

    /**
     * Keeps only the latest element while the consumer is busy; superseded
     * elements are dropped. Never drops the element buffered at completion.
     * Same as `latest(1)`.
     */
    fun conflate(): KapStream<A> = withBackpressure(Backpressure.Conflate)

    /**
     * A [capacity]-slot buffer that drops the **oldest** buffered element when
     * a new one arrives and the buffer is full: the freshest data survives, the
     * producer never waits.
     */
    fun dropOldest(capacity: Int = 1): KapStream<A> = withBackpressure(Backpressure.DropOldest(capacity))

    /**
     * A [capacity]-slot buffer that drops the **incoming** element when the
     * buffer is full: the oldest data survives, the producer never waits.
     */
    fun dropLatest(capacity: Int = 1): KapStream<A> = withBackpressure(Backpressure.DropLatest(capacity))

    /** Keeps the last [n] elements; everything older is dropped. Exact for `n` slots of slack. */
    fun latest(n: Int = 1): KapStream<A> = withBackpressure(Backpressure.Latest(n))

    /**
     * Maps every element through a [Kap], with at most [concurrency] elements
     * in flight. With `concurrency > 1` results are emitted in **completion
     * order**, not upstream order — use [mapKapOrdered] when position matters.
     *
     * [backpressure] applies to this stage's input (the strategy is named at
     * the stage, not global); it defaults to exact ([Backpressure.Suspend]).
     */
    fun <B> mapKap(
        concurrency: Int = 1,
        backpressure: Backpressure = Backpressure.Suspend,
        transform: (A) -> Kap<B>,
    ): KapStream<B> {
        require(concurrency >= 1) { "concurrency must be >= 1, was $concurrency" }
        val source = flow.withBackpressure(backpressure)
        return KapStream(
            // Sequential fast path: one isolation scope per stage, not per element
            // (elements are strictly sequential, so there is nothing to isolate
            // them from; a Kap-per-element scope is measurable overhead).
            if (concurrency == 1) source.mapSequential(transform) else source.mapKap(concurrency, transform),
        )
    }

    /**
     * Keeps the elements whose [Kap] predicate returns `true`. Elements are
     * tested sequentially, so the output order is the upstream order.
     *
     * [backpressure] applies to this stage's input and defaults to exact.
     */
    fun filterKap(
        backpressure: Backpressure = Backpressure.Suspend,
        predicate: (A) -> Kap<Boolean>,
    ): KapStream<A> = KapStream(flow.withBackpressure(backpressure).filterSequential(predicate))

    /**
     * Like [mapKap] but **preserves the upstream order** even with
     * `concurrency > 1`: completed results are held until every earlier
     * element has been emitted. Bounded by [concurrency] in flight; memory is
     * the price of order.
     *
     * [backpressure] applies to this stage's input and defaults to exact.
     */
    fun <B> mapKapOrdered(
        concurrency: Int = 1,
        backpressure: Backpressure = Backpressure.Suspend,
        transform: (A) -> Kap<B>,
    ): KapStream<B> {
        require(concurrency >= 1) { "concurrency must be >= 1, was $concurrency" }
        val source = flow.withBackpressure(backpressure)
        return KapStream(
            if (concurrency == 1) source.mapSequential(transform) else source.mapKapOrdered(concurrency, transform),
        )
    }

    /**
     * Flat-maps every element into its own inner [KapStream] and merges the
     * inners with at most [concurrency] collected at a time (the stream
     * algebra's `flatMap`). With `concurrency = 1` the output is sequential in
     * upstream order; above it, inners interleave in completion order.
     *
     * [backpressure] applies to this stage's input and defaults to exact.
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun <B> flatMapKap(
        concurrency: Int = 1,
        backpressure: Backpressure = Backpressure.Suspend,
        transform: (A) -> KapStream<B>,
    ): KapStream<B> = KapStream(
        flow.withBackpressure(backpressure).flatMapMerge(concurrency) { value -> transform(value).flow },
    )

    /**
     * Collects the whole stream into a [List] as a [Kap]. The list is complete
     * by construction: whatever the stream did not drop per its named
     * strategies is in it, in order. Terminal: only use it on finite streams
     * (or bounded ones, e.g. via `emitted(...).every(Schedule.times(n))`).
     */
    fun collectAsKap(): Kap<List<A>> = Kap { flow.toList() }

    /**
     * Drains the stream into [channel] as a [Kap], suspending on the channel's
     * own capacity — a backpressured sink. Completion of the stream completes
     * the `Kap`; a closed channel fails it. The channel is **not** closed by
     * this sink (the caller owns its lifecycle).
     */
    fun drainTo(channel: SendChannel<A>): Kap<Unit> = Kap {
        flow.collect { channel.send(it) }
    }

    companion object {
        /** Lifts a plain [Flow] into a [KapStream], exact ([Backpressure.Suspend]) by default. */
        fun <A> from(flow: Flow<A>): KapStream<A> = KapStream(flow)

        /**
         * A polling source: [EmittedSource.every] runs [kap] on a cadence and
         * emits each value, until the schedule says `Done` or the stream is
         * cancelled.
         *
         * ```
         * KapStream.emitted(Kap { sensor.read() })
         *     .every(Schedule.spaced(1.seconds) and Schedule.doWhile { it != Reading.CLOSED })
         * ```
         */
        fun <A> emitted(kap: Kap<A>): EmittedSource<A> = EmittedSource(kap)

        /**
         * A **drain source**: polls [progress] on [schedule]'s cadence, emits
         * every report that handed work over, and completes when the schedule
         * says [Schedule.Decision.Done] — never on an internal count.
         *
         * This is the lawful home of the "drain until quiet" loop (agent-brain's
         * `drainUntilQuiet`, D3). The quiet window is a policy value, not a
         * counter in the loop: `Schedule.spaced(d) and Schedule.debounce(n) {
         * it.quiet }` polls every `d` until `n` consecutive polls come back
         * quiet, and the drain then completes exactly there.
         *
         * ```
         * // Ingest until three consecutive polls find nothing, 50 ms apart
         * KapStream.pollUntilQuiet(
         *     progress = { Progress(total = consumer.pollOnce(), quiet = count == 0) },
         *     schedule = Schedule.spaced<Progress>(50.milliseconds) and
         *         Schedule.debounce<Progress>(3) { it.quiet },
         * ).drainTo(sink)
         * ```
         *
         * ## Laws
         *
         * 1. **Work-only emission.** The stream emits exactly the reports with
         *    `quiet = false`, in poll order, one element per poll: a quiet
         *    report carries nothing and is never emitted.
         * 2. **Quiet-driven termination.** The schedule is the only stop: the
         *    stream completes when it is `Done`, and nothing is polled or
         *    emitted after that. With the canonical policy above, `Done` lands
         *    exactly on the `n`-th consecutive quiet report — the drain's
         *    quietness — so no element is emitted after the drain went quiet.
         *    A budget (`Schedule.times(n)`, `withMaxDuration(...)`) is an
         *    explicit early stop, and it is the caller's named choice.
         * 3. **Cadence is the schedule's.** The wait between two polls is
         *    exactly the `Continue` delay the schedule asks for (virtual time:
         *    exact); a `Done` waits nothing.
         * 4. **Failure propagates.** A failing poll fails the stream; each poll
         *    is an isolated run (the Isolation law), so the failure is an
         *    ordinary stream failure, never a silent retry or a cancelled
         *    collector.
         * 5. **Backpressure is named.** The source stage applies [backpressure];
         *    the default [Backpressure.Suspend] is exact — the consumer's pace
         *    throttles the polling, so a drain never runs away from its sink.
         *
         * @param progress the polling function: one call is one poll, returning
         *   what it handed over and whether the source is quiet.
         * @param schedule the cadence and the stop condition (see
         *   [Schedule.debounce] for the quiet window).
         * @param backpressure the strategy at this stage's output.
         * @return a cold stream of the polls that found work.
         */
        fun pollUntilQuiet(
            progress: suspend () -> Progress,
            schedule: Schedule<Progress>,
            backpressure: Backpressure = Backpressure.Suspend,
        ): KapStream<Progress> = KapStream(
            flow {
                var attempt = 0
                var polling = true
                while (polling) {
                    // One isolated run per poll (the Isolation law): a failure
                    // is this stream's failure, not a cancellation of the scope.
                    val report = coroutineScope { with(Kap { progress() }) { execute() } }
                    if (!report.quiet) emit(report)
                    when (val decision = schedule.decideAt(attempt, report)) {
                        is Schedule.Decision.Done -> polling = false
                        is Schedule.Decision.Continue -> {
                            if (decision.delay > ZERO) delay(decision.delay)
                            attempt += 1
                        }
                    }
                }
            }.withBackpressure(backpressure),
        )
    }
}

/**
 * Sequential map stage: every element runs through its [Kap] in the stage's
 * own isolation scope. One scope per stage (elements are strictly sequential),
 * which keeps the per-element cost to the `Kap` itself rather than a
 * `coroutineScope` allocation per element.
 */
private fun <A, B> Flow<A>.mapSequential(transform: (A) -> Kap<B>): Flow<B> = flow {
    coroutineScope {
        this@mapSequential.collect { value -> emit(with(transform(value)) { execute() }) }
    }
}

/** Sequential filter stage: same isolation-scope reasoning as [mapSequential]. */
private fun <A> Flow<A>.filterSequential(predicate: (A) -> Kap<Boolean>): Flow<A> = flow {
    coroutineScope {
        this@filterSequential.collect { value ->
            if (with(predicate(value)) { execute() }) emit(value)
        }
    }
}

/**
 * The source half of `KapStream.emitted(kap).every(schedule)`: a [Kap] polled
 * on a [Schedule]'s cadence.
 */
class EmittedSource<A> internal constructor(
    private val kap: Kap<A>,
) {
    /**
     * Emits the result of running [kap] immediately, then again after each
     * delay the [schedule] asks for, until the schedule returns
     * [Schedule.Decision.Done]. Each run is isolated (the Isolation law), so a
     * failing run fails the stream cleanly; compose `kap.retry(...)` for
     * per-tick resilience.
     *
     * The schedule receives the emitted value as its input, so policies can
     * stop on the data itself: `Schedule.doUntil { it == null }`.
     */
    fun every(schedule: Schedule<A>): KapStream<A> = KapStream(
        flow {
            var attempt = 0
            var polling = true
            while (polling) {
                val value = coroutineScope { with(kap.isolated()) { execute() } }
                emit(value)
                when (val decision = schedule.decideAt(attempt, value)) {
                    is Schedule.Decision.Done -> polling = false
                    is Schedule.Decision.Continue -> {
                        if (decision.delay > ZERO) delay(decision.delay)
                        attempt += 1
                    }
                }
            }
        },
    )
}
