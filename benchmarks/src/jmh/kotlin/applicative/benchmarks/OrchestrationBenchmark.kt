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
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.openjdk.jmh.annotations.*
import org.openjdk.jmh.infra.Blackhole
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
    ): Either<NonEmptyList<String>, String> {
        delay(delayMs)
        return if (pass) Either.Right(label) else Either.Left("$label-failed".toNonEmptyList())
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
        val errors = results.filterIsInstance<Either.Left<NonEmptyList<String>>>()
        if (errors.isEmpty()) {
            results.map { (it as Either.Right).value }.joinToString("|")
        } else {
            "errors:${errors.flatMap { it.value }}"
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Group 5: liftA — Haskell-style applicative lifting (parZip equivalent)
    //
    // Compares liftA3/liftA5 vs lift+ap vs Arrow parZip.
    // These accept suspend lambdas directly — no Computation wrapping.
    // ════════════════════════════════════════════════════════════════════════

    @Benchmark
    fun liftA3_overhead_arity3(): String = runBlocking {
        Async {
            liftA3(
                { compute(1) },
                { compute(2) },
                { compute(3) },
            ) { a, b, c -> "$a|$b|$c" }
        }
    }

    @Benchmark
    fun liftA5_overhead_arity5(): String = runBlocking {
        Async {
            liftA5(
                { compute(1) },
                { compute(2) },
                { compute(3) },
                { compute(4) },
                { compute(5) },
            ) { a, b, c, d, e -> "$a|$b|$c|$d|$e" }
        }
    }

    @Benchmark
    fun liftA5_latency_arity5(): String = runBlocking {
        Async {
            liftA5(
                { networkCall("user", 50) },
                { networkCall("cart", 50) },
                { networkCall("prefs", 50) },
                { networkCall("recs", 50) },
                { networkCall("promos", 50) },
            ) { a, b, c, d, e -> "$a|$b|$c|$d|$e" }
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Group 6: Resilience stack composition
    //
    // Measures the overhead of retry + timeout + recover chains.
    // ════════════════════════════════════════════════════════════════════════

    @Benchmark
    fun resilience_retry_succeed_first(): String = runBlocking {
        Async {
            Computation { networkCall("service", 30) }
                .retry(Schedule.recurs<Throwable>(3) and Schedule.spaced(kotlin.time.Duration.parse("10ms")))
                .recover { "fallback" }
        }
    }

    @Benchmark
    fun resilience_timeout_with_default(): String = runBlocking {
        Async {
            Computation { networkCall("slow", 200) }
                .timeout(kotlin.time.Duration.parse("100ms"), default = "cached")
        }
    }

    @Benchmark
    fun resilience_race_two(): String = runBlocking {
        Async {
            race(
                Computation { networkCall("primary", 100) },
                Computation { networkCall("replica", 50) },
            )
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Group 7: Traverse with bounded concurrency
    //
    // 20 items @ 30ms each, concurrency=5 → ~120ms (4 batches)
    // ════════════════════════════════════════════════════════════════════════

    @Benchmark
    fun traverse_unbounded_20(): String = runBlocking {
        Async {
            (1..20).toList().traverse { i ->
                Computation { networkCall("item-$i", 30) }
            }.map { it.joinToString("|") }
        }
    }

    @Benchmark
    fun traverse_bounded_20_concurrency5(): String = runBlocking {
        Async {
            (1..20).toList().traverse(concurrency = 5) { i ->
                Computation { networkCall("item-$i", 30) }
            }.map { it.joinToString("|") }
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Group 8: High-arity overhead (lift15)
    //
    // Measures curried chain overhead at high arities.
    // ════════════════════════════════════════════════════════════════════════

    @Benchmark
    fun liftAp_overhead_arity15(): String = runBlocking {
        Async {
            lift15 { a: String, b: String, c: String, d: String, e: String,
                     f: String, g: String, h: String, i: String, j: String,
                     k: String, l: String, m: String, n: String, o: String ->
                listOf(a, b, c, d, e, f, g, h, i, j, k, l, m, n, o).joinToString("|")
            }
                .ap { compute(1) }.ap { compute(2) }.ap { compute(3) }
                .ap { compute(4) }.ap { compute(5) }.ap { compute(6) }
                .ap { compute(7) }.ap { compute(8) }.ap { compute(9) }
                .ap { compute(10) }.ap { compute(11) }.ap { compute(12) }
                .ap { compute(13) }.ap { compute(14) }.ap { compute(15) }
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Group 9: Memoize — cache hit vs miss
    // ════════════════════════════════════════════════════════════════════════

    @Benchmark
    fun memoize_cold_miss(): String = runBlocking {
        // Each iteration creates a fresh memoized computation — always a miss
        val m = Computation { compute(1) }.memoize()
        Async { m }
    }

    @Benchmark
    fun memoize_warm_hit(): String = runBlocking {
        // Pre-warm, then measure cache hit
        Async { memoizedForBenchmark }
    }

    private val memoizedForBenchmark: Computation<String> = run {
        val m = Computation { compute(1) }.memoize()
        runBlocking { Async { m } } // warm the cache
        m
    }

    // ════════════════════════════════════════════════════════════════════════
    // Group 10: MemoizeOnSuccess — transient failure retry vs permanent cache
    // ════════════════════════════════════════════════════════════════════════

    @Benchmark
    fun memoizeOnSuccess_cold_miss(): String = runBlocking {
        val m = Computation { compute(1) }.memoizeOnSuccess()
        Async { m }
    }

    @Benchmark
    fun memoizeOnSuccess_warm_hit(): String = runBlocking {
        Async { memoizedOnSuccessForBenchmark }
    }

    private val memoizedOnSuccessForBenchmark: Computation<String> = run {
        val m = Computation { compute(1) }.memoizeOnSuccess()
        runBlocking { Async { m } }
        m
    }

    // ════════════════════════════════════════════════════════════════════════
    // Group 11: Bracket / Resource overhead
    // ════════════════════════════════════════════════════════════════════════

    @Benchmark
    fun bracket_overhead(): String = runBlocking {
        Async {
            bracket(
                acquire = { "resource" },
                use = { r -> Computation { "$r-used" } },
                release = { },
            )
        }
    }

    @Benchmark
    fun bracket_latency_with_parallel(): String = runBlocking {
        Async {
            bracket(
                acquire = { "conn" },
                use = { conn ->
                    lift3 { a: String, b: String, c: String -> "$a|$b|$c" }
                        .ap { networkCall("$conn-q1", 50) }
                        .ap { networkCall("$conn-q2", 50) }
                        .ap { networkCall("$conn-q3", 50) }
                },
                release = { },
            )
        }
    }

    @Benchmark
    fun resource_zip_overhead(): String = runBlocking {
        val r1 = Resource({ "db" }, { })
        val r2 = Resource({ "cache" }, { })
        val r3 = Resource({ "http" }, { })
        Resource.zip(r1, r2, r3) { a, b, c -> "$a|$b|$c" }.use { it }
    }

    @Benchmark
    fun resource_zip_latency(): String = runBlocking {
        val r1 = Resource({ networkCall("db", 50) }, { })
        val r2 = Resource({ networkCall("cache", 50) }, { })
        Resource.zip(r1, r2) { a, b -> "$a|$b" }.use { it }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Group 12: CircuitBreaker overhead
    // ════════════════════════════════════════════════════════════════════════

    private val closedBreaker = CircuitBreaker(
        maxFailures = 5,
        resetTimeout = kotlin.time.Duration.parse("30s"),
    )

    @Benchmark
    fun circuitBreaker_closed_overhead(): String = runBlocking {
        Async {
            Computation { compute(1) }.withCircuitBreaker(closedBreaker)
        }
    }

    @Benchmark
    fun circuitBreaker_closed_latency(): String = runBlocking {
        Async {
            Computation { networkCall("service", 50) }.withCircuitBreaker(closedBreaker)
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Group 13: Validated apV/followedByV with mixed results
    // ════════════════════════════════════════════════════════════════════════

    @Benchmark
    fun apV_latency_all_pass(): String = runBlocking {
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
    fun apV_latency_all_fail(): String = runBlocking {
        val result = Async {
            liftV4<String, String, String, String, String, String> { a, b, c, d ->
                "$a|$b|$c|$d"
            }
                .apV { validate("card", 40, pass = false) }
                .apV { validate("stock", 40, pass = false) }
                .apV { validate("address", 40, pass = false) }
                .apV { validate("age", 40, pass = false) }
        }
        when (result) {
            is Either.Right -> result.value
            is Either.Left -> "errors:${result.value.size}"
        }
    }

    @Benchmark
    fun zipV_latency_mixed_results(): String = runBlocking {
        val result = Async {
            zipV(
                { validate("card", 40, pass = true) },
                { validate("stock", 40, pass = false) },
                { validate("address", 40, pass = true) },
                { validate("age", 40, pass = false) },
            ) { a, b, c, d -> "$a|$b|$c|$d" }
        }
        when (result) {
            is Either.Right -> result.value
            is Either.Left -> "errors:${result.value.size}"
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Group 14: TraverseV — parallel validation over collections
    // ════════════════════════════════════════════════════════════════════════

    @Benchmark
    fun traverseV_10_items_all_pass(): String = runBlocking {
        val result = Async {
            (1..10).toList().traverseV { i ->
                Computation<Either<NonEmptyList<String>, String>> { validate("item-$i", 30, pass = true) }
            }
        }
        when (result) {
            is Either.Right -> "ok:${result.value.size}"
            is Either.Left -> "errors:${result.value.size}"
        }
    }

    @Benchmark
    fun traverseV_10_items_half_fail(): String = runBlocking {
        val result = Async {
            (1..10).toList().traverseV { i ->
                Computation<Either<NonEmptyList<String>, String>> { validate("item-$i", 30, pass = i % 2 == 0) }
            }
        }
        when (result) {
            is Either.Right -> "ok:${result.value.size}"
            is Either.Left -> "errors:${result.value.size}"
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Group 15: computation {} builder overhead
    // ════════════════════════════════════════════════════════════════════════

    @Benchmark
    fun computation_builder_overhead(): String = runBlocking {
        Async {
            computation {
                val a = bind { compute(1) }
                val b = bind { compute(2) }
                val c = bind { compute(3) }
                "$a|$b|$c"
            }
        }
    }

    @Benchmark
    fun computation_builder_latency(): String = runBlocking {
        Async {
            computation {
                val a = bind { networkCall("user", 50) }
                val b = bind { networkCall("cart-${a.length}", 50) }
                val c = bind { networkCall("recs-${b.length}", 50) }
                "$a|$b|$c"
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Group 16: raceEither — heterogeneous racing
    // ════════════════════════════════════════════════════════════════════════

    @Benchmark
    fun raceEither_latency(): String = runBlocking {
        val result = Async {
            raceEither(
                Computation { networkCall("cache", 30) },
                Computation { networkCall("network", 100) },
            )
        }
        when (result) {
            is Either.Left -> "cache:${result.value}"
            is Either.Right -> "network:${result.value}"
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Group 17: timeoutRace — parallel timeout with eager fallback
    // ════════════════════════════════════════════════════════════════════════

    @Benchmark
    fun timeoutRace_primary_wins(): String = runBlocking {
        Async {
            Computation { networkCall("primary", 30) }
                .timeoutRace(kotlin.time.Duration.parse("100ms"), Computation { networkCall("fallback", 80) })
        }
    }

    @Benchmark
    fun timeoutRace_fallback_wins(): String = runBlocking {
        Async {
            Computation { networkCall("primary", 200) }
                .timeoutRace(kotlin.time.Duration.parse("50ms"), Computation { networkCall("fallback", 30) })
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Group 18: Schedule.fold / collect — stateful schedule overhead
    // ════════════════════════════════════════════════════════════════════════

    @Benchmark
    fun schedule_fold_overhead(): String = runBlocking {
        val policy = Schedule.recurs<Throwable>(3)
            .fold(0) { count, _ -> count + 1 }
        var attempts = 0
        Async {
            Computation {
                attempts++
                if (attempts < 3) error("flaky")
                "ok"
            }.retry(policy)
        }
    }

    @Benchmark
    fun schedule_jittered_exponential(): String = runBlocking {
        Async {
            Computation { networkCall("service", 30) }
                .retry(
                    Schedule.recurs<Throwable>(3)
                        .and(Schedule.exponential(kotlin.time.Duration.parse("1ms")))
                        .jittered()
                )
                .recover { "fallback" }
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Group 19: bracketCase — outcome-aware release overhead
    // ════════════════════════════════════════════════════════════════════════

    @Benchmark
    fun bracketCase_overhead(): String = runBlocking {
        Async {
            bracketCase(
                acquire = { "resource" },
                use = { r -> Computation { "$r-used" } },
                release = { _, case ->
                    when (case) {
                        is ExitCase.Completed<*> -> {} // commit
                        else -> {} // rollback
                    }
                },
            )
        }
    }

    @Benchmark
    fun bracketCase_latency_with_parallel(): String = runBlocking {
        Async {
            bracketCase(
                acquire = { networkCall("conn", 10) },
                use = { conn ->
                    lift3 { a: String, b: String, c: String -> "$a|$b|$c" }
                        .ap { networkCall("$conn-q1", 50) }
                        .ap { networkCall("$conn-q2", 50) }
                        .ap { networkCall("$conn-q3", 50) }
                },
                release = { _, _ -> },
            )
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Group 20: CircuitBreaker half-open to close transition
    // ════════════════════════════════════════════════════════════════════════

    @Benchmark
    fun circuitBreaker_halfOpen_probe_success(): String = runBlocking {
        // Create a breaker, trip it, then wait for half-open and probe
        val breaker = CircuitBreaker(
            maxFailures = 1,
            resetTimeout = kotlin.time.Duration.parse("1ms"),
        )
        // Trip the breaker
        runCatching {
            Async { Computation<String> { error("trip") }.withCircuitBreaker(breaker) }
        }
        // Wait for half-open
        delay(2)
        // Probe succeeds → closes
        Async { Computation { compute(1) }.withCircuitBreaker(breaker) }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Group 21: orElse chain performance
    // ════════════════════════════════════════════════════════════════════════

    @Benchmark
    fun orElse_chain_3_overhead(): String = runBlocking {
        Async {
            Computation<String> { error("fail-1") }
                .orElse(Computation { error("fail-2") })
                .orElse(Computation { compute(3) })
        }
    }

    @Benchmark
    fun orElse_chain_3_latency(): String = runBlocking {
        Async {
            Computation<String> { delay(10); error("fail-1") }
                .orElse(Computation { delay(10); error("fail-2") })
                .orElse(Computation { networkCall("ok", 10) })
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Group 22: firstSuccessOf performance
    // ════════════════════════════════════════════════════════════════════════

    @Benchmark
    fun firstSuccessOf_5_third_wins(): String = runBlocking {
        Async {
            firstSuccessOf(
                Computation { error("fail-1") },
                Computation { error("fail-2") },
                Computation { compute(3) },
                Computation { compute(4) },
                Computation { compute(5) },
            )
        }
    }

    @Benchmark
    fun firstSuccessOf_5_latency(): String = runBlocking {
        Async {
            firstSuccessOf(
                Computation { delay(10); error("fail-1") },
                Computation { delay(10); error("fail-2") },
                Computation { networkCall("ok", 10) },
                Computation { networkCall("unused", 10) },
                Computation { networkCall("unused", 10) },
            )
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Group 23: timeoutRace vs timeout+fallback comparison
    // ════════════════════════════════════════════════════════════════════════

    @Benchmark
    fun timeoutRace_parallel_fallback(): String = runBlocking {
        Async {
            Computation { networkCall("primary", 200) }
                .timeoutRace(kotlin.time.Duration.parse("50ms"), Computation { networkCall("fallback", 30) })
        }
    }

    @Benchmark
    fun timeout_sequential_fallback(): String = runBlocking {
        Async {
            Computation { networkCall("primary", 200) }
                .timeout(kotlin.time.Duration.parse("50ms"), Computation { networkCall("fallback", 30) })
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Group 24: computation{bind{}} vs flatMap chain comparison
    // ════════════════════════════════════════════════════════════════════════

    @Benchmark
    fun flatMap_chain_3_overhead(): String = runBlocking {
        Async {
            pure(compute(1)).flatMap { a ->
                pure(compute(2)).flatMap { b ->
                    pure(compute(3)).map { c -> "$a|$b|$c" }
                }
            }
        }
    }

    @Benchmark
    fun flatMap_chain_3_latency(): String = runBlocking {
        Async {
            Computation { networkCall("a", 50) }.flatMap { a ->
                Computation { networkCall("b-${a.length}", 50) }.flatMap { b ->
                    Computation { networkCall("c-${b.length}", 50) }.map { c -> "$a|$b|$c" }
                }
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Group 25: traverseV with bounded concurrency
    // ════════════════════════════════════════════════════════════════════════

    @Benchmark
    fun traverseV_bounded_20_concurrency5_all_pass(): String = runBlocking {
        val result = Async {
            (1..20).toList().traverseV(concurrency = 5) { i ->
                Computation<Either<NonEmptyList<String>, String>> { validate("item-$i", 30, pass = true) }
            }
        }
        when (result) {
            is Either.Right -> "ok:${result.value.size}"
            is Either.Left -> "errors:${result.value.size}"
        }
    }

    @Benchmark
    fun traverseV_bounded_20_concurrency5_half_fail(): String = runBlocking {
        val result = Async {
            (1..20).toList().traverseV(concurrency = 5) { i ->
                Computation<Either<NonEmptyList<String>, String>> { validate("item-$i", 30, pass = i % 2 == 0) }
            }
        }
        when (result) {
            is Either.Right -> "ok:${result.value.size}"
            is Either.Left -> "errors:${result.value.size}"
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Group 26: memoizeOnSuccess — failure retry path
    // ════════════════════════════════════════════════════════════════════════

    @Benchmark
    fun memoizeOnSuccess_failure_then_success(): String = runBlocking {
        var calls = 0
        val m = Computation {
            calls++
            if (calls == 1) error("transient")
            compute(1)
        }.memoizeOnSuccess()

        // First call fails
        runCatching { Async { m } }
        // Second call retries and succeeds
        Async { m }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Group 27: raceEither vs race overhead comparison
    // ════════════════════════════════════════════════════════════════════════

    @Benchmark
    fun race_homogeneous_overhead(): String = runBlocking {
        Async {
            race(
                Computation { compute(1) },
                Computation { compute(2) },
            )
        }
    }

    @Benchmark
    fun raceEither_heterogeneous_overhead(): String = runBlocking {
        val result = Async {
            raceEither(
                Computation { compute(1) },
                Computation { 42 },
            )
        }
        when (result) {
            is Either.Left -> result.value
            is Either.Right -> "n:${result.value}"
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Group 28: ensureV — validated guard overhead
    // ════════════════════════════════════════════════════════════════════════

    @Benchmark
    fun ensureV_pass(): String = runBlocking {
        val result = Async {
            Computation { 25 }
                .ensureV({ "too young" }) { it >= 18 }
        }
        when (result) {
            is Either.Right -> "ok:${result.value}"
            is Either.Left -> "fail:${result.value}"
        }
    }

    @Benchmark
    fun ensureV_fail(): String = runBlocking {
        val result = Async {
            Computation { 15 }
                .ensureV({ "too young" }) { it >= 18 }
        }
        when (result) {
            is Either.Right -> "ok:${result.value}"
            is Either.Left -> "fail:${result.value.head}"
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Group 29: traverseSettled — collect ALL results without cancellation
    // ════════════════════════════════════════════════════════════════════════

    @Benchmark
    fun traverseSettled_10_all_pass(): List<Result<String>> = runBlocking {
        Async {
            (1..10).toList().traverseSettled { i ->
                Computation { networkCall("item-$i", 30) }
            }
        }
    }

    @Benchmark
    fun traverseSettled_10_half_fail(): List<Result<String>> = runBlocking {
        Async {
            (1..10).toList().traverseSettled { i ->
                Computation {
                    delay(30)
                    if (i % 2 == 0) throw RuntimeException("fail-$i")
                    "ok-$i"
                }
            }
        }
    }

    @Benchmark
    fun traverseSettled_bounded_20_concurrency5(): List<Result<String>> = runBlocking {
        Async {
            (1..20).toList().traverseSettled(5) { i ->
                Computation { networkCall("item-$i", 30) }
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Group 30: settled() — partial failure tolerance in ap chains
    // ════════════════════════════════════════════════════════════════════════

    @Benchmark
    fun settled_success_overhead(bh: Blackhole) = runBlocking {
        bh.consume(Async {
            Computation { compute(1) }.settled()
        })
    }

    @Benchmark
    fun settled_failure_no_cancel(): String = runBlocking {
        data class R(val a: Result<String>, val b: String, val c: String)
        val result = Async {
            lift3(::R)
                .ap(Computation<String> { throw RuntimeException("down") }.settled())
                .ap { networkCall("b", 50) }
                .ap { networkCall("c", 50) }
        }
        "${result.a.isFailure}|${result.b}|${result.c}"
    }

    // ════════════════════════════════════════════════════════════════════════
    // Group 31: traverseSettled vs raw supervisorScope comparison
    // ════════════════════════════════════════════════════════════════════════

    @Benchmark
    fun traverseSettled_vs_raw_settled_10(): List<Result<String>> = runBlocking {
        Async {
            (1..10).toList().traverseSettled { i ->
                Computation { networkCall("item-$i", 30) }
            }
        }
    }

    @Benchmark
    fun raw_supervisorScope_settled_10(): List<Result<String>> = runBlocking {
        kotlinx.coroutines.supervisorScope {
            (1..10).map { i ->
                async { runCatching { networkCall("item-$i", 30) } }
            }.map { it.await() }
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Group 32: raceQuorum benchmarks
    // ════════════════════════════════════════════════════════════════════════

    @Benchmark
    fun raceQuorum_2_of_5_latency(): List<String> = runBlocking {
        Async {
            raceQuorum(
                required = 2,
                Computation { networkCall("replica-1", 50) },
                Computation { networkCall("replica-2", 30) },
                Computation { networkCall("replica-3", 100) },
                Computation { networkCall("replica-4", 40) },
                Computation { networkCall("replica-5", 80) },
            )
        }
    }

    @Benchmark
    fun raceQuorum_3_of_5_latency(): List<String> = runBlocking {
        Async {
            raceQuorum(
                required = 3,
                Computation { networkCall("replica-1", 50) },
                Computation { networkCall("replica-2", 30) },
                Computation { networkCall("replica-3", 100) },
                Computation { networkCall("replica-4", 40) },
                Computation { networkCall("replica-5", 80) },
            )
        }
    }

    @Benchmark
    fun raceQuorum_2_of_3_overhead(): List<String> = runBlocking {
        Async {
            raceQuorum(
                required = 2,
                Computation { compute(1) },
                Computation { compute(2) },
                Computation { compute(3) },
            )
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Group 33: Flow integration benchmarks
    // ════════════════════════════════════════════════════════════════════════

    @Benchmark
    fun flow_mapComputation_sequential_10(): List<String> = runBlocking {
        kotlinx.coroutines.flow.flowOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
            .mapComputation { n -> Computation { networkCall("item-$n", 30) } }
            .toList()
    }

    @Benchmark
    fun flow_mapComputation_concurrent5_10(): List<String> = runBlocking {
        kotlinx.coroutines.flow.flowOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
            .mapComputation(concurrency = 5) { n -> Computation { networkCall("item-$n", 30) } }
            .toList()
    }

    @Benchmark
    fun flow_mapComputationOrdered_concurrent5_10(): List<String> = runBlocking {
        kotlinx.coroutines.flow.flowOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
            .mapComputationOrdered(concurrency = 5) { n -> Computation { networkCall("item-$n", 30) } }
            .toList()
    }

    @Benchmark
    fun flow_mapComputation_overhead_10(): List<String> = runBlocking {
        kotlinx.coroutines.flow.flowOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
            .mapComputation(concurrency = 5) { n -> Computation { compute(n) } }
            .toList()
    }

    @Benchmark
    fun flow_mapComputationOrdered_overhead_10(): List<String> = runBlocking {
        kotlinx.coroutines.flow.flowOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
            .mapComputationOrdered(concurrency = 5) { n -> Computation { compute(n) } }
            .toList()
    }

    @Benchmark
    fun flow_filterComputation_10(): List<Int> = runBlocking {
        kotlinx.coroutines.flow.flowOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
            .filterComputation { n -> Computation { n % 2 == 0 } }
            .toList()
    }
}
