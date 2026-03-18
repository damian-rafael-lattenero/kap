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
        val comp = Computation<String> {
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
    fun `attempt never catches CancellationException`() = runTest {
        val comp = Computation<String> {
            delay(1.seconds)
            "done"
        }.attempt()

        assertFailsWith<CancellationException> {
            withTimeout(50.milliseconds) {
                comp.await()
            }
        }
    }

    @Test
    fun `retry never retries on CancellationException`() = runTest {
        val attemptCount = AtomicInteger(0)
        val comp = Computation<String> {
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
    fun `recoverV never catches CancellationException`() = runTest {
        val recovered = AtomicBoolean(false)
        val comp = Computation<Either<NonEmptyList<String>, String>> {
            delay(1.seconds)
            Either.Right("done")
        }.recoverV {
            recovered.set(true)
            "recovered-error"
        }

        assertFailsWith<CancellationException> {
            withTimeout(50.milliseconds) {
                comp.await()
            }
        }
        assertFalse(recovered.get())
    }

    @Test
    fun `orElse never falls through on CancellationException`() = runTest {
        val fallbackCalled = AtomicBoolean(false)
        val comp = Computation<String> {
            delay(1.seconds)
            "done"
        } orElse Computation {
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
    fun `bracket release runs on cancellation in NonCancellable context`() = runTest {
        val released = AtomicBoolean(false)
        val comp = bracket(
            acquire = { "resource" },
            use = { Computation { delay(1.seconds); "done" } },
            release = {
                delay(10) // This should run even during cancellation
                released.set(true)
            },
        )

        assertFailsWith<CancellationException> {
            withTimeout(50.milliseconds) {
                comp.await()
            }
        }
        assertTrue(released.get(), "Release must run even on cancellation")
    }

    @Test
    fun `guaranteeCase reports Cancelled on cancellation`() = runTest {
        var exitCase: ExitCase? = null
        val comp = Computation<String> {
            delay(1.seconds)
            "done"
        }.guaranteeCase { case ->
            exitCase = case
        }

        assertFailsWith<CancellationException> {
            withTimeout(50.milliseconds) {
                comp.await()
            }
        }
        assertTrue(exitCase is ExitCase.Cancelled, "Should report Cancelled exit case")
    }

    @Test
    fun `ap cancels sibling branches when one fails`() = runTest {
        val siblingCancelled = AtomicBoolean(false)
        val result = runCatching {
            Async {
                lift2 { a: String, b: String -> "$a$b" }
                    .ap {
                        delay(10)
                        throw IllegalStateException("fail fast")
                    }
                    .ap {
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
        assertTrue(siblingCancelled.get(), "Sibling should be cancelled when one ap branch fails")
    }

    @Test
    fun `memoize does not cache CancellationException`() = runTest {
        val attemptCount = AtomicInteger(0)
        val comp = Computation {
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
