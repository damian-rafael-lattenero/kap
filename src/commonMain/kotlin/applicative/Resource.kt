package applicative

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration

/**
 * A composable resource with guaranteed cleanup.
 *
 * Unlike [bracket] which requires nesting for multiple resources, [Resource]
 * supports flat composition via [map], [flatMap], and [zip]:
 *
 * ```
 * val combined = Resource.zip(
 *     Resource({ openDb() }, { it.close() }),
 *     Resource({ openCache() }, { it.close() }),
 * ) { db, cache -> db to cache }
 *
 * combined.use { (db, cache) ->
 *     lift2(::Result)
 *         .ap { Computation { db.query("...") } }
 *         .ap { Computation { cache.get("key") } }
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
         */
        fun <A> defer(block: () -> Resource<A>): Resource<A> = Resource { use ->
            block().bind(use)
        }

        // zip 2-22: see ResourceZip.kt (auto-generated via ./gradlew generateResourceZip)
    }

    /** Transforms the resource value. Release still applies to the original. */
    fun <B> map(f: (A) -> B): Resource<B> = Resource { use ->
        this@Resource.bind { a -> use(f(a)) }
    }

    /** Composes resources sequentially. Both are released in reverse order. */
    fun <B> flatMap(f: (A) -> Resource<B>): Resource<B> = Resource { use ->
        this@Resource.bind { a -> f(a).bind(use) }
    }

    /**
     * Terminal operation: acquires the resource, applies [f], then releases.
     */
    suspend fun <B> use(f: suspend (A) -> B): B {
        var box: Result<B>? = null
        bind { a -> box = Result.success(f(a)) }
        return box?.getOrThrow()
            ?: throw IllegalStateException("Resource bind did not invoke use callback")
    }

    /**
     * Terminal operation with a timeout: acquires the resource, applies [f]
     * within [duration], then releases — even if the timeout fires.
     *
     * ```
     * dbResource.useWithTimeout(5.seconds) { conn -> conn.query("SELECT ...") }
     * ```
     */
    suspend fun <B> useWithTimeout(duration: Duration, f: suspend (A) -> B): B {
        var box: Result<B>? = null
        bind { a ->
            box = Result.success(withTimeout(duration) { f(a) })
        }
        return box?.getOrThrow()
            ?: throw IllegalStateException("Resource bind did not invoke use callback")
    }

    /**
     * Terminal operation returning a [Computation] — integrates with [ap] chains.
     *
     * ```
     * val result = Async {
     *     dbResource.use { conn ->
     *         lift2(::Result)
     *             .ap { Computation { conn.query("...") } }
     *             .ap { Computation { conn.fetchMeta() } }
     *     }
     * }
     * ```
     */
    fun <B> useComputation(f: (A) -> Computation<B>): Computation<B> = Computation {
        var result: B? = null
        var completed = false
        this@Resource.bind { a ->
            result = with(f(a)) { execute() }
            completed = true
        }
        if (!completed) throw IllegalStateException("Resource bind did not complete")
        @Suppress("UNCHECKED_CAST")
        result as B
    }
}
