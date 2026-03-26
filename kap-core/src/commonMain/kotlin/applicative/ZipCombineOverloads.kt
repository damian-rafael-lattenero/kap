// ┌──────────────────────────────────────────────────────────────────────┐
// │  AUTO-GENERATED — do not edit by hand.                               │
// │  Run: ./gradlew :kap-core:generateZipCombine                          │
// └──────────────────────────────────────────────────────────────────────┘
package applicative

import kotlinx.coroutines.async

fun <A, B, C, R> zip(
    c1: Effect<A>,
    c2: Effect<B>,
    c3: Effect<C>,
    combine: (A, B, C) -> R,
): Effect<R> = Effect {
    val d1 = async { with(c1) { execute() } }
    val d2 = async { with(c2) { execute() } }
    val d3 = async { with(c3) { execute() } }
    combine(d1.await(), d2.await(), d3.await())
}

fun <A, B, C, D, R> zip(
    c1: Effect<A>,
    c2: Effect<B>,
    c3: Effect<C>,
    c4: Effect<D>,
    combine: (A, B, C, D) -> R,
): Effect<R> = Effect {
    val d1 = async { with(c1) { execute() } }
    val d2 = async { with(c2) { execute() } }
    val d3 = async { with(c3) { execute() } }
    val d4 = async { with(c4) { execute() } }
    combine(d1.await(), d2.await(), d3.await(), d4.await())
}

fun <A, B, C, D, E, R> zip(
    c1: Effect<A>,
    c2: Effect<B>,
    c3: Effect<C>,
    c4: Effect<D>,
    c5: Effect<E>,
    combine: (A, B, C, D, E) -> R,
): Effect<R> = Effect {
    val d1 = async { with(c1) { execute() } }
    val d2 = async { with(c2) { execute() } }
    val d3 = async { with(c3) { execute() } }
    val d4 = async { with(c4) { execute() } }
    val d5 = async { with(c5) { execute() } }
    combine(d1.await(), d2.await(), d3.await(), d4.await(), d5.await())
}

fun <A, B, C, D, E, F, R> zip(
    c1: Effect<A>,
    c2: Effect<B>,
    c3: Effect<C>,
    c4: Effect<D>,
    c5: Effect<E>,
    c6: Effect<F>,
    combine: (A, B, C, D, E, F) -> R,
): Effect<R> = Effect {
    val d1 = async { with(c1) { execute() } }
    val d2 = async { with(c2) { execute() } }
    val d3 = async { with(c3) { execute() } }
    val d4 = async { with(c4) { execute() } }
    val d5 = async { with(c5) { execute() } }
    val d6 = async { with(c6) { execute() } }
    combine(d1.await(), d2.await(), d3.await(), d4.await(), d5.await(), d6.await())
}

