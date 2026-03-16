// ┌──────────────────────────────────────────────────────────────────────┐
// │  AUTO-GENERATED — do not edit by hand.                               │
// │  Run: ./gradlew generateZipMapN                                      │
// └──────────────────────────────────────────────────────────────────────┘
package applicative

import kotlinx.coroutines.async

// ── zip: parallel combination (3-22 arity) ──────────────────────────────

/** Runs 3 computations in parallel, combining results with [combine]. */
fun <A, B, C, R> zip(
    c1: Computation<A>,
    c2: Computation<B>,
    c3: Computation<C>,
    combine: (A, B, C) -> R,
): Computation<R> = Computation {
    val d1 = async { with(c1) { execute() } }
    val d2 = async { with(c2) { execute() } }
    val d3 = async { with(c3) { execute() } }
    combine(d1.await(), d2.await(), d3.await())
}

/** Runs 4 computations in parallel, combining results with [combine]. */
fun <A, B, C, D, R> zip(
    c1: Computation<A>,
    c2: Computation<B>,
    c3: Computation<C>,
    c4: Computation<D>,
    combine: (A, B, C, D) -> R,
): Computation<R> = Computation {
    val d1 = async { with(c1) { execute() } }
    val d2 = async { with(c2) { execute() } }
    val d3 = async { with(c3) { execute() } }
    val d4 = async { with(c4) { execute() } }
    combine(d1.await(), d2.await(), d3.await(), d4.await())
}

/** Runs 5 computations in parallel, combining results with [combine]. */
fun <A, B, C, D, E, R> zip(
    c1: Computation<A>,
    c2: Computation<B>,
    c3: Computation<C>,
    c4: Computation<D>,
    c5: Computation<E>,
    combine: (A, B, C, D, E) -> R,
): Computation<R> = Computation {
    val d1 = async { with(c1) { execute() } }
    val d2 = async { with(c2) { execute() } }
    val d3 = async { with(c3) { execute() } }
    val d4 = async { with(c4) { execute() } }
    val d5 = async { with(c5) { execute() } }
    combine(d1.await(), d2.await(), d3.await(), d4.await(), d5.await())
}

/** Runs 6 computations in parallel, combining results with [combine]. */
fun <A, B, C, D, E, F, R> zip(
    c1: Computation<A>,
    c2: Computation<B>,
    c3: Computation<C>,
    c4: Computation<D>,
    c5: Computation<E>,
    c6: Computation<F>,
    combine: (A, B, C, D, E, F) -> R,
): Computation<R> = Computation {
    val d1 = async { with(c1) { execute() } }
    val d2 = async { with(c2) { execute() } }
    val d3 = async { with(c3) { execute() } }
    val d4 = async { with(c4) { execute() } }
    val d5 = async { with(c5) { execute() } }
    val d6 = async { with(c6) { execute() } }
    combine(d1.await(), d2.await(), d3.await(), d4.await(), d5.await(), d6.await())
}

/** Runs 7 computations in parallel, combining results with [combine]. */
fun <A, B, C, D, E, F, G, R> zip(
    c1: Computation<A>,
    c2: Computation<B>,
    c3: Computation<C>,
    c4: Computation<D>,
    c5: Computation<E>,
    c6: Computation<F>,
    c7: Computation<G>,
    combine: (A, B, C, D, E, F, G) -> R,
): Computation<R> = Computation {
    val d1 = async { with(c1) { execute() } }
    val d2 = async { with(c2) { execute() } }
    val d3 = async { with(c3) { execute() } }
    val d4 = async { with(c4) { execute() } }
    val d5 = async { with(c5) { execute() } }
    val d6 = async { with(c6) { execute() } }
    val d7 = async { with(c7) { execute() } }
    combine(d1.await(), d2.await(), d3.await(), d4.await(), d5.await(), d6.await(), d7.await())
}

/** Runs 8 computations in parallel, combining results with [combine]. */
fun <A, B, C, D, E, F, G, H, R> zip(
    c1: Computation<A>,
    c2: Computation<B>,
    c3: Computation<C>,
    c4: Computation<D>,
    c5: Computation<E>,
    c6: Computation<F>,
    c7: Computation<G>,
    c8: Computation<H>,
    combine: (A, B, C, D, E, F, G, H) -> R,
): Computation<R> = Computation {
    val d1 = async { with(c1) { execute() } }
    val d2 = async { with(c2) { execute() } }
    val d3 = async { with(c3) { execute() } }
    val d4 = async { with(c4) { execute() } }
    val d5 = async { with(c5) { execute() } }
    val d6 = async { with(c6) { execute() } }
    val d7 = async { with(c7) { execute() } }
    val d8 = async { with(c8) { execute() } }
    combine(d1.await(), d2.await(), d3.await(), d4.await(), d5.await(), d6.await(), d7.await(), d8.await())
}

