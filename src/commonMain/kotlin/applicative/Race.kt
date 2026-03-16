package applicative

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.supervisorScope

// ── race: first to *succeed* wins, loser is cancelled ───────────────────

/**
 * Runs [fa] and [fb] concurrently; the first to **succeed** wins, the loser is cancelled.
 *
 * If the first to complete fails (non-cancellation), the other racer is given a
 * chance to finish. Only when **both** fail does the exception propagate
 * (the second failure is added as a suppressed exception on the first).
 *
 * Uses [Result]-wrapping internally so that a successful completion is never
 * lost due to a concurrent failure arriving at `select` first.
 *
 * The winner is tracked explicitly via `select` clause pairing to avoid
 * race conditions between `select` completion and `isCompleted` checks.
 *
 * ```
 * race(
 *     fa = Computation { fetchFromPrimary() },
 *     fb = Computation { fetchFromFallback() }
 * )
 * ```
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun <A> race(fa: Computation<A>, fb: Computation<A>): Computation<A> = Computation {
    supervisorScope {
        val da = async { runCatching { with(fa) { execute() } } }
        val db = async { runCatching { with(fb) { execute() } } }
        try {
            val (first, other) = select<Pair<Result<A>, Deferred<Result<A>>>> {
                da.onAwait { it to db }
                db.onAwait { it to da }
            }
            first.getOrNull()?.let { return@supervisorScope it }
            // First to complete failed — wait for the other
            val second = other.await()
            second.getOrElse { secondError ->
                val firstError = first.exceptionOrNull()!!
                firstError.addSuppressed(secondError)
                throw firstError
            }
        } catch (e: CancellationException) {
            throw e
        } finally {
            da.cancel()
            db.cancel()
        }
    }
}

/**
 * N-way race — runs all [computations] concurrently, the first to **succeed** wins,
 * all losers are cancelled.
 *
 * Failed racers are discarded as long as at least one is still running.
 * Only when **all** fail does the last exception propagate (prior failures
 * are added as suppressed exceptions).
 *
 * Each `select` round explicitly tracks which deferred completed, avoiding
 * race conditions between completion detection and list mutation.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun <A> raceN(vararg computations: Computation<A>): Computation<A> {
    require(computations.isNotEmpty()) { "raceN requires at least one computation" }
    if (computations.size == 1) return computations[0]
    return Computation {
        supervisorScope {
            val deferreds: List<Deferred<Result<A>>> =
                computations.map { c -> async { runCatching { with(c) { execute() } } } }
            try {
                val pending = deferreds.toMutableSet()
                val errors = mutableListOf<Throwable>()
                while (pending.isNotEmpty()) {
                    val (result, winner) = select<Pair<Result<A>, Deferred<Result<A>>>> {
                        pending.forEach { d -> d.onAwait { it to d } }
                    }
                    result.getOrNull()?.let { return@supervisorScope it }
                    // This racer failed — collect error and continue
                    val error = result.exceptionOrNull()!!
                    if (error is CancellationException) throw error
                    errors.add(error)
                    pending.remove(winner)
                }
                // All failed — throw first with others as suppressed
                val primary = errors.first()
                errors.drop(1).forEach { primary.addSuppressed(it) }
                throw primary
            } finally {
                deferreds.forEach { it.cancel() }
            }
        }
    }
}

/**
 * Races all computations in this collection; the first to complete wins.
 */
fun <A> Iterable<Computation<A>>.raceAll(): Computation<A> =
    raceN(*toList().toTypedArray())

// ── raceEither: race with heterogeneous types ───────────────────────────

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
 *         fa = Computation { fetchFromCache() },   // fast
 *         fb = Computation { fetchFromNetwork() },  // slow but fresh
 *     )
 * }
 * ```
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun <A, B> raceEither(fa: Computation<A>, fb: Computation<B>): Computation<Either<A, B>> = Computation {
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
            // First failed — wait for the other
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
            // Both failed
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
