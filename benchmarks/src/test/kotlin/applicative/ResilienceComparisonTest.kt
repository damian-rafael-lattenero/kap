package applicative

import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

// ════════════════════════════════════════════════════════════════════════════════
// Three-Way Comparison: kap-resilience
//
// Every major kap-resilience operation compared to raw coroutine equivalents.
// Arrow equivalents are noted where they exist (arrow-fx has Resource but
// deprecated CircuitBreaker; no Schedule, timeoutRace, or raceQuorum).
// ════════════════════════════════════════════════════════════════════════════════

private suspend fun networkCall(label: String, delayMs: Long): String {
    delay(delayMs.milliseconds); return label
}

class ResilienceComparisonTest {

    // ═══════════════════════════════════════════════════════════════════════
    // RETRY WITH SCHEDULE
    // ═══════════════════════════════════════════════════════════════════════

    @Test fun `retry - raw - manual exponential backoff`() = runTest {
        var attempts = 0
        var result: String? = null
        var backoff = 10L
        for (i in 0 until 4) {
            try {
                attempts++
                if (attempts < 3) throw RuntimeException("flaky")
                result = "success"
                break
            } catch (_: RuntimeException) {
                delay(backoff.milliseconds)
                backoff *= 2
            }
        }
        assertEquals("success", result)
        assertEquals(3, attempts)
    }

    @Test fun `retry - kap - Schedule times + spaced`() = runTest {
        var attempts = 0
        val result = Async {
            Effect {
                attempts++
                if (attempts < 3) throw RuntimeException("flaky")
                "success"
            }.retry(Schedule.times<Throwable>(3) and Schedule.spaced(10.milliseconds))
        }
        assertEquals("success", result)
    }