/** Runs 9 computations in parallel, combining results with [combine]. */
fun <A, B, C, D, E, F, G, H, I, R> zip(
    c1: Computation<A>,
    c2: Computation<B>,
    c3: Computation<C>,
    c4: Computation<D>,
    c5: Computation<E>,
    c6: Computation<F>,
    c7: Computation<G>,
    c8: Computation<H>,
    c9: Computation<I>,
    combine: (A, B, C, D, E, F, G, H, I) -> R,
): Computation<R> = Computation {
    val d1 = async { with(c1) { execute() } }
    val d2 = async { with(c2) { execute() } }
    val d3 = async { with(c3) { execute() } }
    val d4 = async { with(c4) { execute() } }
    val d5 = async { with(c5) { execute() } }
    val d6 = async { with(c6) { execute() } }
    val d7 = async { with(c7) { execute() } }
    val d8 = async { with(c8) { execute() } }
    val d9 = async { with(c9) { execute() } }
    combine(d1.await(), d2.await(), d3.await(), d4.await(), d5.await(), d6.await(), d7.await(), d8.await(), d9.await())
}

/** Runs 10 computations in parallel, combining results with [combine]. */
fun <A, B, C, D, E, F, G, H, I, J, R> zip(
    c1: Computation<A>,
    c2: Computation<B>,
    c3: Computation<C>,
    c4: Computation<D>,
    c5: Computation<E>,
    c6: Computation<F>,
    c7: Computation<G>,
    c8: Computation<H>,
    c9: Computation<I>,
    c10: Computation<J>,
    combine: (A, B, C, D, E, F, G, H, I, J) -> R,
): Computation<R> = Computation {
    val d1 = async { with(c1) { execute() } }
    val d2 = async { with(c2) { execute() } }
    val d3 = async { with(c3) { execute() } }
    val d4 = async { with(c4) { execute() } }
    val d5 = async { with(c5) { execute() } }
    val d6 = async { with(c6) { execute() } }
    val d7 = async { with(c7) { execute() } }
    val d8 = async { with(c8) { execute() } }
    val d9 = async { with(c9) { execute() } }
    val d10 = async { with(c10) { execute() } }
    combine(d1.await(), d2.await(), d3.await(), d4.await(), d5.await(), d6.await(), d7.await(), d8.await(), d9.await(), d10.await())
}

/** Runs 11 computations in parallel, combining results with [combine]. */
fun <A, B, C, D, E, F, G, H, I, J, K, R> zip(
    c1: Computation<A>,
    c2: Computation<B>,
    c3: Computation<C>,
    c4: Computation<D>,
    c5: Computation<E>,
    c6: Computation<F>,
    c7: Computation<G>,
    c8: Computation<H>,
    c9: Computation<I>,
    c10: Computation<J>,
    c11: Computation<K>,
    combine: (A, B, C, D, E, F, G, H, I, J, K) -> R,
): Computation<R> = Computation {
    val d1 = async { with(c1) { execute() } }
    val d2 = async { with(c2) { execute() } }
    val d3 = async { with(c3) { execute() } }
    val d4 = async { with(c4) { execute() } }
    val d5 = async { with(c5) { execute() } }
    val d6 = async { with(c6) { execute() } }
    val d7 = async { with(c7) { execute() } }
    val d8 = async { with(c8) { execute() } }
    val d9 = async { with(c9) { execute() } }
    val d10 = async { with(c10) { execute() } }
    val d11 = async { with(c11) { execute() } }
    combine(d1.await(), d2.await(), d3.await(), d4.await(), d5.await(), d6.await(), d7.await(), d8.await(), d9.await(), d10.await(), d11.await())
}

/** Runs 12 computations in parallel, combining results with [combine]. */
fun <A, B, C, D, E, F, G, H, I, J, K, L, R> zip(
    c1: Computation<A>,
    c2: Computation<B>,
    c3: Computation<C>,
    c4: Computation<D>,
    c5: Computation<E>,
    c6: Computation<F>,
    c7: Computation<G>,
    c8: Computation<H>,
    c9: Computation<I>,
    c10: Computation<J>,
    c11: Computation<K>,
    c12: Computation<L>,
    combine: (A, B, C, D, E, F, G, H, I, J, K, L) -> R,
): Computation<R> = Computation {
    val d1 = async { with(c1) { execute() } }
    val d2 = async { with(c2) { execute() } }
    val d3 = async { with(c3) { execute() } }
    val d4 = async { with(c4) { execute() } }
    val d5 = async { with(c5) { execute() } }
    val d6 = async { with(c6) { execute() } }
    val d7 = async { with(c7) { execute() } }
    val d8 = async { with(c8) { execute() } }
    val d9 = async { with(c9) { execute() } }
    val d10 = async { with(c10) { execute() } }
    val d11 = async { with(c11) { execute() } }
    val d12 = async { with(c12) { execute() } }
    combine(d1.await(), d2.await(), d3.await(), d4.await(), d5.await(), d6.await(), d7.await(), d8.await(), d9.await(), d10.await(), d11.await(), d12.await())
}

