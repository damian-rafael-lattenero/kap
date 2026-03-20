// ┌──────────────────────────────────────────────────────────────────────┐
// │  AUTO-GENERATED — do not edit by hand.                               │
// │  Run: ./gradlew :kap-resilience:generateResourceZip                  │
// └──────────────────────────────────────────────────────────────────────┘
package applicative

fun <A, B, R> Resource.Companion.zip(
    r1: Resource<A>,
    r2: Resource<B>,
    combine: (A, B) -> R,
): Resource<R> = Resource { use ->
    r1.bind { v1 -> r2.bind { v2 ->
        use(combine(v1, v2))
     } }
}

fun <A, B, C, R> Resource.Companion.zip(
    r1: Resource<A>,
    r2: Resource<B>,
    r3: Resource<C>,
    combine: (A, B, C) -> R,
): Resource<R> = Resource { use ->
    r1.bind { v1 -> r2.bind { v2 -> r3.bind { v3 ->
        use(combine(v1, v2, v3))
     } } }
}

fun <A, B, C, D, R> Resource.Companion.zip(
    r1: Resource<A>,
    r2: Resource<B>,
    r3: Resource<C>,
    r4: Resource<D>,
    combine: (A, B, C, D) -> R,
): Resource<R> = Resource { use ->
    r1.bind { v1 -> r2.bind { v2 -> r3.bind { v3 -> r4.bind { v4 ->
        use(combine(v1, v2, v3, v4))
     } } } }
}

fun <A, B, C, D, E, R> Resource.Companion.zip(
    r1: Resource<A>,
    r2: Resource<B>,
    r3: Resource<C>,
    r4: Resource<D>,
    r5: Resource<E>,
    combine: (A, B, C, D, E) -> R,
): Resource<R> = Resource { use ->
    r1.bind { v1 -> r2.bind { v2 -> r3.bind { v3 -> r4.bind { v4 -> r5.bind { v5 ->
        use(combine(v1, v2, v3, v4, v5))
     } } } } }
}

fun <A, B, C, D, E, F, R> Resource.Companion.zip(
    r1: Resource<A>,
    r2: Resource<B>,
    r3: Resource<C>,
    r4: Resource<D>,
    r5: Resource<E>,
    r6: Resource<F>,
    combine: (A, B, C, D, E, F) -> R,
): Resource<R> = Resource { use ->
    r1.bind { v1 -> r2.bind { v2 -> r3.bind { v3 -> r4.bind { v4 -> r5.bind { v5 -> r6.bind { v6 ->
        use(combine(v1, v2, v3, v4, v5, v6))
     } } } } } }
}

fun <A, B, C, D, E, F, G, R> Resource.Companion.zip(
    r1: Resource<A>,
    r2: Resource<B>,
    r3: Resource<C>,
    r4: Resource<D>,
    r5: Resource<E>,
    r6: Resource<F>,
    r7: Resource<G>,
    combine: (A, B, C, D, E, F, G) -> R,
): Resource<R> = Resource { use ->
    r1.bind { v1 -> r2.bind { v2 -> r3.bind { v3 -> r4.bind { v4 -> r5.bind { v5 -> r6.bind { v6 -> r7.bind { v7 ->
        use(combine(v1, v2, v3, v4, v5, v6, v7))
     } } } } } } }
}

fun <A, B, C, D, E, F, G, H, R> Resource.Companion.zip(
    r1: Resource<A>,
    r2: Resource<B>,
    r3: Resource<C>,
    r4: Resource<D>,
    r5: Resource<E>,
    r6: Resource<F>,
    r7: Resource<G>,
    r8: Resource<H>,
    combine: (A, B, C, D, E, F, G, H) -> R,
): Resource<R> = Resource { use ->
    r1.bind { v1 -> r2.bind { v2 -> r3.bind { v3 -> r4.bind { v4 -> r5.bind { v5 -> r6.bind { v6 -> r7.bind { v7 -> r8.bind { v8 ->
        use(combine(v1, v2, v3, v4, v5, v6, v7, v8))
     } } } } } } } }
}

