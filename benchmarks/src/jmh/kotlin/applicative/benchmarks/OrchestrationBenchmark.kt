package applicative.benchmarks

import applicative.*
import arrow.core.Either.Left as ArrowLeft
import arrow.core.Either.Right as ArrowRight
import arrow.core.raise.either
import arrow.core.raise.zipOrAccumulate
import arrow.fx.coroutines.parZip
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.openjdk.jmh.annotations.*
import java.util.concurrent.TimeUnit

/**
 * Benchmarks comparing orchestration approaches:
 * - Raw coroutines (async/await)
 * - This library: lift+ap / liftV+apV
 * - Arrow (parZip / zipOrAccumulate)
 * - Sequential baseline (no parallelism)
 *
 * Group 1: Framework overhead — trivial compute(), measures pure overhead.
 * Group 2: Realistic latency — delay()-based, shows wall-clock compression.
 * Group 3: Multi-phase latency — checkout flow with sequential barriers.
 * Group 4: Validated error accumulation — parallel validation with apV.
 *
 * Run with: ./gradlew :benchmarks:jmh
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(1)
open class OrchestrationBenchmark {

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Trivial workload to isolate framework overhead. */
    private fun compute(n: Int): String = "v$n"

    /** Simulated network call — the delay dominates wall-clock time. */
    private suspend fun networkCall(label: String, delayMs: Long): String {
        delay(delayMs)
        return label
    }

    /** Simulated validator — returns Right on success, Left on failure. */
    private suspend fun validate(
        label: String,
        delayMs: Long,
        pass: Boolean,
    ): Either<Nel<String>, String> {
        delay(delayMs)
        return if (pass) Either.Right(label) else Either.Left("$label-failed".nel())
    }

    /** Arrow-compatible validator — returns Arrow Either. */
    private suspend fun arrowValidate(
        label: String,
        delayMs: Long,
        pass: Boolean,
    ): arrow.core.Either<String, String> {
        delay(delayMs)
        return if (pass) ArrowRight(label) else ArrowLeft("$label-failed")
    }

    // ════════════════════════════════════════════════════════════════════════
    // Group 1: Framework Overhead (trivial compute, no delay)
    // ════════════════════════════════════════════════════════════════════════

    @Benchmark
    fun rawCoroutines_overhead_arity3(): String = runBlocking {
        coroutineScope {
            val da = async { compute(1) }
            val db = async { compute(2) }
            val dc = async { compute(3) }
            "${da.await()}|${db.await()}|${dc.await()}"
        }
    }

    @Benchmark
    fun liftAp_overhead_arity3(): String = runBlocking {
        Async {
            lift3 { a: String, b: String, c: String -> "$a|$b|$c" }
                .ap { compute(1) }
                .ap { compute(2) }
                .ap { compute(3) }
        }
    }

    @Benchmark
    fun arrow_overhead_arity3(): String = runBlocking {
        parZip(
            { compute(1) },
            { compute(2) },
            { compute(3) }
        ) { a, b, c -> "$a|$b|$c" }
    }

    @Benchmark
    fun rawCoroutines_overhead_arity9(): String = runBlocking {
        coroutineScope {
            val d1 = async { compute(1) }
            val d2 = async { compute(2) }
            val d3 = async { compute(3) }
            val d4 = async { compute(4) }
            val d5 = async { compute(5) }
            val d6 = async { compute(6) }
            val d7 = async { compute(7) }
            val d8 = async { compute(8) }
            val d9 = async { compute(9) }
            listOf(d1, d2, d3, d4, d5, d6, d7, d8, d9)
                .map { it.await() }.joinToString("|")
        }
    }

    @Benchmark
    fun liftAp_overhead_arity9(): String = runBlocking {
        Async {
            lift9 { a: String, b: String, c: String, d: String, e: String,
                    f: String, g: String, h: String, i: String ->
                listOf(a, b, c, d, e, f, g, h, i).joinToString("|")
            }
                .ap { compute(1) }.ap { compute(2) }.ap { compute(3) }
                .ap { compute(4) }.ap { compute(5) }.ap { compute(6) }
                .ap { compute(7) }.ap { compute(8) }.ap { compute(9) }
        }
    }

    @Benchmark
    fun arrow_overhead_arity9(): String = runBlocking {
        parZip(
            { compute(1) }, { compute(2) }, { compute(3) },
            { compute(4) }, { compute(5) }, { compute(6) },
            { compute(7) }, { compute(8) }, { compute(9) }
        ) { a, b, c, d, e, f, g, h, i ->
            listOf(a, b, c, d, e, f, g, h, i).joinToString("|")
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Group 2: Realistic Latency — Simple Parallel (arity 5)
    //
    // 5 network calls @ 50ms each.
    //   Parallel:   ~50ms   (all 5 overlap)
    //   Sequential: ~250ms  (5 x 50ms)
    // ════════════════════════════════════════════════════════════════════════

    @Benchmark
    fun rawCoroutines_latency_arity5(): String = runBlocking {
        coroutineScope {
            val d1 = async { networkCall("user", 50) }
            val d2 = async { networkCall("cart", 50) }
            val d3 = async { networkCall("prefs", 50) }
            val d4 = async { networkCall("recs", 50) }
            val d5 = async { networkCall("promos", 50) }
            "${d1.await()}|${d2.await()}|${d3.await()}|${d4.await()}|${d5.await()}"
        }
    }

    @Benchmark
    fun liftAp_latency_arity5(): String = runBlocking {
        Async {
            lift5 { a: String, b: String, c: String, d: String, e: String ->
                "$a|$b|$c|$d|$e"
            }
                .ap { networkCall("user", 50) }
                .ap { networkCall("cart", 50) }
                .ap { networkCall("prefs", 50) }
                .ap { networkCall("recs", 50) }
                .ap { networkCall("promos", 50) }
        }
    }

    @Benchmark
    fun arrow_latency_arity5(): String = runBlocking {
        parZip(
            { networkCall("user", 50) },
            { networkCall("cart", 50) },
            { networkCall("prefs", 50) },
            { networkCall("recs", 50) },
            { networkCall("promos", 50) }
        ) { a, b, c, d, e -> "$a|$b|$c|$d|$e" }
    }

    @Benchmark
    fun sequential_latency_arity5(): String = runBlocking {
        val a = networkCall("user", 50)
        val b = networkCall("cart", 50)
        val c = networkCall("prefs", 50)
        val d = networkCall("recs", 50)
        val e = networkCall("promos", 50)
        "$a|$b|$c|$d|$e"
    }

    // ════════════════════════════════════════════════════════════════════════
    // Group 3: Realistic Latency — Multi-Phase Checkout Flow
    //
    // Phase 1: 4 calls @ 50ms each  (parallel = ~50ms)
    // Phase 2: 1 validation @ 30ms  (sequential barrier)
    // Phase 3: 3 calls @ 40ms each  (parallel = ~40ms)
    // Phase 4: 1 payment @ 60ms     (sequential barrier)
    //
    // Total parallel:   ~180ms
    // Total sequential: ~430ms  (4x50 + 30 + 3x40 + 60)
    // ════════════════════════════════════════════════════════════════════════

    @Benchmark
    fun rawCoroutines_latency_multiPhase(): String = runBlocking {
        coroutineScope {
            // Phase 1: parallel fan-out
            val dUser = async { networkCall("user", 50) }
            val dCart = async { networkCall("cart", 50) }
            val dInv = async { networkCall("inventory", 50) }
            val dAddr = async { networkCall("address", 50) }
            val user = dUser.await()
            val cart = dCart.await()
            val inv = dInv.await()
            val addr = dAddr.await()

            // Phase 2: sequential barrier
            val validated = networkCall("validated", 30)

            // Phase 3: parallel fan-out
            val dShip = async { networkCall("shipping", 40) }
            val dTax = async { networkCall("tax", 40) }
            val dDisc = async { networkCall("discount", 40) }
            val ship = dShip.await()
            val tax = dTax.await()
            val disc = dDisc.await()

            // Phase 4: sequential barrier
            val payment = networkCall("payment", 60)

            "$user|$cart|$inv|$addr|$validated|$ship|$tax|$disc|$payment"
        }
    }

    @Benchmark
    fun liftAp_latency_multiPhase(): String = runBlocking {
        Async {
            lift9 { user: String, cart: String, inv: String, addr: String,
                    validated: String,
                    ship: String, tax: String, disc: String,
                    payment: String ->
                "$user|$cart|$inv|$addr|$validated|$ship|$tax|$disc|$payment"
            }
                // Phase 1: parallel
                .ap { networkCall("user", 50) }
                .ap { networkCall("cart", 50) }
                .ap { networkCall("inventory", 50) }
                .ap { networkCall("address", 50) }
                // Phase 2: sequential barrier
                .followedBy { networkCall("validated", 30) }
                // Phase 3: parallel
                .ap { networkCall("shipping", 40) }
                .ap { networkCall("tax", 40) }
                .ap { networkCall("discount", 40) }
                // Phase 4: sequential barrier
                .followedBy { networkCall("payment", 60) }
        }
    }

    @Benchmark
    fun arrow_latency_multiPhase(): String = runBlocking {
        // Phase 1: parallel fan-out
        val phase1 = parZip(
            { networkCall("user", 50) },
            { networkCall("cart", 50) },
            { networkCall("inventory", 50) },
            { networkCall("address", 50) }
        ) { user, cart, inv, addr -> "$user|$cart|$inv|$addr" }

        // Phase 2: sequential barrier
        val validated = networkCall("validated", 30)

        // Phase 3: parallel fan-out
        val phase3 = parZip(
            { networkCall("shipping", 40) },
            { networkCall("tax", 40) },
            { networkCall("discount", 40) }
        ) { ship, tax, disc -> "$ship|$tax|$disc" }

        // Phase 4: sequential barrier
        val payment = networkCall("payment", 60)

        "$phase1|$validated|$phase3|$payment"
    }

    @Benchmark
    fun sequential_latency_multiPhase(): String = runBlocking {
        val user = networkCall("user", 50)
        val cart = networkCall("cart", 50)
        val inv = networkCall("inventory", 50)
        val addr = networkCall("address", 50)
        val validated = networkCall("validated", 30)
        val ship = networkCall("shipping", 40)
        val tax = networkCall("tax", 40)
        val disc = networkCall("discount", 40)
        val payment = networkCall("payment", 60)
        "$user|$cart|$inv|$addr|$validated|$ship|$tax|$disc|$payment"
    }

    // ════════════════════════════════════════════════════════════════════════
    // Group 4: Validated Error Accumulation
    //
    // 4 validators @ 40ms each, all passing.
    //   Parallel (liftV+apV): ~40ms
    //   Sequential:           ~160ms
    //
    // Raw coroutines have no equivalent — they can't accumulate errors
    // from parallel branches without manual Either merging (which is
    // exactly what this library provides).
    // ════════════════════════════════════════════════════════════════════════

    @Benchmark
    fun liftApV_latency_validation(): String = runBlocking {
        val result = Async {
            liftV4<String, String, String, String, String, String> { a, b, c, d ->
                "$a|$b|$c|$d"
            }
                .apV { validate("card", 40, pass = true) }
                .apV { validate("stock", 40, pass = true) }
                .apV { validate("address", 40, pass = true) }
                .apV { validate("age", 40, pass = true) }
        }
        when (result) {
            is Either.Right -> result.value
            is Either.Left -> "errors:${result.value}"
        }
    }

    @Benchmark
    fun arrow_latency_validation(): String = runBlocking {
        // Run all validations in parallel using parZip
        val results = parZip(
            { arrowValidate("card", 40, true) },
            { arrowValidate("stock", 40, true) },
            { arrowValidate("address", 40, true) },
            { arrowValidate("age", 40, true) }
        ) { a, b, c, d -> listOf(a, b, c, d) }

        // Accumulate errors using Arrow's zipOrAccumulate (non-suspend, pure accumulation)
        val result = either<arrow.core.NonEmptyList<String>, String> {
            zipOrAccumulate(
                { results[0].bind() },
                { results[1].bind() },
                { results[2].bind() },
                { results[3].bind() },
            ) { a: String, b: String, c: String, d: String -> "$a|$b|$c|$d" }
        }
        when (result) {
            is ArrowRight -> result.value
            is ArrowLeft -> "errors:${result.value}"
        }
    }

    @Benchmark
    fun sequential_validation(): String = runBlocking {
        val card = validate("card", 40, pass = true)
        val stock = validate("stock", 40, pass = true)
        val address = validate("address", 40, pass = true)
        val age = validate("age", 40, pass = true)
        val results = listOf(card, stock, address, age)
        val errors = results.filterIsInstance<Either.Left<Nel<String>>>()
        if (errors.isEmpty()) {
            results.map { (it as Either.Right).value }.joinToString("|")
        } else {
            "errors:${errors.flatMap { it.value }}"
        }
    }
}
