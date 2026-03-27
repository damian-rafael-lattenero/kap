package kap.kotest

import kap.CircuitBreaker

/**
 * Assert that a CircuitBreaker is in Closed state.
 */
suspend fun CircuitBreaker.shouldBeClosed() {
    val state = currentState
    assert(state == CircuitBreaker.State.Closed) {
        "Expected CircuitBreaker to be Closed but was $state"
    }
}

/**
 * Assert that a CircuitBreaker is in Open state.
 */
suspend fun CircuitBreaker.shouldBeOpen() {
    val state = currentState
    assert(state == CircuitBreaker.State.Open) {
        "Expected CircuitBreaker to be Open but was $state"
    }
}

/**
 * Assert that a CircuitBreaker is in HalfOpen state.
 */
suspend fun CircuitBreaker.shouldBeHalfOpen() {
    val state = currentState
    assert(state == CircuitBreaker.State.HalfOpen) {
        "Expected CircuitBreaker to be HalfOpen but was $state"
    }
}

/**
 * Track CircuitBreaker state transitions for assertions.
 *
 * ```kotlin
 * val tracker = CircuitBreakerTracker()
 * val breaker = CircuitBreaker(maxFailures = 3, resetTimeout = 1.seconds,
 *     onStateChange = tracker::record)
 *
 * // ... trigger transitions ...
 *
 * tracker.shouldHaveTransitioned(Closed to Open)
 * tracker.shouldHaveTransitionCount(2)
 * ```
 */
class CircuitBreakerTracker {
    private val _transitions = mutableListOf<Pair<CircuitBreaker.State, CircuitBreaker.State>>()
    val transitions: List<Pair<CircuitBreaker.State, CircuitBreaker.State>> get() = _transitions

    fun record(from: CircuitBreaker.State, to: CircuitBreaker.State) {
        _transitions.add(from to to)
    }

    fun shouldHaveTransitioned(transition: Pair<CircuitBreaker.State, CircuitBreaker.State>) {
        assert(transition in _transitions) {
            "Expected transition ${transition.first} -> ${transition.second} " +
                    "but transitions were: ${_transitions.map { "${it.first} -> ${it.second}" }}"
        }
    }

    fun shouldHaveTransitionCount(expected: Int) {
        assert(_transitions.size == expected) {
            "Expected $expected transitions but got ${_transitions.size}: " +
                    _transitions.map { "${it.first} -> ${it.second}" }
        }
    }
}