/** Runs 13 computations in parallel, combining results with [combine]. */
fun <A, B, C, D, E, F, G, H, I, J, K, L, M, R> zip(
    c1: Computation<A>,
    c2: Computation<B>,
    c3: Computation<C>,
    c4: Computation<D>,
    c5: Computation<E>,
    c6: Computation<F>,
    c7: Computation<G>,
    c8: Computation<H>,
    c9: Computation<I>,
    c10: Computation<J>,
    c11: Computation<K>,
    c12: Computation<L>,
    c13: Computation<M>,
    combine: (A, B, C, D, E, F, G, H, I, J, K, L, M) -> R,
): Computation<R> = Computation {
    val d1 = async { with(c1) { execute() } }
    val d2 = async { with(c2) { execute() } }
    val d3 = async { with(c3) { execute() } }
    val d4 = async { with(c4) { execute() } }
    val d5 = async { with(c5) { execute() } }
    val d6 = async { with(c6) { execute() } }
    val d7 = async { with(c7) { execute() } }
    val d8 = async { with(c8) { execute() } }
    val d9 = async { with(c9) { execute() } }
    val d10 = async { with(c10) { execute() } }
    val d11 = async { with(c11) { execute() } }
    val d12 = async { with(c12) { execute() } }
    val d13 = async { with(c13) { execute() } }
    combine(d1.await(), d2.await(), d3.await(), d4.await(), d5.await(), d6.await(), d7.await(), d8.await(), d9.await(), d10.await(), d11.await(), d12.await(), d13.await())
}

/** Runs 14 computations in parallel, combining results with [combine]. */
fun <A, B, C, D, E, F, G, H, I, J, K, L, M, N, R> zip(
    c1: Computation<A>,
    c2: Computation<B>,
    c3: Computation<C>,
    c4: Computation<D>,
    c5: Computation<E>,
    c6: Computation<F>,
    c7: Computation<G>,
    c8: Computation<H>,
    c9: Computation<I>,
    c10: Computation<J>,
    c11: Computation<K>,
    c12: Computation<L>,
    c13: Computation<M>,
    c14: Computation<N>,
    combine: (A, B, C, D, E, F, G, H, I, J, K, L, M, N) -> R,
): Computation<R> = Computation {
    val d1 = async { with(c1) { execute() } }
    val d2 = async { with(c2) { execute() } }
    val d3 = async { with(c3) { execute() } }
    val d4 = async { with(c4) { execute() } }
    val d5 = async { with(c5) { execute() } }
    val d6 = async { with(c6) { execute() } }
    val d7 = async { with(c7) { execute() } }
    val d8 = async { with(c8) { execute() } }
    val d9 = async { with(c9) { execute() } }
    val d10 = async { with(c10) { execute() } }
    val d11 = async { with(c11) { execute() } }
    val d12 = async { with(c12) { execute() } }
    val d13 = async { with(c13) { execute() } }
    val d14 = async { with(c14) { execute() } }
    combine(d1.await(), d2.await(), d3.await(), d4.await(), d5.await(), d6.await(), d7.await(), d8.await(), d9.await(), d10.await(), d11.await(), d12.await(), d13.await(), d14.await())
}

/** Runs 15 computations in parallel, combining results with [combine]. */
fun <A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, R> zip(
    c1: Computation<A>,
    c2: Computation<B>,
    c3: Computation<C>,
    c4: Computation<D>,
    c5: Computation<E>,
    c6: Computation<F>,
    c7: Computation<G>,
    c8: Computation<H>,
    c9: Computation<I>,
    c10: Computation<J>,
    c11: Computation<K>,
    c12: Computation<L>,
    c13: Computation<M>,
    c14: Computation<N>,
    c15: Computation<O>,
    combine: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O) -> R,
): Computation<R> = Computation {
    val d1 = async { with(c1) { execute() } }
    val d2 = async { with(c2) { execute() } }
    val d3 = async { with(c3) { execute() } }
    val d4 = async { with(c4) { execute() } }
    val d5 = async { with(c5) { execute() } }
    val d6 = async { with(c6) { execute() } }
    val d7 = async { with(c7) { execute() } }
    val d8 = async { with(c8) { execute() } }
    val d9 = async { with(c9) { execute() } }
    val d10 = async { with(c10) { execute() } }
    val d11 = async { with(c11) { execute() } }
    val d12 = async { with(c12) { execute() } }
    val d13 = async { with(c13) { execute() } }
    val d14 = async { with(c14) { execute() } }
    val d15 = async { with(c15) { execute() } }
    combine(d1.await(), d2.await(), d3.await(), d4.await(), d5.await(), d6.await(), d7.await(), d8.await(), d9.await(), d10.await(), d11.await(), d12.await(), d13.await(), d14.await(), d15.await())
}

/** Runs 16 computations in parallel, combining results with [combine]. */
fun <A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, R> zip(
    c1: Computation<A>,
    c2: Computation<B>,
    c3: Computation<C>,
    c4: Computation<D>,
    c5: Computation<E>,
    c6: Computation<F>,
    c7: Computation<G>,
    c8: Computation<H>,
    c9: Computation<I>,
    c10: Computation<J>,
    c11: Computation<K>,
    c12: Computation<L>,
    c13: Computation<M>,
    c14: Computation<N>,
    c15: Computation<O>,
    c16: Computation<P>,
    combine: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P) -> R,
): Computation<R> = Computation {
    val d1 = async { with(c1) { execute() } }
    val d2 = async { with(c2) { execute() } }
    val d3 = async { with(c3) { execute() } }
    val d4 = async { with(c4) { execute() } }
    val d5 = async { with(c5) { execute() } }
    val d6 = async { with(c6) { execute() } }
    val d7 = async { with(c7) { execute() } }
    val d8 = async { with(c8) { execute() } }
    val d9 = async { with(c9) { execute() } }
    val d10 = async { with(c10) { execute() } }
    val d11 = async { with(c11) { execute() } }
    val d12 = async { with(c12) { execute() } }
    val d13 = async { with(c13) { execute() } }
    val d14 = async { with(c14) { execute() } }
    val d15 = async { with(c15) { execute() } }
    val d16 = async { with(c16) { execute() } }
    combine(d1.await(), d2.await(), d3.await(), d4.await(), d5.await(), d6.await(), d7.await(), d8.await(), d9.await(), d10.await(), d11.await(), d12.await(), d13.await(), d14.await(), d15.await(), d16.await())
}

