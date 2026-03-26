package applicative.benchmarks

import applicative.*
import arrow.fx.coroutines.parZip
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.andThenMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.supervisorScope
import org.openjdk.jmh.annotations.*
import org.openjdk.jmh.infra.Blackhole
import java.util.concurrent.TimeUnit

/**
 * JMH benchmarks for **kap-core** APIs.
 *
 * Covers: kap+with, then, combine, zip, traverse, sequence,
 * race, timeout, recover, memoize, computation{}, settled, Flow interop.
 *
 * Every KAP benchmark has a `raw_` baseline (and `arrow_` where applicable).
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
open class CoreBenchmark {

    private fun compute(n: Int): String = "v$n"

    private suspend fun networkCall(label: String, delayMs: Long): String {
        delay(delayMs)
        return label
    }

    // ════════════════════════════════════════════════════════════════════════
    // 1. FRAMEWORK OVERHEAD — trivial compute, arity 3 and 9
    // ════════════════════════════════════════════════════════════════════════

    @Benchmark fun raw_overhead_arity3(): String = runBlocking {
        coroutineScope {
            val a = async { compute(1) }; val b = async { compute(2) }; val c = async { compute(3) }
            "${a.await()}|${b.await()}|${c.await()}"
        }
    }

    @Benchmark fun kap_overhead_arity3(): String = runBlocking {
        Async {
            kap { a: String, b: String, c: String -> "$a|$b|$c" }
                .with { compute(1) }.with { compute(2) }.with { compute(3) }
        }
    }

    @Benchmark fun arrow_overhead_arity3(): String = runBlocking {
        parZip({ compute(1) }, { compute(2) }, { compute(3) }) { a, b, c -> "$a|$b|$c" }
    }

    @Benchmark fun raw_overhead_arity9(): String = runBlocking {
        coroutineScope {
            (1..9).map { async { compute(it) } }.map { it.await() }.joinToString("|")
        }
    }

    @Benchmark fun kap_overhead_arity9(): String = runBlocking {
        Async {
            kap { a: String, b: String, c: String, d: String, e: String,
                    f: String, g: String, h: String, i: String ->
                listOf(a, b, c, d, e, f, g, h, i).joinToString("|")
            }.with { compute(1) }.with { compute(2) }.with { compute(3) }
             .with { compute(4) }.with { compute(5) }.with { compute(6) }
             .with { compute(7) }.with { compute(8) }.with { compute(9) }
        }
    }

    @Benchmark fun arrow_overhead_arity9(): String = runBlocking {
        parZip(
            { compute(1) }, { compute(2) }, { compute(3) },
            { compute(4) }, { compute(5) }, { compute(6) },
            { compute(7) }, { compute(8) }, { compute(9) }
        ) { a, b, c, d, e, f, g, h, i ->
            listOf(a, b, c, d, e, f, g, h, i).joinToString("|")
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // 2. REALISTIC LATENCY — arity 5, 50ms per call
    // ════════════════════════════════════════════════════════════════════════

    @Benchmark fun raw_latency_arity5(): String = runBlocking {
        coroutineScope {
            val d1 = async { networkCall("user", 50) }
            val d2 = async { networkCall("cart", 50) }
            val d3 = async { networkCall("prefs", 50) }
            val d4 = async { networkCall("recs", 50) }
            val d5 = async { networkCall("promos", 50) }
            "${d1.await()}|${d2.await()}|${d3.await()}|${d4.await()}|${d5.await()}"
        }
    }

    @Benchmark fun kap_latency_arity5(): String = runBlocking {
        Async {
            kap { a: String, b: String, c: String, d: String, e: String -> "$a|$b|$c|$d|$e" }
                .with { networkCall("user", 50) }
                .with { networkCall("cart", 50) }
                .with { networkCall("prefs", 50) }
                .with { networkCall("recs", 50) }
                .with { networkCall("promos", 50) }
        }
    }

    @Benchmark fun arrow_latency_arity5(): String = runBlocking {
        parZip(
            { networkCall("user", 50) }, { networkCall("cart", 50) },
            { networkCall("prefs", 50) }, { networkCall("recs", 50) },
            { networkCall("promos", 50) }
        ) { a, b, c, d, e -> "$a|$b|$c|$d|$e" }
    }

    @Benchmark fun sequential_latency_arity5(): String = runBlocking {
        val a = networkCall("user", 50); val b = networkCall("cart", 50)
        val c = networkCall("prefs", 50); val d = networkCall("recs", 50)
        val e = networkCall("promos", 50)
        "$a|$b|$c|$d|$e"
    }

    // ════════════════════════════════════════════════════════════════════════
    // 3. MULTI-PHASE CHECKOUT — 4 phases, barriers between parallel fan-outs
    // ════════════════════════════════════════════════════════════════════════

    @Benchmark fun raw_latency_multiPhase(): String = runBlocking {
        coroutineScope {
            val dUser = async { networkCall("user", 50) }
            val dCart = async { networkCall("cart", 50) }
            val dInv = async { networkCall("inventory", 50) }
            val dAddr = async { networkCall("address", 50) }
            val user = dUser.await(); val cart = dCart.await()
            val inv = dInv.await(); val addr = dAddr.await()
            val validated = networkCall("validated", 30)
            val dShip = async { networkCall("shipping", 40) }
            val dTax = async { networkCall("tax", 40) }
            val dDisc = async { networkCall("discount", 40) }
            val ship = dShip.await(); val tax = dTax.await(); val disc = dDisc.await()
            val payment = networkCall("payment", 60)
            "$user|$cart|$inv|$addr|$validated|$ship|$tax|$disc|$payment"
        }
    }

    @Benchmark fun kap_latency_multiPhase(): String = runBlocking {
        Async {
            kap { user: String, cart: String, inv: String, addr: String,
                    validated: String, ship: String, tax: String, disc: String,
                    payment: String ->
                "$user|$cart|$inv|$addr|$validated|$ship|$tax|$disc|$payment"
            }
                .with { networkCall("user", 50) }
                .with { networkCall("cart", 50) }
                .with { networkCall("inventory", 50) }
                .with { networkCall("address", 50) }
                .then { networkCall("validated", 30) }
                .with { networkCall("shipping", 40) }
                .with { networkCall("tax", 40) }
                .with { networkCall("discount", 40) }
                .then { networkCall("payment", 60) }
        }
    }

    @Benchmark fun arrow_latency_multiPhase(): String = runBlocking {
        val phase1 = parZip(
            { networkCall("user", 50) }, { networkCall("cart", 50) },
            { networkCall("inventory", 50) }, { networkCall("address", 50) }
        ) { u, c, i, a -> "$u|$c|$i|$a" }
        val validated = networkCall("validated", 30)
        val phase3 = parZip(
            { networkCall("shipping", 40) }, { networkCall("tax", 40) },
            { networkCall("discount", 40) }
        ) { s, t, d -> "$s|$t|$d" }
        val payment = networkCall("payment", 60)
        "$phase1|$validated|$phase3|$payment"
    }

    @Benchmark fun sequential_latency_multiPhase(): String = runBlocking {
        val parts = listOf("user", "cart", "inventory", "address").map { networkCall(it, 50) }
        val validated = networkCall("validated", 30)
        val phase3 = listOf("shipping", "tax", "discount").map { networkCall(it, 40) }
        val payment = networkCall("payment", 60)
        (parts + validated + phase3 + payment).joinToString("|")
    }

    // ════════════════════════════════════════════════════════════════════════
    // 4. COMBINE — parallel suspend lambdas (parZip-like)
    // ════════════════════════════════════════════════════════════════════════

    @Benchmark fun raw_combine3_overhead(): String = runBlocking {
        coroutineScope {
            val a = async { compute(1) }; val b = async { compute(2) }; val c = async { compute(3) }
            "${a.await()}|${b.await()}|${c.await()}"
        }
    }

    @Benchmark fun kap_combine3_overhead(): String = runBlocking {
        Async {
            combine({ compute(1) }, { compute(2) }, { compute(3) }) { a, b, c -> "$a|$b|$c" }
        }
    }

    @Benchmark fun arrow_combine3_overhead(): String = runBlocking {
        parZip({ compute(1) }, { compute(2) }, { compute(3) }) { a, b, c -> "$a|$b|$c" }
    }

    @Benchmark fun kap_combine5_overhead(): String = runBlocking {
        Async {
            combine(
                { compute(1) }, { compute(2) }, { compute(3) }, { compute(4) }, { compute(5) },
            ) { a, b, c, d, e -> "$a|$b|$c|$d|$e" }
        }
    }

    @Benchmark fun kap_combine5_latency(): String = runBlocking {
        Async {
            combine(
                { networkCall("user", 50) }, { networkCall("cart", 50) },
                { networkCall("prefs", 50) }, { networkCall("recs", 50) },
                { networkCall("promos", 50) },
            ) { a, b, c, d, e -> "$a|$b|$c|$d|$e" }
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // 5. HIGH-ARITY — kap (15-arity), measure curried chain overhead
    // ════════════════════════════════════════════════════════════════════════

    @Benchmark fun raw_overhead_arity15(): String = runBlocking {
        coroutineScope {
            (1..15).map { async { compute(it) } }.map { it.await() }.joinToString("|")
        }
    }

    @Benchmark fun kap_overhead_arity15(): String = runBlocking {
        Async {
            kap { a: String, b: String, c: String, d: String, e: String,
                     f: String, g: String, h: String, i: String, j: String,
                     k: String, l: String, m: String, n: String, o: String ->
                listOf(a, b, c, d, e, f, g, h, i, j, k, l, m, n, o).joinToString("|")
            }
                .with { compute(1) }.with { compute(2) }.with { compute(3) }
                .with { compute(4) }.with { compute(5) }.with { compute(6) }
                .with { compute(7) }.with { compute(8) }.with { compute(9) }
                .with { compute(10) }.with { compute(11) }.with { compute(12) }
                .with { compute(13) }.with { compute(14) }.with { compute(15) }
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // 6. TRAVERSE — unbounded + bounded concurrency
    // ════════════════════════════════════════════════════════════════════════

    @Benchmark fun raw_traverse_unbounded_20(): String = runBlocking {
        coroutineScope {
            (1..20).map { i -> async { networkCall("item-$i", 30) } }
                .awaitAll().joinToString("|")
        }
    }

    @Benchmark fun kap_traverse_unbounded_20(): String = runBlocking {
        Async {
            (1..20).toList().traverse { i ->
                Effect { networkCall("item-$i", 30) }
            }.map { it.joinToString("|") }
        }
    }

    @Benchmark fun raw_traverse_bounded_20_c5(): String = runBlocking {
        val semaphore = Semaphore(5)
        coroutineScope {
            (1..20).map { i ->
                async { semaphore.withPermit { networkCall("item-$i", 30) } }
            }.awaitAll().joinToString("|")
        }
    }

    @Benchmark fun kap_traverse_bounded_20_c5(): String = runBlocking {
        Async {
            (1..20).toList().traverse(concurrency = 5) { i ->
                Effect { networkCall("item-$i", 30) }
            }.map { it.joinToString("|") }
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // 7. RACE — first to succeed wins
    // ════════════════════════════════════════════════════════════════════════

    @Benchmark fun raw_race_two(): String = runBlocking {
        coroutineScope {
            kotlinx.coroutines.selects.select {
                async { networkCall("primary", 100) }.onAwait { it }
                async { networkCall("replica", 50) }.onAwait { it }
            }
        }
    }

    @Benchmark fun kap_race_two(): String = runBlocking {
        Async {
            race(
                Effect { networkCall("primary", 100) },
                Effect { networkCall("replica", 50) },
            )
        }
    }

    @Benchmark fun arrow_race_two(): String = runBlocking {
        arrow.fx.coroutines.raceN(
            { networkCall("primary", 100) },
            { networkCall("replica", 50) },
        ).fold({ it }, { it })
    }

    // ════════════════════════════════════════════════════════════════════════
    // 8. TIMEOUT — with default value
    // ════════════════════════════════════════════════════════════════════════

    @Benchmark fun raw_timeout_with_default(): String = runBlocking {
        kotlinx.coroutines.withTimeoutOrNull(100) { networkCall("slow", 200) } ?: "cached"
    }

    @Benchmark fun kap_timeout_with_default(): String = runBlocking {
        Async {
            Effect { networkCall("slow", 200) }
                .timeout(kotlin.time.Duration.parse("100ms"), default = "cached")
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // 9. MEMOIZE — cache hit vs miss
    // ════════════════════════════════════════════════════════════════════════

    @Benchmark fun raw_memoize_cold(): String = runBlocking {
        val lock = kotlinx.coroutines.sync.Mutex()
        var cached: String? = null
        lock.lock()
        try {
            if (cached == null) cached = compute(1)
            cached!!
        } finally {
            lock.unlock()
        }
    }

    @Benchmark fun kap_memoize_cold(): String = runBlocking {
        val m = Effect { compute(1) }.memoize()
        Async { m }
    }

    @Benchmark fun kap_memoize_warm(): String = runBlocking {
        Async { warmMemoized }
    }

    private val warmMemoized: Effect<String> = run {
        val m = Effect { compute(1) }.memoize()
        runBlocking { Async { m } }
        m
    }

    // ════════════════════════════════════════════════════════════════════════
    // 10. MEMOIZE-ON-SUCCESS — transient failure retry
    // ════════════════════════════════════════════════════════════════════════

    @Benchmark fun kap_memoizeOnSuccess_cold(): String = runBlocking {
        val m = Effect { compute(1) }.memoizeOnSuccess()
        Async { m }
    }

    @Benchmark fun kap_memoizeOnSuccess_warm(): String = runBlocking {
        Async { warmMemoizedOnSuccess }
    }

    private val warmMemoizedOnSuccess: Effect<String> = run {
        val m = Effect { compute(1) }.memoizeOnSuccess()
        runBlocking { Async { m } }
        m
    }

    @Benchmark fun kap_memoizeOnSuccess_failure_retry(): String = runBlocking {
        var calls = 0
        val m = Effect {
            calls++
            if (calls == 1) error("transient")
            compute(1)
        }.memoizeOnSuccess()
        runCatching { Async { m } }
        Async { m }
    }

    // ════════════════════════════════════════════════════════════════════════
    // 11. COMPUTATION {} BUILDER — monadic bind overhead
    // ════════════════════════════════════════════════════════════════════════

    @Benchmark fun raw_sequential_3(): String = runBlocking {
        val a = compute(1); val b = compute(2); val c = compute(3)
        "$a|$b|$c"
    }

    @Benchmark fun kap_computation_overhead(): String = runBlocking {
        Async {
            computation {
                val a = bind { compute(1) }
                val b = bind { compute(2) }
                val c = bind { compute(3) }
                "$a|$b|$c"
            }
        }
    }

    @Benchmark fun kap_computation_latency(): String = runBlocking {
        Async {
            computation {
                val a = bind { networkCall("user", 50) }
                val b = bind { networkCall("cart-${a.length}", 50) }
                val c = bind { networkCall("recs-${b.length}", 50) }
                "$a|$b|$c"
            }
        }
    }

    @Benchmark fun raw_sequential_latency_3(): String = runBlocking {
        val a = networkCall("user", 50)
        val b = networkCall("cart-${a.length}", 50)
        val c = networkCall("recs-${b.length}", 50)
        "$a|$b|$c"
    }

    // ════════════════════════════════════════════════════════════════════════
    // 12. FLATMAP CHAIN — comparison with computation builder
    // ════════════════════════════════════════════════════════════════════════

    @Benchmark fun kap_andThen_chain_overhead(): String = runBlocking {
        Async {
            Effect.of(compute(1)).andThen { a ->
                Effect.of(compute(2)).andThen { b ->
                    Effect.of(compute(3)).map { c -> "$a|$b|$c" }
                }
            }
        }
    }

    @Benchmark fun kap_andThen_chain_latency(): String = runBlocking {
        Async {
            Effect { networkCall("a", 50) }.andThen { a ->
                Effect { networkCall("b-${a.length}", 50) }.andThen { b ->
                    Effect { networkCall("c-${b.length}", 50) }.map { c -> "$a|$b|$c" }
                }
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // 13. OR-ELSE CHAIN — fallback chain performance
    // ════════════════════════════════════════════════════════════════════════

    @Benchmark fun raw_orElse_chain_3(): String = runBlocking {
        val result = runCatching { error("fail-1") as String }
            .recoverCatching { error("fail-2") as String }
            .recoverCatching { compute(3) }
        result.getOrThrow()
    }

    @Benchmark fun kap_orElse_chain_overhead(): String = runBlocking {
        Async {
            Effect<String> { error("fail-1") }
                .orElse(Effect { error("fail-2") })
                .orElse(Effect { compute(3) })
        }
    }

    @Benchmark fun kap_orElse_chain_latency(): String = runBlocking {
        Async {
            Effect<String> { delay(10); error("fail-1") }
                .orElse(Effect { delay(10); error("fail-2") })
                .orElse(Effect { networkCall("ok", 10) })
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // 14. FIRST-SUCCESS-OF — first non-failing computation
    // ════════════════════════════════════════════════════════════════════════

    @Benchmark fun raw_firstSuccessOf_5(): String = runBlocking {
        val fns = listOf<suspend () -> String>(
            { error("fail-1") }, { error("fail-2") },
            { compute(3) }, { compute(4) }, { compute(5) },
        )
        var result: String? = null
        for (fn in fns) {
            result = runCatching { fn() }.getOrNull()
            if (result != null) break
        }
        result!!
    }

    @Benchmark fun kap_firstSuccessOf_overhead(): String = runBlocking {
        Async {
            firstSuccessOf(
                Effect { error("fail-1") }, Effect { error("fail-2") },
                Effect { compute(3) }, Effect { compute(4) }, Effect { compute(5) },
            )
        }
    }

    @Benchmark fun kap_firstSuccessOf_latency(): String = runBlocking {
        Async {
            firstSuccessOf(
                Effect { delay(10); error("fail-1") },
                Effect { delay(10); error("fail-2") },
                Effect { networkCall("ok", 10) },
                Effect { networkCall("unused", 10) },
                Effect { networkCall("unused", 10) },
            )
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // 15. TRAVERSE-SETTLED — collect ALL results without cancellation
    // ════════════════════════════════════════════════════════════════════════

    @Benchmark fun raw_traverseSettled_10(): List<Result<String>> = runBlocking {
        supervisorScope {
            (1..10).map { i -> async { runCatching { networkCall("item-$i", 30) } } }
                .map { it.await() }
        }
    }

    @Benchmark fun kap_traverseSettled_10_pass(): List<Result<String>> = runBlocking {
        Async {
            (1..10).toList().traverseSettled { i ->
                Effect { networkCall("item-$i", 30) }
            }
        }
    }

    @Benchmark fun kap_traverseSettled_10_half_fail(): List<Result<String>> = runBlocking {
        Async {
            (1..10).toList().traverseSettled { i ->
                Effect {
                    delay(30)
                    if (i % 2 == 0) throw RuntimeException("fail-$i")
                    "ok-$i"
                }
            }
        }
    }

    @Benchmark fun kap_traverseSettled_bounded_20_c5(): List<Result<String>> = runBlocking {
        Async {
            (1..20).toList().traverseSettled(5) { i ->
                Effect { networkCall("item-$i", 30) }
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // 16. SETTLED — partial failure tolerance in ap chains
    // ════════════════════════════════════════════════════════════════════════

    @Benchmark fun kap_settled_success(bh: Blackhole) = runBlocking {
        bh.consume(Async {
            Effect { compute(1) }.settled()
        })
    }

    @Benchmark fun kap_settled_failure_no_cancel(): String = runBlocking {
        data class R(val a: Result<String>, val b: String, val c: String)
        val result = Async {
            kap(::R)
                .with(Effect<String> { throw RuntimeException("down") }.settled())
                .with { networkCall("b", 50) }
                .with { networkCall("c", 50) }
        }
        "${result.a.isFailure}|${result.b}|${result.c}"
    }

    // ════════════════════════════════════════════════════════════════════════
    // 17. FLOW INTEGRATION — mapEffect, filterEffect
    // ════════════════════════════════════════════════════════════════════════

    @Benchmark fun raw_flow_andThenMerge_10(): List<String> = runBlocking {
        flowOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
            .andThenMerge(concurrency = 5) { n ->
                flow { emit(networkCall("item-$n", 30)) }
            }.toList()
    }

    @Benchmark fun kap_flow_mapEffect_seq_10(): List<String> = runBlocking {
        flowOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
            .mapEffect { n -> Effect { networkCall("item-$n", 30) } }
            .toList()
    }

    @Benchmark fun kap_flow_mapEffect_c5_10(): List<String> = runBlocking {
        flowOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
            .mapEffect(concurrency = 5) { n -> Effect { networkCall("item-$n", 30) } }
            .toList()
    }

    @Benchmark fun kap_flow_mapEffectOrdered_c5_10(): List<String> = runBlocking {
        flowOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
            .mapEffectOrdered(concurrency = 5) { n -> Effect { networkCall("item-$n", 30) } }
            .toList()
    }

    @Benchmark fun raw_flow_map_overhead_10(): List<String> = runBlocking {
        flowOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
            .map { n -> compute(n) }
            .toList()
    }

    @Benchmark fun kap_flow_mapEffect_overhead_10(): List<String> = runBlocking {
        flowOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
            .mapEffect(concurrency = 5) { n -> Effect { compute(n) } }
            .toList()
    }

    @Benchmark fun kap_flow_filterEffect_10(): List<Int> = runBlocking {
        flowOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
            .filterEffect { n -> Effect { n % 2 == 0 } }
            .toList()
    }
}
