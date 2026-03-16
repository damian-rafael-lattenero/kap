package applicative

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

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
         * Combines two resources, releasing both even if one fails.
         */
        fun <A, B, C> zip(
            ra: Resource<A>,
            rb: Resource<B>,
            f: (A, B) -> C,
        ): Resource<C> = Resource { use ->
            ra.bind { a ->
                rb.bind { b ->
                    use(f(a, b))
                }
            }
        }

        /** Combines three resources. */
        fun <A, B, C, D> zip(
            ra: Resource<A>,
            rb: Resource<B>,
            rc: Resource<C>,
            f: (A, B, C) -> D,
        ): Resource<D> = Resource { use ->
            ra.bind { a ->
                rb.bind { b ->
                    rc.bind { c ->
                        use(f(a, b, c))
                    }
                }
            }
        }

        /** Combines four resources. */
        fun <A, B, C, D, E> zip(
            ra: Resource<A>,
            rb: Resource<B>,
            rc: Resource<C>,
            rd: Resource<D>,
            f: (A, B, C, D) -> E,
        ): Resource<E> = Resource { use ->
            ra.bind { a ->
                rb.bind { b ->
                    rc.bind { c ->
                        rd.bind { d ->
                            use(f(a, b, c, d))
                        }
                    }
                }
            }
        }

        /** Combines five resources. */
        fun <A, B, C, D, E, F> zip(
            ra: Resource<A>,
            rb: Resource<B>,
            rc: Resource<C>,
            rd: Resource<D>,
            re: Resource<E>,
            f: (A, B, C, D, E) -> F,
        ): Resource<F> = Resource { use ->
            ra.bind { a -> rb.bind { b -> rc.bind { c ->
                rd.bind { d -> re.bind { e -> use(f(a, b, c, d, e)) } } } } }
        }

        /** Combines six resources. */
        fun <A, B, C, D, E, F, G> zip(
            ra: Resource<A>,
            rb: Resource<B>,
            rc: Resource<C>,
            rd: Resource<D>,
            re: Resource<E>,
            rf: Resource<F>,
            f: (A, B, C, D, E, F) -> G,
        ): Resource<G> = Resource { use ->
            ra.bind { a -> rb.bind { b -> rc.bind { c ->
                rd.bind { d -> re.bind { e -> rf.bind { ff ->
                    use(f(a, b, c, d, e, ff)) } } } } } }
        }

        /** Combines seven resources. */
        fun <A, B, C, D, E, F, G, H> zip(
            ra: Resource<A>,
            rb: Resource<B>,
            rc: Resource<C>,
            rd: Resource<D>,
            re: Resource<E>,
            rf: Resource<F>,
            rg: Resource<G>,
            f: (A, B, C, D, E, F, G) -> H,
        ): Resource<H> = Resource { use ->
            ra.bind { a -> rb.bind { b -> rc.bind { c ->
                rd.bind { d -> re.bind { e -> rf.bind { ff ->
                    rg.bind { g -> use(f(a, b, c, d, e, ff, g)) } } } } } } }
        }

        /** Combines eight resources. */
        fun <A, B, C, D, E, F, G, H, I> zip(
            ra: Resource<A>,
            rb: Resource<B>,
            rc: Resource<C>,
            rd: Resource<D>,
            re: Resource<E>,
            rf: Resource<F>,
            rg: Resource<G>,
            rh: Resource<H>,
            f: (A, B, C, D, E, F, G, H) -> I,
        ): Resource<I> = Resource { use ->
            ra.bind { a -> rb.bind { b -> rc.bind { c ->
                rd.bind { d -> re.bind { e -> rf.bind { ff ->
                    rg.bind { g -> rh.bind { h ->
                        use(f(a, b, c, d, e, ff, g, h)) } } } } } } } }
        }

        /** Combines nine resources. */
        fun <A, B, C, D, E, F, G, H, I, J> zip(
            ra: Resource<A>,
            rb: Resource<B>,
            rc: Resource<C>,
            rd: Resource<D>,
            re: Resource<E>,
            rf: Resource<F>,
            rg: Resource<G>,
            rh: Resource<H>,
            ri: Resource<I>,
            f: (A, B, C, D, E, F, G, H, I) -> J,
        ): Resource<J> = Resource { use ->
            ra.bind { a -> rb.bind { b -> rc.bind { c ->
                rd.bind { d -> re.bind { e -> rf.bind { ff ->
                    rg.bind { g -> rh.bind { h -> ri.bind { i ->
                        use(f(a, b, c, d, e, ff, g, h, i)) } } } } } } } } }
        }

        /** Combines ten resources. */
        fun <A, B, C, D, E, F, G, H, I, J, K> zip(
            ra: Resource<A>,
            rb: Resource<B>,
            rc: Resource<C>,
            rd: Resource<D>,
            re: Resource<E>,
            rf: Resource<F>,
            rg: Resource<G>,
            rh: Resource<H>,
            ri: Resource<I>,
            rj: Resource<J>,
            f: (A, B, C, D, E, F, G, H, I, J) -> K,
        ): Resource<K> = Resource { use ->
            ra.bind { a -> rb.bind { b -> rc.bind { c ->
                rd.bind { d -> re.bind { e -> rf.bind { ff ->
                    rg.bind { g -> rh.bind { h -> ri.bind { i ->
                        rj.bind { j ->
                            use(f(a, b, c, d, e, ff, g, h, i, j)) } } } } } } } } } }
        }

        /** Combines eleven resources. */
        fun <A, B, C, D, E, F, G, H, I, J, K, L> zip(
            ra: Resource<A>,
            rb: Resource<B>,
            rc: Resource<C>,
            rd: Resource<D>,
            re: Resource<E>,
            rf: Resource<F>,
            rg: Resource<G>,
            rh: Resource<H>,
            ri: Resource<I>,
            rj: Resource<J>,
            rk: Resource<K>,
            f: (A, B, C, D, E, F, G, H, I, J, K) -> L,
        ): Resource<L> = Resource { use ->
            ra.bind { a -> rb.bind { b -> rc.bind { c ->
                rd.bind { d -> re.bind { e -> rf.bind { ff ->
                    rg.bind { g -> rh.bind { h -> ri.bind { i ->
                        rj.bind { j -> rk.bind { k ->
                            use(f(a, b, c, d, e, ff, g, h, i, j, k)) } } } } } } } } } } }
        }

        /** Combines twelve resources. */
        fun <A, B, C, D, E, F, G, H, I, J, K, L, M> zip(
            ra: Resource<A>,
            rb: Resource<B>,
            rc: Resource<C>,
            rd: Resource<D>,
            re: Resource<E>,
            rf: Resource<F>,
            rg: Resource<G>,
            rh: Resource<H>,
            ri: Resource<I>,
            rj: Resource<J>,
            rk: Resource<K>,
            rl: Resource<L>,
            f: (A, B, C, D, E, F, G, H, I, J, K, L) -> M,
        ): Resource<M> = Resource { use ->
            ra.bind { a -> rb.bind { b -> rc.bind { c ->
                rd.bind { d -> re.bind { e -> rf.bind { ff ->
                    rg.bind { g -> rh.bind { h -> ri.bind { i ->
                        rj.bind { j -> rk.bind { k -> rl.bind { l ->
                            use(f(a, b, c, d, e, ff, g, h, i, j, k, l)) } } } } } } } } } } } }
        }
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
