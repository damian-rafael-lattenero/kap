package applicative

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

// ── zip: parallel combination ───────────────────────────────────────────

/**
 * Runs this computation and [fb] in parallel, returning both results as a [Pair].
 */
fun <A, B> Effect<A>.zip(fb: Effect<B>): Effect<Pair<A, B>> = Effect {
    val da = async { with(this@zip) { execute() } }
    val db = async { with(fb) { execute() } }
    da.await() to db.await()
}

/**
 * Runs this computation and [fb] in parallel, combining results with [f].
 */
fun <A, B, C> Effect<A>.zip(fb: Effect<B>, f: (A, B) -> C): Effect<C> = Effect {
    val da = async { with(this@zip) { execute() } }
    val db = async { with(fb) { execute() } }
    f(da.await(), db.await())
}

// ── zip 3-22 and mapN 2-22: see ZipOverloads.kt (auto-generated) ────────

// ── traverse / sequence: parallel collection operations ─────────────────

/**
 * Applies [f] to each element in parallel, collecting results in order.
 *
 * **Concurrency warning:** this launches one coroutine per element with no upper bound.
 * For large or unbounded collections, prefer the [traverse] overload with a `concurrency`
 * parameter to avoid overwhelming downstream services or running out of resources.
 *
 * ```
 * listOf(1, 2, 3).traverse { id -> Effect { fetchUser(id) } }
 * ```
 */
fun <A, B> Iterable<A>.traverse(f: (A) -> Effect<B>): Effect<List<B>> = Effect {
    map { a -> async { with(f(a)) { execute() } } }.awaitAll()
}

/**
 * Like [traverse] but limits the number of concurrent computations.
 *
 * @param concurrency maximum number of computations running simultaneously
 */
fun <A, B> Iterable<A>.traverse(concurrency: Int, f: (A) -> Effect<B>): Effect<List<B>> = Effect {
    val semaphore = Semaphore(concurrency)
    map { a -> async { semaphore.withPermit { with(f(a)) { execute() } } } }.awaitAll()
}

/**
 * Executes all computations in this collection in parallel, collecting results in order.
 *
 * **Concurrency warning:** this launches one coroutine per element with no upper bound.
 * For large or unbounded collections, prefer the [sequence] overload with a `concurrency` parameter.
 */
fun <A> Iterable<Effect<A>>.sequence(): Effect<List<A>> = Effect {
    map { c -> async { with(c) { execute() } } }.awaitAll()
}

/**
 * Like [sequence] but limits the number of concurrent computations.
 *
 * @param concurrency maximum number of computations running simultaneously
 */
fun <A> Iterable<Effect<A>>.sequence(concurrency: Int): Effect<List<A>> = Effect {
    val semaphore = Semaphore(concurrency)
    map { c -> async { semaphore.withPermit { with(c) { execute() } } } }.awaitAll()
}

// ── traverseDiscard / sequenceDiscard: fire-and-forget (discard results)

/**
 * Like [traverse] but discards results. Useful for side-effects (logging,
 * metrics, notifications) where you need parallelism but don't need the output.
 *
 * ```
 * userIds.traverseDiscard { id -> Effect { notifyUser(id) } }
 * ```
 */
fun <A> Iterable<A>.traverseDiscard(f: (A) -> Effect<Unit>): Effect<Unit> =
    traverse(f).map { }

/**
 * Like [traverseDiscard] but limits the number of concurrent computations.
 */
fun <A> Iterable<A>.traverseDiscard(concurrency: Int, f: (A) -> Effect<Unit>): Effect<Unit> =
    traverse(concurrency, f).map { }

/**
 * Like [sequence] but discards results. Executes all computations for
 * their side-effects only.
 */
fun Iterable<Effect<Unit>>.sequenceDiscard(): Effect<Unit> =
    sequence().map { }

/**
 * Like [sequenceDiscard] but limits the number of concurrent computations.
 */
fun Iterable<Effect<Unit>>.sequenceDiscard(concurrency: Int): Effect<Unit> =
    sequence(concurrency).map { }

// ── traverseSettled / sequenceSettled: collect ALL results (no cancellation) ──

/**
 * Applies [f] to each element in parallel, collecting ALL results — both successes
 * and failures — without cancelling siblings on failure.
 *
 * Unlike [traverse] which cancels all siblings when any computation fails (standard
 * structured concurrency), [traverseSettled] runs every computation to completion
 * and returns a [List] of [Result] values.
 *
 * This is useful when you need to know ALL outcomes, not just the first failure:
 *
 * ```
 * val results: List<Result<User>> = Async {
 *     userIds.traverseSettled { id -> Effect { fetchUser(id) } }
 * }
 * val successes = results.filter { it.isSuccess }.map { it.getOrThrow() }
 * val failures = results.filter { it.isFailure }.map { it.exceptionOrNull()!! }
 * ```
 *
 * **Concurrency warning:** launches one coroutine per element with no upper bound.
 * For bounded concurrency, use the overload with a `concurrency` parameter.
 */
fun <A, B> Iterable<A>.traverseSettled(f: (A) -> Effect<B>): Effect<List<Result<B>>> = Effect {
    kotlinx.coroutines.supervisorScope {
        map { a -> async { runCatching { with(f(a)) { execute() } } } }.awaitAll()
    }
}

/**
 * Like [traverseSettled] but limits the number of concurrent computations.
 *
 * @param concurrency maximum number of computations running simultaneously
 */
fun <A, B> Iterable<A>.traverseSettled(concurrency: Int, f: (A) -> Effect<B>): Effect<List<Result<B>>> = Effect {
    val semaphore = Semaphore(concurrency)
    kotlinx.coroutines.supervisorScope {
        map { a -> async { semaphore.withPermit { runCatching { with(f(a)) { execute() } } } } }.awaitAll()
    }
}

/**
 * Executes all computations in parallel, collecting ALL results without cancellation.
 *
 * Returns a list of [Result] values — every computation runs to completion
 * regardless of whether siblings fail.
 */
fun <A> Iterable<Effect<A>>.sequenceSettled(): Effect<List<Result<A>>> =
    traverseSettled { it }

/**
 * Like [sequenceSettled] but limits the number of concurrent computations.
 */
fun <A> Iterable<Effect<A>>.sequenceSettled(concurrency: Int): Effect<List<Result<A>>> =
    traverseSettled(concurrency) { it }

// ── Why no parMap? ──────────────────────────────────────────────────────
// parMap is intentionally not provided. This library uses `traverse` and
// `sequence` — the standard functional vocabulary. `parMap` is Arrow/Cats
// naming that implies "parallel map" as a special operation, but in this
// library ALL collection operations are parallel by default. The name
// `traverse` makes the Haskell heritage explicit and avoids suggesting
// that a non-`par` variant exists. Use `traverse(f)` or `traverse(n, f)`.
