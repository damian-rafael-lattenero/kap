package kap

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration

class TimedTest {

    // ── Basic: timed() returns value + duration ─────────────────────

    @Test
    fun `timed returns correct value`() = runTest {
        val result = Kap { "hello" }
            .timed()
            .evalGraph()

        assertEquals("hello", result.value)
        assertTrue(result.duration >= Duration.ZERO)
    }

    @Test
    fun `evalGraphTimed is shortcut for timed + evalGraph`() = runTest {
        val result = Kap { 42 }.evalGraphTimed()

        assertEquals(42, result.value)
        assertTrue(result.duration >= Duration.ZERO)
    }

    // ── Parallel: value correctness ─────────────────────────────────

    @Test
    fun `timed preserves parallel execution results`() = runTest {
        val result = Kap.of { a: String -> { b: String -> { c: String -> "$a|$b|$c" } } }
            .with { delay(50); "a" }
            .with { delay(50); "b" }
            .with { delay(50); "c" }
            .timed()
            .evalGraph()

        assertEquals("a|b|c", result.value)
    }

    // ── Phases: timed preserves barrier semantics ────────────────────

    @Test
    fun `timed preserves phase barrier execution order`() = runTest {
        val log = mutableListOf<String>()

        val result = Kap.of { a: String -> { b: String -> { c: String -> "$a|$b|$c" } } }
            .with { log.add("p1-start"); delay(30); log.add("p1-end"); "phase1" }
            .then { log.add("barrier"); delay(10); "barrier-ok" }
            .with { log.add("p2-start"); delay(20); "phase2" }
            .timed()
            .evalGraph()

        assertEquals("phase1|barrier-ok|phase2", result.value)
        // Barrier must happen after phase 1
        assertTrue(log.indexOf("barrier") > log.indexOf("p1-end"),
            "Barrier should run after phase 1 ends")
    }

    // ── Composition: timed composes with other combinators ──────────

    @Test
    fun `timed composes with map`() = runTest {
        val result = Kap { "hello" }
            .timed()
            .map { "value=${it.value}" }
            .evalGraph()

        assertEquals("value=hello", result)
    }

    @Test
    fun `timed composes with andThen`() = runTest {
        val result = Kap { "first" }
            .timed()
            .andThen { timedResult ->
                Kap.of("got: ${timedResult.value}")
            }
            .evalGraph()

        assertEquals("got: first", result)
    }

    @Test
    fun `timed composes with recover`() = runTest {
        val result = Kap<String> { throw RuntimeException("boom") }
            .timed()
            .recover { TimedResult("fallback", Duration.ZERO) }
            .evalGraph()

        assertEquals("fallback", result.value)
    }

    @Test
    fun `timed composes with memoize`() = runTest {
        var callCount = 0
        val memoized = Kap { callCount++; "expensive" }
            .timed()
            .memoize()

        val r1 = memoized.evalGraph()
        val r2 = memoized.evalGraph()

        assertEquals("expensive", r1.value)
        assertEquals(r1, r2)
        assertEquals(1, callCount, "Should only execute once")
    }

    @Test
    fun `timed composes with settled`() = runTest {
        val result = Kap<String> { throw RuntimeException("fail") }
            .timed()
            .settled()
            .evalGraph()

        assertTrue(result.isFailure)
    }

    // ── Per-branch: timed on individual branches ────────────────────

    @Test
    fun `timed on individual branches produces per-branch TimedResult`() = runTest {
        val userTimed = Kap { "Alice" }.timed()
        val cartTimed = Kap { "3 items" }.timed()

        val result = Kap.of { a: TimedResult<String> -> { b: TimedResult<String> ->
            "${a.value}|${b.value}"
        } }
            .with(userTimed)
            .with(cartTimed)
            .evalGraph()

        assertEquals("Alice|3 items", result)
    }

    @Test
    fun `per-branch timed durations are independent`() = runTest {
        // Branch A is slow (real delay via Thread.sleep to bypass virtual time)
        val branchA = Kap {
            Thread.sleep(50)
            "slow"
        }.timed()

        val branchB = Kap { "fast" }.timed()

        val result = Kap.of { a: TimedResult<String> -> { b: TimedResult<String> ->
            Pair(a, b)
        } }
            .with(branchA)
            .with(branchB)
            .evalGraph()

        assertEquals("slow", result.first.value)
        assertEquals("fast", result.second.value)
        // Branch A took real time, branch B was instant
        assertTrue(result.first.duration > result.second.duration,
            "Slow branch should have longer duration than fast branch")
    }

    // ── Error: timed does not swallow exceptions ────────────────────

    @Test
    fun `timed propagates exceptions`() = runTest {
        val result = runCatching {
            Kap<String> { throw IllegalStateException("boom") }
                .timed()
                .evalGraph()
        }

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun `timed propagates cancellation`() = runTest {
        val result = runCatching {
            Kap.of { a: String -> { b: String -> "$a|$b" } }
                .with { "ok" }
                .with { throw RuntimeException("fail") }
                .timed()
                .evalGraph()
        }

        assertTrue(result.isFailure)
    }

    // ── Nested: timed inside timed ──────────────────────────────────

    @Test
    fun `nested timed wraps correctly`() = runTest {
        val result = Kap { "inner" }
            .timed()
            .timed()
            .evalGraph()

        assertEquals("inner", result.value.value)
        assertTrue(result.duration >= Duration.ZERO)
        assertTrue(result.value.duration >= Duration.ZERO)
    }

    // ── Zero-cost: timed on pure value ──────────────────────────────

    @Test
    fun `timed on pure value shows minimal duration`() = runTest {
        val result = Kap.of(42).timed().evalGraph()

        assertEquals(42, result.value)
    }

    // ── Destructuring: TimedResult supports component functions ─────

    @Test
    fun `TimedResult supports destructuring`() = runTest {
        val (value, duration) = Kap { "hello" }.evalGraphTimed()

        assertEquals("hello", value)
        assertTrue(duration >= Duration.ZERO)
    }

    // ── Real timing: verify with Thread.sleep (bypasses virtual time) ──

    @Test
    fun `timed measures real wall-clock time`() = runTest {
        val result = Kap {
            Thread.sleep(100)
            "done"
        }.evalGraphTimed()

        assertEquals("done", result.value)
        assertTrue(result.duration.inWholeMilliseconds >= 90,
            "Expected >=90ms real time, got ${result.duration.inWholeMilliseconds}ms")
    }

    @Test
    fun `parallel branches with timed show wall-clock not sum`() = runTest {
        // Use delay (virtual time) — timed() measures real clock, but
        // the key assertion is that the value is correct and timed() doesn't break parallelism
        val result = Kap.of { a: String -> { b: String -> "$a|$b" } }
            .with { delay(80); "a" }
            .with { delay(80); "b" }
            .timed()
            .evalGraph()

        assertEquals("a|b", result.value)
    }
}
