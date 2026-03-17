package applicative

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/**
 * Marks the [Async] DSL scope to prevent accidental outer-scope resolution
 * in nested builders.
 */
@DslMarker
@Target(AnnotationTarget.CLASS)
annotation class AsyncDsl

/**
 * A lazy computation that produces [A] when executed inside a [CoroutineScope].
 *
 * Computations are descriptions — they don't run until [Async.invoke] executes them.
 * They can be composed outside the DSL using [map], [ap], [followedBy], [flatMap], [zip],
 * and other top-level combinators, then executed via `Async { computation }`.
 *
 * ## Design note: `suspend fun CoroutineScope.execute()`
 *
 * This signature intentionally combines a [CoroutineScope] receiver with `suspend`.
 * The Kotlin coroutines convention separates them (`suspend fun` = suspends,
 * `CoroutineScope.fun` = launches coroutines), but [Computation] requires both:
 *
 * - **CoroutineScope** — operators like [ap] and [race] call `async {}` to launch
 *   parallel branches within the caller's scope (structured concurrency).
 * - **suspend** — branches call `await()`, `withContext()`, `withTimeout()`, etc.
 *
 * This is safe because **users never call [execute] directly** — they compose
 * via [ap], [followedBy], [flatMap], and execute via [Async.invoke].
 * The dual contract is an internal implementation detail, not a public API concern.
 *
 * This mirrors `kotlinx.coroutines.async {}` and `launch {}`, whose blocks are
 * also `suspend CoroutineScope.() -> T`.
 */
fun interface Computation<out A> {
    suspend fun CoroutineScope.execute(): A

    companion object {
        /**
         * Creates a [Computation] that immediately throws [error] when executed.
         *
         * Useful for lifting a known failure into the computation graph
         * without wrapping in a lambda:
         *
         * ```
         * val fail: Computation<Nothing> = Computation.failed(IllegalStateException("boom"))
         * ```
         */
        fun failed(error: Throwable): Computation<Nothing> = Computation { throw error }

        /**
         * Lazily constructs a [Computation] by deferring [block] evaluation
         * until execution time.
         *
         * Useful for recursive or self-referential composition where
         * eagerly building the computation graph would cause a stack overflow:
         *
         * ```
         * fun retryForever(c: Computation<Int>): Computation<Int> =
         *     Computation.defer {
         *         c.attempt().flatMap { either ->
         *             when (either) {
         *                 is Either.Right -> pure(either.value)
         *                 is Either.Left  -> retryForever(c)
         *             }
         *         }
         *     }
         * ```
         */
        fun <A> defer(block: () -> Computation<A>): Computation<A> = Computation {
            with(block()) { execute() }
        }
    }
}

/**
 * A [Computation] that acts as a phase barrier. When subsequent [ap] calls
 * are chained on a PhaseBarrier, their right-side launches are gated until
 * the barrier completes. All gated launches proceed in parallel once the
 * barrier's signal fires.
 *
 * This is an internal implementation detail — users interact through
 * [followedBy] which creates barriers, and [ap] which respects them.
 */
class PhaseBarrier<out A>(
    @PublishedApi internal val inner: Computation<A>,
    @PublishedApi internal val signal: CompletableDeferred<Unit>,
) : Computation<A> {
    override suspend fun CoroutineScope.execute(): A {
        try {
            val result = with(inner) { execute() }
            signal.complete(Unit)
            return result
        } catch (e: Throwable) {
            // Complete the signal even on failure so gated ap/apV calls don't hang.
            // They will observe the failure through structured concurrency (parent scope
            // cancellation), but the signal must fire to prevent deadlock if the exception
            // is caught by recover/attempt inside the chain.
            signal.complete(Unit)
            throw e
        }
    }
}

// ── pure: wrap a value ──────────────────────────────────────────────────

/**
 * Wraps a pure value into a [Computation] that immediately returns it.
 *
 * ```
 * val answer: Computation<Int> = pure(42)
 * ```
 */
fun <A> pure(a: A): Computation<A> = Computation { a }

// ── map: transform the result ───────────────────────────────────────────

/**
 * Transforms the result of this computation by applying [f].
 *
 * ```
 * pure(42).map { it * 2 }  // Computation producing 84
 * ```
 */
