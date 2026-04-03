package kap

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.withContext

class OverloadsAndEdgeCasesTest {

    // ════════════════════════════════════════════════════════════════════════
    // Async.kt gaps
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `sequence with bounded concurrency respects limit`() = runTest {
        var concurrent = 0
        var maxConcurrent = 0

        val computations = (0 until 8).map { i ->
            Kap {
                concurrent++
                if (concurrent > maxConcurrent) maxConcurrent = concurrent
                delay(50)
                concurrent--
                "v$i"
            }
        }

        val result = computations.sequence(3).executeGraph()

        assertEquals((0 until 8).map { "v$it" }, result)
        assertTrue(maxConcurrent <= 3, "Max concurrent was $maxConcurrent, expected <= 3")
    }

    @Test
    fun `Async with context overload runs on specified context`() = runTest {
        val result = withContext(CoroutineName("test-ctx")) {
            context.map { it[CoroutineName]?.name ?: "unknown" }.executeGraph()
        }
        assertEquals("test-ctx", result)
    }

    @Test
    fun `ap with Kap overload works the same as suspend lambda`() = runTest {
        val comp = Kap { "from-computation" }

        val result = Kap.of { a: String -> { b: String -> "$a|$b" } }
                .with(comp)
                .with(Kap { "also-computation" }).executeGraph()

        assertEquals("from-computation|also-computation", result)
    }

    @Test
    fun `then with Kap overload`() = runTest {
        val comp = Kap { "barrier" }

        val result = Kap.of { a: String -> { b: String -> "$a|$b" } }
                .then(comp)
                .with { "after" }.executeGraph()

        assertEquals("barrier|after", result)
    }

    // ════════════════════════════════════════════════════════════════════════
    // Combinators.kt gaps
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `timeout with fallback Kap runs fallback on timeout`() = runTest {
        val fallback = Kap { "fallback-value" }

        val result = Kap<String> { delay(10.seconds); "too-slow" }
                .timeout(50.milliseconds, fallback).executeGraph()

        assertEquals("fallback-value", result)
    }

    @Test
    fun `timeout with fallback Kap returns original on success`() = runTest {
        val fallback = Kap { "fallback-value" }

        val result = Kap.of("fast").timeout(1.seconds, fallback).executeGraph()

        assertEquals("fast", result)
    }

    @Test
    fun `recoverWith switches to recovery computation`() = runTest {
        val result = Kap<String> { throw RuntimeException("boom") }
                .recoverWith { e -> Kap.of("recovered: ${e.message}") }.executeGraph()

        assertEquals("recovered: boom", result)
    }

    @Test
    fun `recoverWith passes through on success`() = runTest {
        val result = Kap.of("ok").recoverWith { Kap.of("should-not-reach") }.executeGraph()

        assertEquals("ok", result)
    }

    @Test
    fun `exponential val doubles the duration`() {
        val backoff: (Duration) -> Duration = exponential
        val d = 100.milliseconds
        assertEquals(200.milliseconds, backoff(d))
        assertEquals(400.milliseconds, backoff(backoff(d)))
    }

    @Test
    fun `exponential with max caps at maximum`() {
        val capped = exponential(max = 500.milliseconds)
        assertEquals(200.milliseconds, capped(100.milliseconds))
        assertEquals(500.milliseconds, capped(300.milliseconds))
        assertEquals(500.milliseconds, capped(500.milliseconds))
    }

    @Test
    fun `retry with exponential backoff`() = runTest {
        var attempts = 0

        val result = Kap {
                attempts++
                if (attempts < 3) throw RuntimeException("fail #$attempts")
                "success on attempt $attempts"
            }.retry(maxAttempts = 5, delay = 10.milliseconds, backoff = exponential).executeGraph()

        assertEquals("success on attempt 3", result)
        assertEquals(3, attempts)
    }

    // ════════════════════════════════════════════════════════════════════════
    // Race.kt gaps — both fail scenario
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `race returns winner even if loser fails`() = runTest {
        val result = race(
                Kap { "fast-winner" },
                Kap<String> { delay(100); throw RuntimeException("loser-error") },
            ).executeGraph()
        assertEquals("fast-winner", result)
    }

    @Test
    fun `raceN returns winner even if other fails`() = runTest {
        val result = raceN(
                Kap { "winner" },
                Kap<String> { delay(100); throw RuntimeException("boom") },
                Kap<String> { delay(100); throw RuntimeException("boom2") },
            ).executeGraph()
        assertEquals("winner", result)
    }

    // ════════════════════════════════════════════════════════════════════════
    // on combinator — per-computation context switch
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `on switches dispatcher for a specific computation`() = runTest {
        val result = Kap.of { a: String -> { b: String -> "$a|$b" } }
                .with(Kap { "io-task" }.on(Dispatchers.IO))
                .with { "default-task" }.executeGraph()
        assertEquals("io-task|default-task", result)
    }
}
