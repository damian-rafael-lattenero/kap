package kap

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
 * `Validated<E, A>` = `Kap<Either<NonEmptyList<E>, A>>`
 */
typealias Validated<E, A> = Kap<Either<NonEmptyList<E>, A>>

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
 *     Kap { fetchUser() }.attempt()
 * }
 * ```
 */
fun <A> Kap<A>.attempt(): Kap<Either<Throwable, A>> = Kap {
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
 *         fa = Kap { fetchFromCache() },
 *         fb = Kap { fetchFromNetwork() },
 *     )
 * }
 * ```
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun <A, B> raceEither(fa: Kap<A>, fb: Kap<B>): Kap<Either<A, B>> = Kap {
    supervisorScope {
        val da: Deferred<Result<A>> = async { runCatching { with(fa) { execute() } } }
        val db: Deferred<Result<B>> = async { runCatching { with(fb) { execute() } } }
        try {
            select<Either<A, B>> {
                da.onAwait { resultA ->
                    resultA.getOrNull()?.let { return@onAwait Either.Left(it) }
                    // A failed — wait for B
                    val resultB = db.await()
                    resultB.getOrNull()?.let { return@onAwait Either.Right(it) }
                    // Both failed
                    val errA = resultA.exceptionOrNull()!!
                    errA.addSuppressed(resultB.exceptionOrNull()!!)
                    throw errA
                }
                db.onAwait { resultB ->
                    resultB.getOrNull()?.let { return@onAwait Either.Right(it) }
                    // B failed — wait for A
                    val resultA = da.await()
                    resultA.getOrNull()?.let { return@onAwait Either.Left(it) }
                    // Both failed
                    val errB = resultB.exceptionOrNull()!!
                    errB.addSuppressed(resultA.exceptionOrNull()!!)
                    throw errB
                }
            }
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
 * Wraps a [kotlin.Result] into a validated [Kap], mapping failures with [onError].
 */
fun <E, A> Result<A>.toValidated(onError: (Throwable) -> E): Kap<Either<NonEmptyList<E>, A>> =
    fold(
        onSuccess = { valid(it) },
        onFailure = { invalid(onError(it)) },
    )

// ── Arrow parZip result → Kap ──────────────────────────────────

/**
 * Wraps a suspend lambda (typically calling Arrow's `parZip` or `parMap`) into a [Kap].
 */
fun <A> fromArrow(block: suspend () -> A): Kap<A> = Kap { block() }

// ── Kap → Arrow's suspend world ────────────────────────────────

/**
 * Executes this [Kap] and returns the result as an Arrow [Either],
 * catching non-cancellation exceptions into [Either.Left].
 */
suspend fun <A> Kap<A>.runCatchingArrow(
    scope: kotlinx.coroutines.CoroutineScope,
): Either<Throwable, A> =
    try {
        Either.Right(with(this) { scope.execute() })
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Either.Left(e)
    }
