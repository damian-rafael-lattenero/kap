package kap

import kotlin.time.Duration

// 4.1.0: resilience operators available mid-chain on @KapTypeSafe wrappers (SPEC F-06).

/**
 * Mirrors [Kap.retry] on a [KapLike] (e.g. the KSP wrapper of an `@KapTypeSafe`
 * function), so resilience operators compose mid-chain.
 *
 * @param schedule composable retry policy.
 * @param onRetry optional callback before each retry.
 * @return a [Kap] that retries the wrapped computation per [schedule].
 * @see Kap.retry
 */
fun <F> KapLike<F>.retry(
    schedule: Schedule<Throwable>,
    onRetry: suspend (attempt: Int, error: Throwable, nextDelay: Duration) -> Unit = { _, _, _ -> },
): Kap<F> = asKap.retry(schedule, onRetry)

/**
 * Mirrors the fresh-schedule [Kap.retry] overload on a [KapLike]: a new
 * [Schedule] is built per invocation, so stateful policies do not leak state
 * between runs.
 *
 * @param scheduleFactory builds a fresh schedule for each run.
 * @param onRetry optional callback before each retry.
 * @return a [Kap] that retries the wrapped computation.
 * @see Kap.retry
 */
fun <F> KapLike<F>.retry(
    scheduleFactory: () -> Schedule<Throwable>,
    onRetry: suspend (attempt: Int, error: Throwable, nextDelay: Duration) -> Unit = { _, _, _ -> },
): Kap<F> = asKap.retry(scheduleFactory, onRetry)

/**
 * Mirrors [Kap.retryOrElse] on a [KapLike]: retries per [schedule] and calls
 * [orElse] with the last error when retries are exhausted.
 *
 * @param schedule composable retry policy.
 * @param orElse fallback invoked with the last error.
 * @return a [Kap] that calls [orElse] instead of throwing once the schedule is done.
 * @see Kap.retryOrElse
 */
fun <F> KapLike<F>.retryOrElse(schedule: Schedule<Throwable>, orElse: suspend (Throwable) -> F): Kap<F> =
    asKap.retryOrElse(schedule, orElse)

/**
 * Mirrors [Kap.retryWithResult] on a [KapLike]: retries per [schedule] and
 * reports the value plus the attempt count and total delay.
 *
 * @param schedule composable retry policy.
 * @return a [Kap] of a [RetryResult] carrying retry metadata.
 * @see Kap.retryWithResult
 */
fun <F> KapLike<F>.retryWithResult(schedule: Schedule<Throwable>): Kap<RetryResult<F>> = asKap.retryWithResult(schedule)

/**
 * Mirrors [Kap.withCircuitBreaker] on a [KapLike]: guards the wrapped
 * computation with [breaker], failing fast with [CircuitBreakerOpenException]
 * while the circuit is open.
 *
 * @param breaker the breaker guarding this computation.
 * @return a [Kap] protected by the breaker.
 * @see Kap.withCircuitBreaker
 */
fun <F> KapLike<F>.withCircuitBreaker(breaker: CircuitBreaker): Kap<F> = asKap.withCircuitBreaker(breaker)

/**
 * Mirrors [Kap.timeoutRace] on a [KapLike]: races the wrapped computation
 * against [fallback], which starts in parallel from the beginning.
 *
 * @param duration the budget before the [fallback] result is used.
 * @param fallback computation racing against the timeout.
 * @return a [Kap] yielding whichever finishes first.
 * @see Kap.timeoutRace
 */
fun <F> KapLike<F>.timeoutRace(duration: Duration, fallback: Kap<F>): Kap<F> = asKap.timeoutRace(duration, fallback)
