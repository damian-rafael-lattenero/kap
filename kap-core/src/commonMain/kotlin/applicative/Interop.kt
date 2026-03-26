package applicative

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlin.time.Duration

// ── Deferred ↔ Effect ───────────────────────────────────────────────

/**
 * Wraps an already-started [Deferred] into a [Effect] that awaits it.
 */
fun <A> Deferred<A>.toEffect(): Effect<A> = Effect {
    this@toEffect.await()
}

/**
 * Eagerly starts this computation as a [Deferred] in [scope].
 */
fun <A> Effect<A>.toDeferred(scope: CoroutineScope): Deferred<A> =
    scope.async { with(this@toDeferred) { execute() } }

// ── Flow → Effect ───────────────────────────────────────────────────

/**
 * Creates a [Effect] that collects the first emission from this [Flow].
 */
fun <A> Flow<A>.firstAsEffect(): Effect<A> = Effect {
    this@firstAsEffect.first()
}

// ── suspend lambda → Effect ─────────────────────────────────────────

/**
 * Wraps a suspend lambda into an explicit [Effect].
 *
 * This is the same conversion that [with]'s lambda overload does internally,
 * but useful when you need a `Effect` value to pass around.
 */
fun <A> (suspend () -> A).toEffect(): Effect<A> = Effect {
    this@toEffect()
}

// ── delayed: computation that waits then returns a value ────────────────

/**
 * Creates a [Effect] that delays for [duration] then returns [value].
 *
 * Useful for testing and for composing timed sequences.
 *
 * ```
 * race(
 *     Effect { fetchFromService() },
 *     delayed(2.seconds, fallbackValue),
 * )
 * ```
 */
fun <A> delayed(duration: Duration, value: A): Effect<A> = Effect {
    kotlinx.coroutines.delay(duration)
    value
}

/**
 * Creates a [Effect] that delays for [duration] then executes [block].
 */
fun <A> delayed(duration: Duration, block: suspend () -> A): Effect<A> = Effect {
    kotlinx.coroutines.delay(duration)
    block()
}

// ── catching: exception-safe computation builder ────────────────────────

/**
 * Creates a [Effect] that catches non-cancellation exceptions and wraps
 * the outcome in a [Result].
 *
 * Unlike [kotlin.runCatching], this **never catches [CancellationException]** —
 * structured concurrency cancellation always propagates.
 *
 * ```
 * val safe: Effect<Result<User>> = catching { fetchUser() }
 * ```
 */
fun <A> catching(block: suspend CoroutineScope.() -> A): Effect<Result<A>> = Effect {
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }
}
