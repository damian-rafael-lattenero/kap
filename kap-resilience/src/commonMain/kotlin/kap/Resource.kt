package kap

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration

/**
 * A composable resource with guaranteed cleanup.
 *
 * Unlike [bracket] which requires nesting for multiple resources, [Resource]
 * supports flat composition via [map], [andThen], and [zip]:
 *
 * ```
 * val combined = Resource.zip(
 *     Resource({ openDb() }, { it.close() }),
 *     Resource({ openCache() }, { it.close() }),
 * ) { db, cache -> db to cache }
 *
 * combined.use { (db, cache) ->
 *     kap(::Result)
 *         .with { Kap { db.query("...") } }
 *         .with { Kap { cache.get("key") } }
 * }
 * ```
 *
 * Release always runs in [NonCancellable] context, matching [bracket] semantics.
 */
class Resource<out A> @PublishedApi internal constructor(
    @PublishedApi internal val bind: suspend (suspend (A) -> Unit) -> Unit,
) {
    companion object {
        /**
         * Creates a [Resource] from an [acquire] and [release] pair.
         *
         * [release] is guaranteed to run even on failure or cancellation.
         *
         * @param acquire suspending acquisition; runs once per use.
         * @param release suspending release; runs exactly once, in a
         *   [NonCancellable] context.
         * @return a resource that acquires lazily, at every [use].
         */
        operator fun <A> invoke(
            acquire: suspend () -> A,
            release: suspend (A) -> Unit,
        ): Resource<A> = Resource { use ->
            val a = acquire()
            try {
                use(a)
            } finally {
                withContext(NonCancellable) { release(a) }
            }
        }

        /**
         * Lazily constructs a [Resource] by deferring [block] evaluation
         * until use-time. Useful for conditional resource selection:
         *
         * ```
         * val infra = Resource.defer {
         *     if (config.useRedis) redisResource else inMemoryResource
         * }
         * ```
         *
         * @param block builds the resource at use-time.
         * @return a resource that delegates acquisition/release to [block]'s result.
         */
        fun <A> defer(block: () -> Resource<A>): Resource<A> = Resource { use ->
            block().bind(use)
        }

        // zip 2-22: see ResourceZip.kt (auto-generated via ./gradlew generateResourceZip)
    }

    /**
     * Transforms the resource value. Release still applies to the original.
     *
     * @param f pure transformation applied to the acquired value.
     * @return a resource yielding `f(value)`; the original resource is
     *   released as before.
     */
    fun <B> map(f: (A) -> B): Resource<B> = Resource { use ->
        this@Resource.bind { a -> use(f(a)) }
    }

    /**
     * Composes resources sequentially. Both are released in reverse order.
     *
     * @param f builds the second resource from the first's acquired value.
     * @return a resource that acquires this one, then [f]'s; releases the
     *   second before the first.
     */
    fun <B> andThen(f: (A) -> Resource<B>): Resource<B> = Resource { use ->
        this@Resource.bind { a -> f(a).bind(use) }
    }

    /**
     * Terminal operation: acquires the resource, applies [f], then releases.
     *
     * @param f the computation to run with the acquired value.
     * @return [f]'s result; the release runs even when [f] fails or is cancelled.
     */
    suspend fun <B> use(f: suspend (A) -> B): B {
        var box: Result<B>? = null
        bind { a -> box = Result.success(f(a)) }
        return box?.getOrThrow()
            ?: error("Resource bind did not invoke use callback")
    }

    /**
     * Terminal operation with a timeout: acquires the resource, applies [f]
     * within [duration], then releases — even if the timeout fires.
     *
     * ```
     * dbResource.useWithTimeout(5.seconds) { conn -> conn.query("SELECT ...") }
     * ```
     *
     * @param duration the budget for [f]; on timeout [f]'s result is discarded
     *   with [kotlinx.coroutines.TimeoutCancellationException].
     * @param f the computation to run with the acquired value.
     * @return [f]'s result; the release runs even when the timeout fires.
     * @throws kotlinx.coroutines.TimeoutCancellationException when [f] exceeds [duration].
     */
    suspend fun <B> useWithTimeout(duration: Duration, f: suspend (A) -> B): B {
        var box: Result<B>? = null
        bind { a ->
            box = Result.success(withTimeout(duration) { f(a) })
        }
        return box?.getOrThrow()
            ?: error("Resource bind did not invoke use callback")
    }

    /**
     * Terminal operation returning a [Kap] — integrates with [with] chains.
     *
     * ```
     * val result = dbResource.useKap { conn ->
     *     kap(::Result)
     *         .with { Kap { conn.query("...") } }
     *         .with { Kap { conn.fetchMeta() } }
     * }.evalGraph()
     * ```
     *
     * @param f builds the [Kap] to run with the acquired value.
     * @return a [Kap] that acquires, runs [f]'s graph, and releases — so the
     *   resource's lifecycle composes with the graph's parallelism.
     */
    fun <B> useKap(f: (A) -> Kap<B>): Kap<B> = Kap {
        var box: Result<B>? = null
        this@Resource.bind { a ->
            box = Result.success(with(f(a)) { execute() })
        }
        (box ?: error("Resource bind did not complete")).getOrThrow()
    }
}
