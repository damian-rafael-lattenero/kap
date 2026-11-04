package kap

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The `SyncCell` laws (kap-core): a non-suspending atomic register with the
 * [Ref] semantics.
 *
 * 1. **Atomicity.** Concurrent updates never lose a write — the exact total
 *    after `N` threads × `M` increments is `N * M`.
 * 2. **Read-modify-write.** The transform lands on the value it observed: every
 *    previous value handed out by [SyncCell.getAndUpdate] is returned exactly
 *    once, and a compare-and-set idiom claims its value exactly once.
 * 3. **No suspension.** The cell works from plain [Thread]s and from coroutines;
 *    the `KapTracer` test exercises the real non-suspending SPI.
 * 4. **Same semantics as [Ref]** for get/set/update/modify, plus the
 *    `AtomicReference`-style previous-value read.
 *
 * Everything is synchronized with thread start/join and latches — no sleeps.
 */
class SyncCellTest {

    // ── 4. The value semantics ──────────────────────────────────────────

    @Test
    fun `get set update getAndUpdate and modify follow their contracts`() {
        val cell = SyncCell.of(1)

        assertEquals(1, cell.get())

        cell.set(5)
        assertEquals(5, cell.get())

        assertEquals(6, cell.update { it + 1 }, "update returns the new value")
        assertEquals(6, cell.get())

        assertEquals(6, cell.getAndUpdate { it * 2 }, "getAndUpdate returns the previous value")
        assertEquals(12, cell.get())

        val label = cell.modify { current -> (current + 8) to "was $current" }
        assertEquals("was 12", label, "modify returns the second component")
        assertEquals(20, cell.get(), "modify stores the first component")
    }

    @Test
    fun `Ref sync creates a working cell — the same value named from the Ref side`() {
        val cell = Ref.sync(0)

        assertEquals(0, cell.get())
        assertEquals(1, cell.update { it + 1 })
        assertEquals(1, cell.getAndUpdate { it + 1 }, "getAndUpdate returns the previous value")
        assertEquals(2, cell.get())
    }

    @Test
    fun `a throwing transform leaves the cell untouched`() {
        val cell = Ref.sync("stable")

        assertFailsWith<IllegalStateException> {
            cell.update { error("boom") }
        }

        assertEquals("stable", cell.get(), "a transform that never landed changed nothing")
    }

    // ── 1. Atomicity under real threads ─────────────────────────────────

    @Test
    fun `8 threads incrementing 1000 times each leave exactly 8000`() {
        val cell = Ref.sync(0)

        val threads = (1..8).map {
            Thread { repeat(1_000) { cell.update { count -> count + 1 } } }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }

        assertEquals(8_000, cell.get(), "no increment may be lost")
    }

    // ── 2. Read-modify-write: the previous value is handed out once ─────

    @Test
    fun `every previous value is returned exactly once under contention`() {
        val cell = Ref.sync(0)
        val threads = 8
        val perThread = 500

        val observed = Array(threads) { mutableListOf<Int>() }
        val workers = (0 until threads).map { index ->
            Thread { repeat(perThread) { observed[index] += cell.getAndUpdate { it + 1 } } }
        }
        workers.forEach { it.start() }
        workers.forEach { it.join() }

        val all = observed.flatMap { it }
        assertEquals(threads * perThread, cell.get(), "the final value is the number of updates")
        assertEquals((0 until threads * perThread).toList(), all.sorted(), "the previous values are 0..n-1, no gaps, no repeats")
    }

    @Test
    fun `a compare-and-set idiom claims the initial value exactly once`() {
        val cell = Ref.sync(0)
        val threads = 16

        val claimed = Array(threads) { false }
        val workers = (0 until threads).map { index ->
            Thread {
                claimed[index] = cell.modify { current ->
                    if (current == 0) 1 to true else current to false
                }
            }
        }
        workers.forEach { it.start() }
        workers.forEach { it.join() }

        assertEquals(1, claimed.count { it }, "exactly one thread may observe and claim the initial value")
        assertEquals(1, cell.get())
    }

    // ── 1b. Reads observe whole values, never a torn state ──────────────

    @Test
    fun `readers only ever observe a value that was actually written`() {
        val first = State(1, "one")
        val second = State(2, "two")
        val cell = Ref.sync(first)
        val done = AtomicBoolean(false)
        val failures = arrayOfNulls<Throwable>(4)

        val readers = (0 until 4).map { index ->
            Thread {
                try {
                    // The first read is unconditional: the reader always observes the cell.
                    do {
                        val seen = cell.get()
                        check(seen === first || seen === second) { "a torn/foreign value escaped: $seen" }
                    } while (!done.get())
                } catch (e: Throwable) {
                    failures[index] = e
                }
            }
        }
        readers.forEach { it.start() }
        repeat(5_000) { round -> cell.set(if (round % 2 == 0) second else first) }
        done.set(true)
        readers.forEach { it.join() }

        failures.firstOrNull { it != null }?.let { throw it }
        assertTrue(cell.get() === first || cell.get() === second)
    }

    // ── 3. Non-suspending SPI and coroutine callers ─────────────────────

    @Test
    fun `a KapTracer counts events from many threads without suspending`() {
        val events = Ref.sync(0)
        val tracer = KapTracer { events.update { count -> count + 1 } }

        val threads = 8
        val perThread = 250
        val workers = (1 until threads).map {
            Thread { repeat(perThread) { tracer.onEvent(TraceEvent.Started("op")) } }
        }
        workers.forEach { it.start() }
        repeat(perThread) { tracer.onEvent(TraceEvent.Started("main")) }
        workers.forEach { it.join() }

        assertEquals(threads * perThread, events.get())
    }

    @Test
    fun `updates from concurrent coroutines lose no writes`() = runTest {
        val cell = Ref.sync(0)

        withContext(Dispatchers.Default) {
            coroutineScope {
                repeat(1_000) {
                    launch { cell.update { count -> count + 1 } }
                }
            }
        }

        assertEquals(1_000, cell.get())
    }

    private data class State(val id: Int, val label: String)
}
