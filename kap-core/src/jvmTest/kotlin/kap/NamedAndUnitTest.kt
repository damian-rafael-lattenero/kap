package kap

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.coroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class NamedAndUnitTest {

    // ════════════════════════════════════════════════════════════════════════
    // Kap.empty
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `Kap empty returns Unit`() = runTest {
        val result = Kap.empty.executeGraph()
        assertEquals(Unit, result)
    }

    @Test
    fun `Kap empty works as then barrier value`() = runTest {
        // Kap.empty is just Kap.of(Unit), usable as a barrier value in phase chains
        val result = kap { a: String, _: Unit, b: Int -> "$a=$b" }
                .with { delay(30); "hello" }
                .then(Kap.empty)
                .with { delay(30); 42 }.executeGraph()
        assertEquals("hello=42", result)
        // phase 1: 30ms, barrier: 0ms (Kap.empty is instant), phase 2: 30ms
        assertEquals(60, currentTime)
    }

    // ════════════════════════════════════════════════════════════════════════
    // named
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `named sets CoroutineName in coroutineContext`() = runTest {
        val result = Kap {
                coroutineContext[CoroutineName]?.name ?: "missing"
            }.named("my-computation").executeGraph()
        assertEquals("my-computation", result)
    }

    @Test
    fun `named composes with with - each branch has its own name`() = runTest {
        val result = kap { a: String, b: String, c: String -> listOf(a, b, c) }
                .with(Kap<String> {
                    coroutineContext[CoroutineName]?.name ?: "missing"
                }.named("branch-a"))
                .with(Kap<String> {
                    coroutineContext[CoroutineName]?.name ?: "missing"
                }.named("branch-b"))
                .with(Kap<String> {
                    coroutineContext[CoroutineName]?.name ?: "missing"
                }.named("branch-c")).executeGraph()
        assertEquals(listOf("branch-a", "branch-b", "branch-c"), result)
    }

    @Test
    fun `named does not affect computation result`() = runTest {
        val result = Kap { delay(30); 42 }.named("answer").executeGraph()
        assertEquals(42, result)
        assertEquals(30, currentTime)
    }

    @Test
    fun `named composes with traced`() = runTest {
        val events = mutableListOf<TraceEvent>()
        val tracer = KapTracer { events += it }

        val result = Kap.of(42).named("x").traced("x", tracer).executeGraph()

        assertEquals(42, result)
        assertEquals(2, events.size)
        assertIs<TraceEvent.Started>(events[0])
        assertEquals("x", events[0].name)
        assertIs<TraceEvent.Succeeded>(events[1])
        assertEquals("x", events[1].name)
    }

    // ════════════════════════════════════════════════════════════════════════
    // catching (top-level, from Interop.kt)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `catching returns Result success on success`() = runTest {
        val result: Result<Int> = catching { 42 }.executeGraph()
        assertTrue(result.isSuccess)
        assertEquals(42, result.getOrNull())
    }

    @Test
    fun `catching returns Result failure on exception`() = runTest {
        val result: Result<String> = catching<String> { throw IllegalStateException("boom") }.executeGraph()
        assertTrue(result.isFailure)
        assertIs<IllegalStateException>(result.exceptionOrNull())
        assertEquals("boom", result.exceptionOrNull()?.message)
    }

    @Test
    fun `catching does not catch CancellationException`() = runTest {
        val result = runCatching {
            catching<String> { throw CancellationException("cancelled") }.executeGraph()
        }
        assertTrue(result.isFailure)
        assertIs<CancellationException>(result.exceptionOrNull())
    }

    @Test
    fun `catching composes with with branches in parallel`() = runTest {
        val result = kap { a: Result<Int>, b: Result<String> -> a to b }
                .with(catching<Int> { delay(30); 42 })
                .with(catching<String> { delay(30); "hello" }).executeGraph()
        assertTrue(result.first.isSuccess)
        assertEquals(42, result.first.getOrNull())
        assertTrue(result.second.isSuccess)
        assertEquals("hello", result.second.getOrNull())
        assertEquals(30, currentTime, "Both catching branches run in parallel")
    }
}
