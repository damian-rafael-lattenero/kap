package applicative

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class OverloadsAndEdgeCasesTest {

    // ════════════════════════════════════════════════════════════════════════
    // Async.kt gaps
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `sequence with bounded concurrency respects limit`() = runTest {
        var concurrent = 0
        var maxConcurrent = 0

        val computations = (0 until 8).map { i ->
            Computation {
                concurrent++
                if (concurrent > maxConcurrent) maxConcurrent = concurrent
                delay(50)
                concurrent--
                "v$i"
            }
        }

        val result = Async { computations.sequence(3) }

        assertEquals((0 until 8).map { "v$it" }, result)
        assertTrue(maxConcurrent <= 3, "Max concurrent was $maxConcurrent, expected <= 3")
    }

    @Test
    fun `Async with context overload runs on specified context`() = runTest {
        val result = Async(CoroutineName("test-ctx")) {
            context.map { it[CoroutineName]?.name ?: "unknown" }
        }
        assertEquals("test-ctx", result)
    }

    @Test
    fun `ap with Computation overload works the same as suspend lambda`() = runTest {
        val comp = Computation { "from-computation" }

        val result = Async {
            lift2 { a: String, b: String -> "$a|$b" }
                .ap(comp)
                .ap(Computation { "also-computation" })
        }

        assertEquals("from-computation|also-computation", result)
    }

    @Test
    fun `followedBy with Computation overload`() = runTest {
        val comp = Computation { "barrier" }

        val result = Async {
            lift2 { a: String, b: String -> "$a|$b" }
                .followedBy(comp)
                .ap { "after" }
        }

        assertEquals("barrier|after", result)
    }

    // ════════════════════════════════════════════════════════════════════════
    // Combinators.kt gaps
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `timeout with fallback Computation runs fallback on timeout`() = runTest {
        val fallback = Computation { "fallback-value" }

        val result = Async {
            Computation<String> { delay(10.seconds); "too-slow" }
                .timeout(50.milliseconds, fallback)
        }

        assertEquals("fallback-value", result)
    }

    @Test
    fun `timeout with fallback Computation returns original on success`() = runTest {
        val fallback = Computation { "fallback-value" }

        val result = Async {
            pure("fast").timeout(1.seconds, fallback)
        }

        assertEquals("fast", result)
    }

    @Test
    fun `recoverWith switches to recovery computation`() = runTest {
        val result = Async {
            Computation<String> { throw RuntimeException("boom") }
                .recoverWith { e -> pure("recovered: ${e.message}") }
        }

        assertEquals("recovered: boom", result)
    }

    @Test
    fun `recoverWith passes through on success`() = runTest {
        val result = Async {
            pure("ok").recoverWith { pure("should-not-reach") }
        }

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

        val result = Async {
            Computation {
                attempts++
                if (attempts < 3) throw RuntimeException("fail #$attempts")
                "success on attempt $attempts"
            }.retry(maxAttempts = 5, delay = 10.milliseconds, backoff = exponential)
        }

        assertEquals("success on attempt 3", result)
        assertEquals(3, attempts)
    }

    // ════════════════════════════════════════════════════════════════════════
    // Either.kt gaps
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `right standalone constructor`() {
        val r = right(42)
        assertIs<Either.Right<Int>>(r)
        assertEquals(42, r.value)
    }

    @Test
    fun `left standalone constructor`() {
        val l = left("error")
        assertIs<Either.Left<String>>(l)
        assertEquals("error", l.value)
    }

    @Test
    fun `mapLeft transforms Left value`() {
        val l: Either<String, Int> = Either.Left("error")
        val result = l.mapLeft { it.length }
        assertEquals(Either.Left(5), result)
    }

    @Test
    fun `mapLeft is identity on Right`() {
        val r: Either<String, Int> = Either.Right(42)
        val result = r.mapLeft { it.length }
        assertEquals(Either.Right(42), result)
    }

    // ════════════════════════════════════════════════════════════════════════
    // Validated.kt gaps
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `invalidAll wraps Nel of errors`() = runTest {
        val errors = Nel.of("err1", "err2", "err3")
        val result = Async { invalidAll<String, Int>(errors) }
        assertIs<Either.Left<Nel<String>>>(result)
        assertEquals(listOf("err1", "err2", "err3"), result.value.toList())
    }

    @Test
    fun `apV with suspend lambda convenience overload`() = runTest {
        val result = Async {
            liftV2<String, String, String, String> { a, b -> "$a|$b" }
                .apV { Either.Right("A") }
                .apV { Either.Right("B") }
        }
        assertEquals(Either.Right("A|B"), result)
    }

    @Test
    fun `followedByV with suspend lambda convenience overload`() = runTest {
        val order = mutableListOf<String>()

        val result = Async {
            liftV2<String, String, String, String> { a, b -> "$a|$b" }
                .apV { order.add("first"); Either.Right("A") as Either<Nel<String>, String> }
                .followedByV { order.add("second"); Either.Right("B") as Either<Nel<String>, String> }
        }

        assertEquals(Either.Right("A|B"), result)
        assertEquals(listOf("first", "second"), order)
    }

    @Test
    fun `mapV transforms success side`() = runTest {
        val result = Async {
            valid<String, Int>(42).mapV { it * 2 }
        }
        assertEquals(Either.Right(84), result)
    }

    @Test
    fun `mapV is identity on Left`() = runTest {
        val result = Async {
            invalid<String, Int>("err").mapV { it * 2 }
        }
        assertIs<Either.Left<Nel<String>>>(result)
        assertEquals(listOf("err"), result.value.toList())
    }

    @Test
    fun `errors extension property on Left`() {
        val left = Either.Left(Nel.of("e1", "e2"))
        assertEquals(listOf("e1", "e2"), left.errors.toList())
    }

    @Test
    fun `traverseV with bounded concurrency respects limit`() = runTest {
        var concurrent = 0
        var maxConcurrent = 0

        val result = Async {
            (0 until 8).toList().traverseV<String, Int, String>(3) { i ->
                Computation {
                    concurrent++
                    if (concurrent > maxConcurrent) maxConcurrent = concurrent
                    delay(50)
                    concurrent--
                    Either.Right("v$i")
                }
            }
        }

        assertEquals(Either.Right((0 until 8).map { "v$it" }), result)
        assertTrue(maxConcurrent <= 3, "Max concurrent was $maxConcurrent, expected <= 3")
    }

    @Test
    fun `traverseV with bounded concurrency accumulates errors`() = runTest {
        val result = Async {
            listOf(1, -2, 3, -4).traverseV<String, Int, String>(2) { n ->
                if (n > 0) valid(n.toString()) else invalid("neg:$n")
            }
        }

        assertIs<Either.Left<Nel<String>>>(result)
        assertEquals(listOf("neg:-2", "neg:-4"), result.value.toList())
    }

    @Test
    fun `sequenceV with bounded concurrency respects limit`() = runTest {
        var concurrent = 0
        var maxConcurrent = 0

        val computations = (0 until 6).map { i ->
            Computation<Either<Nel<String>, String>> {
                concurrent++
                if (concurrent > maxConcurrent) maxConcurrent = concurrent
                delay(50)
                concurrent--
                Either.Right("v$i")
            }
        }

        val result = Async { computations.sequenceV(2) }

        assertEquals(Either.Right((0 until 6).map { "v$it" }), result)
        assertTrue(maxConcurrent <= 2, "Max concurrent was $maxConcurrent, expected <= 2")
    }

    // ════════════════════════════════════════════════════════════════════════
    // Race.kt gaps — both fail scenario
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `race returns winner even if loser fails`() = runTest {
        val result = Async {
            race(
                Computation { "fast-winner" },
                Computation<String> { delay(100); throw RuntimeException("loser-error") },
            )
        }
        assertEquals("fast-winner", result)
    }

    @Test
    fun `raceN returns winner even if other fails`() = runTest {
        val result = Async {
            raceN(
                Computation { "winner" },
                Computation<String> { delay(100); throw RuntimeException("boom") },
                Computation<String> { delay(100); throw RuntimeException("boom2") },
            )
        }
        assertEquals("winner", result)
    }

    // ════════════════════════════════════════════════════════════════════════
    // on combinator — per-computation context switch
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `on switches dispatcher for a specific computation`() = runTest {
        val result = Async {
            lift2 { a: String, b: String -> "$a|$b" }
                .ap(Computation { "io-task" }.on(Dispatchers.IO))
                .ap { "default-task" }
        }
        assertEquals("io-task|default-task", result)
    }
}
