// ┌──────────────────────────────────────────────────────────────────────┐
// │  AUTO-GENERATED — do not edit by hand.                               │
// │  Run: ./gradlew :kap-core:generateZipCombine                          │
// └──────────────────────────────────────────────────────────────────────┘
package kap

import kotlinx.coroutines.async

fun <A, B, C, R> zip(
    c1: Kap<A>,
    c2: Kap<B>,
    c3: Kap<C>,
    combine: (A, B, C) -> R,
): Kap<R> = Kap {
    val d1 = async { with(c1) { execute() } }
    val d2 = async { with(c2) { execute() } }
    val d3 = async { with(c3) { execute() } }
    combine(d1.await(), d2.await(), d3.await())
}

fun <A, B, C, D, R> zip(
    c1: Kap<A>,
    c2: Kap<B>,
    c3: Kap<C>,
    c4: Kap<D>,
    combine: (A, B, C, D) -> R,
): Kap<R> = Kap {
    val d1 = async { with(c1) { execute() } }
    val d2 = async { with(c2) { execute() } }
    val d3 = async { with(c3) { execute() } }
    val d4 = async { with(c4) { execute() } }
    combine(d1.await(), d2.await(), d3.await(), d4.await())
}

fun <A, B, C, D, E, R> zip(
    c1: Kap<A>,
    c2: Kap<B>,
    c3: Kap<C>,
    c4: Kap<D>,
    c5: Kap<E>,
    combine: (A, B, C, D, E) -> R,
): Kap<R> = Kap {
    val d1 = async { with(c1) { execute() } }
    val d2 = async { with(c2) { execute() } }
    val d3 = async { with(c3) { execute() } }
    val d4 = async { with(c4) { execute() } }
    val d5 = async { with(c5) { execute() } }
    combine(d1.await(), d2.await(), d3.await(), d4.await(), d5.await())
}

fun <A, B, C, D, E, F, R> zip(
    c1: Kap<A>,
    c2: Kap<B>,
    c3: Kap<C>,
    c4: Kap<D>,
    c5: Kap<E>,
    c6: Kap<F>,
    combine: (A, B, C, D, E, F) -> R,
): Kap<R> = Kap {
    val d1 = async { with(c1) { execute() } }
    val d2 = async { with(c2) { execute() } }
    val d3 = async { with(c3) { execute() } }
    val d4 = async { with(c4) { execute() } }
    val d5 = async { with(c5) { execute() } }
    val d6 = async { with(c6) { execute() } }
    combine(d1.await(), d2.await(), d3.await(), d4.await(), d5.await(), d6.await())
}

fun <A, B, C, D, E, F, G, R> zip(
    c1: Kap<A>,
    c2: Kap<B>,
    c3: Kap<C>,
    c4: Kap<D>,
    c5: Kap<E>,
    c6: Kap<F>,
    c7: Kap<G>,
    combine: (A, B, C, D, E, F, G) -> R,
): Kap<R> = Kap {
    val d1 = async { with(c1) { execute() } }
    val d2 = async { with(c2) { execute() } }
    val d3 = async { with(c3) { execute() } }
    val d4 = async { with(c4) { execute() } }
    val d5 = async { with(c5) { execute() } }
    val d6 = async { with(c6) { execute() } }
    val d7 = async { with(c7) { execute() } }
    combine(d1.await(), d2.await(), d3.await(), d4.await(), d5.await(), d6.await(), d7.await())
}