fun <A, B, C, D, E, F, G, R> zip(
    c1: Effect<A>,
    c2: Effect<B>,
    c3: Effect<C>,
    c4: Effect<D>,
    c5: Effect<E>,
    c6: Effect<F>,
    c7: Effect<G>,
    combine: (A, B, C, D, E, F, G) -> R,
): Effect<R> = Effect {
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
    c1: Effect<A>,
    c2: Effect<B>,
    c3: Effect<C>,
    c4: Effect<D>,
    c5: Effect<E>,
    c6: Effect<F>,
    c7: Effect<G>,
    c8: Effect<H>,
    combine: (A, B, C, D, E, F, G, H) -> R,
): Effect<R> = Effect {
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
    c1: Effect<A>,
    c2: Effect<B>,
    c3: Effect<C>,
    c4: Effect<D>,
    c5: Effect<E>,
    c6: Effect<F>,
    c7: Effect<G>,
    c8: Effect<H>,
    c9: Effect<I>,
    combine: (A, B, C, D, E, F, G, H, I) -> R,
): Effect<R> = Effect {
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
    c1: Effect<A>,
    c2: Effect<B>,
    c3: Effect<C>,
    c4: Effect<D>,
    c5: Effect<E>,
    c6: Effect<F>,
    c7: Effect<G>,
    c8: Effect<H>,
    c9: Effect<I>,
    c10: Effect<J>,
    combine: (A, B, C, D, E, F, G, H, I, J) -> R,
): Effect<R> = Effect {
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
    c1: Effect<A>,
    c2: Effect<B>,
    c3: Effect<C>,
    c4: Effect<D>,
    c5: Effect<E>,
    c6: Effect<F>,
    c7: Effect<G>,
    c8: Effect<H>,
    c9: Effect<I>,
    c10: Effect<J>,
    c11: Effect<K>,
    combine: (A, B, C, D, E, F, G, H, I, J, K) -> R,
): Effect<R> = Effect {
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
    c1: Effect<A>,
    c2: Effect<B>,
    c3: Effect<C>,
    c4: Effect<D>,
    c5: Effect<E>,
    c6: Effect<F>,
    c7: Effect<G>,
    c8: Effect<H>,
    c9: Effect<I>,
    c10: Effect<J>,
    c11: Effect<K>,
    c12: Effect<L>,
    combine: (A, B, C, D, E, F, G, H, I, J, K, L) -> R,
): Effect<R> = Effect {
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
    c1: Effect<A>,
    c2: Effect<B>,
    c3: Effect<C>,
    c4: Effect<D>,
    c5: Effect<E>,
    c6: Effect<F>,
    c7: Effect<G>,
    c8: Effect<H>,
    c9: Effect<I>,
    c10: Effect<J>,
    c11: Effect<K>,
    c12: Effect<L>,
    c13: Effect<M>,
    combine: (A, B, C, D, E, F, G, H, I, J, K, L, M) -> R,
): Effect<R> = Effect {
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
    c1: Effect<A>,
    c2: Effect<B>,
    c3: Effect<C>,
    c4: Effect<D>,
    c5: Effect<E>,
    c6: Effect<F>,
    c7: Effect<G>,
    c8: Effect<H>,
    c9: Effect<I>,
    c10: Effect<J>,
    c11: Effect<K>,
    c12: Effect<L>,
    c13: Effect<M>,
    c14: Effect<N>,
    combine: (A, B, C, D, E, F, G, H, I, J, K, L, M, N) -> R,
): Effect<R> = Effect {
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
    c1: Effect<A>,
    c2: Effect<B>,
    c3: Effect<C>,
    c4: Effect<D>,
    c5: Effect<E>,
    c6: Effect<F>,
    c7: Effect<G>,
    c8: Effect<H>,
    c9: Effect<I>,
    c10: Effect<J>,
    c11: Effect<K>,
    c12: Effect<L>,
    c13: Effect<M>,
    c14: Effect<N>,
    c15: Effect<O>,
    combine: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O) -> R,
): Effect<R> = Effect {
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
    c1: Effect<A>,
    c2: Effect<B>,
    c3: Effect<C>,
    c4: Effect<D>,
    c5: Effect<E>,
    c6: Effect<F>,
    c7: Effect<G>,
    c8: Effect<H>,
    c9: Effect<I>,
    c10: Effect<J>,
    c11: Effect<K>,
    c12: Effect<L>,
    c13: Effect<M>,
    c14: Effect<N>,
    c15: Effect<O>,
    c16: Effect<P>,
    combine: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P) -> R,
): Effect<R> = Effect {
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
    c1: Effect<A>,
    c2: Effect<B>,
    c3: Effect<C>,
    c4: Effect<D>,
    c5: Effect<E>,
    c6: Effect<F>,
    c7: Effect<G>,
    c8: Effect<H>,
    c9: Effect<I>,
    c10: Effect<J>,
    c11: Effect<K>,
    c12: Effect<L>,
    c13: Effect<M>,
    c14: Effect<N>,
    c15: Effect<O>,
    c16: Effect<P>,
    c17: Effect<Q>,
    combine: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q) -> R,
): Effect<R> = Effect {
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
    c1: Effect<A>,
    c2: Effect<B>,
    c3: Effect<C>,
    c4: Effect<D>,
    c5: Effect<E>,
    c6: Effect<F>,
    c7: Effect<G>,
    c8: Effect<H>,
    c9: Effect<I>,
    c10: Effect<J>,
    c11: Effect<K>,
    c12: Effect<L>,
    c13: Effect<M>,
    c14: Effect<N>,
    c15: Effect<O>,
    c16: Effect<P>,
    c17: Effect<Q>,
    c18: Effect<S>,
    combine: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, S) -> R,
): Effect<R> = Effect {
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
    c1: Effect<A>,
    c2: Effect<B>,
    c3: Effect<C>,
    c4: Effect<D>,
    c5: Effect<E>,
    c6: Effect<F>,
    c7: Effect<G>,
    c8: Effect<H>,
    c9: Effect<I>,
    c10: Effect<J>,
    c11: Effect<K>,
    c12: Effect<L>,
    c13: Effect<M>,
    c14: Effect<N>,
    c15: Effect<O>,
    c16: Effect<P>,
    c17: Effect<Q>,
    c18: Effect<S>,
    c19: Effect<T>,
    combine: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, S, T) -> R,
): Effect<R> = Effect {
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
    c1: Effect<A>,
    c2: Effect<B>,
    c3: Effect<C>,
    c4: Effect<D>,
    c5: Effect<E>,
    c6: Effect<F>,
    c7: Effect<G>,
    c8: Effect<H>,
    c9: Effect<I>,
    c10: Effect<J>,
    c11: Effect<K>,
    c12: Effect<L>,
    c13: Effect<M>,
    c14: Effect<N>,
    c15: Effect<O>,
    c16: Effect<P>,
    c17: Effect<Q>,
    c18: Effect<S>,
    c19: Effect<T>,
    c20: Effect<U>,
    combine: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, S, T, U) -> R,
): Effect<R> = Effect {
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
    c1: Effect<A>,
    c2: Effect<B>,
    c3: Effect<C>,
    c4: Effect<D>,
    c5: Effect<E>,
    c6: Effect<F>,
    c7: Effect<G>,
    c8: Effect<H>,
    c9: Effect<I>,
    c10: Effect<J>,
    c11: Effect<K>,
    c12: Effect<L>,
    c13: Effect<M>,
    c14: Effect<N>,
    c15: Effect<O>,
    c16: Effect<P>,
    c17: Effect<Q>,
    c18: Effect<S>,
    c19: Effect<T>,
    c20: Effect<U>,
    c21: Effect<V>,
    combine: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, S, T, U, V) -> R,
): Effect<R> = Effect {
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
    c1: Effect<A>,
    c2: Effect<B>,
    c3: Effect<C>,
    c4: Effect<D>,
    c5: Effect<E>,
    c6: Effect<F>,
    c7: Effect<G>,
    c8: Effect<H>,
    c9: Effect<I>,
    c10: Effect<J>,
    c11: Effect<K>,
    c12: Effect<L>,
    c13: Effect<M>,
    c14: Effect<N>,
    c15: Effect<O>,
    c16: Effect<P>,
    c17: Effect<Q>,
    c18: Effect<S>,
    c19: Effect<T>,
    c20: Effect<U>,
    c21: Effect<V>,
    c22: Effect<W>,
    combine: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, S, T, U, V, W) -> R,
): Effect<R> = Effect {
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
    c1: Effect<A>,
    c2: Effect<B>,
    f: (A, B) -> R,
): Effect<R> = c1.zip(c2, f)

