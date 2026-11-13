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
 * @param name identity for observability: the [observedState] snapshot and the
 *   [CircuitBreakerOpenException] carry it, so logs and metrics name the breaker.
 * @param timeSource injectable time source for testing.
 */
class CircuitBreaker(
    val maxFailures: Int,
    val retryAfter: Duration,
    val successThreshold: Int = 1,
    val onStateChange: (State, State) -> Unit = { _, _ -> },
    val name: String = "circuit",
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

    /**
     * The state as it would be on the next call, read-only: once [retryAfter]
     * has elapsed while Open, this reports [State.HalfOpen] even though the
     * real transition happens lazily inside the next [tryAcquire] — the
     * observation carries no side effect: it never admits a probe, mutates
     * counters, or fires [onStateChange]. Snapshot semantics — may change
     * immediately after reading.
     */
    val observedState: State
        get() {
            val current = state
            if (current != State.Open) return current
            val mark = openedAt ?: return current
            return if (mark.elapsedNow() >= retryAfter) State.HalfOpen else State.Open
        }

    override fun toString(): String = "CircuitBreaker(name=$name, state=$currentState)"

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
        mutex.withLock { if (!shouldAttempt()) throw openException() }
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

    /**
     * Builds the rejection exception under the caller's lock: the breaker's
     * [name], the state the caller was rejected in, and — while the open
     * window is still running — the [CircuitBreakerOpenException.remaining]
     * time until a probe is allowed.
     */
    internal fun openException(): CircuitBreakerOpenException {
        val mark = openedAt
        val remaining = if (state == State.Open && mark != null) {
            val left = retryAfter - mark.elapsedNow()
            if (left > Duration.ZERO) left else Duration.ZERO
        } else {
            null
        }
        return CircuitBreakerOpenException(name = name, state = state, remaining = remaining)
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
 * Exception thrown when a circuit breaker rejects a call.
 *
 * The breaker supplies its identity and its state as structured fields — a
 * rejection names the breaker it came from and, while the open window is
 * still running, how long until a probe is allowed:
 *
 * ```
 * catch (e: CircuitBreakerOpenException) {
 *     log.warn("breaker ${e.name} rejected the call (state=${e.state}, retry in ${e.remaining})")
 * }
 * ```
 *
 * @param name the breaker's [CircuitBreaker.name]; `null` for a hand-built
 *   rejection that is not tied to a breaker.
 * @param state the [CircuitBreaker.State] the call was rejected in.
 * @param remaining how long until the breaker admits its HalfOpen probe —
 *   zero when the window has already elapsed; `null` outside the Open state
 *   (a HalfOpen rejection means a probe is in flight, not a window).
 */
class CircuitBreakerOpenException(
    val name: String? = null,
    val state: CircuitBreaker.State? = null,
    val remaining: Duration? = null,
) : RuntimeException(buildMessage(name, state, remaining)) {

    /**
     * Source-compatible constructor kept for the pre-5.3 message form: the
     * caller-supplied string is replaced by the structured fields' message.
     */
    @Deprecated(
        message = "Construct with the structured fields: name, state, remaining.",
        replaceWith = ReplaceWith("CircuitBreakerOpenException()"),
    )
    constructor(message: String) : this()

    private companion object {
        fun buildMessage(name: String?, state: CircuitBreaker.State?, remaining: Duration?): String = when {
            name == null -> "Circuit breaker is open"
            state == CircuitBreaker.State.HalfOpen ->
                "Circuit breaker '$name' is half-open — a probe is already in flight"
            remaining != null && remaining > Duration.ZERO ->
                "Circuit breaker '$name' is open — retry allowed in $remaining"
            else -> "Circuit breaker '$name' is open"
        }
    }
}

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
                throw breaker.openException()
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