inline fun <A, B> Computation<A>.map(crossinline f: (A) -> B): Computation<B> = Computation {
    f(with(this@map) { execute() })
}

// ── ap: parallel — right side async, left side inline ─────────────────

/**
 * Applicative apply — runs this (a curried function) and [fa] in parallel,
 * then applies the function to the result.
 *
 * The left spine executes inline while the right side launches as [async].
 * Each chain link creates exactly **one** new coroutine (the right side),
 * so a chain of N `.ap` calls creates N coroutines. The left spine itself
 * is evaluated recursively (depth O(N)) with N intermediate curried closures.
 *
 * **Complexity note:** a chain of N `.ap` calls uses O(N) stack depth and
 * allocates N curried closures. For typical BFF orchestrations (N ≤ 15)
 * this is negligible. For very large or dynamic N, prefer [traverse]/[sequence].
 *
 * **Why not `inline`:** Unlike [map] and [flatMap] which transform and execute
 * immediately within the computation body, `ap` returns a new [Computation]
 * that captures [fa] as a closure. The `inline` modifier requires that lambdas
 * be executed in-place, but `Computation {}` is a SAM constructor that stores
 * the lambda — making `inline` inapplicable here. The same applies to
 * [followedBy] and [apOrNull].
 *
 * ```
 * lift3(::buildResult)
 *     .ap { fetchUser() }     // parallel
 *     .ap { fetchConfig() }   // parallel
 * ```
 *
 */
infix fun <A, B> Computation<(A) -> B>.ap(fa: Computation<A>): Computation<B> {
    val self = this
    return if (self is PhaseBarrier) {
        val signal = self.signal
        PhaseBarrier(Computation {
            val deferredA = async {
                signal.await()          // gate: wait for barrier to complete
                with(fa) { execute() }
            }
            val f = with(self) { execute() }  // runs barrier, completes signal
            f(deferredA.await())
        }, signal)
    } else {
        Computation {
            val deferredA = async { with(fa) { execute() } }
            val f = with(self) { execute() }
            f(deferredA.await())
        }
    }
}

/** Convenience overload that wraps a suspend lambda into a [Computation]. */
infix fun <A, B> Computation<(A) -> B>.ap(fa: suspend () -> A): Computation<B> =
    ap(Computation { fa() })

/**
 * Applicative apply for a nullable [Computation] — when the curried function
 * expects a nullable argument (`A?`).
 *
 * When [fa] is non-null, launches it in parallel (same as normal [ap]).
 * When [fa] is null (literal or variable), passes `null` to the function immediately.
 *
 * ```
 * val insurance: Computation<String>? = null
 *
 * lift3 { flight: String, hotel: String, ins: String? -> buildBooking(flight, hotel, ins) }
 *     .ap { fetchFlight() }
 *     .ap { fetchHotel() }
 *     .apOrNull(insurance)     // passes null when insurance is null
 *
 * // Also works with literal null:
 * lift2 { a: String, b: String? -> "$a|${b ?: "nil"}" }
 *     .ap { "hello" }
 *     .apOrNull(null)
 * ```
 */
infix fun <A : Any, B> Computation<(A?) -> B>.apOrNull(fa: Computation<A>?): Computation<B> {
    val self = this
    return if (self is PhaseBarrier) {
        val signal = self.signal
        PhaseBarrier(Computation {
            val deferredA = if (fa != null) async {
                signal.await()
                with(fa) { execute() }
            } else null
            val f = with(self) { execute() }
            f(deferredA?.await())
        }, signal)
    } else {
        Computation {
            val deferredA = if (fa != null) async { with(fa) { execute() } } else null
            val f = with(self) { execute() }
            f(deferredA?.await())
        }
    }
}

// ── followedBy: true phase barrier ─────────────────────────────────────

