package applicative

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/**
 * Tests that prove combinators compose correctly INSIDE parallel chains.
 *
 * The existing tests verify each combinator in isolation. These tests verify
 * the production usage pattern: timeout, retry, recover, traced, race, and
 * flatMap used INSIDE ap/followedBy chains — the whole point of the library.
 *
 * Categories:
 * 1. flatMap creates a TRUE phase boundary (vs followedBy's eager launch)
 * 2. timeout inside with chains
 * 3. retry inside with chains
 * 4. recover inside with chains (error isolation)
 * 5. race timing proof
 * 6. Full production pattern: timeout + retry + recover + traced inside with
 * 7. Consecutive barriers timing
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CompositionProofTest {

    // ════════════════════════════════════════════════════════════════════════
    // 1. flatMap creates a TRUE phase boundary
    //
    //    Unlike followedBy (where with right sides launch eagerly), flatMap
    //    constructs the next Computation INSIDE its lambda — so subsequent
    //    with calls can't launch until flatMap's lambda has run.
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `flatMap prevents eager launch - post-flatMap with waits for flatMap result`() = runTest {
        // With followedBy, the ap{D@30} would launch at t=0.
        // With flatMap, the ap{D@30} cannot exist until flatMap's lambda runs (t=80).
        val result = Async {
            kap { a: String, b: String -> "$a|$b" }
                .with { delay(30); "A" }
                .with { delay(30); "B" }
                .flatMap { ab ->
                    // This lambda runs at t=30 (after A and B complete).
                    // The next ap{C@50} launches INSIDE this lambda.
                    kap { c: String, d: String -> "$ab|$c|$d" }
                        .with { delay(50); "C" }
                        .with { delay(50); "D" }
                }
        }

        assertEquals("A|B|C|D", result)
        // t=0: A, B launch. t=30: A, B done, flatMap lambda runs, C+D launch.
        // t=80: C, D done. Total: 30 + 50 = 80ms.
        assertEquals(80, currentTime,
            "flatMap creates true boundary: 30ms (A,B) + 50ms (C,D) = 80ms. Got ${currentTime}ms")
    }

    @Test
    fun `followedBy and flatMap both create true phase boundaries`() = runTest {
        // followedBy version: C waits for barrier (true phase boundary)
        val followedByResult = Async {
            kap { a: String, b: String, c: String -> "$a|$b|$c" }
                .with { delay(30); "A" }
                .followedBy { delay(50); "B" }  // barrier
                .with { delay(30); "C" }          // waits for barrier, launches at t=80
        }
        val followedByTime = currentTime  // 30 + 50 + 30 = 110ms

        // flatMap version: same timing, but passes value
        Async {
            Computation.of(Unit).flatMap {
                kap { a: String, b: String -> "$a|$b" }
                    .with { delay(30); "A" }
                    .with { delay(30); "B" }
            }.flatMap { ab ->
                kap { c: String, d: String -> "$ab|$c|$d" }
                    .with { delay(50); "C" }
                    .with { delay(50); "D" }
            }
        }
        val flatMapTime = currentTime - followedByTime  // 30 + 50 = 80ms

        assertEquals("A|B|C", followedByResult)
        assertEquals(110, followedByTime, "followedBy: 30+50+30=110 (C waits for barrier)")
        assertEquals(80, flatMapTime, "flatMap: 30+50=80 (C,D wait then parallel)")
    }

    @Test
    fun `thenValue vs followedBy - thenValue allows eager launch`() = runTest {
        // thenValue: C launches eagerly at t=0 (old behavior)
        val thenValueResult = Async {
            kap { a: String, b: String, c: String -> "$a|$b|$c" }
                .with { delay(30); "A" }
                .thenValue { delay(50); "B" }
                .with { delay(30); "C" }          // launches at t=0, overlaps
        }
        val thenValueTime = currentTime  // 30 + 50 = 80ms (C was already done)

        assertEquals("A|B|C", thenValueResult)
        assertEquals(80, thenValueTime, "thenValue: 30+50=80 (C launched eagerly)")
    }

    @Test
    fun `flatMap enables value-dependent parallel fan-out with timing proof`() = runTest {
        val result = Async {
            Computation { delay(20); 10 }.flatMap { base ->
                // base is available here — fan out with computed values
                kap { a: Int, b: Int, c: Int -> a + b + c }
                    .with { delay(30); base * 2 }  // 20
                    .with { delay(30); base * 3 }  // 30
                    .with { delay(30); base * 4 }  // 40
            }
        }

        assertEquals(90, result) // 20 + 30 + 40
        // t=0-20: compute base. t=20-50: three parallel multiplications. Total: 50ms
        assertEquals(50, currentTime,
            "flatMap(20ms) then 3 parallel(30ms) = 50ms. Got ${currentTime}ms")
    }

    // ════════════════════════════════════════════════════════════════════════
    // 2. timeout INSIDE with chains
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `timeout inside with - slow branch gets default while others proceed`() = runTest {
        val result = Async {
            kap { a: String, b: String, c: String -> "$a|$b|$c" }
                .with { delay(30); "fast-A" }
                .with {
                    with(Computation { delay(500); "slow-B" }
                        .timeout(50.milliseconds, "timeout-B")) { execute() }
                }
                .with { delay(30); "fast-C" }
        }

        assertEquals("fast-A|timeout-B|fast-C", result)
        // All launch at t=0. A done at 30, C done at 30, B times out at 50.
        assertEquals(50, currentTime,
            "Slowest branch (timeout at 50ms) determines total. Got ${currentTime}ms")
    }

    @Test
    fun `timeout inside with - fast branch returns before timeout`() = runTest {
        val result = Async {
            kap { a: String, b: String -> "$a|$b" }
                .with {
                    with(Computation { delay(20); "fast" }
                        .timeout(100.milliseconds, "timeout")) { execute() }
                }
                .with { delay(30); "other" }
        }

        assertEquals("fast|other", result)
        assertEquals(30, currentTime, "Max(20,30) = 30ms")
    }

    // ════════════════════════════════════════════════════════════════════════
    // 3. retry INSIDE with chains
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `retry inside with - flaky branch retries while others proceed`() = runTest {
        var attempts = 0

        val result = Async {
            kap { a: String, b: String -> "$a|$b" }
                .with {
                    with(Computation {
                        attempts++
                        if (attempts < 3) throw RuntimeException("flaky")
                        delay(20); "recovered-B"
                    }.retry(3, delay = 10.milliseconds)) { execute() }
                }
                .with { delay(30); "stable-A" }
        }

        assertEquals("recovered-B|stable-A", result)
        assertEquals(3, attempts)
        // A launches at t=0, done at t=30.
        // B: attempt 1 fails at t=0, waits 10ms, attempt 2 fails at t=10, waits 10ms,
        // attempt 3 succeeds with 20ms delay = t=40.
        assertEquals(40, currentTime,
            "Retry(fail, wait 10, fail, wait 10, success+20ms) = 40ms. Got ${currentTime}ms")
    }

    // ════════════════════════════════════════════════════════════════════════
    // 4. recover INSIDE with chains - error isolation
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `recover inside with - one branch fails and recovers without affecting siblings`() = runTest {
        val result = Async {
            kap { a: String, b: String, c: String -> "$a|$b|$c" }
                .with { delay(30); "A" }
                .with {
                    with(Computation<String> { throw RuntimeException("boom") }
                        .recover { "recovered" }) { execute() }
                }
                .with { delay(30); "C" }
        }

        // B failed and recovered immediately. A and C completed normally.
        assertEquals("A|recovered|C", result)
        assertEquals(30, currentTime, "Recover is instant, siblings determine time")
    }

    @Test
    fun `recover on one branch does not suppress errors from other branches`() = runTest {
        // Branch A recovers its own error. Branch B throws.
        // B's error should propagate — recover on A doesn't affect B.
        val result = runCatching {
            Async {
                kap { a: String, b: String -> "$a|$b" }
                    .with {
                        with(Computation<String> { throw RuntimeException("A-error") }
                            .recover { "A-recovered" }) { execute() }
                    }
                    .with {
                        throw RuntimeException("B-crash")
                    }
            }
        }

        assertTrue(result.isFailure, "B's exception should propagate")
        assertEquals("B-crash", result.exceptionOrNull()?.message)
    }

    // ════════════════════════════════════════════════════════════════════════
    // 5. race timing proof
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `race completes in min time not max`() = runTest {
        val result = Async {
            race(
                Computation { delay(100); "slow" },
                Computation { delay(20); "fast" },
            )
        }

        assertEquals("fast", result)
        assertEquals(20, currentTime,
            "Race should complete in min(100,20) = 20ms. Got ${currentTime}ms")
    }

    @Test
    fun `raceN with 4 branches completes in fastest time`() = runTest {
        val result = Async {
            raceN(
                Computation { delay(100); "a" },
                Computation { delay(200); "b" },
                Computation { delay(10); "c" },
                Computation { delay(150); "d" },
            )
        }

        assertEquals("c", result)
        assertEquals(10, currentTime,
            "raceN should complete in min(100,200,10,150) = 10ms. Got ${currentTime}ms")
    }

    @Test
    fun `race inside with chain - use fastest data source`() = runTest {
        val result = Async {
            kap { a: String, b: String -> "$a|$b" }
                .with {
                    with(race(
                        Computation { delay(100); "primary-A" },
                        Computation { delay(20); "cache-A" },
                    )) { execute() }
                }
                .with { delay(30); "B" }
        }

        assertEquals("cache-A|B", result)
        // race(100,20) = 20ms, B = 30ms. All parallel. Total = max(20,30) = 30ms
        assertEquals(30, currentTime,
            "Race resolves at 20ms, B at 30ms, max=30ms. Got ${currentTime}ms")
    }

    // ════════════════════════════════════════════════════════════════════════
    // 6. Full production pattern: everything composed together
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `production pattern - timeout + retry + recover + race inside with chain`() = runTest {
        var fetchAttempts = 0

        val result = Async {
            kap { user: String, cart: String, promos: String, shipping: String ->
                "$user|$cart|$promos|$shipping"
            }
                // Branch 1: normal fast call
                .with { delay(20); "user-data" }
                // Branch 2: flaky service with retry
                .with {
                    with(Computation {
                        fetchAttempts++
                        if (fetchAttempts < 2) throw RuntimeException("flaky")
                        delay(20); "cart-data"
                    }.retry(3, delay = 10.milliseconds)) { execute() }
                }
                // Branch 3: slow service with timeout + fallback
                .with {
                    with(Computation { delay(500); "promos-fresh" }
                        .timeout(40.milliseconds, "promos-cached")) { execute() }
                }
                // Branch 4: race between primary and cache
                .with {
                    with(race(
                        Computation { delay(100); "shipping-api" },
                        Computation { delay(15); "shipping-cache" },
                    )) { execute() }
                }
        }

        assertEquals("user-data|cart-data|promos-cached|shipping-cache", result)
        assertEquals(2, fetchAttempts)
        // Branch 1: 20ms
        // Branch 2: fail at t=0, wait 10ms, succeed at t=10+20=30ms
        // Branch 3: timeout at 40ms → "promos-cached"
        // Branch 4: race resolves at 15ms
        // All parallel. Total = max(20, 30, 40, 15) = 40ms
        assertEquals(40, currentTime,
            "Production pattern: max(20, 30, 40, 15) = 40ms. Got ${currentTime}ms")
    }

    // ════════════════════════════════════════════════════════════════════════
    // 7. Consecutive barriers timing
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `consecutive followedBy barriers are strictly sequential`() = runTest {
        val result = Async {
            kap { a: String, b: String, c: String, d: String -> "$a|$b|$c|$d" }
                .followedBy { delay(20); "A" }
                .followedBy { delay(30); "B" }
                .followedBy { delay(40); "C" }
                .followedBy { delay(10); "D" }
        }

        assertEquals("A|B|C|D", result)
        // All barriers sequential: 20 + 30 + 40 + 10 = 100ms
        assertEquals(100, currentTime,
            "4 consecutive barriers: 20+30+40+10 = 100ms. Got ${currentTime}ms")
    }

    @Test
    fun `sequence unbounded completes in max element time`() = runTest {
        val result = Async {
            listOf(
                Computation { delay(30); "A" },
                Computation { delay(50); "B" },
                Computation { delay(20); "C" },
                Computation { delay(40); "D" },
            ).sequence()
        }

        assertEquals(listOf("A", "B", "C", "D"), result)
        assertEquals(50, currentTime,
            "Unbounded sequence: max(30,50,20,40) = 50ms. Got ${currentTime}ms")
    }

    // ════════════════════════════════════════════════════════════════════════
    // 8. Laziness: Computation is a description, not an execution
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `computation does not execute until Async invokes it`() = runTest {
        var executed = false

        // Build a full computation graph — should NOT run anything yet
        val graph = kap { a: String, b: String, c: String -> "$a|$b|$c" }
            .with(Computation { executed = true; "A" })
            .with(Computation { "B" })
            .with(Computation { "C" })

        // Verify nothing happened
        assertEquals(false, executed, "Computation should NOT execute during construction")

        // Now execute
        val result = Async { graph }
        assertEquals(true, executed, "Computation should execute inside Async {}")
        assertEquals("A|B|C", result)
    }

    @Test
    fun `same computation can be executed multiple times`() = runTest {
        var counter = 0

        val graph = Computation { ++counter }

        val r1 = Async { graph }
        val r2 = Async { graph }
        val r3 = Async { graph }

        assertEquals(1, r1)
        assertEquals(2, r2)
        assertEquals(3, r3)
        assertEquals(3, counter, "Computation executed 3 times, once per Async call")
    }

}
