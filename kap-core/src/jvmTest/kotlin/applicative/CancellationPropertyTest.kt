package applicative

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Property-based cancellation tests verifying that CancellationException
 * is NEVER swallowed by any combinator and that structured concurrency
 * cancellation propagates correctly in all scenarios.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CancellationPropertyTest {

    @Test
    fun `recover never catches CancellationException`() = runTest {
        val recovered = AtomicBoolean(false)
        val comp = Effect<String> {
            delay(1.seconds)
            "done"
        }.recover {
            recovered.set(true)
            "recovered"
        }

        assertFailsWith<CancellationException> {
            withTimeout(50.milliseconds) {
                comp.await()
            }
        }
        assertFalse(recovered.get(), "recover should NEVER catch CancellationException")
    }

    @Test
    fun `settled never catches CancellationException`() = runTest {
        val comp = Effect<String> {
            delay(1.seconds)
            "done"
        }.settled()

        assertFailsWith<CancellationException> {
            withTimeout(50.milliseconds) {
                comp.await()
            }
        }
    }

    @Test
    fun `retry never retries on CancellationException`() = runTest {
        val attemptCount = AtomicInteger(0)
        val comp = Effect<String> {
            attemptCount.incrementAndGet()
            delay(1.seconds)
            "done"
        }.retry(maxAttempts = 5, delay = 10.milliseconds)

        assertFailsWith<CancellationException> {
            withTimeout(50.milliseconds) {
                comp.await()
            }
        }
        assertEquals(1, attemptCount.get(), "Should not retry on CancellationException")
    }

    @Test
    fun `orElse never falls through on CancellationException`() = runTest {
        val fallbackCalled = AtomicBoolean(false)
        val comp = Effect<String> {
            delay(1.seconds)
            "done"
        } orElse Effect {
            fallbackCalled.set(true)
            "fallback"
        }

        assertFailsWith<CancellationException> {
            withTimeout(50.milliseconds) {
                comp.await()
            }
        }
        assertFalse(fallbackCalled.get())
    }

    @Test
    fun `ap cancels sibling branches when one fails`() = runTest {
        val siblingCancelled = AtomicBoolean(false)
        val result = runCatching {
            Async {
                kap { a: String, b: String -> "$a$b" }
                    .with {
                        delay(10)
                        throw IllegalStateException("fail fast")
                    }
                    .with {
                        try {
                            delay(1.seconds)
                            "should not complete"
                        } catch (e: CancellationException) {
                            siblingCancelled.set(true)
                            throw e
                        }
                    }
            }
        }

        assertTrue(result.isFailure)
        assertTrue(siblingCancelled.get(), "Sibling should be cancelled when one with branch fails")
    }

    @Test
    fun `memoize does not cache CancellationException`() = runTest {
        val attemptCount = AtomicInteger(0)
        val comp = Effect {
            attemptCount.incrementAndGet()
            delay(1.seconds)
            "done"
        }.memoize()

        // First call gets cancelled
        try {
            withTimeout(50.milliseconds) {
                comp.await()
            }
        } catch (_: CancellationException) {}

        // Second call should retry (not return cached CancellationException)
        val result = comp.await()
        assertEquals("done", result)
        assertEquals(2, attemptCount.get(), "Should retry after cancellation")
    }
}
