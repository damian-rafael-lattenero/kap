package applicative

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlin.time.Duration

// ── Deferred ↔ Computation ───────────────────────────────────────────────

/**
 * Wraps an already-started [Deferred] into a [Computation] that awaits it.
 */
fun <A> Deferred<A>.toComputation(): Computation<A> = Computation {
    this@toComputation.await()
}

/**
 * Eagerly starts this computation as a [Deferred] in [scope].
 */
fun <A> Computation<A>.toDeferred(scope: CoroutineScope): Deferred<A> =
    scope.async { with(this@toDeferred) { execute() } }

// ── Flow → Computation ───────────────────────────────────────────────────

/**
 * Creates a [Computation] that collects the first emission from this [Flow].
 */
fun <A> Flow<A>.firstAsComputation(): Computation<A> = Computation {
    this@firstAsComputation.first()
}

// ── suspend lambda → Computation ─────────────────────────────────────────

/**
 * Wraps a suspend lambda into an explicit [Computation].
 *
 * This is the same conversion that [ap]'s lambda overload does internally,
 * but useful when you need a `Computation` value to pass around.
 */
fun <A> (suspend () -> A).toComputation(): Computation<A> = Computation {
    this@toComputation()
}

// ── kotlin.Result ↔ Either ──────────────────────────────────────────────

/**
 * Converts a [kotlin.Result] into an [Either].
 *
 * ```
 * val result: Result<User> = runCatching { fetchUser() }
 * val either: Either<Throwable, User> = result.toEither()
 * ```
 */
fun <A> Result<A>.toEither(): Either<Throwable, A> =
    fold(onSuccess = { Either.Right(it) }, onFailure = { Either.Left(it) })

/**
 * Converts an [Either] into a [kotlin.Result].
 *
 * Requires the left type to be [Throwable].
 *
 * ```
 * val either: Either<Throwable, User> = ...
 * val result: Result<User> = either.toResult()
 * ```
 */
fun <A> Either<Throwable, A>.toResult(): Result<A> = when (this) {
    is Either.Left -> Result.failure(value)
    is Either.Right -> Result.success(value)
}

/**
 * Converts an [Either] into a [kotlin.Result], mapping the left side to a [Throwable] first.
 *
 * ```
 * val either: Either<String, User> = ...
 * val result: Result<User> = either.toResult { RuntimeException(it) }
 * ```
 */
fun <E, A> Either<E, A>.toResult(mapError: (E) -> Throwable): Result<A> = when (this) {
    is Either.Left -> Result.failure(mapError(value))
    is Either.Right -> Result.success(value)
}

// ── kotlin.Result → Validated Computation ───────────────────────────────

/**
 * Wraps a [kotlin.Result] into a validated [Computation], mapping failures with [onError].
 *
 * ```
 * val userResult: Result<User> = runCatching { fetchUser() }
 * val validated: Computation<Either<Nel<AppError>, User>> =
 *     userResult.toValidated { AppError.FetchFailed(it.message ?: "unknown") }
 * ```
 */
fun <E, A> Result<A>.toValidated(onError: (Throwable) -> E): Computation<Either<Nel<E>, A>> =
    fold(
        onSuccess = { valid(it) },
        onFailure = { invalid(onError(it)) },
    )

// ── delayed: computation that waits then returns a value ────────────────

/**
 * Creates a [Computation] that delays for [duration] then returns [value].
 *
 * Useful for testing and for composing timed sequences.
 *
 * ```
 * race(
 *     Computation { fetchFromService() },
 *     delayed(2.seconds, fallbackValue),
 * )
 * ```
 */
fun <A> delayed(duration: Duration, value: A): Computation<A> = Computation {
    kotlinx.coroutines.delay(duration)
    value
}

/**
 * Creates a [Computation] that delays for [duration] then executes [block].
 */
fun <A> delayed(duration: Duration, block: suspend () -> A): Computation<A> = Computation {
    kotlinx.coroutines.delay(duration)
    block()
}

// ── catching: exception-safe computation builder ────────────────────────

/**
 * Creates a [Computation] that catches non-cancellation exceptions and wraps
 * the outcome in a [Result].
 *
 * Unlike [kotlin.runCatching], this **never catches [CancellationException]** —
 * structured concurrency cancellation always propagates.
 *
 * ```
 * val safe: Computation<Result<User>> = catching { fetchUser() }
 * ```
 */
fun <A> catching(block: suspend CoroutineScope.() -> A): Computation<Result<A>> = Computation {
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }
}