/** Runs 17 computations in parallel, combining results with [combine]. */
fun <A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R> zip(
    c1: Computation<A>,
    c2: Computation<B>,
    c3: Computation<C>,
    c4: Computation<D>,
    c5: Computation<E>,
    c6: Computation<F>,
    c7: Computation<G>,
    c8: Computation<H>,
    c9: Computation<I>,
    c10: Computation<J>,
    c11: Computation<K>,
    c12: Computation<L>,
    c13: Computation<M>,
    c14: Computation<N>,
    c15: Computation<O>,
    c16: Computation<P>,
    c17: Computation<Q>,
    combine: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q) -> R,
): Computation<R> = Computation {
    val d1 = async { with(c1) { execute() } }
    val d2 = async { with(c2) { execute() } }
    val d3 = async { with(c3) { execute() } }
    val d4 = async { with(c4) { execute() } }
    val d5 = async { with(c5) { execute() } }
    val d6 = async { with(c6) { execute() } }
    val d7 = async { with(c7) { execute() } }
    val d8 = async { with(c8) { execute() } }
    val d9 = async { with(c9) { execute() } }
    val d10 = async { with(c10) { execute() } }
    val d11 = async { with(c11) { execute() } }
    val d12 = async { with(c12) { execute() } }
    val d13 = async { with(c13) { execute() } }
    val d14 = async { with(c14) { execute() } }
    val d15 = async { with(c15) { execute() } }
    val d16 = async { with(c16) { execute() } }
    val d17 = async { with(c17) { execute() } }
    combine(d1.await(), d2.await(), d3.await(), d4.await(), d5.await(), d6.await(), d7.await(), d8.await(), d9.await(), d10.await(), d11.await(), d12.await(), d13.await(), d14.await(), d15.await(), d16.await(), d17.await())
}

/** Runs 18 computations in parallel, combining results with [combine]. */
fun <A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, S, R> zip(
    c1: Computation<A>,
    c2: Computation<B>,
    c3: Computation<C>,
    c4: Computation<D>,
    c5: Computation<E>,
    c6: Computation<F>,
    c7: Computation<G>,
    c8: Computation<H>,
    c9: Computation<I>,
    c10: Computation<J>,
    c11: Computation<K>,
    c12: Computation<L>,
    c13: Computation<M>,
    c14: Computation<N>,
    c15: Computation<O>,
    c16: Computation<P>,
    c17: Computation<Q>,
    c18: Computation<S>,
    combine: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, S) -> R,
): Computation<R> = Computation {
    val d1 = async { with(c1) { execute() } }
    val d2 = async { with(c2) { execute() } }
    val d3 = async { with(c3) { execute() } }
    val d4 = async { with(c4) { execute() } }
    val d5 = async { with(c5) { execute() } }
    val d6 = async { with(c6) { execute() } }
    val d7 = async { with(c7) { execute() } }
    val d8 = async { with(c8) { execute() } }
    val d9 = async { with(c9) { execute() } }
    val d10 = async { with(c10) { execute() } }
    val d11 = async { with(c11) { execute() } }
    val d12 = async { with(c12) { execute() } }
    val d13 = async { with(c13) { execute() } }
    val d14 = async { with(c14) { execute() } }
    val d15 = async { with(c15) { execute() } }
    val d16 = async { with(c16) { execute() } }
    val d17 = async { with(c17) { execute() } }
    val d18 = async { with(c18) { execute() } }
    combine(d1.await(), d2.await(), d3.await(), d4.await(), d5.await(), d6.await(), d7.await(), d8.await(), d9.await(), d10.await(), d11.await(), d12.await(), d13.await(), d14.await(), d15.await(), d16.await(), d17.await(), d18.await())
}

/** Runs 19 computations in parallel, combining results with [combine]. */
fun <A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, S, T, R> zip(
    c1: Computation<A>,
    c2: Computation<B>,
    c3: Computation<C>,
    c4: Computation<D>,
    c5: Computation<E>,
    c6: Computation<F>,
    c7: Computation<G>,
    c8: Computation<H>,
    c9: Computation<I>,
    c10: Computation<J>,
    c11: Computation<K>,
    c12: Computation<L>,
    c13: Computation<M>,
    c14: Computation<N>,
    c15: Computation<O>,
    c16: Computation<P>,
    c17: Computation<Q>,
    c18: Computation<S>,
    c19: Computation<T>,
    combine: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, S, T) -> R,
): Computation<R> = Computation {
    val d1 = async { with(c1) { execute() } }
    val d2 = async { with(c2) { execute() } }
    val d3 = async { with(c3) { execute() } }
    val d4 = async { with(c4) { execute() } }
    val d5 = async { with(c5) { execute() } }
    val d6 = async { with(c6) { execute() } }
    val d7 = async { with(c7) { execute() } }
    val d8 = async { with(c8) { execute() } }
    val d9 = async { with(c9) { execute() } }
    val d10 = async { with(c10) { execute() } }
    val d11 = async { with(c11) { execute() } }
    val d12 = async { with(c12) { execute() } }
    val d13 = async { with(c13) { execute() } }
    val d14 = async { with(c14) { execute() } }
    val d15 = async { with(c15) { execute() } }
    val d16 = async { with(c16) { execute() } }
    val d17 = async { with(c17) { execute() } }
    val d18 = async { with(c18) { execute() } }
    val d19 = async { with(c19) { execute() } }
    combine(d1.await(), d2.await(), d3.await(), d4.await(), d5.await(), d6.await(), d7.await(), d8.await(), d9.await(), d10.await(), d11.await(), d12.await(), d13.await(), d14.await(), d15.await(), d16.await(), d17.await(), d18.await(), d19.await())
}

