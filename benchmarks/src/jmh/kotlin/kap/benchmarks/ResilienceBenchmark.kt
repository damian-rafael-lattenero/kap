package kap.benchmarks

import kap.*
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.openjdk.jmh.annotations.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * JMH benchmarks for **kap-resilience** APIs.
 *
 * Covers: Schedule, retry(Schedule), bracket, bracketCase, guarantee,
 * Resource, CircuitBreaker, timeoutRace, raceQuorum.
 *
 * Every KAP benchmark has a `raw_` baseline (and `arrow_` where applicable).
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(1)
open class ResilienceBenchmark {

    private fun compute(n: Int): String = "v$n"

    private suspend fun networkCall(label: String, delayMs: Long): String {
        delay(delayMs)
        return label
    }

    // ════════════════════════════════════════════════════════════════════════
    // 1. RETRY WITH SCHEDULE — composable retry policies
    // ════════════════════════════════════════════════════════════════════════

    @Benchmark fun raw_retry_manual_3(): String = runBlocking {
        var attempts = 0
        var result: String? = null
        var backoff = 10L
        repeat(4) {
            try {
                attempts++
                result = if (attempts < 3) { delay(backoff); error("flaky") } else networkCall("service", 30)
                return@repeat
            } catch (_: Exception) {
                delay(backoff)
                backoff *= 2
            }
        }
        result ?: "fallback"
    }

    @Benchmark fun kap_retry_schedule_times(): String = runBlocking {
                Kap { networkCall("service", 30) }
            .retry(Schedule.times<Throwable>(3) and Schedule.spaced(kotlin.time.Duration.parse("10ms")))
            .recover { "fallback" }.executeGraph()
    }

    @Benchmark fun kap_retry_schedule_exponential(): String = runBlocking {
                Kap { networkCall("service", 30) }
            .retry(
                Schedule.times<Throwable>(3)
                    .and(Schedule.exponential(kotlin.time.Duration.parse("1ms")))
                    .jittered()
            )
            .recover { "fallback" }.executeGraph()
    }

    // ════════════════════════════════════════════════════════════════════════
    // 2. SCHEDULE.FOLD — stateful schedule with accumulator
    // ════════════════════════════════════════════════════════════════════════

    @Benchmark fun kap_schedule_fold(): String = runBlocking {
        val policy = Schedule.times<Throwable>(3)
            .fold(0) { count, _ -> count + 1 }
        var attempts = 0
                Kap {
            attempts++
            if (attempts < 3) error("flaky")
            "ok"
        }.retry(policy).executeGraph()
    }

    // ════════════════════════════════════════════════════════════════════════
    // 3. BRACKET — acquire/use/release with guaranteed cleanup
    // ════════════════════════════════════════════════════════════════════════

    @Benchmark fun raw_bracket_overhead(): String = runBlocking {
        val resource = "resource"
        try {
            "$resource-used"
        } finally {
            @Suppress("UNUSED_EXPRESSION") resource
        }
    }

    @Benchmark fun kap_bracket_overhead(): String = runBlocking {
                bracket(
            acquire = { "resource" },
            use = { r -> Kap { "$r-used" } },
            release = { },
        ).executeGraph()
    }

    @Benchmark fun raw_bracket_latency(): String = runBlocking {
        val conn = "conn"
        try {
            coroutineScope {
                val a = async { networkCall("$conn-q1", 50) }
                val b = async { networkCall("$conn-q2", 50) }
                val c = async { networkCall("$conn-q3", 50) }
                "${a.await()}|${b.await()}|${c.await()}"
            }
        } finally {
            @Suppress("UNUSED_EXPRESSION") conn
        }
    }

    @Benchmark fun kap_bracket_latency(): String = runBlocking {
                bracket(
            acquire = { "conn" },
            use = { conn ->
                kap { a: String, b: String, c: String -> "$a|$b|$c" }
                    .with { networkCall("$conn-q1", 50) }
                    .with { networkCall("$conn-q2", 50) }
                    .with { networkCall("$conn-q3", 50) }
            },
            release = { },
        ).executeGraph()
    }

