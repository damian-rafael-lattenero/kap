package kap

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.supervisorScope

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
 *         Kap { fetchFromReplicaA() },
 *         Kap { fetchFromReplicaB() },
 *         Kap { fetchFromReplicaC() },
 *     )
 * }
 * ```
 *
 * @param required number of successes needed (must be in `1..computations.size`)
 * @param computations the competing computations
 * @return list of exactly [required] successful results (in completion order)
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun <A> raceQuorum(required: Int, vararg computations: Kap<A>): Kap<List<A>> {
    require(computations.isNotEmpty()) { "raceQuorum requires at least one computation" }
    require(required in 1..computations.size) {
        "required must be in 1..${computations.size}, was $required"
    }
    if (required == computations.size) {
        return computations.toList().map { c -> c }.let { list ->
            Kap {
                list.map { c -> async { with(c) { execute() } } }.map { it.await() }
            }
        }
    }
    return Kap {
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
fun <A> Iterable<Kap<A>>.raceQuorum(required: Int): Kap<List<A>> =
    raceQuorum(required, *toList().toTypedArray())
