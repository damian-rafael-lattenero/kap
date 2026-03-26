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
                Effect { delay(50); "item-$i" }
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
                Effect { delay(30); i }
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
            Effect<String> {
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
            Effect { delay(40); "v$i" }
        }
        val results = Async { computations.sequence() }
        assertEquals(150, results.size)
        assertEquals("v1", results.first())
        assertEquals("v150", results.last())
        assertEquals(40, currentTime)
    }

    // ── deeply nested andThen chains ────────────────────────────────────

    @Test
    fun `andThen chain depth 50 completes without stack overflow`() = runTest {
        var computation: Effect<Int> = Effect.of(0)
        repeat(50) {
            computation = computation.andThen { n -> Effect.of(n + 1) }
        }
        val result = Async { computation }
        assertEquals(50, result)
    }

    @Test
    fun `andThen chain depth 200 with defer completes without stack overflow`() = runTest {
        fun chain(depth: Int, current: Int): Effect<Int> =
            if (depth <= 0) Effect.of(current)
            else Effect.defer { chain(depth - 1, current + 1) }

        val result = Async { chain(200, 0) }
        assertEquals(200, result)
    }

    // ── high-arity kap+with ──────────────────────────────────────────────

    @Test
    fun `kap with with (15 params) runs all 15 in parallel`() = runTest {
        val result = Async {
            kap { a: Int, b: Int, c: Int, d: Int, e: Int,
                     f: Int, g: Int, h: Int, i: Int, j: Int,
                     k: Int, l: Int, m: Int, n: Int, o: Int ->
                a + b + c + d + e + f + g + h + i + j + k + l + m + n + o
            }
                .with { delay(30); 1 }.with { delay(30); 2 }.with { delay(30); 3 }
                .with { delay(30); 4 }.with { delay(30); 5 }.with { delay(30); 6 }
                .with { delay(30); 7 }.with { delay(30); 8 }.with { delay(30); 9 }
                .with { delay(30); 10 }.with { delay(30); 11 }.with { delay(30); 12 }
                .with { delay(30); 13 }.with { delay(30); 14 }.with { delay(30); 15 }
        }
        assertEquals(120, result)
        assertEquals(30, currentTime, "All 15 should run in parallel → 30ms")
    }

    @Test
    fun `kap with with (22 params) runs all 22 in parallel`() = runTest {
        val result = Async {
            kap { a: Int, b: Int, c: Int, d: Int, e: Int,
                     f: Int, g: Int, h: Int, i: Int, j: Int,
                     k: Int, l: Int, m: Int, n: Int, o: Int,
                     p: Int, q: Int, r: Int, s: Int, t: Int,
                     u: Int, v: Int ->
                a + b + c + d + e + f + g + h + i + j +
                k + l + m + n + o + p + q + r + s + t + u + v
            }
                .with { delay(30); 1 }.with { delay(30); 2 }.with { delay(30); 3 }
                .with { delay(30); 4 }.with { delay(30); 5 }.with { delay(30); 6 }
                .with { delay(30); 7 }.with { delay(30); 8 }.with { delay(30); 9 }
                .with { delay(30); 10 }.with { delay(30); 11 }.with { delay(30); 12 }
                .with { delay(30); 13 }.with { delay(30); 14 }.with { delay(30); 15 }
                .with { delay(30); 16 }.with { delay(30); 17 }.with { delay(30); 18 }
                .with { delay(30); 19 }.with { delay(30); 20 }.with { delay(30); 21 }
                .with { delay(30); 22 }
        }
        assertEquals((1..22).sum(), result) // 253
        assertEquals(30, currentTime, "All 22 should run in parallel → 30ms")
    }

    // ── massive raceN ───────────────────────────────────────────────────

    @Test
    fun `raceN with 20 computations - fastest wins`() = runTest {
        val computations = (1..20).map { i ->
            Effect { delay(i.toLong() * 10); "winner-$i" }
        }
        val result = Async { raceN(*computations.toTypedArray()) }
        assertEquals("winner-1", result, "Fastest (10ms) should win")
        assertEquals(10, currentTime)
    }

    @Test
    fun `raceN with 20 computations - 19 fail, 1 succeeds`() = runTest {
        val computations = (1..20).map { i ->
            Effect {
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
        val memoized = Effect {
            executions.incrementAndGet()
            delay(50)
            "computed"
        }.memoize()

        val results = Async {
            (1..50).toList().traverse { Effect { memoized.await() } }
        }
        assertEquals(50, results.size)
        assertTrue(results.all { it == "computed" })
        assertEquals(1, executions.get(), "Should execute exactly once despite 50 consumers")
    }

    // ── orElse chain depth ──────────────────────────────────────────────

    @Test
    fun `orElse chain of 10 - last one succeeds`() = runTest {
        var chain: Effect<String> = Effect { error("fail-1") }
        for (i in 2..9) {
            chain = chain.orElse(Effect { error("fail-$i") })
        }
        chain = chain.orElse(Effect { "success-10" })
        val result = Async { chain }
        assertEquals("success-10", result)
    }

    @Test
    fun `firstSuccessOf with 10 computations - 3rd succeeds`() = runTest {
        val computations = (1..10).map { i ->
            Effect<String> {
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
            combine(
                {
                    // Branch 1: flaky, succeeds on 2nd try
                    var attempts = 0
                    Effect {
                        attempts++
                        if (attempts < 2) error("flaky")
                        "stable"
                    }.retry(3).await()
                },
                {
                    // Branch 2: slow, falls back to cached
                    Effect { delay(500); "slow" }
                        .timeout(kotlin.time.Duration.parse("100ms"), default = "cached")
                        .await()
                },
                {
                    // Branch 3: always fails, recovered
                    Effect<String> { error("down") }
                        .recover { "fallback" }
                        .await()
                },
            ) { a, b, c -> "$a|$b|$c" }
        }
        assertEquals("stable|cached|fallback", result)
    }

}