fun <A, B, C, R> combine(
    c1: Effect<A>,
    c2: Effect<B>,
    c3: Effect<C>,
    f: (A, B, C) -> R,
): Effect<R> = zip(c1, c2, c3, f)

fun <A, B, C, D, R> combine(
    c1: Effect<A>,
    c2: Effect<B>,
    c3: Effect<C>,
    c4: Effect<D>,
    f: (A, B, C, D) -> R,
): Effect<R> = zip(c1, c2, c3, c4, f)

fun <A, B, C, D, E, R> combine(
    c1: Effect<A>,
    c2: Effect<B>,
    c3: Effect<C>,
    c4: Effect<D>,
    c5: Effect<E>,
    f: (A, B, C, D, E) -> R,
): Effect<R> = zip(c1, c2, c3, c4, c5, f)

fun <A, B, C, D, E, F, R> combine(
    c1: Effect<A>,
    c2: Effect<B>,
    c3: Effect<C>,
    c4: Effect<D>,
    c5: Effect<E>,
    c6: Effect<F>,
    f: (A, B, C, D, E, F) -> R,
): Effect<R> = zip(c1, c2, c3, c4, c5, c6, f)

fun <A, B, C, D, E, F, G, R> combine(
    c1: Effect<A>,
    c2: Effect<B>,
    c3: Effect<C>,
    c4: Effect<D>,
    c5: Effect<E>,
    c6: Effect<F>,
    c7: Effect<G>,
    f: (A, B, C, D, E, F, G) -> R,
): Effect<R> = zip(c1, c2, c3, c4, c5, c6, c7, f)

fun <A, B, C, D, E, F, G, H, R> combine(
    c1: Effect<A>,
    c2: Effect<B>,
    c3: Effect<C>,
    c4: Effect<D>,
    c5: Effect<E>,
    c6: Effect<F>,
    c7: Effect<G>,
    c8: Effect<H>,
    f: (A, B, C, D, E, F, G, H) -> R,
): Effect<R> = zip(c1, c2, c3, c4, c5, c6, c7, c8, f)

