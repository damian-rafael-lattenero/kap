package kap

import kotlin.time.Duration
import kotlin.time.TimeSource

/**
 * A tiny **multiplatform metrics SPI**: counters, timers and gauges addressed by
 * name and [tags]. It is the seam an integration module (for example
 * `kap-micrometer`) implements once, so an application never hand-rolls the same
 * counters around every pump, worker or effect.
 *
 * ## The contract
 *
 * 1. **Timed operations take the CALL, not a finished duration.** [time] and
 *    [timeSuspending] run the block and measure it; callers never read a clock
 *    just to feed a meter. [record] exists for durations measured elsewhere
 *    (a frame the integration already timed).
 * 2. **The names are identities; tags are dimensions.** [increment],
 *    [record] and [gauge] receive the metric name verbatim. No implementation
 *    may fold tags into the name: the same name is the same metric for every
 *    tag combination.
 * 3. **Instrumentation never breaks the measured code.** A [Meter]
 *    implementation must not throw. A foreign implementation that can throw is
 *    wrapped once with [guarded]: the failure is caught and handed to the
 *    failure reporter — swallowed silently is not allowed — while the block's
 *    value and exception pass through untouched.
 * 4. **Timers record exactly once**, on success, failure and cancellation
 *    alike; a `CancellationException` is recorded and then rethrown.
 * 5. **Gauges are read at scrape time.** [gauge] registers a supplier; the
 *    backing registry reads it whenever a scrape happens, never at
 *    registration.
 *
 * [Noop] is the strict OFF: no registry, no clock read on the timed paths, no
 * allocation (the block runs bare), exception-transparent.
 *
 * ```
 * val meter: Meter = if (metricsEnabled) MicrometerMeter(registry) else Meter.Noop
 *
 * meter.increment("poller.cycles")
 * meter.time("brain.request") { brain.ask(prompt) }
 * meter.gauge("outbox.depth", Tags.of("lane" to "main")) { outbox.depth().toDouble() }
 * ```
 */
interface Meter {

    /**
     * Counts one occurrence of the event [name]. [tags] are dimensions of the
     * same metric, never part of its identity.
     */
    fun increment(name: String, tags: Tags = Tags.Empty)

    /**
     * Records one [duration] for the timer [name]. Use this overload for a
     * duration already measured elsewhere; for fresh work use [time].
     */
    fun record(name: String, tags: Tags = Tags.Empty, duration: Duration)

    /**
     * Registers a gauge [name] whose value is read from [supplier] at scrape
     * time. Registration happens once; the supplier is called by the registry
     * on every scrape and must return the current value (never a cached one).
     */
    fun gauge(name: String, tags: Tags = Tags.Empty, supplier: () -> Double)

    /**
     * Times [block] and records exactly one sample under [name], on success,
     * failure and cancellation alike. The block's value — or its exception —
     * is returned/rethrown untouched.
     */
    fun <T> time(name: String, tags: Tags = Tags.Empty, block: () -> T): T {
        val mark = TimeSource.Monotonic.markNow()
        try {
            return block()
        } finally {
            record(name, tags, mark.elapsedNow())
        }
    }

    /**
     * The suspending twin of [time]: the sample is recorded in `finally`, so a
     * cancelled block still records exactly once and the cancellation keeps
     * propagating.
     */
    suspend fun <T> timeSuspending(name: String, tags: Tags = Tags.Empty, block: suspend () -> T): T {
        val mark = TimeSource.Monotonic.markNow()
        try {
            return block()
        } finally {
            record(name, tags, mark.elapsedNow())
        }
    }

    /**
     * The strict OFF implementation: every tap is a no-op, [time] and
     * [timeSuspending] run the block bare (not one clock read), gauges are
     * never registered. A component wired with [Noop] is byte-for-byte the
     * uninstrumented one.
     */
    object Noop : Meter {

        override fun increment(name: String, tags: Tags) {}

        override fun record(name: String, tags: Tags, duration: Duration) {}

        override fun gauge(name: String, tags: Tags, supplier: () -> Double) {}

        override fun <T> time(name: String, tags: Tags, block: () -> T): T = block()

        override suspend fun <T> timeSuspending(name: String, tags: Tags, block: suspend () -> T): T = block()
    }

    companion object {
        /**
         * Wraps a foreign [delegate] so that a throwing meter can never break
         * the measured code: every instrumentation failure is caught and
         * reported to [onFailure] (never swallowed silently), while the
         * block's value and exception pass through untouched. A throwing
         * gauge supplier reports and reads as [Double.NaN] — the honest
         * "unknown" scrape value, not a fabricated zero.
         *
         * ```
         * val meter = Meter.guarded(MicrometerMeter(registry), log::meterFailure)
         * ```
         *
         * The reporter is the last stop on the failure path: its own failure
         * cannot be reported anywhere, so it is ignored. Keep it to logging or
         * a local counter.
         */
        fun guarded(delegate: Meter, onFailure: (MeterFailure) -> Unit): Meter = GuardedMeter(delegate, onFailure)
    }
}

