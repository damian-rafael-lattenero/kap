package kap

import kotlin.time.Duration

// 4.1.0: resilience operators available mid-chain on @KapTypeSafe wrappers (SPEC F-06).

fun <F> KapLike<F>.retry(
    schedule: Schedule<Throwable>,
    onRetry: suspend (attempt: Int, error: Throwable, nextDelay: Duration) -> Unit = { _, _, _ -> },
): Kap<F> = asKap.retry(schedule, onRetry)

fun <F> KapLike<F>.retry(
    scheduleFactory: () -> Schedule<Throwable>,
    onRetry: suspend (attempt: Int, error: Throwable, nextDelay: Duration) -> Unit = { _, _, _ -> },
): Kap<F> = asKap.retry(scheduleFactory, onRetry)

fun <F> KapLike<F>.retryOrElse(schedule: Schedule<Throwable>, orElse: suspend (Throwable) -> F): Kap<F> =
    asKap.retryOrElse(schedule, orElse)

fun <F> KapLike<F>.retryWithResult(schedule: Schedule<Throwable>): Kap<RetryResult<F>> = asKap.retryWithResult(schedule)

fun <F> KapLike<F>.withCircuitBreaker(breaker: CircuitBreaker): Kap<F> = asKap.withCircuitBreaker(breaker)

fun <F> KapLike<F>.timeoutRace(duration: Duration, fallback: Kap<F>): Kap<F> = asKap.timeoutRace(duration, fallback)
