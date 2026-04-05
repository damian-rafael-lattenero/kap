package kap

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlin.time.measureTimedValue


/**
 * A lazy computation that produces [A] when executed inside a [CoroutineScope].
 *
 * Kaps are descriptions — they don't run until [evalGraph] executes them.
 * They can be composed outside the DSL using [map], [with], [then], [andThen], [zip],
 * and other top-level combinators, then executed via `.evalGraph()`.
 *
 * ## Design note: `suspend fun CoroutineScope.execute()`
 *
 * This signature intentionally combines a [CoroutineScope] receiver with `suspend`.
 * The Kotlin coroutines convention separates them (`suspend fun` = suspends,
 * `CoroutineScope.fun` = launches coroutines), but [Kap] requires both:
 *
 * - **CoroutineScope** — operators like [with] and [race] call `async {}` to launch
 *   parallel branches within the caller's scope (structured concurrency).
 * - **suspend** — branches call `await()`, `withContext()`, `withTimeout()`, etc.
 *
 * This is safe because **users never call [execute] directly** — they compose
 * via [with], [then], [andThen], and execute via [evalGraph].
 * The dual contract is an internal implementation detail, not a public API concern.
 *
 * This mirrors `kotlinx.coroutines.async {}` and `launch {}`, whose blocks are
 * also `suspend CoroutineScope.() -> T`.
 */
fun interface Kap<out A> {
    suspend fun CoroutineScope.execute(): A

    companion object {
        /**
         * Creates a [Kap] that immediately throws [error] when executed.
         *
         * Useful for lifting a known failure into the computation graph
         * without wrapping in a lambda:
         *
         * ```
         * val fail: Kap<Nothing> = Kap.failed(IllegalStateException("boom"))
         * ```
         */
        fun failed(error: Throwable): Kap<Nothing> = Kap { throw error }

        /**
         * Lazily constructs a [Kap] by deferring [block] evaluation
         * until execution time.
         *
         * Useful for recursive or self-referential composition where
         * eagerly building the computation graph would cause a stack overflow:
         *
         * ```
         * fun retryForever(c: Kap<Int>): Kap<Int> =
         *     Kap.defer {
         *         c.settled().andThen { result ->
         *             result.fold(
         *                 onSuccess = { Kap.of(it) },
         *                 onFailure = { retryForever(c) },
         *             )
         *         }
         *     }
         * ```
         */
        fun <A> defer(block: () -> Kap<A>): Kap<A> = Kap {
            with(block()) { execute() }
        }
    }
}

/**
 * A [Kap] that acts as a phase barrier. When subsequent [with] calls
 * are chained on a PhaseBarrier, their right-side launches are gated until
 * the barrier completes. All gated launches proceed in parallel once the
 * barrier's signal fires.
 *
 * This is an internal implementation detail — users interact through
 * [then] which creates barriers, and [with] which respects them.
 */
class PhaseBarrier<out A>(
    val inner: Kap<A>,
    val signal: CompletableDeferred<Unit>,
) : Kap<A> {
    override suspend fun CoroutineScope.execute(): A {
        try {
            val result = with(inner) { execute() }
            signal.complete(Unit)
            return result
        } catch (e: Throwable) {
            // Complete the signal even on failure so gated with/withV calls don't hang.
            // They will observe the failure through structured concurrency (parent scope
            // cancellation), but the signal must fire to prevent deadlock if the exception
            // is caught by recover/attempt inside the chain.
            signal.complete(Unit)
            throw e
        }
    }
}

// ── Kap.of: wrap a value ────────────────────────────────────────

/**
 * Wraps a value into a [Kap] that immediately returns it.
 *
 * ```
 * val answer: Kap<Int> = Kap.of(42)
 * ```
 */
fun <A> Kap.Companion.of(a: A): Kap<A> = Kap { a }

// ── map: transform the result ───────────────────────────────────────────

/**
 * Transforms the result of this computation by applying [f].
 *
 * ```
 * Kap.of(42).map { it * 2 }  // Kap producing 84
 * ```
 */
inline fun <A, B> Kap<A>.map(crossinline f: (A) -> B): Kap<B> = Kap {
    f(with(this@map) { execute() })
}

// ── with: parallel — right side async, left side inline ─────────────────