/**
 * True phase barrier — awaits the left side, runs [fa], and **gates** all
 * subsequent [ap] calls until the barrier completes.
 *
 * Unlike [ap], [followedBy] enforces ordering: the right side does **not** start
 * until the left side completes. Unlike [flatMap], the right side does
 * **not** receive the left side's value.
 *
 * **Phase semantics:** Any [ap] chained after a [followedBy] will not launch
 * its right-side coroutine until the barrier completes. This means the code
 * structure honestly reflects the execution phases:
 *
 * ```
 * lift4(::build)
 *     .ap { fetchA() }             // ┐ phase 1: parallel
 *     .ap { fetchB() }             // ┘
 *     .followedBy { validate() }   // ── barrier: waits for phase 1
 *     .ap { calcC() }              // ┐ phase 2: parallel (starts AFTER barrier)
 *     .ap { calcD() }              // ┘
 * ```
 */
infix fun <A, B> Computation<(A) -> B>.followedBy(fa: Computation<A>): Computation<B> {
    val self = this
    val signal = CompletableDeferred<Unit>()
    return PhaseBarrier(Computation {
        val f = with(self) { execute() }
        val a = with(fa) { execute() }
        f(a)
    }, signal)
}

/** Convenience overload that wraps a suspend lambda into a [Computation]. */
infix fun <A, B> Computation<(A) -> B>.followedBy(fa: suspend () -> A): Computation<B> =
    followedBy(Computation { fa() })

// ── thenValue: sequential value fill (no barrier) ─────────────────────

/**
 * Sequential value fill — awaits the left side, then runs [fa].
 *
 * Unlike [followedBy], [thenValue] does **not** create a phase barrier.
 * Subsequent [ap] calls will still launch eagerly at t=0.
 * The sequencing only affects the value assembly order, not the launch timing.
 *
 * Use [followedBy] when subsequent [ap] calls should wait for the barrier.
 * Use [thenValue] when subsequent [ap] calls are truly independent and
 * should overlap with the sequential computation for maximum performance.
 *
 * ```
 * lift3(::build)
 *     .ap { fetchData() }          // launched at t=0
 *     .thenValue { enrich() }      // sequential value, but...
 *     .ap { independentWork() }    // launched at t=0 (overlaps!)
 * ```
 */
infix fun <A, B> Computation<(A) -> B>.thenValue(fa: Computation<A>): Computation<B> = Computation {
    val f = with(this@thenValue) { execute() }
    val a = with(fa) { execute() }
    f(a)
}

/** Convenience overload that wraps a suspend lambda into a [Computation]. */
infix fun <A, B> Computation<(A) -> B>.thenValue(fa: suspend () -> A): Computation<B> =
    thenValue(Computation { fa() })

// ── flatMap: monadic bind (sequential, value-dependent) ─────────────────

/**
 * Monadic bind — sequential, value-dependent composition.
 *
 * Unlike [followedBy], the continuation [f] receives the left-hand result and
 * decides what to compute next. This breaks the static dependency-graph
 * property; prefer [ap]/[followedBy] when the right side is independent.
 *
 * ```
 * pure(userId).flatMap { id ->
 *     lift2(::buildProfile)
 *         .ap { fetchUser(id) }
 *         .ap { fetchAvatar(id) }
 * }
 * ```
 */
inline fun <A, B> Computation<A>.flatMap(crossinline f: (A) -> Computation<B>): Computation<B> = Computation {
    val a = with(this@flatMap) { execute() }
    with(f(a)) { execute() }
}

// ── on: per-computation context switch ───────────────────────────────────

/**
 * Switches this computation to run on the given [context].
 *
 * ```
 * lift2(::build)
 *     .ap { readFile().on(Dispatchers.IO) }
 *     .ap { compute().on(Dispatchers.Default) }
 * ```
 */
fun <A> Computation<A>.on(context: CoroutineContext): Computation<A> = Computation {
    withContext(context) { with(this@on) { execute() } }
}

// ── context: read the current CoroutineContext ─────────────────────────

/**
 * A [Computation] that captures the current [kotlin.coroutines.CoroutineContext].
 *
 * Useful for propagating trace IDs, MDC, or other context elements
 * into computation chains:
 *
 * ```
 * Async {
 *     context.flatMap { ctx ->
 *         val traceId = ctx[TraceKey]
 *         lift2(::build)
 *             .ap { fetchUser(traceId) }
 *             .ap { fetchCart(traceId) }
 *     }
 * }
 * ```
 */
val context: Computation<CoroutineContext> = Computation { coroutineContext }

// ── unit: convenience for pure(Unit) ────────────────────────────────────

