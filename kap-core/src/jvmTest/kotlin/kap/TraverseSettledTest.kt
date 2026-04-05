package kap

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class TraverseSettledTest {

    // ════════════════════════════════════════════════════════════════════════
    // traverseSettled — unbounded
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `traverseSettled collects ALL results including failures`() = runTest {
        val results = listOf(1, 2, 3, 4, 5).traverseSettled { i ->
                Kap {
                    if (i % 2 == 0) throw RuntimeException("fail-$i")
                    "ok-$i"
                }
            }.evalGraph()

        assertEquals(5, results.size)
        assertTrue(results[0].isSuccess)
        assertEquals("ok-1", results[0].getOrThrow())
        assertTrue(results[1].isFailure)
        assertEquals("fail-2", results[1].exceptionOrNull()!!.message)
        assertTrue(results[2].isSuccess)
        assertEquals("ok-3", results[2].getOrThrow())
        assertTrue(results[3].isFailure)
        assertEquals("fail-4", results[3].exceptionOrNull()!!.message)
        assertTrue(results[4].isSuccess)
        assertEquals("ok-5", results[4].getOrThrow())
    }

    @Test
    fun `traverseSettled runs in parallel — proven by virtual time`() = runTest {
        val results = (1..5).toList().traverseSettled { i ->
                Kap {
                    delay(50.milliseconds)
                    "done-$i"
                }
            }.evalGraph()

        assertEquals(50L, currentTime, "5 parallel tasks @ 50ms should complete in 50ms")
        assertTrue(results.all { it.isSuccess })
    }

    @Test
    fun `traverseSettled does NOT cancel siblings on failure`() = runTest {
        val completed = mutableListOf<Int>()

        val results = (1..5).toList().traverseSettled { i ->
                Kap {
                    delay(if (i == 1) 10.milliseconds else 50.milliseconds)
                    if (i == 1) throw RuntimeException("fast-fail")
                    synchronized(completed) { completed.add(i) }
                    "ok-$i"
                }
            }.evalGraph()

        assertEquals(5, results.size)
        assertTrue(results[0].isFailure, "first should fail")
        assertEquals(4, results.count { it.isSuccess }, "all others should succeed")
        // All non-failing computations should have completed
        assertEquals(listOf(2, 3, 4, 5), completed.sorted())
    }

    @Test
    fun `traverseSettled with all success returns all Right`() = runTest {
        val results = listOf("a", "b", "c").traverseSettled { s ->
                Kap { s.uppercase() }
            }.evalGraph()

        assertEquals(
            listOf("A", "B", "C"),
            results.map { it.getOrThrow() },
        )
    }

    @Test
    fun `traverseSettled with all failures returns all failures`() = runTest {
        val results = listOf(1, 2, 3).traverseSettled { i ->
                Kap<String> { throw RuntimeException("err-$i") }
            }.evalGraph()

        assertTrue(results.all { it.isFailure })
        assertEquals(
            listOf("err-1", "err-2", "err-3"),
            results.map { it.exceptionOrNull()!!.message },
        )
    }

    // ════════════════════════════════════════════════════════════════════════
    // traverseSettled — bounded concurrency
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `traverseSettled bounded respects concurrency limit`() = runTest {
        val results = (1..9).toList().traverseSettled(3) { i ->
                Kap {
                    delay(30.milliseconds)
                    "ok-$i"
                }
            }.evalGraph()

        // 9 items / 3 concurrency = 3 batches × 30ms = 90ms
        assertEquals(90L, currentTime, "bounded traverseSettled should batch correctly")
        assertTrue(results.all { it.isSuccess })
    }

    @Test
    fun `traverseSettled bounded collects failures without cancelling`() = runTest {
        val results = (1..6).toList().traverseSettled(2) { i ->
                Kap {
                    delay(30.milliseconds)
                    if (i % 3 == 0) throw RuntimeException("fail-$i")
                    "ok-$i"
                }
            }.evalGraph()

        assertEquals(6, results.size)
        assertEquals(4, results.count { it.isSuccess })
        assertEquals(2, results.count { it.isFailure })
    }

    // ════════════════════════════════════════════════════════════════════════
    // sequenceSettled
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `sequenceSettled collects all results from pre-built computations`() = runTest {
        val computations = listOf(
            Kap { "a" },
            Kap<String> { throw RuntimeException("boom") },
            Kap { "c" },
        )

        val results = computations.sequenceSettled().evalGraph()

        assertEquals(3, results.size)
        assertTrue(results[0].isSuccess)
        assertTrue(results[1].isFailure)
        assertTrue(results[2].isSuccess)
    }

    @Test
    fun `sequenceSettled bounded respects concurrency`() = runTest {
        val computations = (1..8).map { i ->
            Kap {
                delay(25.milliseconds)
                "ok-$i"
            }
        }

        val results = computations.sequenceSettled(4).evalGraph()

        // 8 items / 4 concurrency = 2 batches × 25ms = 50ms
        assertEquals(50L, currentTime)
        assertTrue(results.all { it.isSuccess })
    }

    // ════════════════════════════════════════════════════════════════════════
    // settled() — computation extension
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `settled wraps success in Result`() = runTest {
        val result = Kap { 42 }.settled().evalGraph()

        assertTrue(result.isSuccess)
        assertEquals(42, result.getOrThrow())
    }

    @Test
    fun `settled wraps failure in Result without cancelling siblings`() = runTest {
        data class Dashboard(val user: Result<String>, val cart: String, val config: String)

        val result = Kap.of { user: Result<String> -> { cart: String -> { config: String -> Dashboard(user, cart, config) } } }
                .with { Kap<String> { throw RuntimeException("user-down") }.settled().evalGraph() }
                .with { delay(50.milliseconds); "cart-ok" }
                .with { delay(50.milliseconds); "config-ok" }.evalGraph()

        assertTrue(result.user.isFailure)
        assertEquals("user-down", result.user.exceptionOrNull()!!.message)
        assertEquals("cart-ok", result.cart)
        assertEquals("config-ok", result.config)
    }

    @Test
    fun `settled inside with chain — proven parallel by virtual time`() = runTest {
        data class R(val a: Result<String>, val b: String)

        val result = Kap.of { a: Result<String> -> { b: String -> R(a, b) } }
                .with {
                    delay(50.milliseconds)
                    Kap<String> { throw RuntimeException("err") }.settled().evalGraph()
                }
                .with { delay(50.milliseconds); "ok" }.evalGraph()

        assertEquals(50L, currentTime, "both branches should run in parallel")
        assertTrue(result.a.isFailure)
        assertEquals("ok", result.b)
    }
}