fun <A, B, C, D, E, F, G, H, I, R> Resource.Companion.zip(
    r1: Resource<A>,
    r2: Resource<B>,
    r3: Resource<C>,
    r4: Resource<D>,
    r5: Resource<E>,
    r6: Resource<F>,
    r7: Resource<G>,
    r8: Resource<H>,
    r9: Resource<I>,
    combine: (A, B, C, D, E, F, G, H, I) -> R,
): Resource<R> = Resource { use ->
    r1.bind { v1 -> r2.bind { v2 -> r3.bind { v3 -> r4.bind { v4 -> r5.bind { v5 -> r6.bind { v6 -> r7.bind { v7 -> r8.bind { v8 -> r9.bind { v9 ->
        use(combine(v1, v2, v3, v4, v5, v6, v7, v8, v9))
     } } } } } } } } }
}

fun <A, B, C, D, E, F, G, H, I, J, R> Resource.Companion.zip(
    r1: Resource<A>,
    r2: Resource<B>,
    r3: Resource<C>,
    r4: Resource<D>,
    r5: Resource<E>,
    r6: Resource<F>,
    r7: Resource<G>,
    r8: Resource<H>,
    r9: Resource<I>,
    r10: Resource<J>,
    combine: (A, B, C, D, E, F, G, H, I, J) -> R,
): Resource<R> = Resource { use ->
    r1.bind { v1 -> r2.bind { v2 -> r3.bind { v3 -> r4.bind { v4 -> r5.bind { v5 -> r6.bind { v6 -> r7.bind { v7 -> r8.bind { v8 -> r9.bind { v9 -> r10.bind { v10 ->
        use(combine(v1, v2, v3, v4, v5, v6, v7, v8, v9, v10))
     } } } } } } } } } }
}

fun <A, B, C, D, E, F, G, H, I, J, K, R> Resource.Companion.zip(
    r1: Resource<A>,
    r2: Resource<B>,
    r3: Resource<C>,
    r4: Resource<D>,
    r5: Resource<E>,
    r6: Resource<F>,
    r7: Resource<G>,
    r8: Resource<H>,
    r9: Resource<I>,
    r10: Resource<J>,
    r11: Resource<K>,
    combine: (A, B, C, D, E, F, G, H, I, J, K) -> R,
): Resource<R> = Resource { use ->
    r1.bind { v1 -> r2.bind { v2 -> r3.bind { v3 -> r4.bind { v4 -> r5.bind { v5 -> r6.bind { v6 -> r7.bind { v7 -> r8.bind { v8 -> r9.bind { v9 -> r10.bind { v10 -> r11.bind { v11 ->
        use(combine(v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11))
     } } } } } } } } } } }
}

fun <A, B, C, D, E, F, G, H, I, J, K, L, R> Resource.Companion.zip(
    r1: Resource<A>,
    r2: Resource<B>,
    r3: Resource<C>,
    r4: Resource<D>,
    r5: Resource<E>,
    r6: Resource<F>,
    r7: Resource<G>,
    r8: Resource<H>,
    r9: Resource<I>,
    r10: Resource<J>,
    r11: Resource<K>,
    r12: Resource<L>,
    combine: (A, B, C, D, E, F, G, H, I, J, K, L) -> R,
): Resource<R> = Resource { use ->
    r1.bind { v1 -> r2.bind { v2 -> r3.bind { v3 -> r4.bind { v4 -> r5.bind { v5 -> r6.bind { v6 -> r7.bind { v7 -> r8.bind { v8 -> r9.bind { v9 -> r10.bind { v10 -> r11.bind { v11 -> r12.bind { v12 ->
        use(combine(v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12))
     } } } } } } } } } } } }
}

