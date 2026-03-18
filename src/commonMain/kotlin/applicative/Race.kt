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

// ── raceQuorum: N-of-M quorum race ─────────────────────────────────────

/**
 * Quorum race — runs all [computations] concurrently and succeeds when
 * [required] of them succeed. Remaining racers are cancelled once the
 * quorum is reached.
 *
 * If fewer than [required] computations succeed (too many failures),
 * throws the last failure with all prior failures as suppressed exceptions.
 *
 * **Use cases:**
 * - Distributed reads: send 3 requests, need any 2 to agree (consistency quorum)
 * - Hedged requests: send to 3 replicas, take the fastest 2
 * - Redundancy: run 5 health checks, need 3 to pass
 *
 * ```
 * val (fast1, fast2) = Async {
 *     raceQuorum(
 *         required = 2,
 *         Computation { fetchFromReplicaA() },
 *         Computation { fetchFromReplicaB() },
 *         Computation { fetchFromReplicaC() },
 *     )
 * }
 * ```
 *
 * @param required number of successes needed (must be in `1..computations.size`)
 * @param computations the competing computations
 * @return list of exactly [required] successful results (in completion order)
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun <A> raceQuorum(required: Int, vararg computations: Computation<A>): Computation<List<A>> {
    require(computations.isNotEmpty()) { "raceQuorum requires at least one computation" }
    require(required in 1..computations.size) {
        "required must be in 1..${computations.size}, was $required"
    }
    if (required == computations.size) {
        // All must succeed — equivalent to sequence
        return computations.toList().map { c -> c }.let { list ->
            Computation {
                list.map { c -> async { with(c) { execute() } } }.map { it.await() }
            }
        }
    }
    return Computation {
        supervisorScope {
            val deferreds: List<Deferred<Result<A>>> =
                computations.map { c -> async { runCatching { with(c) { execute() } } } }
            try {
                val pending = deferreds.toMutableSet()
                val successes = mutableListOf<A>()
                val errors = mutableListOf<Throwable>()
                val maxFailuresAllowed = computations.size - required

                while (pending.isNotEmpty() && successes.size < required) {
                    val (result, winner) = select<Pair<Result<A>, Deferred<Result<A>>>> {
                        pending.forEach { d -> d.onAwait { it to d } }
                    }
                    pending.remove(winner)

                    result.getOrNull()?.let { successes.add(it) }
                        ?: run {
                            val error = result.exceptionOrNull()!!
                            if (error is CancellationException) throw error
                            errors.add(error)
                            // If too many failures, quorum is impossible
                            if (errors.size > maxFailuresAllowed) {
                                val primary = errors.last()
                                errors.dropLast(1).forEach { primary.addSuppressed(it) }
                                throw primary
                            }
                        }
                }

                successes
            } finally {
                deferreds.forEach { it.cancel() }
            }
        }
    }
}

/**
 * Quorum race from a collection — runs all computations concurrently and
 * succeeds when [required] of them succeed.
 *
 * @see raceQuorum
 */
fun <A> Iterable<Computation<A>>.raceQuorum(required: Int): Computation<List<A>> =
    raceQuorum(required, *toList().toTypedArray())

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

// ── extension: race as instance method ──────────────────────────────────

/**
 * Races this computation against [other]; the first to **succeed** wins,
 * the loser is cancelled.
 *
 * Extension sugar for `race(this, other)`.
 *
 * ```
 * Computation { fetchFromPrimary() }
 *     .raceAgainst(Computation { fetchFromReplica() })
 * ```
 */
fun <A> Computation<A>.raceAgainst(other: Computation<A>): Computation<A> =
    race(this, other)
