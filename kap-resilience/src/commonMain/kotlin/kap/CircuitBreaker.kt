package kap

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * A circuit breaker that protects downstream services from cascading failures.
 *
 * Tracks failures and opens the circuit when [maxFailures] consecutive failures
 * occur. While open, calls fail immediately with [CircuitBreakerOpenException]
 * without executing the underlying computation. After [retryAfter], the circuit
 * transitions to half-open and allows a probe call. When [successThreshold]
 * consecutive probes succeed, the circuit closes; a failed probe reopens it.
 *
 * Thread-safe — all state transitions are protected by a [Mutex].
 *
 * ```
 * val breaker = CircuitBreaker(
 *     maxFailures = 5,
 *     retryAfter = 30.seconds,
 *     successThreshold = 2,   // two consecutive HalfOpen probes to close
 * )
 *
 * Kap { callExternalService() }
 *     .withCircuitBreaker(breaker)
 *     .recover { cachedResponse() }
 * ```
 *
 * @param maxFailures consecutive failures before opening the circuit.
 * @param retryAfter time to wait in open state before allowing a probe.
 * @param successThreshold number of consecutive successful HalfOpen probes
 *   required to close the circuit. With a threshold greater than one, the
 *   first success keeps the circuit HalfOpen and leaves the failure count
 *   untouched; a failure in between resets the probe count.
 * @param onStateChange optional callback for observability (logging, metrics).
 * @param timeSource injectable time source for testing.
 */
class CircuitBreaker(
    val maxFailures: Int,
    val retryAfter: Duration,
    val successThreshold: Int = 1,
    val onStateChange: (State, State) -> Unit = { _, _ -> },
    private val timeSource: TimeSource = TimeSource.Monotonic,
) {
    /**
     * Source-compatible constructor kept for the pre-4.3 name `resetTimeout`.
     *
     * The new name for the open-state cooldown is [retryAfter]; this overload
     * exists so call sites written against the old name keep compiling.
     */
    @Deprecated(
        message = "resetTimeout was renamed to retryAfter",
        replaceWith = ReplaceWith(
            "CircuitBreaker(maxFailures, retryAfter, successThreshold, onStateChange, timeSource)",
        ),
    )
    constructor(
        maxFailures: Int,
        resetTimeout: Duration,
        onStateChange: (State, State) -> Unit = { _, _ -> },
        timeSource: TimeSource = TimeSource.Monotonic,
    ) : this(
        maxFailures = maxFailures,
        retryAfter = resetTimeout,
        successThreshold = 1,
        onStateChange = onStateChange,
        timeSource = timeSource,
    )

    /** Deprecated alias for [retryAfter]. */
    @Deprecated("resetTimeout was renamed to retryAfter", ReplaceWith("retryAfter"))
    val resetTimeout: Duration get() = retryAfter

    init {
        require(maxFailures >= 1) { "maxFailures must be >= 1, was $maxFailures" }
        require(retryAfter > Duration.ZERO) { "retryAfter must be positive" }
        require(successThreshold >= 1) { "successThreshold must be >= 1, was $successThreshold" }
    }

    /**
     * The three states of the circuit lifecycle.
     */
    enum class State {
        /** Calls proceed normally; consecutive failures count towards [CircuitBreaker.maxFailures]. */
        Closed,

        /** Calls are rejected with [CircuitBreakerOpenException] until [CircuitBreaker.retryAfter] elapses. */
        Open,

        /** A single probe call is admitted; its outcome decides between [Closed] and [Open]. */
        HalfOpen,
    }

    internal val mutex = Mutex()

    internal var state: State = State.Closed

    internal var failureCount: Int = 0

    /** Consecutive successful HalfOpen probes towards [successThreshold]. */
    internal var successCount: Int = 0

    /** True while a HalfOpen probe has been admitted and has not reported back. */
    internal var probeInFlight: Boolean = false

    internal var openedAt: TimeMark? = null

    /** Current state of the circuit breaker (snapshot — may change immediately after reading). */
    val currentState: State get() = state

    // ── Public, mutex-taking API (4.1.0): guard code that is not a Kap ──

    // Mutex.withLock is inline-suspend: detekt's RedundantSuspendModifier cannot see the suspension.
    /**
     * True when a call may proceed (Closed, or HalfOpen probing).
     *
     * @return `false` when the circuit is Open and [retryAfter] has not
     *   elapsed yet, or when a HalfOpen probe is already in flight; `true`
     *   otherwise (and the call is expected to report the outcome via
     *   [onSuccess] / [onFailure]).
     */
    @Suppress("RedundantSuspendModifier")
    suspend fun tryAcquire(): Boolean = mutex.withLock { shouldAttempt() }

    /**
     * Records a success. In HalfOpen it counts towards [successThreshold] and
     * closes the circuit when the threshold is reached; in any other state it
     * closes the circuit and resets the failure count immediately.
     */
    @Suppress("RedundantSuspendModifier")
    suspend fun onSuccess() = mutex.withLock { recordSuccess() }

    /** Records a failure: counts towards [maxFailures], reopens from HalfOpen. */
    @Suppress("RedundantSuspendModifier")
    suspend fun onFailure() = mutex.withLock { recordFailure() }

    /**
     * Runs [block] under the breaker: throws [CircuitBreakerOpenException]
     * when open, records the outcome otherwise. Cancellation is never counted.
     *
     * @param block the guarded computation; a normal return counts as a
     *   success, any non-cancellation failure as a failure.
     * @return the value [block] produced.
     * @throws CircuitBreakerOpenException when the circuit is Open (or a
     *   HalfOpen probe is already in flight) and rejects the call.
     */
    // The breaker must count EVERY failure, exactly like withCircuitBreaker does.
    @Suppress("TooGenericExceptionCaught", "ThrowsCount")
    suspend fun <A> protect(block: suspend () -> A): A {
        if (!tryAcquire()) throw CircuitBreakerOpenException()
        return try {
            block().also { onSuccess() }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Throwable) {
            onFailure()
            throw e
        }
    }

    internal fun transitionTo(newState: State) {
        val old = state
        if (old != newState) {
            state = newState
            onStateChange(old, newState)
        }
    }

    internal fun recordSuccess() {
        if (state == State.HalfOpen && successThreshold > 1) {
            successCount++
            probeInFlight = false
            if (successCount < successThreshold) return
        }
        failureCount = 0
        successCount = 0
        probeInFlight = false
        transitionTo(State.Closed)
    }

    internal fun recordFailure() {
        failureCount++
        successCount = 0
        probeInFlight = false
        if (failureCount >= maxFailures) {
            transitionTo(State.Open)
            openedAt = timeSource.markNow()
        }
    }

    internal fun shouldAttempt(): Boolean {
        return when (state) {
            State.Closed -> true
            State.HalfOpen ->
                if (probeInFlight) {
                    false // another probe is already in flight
                } else {
                    probeInFlight = true
                    true
                }
            State.Open -> {
                val mark = openedAt ?: return true
                if (mark.elapsedNow() >= retryAfter) {
                    transitionTo(State.HalfOpen)
                    successCount = 0
                    probeInFlight = true
                    true
                } else {
                    false
                }
            }
        }
    }
}

