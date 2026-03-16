package applicative

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
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Indisputable concurrency proofs — each test uses virtual-time assertions
 * or barrier-based deadlock detection to prove the framework's concurrency
 * properties beyond any doubt.
 *
 * Categories:
 * 1. Virtual-time timing: asserts parallel execution uses O(max) not O(sum) virtual time
 * 2. Phase barriers: proves followedBy blocks with measurable virtual-time delay
 * 3. Parallel error accumulation: barrier-proven branches + error accumulation
 * 4. Mass cancellation: structured concurrency cancels N siblings on failure
 * 5. Latency regression: library virtual time == raw coroutines virtual time
 * 6. Real-world BFF scenario with timing
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ConcurrencyProofTest {

    // ════════════════════════════════════════════════════════════════════════
    // 1. VIRTUAL-TIME PROOF: parallel ap completes in O(max), not O(sum)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `5 parallel 50ms calls complete in 50ms virtual time not 250ms`() = runTest {
        Async {
            lift5 { a: String, b: String, c: String, d: String, e: String -> "$a|$b|$c|$d|$e" }
                .ap { delay(50); "A" }
                .ap { delay(50); "B" }
                .ap { delay(50); "C" }
                .ap { delay(50); "D" }
                .ap { delay(50); "E" }
        }

        // If sequential: 250ms. If parallel: 50ms.
        assertEquals(50, currentTime,
            "Expected 50ms virtual time (parallel). Got ${currentTime}ms (sequential would be 250ms)")
    }

    @Test
    fun `10 parallel 30ms calls complete in 30ms virtual time not 300ms`() = runTest {
        Async {
            lift10 { a: String, b: String, c: String, d: String, e: String,
                     f: String, g: String, h: String, i: String, j: String ->
                listOf(a, b, c, d, e, f, g, h, i, j).joinToString("|")
            }
                .ap { delay(30); "v1" }.ap { delay(30); "v2" }.ap { delay(30); "v3" }
                .ap { delay(30); "v4" }.ap { delay(30); "v5" }.ap { delay(30); "v6" }
                .ap { delay(30); "v7" }.ap { delay(30); "v8" }.ap { delay(30); "v9" }
                .ap { delay(30); "v10" }
        }

        assertEquals(30, currentTime,
            "Expected 30ms virtual time (parallel). Got ${currentTime}ms (sequential would be 300ms)")
    }

    @Test
    fun `traverse with concurrency 3 over 9 items takes 3x longer than unbounded`() = runTest {
        Async {
            (1..9).toList().traverse(3) { i ->
                Computation { delay(30); "v$i" }
            }
        }
        // Bounded (3 at a time, 9 items, 30ms each): 3 batches × 30ms = 90ms
        assertEquals(90, currentTime,
            "Bounded traverse should take 90ms (3 batches of 3 at 30ms each). Got ${currentTime}ms")
    }

    // ════════════════════════════════════════════════════════════════════════
    // 2. PHASE BARRIERS: followedBy actually blocks the next phase
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `followedBy is a true phase barrier - post-barrier ap waits`() = runTest {
        // followedBy creates a real phase barrier. Post-barrier ap calls
        // do NOT launch until the barrier completes.
        //
        // Timeline for: lift4(f).ap{A@30}.ap{B@30}.followedBy{C@50}.ap{D@30}
        //   t=0:  A, B launch (pre-barrier ap, parallel)
        //   t=30: A, B complete. followedBy runs C
        //   t=80: C completes. Barrier signal fires. D launches.
        //   t=110: D completes.
        val result = Async {
            lift4 { a: String, b: String, c: String, d: String -> "$a|$b|$c|$d" }
                .ap { delay(30); "A" }
                .ap { delay(30); "B" }
                .followedBy { delay(50); "C" }
                .ap { delay(30); "D" }
        }

        assertEquals("A|B|C|D", result)
        // Phase 1: max(30,30) = 30. Barrier: 50. Phase 2: 30. Total: 110ms
        assertEquals(110, currentTime,
            "Expected 110ms. followedBy is a true barrier: 30(A,B) + 50(C) + 30(D)")
    }

    @Test
    fun `thenValue preserves old eager-launch semantics - no barrier`() = runTest {
        // thenValue fills a slot sequentially but does NOT gate subsequent ap calls.
        // D launches at t=0 and overlaps with everything.
        val result = Async {
            lift4 { a: String, b: String, c: String, d: String -> "$a|$b|$c|$d" }
                .ap { delay(30); "A" }
                .ap { delay(30); "B" }
                .thenValue { delay(50); "C" }
                .ap { delay(30); "D" }
        }

        assertEquals("A|B|C|D", result)
        // D launches eagerly at t=0. Total: max(30,30) + 50 = 80ms (D overlaps)
        assertEquals(80, currentTime,
            "Expected 80ms. thenValue has no barrier: D launched at t=0")
    }

    @Test
    fun `followedBy ordering proof - barrier value depends on prior phase`() = runTest {
        // Proves the VALUE from followedBy is sequenced correctly,
        // even though subsequent ap computations launch eagerly.
        val order = mutableListOf<String>()

        val result = Async {
            lift4 { a: String, b: String, c: String, d: String -> "$a|$b|$c|$d" }
                .ap { order.add("A"); "A" }
                .ap { order.add("B"); "B" }
                .followedBy { order.add("C"); "C" }
                .followedBy { order.add("D"); "D" }
        }

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
        val result = Async {
            lift6 { a: String, b: String, c: String, d: String, e: String, f: String ->
                "$a|$b|$c|$d|$e|$f"
            }
                .ap { delay(40); "A" }            // ┐ phase 1: parallel
                .ap { delay(40); "B" }            // │
                .ap { delay(40); "C" }            // ┘
                .followedBy { delay(30); "D" }    // ── barrier
                .ap { delay(40); "E" }            // ┐ phase 2: parallel (after barrier)
                .ap { delay(40); "F" }            // ┘
        }

        assertEquals("A|B|C|D|E|F", result)
        assertEquals(110, currentTime,
            "Expected 110ms: 40(A,B,C) + 30(D) + 40(E,F). Got ${currentTime}ms")
    }

    @Test
    fun `multiple ap calls after followedBy all run in parallel`() = runTest {
        // Proves that post-barrier ap calls launch SIMULTANEOUSLY when the barrier fires
        val result = Async {
            lift5 { a: String, b: String, c: String, d: String, e: String ->
                "$a|$b|$c|$d|$e"
            }
                .ap { delay(20); "A" }
                .followedBy { delay(30); "B" }
                .ap { delay(40); "C" }    // ┐ all three launch when barrier fires
                .ap { delay(40); "D" }    // │ at t=50, and complete at t=90
                .ap { delay(40); "E" }    // ┘
        }

        assertEquals("A|B|C|D|E", result)
        // 20(A) + 30(B barrier) + 40(C,D,E parallel) = 90ms
        assertEquals(90, currentTime,
            "Expected 90ms: 20 + 30 + max(40,40,40) = 90. Got ${currentTime}ms")
    }

    // ════════════════════════════════════════════════════════════════════════
    // 3. PARALLEL ERROR ACCUMULATION from truly concurrent branches
    //    Combines barrier proof (deadlock if sequential) with error accumulation
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `apV accumulates errors from branches proven to run in parallel via barriers`() = runTest {
        val latchA = CompletableDeferred<Unit>()
        val latchB = CompletableDeferred<Unit>()
        val latchC = CompletableDeferred<Unit>()

        val result = Async {
            liftV3<String, String, String, String, String> { a, b, c -> "$a|$b|$c" }
                .apV {
                    latchA.complete(Unit)
                    latchB.await(); latchC.await()
                    Either.Left(Nel("err-A"))
                }
                .apV {
                    latchB.complete(Unit)
                    latchA.await(); latchC.await()
                    Either.Left(Nel("err-B"))
                }
                .apV {
                    latchC.complete(Unit)
                    latchA.await(); latchB.await()
                    Either.Left(Nel("err-C"))
                }
        }

        // If sequential, this DEADLOCKS. Completion proves true parallelism.
        // If errors weren't accumulated, we'd only get 1 error.
        assertIs<Either.Left<Nel<String>>>(result)
        assertEquals(listOf("err-A", "err-B", "err-C"), result.value.toList())
    }

    @Test
    fun `zipV accumulates errors from parallel branches - timing proof`() = runTest {
        val result = Async {
            zipV(
                { delay(50); Either.Left(Nel("e1")) as Either<Nel<String>, String> },
                { delay(50); Either.Left(Nel("e2")) },
                { delay(50); Either.Left(Nel("e3")) },
                { delay(50); Either.Left(Nel("e4")) },
            ) { a, b, c, d -> "$a|$b|$c|$d" }
        }

        assertIs<Either.Left<Nel<String>>>(result)
        assertEquals(4, result.value.size)
        // If parallel: 50ms. If sequential: 200ms.
        assertEquals(50, currentTime,
            "Expected 50ms (parallel validation). Got ${currentTime}ms (sequential would be 200ms)")
    }

    // ════════════════════════════════════════════════════════════════════════
    // 4. MASS CANCELLATION: structured concurrency cancels N siblings
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `failure cancels 9 out of 10 parallel siblings`() = runTest {
        val cancelled = (0 until 9).map { CompletableDeferred<Boolean>() }
        val allStarted = (0 until 10).map { CompletableDeferred<Unit>() }

        runCatching {
            Async {
                lift10 { a: String, b: String, c: String, d: String, e: String,
                         f: String, g: String, h: String, i: String, j: String ->
                    "$a|$b|$c|$d|$e|$f|$g|$h|$i|$j"
                }
                    .ap { allStarted[0].complete(Unit); try { awaitCancellation() } catch (e: kotlinx.coroutines.CancellationException) { cancelled[0].complete(true); throw e } }
                    .ap { allStarted[1].complete(Unit); try { awaitCancellation() } catch (e: kotlinx.coroutines.CancellationException) { cancelled[1].complete(true); throw e } }
                    .ap { allStarted[2].complete(Unit); try { awaitCancellation() } catch (e: kotlinx.coroutines.CancellationException) { cancelled[2].complete(true); throw e } }
                    .ap { allStarted[3].complete(Unit); try { awaitCancellation() } catch (e: kotlinx.coroutines.CancellationException) { cancelled[3].complete(true); throw e } }
                    .ap { allStarted[4].complete(Unit); try { awaitCancellation() } catch (e: kotlinx.coroutines.CancellationException) { cancelled[4].complete(true); throw e } }
                    .ap { allStarted[5].complete(Unit); try { awaitCancellation() } catch (e: kotlinx.coroutines.CancellationException) { cancelled[5].complete(true); throw e } }
                    .ap { allStarted[6].complete(Unit); try { awaitCancellation() } catch (e: kotlinx.coroutines.CancellationException) { cancelled[6].complete(true); throw e } }
                    .ap { allStarted[7].complete(Unit); try { awaitCancellation() } catch (e: kotlinx.coroutines.CancellationException) { cancelled[7].complete(true); throw e } }
                    .ap { allStarted[8].complete(Unit); try { awaitCancellation() } catch (e: kotlinx.coroutines.CancellationException) { cancelled[8].complete(true); throw e } }
                    // The 10th: ensures all others started, then crashes
                    .ap {
                        allStarted[9].complete(Unit)
                        allStarted.forEach { it.await() }
                        throw RuntimeException("crash")
                    }
            }
        }

        // ALL 9 siblings must have been cancelled
        cancelled.forEachIndexed { i, deferred ->
            assertTrue(deferred.await(), "Sibling $i should have been cancelled")
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // 5. LATENCY REGRESSION: library virtual time == raw coroutines
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
        Async {
            lift5 { a: String, b: String, c: String, d: String, e: String -> "$a|$b|$c|$d|$e" }
                .ap { delay(50); "A" }
                .ap { delay(50); "B" }
                .ap { delay(50); "C" }
                .ap { delay(50); "D" }
                .ap { delay(50); "E" }
        }
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
        Async {
            lift7 { a: String, b: String, c: String, d: String, e: String, f: String, g: String ->
                "$a|$b|$c|$d|$e|$f|$g"
            }
                .ap { delay(40); "user" }           // ┐ phase 1
                .ap { delay(40); "cart" }           // │
                .ap { delay(40); "promos" }         // ┘
                .followedBy { delay(30); "valid" }  // ── barrier 1
                .ap { delay(40); "shipping" }       // ┐ phase 2 (after barrier 1)
                .ap { delay(40); "tax" }            // ┘
                .followedBy { delay(50); "pay" }    // ── barrier 2
        }

        // 40(phase1) + 30(barrier1) + 40(phase2) + 50(barrier2) = 160ms
        assertEquals(160, currentTime,
            "Expected 160ms: 40+30+40+50. Got ${currentTime}ms")
    }

    // ════════════════════════════════════════════════════════════════════════
    // 6. REAL-WORLD BFF SCENARIO: 14 calls, 5 phases, exact timing
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
        val result = Async {
            lift14 { v1: String, v2: String, v3: String, v4: String,
                     v5: String,
                     v6: String, v7: String, v8: String, v9: String, v10: String,
                     v11: String,
                     v12: String, v13: String, v14: String ->
                listOf(v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14).joinToString("|")
            }
                .ap { delay(30); "user" }          // ┐ phase 1
                .ap { delay(30); "prefs" }         // │
                .ap { delay(30); "loyalty" }       // │
                .ap { delay(30); "orders" }        // ┘
                .followedBy { delay(20); "persona" } // ── barrier 1
                .ap { delay(30); "recs" }          // ┐ phase 2
                .ap { delay(30); "promos" }        // │
                .ap { delay(30); "trending" }      // │
                .ap { delay(30); "seller" }        // │
                .ap { delay(30); "flash" }         // ┘
                .followedBy { delay(20); "layout" }  // ── barrier 2
                .ap { delay(30); "notifs" }        // ┐ phase 3
                .ap { delay(30); "cart" }          // │
                .ap { delay(30); "wishlist" }      // ┘
        }

        assertEquals(14, result.split("|").size)
        assertEquals(130, currentTime,
            "14-call 5-phase dashboard: expected 130ms (30+20+30+20+30). " +
            "Got ${currentTime}ms. Sequential would be 460ms.")
    }
}
