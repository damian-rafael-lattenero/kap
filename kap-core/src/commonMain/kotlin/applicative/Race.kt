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
 *     fa = Effect { fetchFromPrimary() },
 *     fb = Effect { fetchFromFallback() }
 * )
 * ```
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun <A> race(fa: Effect<A>, fb: Effect<A>): Effect<A> = Effect {
    supervisorScope {
        val da = async { runCatching { with(fa) { execute() } } }
        val db = async { runCatching { with(fb) { execute() } } }
        try {
            val (first, other) = select<Pair<Result<A>, Deferred<Result<A>>>> {
                da.onAwait { it to db }
                db.onAwait { it to da }
            }
            first.getOrNull()?.let { return@supervisorScope it }
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
fun <A> raceN(vararg computations: Effect<A>): Effect<A> {
    require(computations.isNotEmpty()) { "raceN requires at least one computation" }
    if (computations.size == 1) return computations[0]
    return Effect {
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
                    val error = result.exceptionOrNull()!!
                    if (error is CancellationException) throw error
                    errors.add(error)
                    pending.remove(winner)
                }
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
fun <A> Iterable<Effect<A>>.raceAll(): Effect<A> =
    raceN(*toList().toTypedArray())

// ── extension: race as instance method ──────────────────────────────────

/**
 * Races this computation against [other]; the first to **succeed** wins,
 * the loser is cancelled.
 *
 * Extension sugar for `race(this, other)`.
 *
 * ```
 * Effect { fetchFromPrimary() }
 *     .raceAgainst(Effect { fetchFromReplica() })
 * ```
 */
fun <A> Effect<A>.raceAgainst(other: Effect<A>): Effect<A> =
    race(this, other)