/** A [Computation] that immediately returns [Unit]. */
val unit: Computation<Unit> = pure(Unit)

// ── named: CoroutineName for debugger/logging ───────────────────────────

/**
 * Assigns a [CoroutineName] to this computation, making it visible in
 * coroutine debugger, thread dumps, and logging frameworks.
 *
 * ```
 * lift3(::Dashboard)
 *     .ap { fetchUser().named("fetchUser") }
 *     .ap { fetchCart().named("fetchCart") }
 *     .ap { fetchPromos().named("fetchPromos") }
 * ```
 */
fun <A> Computation<A>.named(name: String): Computation<A> = Computation {
    withContext(CoroutineName(name)) { with(this@named) { execute() } }
}

// ── void: discard the result ─────────────────────────────────────────────

/**
 * Discards the result of this computation, returning [Unit].
 *
 * ```
 * Computation { sendEmail() }.void()  // Computation<Unit>
 * ```
 */
fun <A> Computation<A>.void(): Computation<Unit> = map { }

// ── attempt: catch to Either ────────────────────────────────────────────

/**
 * Catches non-cancellation exceptions and wraps the outcome in [Either].
 *
 * [CancellationException] is never caught — structured concurrency
 * cancellation always propagates.
 *
 * ```
 * val result: Either<Throwable, User> = Async {
 *     Computation { fetchUser() }.attempt()
 * }
 * ```
 */
fun <A> Computation<A>.attempt(): Computation<Either<Throwable, A>> = Computation {
    try {
        Either.Right(with(this@attempt) { execute() })
    } catch (e: Throwable) {
        if (e is CancellationException) throw e
        Either.Left(e)
    }
}

// ── tap: side-effect without changing value ──────────────────────────────

/**
 * Executes a side-effect [f] with the result, then returns the original value unchanged.
 *
 * ```
 * Computation { fetchUser() }
 *     .tap { user -> logger.info("fetched $user") }
 * ```
 */
inline fun <A> Computation<A>.tap(crossinline f: suspend (A) -> Unit): Computation<A> = Computation {
    val a = with(this@tap) { execute() }
    f(a)
    a
}

// ── zipLeft / zipRight: parallel, keep one side ─────────────────────────

/**
 * Runs this computation and [other] in parallel, returning only this result.
 * Both must succeed; if either fails, the other is cancelled.
 *
 * ```
 * Computation { fetchUser() }
 *     .zipLeft(Computation { logAccess() })  // returns User, logAccess runs in parallel
 * ```
 */
fun <A, B> Computation<A>.zipLeft(other: Computation<B>): Computation<A> = Computation {
    val da = async { with(this@zipLeft) { execute() } }
    val db = async { with(other) { execute() } }
    db.await()
    da.await()
}

/**
 * Runs this computation and [other] in parallel, returning only [other]'s result.
 * Both must succeed; if either fails, the other is cancelled.
 *
 * ```
 * Computation { logAccess() }
 *     .zipRight(Computation { fetchUser() })  // returns User
 * ```
 */
fun <A, B> Computation<A>.zipRight(other: Computation<B>): Computation<B> = Computation {
    val da = async { with(this@zipRight) { execute() } }
    val db = async { with(other) { execute() } }
    da.await()
    db.await()
}

// ── memoize: cache computation result ────────────────────────────────────

/**
 * Returns a computation that executes the original at most once, caching the result.
 * Subsequent executions return the cached value (or rethrow the cached exception).
 *
 * Thread-safe: if multiple coroutines execute concurrently, only the first runs
 * the original computation — others suspend until the result is available.
 *
 * ```
 * val expensive = Computation { fetchExpensiveData() }.memoize()
 *
 * Async {
 *     lift2(::combine)
 *         .ap { expensive }   // executes the original
 *         .ap { expensive }   // reuses cached result
 * }
 * ```
 */
fun <A> Computation<A>.memoize(): Computation<A> {
    val original = this
    val deferred = CompletableDeferred<A>()
    val started = kotlinx.coroutines.sync.Mutex()
    return Computation {
        if (started.tryLock()) {
            // First caller: execute and complete the deferred
            try {
                val value = with(original) { execute() }
                deferred.complete(value)
                value
            } catch (e: Throwable) {
                deferred.completeExceptionally(e)
                throw e
            }
        } else {
            // Subsequent callers: await the result
            deferred.await()
        }
    }
}