/**
 * Provides the next argument in parallel — runs this (a curried function)
 * and [fa] concurrently, then applies the function to the result.
 *
 * The left spine executes inline while the right side launches as [async].
 * Each chain link creates exactly **one** new coroutine (the right side),
 * so a chain of N `.with` calls creates N coroutines.
 *
 * **Complexity note:** a chain of N `.with` calls uses O(N) stack depth and
 * allocates N curried closures. For typical BFF orchestrations (N <= 15)
 * this is negligible. For very large or dynamic N, prefer [traverse]/[sequence].
 *
 * ```
 * kap(::buildResult)
 *     .with { fetchUser() }     // parallel
 *     .with { fetchConfig() }   // parallel
 * ```
 *
 */
infix fun <A, B> Kap<(A) -> B>.with(fa: Kap<A>): Kap<B> {
    val self = this
    return if (self is PhaseBarrier) {
        val signal = self.signal
        PhaseBarrier(Kap {
            val deferredA = async {
                signal.await()          // gate: wait for barrier to complete
                with(fa) { execute() }
            }
            val f = with(self) { execute() }  // runs barrier, completes signal
            f(deferredA.await())
        }, signal)
    } else {
        Kap {
            val deferredA = async { with(fa) { execute() } }
            val f = with(self) { execute() }
            f(deferredA.await())
        }
    }
}

/** Convenience overload that wraps a suspend lambda into a [Kap]. */
infix fun <A, B> Kap<(A) -> B>.with(fa: suspend () -> A): Kap<B> =
    with(Kap { fa() })

/**
 * Provides a nullable argument in parallel — when the curried function
 * expects a nullable argument (`A?`).
 *
 * When [fa] is non-null, launches it in parallel (same as normal [with]).
 * When [fa] is null (literal or variable), passes `null` to the function immediately.
 *
 * ```
 * val insurance: Kap<String>? = null
 *
 * kap { flight: String, hotel: String, ins: String? -> buildBooking(flight, hotel, ins) }
 *     .with { fetchFlight() }
 *     .with { fetchHotel() }
 *     .withOrNull(insurance)     // passes null when insurance is null
 *
 * // Also works with literal null:
 * kap { a: String, b: String? -> "$a|${b ?: "nil"}" }
 *     .with { "hello" }
 *     .withOrNull(null)
 * ```
 */
infix fun <A : Any, B> Kap<(A?) -> B>.withOrNull(fa: Kap<A>?): Kap<B> {
    val self = this
    return if (self is PhaseBarrier) {
        val signal = self.signal
        PhaseBarrier(Kap {
            val deferredA = if (fa != null) async {
                signal.await()
                with(fa) { execute() }
            } else null
            val f = with(self) { execute() }
            f(deferredA?.await())
        }, signal)
    } else {
        Kap {
            val deferredA = if (fa != null) async { with(fa) { execute() } } else null
            val f = with(self) { execute() }
            f(deferredA?.await())
        }
    }
}

// ── then: true phase barrier ─────────────────────────────────────

/**
 * True phase barrier — awaits the left side, runs [fa], and **gates** all
 * subsequent [with] calls until the barrier completes.
 *
 * Unlike [with], [then] enforces ordering: the right side does **not** start
 * until the left side completes. Unlike [andThen], the right side does
 * **not** receive the left side's value.
 *
 * **Phase semantics:** Any [with] chained after a [then] will not launch
 * its right-side coroutine until the barrier completes. This means the code
 * structure honestly reflects the execution phases:
 *
 * ```
 * kap(::build)
 *     .with { fetchA() }             // phase 1: parallel
 *     .with { fetchB() }             //
 *     .then { validate() }     // barrier: waits for phase 1
 *     .with { calcC() }              // phase 2: parallel (starts AFTER barrier)
 *     .with { calcD() }              //
 * ```
 */
infix fun <A, B> Kap<(A) -> B>.then(fa: Kap<A>): Kap<B> {
    val self = this
    val signal = CompletableDeferred<Unit>()
    return PhaseBarrier(Kap {
        val f = with(self) { execute() }
        val a = with(fa) { execute() }
        f(a)
    }, signal)
}

/** Convenience overload that wraps a suspend lambda into a [Kap]. */
infix fun <A, B> Kap<(A) -> B>.then(fa: suspend () -> A): Kap<B> =
    then(Kap { fa() })