/**
 * Exception thrown when the circuit breaker is open and rejecting calls.
 */
class CircuitBreakerOpenException(
    message: String = "Circuit breaker is open",
) : RuntimeException(message)

/**
 * Protects this computation with a [CircuitBreaker].
 *
 * - **Closed:** calls execute normally. Consecutive failures increment the counter.
 * - **Open:** calls fail immediately with [CircuitBreakerOpenException].
 * - **Half-Open:** one probe call at a time. When [CircuitBreaker.successThreshold]
 *   consecutive probes succeed, the circuit closes; a failure reopens it.
 *
 * [kotlinx.coroutines.CancellationException] is **never** counted as a failure —
 * structured concurrency cancellation always propagates without affecting the circuit.
 *
 * ```
 * val breaker = CircuitBreaker(maxFailures = 3, retryAfter = 10.seconds)
 *
 * val result = kap(::Dashboard)
 *     .with { fetchUser().withCircuitBreaker(breaker) }
 *     .with { fetchConfig() }
 *     .with { fetchCart() }
 *     .evalGraph()
 * ```
 *
 * @param breaker the breaker guarding this computation; the same instance can
 *   guard several computations (they share its counters).
 * @throws CircuitBreakerOpenException when the breaker is Open and rejects the call.
 */
fun <A> Kap<A>.withCircuitBreaker(breaker: CircuitBreaker): Kap<A> {
    val self = this
    return Kap {
        breaker.mutex.withLock {
            if (!breaker.shouldAttempt()) {
                throw CircuitBreakerOpenException()
            }
        }
        try {
            val result = with(self.isolated()) { execute() }
            breaker.mutex.withLock { breaker.recordSuccess() }
            result
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Throwable) {
            breaker.mutex.withLock { breaker.recordFailure() }
            throw e
        }
    }
}
