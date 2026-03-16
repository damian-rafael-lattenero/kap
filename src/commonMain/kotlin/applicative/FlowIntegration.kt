package applicative

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

// ── Computation → Flow ──────────────────────────────────────────────────

/**
 * Converts this [Computation] into a [Flow] that emits a single value.
 *
 * ```
 * val userFlow: Flow<User> = Computation { fetchUser() }.toFlow()
 * userFlow.collect { user -> println(user) }
 * ```
 */
fun <A> Computation<A>.toFlow(): Flow<A> = flow {
    emit(coroutineScope { with(this@toFlow) { execute() } })
}

// ── Flow → Computation (collect all) ────────────────────────────────────

/**
 * Creates a [Computation] that collects all emissions from this [Flow] into a [List].
 *
 * ```
 * val users: List<User> = Async {
 *     usersFlow.collectAsComputation()
 * }
 * ```
 */
fun <A> Flow<A>.collectAsComputation(): Computation<List<A>> = Computation {
    this@collectAsComputation.toList()
}

// ── Flow.mapComputation ─────────────────────────────────────────────────

/**
 * Maps each element of this [Flow] through a [Computation] with bounded concurrency.
 *
 * When [concurrency] is 1 (default), elements are processed sequentially.
 * When [concurrency] > 1, up to [concurrency] elements are processed in parallel
 * using a [channelFlow] with a [Semaphore]-based limiter.
 *
 * ```
 * userIdsFlow
 *     .mapComputation(concurrency = 5) { id -> Computation { fetchUser(id) } }
 *     .collect { user -> process(user) }
 * ```
 */
fun <A, B> Flow<A>.mapComputation(
    concurrency: Int = 1,
    transform: (A) -> Computation<B>,
): Flow<B> {
    require(concurrency >= 1) { "concurrency must be >= 1, was $concurrency" }
    return if (concurrency == 1) {
        map { a -> coroutineScope { with(transform(a)) { execute() } } }
    } else {
        val source = this
        channelFlow {
            val semaphore = Semaphore(concurrency)
            source.collect { a ->
                semaphore.withPermit {
                    val result = coroutineScope { with(transform(a)) { execute() } }
                    send(result)
                }
            }
        }
    }
}

// ── Flow.filterComputation ──────────────────────────────────────────────

/**
 * Filters elements of this [Flow] using a [Computation]-based predicate.
 *
 * Each element is tested sequentially. For parallel filtering, collect into
 * a list first and use [traverse].
 *
 * ```
 * usersFlow
 *     .filterComputation { user -> Computation { checkPermission(user) } }
 *     .collect { authorizedUser -> process(authorizedUser) }
 * ```
 */
fun <A> Flow<A>.filterComputation(
    predicate: (A) -> Computation<Boolean>,
): Flow<A> = flow {
    this@filterComputation.collect { a ->
        val keep = coroutineScope { with(predicate(a)) { execute() } }
        if (keep) emit(a)
    }
}