// ── thenValue: sequential value fill (no barrier) ─────────────────────

/**
 * Sequential value fill — awaits the left side, then runs [fa].
 *
 * Unlike [then], [thenValue] does **not** create a phase barrier.
 * Subsequent [with] calls will still launch eagerly at t=0.
 * The sequencing only affects the value assembly order, not the launch timing.
 *
 * Use [then] when subsequent [with] calls should wait for the barrier.
 * Use [thenValue] when subsequent [with] calls are truly independent and
 * should overlap with the sequential computation for maximum performance.
 *
 * ```
 * kap(::build)
 *     .with { fetchData() }          // launched at t=0
 *     .thenValue { enrich() }        // sequential value, but...
 *     .with { independentWork() }    // launched at t=0 (overlaps!)
 * ```
 */
infix fun <A, B> Kap<(A) -> B>.thenValue(fa: Kap<A>): Kap<B> = Kap {
    val f = with(this@thenValue) { execute() }
    val a = with(fa) { execute() }
    f(a)
}

/** Convenience overload that wraps a suspend lambda into a [Kap]. */
infix fun <A, B> Kap<(A) -> B>.thenValue(fa: suspend () -> A): Kap<B> =
    thenValue(Kap { fa() })

// ── andThen: monadic bind (sequential, value-dependent) ─────────────────

/**
 * Monadic bind — sequential, value-dependent composition.
 *
 * Unlike [then], the continuation [f] receives the left-hand result and
 * decides what to compute next. This breaks the static dependency-graph
 * property; prefer [with]/[then] when the right side is independent.
 *
 * ```
 * Kap.of(userId).andThen { id ->
 *     kap(::buildProfile)
 *         .with { fetchUser(id) }
 *         .with { fetchAvatar(id) }
 * }
 * ```
 */
inline fun <A, B> Kap<A>.andThen(crossinline f: (A) -> Kap<B>): Kap<B> = Kap {
    val a = with(this@andThen) { execute() }
    with(f(a)) { execute() }
}

// ── on: per-computation context switch ───────────────────────────────────

/**
 * Switches this computation to run on the given [context].
 *
 * ```
 * kap(::build)
 *     .with { readFile().on(Dispatchers.IO) }
 *     .with { compute().on(Dispatchers.Default) }
 * ```
 */
fun <A> Kap<A>.on(context: CoroutineContext): Kap<A> = Kap {
    withContext(context) { with(this@on) { execute() } }
}

// ── context: read the current CoroutineContext ─────────────────────────

/**
 * A [Kap] that captures the current [kotlin.coroutines.CoroutineContext].
 *
 * Useful for propagating trace IDs, MDC, or other context elements
 * into computation chains:
 *
 * ```
 * context.andThen { ctx ->
 *     val traceId = ctx[TraceKey]
 *     kap(::build)
 *         .with { fetchUser(traceId) }
 *         .with { fetchCart(traceId) }
 * }.evalGraph()
 * ```
 */
val context: Kap<CoroutineContext> = Kap { coroutineContext }

// ── Kap.empty: convenience for Kap.of(Unit) ─────────────

/** A [Kap] that immediately returns [Unit]. */
val Kap.Companion.empty: Kap<Unit> get() = Kap { }

// ── named: CoroutineName for debugger/logging ───────────────────────────

/**
 * Assigns a [CoroutineName] to this computation, making it visible in
 * coroutine debugger, thread dumps, and logging frameworks.
 *
 * ```
 * kap(::Dashboard)
 *     .with { fetchUser().named("fetchUser") }
 *     .with { fetchCart().named("fetchCart") }
 *     .with { fetchPromos().named("fetchPromos") }
 * ```
 */
fun <A> Kap<A>.named(name: String): Kap<A> = Kap {
    withContext(CoroutineName(name)) { with(this@named) { execute() } }
}

// ── discard: discard the result ──────────────────────────────────────────

/**
 * Discards the result of this computation, returning [Unit].
 *
 * ```
 * Kap { sendEmail() }.discard()  // Kap<Unit>
 * ```
 */
fun <A> Kap<A>.discard(): Kap<Unit> = map { }

// ── peek: side-effect without changing value ─────────────────────────────

