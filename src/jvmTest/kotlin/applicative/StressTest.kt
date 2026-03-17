package applicative

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Stress tests verifying correctness under high concurrency and large fan-outs.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class StressTest {

    // ── massive parallel fan-out via traverse ───────────────────────────

    @Test
    fun `traverse 100 parallel computations completes correctly`() = runTest {
        val results = Async {
            (1..100).toList().traverse { i ->
                Computation { delay(50); "item-$i" }
            }
        }
        assertEquals(100, results.size)
        assertEquals("item-1", results.first())
        assertEquals("item-100", results.last())
        assertEquals(50, currentTime, "All 100 should run in parallel → 50ms")
    }

    @Test
    fun `traverse 500 parallel computations with bounded concurrency`() = runTest {
        val results = Async {
            (1..500).toList().traverse(concurrency = 50) { i ->
                Computation { delay(30); i }
            }
        }
        assertEquals(500, results.size)
        assertEquals((1..500).toList(), results)
        // 500 items, concurrency=50 → 10 batches × 30ms = 300ms
        assertEquals(300, currentTime, "Bounded concurrency: 10 batches × 30ms = 300ms")
    }

    @Test
    fun `traverse 200 with single failure cancels all siblings`() = runTest {
        val started = java.util.concurrent.atomic.AtomicInteger(0)
        val comp = (1..200).toList().traverse { i ->
            Computation<String> {
                started.incrementAndGet()
                delay(50)
                if (i == 1) error("first fails fast")
                "item-$i"
            }
        }
        assertFailsWith<IllegalStateException> { val r = Async { comp } }
        // All 200 start (parallel), but cancellation propagates
        assertEquals(200, started.get(), "All 200 should start in parallel")
    }

    // ── sequence with large collections ─────────────────────────────────

    @Test
    fun `sequence 150 computations all complete`() = runTest {
        val computations = (1..150).map { i ->
            Computation { delay(40); "v$i" }
        }
        val results = Async { computations.sequence() }
        assertEquals(150, results.size)
        assertEquals("v1", results.first())
        assertEquals("v150", results.last())
        assertEquals(40, currentTime)
    }

    // ── validated traverse at scale ─────────────────────────────────────

    @Test
    fun `traverseV with 100 validators - all pass`() = runTest {
        val results = Async {
            (1..100).toList().traverseV { i ->
                Computation<Either<NonEmptyList<String>, String>> {
                    delay(20); Either.Right("valid-$i")
                }
            }
        }
        assertTrue(results is Either.Right)
        assertEquals(100, (results as Either.Right).value.size)
    }

    @Test
    fun `traverseV with 100 validators - half fail collects all errors`() = runTest {
        val results = Async {
            (1..100).toList().traverseV { i ->
                Computation<Either<NonEmptyList<String>, String>> {
                    delay(20)
                    if (i % 2 == 0) Either.Left("err-$i".toNonEmptyList())
                    else Either.Right("ok-$i")
                }
            }
        }
        assertTrue(results is Either.Left)
        val errors = (results as Either.Left).value
        assertEquals(50, errors.size, "50 even-numbered validators should fail")
        assertTrue(errors.contains("err-2"))
        assertTrue(errors.contains("err-100"))
    }

    // ── deeply nested flatMap chains ────────────────────────────────────

    @Test
    fun `flatMap chain depth 50 completes without stack overflow`() = runTest {
        var computation: Computation<Int> = pure(0)
        repeat(50) {
            computation = computation.flatMap { n -> pure(n + 1) }
        }
        val result = Async { computation }
        assertEquals(50, result)
    }

    @Test
    fun `flatMap chain depth 200 with defer completes without stack overflow`() = runTest {
        fun chain(depth: Int, current: Int): Computation<Int> =
            if (depth <= 0) pure(current)
            else Computation.defer { chain(depth - 1, current + 1) }

        val result = Async { chain(200, 0) }
        assertEquals(200, result)
    }

    // ── high-arity lift+ap ──────────────────────────────────────────────

    @Test
    fun `lift15 with ap runs all 15 in parallel`() = runTest {
        val result = Async {
            lift15 { a: Int, b: Int, c: Int, d: Int, e: Int,
                     f: Int, g: Int, h: Int, i: Int, j: Int,
                     k: Int, l: Int, m: Int, n: Int, o: Int ->
                a + b + c + d + e + f + g + h + i + j + k + l + m + n + o
            }
                .ap { delay(30); 1 }.ap { delay(30); 2 }.ap { delay(30); 3 }
                .ap { delay(30); 4 }.ap { delay(30); 5 }.ap { delay(30); 6 }
                .ap { delay(30); 7 }.ap { delay(30); 8 }.ap { delay(30); 9 }
                .ap { delay(30); 10 }.ap { delay(30); 11 }.ap { delay(30); 12 }
                .ap { delay(30); 13 }.ap { delay(30); 14 }.ap { delay(30); 15 }
        }
        assertEquals(120, result)
        assertEquals(30, currentTime, "All 15 should run in parallel → 30ms")
    }

    @Test
    fun `lift22 with ap runs all 22 in parallel`() = runTest {
        val result = Async {
            lift22 { a: Int, b: Int, c: Int, d: Int, e: Int,
                     f: Int, g: Int, h: Int, i: Int, j: Int,
                     k: Int, l: Int, m: Int, n: Int, o: Int,
                     p: Int, q: Int, r: Int, s: Int, t: Int,
                     u: Int, v: Int ->
                a + b + c + d + e + f + g + h + i + j +
                k + l + m + n + o + p + q + r + s + t + u + v
            }
                .ap { delay(30); 1 }.ap { delay(30); 2 }.ap { delay(30); 3 }
                .ap { delay(30); 4 }.ap { delay(30); 5 }.ap { delay(30); 6 }
                .ap { delay(30); 7 }.ap { delay(30); 8 }.ap { delay(30); 9 }
                .ap { delay(30); 10 }.ap { delay(30); 11 }.ap { delay(30); 12 }
                .ap { delay(30); 13 }.ap { delay(30); 14 }.ap { delay(30); 15 }
                .ap { delay(30); 16 }.ap { delay(30); 17 }.ap { delay(30); 18 }
                .ap { delay(30); 19 }.ap { delay(30); 20 }.ap { delay(30); 21 }
                .ap { delay(30); 22 }
        }
        assertEquals((1..22).sum(), result) // 253
        assertEquals(30, currentTime, "All 22 should run in parallel → 30ms")
    }

    // ── massive raceN ───────────────────────────────────────────────────

    @Test
    fun `raceN with 20 computations - fastest wins`() = runTest {
        val computations = (1..20).map { i ->
            Computation { delay(i.toLong() * 10); "winner-$i" }
        }
        val result = Async { raceN(*computations.toTypedArray()) }
        assertEquals("winner-1", result, "Fastest (10ms) should win")
        assertEquals(10, currentTime)
    }

    @Test
    fun `raceN with 20 computations - 19 fail, 1 succeeds`() = runTest {
        val computations = (1..20).map { i ->
            Computation {
                delay(i.toLong() * 5)
                if (i < 20) error("fail-$i")
                "survivor"
            }
        }
        val result = Async { raceN(*computations.toTypedArray()) }
        assertEquals("survivor", result)
    }

    // ── memoize under concurrent load ───────────────────────────────────

    @Test
    fun `memoize with 50 concurrent consumers executes only once`() = runTest {
        val executions = java.util.concurrent.atomic.AtomicInteger(0)
        val memoized = Computation {
            executions.incrementAndGet()
            delay(50)
            "computed"
        }.memoize()

        val results = Async {
            (1..50).toList().traverse { Computation { memoized.await() } }
        }
        assertEquals(50, results.size)
        assertTrue(results.all { it == "computed" })
        assertEquals(1, executions.get(), "Should execute exactly once despite 50 consumers")
    }

    // ── orElse chain depth ──────────────────────────────────────────────

    @Test
    fun `orElse chain of 10 - last one succeeds`() = runTest {
        var chain: Computation<String> = Computation { error("fail-1") }
        for (i in 2..9) {
            chain = chain.orElse(Computation { error("fail-$i") })
        }
        chain = chain.orElse(Computation { "success-10" })
        val result = Async { chain }
        assertEquals("success-10", result)
    }

    @Test
    fun `firstSuccessOf with 10 computations - 3rd succeeds`() = runTest {
        val computations = (1..10).map { i ->
            Computation<String> {
                if (i < 3) error("fail-$i")
                "success-$i"
            }
        }
        val result = Async { firstSuccessOf(*computations.toTypedArray()) }
        assertEquals("success-3", result)
    }

    // ── resilience stack composition under load ─────────────────────────

    @Test
    fun `retry + timeout + recover composition in parallel branches`() = runTest {
        val result = Async {
            liftA3(
                {
                    // Branch 1: flaky, succeeds on 2nd try
                    var attempts = 0
                    Computation {
                        attempts++
                        if (attempts < 2) error("flaky")
                        "stable"
                    }.retry(3).await()
                },
                {
                    // Branch 2: slow, falls back to cached
                    Computation { delay(500); "slow" }
                        .timeout(kotlin.time.Duration.parse("100ms"), default = "cached")
                        .await()
                },
                {
                    // Branch 3: always fails, recovered
                    Computation<String> { error("down") }
                        .recover { "fallback" }
                        .await()
                },
            ) { a, b, c -> "$a|$b|$c" }
        }
        assertEquals("stable|cached|fallback", result)
    }

    // ── cancellation propagation through deep chains ────────────────────

    @Test
    fun `cancellation through flatMap+ap chain does not leak`() = runTest {
        val released = java.util.concurrent.atomic.AtomicInteger(0)
        val comp = bracket<String, String>(
            acquire = { "resource" },
            use = { _ ->
                liftA3(
                    { delay(50); "A" },
                    { delay(50); "B" },
                    { delay(50); "C" },
                ) { a, b, c -> "$a|$b|$c" }
                .flatMap { Computation<String> { throw CancellationException("cancelled") } }
            },
            release = { _ -> released.incrementAndGet(); Unit },
        )
        assertFailsWith<CancellationException> { val r = Async { comp } }
        assertEquals(1, released.get(), "Resource must be released even through cancellation")
    }
}
