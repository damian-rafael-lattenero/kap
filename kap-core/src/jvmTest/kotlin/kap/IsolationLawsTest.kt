package kap

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * The Isolation law (SPEC-4.1.0 F-01): a CONVERT or RETRY operator applied
 * to a graph with PARALLEL children observes the first child failure as an
 * ordinary failure; the operator's own coroutine is not cancelled; siblings
 * outside the operator are unaffected.
 *
 * Before 4.1.0 every operator here leaked the exception, because `traverse`
 * and `with` launch their children on the caller's scope and structured
 * concurrency cancelled the operator before it could convert.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class IsolationLawsTest {

    private class ChildFailed(n: Int) : RuntimeException("child $n failed")

    /** A graph whose SECOND parallel child fails. */
    private fun nested(): Kap<List<Int>> = listOf(1, 2, 3).traverse { n ->
        Kap { if (n == 2) throw ChildFailed(n) else n }
    }

    private fun nestedWith(): Kap<Pair<Int, Int>> =
        Kap.of { a: Int -> { b: Int -> a to b } }
            .with(Kap { 1 })
            .with(Kap<Int> { throw ChildFailed(2) })

    @Test
    fun `settled converts a nested parallel failure`() = runTest {
        val r = nested().settled().evalGraph()
        assertTrue(r.isFailure)
        assertIs<ChildFailed>(r.exceptionOrNull())
        assertTrue(nestedWith().settled().evalGraph().isFailure)
    }

    @Test
    fun `traverseSettled bounded and unbounded convert nested parallel failures`() = runTest {
        val unbounded = listOf("a", "b").traverseSettled { if (it == "a") nested() else Kap { listOf(0) } }.evalGraph()
        assertTrue(unbounded[0].isFailure)
        assertEquals(listOf(0), unbounded[1].getOrThrow())
        val bounded = listOf("a", "b").traverseSettled(1) { if (it == "a") nested() else Kap { listOf(0) } }.evalGraph()
        assertTrue(bounded[0].isFailure)
        assertEquals(listOf(0), bounded[1].getOrThrow())
    }

    @Test
    fun `recover and recoverWith handle a nested parallel failure`() = runTest {
        assertEquals(listOf(-1), nested().recover { listOf(-1) }.evalGraph())
        assertEquals(listOf(-2), nested().recoverWith { Kap.of(listOf(-2)) }.evalGraph())
    }

    @Test
    fun `orElse and firstSuccessOf fall through a nested parallel failure`() = runTest {
        assertEquals(listOf(9), (nested() orElse Kap.of(listOf(9))).evalGraph())
        assertEquals(listOf(8), firstSuccessOf(nested(), nested(), Kap.of(listOf(8))).evalGraph())
    }

    @Test
    fun `catching converts a nested parallel failure raised inside the block`() = runTest {
        val r = catching { with(nested()) { execute() } }.evalGraph()
        assertTrue(r.isFailure)
        assertIs<ChildFailed>(r.exceptionOrNull())
    }

    @Test
    fun `core retry retries a nested parallel failure and surfaces the ORIGINAL error when exhausted`() = runTest {
        var attempts = 0
        val flaky = listOf(1, 2).traverse { n ->
            Kap { if (n == 2 && attempts++ < 2) throw ChildFailed(n) else n }
        }
        assertEquals(listOf(1, 2), flaky.retry(maxAttempts = 3).evalGraph())
        assertEquals(3, attempts)
        assertFailsWith<ChildFailed> { nested().retry(maxAttempts = 2).evalGraph() }
    }

    @Test
    fun `siblings outside the converting operator are unaffected and still run in parallel`() = runTest {
        val start = currentTime
        val result = Kap.of { a: Result<List<Int>> -> { b: Int -> a to b } }
            .with(nested().settled())
            .with(Kap { delay(50); 42 })
            .evalGraph()
        assertTrue(result.first.isFailure)
        assertEquals(42, result.second)
        assertEquals(50, currentTime - start)
    }

    @Test
    fun `isolated passes success through and is a no-op boundary for leaves`() = runTest {
        assertEquals(listOf(1, 2), listOf(1, 2).traverse { Kap.of(it) }.isolated().evalGraph())
        assertEquals(7, Kap.of(7).isolated().evalGraph())
    }

    @Test
    fun `timeoutAsFailure is an ordinary failure, so retry retries it`() = runTest {
        var calls = 0
        val slowOnce = Kap { if (calls++ == 0) delay(10.minutes); "ok" }
        assertEquals("ok", slowOnce.timeoutAsFailure(1.seconds).retry(maxAttempts = 2).evalGraph())
        assertEquals(2, calls)
        val always = Kap { delay(10.minutes); "never" }
        val e = assertFailsWith<KapTimeoutException> { always.timeoutAsFailure(1.seconds).retry(maxAttempts = 2).evalGraph() }
        assertEquals(1.seconds, e.duration)
    }

    @Test
    fun `plain timeout before retry is NOT retried, as documented`() = runTest {
        var calls = 0
        val slow = Kap { calls++; delay(10.minutes); "never" }
        assertFailsWith<kotlinx.coroutines.TimeoutCancellationException> {
            slow.timeout(1.seconds).retry(maxAttempts = 3).evalGraph()
        }
        assertEquals(1, calls)
    }

    @Test
    fun `Kap of and empty are companion members`() = runTest {
        assertEquals(5, Kap.of(5).evalGraph())
        assertEquals(Unit, Kap.empty.evalGraph())
    }
}