    @Test fun `retry - kap - Schedule exponential + jittered`() = runTest {
        var attempts = 0
        val result = Async {
            Effect {
                attempts++
                if (attempts < 3) throw RuntimeException("flaky")
                "success"
            }.retry(
                Schedule.times<Throwable>(5)
                    .and(Schedule.exponential(1.milliseconds))
                    .jittered()
            )
        }
        assertEquals("success", result)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // BRACKET — acquire/use/release with guaranteed cleanup
    // ═══════════════════════════════════════════════════════════════════════

    @Test fun `bracket - raw - try-finally`() = runTest {
        val log = mutableListOf<String>()
        val conn = "db-conn"
        val result = try {
            log += "acquired:$conn"
            coroutineScope {
                val a = async { networkCall("q1", 50) }
                val b = async { networkCall("q2", 50) }
                "${a.await()}|${b.await()}"
            }
        } finally {
            log += "released:$conn"
        }
        assertTrue(result.contains("q1"))
        assertTrue(log.contains("acquired:$conn"))
        assertTrue(log.contains("released:$conn"))
    }

    @Test fun `bracket - kap`() = runTest {
        val log = mutableListOf<String>()
        val result = Async {
            bracket(
                acquire = { "db-conn".also { log += "acquired:$it" } },
                use = { conn ->
                    kap { a: String, b: String -> "$a|$b" }
                        .with { networkCall("q1", 50) }
                        .with { networkCall("q2", 50) }
                },
                release = { conn -> log += "released:$conn" },
            )
        }
        assertTrue(result.contains("q1"))
        assertTrue(log.contains("acquired:db-conn"))
        assertTrue(log.contains("released:db-conn"))
    }

    // ═══════════════════════════════════════════════════════════════════════
    // BRACKET-CASE — outcome-aware release
    // ═══════════════════════════════════════════════════════════════════════

    @Test fun `bracketCase - raw - try-catch-finally with outcome tracking`() = runTest {
        val log = mutableListOf<String>()
        val conn = "txn-conn"
        var outcome = "completed"
        val result = try {
            "success"
        } catch (e: Throwable) {
            outcome = "failed:${e.message}"; throw e
        } finally {
            log += "$conn:$outcome"
        }
        assertEquals("success", result)
        assertEquals("txn-conn:completed", log.first())
    }

    @Test fun `bracketCase - kap`() = runTest {
        val log = mutableListOf<String>()
        val result = Async {
            bracketCase(
                acquire = { "txn-conn" },
                use = { Effect { "success" } },
                release = { conn, case ->
                    when (case) {
                        is ExitCase.Completed<*> -> log += "$conn:completed"
                        is ExitCase.Failed -> log += "$conn:failed"
                        is ExitCase.Cancelled -> log += "$conn:cancelled"
                    }
                },
            )
        }
        assertEquals("success", result)
        assertEquals("txn-conn:completed", log.first())
    }

    // ═══════════════════════════════════════════════════════════════════════
    // GUARANTEE — finalizer runs regardless of outcome
    // ═══════════════════════════════════════════════════════════════════════

    @Test fun `guarantee - raw - try-finally`() = runTest {
        val log = mutableListOf<String>()
        val result = try {
            "success"
        } finally {
            log += "finalized"
        }
        assertEquals("success", result)
        assertTrue("finalized" in log)
    }

    @Test fun `guarantee - kap`() = runTest {
        val log = mutableListOf<String>()
        val result = Async {
            Effect { "success" }.guarantee { log += "finalized" }
        }
        assertEquals("success", result)
        assertTrue("finalized" in log)
    }

    @Test fun `guaranteeCase - kap`() = runTest {
        val log = mutableListOf<String>()
        val result = Async {
            Effect { "success" }.guaranteeCase { case ->
                when (case) {
                    is ExitCase.Completed<*> -> log += "completed"
                    is ExitCase.Failed -> log += "failed"
                    ExitCase.Cancelled -> log += "cancelled"
                }
            }
        }
        assertEquals("success", result)
        assertEquals("completed", log.first())
    }

    // ═══════════════════════════════════════════════════════════════════════
    // RESOURCE — safe acquisition with zip composition
    // ═══════════════════════════════════════════════════════════════════════

    @Test fun `resource zip - raw - nested try-finally`() = runTest {
        val log = mutableListOf<String>()
        val result = run {
            val db = "db".also { log += "acquired:$it" }
            try {
                val cache = "cache".also { log += "acquired:$it" }
                try {
                    "$db|$cache"
                } finally {
                    log += "released:$cache"
                }
            } finally {
                log += "released:$db"
            }
        }
        assertEquals("db|cache", result)
        assertTrue(log.contains("released:db"))
        assertTrue(log.contains("released:cache"))
    }

    @Test fun `resource zip - kap`() = runTest {
        val log = mutableListOf<String>()
        val r1 = Resource({ "db".also { log += "acquired:$it" } }, { log += "released:$it" })
        val r2 = Resource({ "cache".also { log += "acquired:$it" } }, { log += "released:$it" })
        val result = Resource.zip(r1, r2) { a, b -> "$a|$b" }.use { it }
        assertEquals("db|cache", result)
        assertTrue(log.contains("released:db"))
        assertTrue(log.contains("released:cache"))
    }

    // ═══════════════════════════════════════════════════════════════════════
    // CIRCUIT BREAKER
    // ═══════════════════════════════════════════════════════════════════════

    @Test fun `circuitBreaker - raw - manual counter + state`() = runTest {
        var failures = 0
        val maxFailures = 3
        var isOpen = false

        fun call(fn: () -> String): String {
            if (isOpen) throw IllegalStateException("circuit open")
            return try {
                val r = fn()
                failures = 0
                r
            } catch (e: Exception) {
                failures++
                if (failures >= maxFailures) isOpen = true
                throw e
            }
        }

        val result1 = call { "ok" }
        assertEquals("ok", result1)
        assertEquals(0, failures)
    }

    @Test fun `circuitBreaker - kap - closed state`() = runTest {
        val breaker = CircuitBreaker(maxFailures = 3, resetTimeout = 30_000.milliseconds)
        val result = Async { Effect { "ok" }.withCircuitBreaker(breaker) }
        assertEquals("ok", result)
    }

    @Test fun `circuitBreaker - kap - trips on failures`() = runTest {
        val breaker = CircuitBreaker(maxFailures = 2, resetTimeout = 1000.milliseconds)
        repeat(2) {
            runCatching { Async { Effect<String> { error("fail") }.withCircuitBreaker(breaker) } }
        }
        val error = runCatching { Async { Effect { "ok" }.withCircuitBreaker(breaker) } }
        assertTrue(error.isFailure)
        assertTrue(error.exceptionOrNull() is CircuitBreakerOpenException)
    }

    @Test fun `circuitBreaker - kap - half-open recovery`() = kotlinx.coroutines.runBlocking {
        val breaker = CircuitBreaker(maxFailures = 1, resetTimeout = 5.milliseconds)
        runCatching { Async { Effect<String> { error("trip") }.withCircuitBreaker(breaker) } }
        // Real sleep so monotonic clock advances past resetTimeout
        delay(20.milliseconds)
        val result = Async { Effect { "recovered" }.withCircuitBreaker(breaker) }
        assertEquals("recovered", result)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // TIMEOUT-RACE — parallel timeout with eager fallback
    // ═══════════════════════════════════════════════════════════════════════

    @Test fun `timeoutRace - raw - select with timeout`() = runTest {
        val result = coroutineScope {
            select {
                async { networkCall("primary", 30) }.onAwait { it }
                async { delay(100.milliseconds); networkCall("fallback", 80) }.onAwait { it }
            }
        }
        assertEquals("primary", result)
    }

    @Test fun `timeoutRace - kap - primary wins`() = runTest {
        val result = Async {
            Effect { networkCall("primary", 30) }
                .timeoutRace(100.milliseconds, Effect { networkCall("fallback", 80) })
        }
        assertEquals("primary", result)
    }

    @Test fun `timeoutRace - kap - fallback wins`() = runTest {
        val result = Async {
            Effect { networkCall("primary", 200) }
                .timeoutRace(50.milliseconds, Effect { networkCall("fallback", 30) })
        }
        assertEquals("fallback", result)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // RACE-QUORUM — wait for N-of-M successes (KAP-only)
    // ═══════════════════════════════════════════════════════════════════════

    @Test fun `raceQuorum - raw - Channel + supervisorScope`() = runTest {
        val required = 2
        val results = Channel<String>(required)
        val collected = supervisorScope {
            val jobs = listOf(100L, 30L, 80L, 50L, 200L).mapIndexed { i, d ->
                launch {
                    val r = networkCall("replica-${i + 1}", d)
                    results.trySend(r)
                }
            }
            val out = mutableListOf<String>()
            repeat(required) { out += results.receive() }
            jobs.forEach { it.cancel() }
            out
        }
        assertEquals(2, collected.size)
    }

    @Test fun `raceQuorum - kap - 2 of 5`() = runTest {
        val result = Async {
            raceQuorum(
                required = 2,
                Effect { networkCall("replica-1", 100) },
                Effect { networkCall("replica-2", 30) },
                Effect { networkCall("replica-3", 80) },
                Effect { networkCall("replica-4", 50) },
                Effect { networkCall("replica-5", 200) },
            )
        }
        assertEquals(2, result.size)
    }

    @Test fun `raceQuorum - kap - 3 of 5`() = runTest {
        val result = Async {
            raceQuorum(
                required = 3,
                Effect { networkCall("replica-1", 100) },
                Effect { networkCall("replica-2", 30) },
                Effect { networkCall("replica-3", 80) },
                Effect { networkCall("replica-4", 50) },
                Effect { networkCall("replica-5", 200) },
            )
        }
        assertEquals(3, result.size)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // SCHEDULE — stateful retry policy composition
    // ═══════════════════════════════════════════════════════════════════════

    @Test fun `schedule - fold accumulator`() = runTest {
        val policy = Schedule.times<Throwable>(3)
            .fold(0) { count, _ -> count + 1 }
        var attempts = 0
        val result = Async {
            Effect {
                attempts++
                if (attempts < 3) error("flaky")
                "ok"
            }.retry(policy)
        }
        assertEquals("ok", result)
        assertEquals(3, attempts)
    }

    @Test fun `schedule - composed policies`() = runTest {
        var attempts = 0
        val policy = Schedule.times<Throwable>(5)
            .and(Schedule.exponential(1.milliseconds))
            .jittered()
        val result = Async {
            Effect {
                attempts++
                if (attempts < 3) error("flaky")
                "ok"
            }.retry(policy)
                .recover { "fallback" }
        }
        assertEquals("ok", result)
    }
}
