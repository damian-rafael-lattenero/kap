package kap

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A typed, thread-safe mutable reference: the small piece of shared state that
 * does not need STM.
 *
 * Every operation runs atomically under a [Mutex], so concurrent updates never
 * lose writes. The mutating functions ([update], [modify]) compute the new
 * value inside the lock: `f` must be pure and fast, and must not call back
 * into the same `Ref` (that would deadlock).
 *
 * A `Ref` is created by executing [Ref.of]:
 *
 * ```
 * val counter = Ref.of(0).evalGraph()
 *
 * val graph = Kap.of { a: Int -> { b: Int -> a + b } }
 *     .with { counter.update { it + 1 } }
 *     .with { counter.update { it + 1 } }
 * counter.get().evalGraph()   // == 2: get() is a Kap<Int>, run it to read
 * ```
 *
 * Cancellation while waiting for the lock leaves the value untouched; the lock
 * is always released, so a cancelled caller cannot stall the reference.
 *
 * For an SPI that **cannot suspend** (`KapTracer.onEvent`, a listener, a shutdown
 * hook) use [SyncCell] — the same state and the same laws as a lock-free atomic
 * register, obtained from [Ref.sync].
 */
class Ref<A> private constructor(initial: A) {

    internal val mutex = Mutex()

    private var value: A = initial

    companion object {
        /**
         * A [Kap] that creates a fresh reference holding [initial] every time it
         * is executed — the reference is state, the computation that builds it
         * is a value.
         */
        fun <A> of(initial: A): Kap<Ref<A>> = Kap { Ref(initial) }

        /**
         * A [SyncCell] holding [initial] — the same semantics as [Ref] (get, set,
         * update, read-modify-write) with **no suspension**: every operation is a
         * single atomic step, callable from any thread and from non-suspending
         * code.
         *
         * ```
         * val counter = Ref.sync(0)
         * counter.update { it + 1 }        // direct call, no Kap, no suspend
         * ```
         *
         * Prefer [of] in graph code (the mutex yields to the dispatcher under
         * contention) and this factory where a `suspend` call is impossible.
         */
        fun <A> sync(initial: A): SyncCell<A> = SyncCell.of(initial)
    }

    /** Reads the current value. */
    fun get(): Kap<A> = Kap {
        mutex.withLock { value }
    }

    /** Replaces the current value. */
    fun set(a: A): Kap<Unit> = Kap {
        mutex.withLock { value = a }
    }

    /** Atomically transforms the value and returns the new one. */
    fun update(f: (A) -> A): Kap<A> = Kap {
        mutex.withLock {
            f(value).also { value = it }
        }
    }

    /**
     * Atomically transforms the value with `f`, stores the first component of
     * the returned pair and yields the second. This is the general
     * read-modify-write: [update] and [set] are special cases of it.
     */
    fun <B> modify(f: (A) -> Pair<A, B>): Kap<B> = Kap {
        mutex.withLock {
            val (next, result) = f(value)
            value = next
            result
        }
    }
}
