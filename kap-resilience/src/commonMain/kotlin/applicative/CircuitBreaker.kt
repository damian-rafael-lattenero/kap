package applicative

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
 * without executing the underlying computation. After [resetTimeout], the circuit
 * transitions to half-open and allows a single probe call. If the probe succeeds,
 * the circuit closes; if it fails, the circuit reopens.
 *
 * Thread-safe — all state transitions are protected by a [Mutex].
 *
 * ```
 * val breaker = CircuitBreaker(
 *     maxFailures = 5,
 *     resetTimeout = 30.seconds,
 * )
 *
 * Computation { callExternalService() }
 *     .withCircuitBreaker(breaker)
 *     .recover { cachedResponse() }
 * ```
 *
 * @param maxFailures consecutive failures before opening the circuit.
 * @param resetTimeout time to wait in open state before allowing a probe.
 * @param onStateChange optional callback for observability (logging, metrics).
 * @param timeSource injectable time source for testing.
 */
class CircuitBreaker(
    val maxFailures: Int,
    val resetTimeout: Duration,
    val onStateChange: (State, State) -> Unit = { _, _ -> },
    private val timeSource: TimeSource = TimeSource.Monotonic,
) {
    init {
        require(maxFailures >= 1) { "maxFailures must be >= 1, was $maxFailures" }
        require(resetTimeout > Duration.ZERO) { "resetTimeout must be positive" }
    }

    enum class State { Closed, Open, HalfOpen }

    @PublishedApi
    internal val mutex = Mutex()

    @PublishedApi
    internal var state: State = State.Closed

    @PublishedApi
    internal var failureCount: Int = 0

    @PublishedApi
    internal var openedAt: TimeMark? = null

    /** Current state of the circuit breaker (snapshot — may change immediately after reading). */
    val currentState: State get() = state

    @PublishedApi
    internal fun transitionTo(newState: State) {
        val old = state
        if (old != newState) {
            state = newState
            onStateChange(old, newState)
        }
    }

    @PublishedApi
    internal fun recordSuccess() {
        failureCount = 0
        transitionTo(State.Closed)
    }

    @PublishedApi
    internal fun recordFailure() {
        failureCount++
        if (failureCount >= maxFailures) {
            transitionTo(State.Open)
            openedAt = timeSource.markNow()
        }
    }

    @PublishedApi
    internal fun shouldAttempt(): Boolean {
        return when (state) {
            State.Closed -> true
            State.HalfOpen -> false // another probe is already in flight
            State.Open -> {
                val mark = openedAt ?: return true
                if (mark.elapsedNow() >= resetTimeout) {
                    transitionTo(State.HalfOpen)
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
 * - **Half-Open:** one probe call is allowed. Success closes, failure reopens.
 *
 * [kotlinx.coroutines.CancellationException] is **never** counted as a failure —
 * structured concurrency cancellation always propagates without affecting the circuit.
 *
 * ```
 * val breaker = CircuitBreaker(maxFailures = 3, resetTimeout = 10.seconds)
 *
 * val result = Async {
 *     kap(::Dashboard)
 *         .with { fetchUser().withCircuitBreaker(breaker) }
 *         .with { fetchConfig() }
 *         .with { fetchCart() }
 * }
 * ```
 */
fun <A> Computation<A>.withCircuitBreaker(breaker: CircuitBreaker): Computation<A> {
    val self = this
    return Computation {
        breaker.mutex.withLock {
            if (!breaker.shouldAttempt()) {
                throw CircuitBreakerOpenException()
            }
        }
        try {
            val result = with(self) { execute() }
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
