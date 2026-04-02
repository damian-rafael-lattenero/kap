package kap

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.channels.Channel
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

// ── Kap → Flow ──────────────────────────────────────────────────

/**
 * Converts this [Kap] into a [Flow] that emits a single value.
 *
 * ```
 * val userFlow: Flow<User> = Kap { fetchUser() }.toFlow()
 * userFlow.collect { user -> println(user) }
 * ```
 */
fun <A> Kap<A>.toFlow(): Flow<A> = flow {
    emit(coroutineScope { with(this@toFlow) { execute() } })
}

// ── Flow → Kap (collect all) ────────────────────────────────────

/**
 * Creates a [Kap] that collects all emissions from this [Flow] into a [List].
 *
 * ```
 * val users: List<User> = usersFlow.collectAsKap()
 *     .executeGraph()
 * ```
 */
fun <A> Flow<A>.collectAsKap(): Kap<List<A>> = Kap {
    this@collectAsKap.toList()
}

// ── Flow.mapKap ─────────────────────────────────────────────────

/**
 * Maps each element of this [Flow] through a [Kap] with bounded concurrency.
 *
 * When [concurrency] is 1 (default), elements are processed sequentially and
 * emission order is preserved.
 * When [concurrency] > 1, up to [concurrency] elements are processed in parallel
 * using a [channelFlow] with a [Semaphore]-based limiter.
 *
 * **Ordering caveat:** when [concurrency] > 1, results are emitted in
 * *completion order*, not in the original upstream order. If you need ordered
 * output, use [mapKapOrdered] instead.
 *
 * ```
 * userIdsFlow
 *     .mapKap(concurrency = 5) { id -> Kap { fetchUser(id) } }
 *     .collect { user -> process(user) }  // results arrive in completion order
 * ```
 */
fun <A, B> Flow<A>.mapKap(
    concurrency: Int = 1,
    transform: (A) -> Kap<B>,
): Flow<B> {
    require(concurrency >= 1) { "concurrency must be >= 1, was $concurrency" }
    return if (concurrency == 1) {
        map { a -> coroutineScope { with(transform(a)) { execute() } } }
    } else {
        val source = this
        channelFlow {
            val semaphore = Semaphore(concurrency)
            source.collect { a ->
                launch {
                    semaphore.withPermit {
                        val result = coroutineScope { with(transform(a)) { execute() } }
                        send(result)
                    }
                }
            }
        }
    }
}

// ── Flow.mapKapOrdered ─────────────────────────────────────────

/**
 * Like [mapKap] but **preserves upstream emission order** even when
 * [concurrency] > 1.
 *
 * Kaps run in parallel (bounded by [concurrency]), but results are
 * buffered and re-emitted in the same order as the original upstream elements.
 * This is useful when downstream relies on positional correspondence with the
 * source (e.g., zipping results back with input).
 *
 * When [concurrency] is 1, behaves identically to sequential [mapKap].
 *
 * ```
 * flowOf("a", "b", "c")
 *     .mapKapOrdered(concurrency = 3) { s ->
 *         Kap { delay(Random.nextLong(50)); s.uppercase() }
 *     }
 *     .toList() // always ["A", "B", "C"] regardless of completion order
 * ```
 *
 * **Trade-off vs [mapKap]:** ordered emission may hold completed results
 * in memory while waiting for slower earlier elements. If order does not matter,
 * prefer [mapKap] for lower memory pressure and earlier downstream delivery.
 */
fun <A, B> Flow<A>.mapKapOrdered(
    concurrency: Int = 1,
    transform: (A) -> Kap<B>,
): Flow<B> {
    require(concurrency >= 1) { "concurrency must be >= 1, was $concurrency" }
    return if (concurrency == 1) {
        map { a -> coroutineScope { with(transform(a)) { execute() } } }
    } else {
        val source = this
        channelFlow {
            val semaphore = Semaphore(concurrency)
            val deferreds = Channel<Deferred<B>>(Channel.UNLIMITED)

            // Producer: enqueue a deferred per element in upstream order,
            // then launch the computation concurrently.
            launch {
                source.collect { a ->
                    val deferred = CompletableDeferred<B>()
                    deferreds.send(deferred)
                    launch {
                        semaphore.withPermit {
                            try {
                                val result = coroutineScope { with(transform(a)) { execute() } }
                                deferred.complete(result)
                            } catch (e: Throwable) {
                                deferred.completeExceptionally(e)
                            }
                        }
                    }
                }
                deferreds.close()
            }

            // Consumer: await each deferred in order and emit.
            for (deferred in deferreds) {
                send(deferred.await())
            }
        }
    }
}

// ── Flow.filterKap ──────────────────────────────────────────────

/**
 * Filters elements of this [Flow] using a [Kap]-based predicate.
 *
 * Each element is tested sequentially. For parallel filtering, collect into
 * a list first and use [traverse].
 *
 * ```
 * usersFlow
 *     .filterKap { user -> Kap { checkPermission(user) } }
 *     .collect { authorizedUser -> process(authorizedUser) }
 * ```
 */
fun <A> Flow<A>.filterKap(
    predicate: (A) -> Kap<Boolean>,
): Flow<A> = flow {
    this@filterKap.collect { a ->
        val keep = coroutineScope { with(predicate(a)) { execute() } }
        if (keep) emit(a)
    }
}