fun <A, B, C, D, E, F, G, H, I, J, K, L, M, R> Resource.Companion.zip(
    r1: Resource<A>,
    r2: Resource<B>,
    r3: Resource<C>,
    r4: Resource<D>,
    r5: Resource<E>,
    r6: Resource<F>,
    r7: Resource<G>,
    r8: Resource<H>,
    r9: Resource<I>,
    r10: Resource<J>,
    r11: Resource<K>,
    r12: Resource<L>,
    r13: Resource<M>,
    combine: (A, B, C, D, E, F, G, H, I, J, K, L, M) -> R,
): Resource<R> = Resource { use ->
    r1.bind { v1 -> r2.bind { v2 -> r3.bind { v3 -> r4.bind { v4 -> r5.bind { v5 -> r6.bind { v6 -> r7.bind { v7 -> r8.bind { v8 -> r9.bind { v9 -> r10.bind { v10 -> r11.bind { v11 -> r12.bind { v12 -> r13.bind { v13 ->
        use(combine(v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13))
     } } } } } } } } } } } } }
}

fun <A, B, C, D, E, F, G, H, I, J, K, L, M, N, R> Resource.Companion.zip(
    r1: Resource<A>,
    r2: Resource<B>,
    r3: Resource<C>,
    r4: Resource<D>,
    r5: Resource<E>,
    r6: Resource<F>,
    r7: Resource<G>,
    r8: Resource<H>,
    r9: Resource<I>,
    r10: Resource<J>,
    r11: Resource<K>,
    r12: Resource<L>,
    r13: Resource<M>,
    r14: Resource<N>,
    combine: (A, B, C, D, E, F, G, H, I, J, K, L, M, N) -> R,
): Resource<R> = Resource { use ->
    r1.bind { v1 -> r2.bind { v2 -> r3.bind { v3 -> r4.bind { v4 -> r5.bind { v5 -> r6.bind { v6 -> r7.bind { v7 -> r8.bind { v8 -> r9.bind { v9 -> r10.bind { v10 -> r11.bind { v11 -> r12.bind { v12 -> r13.bind { v13 -> r14.bind { v14 ->
        use(combine(v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14))
     } } } } } } } } } } } } } }
}

fun <A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, R> Resource.Companion.zip(
    r1: Resource<A>,
    r2: Resource<B>,
    r3: Resource<C>,
    r4: Resource<D>,
    r5: Resource<E>,
    r6: Resource<F>,
    r7: Resource<G>,
    r8: Resource<H>,
    r9: Resource<I>,
    r10: Resource<J>,
    r11: Resource<K>,
    r12: Resource<L>,
    r13: Resource<M>,
    r14: Resource<N>,
    r15: Resource<O>,
    combine: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O) -> R,
): Resource<R> = Resource { use ->
    r1.bind { v1 -> r2.bind { v2 -> r3.bind { v3 -> r4.bind { v4 -> r5.bind { v5 -> r6.bind { v6 -> r7.bind { v7 -> r8.bind { v8 -> r9.bind { v9 -> r10.bind { v10 -> r11.bind { v11 -> r12.bind { v12 -> r13.bind { v13 -> r14.bind { v14 -> r15.bind { v15 ->
        use(combine(v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14, v15))
     } } } } } } } } } } } } } } }
}

fun <A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, R> Resource.Companion.zip(
    r1: Resource<A>,
    r2: Resource<B>,
    r3: Resource<C>,
    r4: Resource<D>,
    r5: Resource<E>,
    r6: Resource<F>,
    r7: Resource<G>,
    r8: Resource<H>,
    r9: Resource<I>,
    r10: Resource<J>,
    r11: Resource<K>,
    r12: Resource<L>,
    r13: Resource<M>,
    r14: Resource<N>,
    r15: Resource<O>,
    r16: Resource<P>,
    combine: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P) -> R,
): Resource<R> = Resource { use ->
    r1.bind { v1 -> r2.bind { v2 -> r3.bind { v3 -> r4.bind { v4 -> r5.bind { v5 -> r6.bind { v6 -> r7.bind { v7 -> r8.bind { v8 -> r9.bind { v9 -> r10.bind { v10 -> r11.bind { v11 -> r12.bind { v12 -> r13.bind { v13 -> r14.bind { v14 -> r15.bind { v15 -> r16.bind { v16 ->
        use(combine(v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14, v15, v16))
     } } } } } } } } } } } } } } } }
}

