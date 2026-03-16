package applicative

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

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
}