/** Runs 20 computations in parallel, combining results with [combine]. */
fun <A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, S, T, U, R> zip(
    c1: Computation<A>,
    c2: Computation<B>,
    c3: Computation<C>,
    c4: Computation<D>,
    c5: Computation<E>,
    c6: Computation<F>,
    c7: Computation<G>,
    c8: Computation<H>,
    c9: Computation<I>,
    c10: Computation<J>,
    c11: Computation<K>,
    c12: Computation<L>,
    c13: Computation<M>,
    c14: Computation<N>,
    c15: Computation<O>,
    c16: Computation<P>,
    c17: Computation<Q>,
    c18: Computation<S>,
    c19: Computation<T>,
    c20: Computation<U>,
    combine: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, S, T, U) -> R,
): Computation<R> = Computation {
    val d1 = async { with(c1) { execute() } }
    val d2 = async { with(c2) { execute() } }
    val d3 = async { with(c3) { execute() } }
    val d4 = async { with(c4) { execute() } }
    val d5 = async { with(c5) { execute() } }
    val d6 = async { with(c6) { execute() } }
    val d7 = async { with(c7) { execute() } }
    val d8 = async { with(c8) { execute() } }
    val d9 = async { with(c9) { execute() } }
    val d10 = async { with(c10) { execute() } }
    val d11 = async { with(c11) { execute() } }
    val d12 = async { with(c12) { execute() } }
    val d13 = async { with(c13) { execute() } }
    val d14 = async { with(c14) { execute() } }
    val d15 = async { with(c15) { execute() } }
    val d16 = async { with(c16) { execute() } }
    val d17 = async { with(c17) { execute() } }
    val d18 = async { with(c18) { execute() } }
    val d19 = async { with(c19) { execute() } }
    val d20 = async { with(c20) { execute() } }
    combine(d1.await(), d2.await(), d3.await(), d4.await(), d5.await(), d6.await(), d7.await(), d8.await(), d9.await(), d10.await(), d11.await(), d12.await(), d13.await(), d14.await(), d15.await(), d16.await(), d17.await(), d18.await(), d19.await(), d20.await())
}

/** Runs 21 computations in parallel, combining results with [combine]. */
fun <A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, S, T, U, V, R> zip(
    c1: Computation<A>,
    c2: Computation<B>,
    c3: Computation<C>,
    c4: Computation<D>,
    c5: Computation<E>,
    c6: Computation<F>,
    c7: Computation<G>,
    c8: Computation<H>,
    c9: Computation<I>,
    c10: Computation<J>,
    c11: Computation<K>,
    c12: Computation<L>,
    c13: Computation<M>,
    c14: Computation<N>,
    c15: Computation<O>,
    c16: Computation<P>,
    c17: Computation<Q>,
    c18: Computation<S>,
    c19: Computation<T>,
    c20: Computation<U>,
    c21: Computation<V>,
    combine: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, S, T, U, V) -> R,
): Computation<R> = Computation {
    val d1 = async { with(c1) { execute() } }
    val d2 = async { with(c2) { execute() } }
    val d3 = async { with(c3) { execute() } }
    val d4 = async { with(c4) { execute() } }
    val d5 = async { with(c5) { execute() } }
    val d6 = async { with(c6) { execute() } }
    val d7 = async { with(c7) { execute() } }
    val d8 = async { with(c8) { execute() } }
    val d9 = async { with(c9) { execute() } }
    val d10 = async { with(c10) { execute() } }
    val d11 = async { with(c11) { execute() } }
    val d12 = async { with(c12) { execute() } }
    val d13 = async { with(c13) { execute() } }
    val d14 = async { with(c14) { execute() } }
    val d15 = async { with(c15) { execute() } }
    val d16 = async { with(c16) { execute() } }
    val d17 = async { with(c17) { execute() } }
    val d18 = async { with(c18) { execute() } }
    val d19 = async { with(c19) { execute() } }
    val d20 = async { with(c20) { execute() } }
    val d21 = async { with(c21) { execute() } }
    combine(d1.await(), d2.await(), d3.await(), d4.await(), d5.await(), d6.await(), d7.await(), d8.await(), d9.await(), d10.await(), d11.await(), d12.await(), d13.await(), d14.await(), d15.await(), d16.await(), d17.await(), d18.await(), d19.await(), d20.await(), d21.await())
}