fun <A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R> Resource.Companion.zip(
    r1: Resource<A>,
    r2: Resource<B>,
    r3: Resource<C>,
    r4: Resource<D>,
    r5: Resource<E>,
    r6: Resource<F>,
    r7: Resource<G>,
    r8: Resource<H>,
    r9: Resource<I>,
    r10: Resource<J>,
    r11: Resource<K>,
    r12: Resource<L>,
    r13: Resource<M>,
    r14: Resource<N>,
    r15: Resource<O>,
    r16: Resource<P>,
    r17: Resource<Q>,
    combine: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q) -> R,
): Resource<R> = Resource { use ->
    r1.bind { v1 -> r2.bind { v2 -> r3.bind { v3 -> r4.bind { v4 -> r5.bind { v5 -> r6.bind { v6 -> r7.bind { v7 -> r8.bind { v8 -> r9.bind { v9 -> r10.bind { v10 -> r11.bind { v11 -> r12.bind { v12 -> r13.bind { v13 -> r14.bind { v14 -> r15.bind { v15 -> r16.bind { v16 -> r17.bind { v17 ->
        use(combine(v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14, v15, v16, v17))
     } } } } } } } } } } } } } } } } }
}

fun <A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, S, R> Resource.Companion.zip(
    r1: Resource<A>,
    r2: Resource<B>,
    r3: Resource<C>,
    r4: Resource<D>,
    r5: Resource<E>,
    r6: Resource<F>,
    r7: Resource<G>,
    r8: Resource<H>,
    r9: Resource<I>,
    r10: Resource<J>,
    r11: Resource<K>,
    r12: Resource<L>,
    r13: Resource<M>,
    r14: Resource<N>,
    r15: Resource<O>,
    r16: Resource<P>,
    r17: Resource<Q>,
    r18: Resource<S>,
    combine: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, S) -> R,
): Resource<R> = Resource { use ->
    r1.bind { v1 -> r2.bind { v2 -> r3.bind { v3 -> r4.bind { v4 -> r5.bind { v5 -> r6.bind { v6 -> r7.bind { v7 -> r8.bind { v8 -> r9.bind { v9 -> r10.bind { v10 -> r11.bind { v11 -> r12.bind { v12 -> r13.bind { v13 -> r14.bind { v14 -> r15.bind { v15 -> r16.bind { v16 -> r17.bind { v17 -> r18.bind { v18 ->
        use(combine(v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14, v15, v16, v17, v18))
     } } } } } } } } } } } } } } } } } }
}

fun <A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, S, T, R> Resource.Companion.zip(
    r1: Resource<A>,
    r2: Resource<B>,
    r3: Resource<C>,
    r4: Resource<D>,
    r5: Resource<E>,
    r6: Resource<F>,
    r7: Resource<G>,
    r8: Resource<H>,
    r9: Resource<I>,
    r10: Resource<J>,
    r11: Resource<K>,
    r12: Resource<L>,
    r13: Resource<M>,
    r14: Resource<N>,
    r15: Resource<O>,
    r16: Resource<P>,
    r17: Resource<Q>,
    r18: Resource<S>,
    r19: Resource<T>,
    combine: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, S, T) -> R,
): Resource<R> = Resource { use ->
    r1.bind { v1 -> r2.bind { v2 -> r3.bind { v3 -> r4.bind { v4 -> r5.bind { v5 -> r6.bind { v6 -> r7.bind { v7 -> r8.bind { v8 -> r9.bind { v9 -> r10.bind { v10 -> r11.bind { v11 -> r12.bind { v12 -> r13.bind { v13 -> r14.bind { v14 -> r15.bind { v15 -> r16.bind { v16 -> r17.bind { v17 -> r18.bind { v18 -> r19.bind { v19 ->
        use(combine(v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14, v15, v16, v17, v18, v19))
     } } } } } } } } } } } } } } } } } } }
}

