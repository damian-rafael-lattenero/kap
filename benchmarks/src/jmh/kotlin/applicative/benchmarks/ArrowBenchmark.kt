package applicative.benchmarks

import applicative.*
import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import arrow.core.Either.Left as ArrowLeft
import arrow.core.Either.Right as ArrowRight
import arrow.core.raise.either
import arrow.core.raise.zipOrAccumulate
import arrow.fx.coroutines.parZip
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.supervisorScope
import org.openjdk.jmh.annotations.*
import java.util.concurrent.TimeUnit

/**
 * JMH benchmarks for **kap-arrow** APIs.
 *
 * Covers: kapV+withV, zipV, traverseV, raceEither, ensureV,
 * attempt, catching, validated{}, andThenV.
 *
 * Every KAP benchmark has a `raw_` and/or `arrow_` baseline where applicable.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(1)
open class ArrowBenchmark {

    private fun compute(n: Int): String = "v$n"

    private suspend fun networkCall(label: String, delayMs: Long): String {
        delay(delayMs)
        return label
    }

    private suspend fun validate(
        label: String,
        delayMs: Long,
        pass: Boolean,
    ): Either<NonEmptyList<String>, String> {
        delay(delayMs)
        return if (pass) Either.Right(label) else Either.Left(nonEmptyListOf("$label-failed"))
    }

    private suspend fun arrowValidate(
        label: String,
        delayMs: Long,
        pass: Boolean,
    ): Either<String, String> {
        delay(delayMs)
        return if (pass) ArrowRight(label) else ArrowLeft("$label-failed")
    }

    // ════════════════════════════════════════════════════════════════════════
    // 1. VALIDATED ERROR ACCUMULATION — kapV + withV
    // ════════════════════════════════════════════════════════════════════════

    @Benchmark fun kap_withV_latency_all_pass(): String = runBlocking {
        val result = Async {
            kapV<String, String, String, String, String, String> { a, b, c, d -> "$a|$b|$c|$d" }
                .withV { validate("card", 40, pass = true) }
                .withV { validate("stock", 40, pass = true) }
                .withV { validate("address", 40, pass = true) }
                .withV { validate("age", 40, pass = true) }
        }
        when (result) {
            is Either.Right -> result.value
            is Either.Left -> "errors:${result.value}"
        }
    }

    @Benchmark fun arrow_validation_all_pass(): String = runBlocking {
        val results = parZip(
            { arrowValidate("card", 40, true) },
            { arrowValidate("stock", 40, true) },
            { arrowValidate("address", 40, true) },
            { arrowValidate("age", 40, true) }
        ) { a, b, c, d -> listOf(a, b, c, d) }
        val result = either<NonEmptyList<String>, String> {
            zipOrAccumulate(
                { results[0].bind() }, { results[1].bind() },
                { results[2].bind() }, { results[3].bind() },
            ) { a: String, b: String, c: String, d: String -> "$a|$b|$c|$d" }
        }
        when (result) {
            is ArrowRight -> result.value
            is ArrowLeft -> "errors:${result.value}"
        }
    }

    @Benchmark fun sequential_validation_all_pass(): String = runBlocking {
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

    @Benchmark fun kap_withV_latency_all_fail(): String = runBlocking {
        val result = Async {
            kapV<String, String, String, String, String, String> { a, b, c, d -> "$a|$b|$c|$d" }
                .withV { validate("card", 40, pass = false) }
                .withV { validate("stock", 40, pass = false) }
                .withV { validate("address", 40, pass = false) }
                .withV { validate("age", 40, pass = false) }
        }
        when (result) {
            is Either.Right -> result.value
            is Either.Left -> "errors:${result.value.size}"
        }
    }

    @Benchmark fun arrow_validation_all_fail(): String = runBlocking {
        val results = parZip(
            { arrowValidate("card", 40, false) },
            { arrowValidate("stock", 40, false) },
            { arrowValidate("address", 40, false) },
            { arrowValidate("age", 40, false) }
        ) { a, b, c, d -> listOf(a, b, c, d) }
        val result = either<NonEmptyList<String>, String> {
            zipOrAccumulate(
                { results[0].bind() }, { results[1].bind() },
                { results[2].bind() }, { results[3].bind() },
            ) { a: String, b: String, c: String, d: String -> "$a|$b|$c|$d" }
        }
        when (result) {
            is ArrowRight -> result.value
            is ArrowLeft -> "errors:${result.value.size}"
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // 2. ZIPV — validated zip with mixed results
    // ════════════════════════════════════════════════════════════════════════

    @Benchmark fun kap_zipV_mixed(): String = runBlocking {
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
    // 3. TRAVERSE-V — parallel validation over collections
    // ════════════════════════════════════════════════════════════════════════

    @Benchmark fun kap_traverseV_10_all_pass(): String = runBlocking {
        val result = Async {
            (1..10).toList().traverseV { i ->
                Effect<Either<NonEmptyList<String>, String>> { validate("item-$i", 30, pass = true) }
            }
        }
        when (result) {
            is Either.Right -> "ok:${result.value.size}"
            is Either.Left -> "errors:${result.value.size}"
        }
    }

    @Benchmark fun kap_traverseV_10_half_fail(): String = runBlocking {
        val result = Async {
            (1..10).toList().traverseV { i ->
                Effect<Either<NonEmptyList<String>, String>> { validate("item-$i", 30, pass = i % 2 == 0) }
            }
        }
        when (result) {
            is Either.Right -> "ok:${result.value.size}"
            is Either.Left -> "errors:${result.value.size}"
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // 4. TRAVERSE-V BOUNDED — bounded concurrency with validation
    // ════════════════════════════════════════════════════════════════════════

    @Benchmark fun kap_traverseV_bounded_20_c5_pass(): String = runBlocking {
        val result = Async {
            (1..20).toList().traverseV(concurrency = 5) { i ->
                Effect<Either<NonEmptyList<String>, String>> { validate("item-$i", 30, pass = true) }
            }
        }
        when (result) {
            is Either.Right -> "ok:${result.value.size}"
            is Either.Left -> "errors:${result.value.size}"
        }
    }

    @Benchmark fun kap_traverseV_bounded_20_c5_half_fail(): String = runBlocking {
        val result = Async {
            (1..20).toList().traverseV(concurrency = 5) { i ->
                Effect<Either<NonEmptyList<String>, String>> { validate("item-$i", 30, pass = i % 2 == 0) }
            }
        }
        when (result) {
            is Either.Right -> "ok:${result.value.size}"
            is Either.Left -> "errors:${result.value.size}"
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // 5. RACE-EITHER — heterogeneous racing (returns Either<A, B>)
    // ════════════════════════════════════════════════════════════════════════

    @Benchmark fun raw_raceEither(): String = runBlocking {
        supervisorScope {
            val a = async { networkCall("cache", 30) }
            val b = async { networkCall("network", 100) }
            val winner = select<String> {
                a.onAwait { "left:$it" }
                b.onAwait { "right:$it" }
            }
            a.cancel(); b.cancel()
            winner
        }
    }

    @Benchmark fun arrow_raceEither(): String = runBlocking {
        arrow.fx.coroutines.raceN(
            { networkCall("cache", 30) },
            { networkCall("network", 100) },
        ).fold({ "left:$it" }, { "right:$it" })
    }

    @Benchmark fun kap_raceEither_latency(): String = runBlocking {
        val result = Async {
            raceEither(
                Effect { networkCall("cache", 30) },
                Effect { networkCall("network", 100) },
            )
        }
        when (result) {
            is Either.Left -> "cache:${result.value}"
            is Either.Right -> "network:${result.value}"
        }
    }

    @Benchmark fun kap_race_homogeneous_overhead(): String = runBlocking {
        Async {
            race(
                Effect { compute(1) },
                Effect { compute(2) },
            )
        }
    }

    @Benchmark fun kap_raceEither_heterogeneous_overhead(): String = runBlocking {
        val result = Async {
            raceEither(
                Effect { compute(1) },
                Effect { 42 },
            )
        }
        when (result) {
            is Either.Left -> result.value
            is Either.Right -> "n:${result.value}"
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // 6. ENSURE-V — validated guard
    // ════════════════════════════════════════════════════════════════════════

    @Benchmark fun kap_ensureV_pass(): String = runBlocking {
        val result = Async {
            Effect { 25 }.ensureV({ "too young" }) { it >= 18 }
        }
        when (result) {
            is Either.Right -> "ok:${result.value}"
            is Either.Left -> "fail:${result.value}"
        }
    }

    @Benchmark fun kap_ensureV_fail(): String = runBlocking {
        val result = Async {
            Effect { 15 }.ensureV({ "too young" }) { it >= 18 }
        }
        when (result) {
            is Either.Right -> "ok:${result.value}"
            is Either.Left -> "fail:${result.value.head}"
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // 7. ATTEMPT — bridge exceptions into Either<Throwable, A>
    // ════════════════════════════════════════════════════════════════════════

    @Benchmark fun raw_attempt_success(): String = runBlocking {
        val result: Either<Throwable, String> = try {
            Either.Right(compute(1))
        } catch (e: Throwable) {
            Either.Left(e)
        }
        when (result) {
            is Either.Right -> result.value
            is Either.Left -> "error"
        }
    }

    @Benchmark fun raw_attempt_failure(): String = runBlocking {
        val result: Either<Throwable, String> = try {
            Either.Right(error("boom") as String)
        } catch (e: Throwable) {
            Either.Left(e)
        }
        when (result) {
            is Either.Right -> result.value
            is Either.Left -> "caught:${result.value.message}"
        }
    }

    @Benchmark fun arrow_attempt_success(): String = runBlocking {
        val result = either<Throwable, String> { compute(1) }
        when (result) {
            is Either.Right -> result.value
            is Either.Left -> "error"
        }
    }

    @Benchmark fun kap_attempt_success(): String = runBlocking {
        val result = Async {
            Effect { compute(1) }.attempt()
        }
        when (result) {
            is Either.Right -> result.value
            is Either.Left -> "error"
        }
    }

    @Benchmark fun kap_attempt_failure(): String = runBlocking {
        val result = Async {
            Effect<String> { error("boom") }.attempt()
        }
        when (result) {
            is Either.Right -> result.value
            is Either.Left -> "caught:${result.value.message}"
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // 8. CATCHING — bridge exceptions into validated (Nel<E>)
    // ════════════════════════════════════════════════════════════════════════

    @Benchmark fun kap_catching_success(): String = runBlocking {
        val result = Async {
            Effect { compute(1) }.catching { "caught: ${it.message}" }
        }
        when (result) {
            is Either.Right -> result.value
            is Either.Left -> "fail:${result.value}"
        }
    }

    @Benchmark fun kap_catching_failure(): String = runBlocking {
        val result = Async {
            Effect<String> { error("boom") }.catching { "caught: ${it.message}" }
        }
        when (result) {
            is Either.Right -> result.value
            is Either.Left -> result.value.head
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // 9. VALIDATED {} BUILDER — accumulate errors in a DSL block
    // ════════════════════════════════════════════════════════════════════════

    @Benchmark fun arrow_either_builder(): String = runBlocking {
        val result: Either<NonEmptyList<String>, String> = either {
            zipOrAccumulate(
                { Either.Right("Alice").bind() },
                { Either.Right("alice@example.com").bind() },
                { Either.Right(25).bind() },
            ) { name: String, email: String, age: Int -> "$name|$email|$age" }
        }
        when (result) {
            is Either.Right -> result.value
            is Either.Left -> "errors:${result.value.size}"
        }
    }

    @Benchmark fun kap_validated_builder(): String = runBlocking {
        val result = Async {
            validated<String, String> {
                val name = Effect<Either<NonEmptyList<String>, String>> { Either.Right("Alice") }.bindV()
                val email = Effect<Either<NonEmptyList<String>, String>> { Either.Right("alice@example.com") }.bindV()
                val age = Effect<Either<NonEmptyList<String>, Int>> { Either.Right(25) }.bindV()
                "$name|$email|$age"
            }
        }
        when (result) {
            is Either.Right -> result.value
            is Either.Left -> "errors:${result.value.size}"
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // 10. FLATMAPV — phased validation (phase 1 validates, phase 2 depends)
    // ════════════════════════════════════════════════════════════════════════

    @Benchmark fun arrow_phased_validation(): String = runBlocking {
        val phase1: Either<NonEmptyList<String>, Pair<String, String>> = either {
            zipOrAccumulate(
                { arrowValidate("name", 40, true).bind() },
                { arrowValidate("email", 40, true).bind() },
            ) { n, e -> n to e }
        }
        val result = phase1.map { (n, e) -> "$n|$e|confirmed" }
        when (result) {
            is Either.Right -> result.value
            is Either.Left -> "errors:${result.value.size}"
        }
    }

    @Benchmark fun kap_andThenV_phased(): String = runBlocking {
        val result = Async {
            zipV(
                { validate("name", 40, true) },
                { validate("email", 40, true) },
            ) { n, e -> n to e }
            .andThenV { (name, email) ->
                Effect { validate("age", 40, true) }
                    .mapV { age -> "$name|$email|$age" }
            }
        }
        when (result) {
            is Either.Right -> result.value
            is Either.Left -> "errors:${result.value.size}"
        }
    }
}
