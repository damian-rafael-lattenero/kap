package applicative

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

// ── zip: parallel combination ───────────────────────────────────────────

/**
 * Runs this computation and [fb] in parallel, returning both results as a [Pair].
 */
fun <A, B> Computation<A>.zip(fb: Computation<B>): Computation<Pair<A, B>> = Computation {
    val da = async { with(this@zip) { execute() } }
    val db = async { with(fb) { execute() } }
    da.await() to db.await()
}

/**
 * Runs this computation and [fb] in parallel, combining results with [f].
 */
fun <A, B, C> Computation<A>.zip(fb: Computation<B>, f: (A, B) -> C): Computation<C> = Computation {
    val da = async { with(this@zip) { execute() } }
    val db = async { with(fb) { execute() } }
    f(da.await(), db.await())
}

// ── zipN: parallel combination (3-5 arity) ─────────────────────────────

/**
 * Runs 3 computations in parallel, combining results with [f].
 */
fun <A, B, C, D> zip(
    fa: Computation<A>,
    fb: Computation<B>,
    fc: Computation<C>,
    f: (A, B, C) -> D,
): Computation<D> = Computation {
    val da = async { with(fa) { execute() } }
    val db = async { with(fb) { execute() } }
    val dc = async { with(fc) { execute() } }
    f(da.await(), db.await(), dc.await())
}

/**
 * Runs 4 computations in parallel, combining results with [f].
 */
fun <A, B, C, D, E> zip(
    fa: Computation<A>,
    fb: Computation<B>,
    fc: Computation<C>,
    fd: Computation<D>,
    f: (A, B, C, D) -> E,
): Computation<E> = Computation {
    val da = async { with(fa) { execute() } }
    val db = async { with(fb) { execute() } }
    val dc = async { with(fc) { execute() } }
    val dd = async { with(fd) { execute() } }
    f(da.await(), db.await(), dc.await(), dd.await())
}

/**
 * Runs 5 computations in parallel, combining results with [f].
 */
fun <A, B, C, D, E, F> zip(
    fa: Computation<A>,
    fb: Computation<B>,
    fc: Computation<C>,
    fd: Computation<D>,
    fe: Computation<E>,
    f: (A, B, C, D, E) -> F,
): Computation<F> = Computation {
    val da = async { with(fa) { execute() } }
    val db = async { with(fb) { execute() } }
    val dc = async { with(fc) { execute() } }
    val dd = async { with(fd) { execute() } }
    val de = async { with(fe) { execute() } }
    f(da.await(), db.await(), dc.await(), dd.await(), de.await())
}

// ── mapN: top-level parallel combination (2-5 arity) ───────────────────

/**
 * Runs 2 computations in parallel, combining results with [f].
 * Equivalent to `fa.zip(fb, f)`.
 */
fun <A, B, C> mapN(
    fa: Computation<A>,
    fb: Computation<B>,
    f: (A, B) -> C,
): Computation<C> = fa.zip(fb, f)

/** Runs 3 computations in parallel, combining results with [f]. */
fun <A, B, C, D> mapN(
    fa: Computation<A>,
    fb: Computation<B>,
    fc: Computation<C>,
    f: (A, B, C) -> D,
): Computation<D> = zip(fa, fb, fc, f)

/** Runs 4 computations in parallel, combining results with [f]. */
fun <A, B, C, D, E> mapN(
    fa: Computation<A>,
    fb: Computation<B>,
    fc: Computation<C>,
    fd: Computation<D>,
    f: (A, B, C, D) -> E,
): Computation<E> = zip(fa, fb, fc, fd, f)

/** Runs 5 computations in parallel, combining results with [f]. */
fun <A, B, C, D, E, F> mapN(
    fa: Computation<A>,
    fb: Computation<B>,
    fc: Computation<C>,
    fd: Computation<D>,
    fe: Computation<E>,
    f: (A, B, C, D, E) -> F,
): Computation<F> = zip(fa, fb, fc, fd, fe, f)

// ── traverse / sequence: parallel collection operations ─────────────────

/**
 * Applies [f] to each element in parallel, collecting results in order.
 *
 * **Concurrency warning:** this launches one coroutine per element with no upper bound.
 * For large or unbounded collections, prefer the [traverse] overload with a `concurrency`
 * parameter to avoid overwhelming downstream services or running out of resources.
 *
 * ```
 * listOf(1, 2, 3).traverse { id -> Computation { fetchUser(id) } }
 * ```
 */
fun <A, B> Iterable<A>.traverse(f: (A) -> Computation<B>): Computation<List<B>> = Computation {
    map { a -> async { with(f(a)) { execute() } } }.awaitAll()
}

/**
 * Like [traverse] but limits the number of concurrent computations.
 *
 * @param concurrency maximum number of computations running simultaneously
 */
fun <A, B> Iterable<A>.traverse(concurrency: Int, f: (A) -> Computation<B>): Computation<List<B>> = Computation {
    val semaphore = Semaphore(concurrency)
    map { a -> async { semaphore.withPermit { with(f(a)) { execute() } } } }.awaitAll()
}

/**
 * Executes all computations in this collection in parallel, collecting results in order.
 *
 * **Concurrency warning:** this launches one coroutine per element with no upper bound.
 * For large or unbounded collections, prefer the [sequence] overload with a `concurrency` parameter.
 */
fun <A> Iterable<Computation<A>>.sequence(): Computation<List<A>> = Computation {
    map { c -> async { with(c) { execute() } } }.awaitAll()
}

/**
 * Like [sequence] but limits the number of concurrent computations.
 *
 * @param concurrency maximum number of computations running simultaneously
 */
fun <A> Iterable<Computation<A>>.sequence(concurrency: Int): Computation<List<A>> = Computation {
    val semaphore = Semaphore(concurrency)
    map { c -> async { semaphore.withPermit { with(c) { execute() } } } }.awaitAll()
}

// ── parMap: alias for traverse (more discoverable name) ────────────────

/**
 * Alias for [traverse] — applies [f] to each element in parallel, collecting results in order.
 *
 * Uses the name familiar to Arrow/Cats users.
 */
fun <A, B> Iterable<A>.parMap(f: (A) -> Computation<B>): Computation<List<B>> = traverse(f)

/**
 * Alias for [traverse] with bounded concurrency.
 */
fun <A, B> Iterable<A>.parMap(concurrency: Int, f: (A) -> Computation<B>): Computation<List<B>> =
    traverse(concurrency, f)
