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
 * andThen used INSIDE ap/then chains — the whole point of the library.
 *
 * Categories:
 * 1. andThen creates a TRUE phase boundary (vs then's eager launch)
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
    // 1. andThen creates a TRUE phase boundary
    //
    //    Unlike then (where with right sides launch eagerly), andThen
    //    constructs the next Effect INSIDE its lambda — so subsequent
    //    with calls can't launch until andThen's lambda has run.
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `andThen prevents eager launch - post-andThen with waits for andThen result`() = runTest {
        // With then, the ap{D@30} would launch at t=0.
        // With andThen, the ap{D@30} cannot exist until andThen's lambda runs (t=80).
        val result = Async {
            kap { a: String, b: String -> "$a|$b" }
                .with { delay(30); "A" }
                .with { delay(30); "B" }
                .andThen { ab ->
                    // This lambda runs at t=30 (after A and B complete).
                    // The next ap{C@50} launches INSIDE this lambda.
                    kap { c: String, d: String -> "$ab|$c|$d" }
                        .with { delay(50); "C" }
                        .with { delay(50); "D" }
                }
        }

        assertEquals("A|B|C|D", result)
        // t=0: A, B launch. t=30: A, B done, andThen lambda runs, C+D launch.
        // t=80: C, D done. Total: 30 + 50 = 80ms.
        assertEquals(80, currentTime,
            "andThen creates true boundary: 30ms (A,B) + 50ms (C,D) = 80ms. Got ${currentTime}ms")
    }

    @Test
    fun `then and andThen both create true phase boundaries`() = runTest {
        // then version: C waits for barrier (true phase boundary)
        val thenResult = Async {
            kap { a: String, b: String, c: String -> "$a|$b|$c" }
                .with { delay(30); "A" }
                .then { delay(50); "B" }  // barrier
                .with { delay(30); "C" }          // waits for barrier, launches at t=80
        }
        val thenTime = currentTime  // 30 + 50 + 30 = 110ms

        // andThen version: same timing, but passes value
        Async {
            Effect.of(Unit).andThen {
                kap { a: String, b: String -> "$a|$b" }
                    .with { delay(30); "A" }
                    .with { delay(30); "B" }
            }.andThen { ab ->
                kap { c: String, d: String -> "$ab|$c|$d" }
                    .with { delay(50); "C" }
                    .with { delay(50); "D" }
            }
        }
        val andThenTime = currentTime - thenTime  // 30 + 50 = 80ms

        assertEquals("A|B|C", thenResult)
        assertEquals(110, thenTime, "then: 30+50+30=110 (C waits for barrier)")
        assertEquals(80, andThenTime, "andThen: 30+50=80 (C,D wait then parallel)")
    }

    @Test
    fun `thenValue vs then - thenValue allows eager launch`() = runTest {
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
    fun `andThen enables value-dependent parallel fan-out with timing proof`() = runTest {
        val result = Async {
            Effect { delay(20); 10 }.andThen { base ->
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
            "andThen(20ms) then 3 parallel(30ms) = 50ms. Got ${currentTime}ms")
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
                    with(Effect { delay(500); "slow-B" }
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
                    with(Effect { delay(20); "fast" }
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
                    with(Effect {
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
                    with(Effect<String> { throw RuntimeException("boom") }
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
                        with(Effect<String> { throw RuntimeException("A-error") }
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
                Effect { delay(100); "slow" },
                Effect { delay(20); "fast" },
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
                Effect { delay(100); "a" },
                Effect { delay(200); "b" },
                Effect { delay(10); "c" },
                Effect { delay(150); "d" },
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
                        Effect { delay(100); "primary-A" },
                        Effect { delay(20); "cache-A" },
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
                    with(Effect {
                        fetchAttempts++
                        if (fetchAttempts < 2) throw RuntimeException("flaky")
                        delay(20); "cart-data"
                    }.retry(3, delay = 10.milliseconds)) { execute() }
                }
                // Branch 3: slow service with timeout + fallback
                .with {
                    with(Effect { delay(500); "promos-fresh" }
                        .timeout(40.milliseconds, "promos-cached")) { execute() }
                }
                // Branch 4: race between primary and cache
                .with {
                    with(race(
                        Effect { delay(100); "shipping-api" },
                        Effect { delay(15); "shipping-cache" },
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
    fun `consecutive then barriers are strictly sequential`() = runTest {
        val result = Async {
            kap { a: String, b: String, c: String, d: String -> "$a|$b|$c|$d" }
                .then { delay(20); "A" }
                .then { delay(30); "B" }
                .then { delay(40); "C" }
                .then { delay(10); "D" }
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
                Effect { delay(30); "A" },
                Effect { delay(50); "B" },
                Effect { delay(20); "C" },
                Effect { delay(40); "D" },
            ).sequence()
        }

        assertEquals(listOf("A", "B", "C", "D"), result)
        assertEquals(50, currentTime,
            "Unbounded sequence: max(30,50,20,40) = 50ms. Got ${currentTime}ms")
    }

    // ════════════════════════════════════════════════════════════════════════
    // 8. Laziness: Effect is a description, not an execution
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `computation does not execute until Async invokes it`() = runTest {
        var executed = false

        // Build a full computation graph — should NOT run anything yet
        val graph = kap { a: String, b: String, c: String -> "$a|$b|$c" }
            .with(Effect { executed = true; "A" })
            .with(Effect { "B" })
            .with(Effect { "C" })

        // Verify nothing happened
        assertEquals(false, executed, "Effect should NOT execute during construction")

        // Now execute
        val result = Async { graph }
        assertEquals(true, executed, "Effect should execute inside Async {}")
        assertEquals("A|B|C", result)
    }

    @Test
    fun `same computation can be executed multiple times`() = runTest {
        var counter = 0

        val graph = Effect { ++counter }

        val r1 = Async { graph }
        val r2 = Async { graph }
        val r3 = Async { graph }

        assertEquals(1, r1)
        assertEquals(2, r2)
        assertEquals(3, r3)
        assertEquals(3, counter, "Effect executed 3 times, once per Async call")
    }

}