fun <A, B, C, D, E, F, G, H, R> zip(
    c1: Kap<A>,
    c2: Kap<B>,
    c3: Kap<C>,
    c4: Kap<D>,
    c5: Kap<E>,
    c6: Kap<F>,
    c7: Kap<G>,
    c8: Kap<H>,
    combine: (A, B, C, D, E, F, G, H) -> R,
): Kap<R> = Kap {
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

fun <A, B, C, D, E, F, G, H, I, R> zip(
    c1: Kap<A>,
    c2: Kap<B>,
    c3: Kap<C>,
    c4: Kap<D>,
    c5: Kap<E>,
    c6: Kap<F>,
    c7: Kap<G>,
    c8: Kap<H>,
    c9: Kap<I>,
    combine: (A, B, C, D, E, F, G, H, I) -> R,
): Kap<R> = Kap {
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

fun <A, B, C, D, E, F, G, H, I, J, R> zip(
    c1: Kap<A>,
    c2: Kap<B>,
    c3: Kap<C>,
    c4: Kap<D>,
    c5: Kap<E>,
    c6: Kap<F>,
    c7: Kap<G>,
    c8: Kap<H>,
    c9: Kap<I>,
    c10: Kap<J>,
    combine: (A, B, C, D, E, F, G, H, I, J) -> R,
): Kap<R> = Kap {
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

fun <A, B, C, D, E, F, G, H, I, J, K, R> zip(
    c1: Kap<A>,
    c2: Kap<B>,
    c3: Kap<C>,
    c4: Kap<D>,
    c5: Kap<E>,
    c6: Kap<F>,
    c7: Kap<G>,
    c8: Kap<H>,
    c9: Kap<I>,
    c10: Kap<J>,
    c11: Kap<K>,
    combine: (A, B, C, D, E, F, G, H, I, J, K) -> R,
): Kap<R> = Kap {
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

fun <A, B, C, D, E, F, G, H, I, J, K, L, R> zip(
    c1: Kap<A>,
    c2: Kap<B>,
    c3: Kap<C>,
    c4: Kap<D>,
    c5: Kap<E>,
    c6: Kap<F>,
    c7: Kap<G>,
    c8: Kap<H>,
    c9: Kap<I>,
    c10: Kap<J>,
    c11: Kap<K>,
    c12: Kap<L>,
    combine: (A, B, C, D, E, F, G, H, I, J, K, L) -> R,
): Kap<R> = Kap {
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

fun <A, B, C, D, E, F, G, H, I, J, K, L, M, R> zip(
    c1: Kap<A>,
    c2: Kap<B>,
    c3: Kap<C>,
    c4: Kap<D>,
    c5: Kap<E>,
    c6: Kap<F>,
    c7: Kap<G>,
    c8: Kap<H>,
    c9: Kap<I>,
    c10: Kap<J>,
    c11: Kap<K>,
    c12: Kap<L>,
    c13: Kap<M>,
    combine: (A, B, C, D, E, F, G, H, I, J, K, L, M) -> R,
): Kap<R> = Kap {
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

fun <A, B, C, D, E, F, G, H, I, J, K, L, M, N, R> zip(
    c1: Kap<A>,
    c2: Kap<B>,
    c3: Kap<C>,
    c4: Kap<D>,
    c5: Kap<E>,
    c6: Kap<F>,
    c7: Kap<G>,
    c8: Kap<H>,
    c9: Kap<I>,
    c10: Kap<J>,
    c11: Kap<K>,
    c12: Kap<L>,
    c13: Kap<M>,
    c14: Kap<N>,
    combine: (A, B, C, D, E, F, G, H, I, J, K, L, M, N) -> R,
): Kap<R> = Kap {
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

fun <A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, R> zip(
    c1: Kap<A>,
    c2: Kap<B>,
    c3: Kap<C>,
    c4: Kap<D>,
    c5: Kap<E>,
    c6: Kap<F>,
    c7: Kap<G>,
    c8: Kap<H>,
    c9: Kap<I>,
    c10: Kap<J>,
    c11: Kap<K>,
    c12: Kap<L>,
    c13: Kap<M>,
    c14: Kap<N>,
    c15: Kap<O>,
    combine: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O) -> R,
): Kap<R> = Kap {
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

fun <A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, R> zip(
    c1: Kap<A>,
    c2: Kap<B>,
    c3: Kap<C>,
    c4: Kap<D>,
    c5: Kap<E>,
    c6: Kap<F>,
    c7: Kap<G>,
    c8: Kap<H>,
    c9: Kap<I>,
    c10: Kap<J>,
    c11: Kap<K>,
    c12: Kap<L>,
    c13: Kap<M>,
    c14: Kap<N>,
    c15: Kap<O>,
    c16: Kap<P>,
    combine: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P) -> R,
): Kap<R> = Kap {
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

fun <A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R> zip(
    c1: Kap<A>,
    c2: Kap<B>,
    c3: Kap<C>,
    c4: Kap<D>,
    c5: Kap<E>,
    c6: Kap<F>,
    c7: Kap<G>,
    c8: Kap<H>,
    c9: Kap<I>,
    c10: Kap<J>,
    c11: Kap<K>,
    c12: Kap<L>,
    c13: Kap<M>,
    c14: Kap<N>,
    c15: Kap<O>,
    c16: Kap<P>,
    c17: Kap<Q>,
    combine: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q) -> R,
): Kap<R> = Kap {
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

fun <A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, S, R> zip(
    c1: Kap<A>,
    c2: Kap<B>,
    c3: Kap<C>,
    c4: Kap<D>,
    c5: Kap<E>,
    c6: Kap<F>,
    c7: Kap<G>,
    c8: Kap<H>,
    c9: Kap<I>,
    c10: Kap<J>,
    c11: Kap<K>,
    c12: Kap<L>,
    c13: Kap<M>,
    c14: Kap<N>,
    c15: Kap<O>,
    c16: Kap<P>,
    c17: Kap<Q>,
    c18: Kap<S>,
    combine: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, S) -> R,
): Kap<R> = Kap {
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

fun <A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, S, T, R> zip(
    c1: Kap<A>,
    c2: Kap<B>,
    c3: Kap<C>,
    c4: Kap<D>,
    c5: Kap<E>,
    c6: Kap<F>,
    c7: Kap<G>,
    c8: Kap<H>,
    c9: Kap<I>,
    c10: Kap<J>,
    c11: Kap<K>,
    c12: Kap<L>,
    c13: Kap<M>,
    c14: Kap<N>,
    c15: Kap<O>,
    c16: Kap<P>,
    c17: Kap<Q>,
    c18: Kap<S>,
    c19: Kap<T>,
    combine: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, S, T) -> R,
): Kap<R> = Kap {
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

fun <A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, S, T, U, R> zip(
    c1: Kap<A>,
    c2: Kap<B>,
    c3: Kap<C>,
    c4: Kap<D>,
    c5: Kap<E>,
    c6: Kap<F>,
    c7: Kap<G>,
    c8: Kap<H>,
    c9: Kap<I>,
    c10: Kap<J>,
    c11: Kap<K>,
    c12: Kap<L>,
    c13: Kap<M>,
    c14: Kap<N>,
    c15: Kap<O>,
    c16: Kap<P>,
    c17: Kap<Q>,
    c18: Kap<S>,
    c19: Kap<T>,
    c20: Kap<U>,
    combine: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, S, T, U) -> R,
): Kap<R> = Kap {
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

fun <A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, S, T, U, V, R> zip(
    c1: Kap<A>,
    c2: Kap<B>,
    c3: Kap<C>,
    c4: Kap<D>,
    c5: Kap<E>,
    c6: Kap<F>,
    c7: Kap<G>,
    c8: Kap<H>,
    c9: Kap<I>,
    c10: Kap<J>,
    c11: Kap<K>,
    c12: Kap<L>,
    c13: Kap<M>,
    c14: Kap<N>,
    c15: Kap<O>,
    c16: Kap<P>,
    c17: Kap<Q>,
    c18: Kap<S>,
    c19: Kap<T>,
    c20: Kap<U>,
    c21: Kap<V>,
    combine: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, S, T, U, V) -> R,
): Kap<R> = Kap {
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

fun <A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, S, T, U, V, W, R> zip(
    c1: Kap<A>,
    c2: Kap<B>,
    c3: Kap<C>,
    c4: Kap<D>,
    c5: Kap<E>,
    c6: Kap<F>,
    c7: Kap<G>,
    c8: Kap<H>,
    c9: Kap<I>,
    c10: Kap<J>,
    c11: Kap<K>,
    c12: Kap<L>,
    c13: Kap<M>,
    c14: Kap<N>,
    c15: Kap<O>,
    c16: Kap<P>,
    c17: Kap<Q>,
    c18: Kap<S>,
    c19: Kap<T>,
    c20: Kap<U>,
    c21: Kap<V>,
    c22: Kap<W>,
    combine: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, S, T, U, V, W) -> R,
): Kap<R> = Kap {
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

fun <A, B, R> combine(
    c1: Kap<A>,
    c2: Kap<B>,
    f: (A, B) -> R,
): Kap<R> = c1.zip(c2, f)

fun <A, B, C, R> combine(
    c1: Kap<A>,
    c2: Kap<B>,
    c3: Kap<C>,
    f: (A, B, C) -> R,
): Kap<R> = zip(c1, c2, c3, f)

fun <A, B, C, D, R> combine(
    c1: Kap<A>,
    c2: Kap<B>,
    c3: Kap<C>,
    c4: Kap<D>,
    f: (A, B, C, D) -> R,
): Kap<R> = zip(c1, c2, c3, c4, f)

fun <A, B, C, D, E, R> combine(
    c1: Kap<A>,
    c2: Kap<B>,
    c3: Kap<C>,
    c4: Kap<D>,
    c5: Kap<E>,
    f: (A, B, C, D, E) -> R,
): Kap<R> = zip(c1, c2, c3, c4, c5, f)

fun <A, B, C, D, E, F, R> combine(
    c1: Kap<A>,
    c2: Kap<B>,
    c3: Kap<C>,
    c4: Kap<D>,
    c5: Kap<E>,
    c6: Kap<F>,
    f: (A, B, C, D, E, F) -> R,
): Kap<R> = zip(c1, c2, c3, c4, c5, c6, f)

fun <A, B, C, D, E, F, G, R> combine(
    c1: Kap<A>,
    c2: Kap<B>,
    c3: Kap<C>,
    c4: Kap<D>,
    c5: Kap<E>,
    c6: Kap<F>,
    c7: Kap<G>,
    f: (A, B, C, D, E, F, G) -> R,
): Kap<R> = zip(c1, c2, c3, c4, c5, c6, c7, f)

fun <A, B, C, D, E, F, G, H, R> combine(
    c1: Kap<A>,
    c2: Kap<B>,
    c3: Kap<C>,
    c4: Kap<D>,
    c5: Kap<E>,
    c6: Kap<F>,
    c7: Kap<G>,
    c8: Kap<H>,
    f: (A, B, C, D, E, F, G, H) -> R,
): Kap<R> = zip(c1, c2, c3, c4, c5, c6, c7, c8, f)

fun <A, B, C, D, E, F, G, H, I, R> combine(
    c1: Kap<A>,
    c2: Kap<B>,
    c3: Kap<C>,
    c4: Kap<D>,
    c5: Kap<E>,
    c6: Kap<F>,
    c7: Kap<G>,
    c8: Kap<H>,
    c9: Kap<I>,
    f: (A, B, C, D, E, F, G, H, I) -> R,
): Kap<R> = zip(c1, c2, c3, c4, c5, c6, c7, c8, c9, f)

fun <A, B, C, D, E, F, G, H, I, J, R> combine(
    c1: Kap<A>,
    c2: Kap<B>,
    c3: Kap<C>,
    c4: Kap<D>,
    c5: Kap<E>,
    c6: Kap<F>,
    c7: Kap<G>,
    c8: Kap<H>,
    c9: Kap<I>,
    c10: Kap<J>,
    f: (A, B, C, D, E, F, G, H, I, J) -> R,
): Kap<R> = zip(c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, f)

fun <A, B, C, D, E, F, G, H, I, J, K, R> combine(
    c1: Kap<A>,
    c2: Kap<B>,
    c3: Kap<C>,
    c4: Kap<D>,
    c5: Kap<E>,
    c6: Kap<F>,
    c7: Kap<G>,
    c8: Kap<H>,
    c9: Kap<I>,
    c10: Kap<J>,
    c11: Kap<K>,
    f: (A, B, C, D, E, F, G, H, I, J, K) -> R,
): Kap<R> = zip(c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, f)

fun <A, B, C, D, E, F, G, H, I, J, K, L, R> combine(
    c1: Kap<A>,
    c2: Kap<B>,
    c3: Kap<C>,
    c4: Kap<D>,
    c5: Kap<E>,
    c6: Kap<F>,
    c7: Kap<G>,
    c8: Kap<H>,
    c9: Kap<I>,
    c10: Kap<J>,
    c11: Kap<K>,
    c12: Kap<L>,
    f: (A, B, C, D, E, F, G, H, I, J, K, L) -> R,
): Kap<R> = zip(c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, f)

fun <A, B, C, D, E, F, G, H, I, J, K, L, M, R> combine(
    c1: Kap<A>,
    c2: Kap<B>,
    c3: Kap<C>,
    c4: Kap<D>,
    c5: Kap<E>,
    c6: Kap<F>,
    c7: Kap<G>,
    c8: Kap<H>,
    c9: Kap<I>,
    c10: Kap<J>,
    c11: Kap<K>,
    c12: Kap<L>,
    c13: Kap<M>,
    f: (A, B, C, D, E, F, G, H, I, J, K, L, M) -> R,
): Kap<R> = zip(c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13, f)

fun <A, B, C, D, E, F, G, H, I, J, K, L, M, N, R> combine(
    c1: Kap<A>,
    c2: Kap<B>,
    c3: Kap<C>,
    c4: Kap<D>,
    c5: Kap<E>,
    c6: Kap<F>,
    c7: Kap<G>,
    c8: Kap<H>,
    c9: Kap<I>,
    c10: Kap<J>,
    c11: Kap<K>,
    c12: Kap<L>,
    c13: Kap<M>,
    c14: Kap<N>,
    f: (A, B, C, D, E, F, G, H, I, J, K, L, M, N) -> R,
): Kap<R> = zip(c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13, c14, f)

fun <A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, R> combine(
    c1: Kap<A>,
    c2: Kap<B>,
    c3: Kap<C>,
    c4: Kap<D>,
    c5: Kap<E>,
    c6: Kap<F>,
    c7: Kap<G>,
    c8: Kap<H>,
    c9: Kap<I>,
    c10: Kap<J>,
    c11: Kap<K>,
    c12: Kap<L>,
    c13: Kap<M>,
    c14: Kap<N>,
    c15: Kap<O>,
    f: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O) -> R,
): Kap<R> = zip(c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13, c14, c15, f)

fun <A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, R> combine(
    c1: Kap<A>,
    c2: Kap<B>,
    c3: Kap<C>,
    c4: Kap<D>,
    c5: Kap<E>,
    c6: Kap<F>,
    c7: Kap<G>,
    c8: Kap<H>,
    c9: Kap<I>,
    c10: Kap<J>,
    c11: Kap<K>,
    c12: Kap<L>,
    c13: Kap<M>,
    c14: Kap<N>,
    c15: Kap<O>,
    c16: Kap<P>,
    f: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P) -> R,
): Kap<R> = zip(c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13, c14, c15, c16, f)

fun <A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R> combine(
    c1: Kap<A>,
    c2: Kap<B>,
    c3: Kap<C>,
    c4: Kap<D>,
    c5: Kap<E>,
    c6: Kap<F>,
    c7: Kap<G>,
    c8: Kap<H>,
    c9: Kap<I>,
    c10: Kap<J>,
    c11: Kap<K>,
    c12: Kap<L>,
    c13: Kap<M>,
    c14: Kap<N>,
    c15: Kap<O>,
    c16: Kap<P>,
    c17: Kap<Q>,
    f: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q) -> R,
): Kap<R> = zip(c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13, c14, c15, c16, c17, f)

fun <A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, S, R> combine(
    c1: Kap<A>,
    c2: Kap<B>,
    c3: Kap<C>,
    c4: Kap<D>,
    c5: Kap<E>,
    c6: Kap<F>,
    c7: Kap<G>,
    c8: Kap<H>,
    c9: Kap<I>,
    c10: Kap<J>,
    c11: Kap<K>,
    c12: Kap<L>,
    c13: Kap<M>,
    c14: Kap<N>,
    c15: Kap<O>,
    c16: Kap<P>,
    c17: Kap<Q>,
    c18: Kap<S>,
    f: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, S) -> R,
): Kap<R> = zip(c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13, c14, c15, c16, c17, c18, f)

fun <A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, S, T, R> combine(
    c1: Kap<A>,
    c2: Kap<B>,
    c3: Kap<C>,
    c4: Kap<D>,
    c5: Kap<E>,
    c6: Kap<F>,
    c7: Kap<G>,
    c8: Kap<H>,
    c9: Kap<I>,
    c10: Kap<J>,
    c11: Kap<K>,
    c12: Kap<L>,
    c13: Kap<M>,
    c14: Kap<N>,
    c15: Kap<O>,
    c16: Kap<P>,
    c17: Kap<Q>,
    c18: Kap<S>,
    c19: Kap<T>,
    f: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, S, T) -> R,
): Kap<R> = zip(c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13, c14, c15, c16, c17, c18, c19, f)

fun <A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, S, T, U, R> combine(
    c1: Kap<A>,
    c2: Kap<B>,
    c3: Kap<C>,
    c4: Kap<D>,
    c5: Kap<E>,
    c6: Kap<F>,
    c7: Kap<G>,
    c8: Kap<H>,
    c9: Kap<I>,
    c10: Kap<J>,
    c11: Kap<K>,
    c12: Kap<L>,
    c13: Kap<M>,
    c14: Kap<N>,
    c15: Kap<O>,
    c16: Kap<P>,
    c17: Kap<Q>,
    c18: Kap<S>,
    c19: Kap<T>,
    c20: Kap<U>,
    f: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, S, T, U) -> R,
): Kap<R> = zip(c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13, c14, c15, c16, c17, c18, c19, c20, f)

fun <A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, S, T, U, V, R> combine(
    c1: Kap<A>,
    c2: Kap<B>,
    c3: Kap<C>,
    c4: Kap<D>,
    c5: Kap<E>,
    c6: Kap<F>,
    c7: Kap<G>,
    c8: Kap<H>,
    c9: Kap<I>,
    c10: Kap<J>,
    c11: Kap<K>,
    c12: Kap<L>,
    c13: Kap<M>,
    c14: Kap<N>,
    c15: Kap<O>,
    c16: Kap<P>,
    c17: Kap<Q>,
    c18: Kap<S>,
    c19: Kap<T>,
    c20: Kap<U>,
    c21: Kap<V>,
    f: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, S, T, U, V) -> R,
): Kap<R> = zip(c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13, c14, c15, c16, c17, c18, c19, c20, c21, f)

fun <A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, S, T, U, V, W, R> combine(
    c1: Kap<A>,
    c2: Kap<B>,
    c3: Kap<C>,
    c4: Kap<D>,
    c5: Kap<E>,
    c6: Kap<F>,
    c7: Kap<G>,
    c8: Kap<H>,
    c9: Kap<I>,
    c10: Kap<J>,
    c11: Kap<K>,
    c12: Kap<L>,
    c13: Kap<M>,
    c14: Kap<N>,
    c15: Kap<O>,
    c16: Kap<P>,
    c17: Kap<Q>,
    c18: Kap<S>,
    c19: Kap<T>,
    c20: Kap<U>,
    c21: Kap<V>,
    c22: Kap<W>,
    f: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, S, T, U, V, W) -> R,
): Kap<R> = zip(c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13, c14, c15, c16, c17, c18, c19, c20, c21, c22, f)