/** Runs 22 computations in parallel, combining results with [combine]. */
fun <A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, S, T, U, V, W, R> zip(
    c1: Computation<A>,
    c2: Computation<B>,
    c3: Computation<C>,
    c4: Computation<D>,
    c5: Computation<E>,
    c6: Computation<F>,
    c7: Computation<G>,
    c8: Computation<H>,
    c9: Computation<I>,
    c10: Computation<J>,
    c11: Computation<K>,
    c12: Computation<L>,
    c13: Computation<M>,
    c14: Computation<N>,
    c15: Computation<O>,
    c16: Computation<P>,
    c17: Computation<Q>,
    c18: Computation<S>,
    c19: Computation<T>,
    c20: Computation<U>,
    c21: Computation<V>,
    c22: Computation<W>,
    combine: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, S, T, U, V, W) -> R,
): Computation<R> = Computation {
    val d1 = async { with(c1) { execute() } }
    val d2 = async { with(c2) { execute() } }
    val d3 = async { with(c3) { execute() } }
    val d4 = async { with(c4) { execute() } }
    val d5 = async { with(c5) { execute() } }
    val d6 = async { with(c6) { execute() } }
    val d7 = async { with(c7) { execute() } }
    val d8 = async { with(c8) { execute() } }
    val d9 = async { with(c9) { execute() } }
    val d10 = async { with(c10) { execute() } }
    val d11 = async { with(c11) { execute() } }
    val d12 = async { with(c12) { execute() } }
    val d13 = async { with(c13) { execute() } }
    val d14 = async { with(c14) { execute() } }
    val d15 = async { with(c15) { execute() } }
    val d16 = async { with(c16) { execute() } }
    val d17 = async { with(c17) { execute() } }
    val d18 = async { with(c18) { execute() } }
    val d19 = async { with(c19) { execute() } }
    val d20 = async { with(c20) { execute() } }
    val d21 = async { with(c21) { execute() } }
    val d22 = async { with(c22) { execute() } }
    combine(d1.await(), d2.await(), d3.await(), d4.await(), d5.await(), d6.await(), d7.await(), d8.await(), d9.await(), d10.await(), d11.await(), d12.await(), d13.await(), d14.await(), d15.await(), d16.await(), d17.await(), d18.await(), d19.await(), d20.await(), d21.await(), d22.await())
}

// ── mapN: alias for zip (2-22 arity) ────────────────────────────────────

/** Runs 2 computations in parallel, combining results with [combine]. Alias for [zip]. */
fun <A, B, R> mapN(
    c1: Computation<A>,
    c2: Computation<B>,
    combine: (A, B) -> R,
): Computation<R> = c1.zip(c2, combine)

/** Runs 3 computations in parallel, combining results with [combine]. Alias for [zip]. */
fun <A, B, C, R> mapN(
    c1: Computation<A>,
    c2: Computation<B>,
    c3: Computation<C>,
    combine: (A, B, C) -> R,
): Computation<R> = zip(c1, c2, c3, combine)

/** Runs 4 computations in parallel, combining results with [combine]. Alias for [zip]. */
fun <A, B, C, D, R> mapN(
    c1: Computation<A>,
    c2: Computation<B>,
    c3: Computation<C>,
    c4: Computation<D>,
    combine: (A, B, C, D) -> R,
): Computation<R> = zip(c1, c2, c3, c4, combine)

/** Runs 5 computations in parallel, combining results with [combine]. Alias for [zip]. */
fun <A, B, C, D, E, R> mapN(
    c1: Computation<A>,
    c2: Computation<B>,
    c3: Computation<C>,
    c4: Computation<D>,
    c5: Computation<E>,
    combine: (A, B, C, D, E) -> R,
): Computation<R> = zip(c1, c2, c3, c4, c5, combine)

/** Runs 6 computations in parallel, combining results with [combine]. Alias for [zip]. */
fun <A, B, C, D, E, F, R> mapN(
    c1: Computation<A>,
    c2: Computation<B>,
    c3: Computation<C>,
    c4: Computation<D>,
    c5: Computation<E>,
    c6: Computation<F>,
    combine: (A, B, C, D, E, F) -> R,
): Computation<R> = zip(c1, c2, c3, c4, c5, c6, combine)

/** Runs 7 computations in parallel, combining results with [combine]. Alias for [zip]. */
fun <A, B, C, D, E, F, G, R> mapN(
    c1: Computation<A>,
    c2: Computation<B>,
    c3: Computation<C>,
    c4: Computation<D>,
    c5: Computation<E>,
    c6: Computation<F>,
    c7: Computation<G>,
    combine: (A, B, C, D, E, F, G) -> R,
): Computation<R> = zip(c1, c2, c3, c4, c5, c6, c7, combine)

/** Runs 8 computations in parallel, combining results with [combine]. Alias for [zip]. */
fun <A, B, C, D, E, F, G, H, R> mapN(
    c1: Computation<A>,
    c2: Computation<B>,
    c3: Computation<C>,
    c4: Computation<D>,
    c5: Computation<E>,
    c6: Computation<F>,
    c7: Computation<G>,
    c8: Computation<H>,
    combine: (A, B, C, D, E, F, G, H) -> R,
): Computation<R> = zip(c1, c2, c3, c4, c5, c6, c7, c8, combine)