/**
 * Executes a side-effect [f] with the result, then returns the original value unchanged.
 *
 * ```
 * Kap { fetchUser() }
 *     .peek { user -> logger.info("fetched $user") }
 * ```
 */
inline fun <A> Kap<A>.peek(crossinline f: suspend (A) -> Unit): Kap<A> = Kap {
    val a = with(this@peek) { execute() }
    f(a)
    a
}

// ── keepFirst / keepSecond: parallel, keep one side ──────────────────────

/**
 * Runs this computation and [other] in parallel, returning only this result.
 * Both must succeed; if either fails, the other is cancelled.
 *
 * ```
 * Kap { fetchUser() }
 *     .keepFirst(Kap { logAccess() })  // returns User, logAccess runs in parallel
 * ```
 */
fun <A, B> Kap<A>.keepFirst(other: Kap<B>): Kap<A> = Kap {
    val da = async { with(this@keepFirst) { execute() } }
    val db = async { with(other) { execute() } }
    db.await()
    da.await()
}

/**
 * Runs this computation and [other] in parallel, returning only [other]'s result.
 * Both must succeed; if either fails, the other is cancelled.
 *
 * ```
 * Kap { logAccess() }
 *     .keepSecond(Kap { fetchUser() })  // returns User
 * ```
 */
