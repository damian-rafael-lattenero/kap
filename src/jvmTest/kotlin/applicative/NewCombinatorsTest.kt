package applicative

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Tests for new combinators: bracketCase, raceEither, Schedule.forever,
 * Resource.useWithTimeout.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NewCombinatorsTest {

    // ════════════════════════════════════════════════════════════════════════
    // bracketCase
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `bracketCase releases with Completed on success`() = runTest {
        val events = CopyOnWriteArrayList<String>()

        val result = Async {
            bracketCase(
                acquire = { events.add("acquire"); "conn" },
                use = { r -> Computation { delay(50); "result-$r" } },
                release = { r, case ->
                    assertIs<ExitCase.Completed<*>>(case)
                    events.add("release:$r:completed")
                },
            )
        }

        assertEquals("result-conn", result)
        assertEquals(listOf("acquire", "release:conn:completed"), events)
    }

    @Test
    fun `bracketCase releases with Failed on error`() = runTest {
        val events = CopyOnWriteArrayList<String>()

        assertFailsWith<RuntimeException>("boom") {
            Async {
                bracketCase(
                    acquire = { events.add("acquire"); "conn" },
                    use = { _ -> Computation { throw RuntimeException("boom") } },
                    release = { r, case ->
                        assertIs<ExitCase.Failed>(case)
                        assertEquals("boom", (case as ExitCase.Failed).error.message)
                        events.add("release:$r:failed")
                    },
                )
            }
        }

        assertEquals(listOf("acquire", "release:conn:failed"), events)
    }

    @Test
    fun `bracketCase releases with Cancelled on cancellation`() = runTest {
        val events = CopyOnWriteArrayList<String>()

        val job = launch {
            Async {
                bracketCase(
                    acquire = { events.add("acquire"); "conn" },
                    use = { _ -> Computation { awaitCancellation() } },
                    release = { r, case ->
                        assertIs<ExitCase.Cancelled>(case)
                        events.add("release:$r:cancelled")
                    },
                )
            }
        }

        delay(10)
        job.cancel()
        job.join()

        assertEquals(listOf("acquire", "release:conn:cancelled"), events)
    }

    @Test
    fun `bracketCase can commit or rollback based on ExitCase`() = runTest {
        val events = CopyOnWriteArrayList<String>()

        // Success path → commit
        Async {
            bracketCase(
                acquire = { "tx" },
                use = { Computation { "ok" } },
                release = { _, case ->
                    when (case) {
                        is ExitCase.Completed<*> -> events.add("commit")
                        else -> events.add("rollback")
                    }
                },
            )
        }

        // Failure path → rollback
        runCatching {
            Async {
                bracketCase(
                    acquire = { "tx" },
                    use = { Computation { error("fail") } },
                    release = { _, case ->
                        when (case) {
                            is ExitCase.Completed<*> -> events.add("commit")
                            else -> events.add("rollback")
                        }
                    },
                )
            }
        }

        assertEquals(listOf("commit", "rollback"), events)
    }

    // ════════════════════════════════════════════════════════════════════════
    // raceEither
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `raceEither returns Left when fa wins`() = runTest {
        val result = Async {
            raceEither(
                fa = Computation { delay(20); 42 },
                fb = Computation { delay(100); "slow" },
            )
        }

        assertIs<Either.Left<Int>>(result)
        assertEquals(42, result.value)
        assertEquals(20, currentTime)
    }

    @Test
    fun `raceEither returns Right when fb wins`() = runTest {
        val result = Async {
            raceEither(
                fa = Computation { delay(100); 42 },
                fb = Computation { delay(20); "fast" },
            )
        }

        assertIs<Either.Right<String>>(result)
        assertEquals("fast", result.value)
        assertEquals(20, currentTime)
    }

    @Test
    fun `raceEither falls back to other when first fails`() = runTest {
        val fa: Computation<Int> = Computation { delay(10); throw RuntimeException("fa failed") }
        val fb: Computation<String> = Computation { delay(50); "fb-ok" }

        val result = Async { raceEither(fa, fb) }

        assertIs<Either.Right<String>>(result)
        assertEquals("fb-ok", result.value)
    }

    @Test
    fun `raceEither throws when both fail`() = runTest {
        val fa: Computation<Int> = Computation { delay(10); throw RuntimeException("fa-err") }
        val fb: Computation<String> = Computation { delay(20); throw RuntimeException("fb-err") }

        val ex = assertFailsWith<RuntimeException> {
            Async<Either<Int, String>> { raceEither(fa, fb) }
        }

        // First to fail becomes the primary error
        assertEquals("fa-err", ex.message)
    }

    @Test
    fun `raceEither cancels loser`() = runTest {
        var fbCancelled = false
        val fb: Computation<String> = Computation {
            try { awaitCancellation() }
            catch (e: CancellationException) { fbCancelled = true; throw e }
        }

        val result = Async {
            raceEither(
                fa = Computation { delay(20); 42 },
                fb = fb,
            )
        }

        assertIs<Either.Left<Int>>(result)
        assertTrue(fbCancelled)
    }

    // ════════════════════════════════════════════════════════════════════════
    // Schedule.forever
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `Schedule forever always continues`() {
        val schedule = Schedule.forever<Throwable>()
        repeat(100) { attempt ->
            val decision = schedule.decide(attempt, RuntimeException())
            assertIs<Schedule.Decision.Continue>(decision)
            assertEquals(kotlin.time.Duration.ZERO, decision.delay)
        }
    }

    @Test
    fun `Schedule forever composed with recurs stops after N`() {
        val schedule = Schedule.forever<Throwable>() and Schedule.recurs(3)
        val err = RuntimeException()

        assertIs<Schedule.Decision.Continue>(schedule.decide(0, err))
        assertIs<Schedule.Decision.Continue>(schedule.decide(1, err))
        assertIs<Schedule.Decision.Continue>(schedule.decide(2, err))
        assertIs<Schedule.Decision.Done>(schedule.decide(3, err))
    }

    @Test
    fun `Schedule forever composed with exponential uses exponential delays`() {
        val schedule = Schedule.forever<Throwable>() and Schedule.exponential(100.milliseconds)
        val err = RuntimeException()

        val d0 = schedule.decide(0, err) as Schedule.Decision.Continue
        val d1 = schedule.decide(1, err) as Schedule.Decision.Continue
        val d2 = schedule.decide(2, err) as Schedule.Decision.Continue

        assertEquals(100.milliseconds, d0.delay)
        assertEquals(200.milliseconds, d1.delay)
        assertEquals(400.milliseconds, d2.delay)
    }

    @Test
    fun `retry with Schedule forever composed with recurs retries correct times`() = runTest {
        var attempts = 0
        val schedule = Schedule.forever<Throwable>()
            .and(Schedule.recurs(4))
            .and(Schedule.spaced(10.milliseconds))

        val failing: Computation<String> = Computation { attempts++; throw RuntimeException("fail") }
        assertFailsWith<RuntimeException> {
            Async<String> { failing.retry(schedule) }
        }

        assertEquals(5, attempts) // 1 initial + 4 retries
        assertEquals(40, currentTime) // 4 delays of 10ms each
    }

    // ════════════════════════════════════════════════════════════════════════
    // Resource.useWithTimeout
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `useWithTimeout succeeds within timeout`() = runTest {
        val events = CopyOnWriteArrayList<String>()

        val resource = Resource(
            acquire = { events.add("acquire"); "conn" },
            release = { events.add("release") },
        )

        val result = resource.useWithTimeout(1.seconds) { r ->
            delay(50)
            "result-$r"
        }

        assertEquals("result-conn", result)
        assertEquals(listOf("acquire", "release"), events)
        assertEquals(50, currentTime)
    }

    @Test
    fun `useWithTimeout releases on timeout`() = runTest {
        val events = CopyOnWriteArrayList<String>()

        val resource = Resource(
            acquire = { events.add("acquire"); "conn" },
            release = { events.add("release") },
        )

        assertFailsWith<kotlinx.coroutines.TimeoutCancellationException> {
            resource.useWithTimeout(50.milliseconds) { _ ->
                delay(500)
                "never"
            }
        }

        assertTrue(events.contains("acquire"))
        assertTrue(events.contains("release"))
    }

    @Test
    fun `useWithTimeout releases on exception`() = runTest {
        val events = CopyOnWriteArrayList<String>()

        val resource = Resource(
            acquire = { events.add("acquire"); "conn" },
            release = { events.add("release") },
        )

        assertFailsWith<RuntimeException> {
            resource.useWithTimeout(1.seconds) { _ ->
                throw RuntimeException("boom")
            }
        }

        assertEquals(listOf("acquire", "release"), events)
    }
}