fun <A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, S, T, U, R> Resource.Companion.zip(
    r1: Resource<A>,
    r2: Resource<B>,
    r3: Resource<C>,
    r4: Resource<D>,
    r5: Resource<E>,
    r6: Resource<F>,
    r7: Resource<G>,
    r8: Resource<H>,
    r9: Resource<I>,
    r10: Resource<J>,
    r11: Resource<K>,
    r12: Resource<L>,
    r13: Resource<M>,
    r14: Resource<N>,
    r15: Resource<O>,
    r16: Resource<P>,
    r17: Resource<Q>,
    r18: Resource<S>,
    r19: Resource<T>,
    r20: Resource<U>,
    combine: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, S, T, U) -> R,
): Resource<R> = Resource { use ->
    r1.bind { v1 -> r2.bind { v2 -> r3.bind { v3 -> r4.bind { v4 -> r5.bind { v5 -> r6.bind { v6 -> r7.bind { v7 -> r8.bind { v8 -> r9.bind { v9 -> r10.bind { v10 -> r11.bind { v11 -> r12.bind { v12 -> r13.bind { v13 -> r14.bind { v14 -> r15.bind { v15 -> r16.bind { v16 -> r17.bind { v17 -> r18.bind { v18 -> r19.bind { v19 -> r20.bind { v20 ->
        use(combine(v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14, v15, v16, v17, v18, v19, v20))
     } } } } } } } } } } } } } } } } } } } }
}

fun <A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, S, T, U, V, R> Resource.Companion.zip(
    r1: Resource<A>,
    r2: Resource<B>,
    r3: Resource<C>,
    r4: Resource<D>,
    r5: Resource<E>,
    r6: Resource<F>,
    r7: Resource<G>,
    r8: Resource<H>,
    r9: Resource<I>,
    r10: Resource<J>,
    r11: Resource<K>,
    r12: Resource<L>,
    r13: Resource<M>,
    r14: Resource<N>,
    r15: Resource<O>,
    r16: Resource<P>,
    r17: Resource<Q>,
    r18: Resource<S>,
    r19: Resource<T>,
    r20: Resource<U>,
    r21: Resource<V>,
    combine: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, S, T, U, V) -> R,
): Resource<R> = Resource { use ->
    r1.bind { v1 -> r2.bind { v2 -> r3.bind { v3 -> r4.bind { v4 -> r5.bind { v5 -> r6.bind { v6 -> r7.bind { v7 -> r8.bind { v8 -> r9.bind { v9 -> r10.bind { v10 -> r11.bind { v11 -> r12.bind { v12 -> r13.bind { v13 -> r14.bind { v14 -> r15.bind { v15 -> r16.bind { v16 -> r17.bind { v17 -> r18.bind { v18 -> r19.bind { v19 -> r20.bind { v20 -> r21.bind { v21 ->
        use(combine(v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14, v15, v16, v17, v18, v19, v20, v21))
     } } } } } } } } } } } } } } } } } } } } }
}

fun <A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, S, T, U, V, W, R> Resource.Companion.zip(
    r1: Resource<A>,
    r2: Resource<B>,
    r3: Resource<C>,
    r4: Resource<D>,
    r5: Resource<E>,
    r6: Resource<F>,
    r7: Resource<G>,
    r8: Resource<H>,
    r9: Resource<I>,
    r10: Resource<J>,
    r11: Resource<K>,
    r12: Resource<L>,
    r13: Resource<M>,
    r14: Resource<N>,
    r15: Resource<O>,
    r16: Resource<P>,
    r17: Resource<Q>,
    r18: Resource<S>,
    r19: Resource<T>,
    r20: Resource<U>,
    r21: Resource<V>,
    r22: Resource<W>,
    combine: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, S, T, U, V, W) -> R,
): Resource<R> = Resource { use ->
    r1.bind { v1 -> r2.bind { v2 -> r3.bind { v3 -> r4.bind { v4 -> r5.bind { v5 -> r6.bind { v6 -> r7.bind { v7 -> r8.bind { v8 -> r9.bind { v9 -> r10.bind { v10 -> r11.bind { v11 -> r12.bind { v12 -> r13.bind { v13 -> r14.bind { v14 -> r15.bind { v15 -> r16.bind { v16 -> r17.bind { v17 -> r18.bind { v18 -> r19.bind { v19 -> r20.bind { v20 -> r21.bind { v21 -> r22.bind { v22 ->
        use(combine(v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14, v15, v16, v17, v18, v19, v20, v21, v22))
     } } } } } } } } } } } } } } } } } } } } } }
}
