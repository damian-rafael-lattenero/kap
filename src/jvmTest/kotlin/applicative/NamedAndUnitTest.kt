package applicative

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
    // unit
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `unit returns Unit`() = runTest {
        val result = Async { unit }
        assertEquals(Unit, result)
    }

    @Test
    fun `unit works as followedBy barrier value`() = runTest {
        // unit is just pure(Unit), usable as a barrier value in phase chains
        val result = Async {
            lift3 { a: String, _: Unit, b: Int -> "$a=$b" }
                .ap { delay(30); "hello" }
                .followedBy(unit)
                .ap { delay(30); 42 }
        }
        assertEquals("hello=42", result)
        // phase 1: 30ms, barrier: 0ms (unit is instant), phase 2: 30ms
        assertEquals(60, currentTime)
    }

    // ════════════════════════════════════════════════════════════════════════
    // named
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `named sets CoroutineName in coroutineContext`() = runTest {
        val result = Async {
            Computation {
                coroutineContext[CoroutineName]?.name ?: "missing"
            }.named("my-computation")
        }
        assertEquals("my-computation", result)
    }

    @Test
    fun `named composes with ap - each branch has its own name`() = runTest {
        val result = Async {
            lift3 { a: String, b: String, c: String -> listOf(a, b, c) }
                .ap(Computation<String> {
                    coroutineContext[CoroutineName]?.name ?: "missing"
                }.named("branch-a"))
                .ap(Computation<String> {
                    coroutineContext[CoroutineName]?.name ?: "missing"
                }.named("branch-b"))
                .ap(Computation<String> {
                    coroutineContext[CoroutineName]?.name ?: "missing"
                }.named("branch-c"))
        }
        assertEquals(listOf("branch-a", "branch-b", "branch-c"), result)
    }

    @Test
    fun `named does not affect computation result`() = runTest {
        val result = Async {
            Computation { delay(30); 42 }.named("answer")
        }
        assertEquals(42, result)
        assertEquals(30, currentTime)
    }

    @Test
    fun `named composes with traced`() = runTest {
        val events = mutableListOf<TraceEvent>()
        val tracer = ComputationTracer { events += it }

        val result = Async {
            pure(42).named("x").traced("x", tracer)
        }

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
        val result: Result<Int> = Async {
            catching { 42 }
        }
        assertTrue(result.isSuccess)
        assertEquals(42, result.getOrNull())
    }

    @Test
    fun `catching returns Result failure on exception`() = runTest {
        val result: Result<String> = Async {
            catching<String> { throw IllegalStateException("boom") }
        }
        assertTrue(result.isFailure)
        assertIs<IllegalStateException>(result.exceptionOrNull())
        assertEquals("boom", result.exceptionOrNull()?.message)
    }

    @Test
    fun `catching does not catch CancellationException`() = runTest {
        val result = runCatching {
            Async {
                catching<String> { throw CancellationException("cancelled") }
            }
        }
        assertTrue(result.isFailure)
        assertIs<CancellationException>(result.exceptionOrNull())
    }

    @Test
    fun `catching composes with ap branches in parallel`() = runTest {
        val result = Async {
            lift2 { a: Result<Int>, b: Result<String> -> a to b }
                .ap(catching<Int> { delay(30); 42 })
                .ap(catching<String> { delay(30); "hello" })
        }
        assertTrue(result.first.isSuccess)
        assertEquals(42, result.first.getOrNull())
        assertTrue(result.second.isSuccess)
        assertEquals("hello", result.second.getOrNull())
        assertEquals(30, currentTime, "Both catching branches run in parallel")
    }
}