/** Runs 9 computations in parallel, combining results with [combine]. Alias for [zip]. */
fun <A, B, C, D, E, F, G, H, I, R> mapN(
    c1: Computation<A>,
    c2: Computation<B>,
    c3: Computation<C>,
    c4: Computation<D>,
    c5: Computation<E>,
    c6: Computation<F>,
    c7: Computation<G>,
    c8: Computation<H>,
    c9: Computation<I>,
    combine: (A, B, C, D, E, F, G, H, I) -> R,
): Computation<R> = zip(c1, c2, c3, c4, c5, c6, c7, c8, c9, combine)

/** Runs 10 computations in parallel, combining results with [combine]. Alias for [zip]. */
fun <A, B, C, D, E, F, G, H, I, J, R> mapN(
    c1: Computation<A>,
    c2: Computation<B>,
    c3: Computation<C>,
    c4: Computation<D>,
    c5: Computation<E>,
    c6: Computation<F>,
    c7: Computation<G>,
    c8: Computation<H>,
    c9: Computation<I>,
    c10: Computation<J>,
    combine: (A, B, C, D, E, F, G, H, I, J) -> R,
): Computation<R> = zip(c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, combine)

/** Runs 11 computations in parallel, combining results with [combine]. Alias for [zip]. */
fun <A, B, C, D, E, F, G, H, I, J, K, R> mapN(
    c1: Computation<A>,
    c2: Computation<B>,
    c3: Computation<C>,
    c4: Computation<D>,
    c5: Computation<E>,
    c6: Computation<F>,
    c7: Computation<G>,
    c8: Computation<H>,
    c9: Computation<I>,
    c10: Computation<J>,
    c11: Computation<K>,
    combine: (A, B, C, D, E, F, G, H, I, J, K) -> R,
): Computation<R> = zip(c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, combine)

/** Runs 12 computations in parallel, combining results with [combine]. Alias for [zip]. */
fun <A, B, C, D, E, F, G, H, I, J, K, L, R> mapN(
    c1: Computation<A>,
    c2: Computation<B>,
    c3: Computation<C>,
    c4: Computation<D>,
    c5: Computation<E>,
    c6: Computation<F>,
    c7: Computation<G>,
    c8: Computation<H>,
    c9: Computation<I>,
    c10: Computation<J>,
    c11: Computation<K>,
    c12: Computation<L>,
    combine: (A, B, C, D, E, F, G, H, I, J, K, L) -> R,
): Computation<R> = zip(c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, combine)

/** Runs 13 computations in parallel, combining results with [combine]. Alias for [zip]. */
fun <A, B, C, D, E, F, G, H, I, J, K, L, M, R> mapN(
    c1: Computation<A>,
    c2: Computation<B>,
    c3: Computation<C>,
    c4: Computation<D>,
    c5: Computation<E>,
    c6: Computation<F>,
    c7: Computation<G>,
    c8: Computation<H>,
    c9: Computation<I>,
    c10: Computation<J>,
    c11: Computation<K>,
    c12: Computation<L>,
    c13: Computation<M>,
    combine: (A, B, C, D, E, F, G, H, I, J, K, L, M) -> R,
): Computation<R> = zip(c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13, combine)

/** Runs 14 computations in parallel, combining results with [combine]. Alias for [zip]. */
fun <A, B, C, D, E, F, G, H, I, J, K, L, M, N, R> mapN(
    c1: Computation<A>,
    c2: Computation<B>,
    c3: Computation<C>,
    c4: Computation<D>,
    c5: Computation<E>,
    c6: Computation<F>,
    c7: Computation<G>,
    c8: Computation<H>,
    c9: Computation<I>,
    c10: Computation<J>,
    c11: Computation<K>,
    c12: Computation<L>,
    c13: Computation<M>,
    c14: Computation<N>,
    combine: (A, B, C, D, E, F, G, H, I, J, K, L, M, N) -> R,
): Computation<R> = zip(c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13, c14, combine)

/** Runs 15 computations in parallel, combining results with [combine]. Alias for [zip]. */
fun <A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, R> mapN(
    c1: Computation<A>,
    c2: Computation<B>,
    c3: Computation<C>,
    c4: Computation<D>,
    c5: Computation<E>,
    c6: Computation<F>,
    c7: Computation<G>,
    c8: Computation<H>,
    c9: Computation<I>,
    c10: Computation<J>,
    c11: Computation<K>,
    c12: Computation<L>,
    c13: Computation<M>,
    c14: Computation<N>,
    c15: Computation<O>,
    combine: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O) -> R,
): Computation<R> = zip(c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13, c14, c15, combine)

/** Runs 16 computations in parallel, combining results with [combine]. Alias for [zip]. */
fun <A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, R> mapN(
    c1: Computation<A>,
    c2: Computation<B>,
    c3: Computation<C>,
    c4: Computation<D>,
    c5: Computation<E>,
    c6: Computation<F>,
    c7: Computation<G>,
    c8: Computation<H>,
    c9: Computation<I>,
    c10: Computation<J>,
    c11: Computation<K>,
    c12: Computation<L>,
    c13: Computation<M>,
    c14: Computation<N>,
    c15: Computation<O>,
    c16: Computation<P>,
    combine: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P) -> R,
): Computation<R> = zip(c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13, c14, c15, c16, combine)