    // ════════════════════════════════════════════════════════════════════════
    // 4. BRACKET-CASE — outcome-aware release
    // ════════════════════════════════════════════════════════════════════════

    @Benchmark fun raw_bracketCase_overhead(): String = runBlocking {
        val resource = "resource"
        var outcome = "completed"
        try {
            "$resource-used"
        } catch (e: Throwable) {
            outcome = "failed"
            throw e
        } finally {
            @Suppress("UNUSED_VARIABLE") val x = outcome
        }
    }

    @Benchmark fun kap_bracketCase_overhead(): String = runBlocking {
                bracketCase(
            acquire = { "resource" },
            use = { r -> Kap { "$r-used" } },
            release = { _, case ->
                when (case) {
                    is ExitCase.Completed<*> -> {}
                    else -> {}
                }
            },
        ).executeGraph()
    }

    @Benchmark fun kap_bracketCase_latency(): String = runBlocking {
                bracketCase(
            acquire = { networkCall("conn", 10) },
            use = { conn ->
                kap { a: String, b: String, c: String -> "$a|$b|$c" }
                    .with { networkCall("$conn-q1", 50) }
                    .with { networkCall("$conn-q2", 50) }
                    .with { networkCall("$conn-q3", 50) }
            },
            release = { _, _ -> },
        ).executeGraph()
    }

    // ════════════════════════════════════════════════════════════════════════
    // 5. GUARANTEE — finalizer runs regardless of outcome
    // ════════════════════════════════════════════════════════════════════════

    @Benchmark fun raw_guarantee_overhead(): String = runBlocking {
        try {
            compute(1)
        } finally {
            @Suppress("UNUSED_EXPRESSION") Unit
        }
    }

    @Benchmark fun kap_guarantee_overhead(): String = runBlocking {
        Kap { compute(1) }.guarantee { }.executeGraph()
    }

    @Benchmark fun kap_guaranteeCase_overhead(): String = runBlocking {
                Kap { compute(1) }.guaranteeCase { case ->
            when (case) {
                is ExitCase.Completed<*> -> {}
                is ExitCase.Failed -> {}
                ExitCase.Cancelled -> {}
            }
        }.executeGraph()
    }

    // ════════════════════════════════════════════════════════════════════════
    // 6. RESOURCE — safe acquisition/release with zip composition
    // ════════════════════════════════════════════════════════════════════════

    @Benchmark fun raw_resource_zip_overhead(): String = runBlocking {
        val db = "db"; val cache = "cache"; val http = "http"
        try {
            "$db|$cache|$http"
        } finally {
            @Suppress("UNUSED_EXPRESSION") db
        }
    }

    @Benchmark fun kap_resource_zip_overhead(): String = runBlocking {
        val r1 = Resource({ "db" }, { })
        val r2 = Resource({ "cache" }, { })
        val r3 = Resource({ "http" }, { })
        Resource.zip(r1, r2, r3) { a, b, c -> "$a|$b|$c" }.use { it }
    }

    @Benchmark fun kap_resource_zip_latency(): String = runBlocking {
        val r1 = Resource({ networkCall("db", 50) }, { })
        val r2 = Resource({ networkCall("cache", 50) }, { })
        Resource.zip(r1, r2) { a, b -> "$a|$b" }.use { it }
    }

    // ════════════════════════════════════════════════════════════════════════
    // 7. CIRCUIT BREAKER — closed, open, half-open states
    // ════════════════════════════════════════════════════════════════════════

    private val closedBreaker = CircuitBreaker(
        maxFailures = 5,
        resetTimeout = kotlin.time.Duration.parse("30s"),
    )

    @Benchmark fun raw_circuitBreaker_closed(): String = runBlocking {
        val counter = AtomicInteger(0)
        val maxFailures = 5
        if (counter.get() >= maxFailures) error("circuit open")
        try {
            val result = compute(1)
            counter.set(0)
            result
        } catch (e: Exception) {
            counter.incrementAndGet()
            throw e
        }
    }

    @Benchmark fun kap_circuitBreaker_closed_overhead(): String = runBlocking {
        Kap { compute(1) }.withCircuitBreaker(closedBreaker).executeGraph()
    }