fun <A, B, C, D, E, F, G, H, I, R> combine(
    c1: Effect<A>,
    c2: Effect<B>,
    c3: Effect<C>,
    c4: Effect<D>,
    c5: Effect<E>,
    c6: Effect<F>,
    c7: Effect<G>,
    c8: Effect<H>,
    c9: Effect<I>,
    f: (A, B, C, D, E, F, G, H, I) -> R,
): Effect<R> = zip(c1, c2, c3, c4, c5, c6, c7, c8, c9, f)

fun <A, B, C, D, E, F, G, H, I, J, R> combine(
    c1: Effect<A>,
    c2: Effect<B>,
    c3: Effect<C>,
    c4: Effect<D>,
    c5: Effect<E>,
    c6: Effect<F>,
    c7: Effect<G>,
    c8: Effect<H>,
    c9: Effect<I>,
    c10: Effect<J>,
    f: (A, B, C, D, E, F, G, H, I, J) -> R,
): Effect<R> = zip(c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, f)

fun <A, B, C, D, E, F, G, H, I, J, K, R> combine(
    c1: Effect<A>,
    c2: Effect<B>,
    c3: Effect<C>,
    c4: Effect<D>,
    c5: Effect<E>,
    c6: Effect<F>,
    c7: Effect<G>,
    c8: Effect<H>,
    c9: Effect<I>,
    c10: Effect<J>,
    c11: Effect<K>,
    f: (A, B, C, D, E, F, G, H, I, J, K) -> R,
): Effect<R> = zip(c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, f)

fun <A, B, C, D, E, F, G, H, I, J, K, L, R> combine(
    c1: Effect<A>,
    c2: Effect<B>,
    c3: Effect<C>,
    c4: Effect<D>,
    c5: Effect<E>,
    c6: Effect<F>,
    c7: Effect<G>,
    c8: Effect<H>,
    c9: Effect<I>,
    c10: Effect<J>,
    c11: Effect<K>,
    c12: Effect<L>,
    f: (A, B, C, D, E, F, G, H, I, J, K, L) -> R,
): Effect<R> = zip(c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, f)

fun <A, B, C, D, E, F, G, H, I, J, K, L, M, R> combine(
    c1: Effect<A>,
    c2: Effect<B>,
    c3: Effect<C>,
    c4: Effect<D>,
    c5: Effect<E>,
    c6: Effect<F>,
    c7: Effect<G>,
    c8: Effect<H>,
    c9: Effect<I>,
    c10: Effect<J>,
    c11: Effect<K>,
    c12: Effect<L>,
    c13: Effect<M>,
    f: (A, B, C, D, E, F, G, H, I, J, K, L, M) -> R,
): Effect<R> = zip(c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13, f)

fun <A, B, C, D, E, F, G, H, I, J, K, L, M, N, R> combine(
    c1: Effect<A>,
    c2: Effect<B>,
    c3: Effect<C>,
    c4: Effect<D>,
    c5: Effect<E>,
    c6: Effect<F>,
    c7: Effect<G>,
    c8: Effect<H>,
    c9: Effect<I>,
    c10: Effect<J>,
    c11: Effect<K>,
    c12: Effect<L>,
    c13: Effect<M>,
    c14: Effect<N>,
    f: (A, B, C, D, E, F, G, H, I, J, K, L, M, N) -> R,
): Effect<R> = zip(c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13, c14, f)

fun <A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, R> combine(
    c1: Effect<A>,
    c2: Effect<B>,
    c3: Effect<C>,
    c4: Effect<D>,
    c5: Effect<E>,
    c6: Effect<F>,
    c7: Effect<G>,
    c8: Effect<H>,
    c9: Effect<I>,
    c10: Effect<J>,
    c11: Effect<K>,
    c12: Effect<L>,
    c13: Effect<M>,
    c14: Effect<N>,
    c15: Effect<O>,
    f: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O) -> R,
): Effect<R> = zip(c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13, c14, c15, f)

fun <A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, R> combine(
    c1: Effect<A>,
    c2: Effect<B>,
    c3: Effect<C>,
    c4: Effect<D>,
    c5: Effect<E>,
    c6: Effect<F>,
    c7: Effect<G>,
    c8: Effect<H>,
    c9: Effect<I>,
    c10: Effect<J>,
    c11: Effect<K>,
    c12: Effect<L>,
    c13: Effect<M>,
    c14: Effect<N>,
    c15: Effect<O>,
    c16: Effect<P>,
    f: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P) -> R,
): Effect<R> = zip(c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13, c14, c15, c16, f)

