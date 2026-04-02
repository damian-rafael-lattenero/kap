package kap

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class EnhancedCombinatorsTest {

    // ════════════════════════════════════════════════════════════════════════
    // RETRY WITH shouldRetry
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `retry with shouldRetry - only retries matching exceptions`() = runTest {
        var attempts = 0
        val result = runCatching {
                        Kap<String> {
                attempts++
                if (attempts == 1) throw IOException("network error")
                if (attempts == 2) throw IllegalArgumentException("bad input")
                "ok"
            }.retry(
                maxAttempts = 5,
                shouldRetry = { it is IOException },
            ).executeGraph()
        }
        assertTrue(result.isFailure)
        assertIs<IllegalArgumentException>(result.exceptionOrNull())
        assertEquals("bad input", result.exceptionOrNull()?.message)
        assertEquals(2, attempts, "Should have attempted twice: first IOException (retried), then IAE (propagated)")
    }

    @Test
    fun `retry with shouldRetry - skips non-retryable on first attempt`() = runTest {
        var attempts = 0
        val result = runCatching {
                        Kap<String> {
                attempts++
                throw IllegalStateException("not retryable")
            }.retry(
                maxAttempts = 5,
                shouldRetry = { it is IOException },
            ).executeGraph()
        }
        assertTrue(result.isFailure)
        assertIs<IllegalStateException>(result.exceptionOrNull())
        assertEquals("not retryable", result.exceptionOrNull()?.message)
        assertEquals(1, attempts, "Should fail immediately on first attempt with non-retryable exception")
    }

    // ════════════════════════════════════════════════════════════════════════
    // RETRY onRetry CALLBACK
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `retry onRetry callback receives correct attempt number and error`() = runTest {
        data class RetryRecord(val attempt: Int, val errorMessage: String, val delay: Duration)

        val records = mutableListOf<RetryRecord>()
        var attempts = 0

        val result = runCatching {
                        Kap<String> {
                attempts++
                throw RuntimeException("fail #$attempts")
            }.retry(
                maxAttempts = 3,
                delay = 100.milliseconds,
                backoff = { it * 2 },
                onRetry = { attempt, error, nextDelay ->
                    records.add(RetryRecord(attempt, error.message!!, nextDelay))
                },
            ).executeGraph()
        }

        assertTrue(result.isFailure)
        assertEquals(3, attempts)
        // onRetry is called for attempt 1 and attempt 2 (not for the final exhausted attempt)
        assertEquals(2, records.size)

        assertEquals(1, records[0].attempt)
        assertEquals("fail #1", records[0].errorMessage)
        assertEquals(100.milliseconds, records[0].delay)

        assertEquals(2, records[1].attempt)
        assertEquals("fail #2", records[1].errorMessage)
        assertEquals(200.milliseconds, records[1].delay)
    }

    @Test
    fun `retry onRetry timing proof - callbacks fire between attempts`() = runTest {
        val callbackTimes = mutableListOf<Long>()

        val result = runCatching {
                        Kap<String> {
                throw RuntimeException("fail")
            }.retry(
                maxAttempts = 4,
                delay = 100.milliseconds,
                backoff = { it * 2 },
                onRetry = { _, _, _ ->
                    callbackTimes.add(currentTime)
                },
            ).executeGraph()
        }

        assertTrue(result.isFailure)
        assertEquals(3, callbackTimes.size)

        // onRetry fires before each delay:
        // attempt 1 fails at t=0, onRetry fires at t=0, then delay 100ms
        // attempt 2 fails at t=100, onRetry fires at t=100, then delay 200ms
        // attempt 3 fails at t=300, onRetry fires at t=300, then delay 400ms
        // attempt 4 fails at t=700 (final, no onRetry)
        assertEquals(0L, callbackTimes[0], "First onRetry at t=0")
        assertEquals(100L, callbackTimes[1], "Second onRetry at t=100")
        assertEquals(300L, callbackTimes[2], "Third onRetry at t=300")

        assertEquals(700L, currentTime, "Total time: 0 + 100 + 200 + 400 = 700ms")
    }

    @Test
    fun `retry shouldRetry and onRetry compose with backoff`() = runTest {
        data class RetryLog(val attempt: Int, val errorType: String, val delay: Duration)

        val logs = mutableListOf<RetryLog>()
        var attempts = 0

        val result = runCatching {
                        Kap<String> {
                attempts++
                when (attempts) {
                    1 -> throw IOException("network #1")
                    2 -> throw IOException("network #2")
                    3 -> throw IllegalStateException("bad state")
                    else -> "ok"
                }
            }.retry(
                maxAttempts = 5,
                delay = 50.milliseconds,
                backoff = { it * 2 },
                shouldRetry = { it is IOException },
                onRetry = { attempt, error, nextDelay ->
                    logs.add(RetryLog(attempt, error::class.simpleName!!, nextDelay))
                },
            ).executeGraph()
        }

        // Attempt 1: IOException -> shouldRetry=true, onRetry(1), delay 50ms
        // Attempt 2: IOException -> shouldRetry=true, onRetry(2), delay 100ms
        // Attempt 3: IllegalStateException -> shouldRetry=false, rethrown immediately
        assertTrue(result.isFailure)
        assertIs<IllegalStateException>(result.exceptionOrNull())
        assertEquals(3, attempts)
        assertEquals(2, logs.size)

        assertEquals(RetryLog(1, "IOException", 50.milliseconds), logs[0])
        assertEquals(RetryLog(2, "IOException", 100.milliseconds), logs[1])

        // Timing: 50ms + 100ms delays before attempt 3 threw
        assertEquals(150L, currentTime, "Total virtual time should be 150ms (50 + 100)")
    }

    // ════════════════════════════════════════════════════════════════════════
    // TIMEOUT RACE
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `timeoutRace - fallback wins when primary is slow`() = runTest {
        val result = Kap<String> {
            delay(500.milliseconds)
            "primary"
        }.timeoutRace(
            duration = 100.milliseconds,
            fallback = Kap {
                delay(30.milliseconds)
                "fallback"
            },
        ).executeGraph()

        assertEquals("fallback", result)
        assertEquals(30L, currentTime,
            "Fallback started immediately and completed at 30ms, beating the 100ms timeout")
    }

    @Test
    fun `timeoutRace - primary wins when fast enough`() = runTest {
        val result = Kap<String> {
            delay(20.milliseconds)
            "primary"
        }.timeoutRace(
            duration = 50.milliseconds,
            fallback = Kap {
                delay(100.milliseconds)
                "fallback"
            },
        ).executeGraph()

        assertEquals("primary", result)
        assertEquals(20L, currentTime,
            "Primary completed at 20ms, well within 50ms timeout, beating fallback at 100ms")
    }

    @Test
    fun `timeoutRace vs regular timeout with fallback computation - timing comparison`() = runTest {
        // Regular timeout: waits for timeout to expire, THEN runs fallback sequentially
        val startRegular = currentTime
        val regularResult = Kap<String> {
            delay(500.milliseconds)
            "slow-primary"
        }.timeout(
            duration = 100.milliseconds,
            fallback = Kap {
                delay(50.milliseconds)
                "regular-fallback"
            },
        ).executeGraph()
        val regularTime = currentTime - startRegular

        assertEquals("regular-fallback", regularResult)
        assertEquals(150L, regularTime,
            "Regular timeout: 100ms timeout expiry + 50ms fallback = 150ms total")

        // timeoutRace: fallback runs in parallel from the start
        val startRace = currentTime
        val raceResult = Kap<String> {
            delay(500.milliseconds)
            "slow-primary"
        }.timeoutRace(
            duration = 100.milliseconds,
            fallback = Kap {
                delay(50.milliseconds)
                "race-fallback"
            },
        ).executeGraph()
        val raceTime = currentTime - startRace

        assertEquals("race-fallback", raceResult)
        assertEquals(50L, raceTime,
            "timeoutRace: fallback started immediately and completed at 50ms, beating 100ms timeout")

        // Prove timeoutRace is faster
        assertTrue(raceTime < regularTime,
            "timeoutRace ($raceTime ms) should be faster than regular timeout ($regularTime ms)")
    }

    // ════════════════════════════════════════════════════════════════════════
    // COMPOSITION: INSIDE with CHAINS
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `timeoutRace inside with chain - parallel branches with different strategies`() = runTest {
        val timeoutRaceBranch = Kap<String> {
            delay(500.milliseconds)
            "slow-service"
        }.timeoutRace(
            duration = 100.milliseconds,
            fallback = Kap {
                delay(30.milliseconds)
                "cached"
            },
        )

        var retryAttempts = 0
        val retryBranch = Kap<String> {
            retryAttempts++
            if (retryAttempts < 3) throw RuntimeException("transient")
            delay(10.milliseconds)
            "retried-ok"
        }.retry(maxAttempts = 3)

        val result = kap { a: String, b: String -> "$a|$b" }
            .with(timeoutRaceBranch)
            .with(retryBranch).executeGraph()

        assertEquals("cached|retried-ok", result)
        assertEquals(3, retryAttempts)
        // Both branches run in parallel.
        // timeoutRace branch: fallback wins at 30ms
        // retry branch: attempts 1, 2 fail instantly, attempt 3 succeeds after 10ms delay
        // Parallel max = 30ms
        assertEquals(30L, currentTime,
            "Parallel branches: max(30ms timeoutRace fallback, ~10ms retry) = 30ms")
    }

    @Test
    fun `retry with shouldRetry inside with chain - selective retry in parallel`() = runTest {
        var ioAttempts = 0
        val ioBranch = Kap<String> {
            ioAttempts++
            if (ioAttempts < 3) throw IOException("network #$ioAttempts")
            delay(10.milliseconds)
            "io-ok"
        }.retry(
            maxAttempts = 5,
            shouldRetry = { it is IOException },
        )

        var stateAttempts = 0
        val stateBranch = Kap<String> {
            stateAttempts++
            if (stateAttempts < 2) throw IllegalStateException("init #$stateAttempts")
            delay(20.milliseconds)
            "state-ok"
        }.retry(
            maxAttempts = 5,
            shouldRetry = { it is IllegalStateException },
        )

        val result = kap { a: String, b: String -> "$a|$b" }
            .with(ioBranch)
            .with(stateBranch).executeGraph()

        assertEquals("io-ok|state-ok", result)
        assertEquals(3, ioAttempts, "IO branch should have taken 3 attempts")
        assertEquals(2, stateAttempts, "State branch should have taken 2 attempts")
        // Both branches run in parallel.
        // ioBranch: 2 instant failures + 10ms success = 10ms
        // stateBranch: 1 instant failure + 20ms success = 20ms
        // Parallel max = 20ms
        assertEquals(20L, currentTime,
            "Parallel branches: max(10ms io-retry, 20ms state-retry) = 20ms")
    }
}
