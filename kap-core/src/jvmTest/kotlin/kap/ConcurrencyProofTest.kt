package kap

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Indisputable concurrency proofs — each test uses virtual-time assertions
 * or barrier-based deadlock detection to prove the framework's concurrency
 * properties beyond any doubt.
 *
 * Categories:
 * 1. Virtual-time timing: asserts parallel execution uses O(max) not O(sum) virtual time
 * 2. Phase barriers: proves then blocks with measurable virtual-time delay
 * 3. Mass cancellation: structured concurrency cancels N siblings on failure
 * 4. Latency regression: library virtual time == raw coroutines virtual time
 * 5. Real-world BFF scenario with timing
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ConcurrencyProofTest {

    // ════════════════════════════════════════════════════════════════════════
    // 1. VIRTUAL-TIME PROOF: parallel with completes in O(max), not O(sum)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `5 parallel 50ms calls complete in 50ms virtual time not 250ms`() = runTest {
        Kap.of { a: String -> { b: String -> { c: String -> { d: String -> { e: String -> "$a|$b|$c|$d|$e" } } } } }
                .with { delay(50); "A" }
                .with { delay(50); "B" }
                .with { delay(50); "C" }
                .with { delay(50); "D" }
                .with { delay(50); "E" }.executeGraph()

        // If sequential: 250ms. If parallel: 50ms.
        assertEquals(50, currentTime,
            "Expected 50ms virtual time (parallel). Got ${currentTime}ms (sequential would be 250ms)")
    }

    @Test
    fun `10 parallel 30ms calls complete in 30ms virtual time not 300ms`() = runTest {
        Kap.of { a: String -> { b: String -> { c: String -> { d: String -> { e: String ->
                     { f: String -> { g: String -> { h: String -> { i: String -> { j: String ->
                listOf(a, b, c, d, e, f, g, h, i, j).joinToString("|")
            } } } } } } } } } }
                .with { delay(30); "v1" }.with { delay(30); "v2" }.with { delay(30); "v3" }
                .with { delay(30); "v4" }.with { delay(30); "v5" }.with { delay(30); "v6" }
                .with { delay(30); "v7" }.with { delay(30); "v8" }.with { delay(30); "v9" }
                .with { delay(30); "v10" }.executeGraph()

        assertEquals(30, currentTime,
            "Expected 30ms virtual time (parallel). Got ${currentTime}ms (sequential would be 300ms)")
    }

    @Test
    fun `traverse with concurrency 3 over 9 items takes 3x longer than unbounded`() = runTest {
        (1..9).toList().traverse(3) { i ->
                Kap { delay(30); "v$i" }
            }.executeGraph()
        // Bounded (3 at a time, 9 items, 30ms each): 3 batches × 30ms = 90ms
        assertEquals(90, currentTime,
            "Bounded traverse should take 90ms (3 batches of 3 at 30ms each). Got ${currentTime}ms")
    }

    // ════════════════════════════════════════════════════════════════════════
    // 2. PHASE BARRIERS: then actually blocks the next phase
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `then is a true phase barrier - post-barrier with waits`() = runTest {
        // then creates a real phase barrier. Post-barrier with calls
        // do NOT launch until the barrier completes.
        //
        // Timeline for: kap(f).with{A@30}.with{B@30}.then{C@50}.with{D@30}
        //   t=0:  A, B launch (pre-barrier ap, parallel)
        //   t=30: A, B complete. then runs C
        //   t=80: C completes. Barrier signal fires. D launches.
        //   t=110: D completes.
        val result = Kap.of { a: String -> { b: String -> { c: String -> { d: String -> "$a|$b|$c|$d" } } } }
                .with { delay(30); "A" }
                .with { delay(30); "B" }
                .then { delay(50); "C" }
                .with { delay(30); "D" }.executeGraph()

        assertEquals("A|B|C|D", result)
        // Phase 1: max(30,30) = 30. Barrier: 50. Phase 2: 30. Total: 110ms
        assertEquals(110, currentTime,
            "Expected 110ms. then is a true barrier: 30(A,B) + 50(C) + 30(D)")
    }

    @Test
    fun `thenValue preserves old eager-launch semantics - no barrier`() = runTest {
        // thenValue fills a slot sequentially but does NOT gate subsequent with calls.
        // D launches at t=0 and overlaps with everything.
        val result = Kap.of { a: String -> { b: String -> { c: String -> { d: String -> "$a|$b|$c|$d" } } } }
                .with { delay(30); "A" }
                .with { delay(30); "B" }
                .thenValue { delay(50); "C" }
                .with { delay(30); "D" }.executeGraph()

        assertEquals("A|B|C|D", result)
        // D launches eagerly at t=0. Total: max(30,30) + 50 = 80ms (D overlaps)
        assertEquals(80, currentTime,
            "Expected 80ms. thenValue has no barrier: D launched at t=0")
    }

    @Test
    fun `then ordering proof - barrier value depends on prior phase`() = runTest {
        // Proves the VALUE from then is sequenced correctly,
        // even though subsequent with computations launch eagerly.
        val order = mutableListOf<String>()

        val result = Kap.of { a: String -> { b: String -> { c: String -> { d: String -> "$a|$b|$c|$d" } } } }
                .with { order.add("A"); "A" }
                .with { order.add("B"); "B" }
                .then { order.add("C"); "C" }
                .then { order.add("D"); "D" }.executeGraph()

        assertEquals("A|B|C|D", result)
        // C must come after both A and B (barrier semantics)
        val cIdx = order.indexOf("C")
        assertTrue(order.indexOf("A") < cIdx, "A before C")
        assertTrue(order.indexOf("B") < cIdx, "B before C")
        // D must come after C (sequential barriers)
        assertTrue(cIdx < order.indexOf("D"), "C before D")
    }

    @Test
    fun `multi-phase timing with barriers and parallel phases`() = runTest {
        // Phase 1: A, B, C launch in parallel (40ms)
        // Barrier D: starts at t=40, ends at t=70
        // Phase 2: E, F launch in parallel AFTER barrier (40ms)
        // Total: 40 + 30 + 40 = 110ms
        val result = Kap.of { a: String -> { b: String -> { c: String -> { d: String -> { e: String -> { f: String ->
                "$a|$b|$c|$d|$e|$f"
            } } } } } }
                .with { delay(40); "A" }            // ┐ phase 1: parallel
                .with { delay(40); "B" }            // │
                .with { delay(40); "C" }            // ┘
                .then { delay(30); "D" }    // ── barrier
                .with { delay(40); "E" }            // ┐ phase 2: parallel (after barrier)
                .with { delay(40); "F" }            // ┘
                .executeGraph()

        assertEquals("A|B|C|D|E|F", result)
        assertEquals(110, currentTime,
            "Expected 110ms: 40(A,B,C) + 30(D) + 40(E,F). Got ${currentTime}ms")
    }

    @Test
    fun `multiple with calls after then all run in parallel`() = runTest {
        // Proves that post-barrier with calls launch SIMULTANEOUSLY when the barrier fires
        val result = Kap.of { a: String -> { b: String -> { c: String -> { d: String -> { e: String ->
                "$a|$b|$c|$d|$e"
            } } } } }
                .with { delay(20); "A" }
                .then { delay(30); "B" }
                .with { delay(40); "C" }    // ┐ all three launch when barrier fires
                .with { delay(40); "D" }    // │ at t=50, and complete at t=90
                .with { delay(40); "E" }    // ┘
                .executeGraph()

        assertEquals("A|B|C|D|E", result)
        // 20(A) + 30(B barrier) + 40(C,D,E parallel) = 90ms
        assertEquals(90, currentTime,
            "Expected 90ms: 20 + 30 + max(40,40,40) = 90. Got ${currentTime}ms")
    }

    // ════════════════════════════════════════════════════════════════════════
    // 3. MASS CANCELLATION: structured concurrency cancels N siblings
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `failure cancels 9 out of 10 parallel siblings`() = runTest {
        val cancelled = (0 until 9).map { CompletableDeferred<Boolean>() }
        val allStarted = (0 until 10).map { CompletableDeferred<Unit>() }

        runCatching {
            Kap.of { a: String -> { b: String -> { c: String -> { d: String -> { e: String ->
                         { f: String -> { g: String -> { h: String -> { i: String -> { j: String ->
                    "$a|$b|$c|$d|$e|$f|$g|$h|$i|$j"
                } } } } } } } } } }
                    .with { allStarted[0].complete(Unit); try { awaitCancellation() } catch (e: kotlinx.coroutines.CancellationException) { cancelled[0].complete(true); throw e } }
                    .with { allStarted[1].complete(Unit); try { awaitCancellation() } catch (e: kotlinx.coroutines.CancellationException) { cancelled[1].complete(true); throw e } }
                    .with { allStarted[2].complete(Unit); try { awaitCancellation() } catch (e: kotlinx.coroutines.CancellationException) { cancelled[2].complete(true); throw e } }
                    .with { allStarted[3].complete(Unit); try { awaitCancellation() } catch (e: kotlinx.coroutines.CancellationException) { cancelled[3].complete(true); throw e } }
                    .with { allStarted[4].complete(Unit); try { awaitCancellation() } catch (e: kotlinx.coroutines.CancellationException) { cancelled[4].complete(true); throw e } }
                    .with { allStarted[5].complete(Unit); try { awaitCancellation() } catch (e: kotlinx.coroutines.CancellationException) { cancelled[5].complete(true); throw e } }
                    .with { allStarted[6].complete(Unit); try { awaitCancellation() } catch (e: kotlinx.coroutines.CancellationException) { cancelled[6].complete(true); throw e } }
                    .with { allStarted[7].complete(Unit); try { awaitCancellation() } catch (e: kotlinx.coroutines.CancellationException) { cancelled[7].complete(true); throw e } }
                    .with { allStarted[8].complete(Unit); try { awaitCancellation() } catch (e: kotlinx.coroutines.CancellationException) { cancelled[8].complete(true); throw e } }
                    // The 10th: ensures all others started, then crashes
                    .with {
                        allStarted[9].complete(Unit)
                        allStarted.forEach { it.await() }
                        throw RuntimeException("crash")
                    }.executeGraph()
        }

        // ALL 9 siblings must have been cancelled
        cancelled.forEachIndexed { i, deferred ->
            assertTrue(deferred.await(), "Sibling $i should have been cancelled")
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // 4. LATENCY REGRESSION: library virtual time == raw coroutines
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `library has zero overhead vs raw coroutines in virtual time`() = runTest {
        // Raw coroutines
        coroutineScope {
            val d1 = async { delay(50); "A" }
            val d2 = async { delay(50); "B" }
            val d3 = async { delay(50); "C" }
            val d4 = async { delay(50); "D" }
            val d5 = async { delay(50); "E" }
            "${d1.await()}|${d2.await()}|${d3.await()}|${d4.await()}|${d5.await()}"
        }
        val rawTime = currentTime

        // Library (starts from same virtual clock)
        Kap.of { a: String -> { b: String -> { c: String -> { d: String -> { e: String -> "$a|$b|$c|$d|$e" } } } } }
                .with { delay(50); "A" }
                .with { delay(50); "B" }
                .with { delay(50); "C" }
                .with { delay(50); "D" }
                .with { delay(50); "E" }.executeGraph()
        val libTime = currentTime - rawTime

        // Both should be exactly 50ms in virtual time
        assertEquals(50, rawTime, "Raw coroutines should take 50ms virtual time")
        assertEquals(50, libTime, "Library should take 50ms virtual time (same as raw)")
    }

    @Test
    fun `multi-phase library timing is correct`() = runTest {
        // Phase 1: user, cart, promos launch in parallel (40ms)
        // Barrier 1: valid (30ms) → t=70
        // Phase 2: shipping, tax launch in parallel (40ms) → t=110
        // Barrier 2: pay (50ms) → t=160
        Kap.of { a: String -> { b: String -> { c: String -> { d: String -> { e: String -> { f: String -> { g: String ->
                "$a|$b|$c|$d|$e|$f|$g"
            } } } } } } }
                .with { delay(40); "user" }           // ┐ phase 1
                .with { delay(40); "cart" }           // │
                .with { delay(40); "promos" }         // ┘
                .then { delay(30); "valid" }  // ── barrier 1
                .with { delay(40); "shipping" }       // ┐ phase 2 (after barrier 1)
                .with { delay(40); "tax" }            // ┘
                .then { delay(50); "pay" }    // ── barrier 2
                .executeGraph()

        // 40(phase1) + 30(barrier1) + 40(phase2) + 50(barrier2) = 160ms
        assertEquals(160, currentTime,
            "Expected 160ms: 40+30+40+50. Got ${currentTime}ms")
    }

    // ════════════════════════════════════════════════════════════════════════
    // 5. REAL-WORLD BFF SCENARIO: 14 calls, 5 phases, exact timing
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `14-call 5-phase dashboard completes in expected virtual time`() = runTest {
        // Phase 1: 4 calls @ 30ms parallel = 30ms
        // Barrier 1: 20ms → t=50
        // Phase 2: 5 calls @ 30ms parallel = 30ms → t=80
        // Barrier 2: 20ms → t=100
        // Phase 3: 3 calls @ 30ms parallel = 30ms → t=130
        // Total: 30 + 20 + 30 + 20 + 30 = 130ms
        // Sequential would be: 14*30 + 2*20 = 460ms (3.5x speedup)
        val result = Kap.of { v1: String -> { v2: String -> { v3: String -> { v4: String ->
                     { v5: String ->
                     { v6: String -> { v7: String -> { v8: String -> { v9: String -> { v10: String ->
                     { v11: String ->
                     { v12: String -> { v13: String -> { v14: String ->
                listOf(v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14).joinToString("|")
            } } } } } } } } } } } } } }
                .with { delay(30); "user" }          // ┐ phase 1
                .with { delay(30); "prefs" }         // │
                .with { delay(30); "loyalty" }       // │
                .with { delay(30); "orders" }        // ┘
                .then { delay(20); "persona" } // ── barrier 1
                .with { delay(30); "recs" }          // ┐ phase 2
                .with { delay(30); "promos" }        // │
                .with { delay(30); "trending" }      // │
                .with { delay(30); "seller" }        // │
                .with { delay(30); "flash" }         // ┘
                .then { delay(20); "layout" }  // ── barrier 2
                .with { delay(30); "notifs" }        // ┐ phase 3
                .with { delay(30); "cart" }          // │
                .with { delay(30); "wishlist" }      // ┘
                .executeGraph()

        assertEquals(14, result.split("|").size)
        assertEquals(130, currentTime,
            "14-call 5-phase dashboard: expected 130ms (30+20+30+20+30). " +
            "Got ${currentTime}ms. Sequential would be 460ms.")
    }
}
