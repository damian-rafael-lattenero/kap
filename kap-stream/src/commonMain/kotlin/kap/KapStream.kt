package kap

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
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
    ): KapStream<B> = KapStream(flow.withBackpressure(backpressure).mapKap(concurrency, transform))

    /**
     * Keeps the elements whose [Kap] predicate returns `true`. Elements are
     * tested sequentially, so the output order is the upstream order.
     *
     * [backpressure] applies to this stage's input and defaults to exact.
     */
    fun filterKap(
        backpressure: Backpressure = Backpressure.Suspend,
        predicate: (A) -> Kap<Boolean>,
    ): KapStream<A> = KapStream(flow.withBackpressure(backpressure).filterKap(predicate))

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
    ): KapStream<B> = KapStream(flow.withBackpressure(backpressure).mapKapOrdered(concurrency, transform))

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