/**
 * Like [memoize], but **does not cache failures** — if the first execution fails,
 * the next caller retries the original computation.
 *
 * This is the production-friendly variant: transient failures (network timeouts,
 * rate limits) don't permanently poison the cache. Once the computation succeeds,
 * all subsequent calls return the cached result instantly.
 *
 * Thread-safe: uses [kotlinx.coroutines.sync.Mutex] to ensure only one caller
 * executes the original computation at a time.
 *
 * ```
 * val config = Computation { fetchRemoteConfig() }.memoizeOnSuccess()
 *
 * Async {
 *     lift2(::combine)
 *         .ap { config }   // first call fetches
 *         .ap { config }   // reuses cached result (or retries if first failed)
 * }
 * ```
 */
fun <A> Computation<A>.memoizeOnSuccess(): Computation<A> =
    MemoizedOnSuccess(this)

private class MemoizedOnSuccess<A>(private val original: Computation<A>) : Computation<A> {
    private val lock = kotlinx.coroutines.sync.Mutex()
    @kotlin.concurrent.Volatile private var cached: Any? = UNSET

    override suspend fun CoroutineScope.execute(): A {
        @Suppress("UNCHECKED_CAST")
        val c = cached
        if (c !== UNSET) return c as A
        lock.lock()
        try {
            @Suppress("UNCHECKED_CAST")
            val c2 = cached
            if (c2 !== UNSET) return c2 as A
            val value = with(original) { execute() }
            cached = value
            return value
        } catch (e: Throwable) {
            throw e  // failure NOT cached — next caller retries
        } finally {
            lock.unlock()
        }
    }

    companion object {
        private val UNSET = Any()
    }
}

// ── await: execute a Computation from any suspend context ────────────────

/**
 * Executes this [Computation] from any suspend context, creating a
 * [coroutineScope] for structured concurrency.
 *
 * This is the ergonomic bridge for using combinators like [timeout],
 * [retry], and [recover] inside `.ap` lambdas without the verbose
 * `with(computation) { execute() }` pattern:
 *
 * ```
 * // Before (verbose):
 * lift3(::Dashboard)
 *     .ap {
 *         with(Computation { fetchUser() }
 *             .timeout(200.milliseconds, User.cached())) { execute() }
 *     }
 *     .ap { fetchCart() }
 *
 * // After (clean):
 * lift3(::Dashboard)
 *     .ap { Computation { fetchUser() }.timeout(200.milliseconds, User.cached()).await() }
 *     .ap { fetchCart() }
 * ```
 *
 * **Note:** Creates a [coroutineScope] internally, which correctly maintains
 * structured concurrency guarantees. For top-level execution, use [Async] instead.
 */
suspend fun <A> Computation<A>.await(): A =
    coroutineScope { with(this@await) { execute() } }

// ── DSL entry point ─────────────────────────────────────────────────────

/**
 * Declarative dependency-graph DSL for Kotlin coroutines.
 *
 * Executes the [Computation] built inside [block] within a [coroutineScope],
 * providing structured concurrency guarantees: if any computation fails,
 * all siblings are automatically cancelled.
 *
 * ```
 * val result = Async {
 *     lift4(::buildDashboard)
 *         .ap { fetchUser() }              // parallel
 *         .ap { fetchConfig() }            // parallel
 *         .followedBy { validate() }       // sequential barrier
 *         .ap { calcShipping() }           // parallel again
 * }
 * ```
 */
@AsyncDsl
object Async {

    suspend operator fun <A> invoke(block: Async.() -> Computation<A>): A =
        coroutineScope { with(block()) { execute() } }

    /**
     * Runs the computation graph on the given [context], preserving the same
     * structured concurrency guarantee as the no-arg overload.
     *
     * Uses `coroutineScope` inside `withContext` so that failures propagate
     * and cancel siblings identically to `Async { }`.
     */
    suspend operator fun <A> invoke(
        context: CoroutineContext,
        block: Async.() -> Computation<A>,
    ): A = withContext(context) { coroutineScope { with(block()) { execute() } } }
}