fun <A, B> Kap<A>.keepSecond(other: Kap<B>): Kap<B> = Kap {
    val da = async { with(this@keepSecond) { execute() } }
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
 * **Cancellation safety:** If the first caller is cancelled before completing,
 * the lock is released so the next caller retries the original computation.
 * CancellationException never poisons the cache.
 *
 * ```
 * val expensive = Kap { fetchExpensiveData() }.memoize()
 *
 * kap(::combine)
 *     .with { expensive }   // executes the original
 *     .with { expensive }   // reuses cached result
 *     .evalGraph()
 * ```
 */
fun <A> Kap<A>.memoize(): Kap<A> =
    Memoized(this)

internal class ValueHolder<A>(val value: A)

private class Memoized<A>(private val original: Kap<A>) : Kap<A> {
    private val lock = kotlinx.coroutines.sync.Mutex()
    @kotlin.concurrent.Volatile private var cached: ValueHolder<A>? = null
    @kotlin.concurrent.Volatile private var cachedError: Throwable? = null

    // ── Fast path (lock-free) ──────────────────────────────────────────
    // Both `cached` and `cachedError` are @Volatile, guaranteeing
    // happens-before visibility across threads.
    //
    // This double-checked locking pattern avoids the Mutex on the
    // hot path (cache hit) while remaining correct under concurrency:
    // 1. Read `cached` — if non-null, return immediately (no lock).
    // 2. Read `cachedError` — if non-null, rethrow immediately (no lock).
    // 3. Only on cache miss: acquire lock, re-check, then execute.
    //
    // The worst-case race is a redundant lock acquisition (benign).
    override suspend fun CoroutineScope.execute(): A {
        // Fast path: already cached (success or failure)
        cached?.let { return it.value }
        cachedError?.let { throw it }

        lock.lock()
        try {
            // Re-check after acquiring lock
            cached?.let { return it.value }
            cachedError?.let { throw it }

            val value = with(original) { execute() }
            cached = ValueHolder(value)
            return value
        } catch (e: CancellationException) {
            // Cancellation NEVER poisons the cache — next caller retries.
            throw e
        } catch (e: Throwable) {
            // Non-cancellation failures ARE cached (memoize caches everything).
            cachedError = e
            throw e
        } finally {
            lock.unlock()
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
 * val config = Kap { fetchRemoteConfig() }.memoizeOnSuccess()
 *
 * kap(::combine)
 *     .with { config }   // first call fetches
 *     .with { config }   // reuses cached result (or retries if first failed)
 *     .evalGraph()
 * ```
 */
fun <A> Kap<A>.memoizeOnSuccess(): Kap<A> =
    MemoizedOnSuccess(this)

private class MemoizedOnSuccess<A>(private val original: Kap<A>) : Kap<A> {
    private val lock = kotlinx.coroutines.sync.Mutex()
    @kotlin.concurrent.Volatile private var cached: ValueHolder<A>? = null

    // Fast path: volatile read avoids lock acquisition on cache hit.
    // See Memoized class above for the full concurrency rationale.
    override suspend fun CoroutineScope.execute(): A {
        cached?.let { return it.value }
        lock.lock()
        try {
            cached?.let { return it.value }
            val value = with(original) { execute() }
            cached = ValueHolder(value)
            return value
        } catch (e: Throwable) {
            throw e  // failure NOT cached — next caller retries
        } finally {
            lock.unlock()
        }
    }
}

// ── evalGraph: execute a Kap computation graph ────────────────

/**
 * Executes this [Kap] computation graph from any suspend context,
 * creating a [coroutineScope] for structured concurrency.
 *
 * This is the primary entry point for running a [Kap] computation.
 * All parallel branches launched by [with] are scoped here: if any
 * computation fails, all siblings are automatically cancelled.
 *
 * ```
 * val checkout: CheckoutResult =
 *     kap(::CheckoutResult)
 *         .with { fetchUser() }
 *         .with { fetchCart() }
 *         .then { validate() }
 *         .with { calcShipping() }
 *         .evalGraph()
 * ```
 *
 * Also useful inside `.with` lambdas for composing sub-graphs with
 * combinators like [timeout], [retry], and [recover]:
 *
 * ```
 * kap(::Dashboard)
 *     .with { Kap { fetchUser() }.timeout(200.milliseconds, User.cached()).evalGraph() }
 *     .with { fetchCart() }
 * ```
 */
suspend fun <A> Kap<A>.evalGraph(): A =
    coroutineScope { with(this@evalGraph) { execute() } }

// ── timed: measure execution duration ───────────────────────────────────

/**
 * The result of a [timed] computation: the produced value and how long it took.
 */
data class TimedResult<out A>(val value: A, val duration: Duration)

/**
 * Wraps this computation to measure its total execution [Duration].
 *
 * Returns a [Kap] that produces a [TimedResult] containing both
 * the original value and the wall-clock time of the execution.
 *
 * Composable — chain it with [map], [andThen], [memoize], etc.:
 *
 * ```
 * val (dashboard, duration) = kap(::Dashboard)
 *     .withUser { fetchUser() }
 *     .withCart { fetchCart() }
 *     .withPromos { fetchPromos() }
 *     .timed()
 *     .evalGraph()
 *
 * println("Built in ${duration.inWholeMilliseconds}ms")
 * ```
 *
 * For per-branch timing, apply [timed] to individual branches:
 *
 * ```
 * kap(::Dashboard)
 *     .withUser(Kap { fetchUser() }.timed().map { log("user: ${it.duration}"); it.value })
 *     .withCart { fetchCart() }
 * ```
 */
fun <A> Kap<A>.timed(): Kap<TimedResult<A>> = Kap {
    val (value, duration) = measureTimedValue {
        with(this@timed) { execute() }
    }
    TimedResult(value, duration)
}

/**
 * Executes this [Kap] and returns the result with its execution [Duration].
 *
 * Convenience for `.timed().evalGraph()`:
 *
 * ```
 * val (result, duration) = kap(::Dashboard)
 *     .withUser { fetchUser() }
 *     .withCart { fetchCart() }
 *     .evalGraphTimed()
 * ```
 */
suspend fun <A> Kap<A>.evalGraphTimed(): TimedResult<A> =
    timed().evalGraph()

// ── settled: capture result without cancelling siblings ──────────────────

/**
 * Wraps this computation's outcome in [Result], catching all non-cancellation
 * exceptions without propagating them to siblings in a [with] chain.
 *
 * Uses Kotlin's built-in [Result] type — ideal when you want partial-failure
 * tolerance in a parallel chain:
 *
 * ```
 * kap { user: Result<User>, cart: Cart, config: Config ->
 *     val u = user.getOrDefault(User.anonymous())
 *     Dashboard(u, cart, config)
 * }
 *     .with { fetchUser().settled() }  // won't cancel siblings on failure
 *     .with { fetchCart() }
 *     .with { fetchConfig() }
 * ```
 *
 * [CancellationException] is never caught — structured concurrency cancellation
 * always propagates.
 */
fun <A> Kap<A>.settled(): Kap<Result<A>> = Kap {
    try {
        Result.success(with(this@settled) { execute() })
    } catch (e: Throwable) {
        if (e is CancellationException) throw e
        Result.failure(e)
    }
}

