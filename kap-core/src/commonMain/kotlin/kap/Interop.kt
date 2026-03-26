package kap

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlin.time.Duration

// ── Deferred ↔ Kap ───────────────────────────────────────────────

/**
 * Wraps an already-started [Deferred] into a [Kap] that awaits it.
 */
fun <A> Deferred<A>.toKap(): Kap<A> = Kap {
    this@toKap.await()
}

/**
 * Eagerly starts this computation as a [Deferred] in [scope].
 */
fun <A> Kap<A>.toDeferred(scope: CoroutineScope): Deferred<A> =
    scope.async { with(this@toDeferred) { execute() } }

// ── Flow → Kap ───────────────────────────────────────────────────

/**
 * Creates a [Kap] that collects the first emission from this [Flow].
 */
fun <A> Flow<A>.firstAsKap(): Kap<A> = Kap {
    this@firstAsKap.first()
}

// ── suspend lambda → Kap ─────────────────────────────────────────

/**
 * Wraps a suspend lambda into an explicit [Kap].
 *
 * This is the same conversion that [with]'s lambda overload does internally,
 * but useful when you need a `Kap` value to pass around.
 */
fun <A> (suspend () -> A).toKap(): Kap<A> = Kap {
    this@toKap()
}

// ── delayed: computation that waits then returns a value ────────────────

/**
 * Creates a [Kap] that delays for [duration] then returns [value].
 *
 * Useful for testing and for composing timed sequences.
 *
 * ```
 * race(
 *     Kap { fetchFromService() },
 *     delayed(2.seconds, fallbackValue),
 * )
 * ```
 */
fun <A> delayed(duration: Duration, value: A): Kap<A> = Kap {
    kotlinx.coroutines.delay(duration)
    value
}

/**
 * Creates a [Kap] that delays for [duration] then executes [block].
 */
fun <A> delayed(duration: Duration, block: suspend () -> A): Kap<A> = Kap {
    kotlinx.coroutines.delay(duration)
    block()
}

// ── catching: exception-safe computation builder ────────────────────────

/**
 * Creates a [Kap] that catches non-cancellation exceptions and wraps
 * the outcome in a [Result].
 *
 * Unlike [kotlin.runCatching], this **never catches [CancellationException]** —
 * structured concurrency cancellation always propagates.
 *
 * ```
 * val safe: Kap<Result<User>> = catching { fetchUser() }
 * ```
 */
fun <A> catching(block: suspend CoroutineScope.() -> A): Kap<Result<A>> = Kap {
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }
}
