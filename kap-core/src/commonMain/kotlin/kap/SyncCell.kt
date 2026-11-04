package kap

import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * A typed, **non-suspending** thread-safe cell: the [Ref] semantics without the
 * coroutine machinery, for the SPIs that cannot suspend.
 *
 * A [Ref] operation is a [Kap] that takes a [Mutex][kotlinx.coroutines.sync.Mutex],
 * so every read and write is a suspension point. That is the right default for
 * graph code, and the wrong one for a callback: `KapTracer.onEvent`, a listener,
 * a shutdown hook or an SDK's `onClose` are plain functions and cannot wait for
 * a lock. `SyncCell` is the same piece of state with the same laws, implemented
 * as a lock-free atomic register ([AtomicReference] + compare-and-set), so it
 * can be read and written from any thread, from inside any coroutine, and from
 * code that has no coroutine at all.
 *
 * ```
 * class Metrics : KapTracer {
 *     private val events = Ref.sync(0)          // no suspend, no Kap
 *     override fun onEvent(event: TraceEvent) {
 *         val seen = events.getAndUpdate { it + 1 }   // atomic, returns the previous count
 *         if (seen == 0) log.firstEvent(event)
 *     }
 * }
 * ```
 *
 * ## Laws
 *
 * 1. **Atomicity.** Every operation is one atomic step: concurrent updates never
 *    lose a write (`N` threads incrementing `M` times leave exactly `N * M`), and
 *    every read observes a value the cell actually held (linearizable register).
 * 2. **Read-modify-write.** [update], [getAndUpdate] and [modify] transform the
 *    value they observed: the transform is retried until it lands on a value that
 *    is still current, so a concurrent writer forces a recomputation, never a
 *    lost update.
 * 3. **No suspension.** No operation suspends, allocates a coroutine, or takes a
 *    lock: it is safe inside `KapTracer.onEvent`, a signal handler or a plain
 *    `Thread`. (On single-threaded targets — JS, Wasm — the atomics are trivially
 *    atomic; the contract is identical.)
 * 4. **Same value semantics as [Ref].** [get]/[set] read and replace; [update]
 *    returns the **new** value; [getAndUpdate] returns the **previous** one;
 *    [modify] stores the first component of the pair and yields the second.
 *
 * ## Contract on the transform
 *
 * The transform passed to [update], [getAndUpdate] and [modify] **must be pure and
 * fast**: under contention it is called again with the newly observed value (the
 * compare-and-set failed because someone else wrote first), so it must not depend
 * on side effects or on being called exactly once, and it must not call back into
 * the same cell.
 *
 * @param A the type of the state. Immutable values are the honest choice: the cell
 *   stores and returns references, so mutating a returned object is outside the
 *   atomicity guarantee.
 */
@OptIn(ExperimentalAtomicApi::class)
class SyncCell<A> private constructor(initial: A) {

    private val state = AtomicReference(initial)

    companion object {
        /**
         * Creates a cell holding [initial] — the non-suspending counterpart of
         * [Ref.of]. [Ref.sync] is the same value named from the `Ref` side.
         */
        fun <A> of(initial: A): SyncCell<A> = SyncCell(initial)
    }

    /** Reads the current value. Never suspends. */
    fun get(): A = state.load()

    /** Replaces the current value. Never suspends. */
    fun set(a: A) {
        state.store(a)
    }

    /**
     * Atomically transforms the value with [f] and returns the **new** value
     * (the [Ref.update] contract). A throwing [f] leaves the cell untouched and
     * the exception propagates.
     */
    fun update(f: (A) -> A): A {
        while (true) {
            val current = state.load()
            val next = f(current)
            if (state.compareAndSet(current, next)) return next
        }
    }

    /**
     * Atomically transforms the value with [f] and returns the **previous** value
     * — the read-then-write pair in one step (`AtomicReference.getAndUpdate`).
     *
     * ```
     * val previous = counter.getAndUpdate { it + 1 }   // previous == the count before this call
     * ```
     */
    fun getAndUpdate(f: (A) -> A): A {
        while (true) {
            val current = state.load()
            val next = f(current)
            if (state.compareAndSet(current, next)) return current
        }
    }

    /**
     * Atomically transforms the value with [f], stores the first component of the
     * returned pair and yields the second — the general read-modify-write of which
     * [set], [update] and [getAndUpdate] are special cases (the [Ref.modify]
     * contract).
     */
    fun <B> modify(f: (A) -> Pair<A, B>): B {
        while (true) {
            val current = state.load()
            val (next, result) = f(current)
            if (state.compareAndSet(current, next)) return result
        }
    }
}