fun <A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R> combine(
    c1: Effect<A>,
    c2: Effect<B>,
    c3: Effect<C>,
    c4: Effect<D>,
    c5: Effect<E>,
    c6: Effect<F>,
    c7: Effect<G>,
    c8: Effect<H>,
    c9: Effect<I>,
    c10: Effect<J>,
    c11: Effect<K>,
    c12: Effect<L>,
    c13: Effect<M>,
    c14: Effect<N>,
    c15: Effect<O>,
    c16: Effect<P>,
    c17: Effect<Q>,
    f: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q) -> R,
): Effect<R> = zip(c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13, c14, c15, c16, c17, f)

fun <A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, S, R> combine(
    c1: Effect<A>,
    c2: Effect<B>,
    c3: Effect<C>,
    c4: Effect<D>,
    c5: Effect<E>,
    c6: Effect<F>,
    c7: Effect<G>,
    c8: Effect<H>,
    c9: Effect<I>,
    c10: Effect<J>,
    c11: Effect<K>,
    c12: Effect<L>,
    c13: Effect<M>,
    c14: Effect<N>,
    c15: Effect<O>,
    c16: Effect<P>,
    c17: Effect<Q>,
    c18: Effect<S>,
    f: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, S) -> R,
): Effect<R> = zip(c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13, c14, c15, c16, c17, c18, f)

fun <A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, S, T, R> combine(
    c1: Effect<A>,
    c2: Effect<B>,
    c3: Effect<C>,
    c4: Effect<D>,
    c5: Effect<E>,
    c6: Effect<F>,
    c7: Effect<G>,
    c8: Effect<H>,
    c9: Effect<I>,
    c10: Effect<J>,
    c11: Effect<K>,
    c12: Effect<L>,
    c13: Effect<M>,
    c14: Effect<N>,
    c15: Effect<O>,
    c16: Effect<P>,
    c17: Effect<Q>,
    c18: Effect<S>,
    c19: Effect<T>,
    f: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, S, T) -> R,
): Effect<R> = zip(c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13, c14, c15, c16, c17, c18, c19, f)

fun <A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, S, T, U, R> combine(
    c1: Effect<A>,
    c2: Effect<B>,
    c3: Effect<C>,
    c4: Effect<D>,
    c5: Effect<E>,
    c6: Effect<F>,
    c7: Effect<G>,
    c8: Effect<H>,
    c9: Effect<I>,
    c10: Effect<J>,
    c11: Effect<K>,
    c12: Effect<L>,
    c13: Effect<M>,
    c14: Effect<N>,
    c15: Effect<O>,
    c16: Effect<P>,
    c17: Effect<Q>,
    c18: Effect<S>,
    c19: Effect<T>,
    c20: Effect<U>,
    f: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, S, T, U) -> R,
): Effect<R> = zip(c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13, c14, c15, c16, c17, c18, c19, c20, f)

fun <A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, S, T, U, V, R> combine(
    c1: Effect<A>,
    c2: Effect<B>,
    c3: Effect<C>,
    c4: Effect<D>,
    c5: Effect<E>,
    c6: Effect<F>,
    c7: Effect<G>,
    c8: Effect<H>,
    c9: Effect<I>,
    c10: Effect<J>,
    c11: Effect<K>,
    c12: Effect<L>,
    c13: Effect<M>,
    c14: Effect<N>,
    c15: Effect<O>,
    c16: Effect<P>,
    c17: Effect<Q>,
    c18: Effect<S>,
    c19: Effect<T>,
    c20: Effect<U>,
    c21: Effect<V>,
    f: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, S, T, U, V) -> R,
): Effect<R> = zip(c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13, c14, c15, c16, c17, c18, c19, c20, c21, f)

fun <A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, S, T, U, V, W, R> combine(
    c1: Effect<A>,
    c2: Effect<B>,
    c3: Effect<C>,
    c4: Effect<D>,
    c5: Effect<E>,
    c6: Effect<F>,
    c7: Effect<G>,
    c8: Effect<H>,
    c9: Effect<I>,
    c10: Effect<J>,
    c11: Effect<K>,
    c12: Effect<L>,
    c13: Effect<M>,
    c14: Effect<N>,
    c15: Effect<O>,
    c16: Effect<P>,
    c17: Effect<Q>,
    c18: Effect<S>,
    c19: Effect<T>,
    c20: Effect<U>,
    c21: Effect<V>,
    c22: Effect<W>,
    f: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, S, T, U, V, W) -> R,
): Effect<R> = zip(c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13, c14, c15, c16, c17, c18, c19, c20, c21, c22, f)
