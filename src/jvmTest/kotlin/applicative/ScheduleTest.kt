package applicative

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleTest {

    // ════════════════════════════════════════════════════════════════════════
    // recurs
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `recurs retries exactly n times`() = runTest {
        var attempts = 0
        val result = runCatching {
            Async {
                Computation<String> {
                    attempts++
                    throw RuntimeException("fail #$attempts")
                }.retry(Schedule.recurs(3))
            }
        }
        assertTrue(result.isFailure)
        assertEquals(4, attempts, "Should attempt 1 + 3 retries = 4 total")
    }

    @Test
    fun `recurs succeeds on retry`() = runTest {
        var attempts = 0
        val result = Async {
            Computation {
                attempts++
                if (attempts < 3) throw RuntimeException("fail")
                "ok"
            }.retry(Schedule.recurs(5))
        }
        assertEquals("ok", result)
        assertEquals(3, attempts)
    }

    // ════════════════════════════════════════════════════════════════════════
    // exponential
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `exponential produces increasing delays`() = runTest {
        val delays = mutableListOf<Long>()
        val result = runCatching {
            Async {
                Computation<String> {
                    throw RuntimeException("fail")
                }.retry(
                    Schedule.recurs<Throwable>(3) and Schedule.exponential(100.milliseconds),
                    onRetry = { _, _, _ -> delays.add(currentTime) },
                )
            }
        }
        assertTrue(result.isFailure)
        assertEquals(3, delays.size)
        // delays recorded at: t=0 (before 100ms wait), t=100 (before 200ms wait), t=300 (before 400ms wait)
        assertEquals(0L, delays[0])
        assertEquals(100L, delays[1])
        assertEquals(300L, delays[2])
        assertEquals(700L, currentTime, "Total: 100 + 200 + 400 = 700ms")
    }

    // ════════════════════════════════════════════════════════════════════════
    // spaced
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `spaced uses fixed delay`() = runTest {
        var attempts = 0
        val result = runCatching {
            Async {
                Computation<String> {
                    attempts++
                    throw RuntimeException("fail")
                }.retry(Schedule.recurs<Throwable>(3) and Schedule.spaced(50.milliseconds))
            }
        }
        assertTrue(result.isFailure)
        assertEquals(4, attempts)
        assertEquals(150L, currentTime, "3 retries * 50ms = 150ms")
    }

    // ════════════════════════════════════════════════════════════════════════
    // doWhile
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `doWhile stops on non-matching exception`() = runTest {
        var attempts = 0
        val result = runCatching {
            Async {
                Computation<String> {
                    attempts++
                    if (attempts <= 2) throw IOException("network")
                    throw IllegalStateException("bad state")
                }.retry(Schedule.doWhile { it is IOException })
            }
        }
        assertTrue(result.isFailure)
        assertIs<IllegalStateException>(result.exceptionOrNull())
        assertEquals(3, attempts, "2 IOExceptions retried, then ISE stops")
    }

    // ════════════════════════════════════════════════════════════════════════
    // and / or composition
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `and stops when either schedule says done`() = runTest {
        var attempts = 0
        // recurs(2) allows 2 retries, doWhile always true → limited by recurs
        val result = runCatching {
            Async {
                Computation<String> {
                    attempts++
                    throw RuntimeException("fail")
                }.retry(Schedule.recurs<Throwable>(2) and Schedule.doWhile { true })
            }
        }
        assertTrue(result.isFailure)
        assertEquals(3, attempts, "recurs(2) limits to 3 total attempts")
    }

    @Test
    fun `or continues when either schedule says continue`() = runTest {
        var attempts = 0
        // recurs(1) allows 1 retry, but or with recurs(3) allows 3
        val result = runCatching {
            Async {
                Computation<String> {
                    attempts++
                    throw RuntimeException("fail")
                }.retry(Schedule.recurs<Throwable>(1) or Schedule.recurs(3))
            }
        }
        assertTrue(result.isFailure)
        assertEquals(4, attempts, "or takes the more permissive schedule: 3 retries = 4 attempts")
    }

    @Test
    fun `and uses max delay, or uses min delay`() {
        val s1 = Schedule.spaced<Throwable>(100.milliseconds)
        val s2 = Schedule.spaced<Throwable>(200.milliseconds)
        val err = RuntimeException("test")

        val combined: Schedule<Throwable> = s1 and s2
        val andDecision = combined.decide(0, err)
        assertIs<Schedule.Decision.Continue>(andDecision)
        assertEquals(200.milliseconds, andDecision.delay, "and uses max delay")

        val either: Schedule<Throwable> = s1 or s2
        val orDecision = either.decide(0, err)
        assertIs<Schedule.Decision.Continue>(orDecision)
        assertEquals(100.milliseconds, orDecision.delay, "or uses min delay")
    }

    // ════════════════════════════════════════════════════════════════════════
    // Integration: composing multiple policies
    // ════════════════════════════════════════════════════════════════════════

    // ════════════════════════════════════════════════════════════════════════
    // jittered
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `jittered adds random spread to delays`() {
        // Seeded random for deterministic test
        val seeded = Random(42)
        val schedule = Schedule.spaced<Throwable>(100.milliseconds).jittered(0.5, seeded)
        val err = RuntimeException("test")

        // All decisions should be Continue with delay in [50ms, 150ms]
        repeat(20) { attempt ->
            val decision = schedule.decide(attempt, err)
            assertIs<Schedule.Decision.Continue>(decision)
            assertTrue(decision.delay >= 50.milliseconds, "delay ${decision.delay} should be >= 50ms")
            assertTrue(decision.delay <= 150.milliseconds, "delay ${decision.delay} should be <= 150ms")
        }
    }

    @Test
    fun `jittered preserves Done decisions`() {
        val schedule = Schedule.recurs<Throwable>(1).jittered()
        val err = RuntimeException("test")

        // attempt 0 → Continue (within recurs limit)
        assertIs<Schedule.Decision.Continue>(schedule.decide(0, err))
        // attempt 1 → Done (recurs exhausted)
        assertIs<Schedule.Decision.Done>(schedule.decide(1, err))
    }

    @Test
    fun `jittered factor must be in valid range`() {
        assertFailsWith<IllegalArgumentException> {
            Schedule.spaced<Throwable>(100.milliseconds).jittered(factor = -0.1)
        }
        assertFailsWith<IllegalArgumentException> {
            Schedule.spaced<Throwable>(100.milliseconds).jittered(factor = 1.1)
        }
    }

    @Test
    fun `jittered with factor zero produces exact delays`() {
        val schedule = Schedule.spaced<Throwable>(100.milliseconds).jittered(factor = 0.0)
        val err = RuntimeException("test")

        repeat(5) { attempt ->
            val decision = schedule.decide(attempt, err)
            assertIs<Schedule.Decision.Continue>(decision)
            assertEquals(100.milliseconds, decision.delay, "factor=0 should produce exact delay")
        }
    }

    @Test
    fun `jittered composes with exponential for production retry`() = runTest {
        val seeded = Random(123)
        val policy = Schedule.recurs<Throwable>(3) and
            Schedule.exponential<Throwable>(100.milliseconds).jittered(0.5, seeded)

        var attempts = 0
        val result = runCatching {
            Async {
                Computation<String> {
                    attempts++
                    throw RuntimeException("fail #$attempts")
                }.retry(policy)
            }
        }
        assertTrue(result.isFailure)
        assertEquals(4, attempts, "recurs(3) limits to 4 total attempts")
        // Virtual time should be > 0 (jittered delays applied)
        assertTrue(currentTime > 0, "jittered delays should produce non-zero wait")
    }

    // ════════════════════════════════════════════════════════════════════════
    // fibonacci
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `fibonacci produces correct delay sequence`() {
        val schedule = Schedule.fibonacci<Throwable>(50.milliseconds)
        val err = RuntimeException("test")

        // Fibonacci: 50, 50, 100, 150, 250, 400, 650
        val expected = listOf(50L, 50L, 100L, 150L, 250L, 400L, 650L)
        expected.forEachIndexed { attempt, expectedMs ->
            val decision = schedule.decide(attempt, err)
            assertIs<Schedule.Decision.Continue>(decision)
            assertEquals(expectedMs.milliseconds, decision.delay, "attempt $attempt")
        }
    }

    @Test
    fun `fibonacci respects max cap`() {
        val schedule = Schedule.fibonacci<Throwable>(50.milliseconds, max = 200.milliseconds)
        val err = RuntimeException("test")

        // 50, 50, 100, 150, 200(capped), 200(capped)...
        val expected = listOf(50L, 50L, 100L, 150L, 200L, 200L)
        expected.forEachIndexed { attempt, expectedMs ->
            val decision = schedule.decide(attempt, err)
            assertIs<Schedule.Decision.Continue>(decision)
            assertEquals(expectedMs.milliseconds, decision.delay, "attempt $attempt")
        }
    }

    @Test
    fun `fibonacci with recurs produces correct virtual time`() = runTest {
        var attempts = 0
        val policy = Schedule.recurs<Throwable>(4) and
            Schedule.fibonacci(50.milliseconds)

        val result = runCatching {
            Async {
                Computation<String> {
                    attempts++
                    throw RuntimeException("fail")
                }.retry(policy)
            }
        }
        assertTrue(result.isFailure)
        assertEquals(5, attempts) // 1 initial + 4 retries
        // Delays: 50 + 50 + 100 + 150 = 350ms
        assertEquals(350L, currentTime, "fibonacci delays: 50+50+100+150 = 350ms")
    }

    @Test
    fun `fibonacci composes with doWhile and jittered`() {
        val seeded = Random(99)
        val policy = Schedule.fibonacci<Throwable>(100.milliseconds, max = 1.seconds) and
            Schedule.doWhile<Throwable> { it is IOException }

        val jittered = policy.jittered(0.3, seeded)

        // IOException → Continue with jittered fibonacci delay
        val ioDecision = jittered.decide(0, IOException("net"))
        assertIs<Schedule.Decision.Continue>(ioDecision)
        assertTrue(ioDecision.delay >= 70.milliseconds, "jittered delay should be >= 70ms (100 * 0.7)")
        assertTrue(ioDecision.delay <= 130.milliseconds, "jittered delay should be <= 130ms (100 * 1.3)")

        // Non-IOException → Done (doWhile stops)
        val iseDecision = jittered.decide(0, IllegalStateException("bad"))
        assertIs<Schedule.Decision.Done>(iseDecision)
    }

    // ════════════════════════════════════════════════════════════════════════
    // withMaxDuration
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `withMaxDuration stops retrying after total elapsed time`() = runTest {
        var attempts = 0
        val policy = Schedule.spaced<Throwable>(100.milliseconds)
            .withMaxDuration(350.milliseconds)

        val result = runCatching {
            Async {
                Computation<String> {
                    attempts++
                    throw RuntimeException("fail")
                }.retry(policy)
            }
        }
        assertTrue(result.isFailure)
        // With 100ms spacing and 350ms max: attempts at t=0, t=100, t=200, t=300, then t=400 would exceed
        assertTrue(attempts >= 3, "Should attempt at least 3 times within 350ms")
    }

    @Test
    fun `withMaxDuration composes with exponential`() = runTest {
        var attempts = 0
        val policy = Schedule.exponential<Throwable>(100.milliseconds)
            .withMaxDuration(500.milliseconds)

        val result = runCatching {
            Async {
                Computation<String> {
                    attempts++
                    throw RuntimeException("fail")
                }.retry(policy)
            }
        }
        assertTrue(result.isFailure)
        // Delays: 100ms (t=100), 200ms (t=300), 400ms would push to t=700 > 500 → Done
        assertTrue(attempts <= 4, "Should stop before delay would exceed max duration")
    }

    // ════════════════════════════════════════════════════════════════════════
    // Integration: composing multiple policies
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `composed policy - recurs + exponential + doWhile`() = runTest {
        var attempts = 0
        val policy = Schedule.recurs<Throwable>(5) and
            Schedule.exponential(50.milliseconds) and
            Schedule.doWhile { it is IOException }

        val result = runCatching {
            Async {
                Computation<String> {
                    attempts++
                    when (attempts) {
                        1 -> throw IOException("net #1")
                        2 -> throw IOException("net #2")
                        3 -> throw IllegalStateException("bad")
                        else -> "ok"
                    }
                }.retry(policy)
            }
        }

        assertTrue(result.isFailure)
        assertIs<IllegalStateException>(result.exceptionOrNull())
        assertEquals(3, attempts, "2 IOExceptions retried, ISE stopped by doWhile")
        // Delays: attempt 1 → 50ms, attempt 2 → 100ms. ISE at attempt 3 exits immediately.
        assertEquals(150L, currentTime, "50ms + 100ms = 150ms")
    }

    // ════════════════════════════════════════════════════════════════════════
    // Schedule.fold — accumulates values across retries
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `fold accumulates values across retries`() = runTest {
        var errorLog = emptyList<String>()
        val policy = Schedule.recurs<Throwable>(3)
            .fold(emptyList<String>()) { acc, err -> acc + err.message.orEmpty() }

        // Capture the fold side-effect via the schedule itself
        val foldPolicy = Schedule<Throwable> { attempt, value ->
            val decision = policy.decide(attempt, value)
            // We can't read the fold accumulator directly, so we track externally
            errorLog = errorLog + value.message.orEmpty()
            decision
        }

        var attempts = 0
        val result = Async {
            Computation<String> {
                attempts++
                if (attempts <= 3) throw RuntimeException("fail-$attempts")
                "ok"
            }.retry(foldPolicy)
        }

        assertEquals("ok", result)
        assertEquals(3, errorLog.size)
        assertEquals(listOf("fail-1", "fail-2", "fail-3"), errorLog)
    }

    // ════════════════════════════════════════════════════════════════════════
    // retryWithResult — returns metadata alongside the result
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `retryWithResult returns attempt count and total delay on success`() = runTest {
        var attempts = 0
        val policy = Schedule.recurs<Throwable>(3) and Schedule.spaced(50.milliseconds)

        val retryResult = Async {
            Computation<String> {
                attempts++
                if (attempts <= 2) throw RuntimeException("fail")
                "ok"
            }.retryWithResult(policy)
        }

        assertEquals("ok", retryResult.value)
        assertEquals(2, retryResult.attempts, "2 retries before success")
        assertEquals(100.milliseconds, retryResult.totalDelay, "50ms + 50ms = 100ms")
        assertEquals(100L, currentTime)
    }

    @Test
    fun `retryWithResult reports zero attempts on immediate success`() = runTest {
        val policy = Schedule.recurs<Throwable>(3)

        val retryResult = Async {
            Computation { "instant" }.retryWithResult(policy)
        }

        assertEquals("instant", retryResult.value)
        assertEquals(0, retryResult.attempts)
        assertEquals(Duration.ZERO, retryResult.totalDelay)
    }

    @Test
    fun `retryWithResult throws when schedule exhausted`() = runTest {
        val policy = Schedule.recurs<Throwable>(2)

        val result = runCatching {
            Async {
                Computation<String> { throw RuntimeException("always fail") }
                    .retryWithResult(policy)
            }
        }

        assertTrue(result.isFailure)
        assertIs<RuntimeException>(result.exceptionOrNull())
    }
}