/**
 * An immutable, ordered set of metric dimensions. Build one where the
 * dimensions are known (wiring time) and reuse it on the hot path — the same
 * value can be passed to every sample without allocating again.
 *
 * ```
 * private val FAILED = Tags.of("outcome" to "failed")
 *
 * meter.increment("turns.sealed", FAILED)
 * ```
 *
 * Tag keys are unique inside a value: [of] rejects duplicates because a
 * registry cannot tell two values for the same key apart.
 */
class Tags private constructor(private val entries: Array<out Pair<String, String>>) {

    /** True when this value carries no dimensions. */
    val isEmpty: Boolean get() = entries.isEmpty()

    /** The number of dimensions. */
    val size: Int get() = entries.size

    /** Applies [action] to every key/value pair, in insertion order, without allocating a map. */
    fun forEach(action: (key: String, value: String) -> Unit) {
        for ((key, value) in entries) action(key, value)
    }

    /** This value as a read-only map (for adapters and assertions). */
    fun asMap(): Map<String, String> = entries.toMap()

    override fun equals(other: Any?): Boolean = other is Tags && entries.contentEquals(other.entries)

    override fun hashCode(): Int = entries.contentHashCode()

    override fun toString(): String =
        entries.joinToString(prefix = "{", postfix = "}") { (key, value) -> "$key=$value" }

    companion object {
        /** No dimensions. */
        val Empty: Tags = Tags(emptyArray())

        /**
         * Builds a tag set from [tags], rejecting duplicate keys. The array is
         * copied, so later mutation of `tags` cannot change the value.
         */
        fun of(vararg tags: Pair<String, String>): Tags {
            if (tags.isEmpty()) return Empty
            val seen = HashSet<String>(tags.size)
            tags.forEach { (key, _) ->
                require(seen.add(key)) { "duplicate tag key '$key' in ${tags.toList()}" }
            }
            val copy: Array<out Pair<String, String>> = tags.copyOf()
            return Tags(copy)
        }
    }
}

/** The meter operation that failed — the honest "who" of a [MeterFailure]. */
enum class MeterOperation {
    INCREMENT,
    RECORD,
    GAUGE,
    GAUGE_READ,
}

/**
 * One instrumentation failure of a guarded meter: what was attempted, under
 * which name and tags, and the failure itself.
 */
data class MeterFailure(
    val operation: MeterOperation,
    val name: String,
    val tags: Tags,
    val error: Throwable,
)

/**
 * The safety net behind [Meter.guarded]: the measured code always wins, the
 * failure always goes to the reporter.
 */
private class GuardedMeter(
    private val delegate: Meter,
    private val onFailure: (MeterFailure) -> Unit,
) : Meter {

    override fun increment(name: String, tags: Tags) {
        guarded(MeterOperation.INCREMENT, name, tags) { delegate.increment(name, tags) }
    }

    override fun record(name: String, tags: Tags, duration: Duration) {
        guarded(MeterOperation.RECORD, name, tags) { delegate.record(name, tags, duration) }
    }

    @Suppress("TooGenericExceptionCaught") // the scrape must survive every supplier failure
    override fun gauge(name: String, tags: Tags, supplier: () -> Double) {
        guarded(MeterOperation.GAUGE, name, tags) {
            delegate.gauge(name, tags) {
                try {
                    supplier()
                } catch (t: Throwable) {
                    report(MeterFailure(MeterOperation.GAUGE_READ, name, tags, t))
                    Double.NaN
                }
            }
        }
    }

    override fun <T> time(name: String, tags: Tags, block: () -> T): T {
        val mark = TimeSource.Monotonic.markNow()
        try {
            return block()
        } finally {
            guarded(MeterOperation.RECORD, name, tags) { delegate.record(name, tags, mark.elapsedNow()) }
        }
    }

    override suspend fun <T> timeSuspending(name: String, tags: Tags, block: suspend () -> T): T {
        val mark = TimeSource.Monotonic.markNow()
        try {
            return block()
        } finally {
            guarded(MeterOperation.RECORD, name, tags) { delegate.record(name, tags, mark.elapsedNow()) }
        }
    }

    // The measured code's own exception is never routed here: this wraps the
    // instrumentation call only, and a CancellationException raised by the
    // delegate is instrumentation failure like any other.
    @Suppress("TooGenericExceptionCaught")
    private inline fun guarded(operation: MeterOperation, name: String, tags: Tags, action: () -> Unit) {
        try {
            action()
        } catch (t: Throwable) {
            report(MeterFailure(operation, name, tags, t))
        }
    }

    // The reporter is the last stop: it has nowhere to report its own failure.
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun report(failure: MeterFailure) {
        try {
            onFailure(failure)
        } catch (_: Throwable) {
            // nowhere left to report it
        }
    }
}
