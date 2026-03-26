package applicative

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.supervisorScope

/**
 * Shorthand typealias for a validated computation using Arrow types.
 *
 * `Validated<E, A>` = `Effect<Either<NonEmptyList<E>, A>>`
 */
typealias Validated<E, A> = Effect<Either<NonEmptyList<E>, A>>

/**
 * Shorthand typealias for Arrow's NonEmptyList.
 */
typealias Nel<A> = NonEmptyList<A>

// ── attempt: catch to Arrow Either ─────────────────────────────────────

/**
 * Catches non-cancellation exceptions and wraps the outcome in Arrow's [Either].
 *
 * [CancellationException] is never caught — structured concurrency
 * cancellation always propagates.
 *
 * ```
 * val result: Either<Throwable, User> = Async {
 *     Effect { fetchUser() }.attempt()
 * }
 * ```
 */
fun <A> Effect<A>.attempt(): Effect<Either<Throwable, A>> = Effect {
    try {
        Either.Right(with(this@attempt) { execute() })
    } catch (e: Throwable) {
        if (e is CancellationException) throw e
        Either.Left(e)
    }
}

// ── raceEither: race with heterogeneous types ──────────────────────────

/**
 * Runs [fa] and [fb] concurrently with **different result types**; the first
 * to succeed wins.
 *
 * Returns [Either.Left] if [fa] wins, [Either.Right] if [fb] wins.
 * The loser is cancelled. If both fail, the first error propagates with
 * the second as a suppressed exception.
 *
 * ```
 * val result: Either<CachedData, FreshData> = Async {
 *     raceEither(
 *         fa = Effect { fetchFromCache() },
 *         fb = Effect { fetchFromNetwork() },
 *     )
 * }
 * ```
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun <A, B> raceEither(fa: Effect<A>, fb: Effect<B>): Effect<Either<A, B>> = Effect {
    supervisorScope {
        val da = async { runCatching { with(fa) { execute() } } }
        val db = async { runCatching { with(fb) { execute() } } }
        try {
            val (firstIsA, firstResult, other) = select<Triple<Boolean, Result<*>, Deferred<*>>> {
                da.onAwait { Triple(true, it, db) }
                db.onAwait { Triple(false, it, da) }
            }
            if (firstIsA) {
                firstResult.getOrNull()?.let {
                    @Suppress("UNCHECKED_CAST")
                    return@supervisorScope Either.Left(it as A)
                }
            } else {
                firstResult.getOrNull()?.let {
                    @Suppress("UNCHECKED_CAST")
                    return@supervisorScope Either.Right(it as B)
                }
            }
            @Suppress("UNCHECKED_CAST")
            val secondResult = (other as Deferred<Result<*>>).await()
            if (!firstIsA) {
                secondResult.getOrNull()?.let {
                    @Suppress("UNCHECKED_CAST")
                    return@supervisorScope Either.Left(it as A)
                }
            } else {
                secondResult.getOrNull()?.let {
                    @Suppress("UNCHECKED_CAST")
                    return@supervisorScope Either.Right(it as B)
                }
            }
            val firstError = firstResult.exceptionOrNull()!!
            val secondError = secondResult.exceptionOrNull()!!
            firstError.addSuppressed(secondError)
            throw firstError
        } catch (e: CancellationException) {
            throw e
        } finally {
            da.cancel()
            db.cancel()
        }
    }
}

// ── Result ↔ Arrow Either bridges ──────────────────────────────────────

/**
 * Converts a [kotlin.Result] into an Arrow [Either].
 */
fun <A> Result<A>.toEither(): Either<Throwable, A> =
    fold(onSuccess = { Either.Right(it) }, onFailure = { Either.Left(it) })

/**
 * Converts an Arrow [Either] into a [kotlin.Result].
 * Requires the left type to be [Throwable].
 */
fun <A> Either<Throwable, A>.toResult(): Result<A> = when (this) {
    is Either.Left -> Result.failure(value)
    is Either.Right -> Result.success(value)
}

/**
 * Converts an Arrow [Either] into a [kotlin.Result], mapping the left side to a [Throwable] first.
 */
fun <E, A> Either<E, A>.toResult(mapError: (E) -> Throwable): Result<A> = when (this) {
    is Either.Left -> Result.failure(mapError(value))
    is Either.Right -> Result.success(value)
}

/**
 * Wraps a [kotlin.Result] into a validated [Effect], mapping failures with [onError].
 */
fun <E, A> Result<A>.toValidated(onError: (Throwable) -> E): Effect<Either<NonEmptyList<E>, A>> =
    fold(
        onSuccess = { valid(it) },
        onFailure = { invalid(onError(it)) },
    )

// ── Arrow parZip result → Effect ──────────────────────────────────

/**
 * Wraps a suspend lambda (typically calling Arrow's `parZip` or `parMap`) into a [Effect].
 */
fun <A> fromArrow(block: suspend () -> A): Effect<A> = Effect { block() }

// ── Effect → Arrow's suspend world ────────────────────────────────

/**
 * Executes this [Effect] and returns the result as an Arrow [Either],
 * catching non-cancellation exceptions into [Either.Left].
 */
suspend fun <A> Effect<A>.runCatchingArrow(
    scope: kotlinx.coroutines.CoroutineScope,
): Either<Throwable, A> =
    try {
        Either.Right(with(this) { scope.execute() })
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Either.Left(e)
    }
