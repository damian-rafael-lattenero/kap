package kap

import kotlinx.coroutines.async

// ── combine: parallel execution of suspend lambdas ──────────────────────
//
// Runs N independent suspend lambdas in parallel and combines their results.
//
// Choose your style:
//   kap+with:  kap(::Dashboard).with { fetchUser() }.with { fetchCart() }.with { fetchPromos() }
//   combine:   combine({ fetchUser() }, { fetchCart() }, { fetchPromos() }) { u, c, p -> Dashboard(u, c, p) }
//   combine(Kap): combine(Kap { fetchUser() }, Kap { fetchCart() }) { u, c -> ... }
//
// kap+with gives compile-time parameter order safety via curried types.
// combine (suspend lambdas) gives parZip-like ergonomics.
// combine (Kaps) takes pre-built Kaps (useful when computations are reused).

/**
 * Runs two suspend lambdas in parallel and combines their results.
 *
 * ```
 * val dashboard = combine(
 *     { fetchUser(id) },
 *     { fetchCart(id) },
 * ) { user, cart -> Dashboard(user, cart) }
 *     .executeGraph()
 * ```
 */
fun <A, B, R> combine(
    fa: suspend () -> A,
    fb: suspend () -> B,
    f: (A, B) -> R,
): Kap<R> = Kap {
    val da = async { fa() }
    val db = async { fb() }
    f(da.await(), db.await())
}

/**
 * Runs three suspend lambdas in parallel and combines their results.
 *
 * ```
 * val dashboard = combine(
 *     { fetchUser(id) },
 *     { fetchCart(id) },
 *     { fetchPromos(id) },
 * ) { user, cart, promos -> Dashboard(user, cart, promos) }
 *     .executeGraph()
 * ```
 */
fun <A, B, C, R> combine(
    fa: suspend () -> A,
    fb: suspend () -> B,
    fc: suspend () -> C,
    f: (A, B, C) -> R,
): Kap<R> = Kap {
    val da = async { fa() }
    val db = async { fb() }
    val dc = async { fc() }
    f(da.await(), db.await(), dc.await())
}

/**
 * Runs four suspend lambdas in parallel and combines their results.
 */
fun <A, B, C, D, R> combine(
    fa: suspend () -> A,
    fb: suspend () -> B,
    fc: suspend () -> C,
    fd: suspend () -> D,
    f: (A, B, C, D) -> R,
): Kap<R> = Kap {
    val da = async { fa() }
    val db = async { fb() }
    val dc = async { fc() }
    val dd = async { fd() }
    f(da.await(), db.await(), dc.await(), dd.await())
}

/**
 * Runs five suspend lambdas in parallel and combines their results.
 */
fun <A, B, C, D, E, R> combine(
    fa: suspend () -> A,
    fb: suspend () -> B,
    fc: suspend () -> C,
    fd: suspend () -> D,
    fe: suspend () -> E,
    f: (A, B, C, D, E) -> R,
): Kap<R> = Kap {
    val da = async { fa() }
    val db = async { fb() }
    val dc = async { fc() }
    val dd = async { fd() }
    val de = async { fe() }
    f(da.await(), db.await(), dc.await(), dd.await(), de.await())
}

// ── pair / triple: parallel combination into Pair/Triple ────────────────

/**
 * Runs two suspend lambdas in parallel and returns their results as a [Pair].
 *
 * ```
 * val (user, cart) = pair({ fetchUser(id) }, { fetchCart(id) })
 *     .executeGraph()
 * ```
 */
fun <A, B> pair(
    fa: suspend () -> A,
    fb: suspend () -> B,
): Kap<Pair<A, B>> = combine(fa, fb, ::Pair)

/**
 * Runs three suspend lambdas in parallel and returns their results as a [Triple].
 *
 * ```
 * val (user, cart, promos) = triple({ fetchUser(id) }, { fetchCart(id) }, { fetchPromos(id) })
 *     .executeGraph()
 * ```
 */
fun <A, B, C> triple(
    fa: suspend () -> A,
    fb: suspend () -> B,
    fc: suspend () -> C,
): Kap<Triple<A, B, C>> = combine(fa, fb, fc, ::Triple)

/**
 * Runs six suspend lambdas in parallel and combines their results.
 */
fun <A, B, C, D, E, F, R> combine(
    fa: suspend () -> A,
    fb: suspend () -> B,
    fc: suspend () -> C,
    fd: suspend () -> D,
    fe: suspend () -> E,
    ff: suspend () -> F,
    f: (A, B, C, D, E, F) -> R,
): Kap<R> = Kap {
    val da = async { fa() }
    val db = async { fb() }
    val dc = async { fc() }
    val dd = async { fd() }
    val de = async { fe() }
    val df = async { ff() }
    f(da.await(), db.await(), dc.await(), dd.await(), de.await(), df.await())
}

/**
 * Runs seven suspend lambdas in parallel and combines their results.
 */
fun <A, B, C, D, E, F, G, R> combine(
    fa: suspend () -> A,
    fb: suspend () -> B,
    fc: suspend () -> C,
    fd: suspend () -> D,
    fe: suspend () -> E,
    ff: suspend () -> F,
    fg: suspend () -> G,
    f: (A, B, C, D, E, F, G) -> R,
): Kap<R> = Kap {
    val da = async { fa() }
    val db = async { fb() }
    val dc = async { fc() }
    val dd = async { fd() }
    val de = async { fe() }
    val df = async { ff() }
    val dg = async { fg() }
    f(da.await(), db.await(), dc.await(), dd.await(), de.await(), df.await(), dg.await())
}

/**
 * Runs eight suspend lambdas in parallel and combines their results.
 */
fun <A, B, C, D, E, F, G, H, R> combine(
    fa: suspend () -> A,
    fb: suspend () -> B,
    fc: suspend () -> C,
    fd: suspend () -> D,
    fe: suspend () -> E,
    ff: suspend () -> F,
    fg: suspend () -> G,
    fh: suspend () -> H,
    f: (A, B, C, D, E, F, G, H) -> R,
): Kap<R> = Kap {
    val da = async { fa() }
    val db = async { fb() }
    val dc = async { fc() }
    val dd = async { fd() }
    val de = async { fe() }
    val df = async { ff() }
    val dg = async { fg() }
    val dh = async { fh() }
    f(da.await(), db.await(), dc.await(), dd.await(), de.await(), df.await(), dg.await(), dh.await())
}

/**
 * Runs nine suspend lambdas in parallel and combines their results.
 *
 * This is the maximum arity for the suspend-lambda variant of `combine`.
 * For higher arities, use `kap`+`with` (up to 22) or `traverse` for dynamic collections.
 */
fun <A, B, C, D, E, F, G, H, I, R> combine(
    fa: suspend () -> A,
    fb: suspend () -> B,
    fc: suspend () -> C,
    fd: suspend () -> D,
    fe: suspend () -> E,
    ff: suspend () -> F,
    fg: suspend () -> G,
    fh: suspend () -> H,
    fi: suspend () -> I,
    f: (A, B, C, D, E, F, G, H, I) -> R,
): Kap<R> = Kap {
    val da = async { fa() }
    val db = async { fb() }
    val dc = async { fc() }
    val dd = async { fd() }
    val de = async { fe() }
    val df = async { ff() }
    val dg = async { fg() }
    val dh = async { fh() }
    val di = async { fi() }
    f(da.await(), db.await(), dc.await(), dd.await(), de.await(), df.await(), dg.await(), dh.await(), di.await())
}

// For arities > 9 or dynamic collections, use traverse/sequence.
// For arities 10-22, use kap+with or combine(Kap) (auto-generated).
