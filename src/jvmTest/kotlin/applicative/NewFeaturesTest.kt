package applicative

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class NewFeaturesTest {

    // ════════════════════════════════════════════════════════════════════════
    // MEMOIZE
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `memoize executes computation only once across sequential uses`() = runTest {
        val counter = AtomicInteger(0)
        val memoized = Computation {
            counter.incrementAndGet()
            "expensive"
        }.memoize()

        val r1 = Async { memoized }
        val r2 = Async { memoized }
        val r3 = Async { memoized }

        assertEquals("expensive", r1)
        assertEquals("expensive", r2)
        assertEquals("expensive", r3)
        assertEquals(1, counter.get(), "Computation should execute exactly once")
    }

    @Test
    fun `memoize caches result and returns it on subsequent uses`() = runTest {
        val counter = AtomicInteger(0)
        val memoized = Computation {
            val value = counter.incrementAndGet()
            value * 10
        }.memoize()

        val result = Async {
            lift2 { a: Int, b: Int -> a + b }
                .ap(memoized)
                .ap(memoized)
        }

        assertEquals(20, result, "Both branches should get cached value 10")
        assertEquals(1, counter.get(), "Computation should execute exactly once even in parallel ap")
    }

    // ════════════════════════════════════════════════════════════════════════
    // COMPUTATION.FAILED
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `failed throws the given exception when executed`() = runTest {
        val result = runCatching {
            Async { Computation.failed(IllegalStateException("boom")) }
        }
        assertTrue(result.isFailure)
        assertIs<IllegalStateException>(result.exceptionOrNull())
        assertEquals("boom", result.exceptionOrNull()?.message)
    }

    @Test
    fun `failed works with recover`() = runTest {
        val result = Async {
            Computation.failed(RuntimeException("oops"))
                .recover { "recovered: ${it.message}" }
        }
        assertEquals("recovered: oops", result)
    }

    @Test
    fun `failed works with recoverWith`() = runTest {
        val result = Async {
            Computation.failed(RuntimeException("oops"))
                .recoverWith { pure("recovered via: ${it.message}") }
        }
        assertEquals("recovered via: oops", result)
    }

    // ════════════════════════════════════════════════════════════════════════
    // COMPUTATION.DEFER
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `defer lazily constructs the computation`() = runTest {
        val constructed = AtomicInteger(0)

        val deferred = Computation.defer {
            constructed.incrementAndGet()
            pure("lazy")
        }

        assertEquals(0, constructed.get(), "Block should not be called until execution")

        val result = Async { deferred }

        assertEquals("lazy", result)
        assertEquals(1, constructed.get(), "Block should be called exactly once on execution")
    }

    @Test
    fun `defer block is only called when the computation is executed`() = runTest {
        val callLog = AtomicInteger(0)

        val deferred = Computation.defer {
            callLog.incrementAndGet()
            pure(callLog.get())
        }

        // Not executed yet — counter should be zero
        assertEquals(0, callLog.get(), "defer block must not run eagerly")

        // First execution
        val r1 = Async { deferred }
        assertEquals(1, r1)
        assertEquals(1, callLog.get())

        // Second execution — defer re-evaluates the block each time
        val r2 = Async { deferred }
        assertEquals(2, r2)
        assertEquals(2, callLog.get(), "defer should re-evaluate on each execution")
    }

    // ════════════════════════════════════════════════════════════════════════
    // SCHEDULE.LINEAR
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `linear produces linearly growing delays`() {
        val schedule = Schedule.linear<Throwable>(100.milliseconds)
        val err = RuntimeException("test")

        // Expected: 100ms, 200ms, 300ms, 400ms, 500ms
        val expected = listOf(100L, 200L, 300L, 400L, 500L)
        expected.forEachIndexed { attempt, expectedMs ->
            val decision = schedule.decide(attempt, err)
            assertIs<Schedule.Decision.Continue>(decision)
            assertEquals(expectedMs.milliseconds, decision.delay, "attempt $attempt")
        }
    }

    @Test
    fun `linear respects max cap`() {
        val schedule = Schedule.linear<Throwable>(100.milliseconds, max = 250.milliseconds)
        val err = RuntimeException("test")

        // Expected: 100, 200, 250(capped), 250(capped)
        val expected = listOf(100L, 200L, 250L, 250L)
        expected.forEachIndexed { attempt, expectedMs ->
            val decision = schedule.decide(attempt, err)
            assertIs<Schedule.Decision.Continue>(decision)
            assertEquals(expectedMs.milliseconds, decision.delay, "attempt $attempt")
        }
    }

    @Test
    fun `linear composes with recurs and produces correct virtual time`() = runTest {
        var attempts = 0
        val policy = Schedule.recurs<Throwable>(3) and
            Schedule.linear(50.milliseconds)

        val result = runCatching {
            Async {
                Computation<String> {
                    attempts++
                    throw RuntimeException("fail")
                }.retry(policy)
            }
        }

        assertTrue(result.isFailure)
        assertEquals(4, attempts, "1 initial + 3 retries = 4 total attempts")
        // Delays: 50 + 100 + 150 = 300ms
        assertEquals(300L, currentTime, "linear delays: 50+100+150 = 300ms")
    }

    @Test
    fun `linear composes with jittered`() {
        val schedule = Schedule.linear<Throwable>(100.milliseconds).jittered(factor = 0.0)
        val err = RuntimeException("test")

        // With factor=0.0, jittered should produce exact linear delays
        val expected = listOf(100L, 200L, 300L)
        expected.forEachIndexed { attempt, expectedMs ->
            val decision = schedule.decide(attempt, err)
            assertIs<Schedule.Decision.Continue>(decision)
            assertEquals(expectedMs.milliseconds, decision.delay, "attempt $attempt with jitter factor=0")
        }
    }
}
