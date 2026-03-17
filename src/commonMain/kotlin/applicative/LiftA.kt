package applicative

import kotlinx.coroutines.async

// ── liftA: Haskell-style applicative lifting (suspend lambda inputs) ────
//
// These are the "parZip equivalent" with Haskell Applicative naming.
// Unlike lift+ap (curried, type-safe ordering), liftA takes suspend
// lambdas directly and runs them in parallel — the simplest way to
// combine N independent async operations.
//
// Haskell:  liftA2 :: Applicative f => (a -> b -> r) -> f a -> f b -> f r
// Kotlin:   liftA2(fa, fb) { a, b -> r }
//
// Choose your style:
//   lift+ap:  lift3(::Dashboard).ap { fetchUser() }.ap { fetchCart() }.ap { fetchPromos() }
//   liftA:    liftA3({ fetchUser() }, { fetchCart() }, { fetchPromos() }) { u, c, p -> Dashboard(u, c, p) }
//   mapN:     mapN(Computation { fetchUser() }, Computation { fetchCart() }, Computation { fetchPromos() }) { u, c, p -> Dashboard(u, c, p) }
//
// lift+ap gives compile-time parameter order safety via curried types.
// liftA gives parZip-like ergonomics with Haskell naming.
// mapN takes pre-built Computations (useful when computations are reused).

/**
 * Runs two suspend lambdas in parallel and combines their results.
 *
 * Haskell equivalent: `liftA2 f fa fb`
 *
 * ```
 * val dashboard = Async {
 *     liftA2(
 *         { fetchUser(id) },
 *         { fetchCart(id) },
 *     ) { user, cart -> Dashboard(user, cart) }
 * }
 * ```
 */
fun <A, B, R> liftA2(
    fa: suspend () -> A,
    fb: suspend () -> B,
    combine: (A, B) -> R,
): Computation<R> = Computation {
    val da = async { fa() }
    val db = async { fb() }
    combine(da.await(), db.await())
}

/**
 * Runs three suspend lambdas in parallel and combines their results.
 *
 * Haskell equivalent: `liftA3 f fa fb fc`
 *
 * ```
 * val dashboard = Async {
 *     liftA3(
 *         { fetchUser(id) },
 *         { fetchCart(id) },
 *         { fetchPromos(id) },
 *     ) { user, cart, promos -> Dashboard(user, cart, promos) }
 * }
 * ```
 */
fun <A, B, C, R> liftA3(
    fa: suspend () -> A,
    fb: suspend () -> B,
    fc: suspend () -> C,
    combine: (A, B, C) -> R,
): Computation<R> = Computation {
    val da = async { fa() }
    val db = async { fb() }
    val dc = async { fc() }
    combine(da.await(), db.await(), dc.await())
}

/**
 * Runs four suspend lambdas in parallel and combines their results.
 *
 * Haskell equivalent: `liftA4 f fa fb fc fd`
 * (Haskell base doesn't have liftA4, but Control.Applicative does via `<*>`)
 *
 * ```
 * val checkout = Async {
 *     liftA4(
 *         { fetchUser(id) },
 *         { fetchCart(id) },
 *         { fetchPromos(id) },
 *         { fetchInventory(id) },
 *     ) { user, cart, promos, inventory -> Phase1(user, cart, promos, inventory) }
 * }
 * ```
 */
fun <A, B, C, D, R> liftA4(
    fa: suspend () -> A,
    fb: suspend () -> B,
    fc: suspend () -> C,
    fd: suspend () -> D,
    combine: (A, B, C, D) -> R,
): Computation<R> = Computation {
    val da = async { fa() }
    val db = async { fb() }
    val dc = async { fc() }
    val dd = async { fd() }
    combine(da.await(), db.await(), dc.await(), dd.await())
}

/**
 * Runs five suspend lambdas in parallel and combines their results.
 *
 * ```
 * val page = Async {
 *     liftA5(
 *         { fetchUser(id) },
 *         { fetchCart(id) },
 *         { fetchPromos(id) },
 *         { fetchRecommendations(id) },
 *         { fetchNotifications(id) },
 *     ) { user, cart, promos, recs, notifs -> PageData(user, cart, promos, recs, notifs) }
 * }
 * ```
 */
fun <A, B, C, D, E, R> liftA5(
    fa: suspend () -> A,
    fb: suspend () -> B,
    fc: suspend () -> C,
    fd: suspend () -> D,
    fe: suspend () -> E,
    combine: (A, B, C, D, E) -> R,
): Computation<R> = Computation {
    val da = async { fa() }
    val db = async { fb() }
    val dc = async { fc() }
    val dd = async { fd() }
    val de = async { fe() }
    combine(da.await(), db.await(), dc.await(), dd.await(), de.await())
}

// ── product: parallel combination into Pair/Triple ──────────────────────
//
// Haskell: (,) <$> fa <*> fb
// Kotlin:  product(fa, fb)

/**
 * Runs two suspend lambdas in parallel and returns their results as a [Pair].
 *
 * Haskell equivalent: `(,) <$> fa <*> fb`
 *
 * ```
 * val (user, cart) = Async {
 *     product({ fetchUser(id) }, { fetchCart(id) })
 * }
 * ```
 */
fun <A, B> product(
    fa: suspend () -> A,
    fb: suspend () -> B,
): Computation<Pair<A, B>> = liftA2(fa, fb, ::Pair)

/**
 * Runs three suspend lambdas in parallel and returns their results as a [Triple].
 *
 * Haskell equivalent: `(,,) <$> fa <*> fb <*> fc`
 *
 * ```
 * val (user, cart, promos) = Async {
 *     product({ fetchUser(id) }, { fetchCart(id) }, { fetchPromos(id) })
 * }
 * ```
 */
fun <A, B, C> product(
    fa: suspend () -> A,
    fb: suspend () -> B,
    fc: suspend () -> C,
): Computation<Triple<A, B, C>> = liftA3(fa, fb, fc, ::Triple)

// ── sequence as liftA: lift a function over N computations ──────────────
//
// For arities > 5 or dynamic collections, use traverse/sequence.
// For arities 6-22, use lift+ap or mapN (auto-generated).