    @Benchmark fun kap_circuitBreaker_closed_latency(): String = runBlocking {
        Kap { networkCall("service", 50) }.withCircuitBreaker(closedBreaker).executeGraph()
    }

    @Benchmark fun kap_circuitBreaker_halfOpen_probe(): String = runBlocking {
        val breaker = CircuitBreaker(
            maxFailures = 1,
            resetTimeout = kotlin.time.Duration.parse("1ms"),
        )
        runCatching {
            Kap<String> { error("trip") }.withCircuitBreaker(breaker).executeGraph()
        }
        delay(2)
        Kap { compute(1) }.withCircuitBreaker(breaker).executeGraph()
    }

    // ════════════════════════════════════════════════════════════════════════
    // 8. TIMEOUT-RACE — parallel timeout with eager fallback
    // ════════════════════════════════════════════════════════════════════════

    @Benchmark fun raw_timeoutRace_primary_wins(): String = runBlocking {
        coroutineScope {
            select {
                async { networkCall("primary", 30) }.onAwait { it }
                async { delay(100); networkCall("fallback", 80) }.onAwait { it }
            }
        }
    }

    @Benchmark fun kap_timeoutRace_primary_wins(): String = runBlocking {
                Kap { networkCall("primary", 30) }
            .timeoutRace(kotlin.time.Duration.parse("100ms"), Kap { networkCall("fallback", 80) }).executeGraph()
    }

    @Benchmark fun raw_timeoutRace_fallback_wins(): String = runBlocking {
        coroutineScope {
            select {
                async {
                    try { withTimeout(50) { networkCall("primary", 200) } }
                    catch (_: Exception) { networkCall("fallback", 30) }
                }.onAwait { it }
                async { networkCall("fallback", 30) }.onAwait { it }
            }
        }
    }

    @Benchmark fun kap_timeoutRace_fallback_wins(): String = runBlocking {
                Kap { networkCall("primary", 200) }
            .timeoutRace(kotlin.time.Duration.parse("50ms"), Kap { networkCall("fallback", 30) }).executeGraph()
    }

    @Benchmark fun kap_timeoutRace_vs_timeout(): String = runBlocking {
                Kap { networkCall("primary", 200) }
            .timeout(kotlin.time.Duration.parse("50ms"), Kap { networkCall("fallback", 30) }).executeGraph()
    }

    // ════════════════════════════════════════════════════════════════════════
    // 9. RACE-QUORUM — wait for N-of-M successes (KAP-only, no equivalent)
    // ════════════════════════════════════════════════════════════════════════

    @Benchmark fun raw_raceQuorum_2of5(): List<String> = runBlocking {
        val required = 2
        val results = Channel<String>(required)
        supervisorScope {
            val jobs = listOf(50L, 30L, 100L, 40L, 80L).mapIndexed { i, d ->
                launch {
                    val r = networkCall("replica-${i + 1}", d)
                    results.trySend(r)
                }
            }
            val collected = mutableListOf<String>()
            repeat(required) { collected += results.receive() }
            jobs.forEach { it.cancel() }
            collected
        }
    }

    @Benchmark fun kap_raceQuorum_2of5(): List<String> = runBlocking {
                raceQuorum(
            required = 2,
            Kap { networkCall("replica-1", 50) },
            Kap { networkCall("replica-2", 30) },
            Kap { networkCall("replica-3", 100) },
            Kap { networkCall("replica-4", 40) },
            Kap { networkCall("replica-5", 80) },
        ).executeGraph()
    }

    @Benchmark fun kap_raceQuorum_3of5(): List<String> = runBlocking {
                raceQuorum(
            required = 3,
            Kap { networkCall("replica-1", 50) },
            Kap { networkCall("replica-2", 30) },
            Kap { networkCall("replica-3", 100) },
            Kap { networkCall("replica-4", 40) },
            Kap { networkCall("replica-5", 80) },
        ).executeGraph()
    }

    @Benchmark fun kap_raceQuorum_2of3_overhead(): List<String> = runBlocking {
                raceQuorum(
            required = 2,
            Kap { compute(1) },
            Kap { compute(2) },
            Kap { compute(3) },
        ).executeGraph()
    }
}