/** Runs 17 computations in parallel, combining results with [combine]. Alias for [zip]. */
fun <A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R> mapN(
    c1: Computation<A>,
    c2: Computation<B>,
    c3: Computation<C>,
    c4: Computation<D>,
    c5: Computation<E>,
    c6: Computation<F>,
    c7: Computation<G>,
    c8: Computation<H>,
    c9: Computation<I>,
    c10: Computation<J>,
    c11: Computation<K>,
    c12: Computation<L>,
    c13: Computation<M>,
    c14: Computation<N>,
    c15: Computation<O>,
    c16: Computation<P>,
    c17: Computation<Q>,
    combine: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q) -> R,
): Computation<R> = zip(c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13, c14, c15, c16, c17, combine)

/** Runs 18 computations in parallel, combining results with [combine]. Alias for [zip]. */
fun <A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, S, R> mapN(
    c1: Computation<A>,
    c2: Computation<B>,
    c3: Computation<C>,
    c4: Computation<D>,
    c5: Computation<E>,
    c6: Computation<F>,
    c7: Computation<G>,
    c8: Computation<H>,
    c9: Computation<I>,
    c10: Computation<J>,
    c11: Computation<K>,
    c12: Computation<L>,
    c13: Computation<M>,
    c14: Computation<N>,
    c15: Computation<O>,
    c16: Computation<P>,
    c17: Computation<Q>,
    c18: Computation<S>,
    combine: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, S) -> R,
): Computation<R> = zip(c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13, c14, c15, c16, c17, c18, combine)

/** Runs 19 computations in parallel, combining results with [combine]. Alias for [zip]. */
fun <A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, S, T, R> mapN(
    c1: Computation<A>,
    c2: Computation<B>,
    c3: Computation<C>,
    c4: Computation<D>,
    c5: Computation<E>,
    c6: Computation<F>,
    c7: Computation<G>,
    c8: Computation<H>,
    c9: Computation<I>,
    c10: Computation<J>,
    c11: Computation<K>,
    c12: Computation<L>,
    c13: Computation<M>,
    c14: Computation<N>,
    c15: Computation<O>,
    c16: Computation<P>,
    c17: Computation<Q>,
    c18: Computation<S>,
    c19: Computation<T>,
    combine: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, S, T) -> R,
): Computation<R> = zip(c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13, c14, c15, c16, c17, c18, c19, combine)

/** Runs 20 computations in parallel, combining results with [combine]. Alias for [zip]. */
fun <A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, S, T, U, R> mapN(
    c1: Computation<A>,
    c2: Computation<B>,
    c3: Computation<C>,
    c4: Computation<D>,
    c5: Computation<E>,
    c6: Computation<F>,
    c7: Computation<G>,
    c8: Computation<H>,
    c9: Computation<I>,
    c10: Computation<J>,
    c11: Computation<K>,
    c12: Computation<L>,
    c13: Computation<M>,
    c14: Computation<N>,
    c15: Computation<O>,
    c16: Computation<P>,
    c17: Computation<Q>,
    c18: Computation<S>,
    c19: Computation<T>,
    c20: Computation<U>,
    combine: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, S, T, U) -> R,
): Computation<R> = zip(c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13, c14, c15, c16, c17, c18, c19, c20, combine)

/** Runs 21 computations in parallel, combining results with [combine]. Alias for [zip]. */
fun <A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, S, T, U, V, R> mapN(
    c1: Computation<A>,
    c2: Computation<B>,
    c3: Computation<C>,
    c4: Computation<D>,
    c5: Computation<E>,
    c6: Computation<F>,
    c7: Computation<G>,
    c8: Computation<H>,
    c9: Computation<I>,
    c10: Computation<J>,
    c11: Computation<K>,
    c12: Computation<L>,
    c13: Computation<M>,
    c14: Computation<N>,
    c15: Computation<O>,
    c16: Computation<P>,
    c17: Computation<Q>,
    c18: Computation<S>,
    c19: Computation<T>,
    c20: Computation<U>,
    c21: Computation<V>,
    combine: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, S, T, U, V) -> R,
): Computation<R> = zip(c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13, c14, c15, c16, c17, c18, c19, c20, c21, combine)

/** Runs 22 computations in parallel, combining results with [combine]. Alias for [zip]. */
fun <A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, S, T, U, V, W, R> mapN(
    c1: Computation<A>,
    c2: Computation<B>,
    c3: Computation<C>,
    c4: Computation<D>,
    c5: Computation<E>,
    c6: Computation<F>,
    c7: Computation<G>,
    c8: Computation<H>,
    c9: Computation<I>,
    c10: Computation<J>,
    c11: Computation<K>,
    c12: Computation<L>,
    c13: Computation<M>,
    c14: Computation<N>,
    c15: Computation<O>,
    c16: Computation<P>,
    c17: Computation<Q>,
    c18: Computation<S>,
    c19: Computation<T>,
    c20: Computation<U>,
    c21: Computation<V>,
    c22: Computation<W>,
    combine: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, S, T, U, V, W) -> R,
): Computation<R> = zip(c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13, c14, c15, c16, c17, c18, c19, c20, c21, c22, combine)
