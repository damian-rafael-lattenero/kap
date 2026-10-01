package kap

import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
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

/**
 * Property-based cancellation tests verifying that CancellationException
 * is NEVER swallowed by any combinator and that structured concurrency
 * cancellation propagates correctly — over arbitrary timeouts and delays.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CancellationPropertyTest {

    @Test
    fun `recover never catches CancellationException, for arbitrary timeouts`() = runTest {
        checkAll(Arb.int(10..50), Arb.int(100..500)) { timeout, work ->
            val recovered = AtomicBoolean(false)
            val comp = Kap<String> {
                delay(work.milliseconds)
                "done"
            }.recover {
                recovered.set(true)
                "recovered"
            }

            assertFailsWith<CancellationException> {
                withTimeout(timeout.milliseconds) {
                    comp.evalGraph()
                }
            }
            assertFalse(recovered.get(), "recover should NEVER catch CancellationException")
        }
    }

    @Test
    fun `settled never catches CancellationException, for arbitrary timeouts`() = runTest {
        checkAll(Arb.int(10..50), Arb.int(100..500)) { timeout, work ->
            val comp = Kap<String> {
                delay(work.milliseconds)
                "done"
            }.settled()

            assertFailsWith<CancellationException> {
                withTimeout(timeout.milliseconds) {
                    comp.evalGraph()
                }
            }
        }
    }

    @Test
    fun `retry never retries on CancellationException, for arbitrary attempt limits`() = runTest {
        checkAll(Arb.int(2..10), Arb.int(10..50), Arb.int(100..500)) { maxAttempts, retryDelay, work ->
            val attemptCount = AtomicInteger(0)
            val comp = Kap<String> {
                attemptCount.incrementAndGet()
                delay(work.milliseconds)
                "done"
            }.retry(maxAttempts = maxAttempts, delay = retryDelay.milliseconds)

            assertFailsWith<CancellationException> {
                withTimeout(50.milliseconds) {
                    comp.evalGraph()
                }
            }
            assertEquals(1, attemptCount.get(), "Should not retry on CancellationException")
        }
    }

    @Test
    fun `orElse never falls through on CancellationException, for arbitrary timeouts`() = runTest {
        checkAll(Arb.int(10..50), Arb.int(100..500)) { timeout, work ->
            val fallbackCalled = AtomicBoolean(false)
            val comp = Kap<String> {
                delay(work.milliseconds)
                "done"
            } orElse Kap {
                fallbackCalled.set(true)
                "fallback"
            }

            assertFailsWith<CancellationException> {
                withTimeout(timeout.milliseconds) {
                    comp.evalGraph()
                }
            }
            assertFalse(fallbackCalled.get())
        }
    }

    @Test
    fun `ap cancels sibling branches when one fails, for arbitrary failure timing`() = runTest {
        checkAll(Arb.int(1..50), Arb.int(100..500)) { failDelay, siblingDelay ->
            val siblingCancelled = AtomicBoolean(false)
            val result = runCatching {
                Kap.of { a: String -> { b: String -> "$a$b" } }
                    .with {
                        delay(failDelay.milliseconds)
                        throw IllegalStateException("fail fast")
                    }
                    .with {
                        try {
                            delay(siblingDelay.milliseconds)
                            "should not complete"
                        } catch (e: CancellationException) {
                            siblingCancelled.set(true)
                            throw e
                        }
                    }.evalGraph()
            }

            assertTrue(result.isFailure)
            assertTrue(siblingCancelled.get(), "Sibling should be cancelled when one with branch fails")
        }
    }

    @Test
    fun `memoize does not cache CancellationException, for arbitrary timeouts`() = runTest {
        checkAll(Arb.int(10..50), Arb.int(100..500)) { timeout, work ->
            val attemptCount = AtomicInteger(0)
            val comp = Kap {
                attemptCount.incrementAndGet()
                delay(work.milliseconds)
                "done"
            }.memoize()

            // First call gets cancelled
            try {
                withTimeout(timeout.milliseconds) {
                    comp.evalGraph()
                }
            } catch (_: CancellationException) {}

            // Second call should retry (not return cached CancellationException)
            val result = comp.evalGraph()
            assertEquals("done", result)
            assertEquals(2, attemptCount.get(), "Should retry after cancellation")
        }
    }
}
