package kap

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
        val memoized = Kap {
            counter.incrementAndGet()
            "expensive"
        }.memoize()

        val r1 = Async { memoized }
        val r2 = Async { memoized }
        val r3 = Async { memoized }

        assertEquals("expensive", r1)
        assertEquals("expensive", r2)
        assertEquals("expensive", r3)
        assertEquals(1, counter.get(), "Kap should execute exactly once")
    }

    @Test
    fun `memoize caches result and returns it on subsequent uses`() = runTest {
        val counter = AtomicInteger(0)
        val memoized = Kap {
            val value = counter.incrementAndGet()
            value * 10
        }.memoize()

        val result = Async {
            kap { a: Int, b: Int -> a + b }
                .with(memoized)
                .with(memoized)
        }

        assertEquals(20, result, "Both branches should get cached value 10")
        assertEquals(1, counter.get(), "Kap should execute exactly once even in parallel ap")
    }

    // ════════════════════════════════════════════════════════════════════════
    // COMPUTATION.FAILED
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `failed throws the given exception when executed`() = runTest {
        val result = runCatching {
            Async { Kap.failed(IllegalStateException("boom")) }
        }
        assertTrue(result.isFailure)
        assertIs<IllegalStateException>(result.exceptionOrNull())
        assertEquals("boom", result.exceptionOrNull()?.message)
    }

    @Test
    fun `failed works with recover`() = runTest {
        val result = Async {
            Kap.failed(RuntimeException("oops"))
                .recover { "recovered: ${it.message}" }
        }
        assertEquals("recovered: oops", result)
    }

    @Test
    fun `failed works with recoverWith`() = runTest {
        val result = Async {
            Kap.failed(RuntimeException("oops"))
                .recoverWith { Kap.of("recovered via: ${it.message}") }
        }
        assertEquals("recovered via: oops", result)
    }

    // ════════════════════════════════════════════════════════════════════════
    // COMPUTATION.DEFER
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `defer lazily constructs the computation`() = runTest {
        val constructed = AtomicInteger(0)

        val deferred = Kap.defer {
            constructed.incrementAndGet()
            Kap.of("lazy")
        }

        assertEquals(0, constructed.get(), "Block should not be called until execution")

        val result = Async { deferred }

        assertEquals("lazy", result)
        assertEquals(1, constructed.get(), "Block should be called exactly once on execution")
    }

    @Test
    fun `defer block is only called when the computation is executed`() = runTest {
        val callLog = AtomicInteger(0)

        val deferred = Kap.defer {
            callLog.incrementAndGet()
            Kap.of(callLog.get())
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

}
