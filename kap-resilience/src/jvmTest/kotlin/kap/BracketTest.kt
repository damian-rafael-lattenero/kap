package kap

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Tests for bracket, guarantee, and guaranteeCase.
 *
 * Proves resource safety under success, failure, and cancellation,
 * including real concurrent scenarios with parallel with branches
 * and virtual-time timing assertions.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BracketTest {

    // ════════════════════════════════════════════════════════════════════════
    // 1. bracket releases resource on success
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `bracket releases resource on success`() = runTest {
        val events = CopyOnWriteArrayList<String>()

        val computation = bracket<String, String>(
            acquire = { events.add("acquire"); "resource" },
            use = { r -> Kap { events.add("use:$r"); delay(50); "result-of-$r" } },
            release = { r -> events.add("release:$r") },
        )

        val result = computation.evalGraph()

        assertEquals("result-of-resource", result)
        assertEquals(listOf("acquire", "use:resource", "release:resource"), events)
    }

    // ════════════════════════════════════════════════════════════════════════
    // 2. bracket releases resource on failure
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `bracket releases resource on failure`() = runTest {
        val released = AtomicBoolean(false)

        val computation = bracket<String, String>(
            acquire = { "conn" },
            use = { _ -> Kap { delay(30); throw IllegalStateException("boom") } },
            release = { _ -> released.set(true) },
        )

        val result = runCatching { computation.evalGraph() }

        assertTrue(result.isFailure)
        assertIs<IllegalStateException>(result.exceptionOrNull())
        assertEquals("boom", result.exceptionOrNull()?.message)
        assertTrue(released.get(), "Release must be called even when use throws")
    }

    // ════════════════════════════════════════════════════════════════════════
    // 3. bracket releases resource on cancellation
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `bracket releases resource on cancellation`() = runTest {
        val released = CompletableDeferred<String>()
        val started = CompletableDeferred<Unit>()

        val computation = bracket<String, String>(
            acquire = { "handle" },
            use = { _ -> Kap { started.complete(Unit); awaitCancellation() } },
            release = { r -> released.complete("released:$r") },
        )

        val job = launch { computation.evalGraph() }

        started.await()
        job.cancel()
        job.join()

        assertEquals("released:handle", released.await(),
            "Release must run in NonCancellable context when coroutine is cancelled")
    }

    // ════════════════════════════════════════════════════════════════════════
    // 4. bracket inside with branches - parallel resources released on sibling failure
    //    Uses CompletableDeferred barriers to PROVE parallelism (deadlocks if sequential)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `bracket inside with branches - parallel resources released on sibling failure`() = runTest {
        val releases = CopyOnWriteArrayList<String>()
        val barrierA = CompletableDeferred<Unit>()
        val barrierB = CompletableDeferred<Unit>()
        val barrierC = CompletableDeferred<Unit>()

        val branchA = bracket<String, String>(
            acquire = { "connA" },
            use = { r -> Kap {
                barrierA.complete(Unit)
                barrierB.await(); barrierC.await()
                delay(100); r
            }},
            release = { r -> releases.add("release:$r") },
        )

        val branchB = bracket<String, String>(
            acquire = { "connB" },
            use = { r -> Kap {
                barrierB.complete(Unit)
                barrierA.await(); barrierC.await()
                delay(100); r
            }},
            release = { r -> releases.add("release:$r") },
        )

        val branchC = bracket<String, String>(
            acquire = { "connC" },
            use = { _ -> Kap {
                barrierC.complete(Unit)
                barrierA.await(); barrierB.await()
                throw RuntimeException("branch C explodes")
            }},
            release = { r -> releases.add("release:$r") },
        )

        val result = runCatching {
            Kap.of { a: String -> { b: String -> { c: String -> "$a|$b|$c" } } }
                .with(branchA)
                .with(branchB)
                .with(branchC)
                .evalGraph()
        }

        assertTrue(result.isFailure)
        assertEquals("branch C explodes", result.exceptionOrNull()?.message)
        // The barrier pattern proves all 3 branches ran in parallel (would deadlock if sequential).
        // All 3 resources must be released: the failing branch releases in its finally,
        // and the cancelled siblings release via NonCancellable.
        assertTrue(releases.contains("release:connC"),
            "Failing branch must release its resource")
        assertTrue(releases.contains("release:connA"),
            "Cancelled sibling A must release its resource")
        assertTrue(releases.contains("release:connB"),
            "Cancelled sibling B must release its resource")
        assertEquals(3, releases.size, "Exactly 3 releases expected")
    }

    // ════════════════════════════════════════════════════════════════════════
    // 5. guarantee runs finalizer on success and failure
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `guarantee runs finalizer on success and failure`() = runTest {
        val events = CopyOnWriteArrayList<String>()

        // ── Success case ──
        val successComp = Kap { delay(30); "ok" }
            .guarantee { events.add("finalizer:success") }
        val successResult = successComp.evalGraph()

        assertEquals("ok", successResult)
        assertTrue(events.contains("finalizer:success"))

        // ── Failure case ──
        events.clear()
        val failComp = Kap<String> { delay(20); throw ArithmeticException("div/0") }
            .guarantee { events.add("finalizer:failure") }

        val failResult = runCatching { failComp.evalGraph() }

        assertTrue(failResult.isFailure)
        assertIs<ArithmeticException>(failResult.exceptionOrNull())
        assertTrue(events.contains("finalizer:failure"),
            "guarantee must run finalizer even when computation fails")
    }

    // ════════════════════════════════════════════════════════════════════════
    // 6. guaranteeCase distinguishes completed, failed, and cancelled
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `guaranteeCase distinguishes completed, failed, and cancelled`() = runTest {
        val cases = CopyOnWriteArrayList<ExitCase>()

        // ── Completed ──
        val completedComp = Kap { delay(10); 42 }
            .guaranteeCase { cases.add(it) }
        val result = completedComp.evalGraph()

        assertEquals(42, result)
        assertEquals(1, cases.size)
        assertIs<ExitCase.Completed<*>>(cases[0])
        assertEquals(42, (cases[0] as ExitCase.Completed<*>).value)

        // ── Failed ──
        cases.clear()
        val failedComp = Kap<Int> { throw IllegalArgumentException("bad arg") }
            .guaranteeCase { cases.add(it) }

        val failResult = runCatching { failedComp.evalGraph() }

        assertTrue(failResult.isFailure)
        assertEquals(1, cases.size)
        assertIs<ExitCase.Failed>(cases[0])
        assertEquals("bad arg", (cases[0] as ExitCase.Failed).error.message)
    }

    // ════════════════════════════════════════════════════════════════════════
    // 7. guaranteeCase receives Cancelled on coroutine cancellation
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `guaranteeCase receives Cancelled on coroutine cancellation`() = runTest {
        val exitCaseRef = CompletableDeferred<ExitCase>()
        val started = CompletableDeferred<Unit>()

        val comp = Kap<String> { started.complete(Unit); awaitCancellation() }
            .guaranteeCase { exitCaseRef.complete(it) }

        val job = launch { comp.evalGraph() }

        started.await()
        job.cancel()
        job.join()

        val exitCase = exitCaseRef.await()
        assertIs<ExitCase.Cancelled>(exitCase,
            "guaranteeCase must receive ExitCase.Cancelled when the coroutine is cancelled")
    }

    // ════════════════════════════════════════════════════════════════════════
    // 8. bracket with kap+with - real multi-phase resource flow
    //    Acquire 3 connections in parallel, use them, verify all 3 released
    //    even when phase 2 fails
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `bracket with kap+with - real multi-phase resource flow`() = runTest {
        val releases = CopyOnWriteArrayList<String>()
        val acquireOrder = CopyOnWriteArrayList<String>()

        val dbBracket = bracket<String, String>(
            acquire = { acquireOrder.add("db"); "db-conn" },
            use = { conn -> Kap { delay(40); "data-from-$conn" } },
            release = { r -> releases.add("release:$r") },
        )

        val cacheBracket = bracket<String, String>(
            acquire = { acquireOrder.add("cache"); "cache-conn" },
            use = { conn -> Kap { delay(40); "data-from-$conn" } },
            release = { r -> releases.add("release:$r") },
        )

        val apiBracket = bracket<String, String>(
            acquire = { acquireOrder.add("api"); "api-conn" },
            use = { conn -> Kap { delay(40); "data-from-$conn" } },
            release = { r -> releases.add("release:$r") },
        )

        val result = runCatching {
                        // Phase 1: acquire 3 connections in parallel via bracket+ap
            Kap.of { a: String -> { b: String -> { c: String -> Triple(a, b, c) } } }
                .with(dbBracket)
                .with(cacheBracket)
                .with(apiBracket)
                // Phase 2: use the combined result - this fails
                .andThen { triple ->
                    Kap {
                        val msg = "phase 2 failed: ${triple.first}, ${triple.second}, ${triple.third}"
                        throw RuntimeException(msg)
                    }
                }.evalGraph()
        }

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("phase 2 failed") == true)
        assertEquals(3, acquireOrder.size, "All 3 resources should have been acquired")
        assertTrue(releases.contains("release:db-conn"), "db-conn must be released")
        assertTrue(releases.contains("release:cache-conn"), "cache-conn must be released")
        assertTrue(releases.contains("release:api-conn"), "api-conn must be released")
        // Phase 1 should complete in 40ms (parallel), not 120ms (sequential)
        assertEquals(40, currentTime,
            "3 parallel bracket acquisitions should take max(40,40,40)=40ms")
    }

    // ════════════════════════════════════════════════════════════════════════
    // 9. nested bracket releases in correct order
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `nested bracket releases in correct order`() = runTest {
        val events = CopyOnWriteArrayList<String>()

        val computation = bracket<String, String>(
            acquire = { events.add("acquire:outer"); "outer" },
            use = { outer ->
                bracket<String, String>(
                    acquire = { events.add("acquire:inner"); "inner" },
                    use = { inner -> Kap {
                        events.add("use:$outer+$inner")
                        delay(30)
                        "$outer+$inner"
                    }},
                    release = { r -> events.add("release:$r") },
                )
            },
            release = { r -> events.add("release:$r") },
        )

        val result = computation.evalGraph()

        assertEquals("outer+inner", result)
        assertEquals(
            listOf(
                "acquire:outer",
                "acquire:inner",
                "use:outer+inner",
                "release:inner",
                "release:outer",
            ),
            events,
            "Inner bracket must release before outer bracket (stack unwinding order)"
        )
    }

    // ════════════════════════════════════════════════════════════════════════
    // 10. bracket timing proof - release does not add to virtual time
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `bracket timing proof - release does not add to virtual time`() = runTest {
        val released = CompletableDeferred<Unit>()
        val started = CompletableDeferred<Unit>()

        val computation = bracket<String, String>(
            acquire = { "resource" },
            use = { _ -> Kap { started.complete(Unit); delay(100); awaitCancellation() } },
            release = { _ ->
                // Release runs in NonCancellable during cancellation cleanup.
                // It should NOT advance the virtual clock because it happens
                // as part of cancellation, not as sequential computation work.
                released.complete(Unit)
            },
        )

        val job = launch { computation.evalGraph() }

        started.await()
        // The computation is suspended in awaitCancellation(). Cancel the job.
        val timeBeforeCancel = currentTime
        job.cancel()
        job.join()

        released.await()

        // The key insight: release runs during cancellation cleanup, so the virtual
        // clock does not advance beyond the point of cancellation.
        val timeAfterRelease = currentTime
        assertEquals(timeBeforeCancel, timeAfterRelease,
            "Release must not add to virtual time - it runs as part of cancellation cleanup. " +
            "Before=$timeBeforeCancel, After=$timeAfterRelease")
    }
}
