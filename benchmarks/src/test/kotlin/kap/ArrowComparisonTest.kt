package kap

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import arrow.core.raise.either
import arrow.core.raise.zipOrAccumulate
import arrow.fx.coroutines.parZip
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

// ════════════════════════════════════════════════════════════════════════════════
// Three-Way Comparison: kap-arrow
//
// Every major kap-arrow operation implemented three ways:
//   1. Raw Coroutines  — manual Either + try-catch
//   2. Arrow           — zipOrAccumulate / either {} / raceN
//   3. KAP             — kapV + withV / zipV / attempt / raceEither
// ════════════════════════════════════════════════════════════════════════════════

// ── Validation domain ─────────────────────────────────────────────────────────

private sealed class FErr(val msg: String) {
    class Name(msg: String) : FErr(msg)
    class Email(msg: String) : FErr(msg)
    class Age(msg: String) : FErr(msg)
    class Phone(msg: String) : FErr(msg)
    override fun equals(other: Any?) = other is FErr && this::class == other::class && msg == other.msg
    override fun hashCode() = this::class.hashCode() * 31 + msg.hashCode()
    override fun toString() = "${this::class.simpleName}($msg)"
}

private data class VName(val v: String)
private data class VEmail(val v: String)
private data class VAge(val v: Int)
private data class VPhone(val v: String)
private data class Reg3(val name: VName, val email: VEmail, val age: VAge)
private data class Reg4(val name: VName, val email: VEmail, val age: VAge, val phone: VPhone)

private fun valName(s: String): Either<NonEmptyList<FErr>, VName> =
    if (s.length >= 2) Either.Right(VName(s)) else Either.Left(nonEmptyListOf(FErr.Name("too short: $s")))
private fun valEmail(s: String): Either<NonEmptyList<FErr>, VEmail> =
    if ("@" in s) Either.Right(VEmail(s)) else Either.Left(nonEmptyListOf(FErr.Email("no @: $s")))
private fun valAge(n: Int): Either<NonEmptyList<FErr>, VAge> =
    if (n >= 18) Either.Right(VAge(n)) else Either.Left(nonEmptyListOf(FErr.Age("too young: $n")))
private fun valPhone(s: String): Either<NonEmptyList<FErr>, VPhone> =
    if (s.length >= 10) Either.Right(VPhone(s)) else Either.Left(nonEmptyListOf(FErr.Phone("too short: $s")))

private fun valNameArrow(s: String): Either<FErr, VName> =
    if (s.length >= 2) Either.Right(VName(s)) else Either.Left(FErr.Name("too short: $s"))
private fun valEmailArrow(s: String): Either<FErr, VEmail> =
    if ("@" in s) Either.Right(VEmail(s)) else Either.Left(FErr.Email("no @: $s"))
private fun valAgeArrow(n: Int): Either<FErr, VAge> =
    if (n >= 18) Either.Right(VAge(n)) else Either.Left(FErr.Age("too young: $n"))
private fun valPhoneArrow(s: String): Either<FErr, VPhone> =
    if (s.length >= 10) Either.Right(VPhone(s)) else Either.Left(FErr.Phone("too short: $s"))

private suspend fun networkCall(label: String, delayMs: Long): String {
    delay(delayMs.milliseconds); return label
}

class ArrowComparisonTest {

    // ═══════════════════════════════════════════════════════════════════════
    // ERROR ACCUMULATION — parallel validation, collect ALL errors
    // ═══════════════════════════════════════════════════════════════════════

    @Test fun `error accumulation 3 - raw - sequential, no parallelism`() = runTest {
        val errors = mutableListOf<FErr>()
        val name = valName("A").let { when (it) {
            is Either.Right -> it.value; is Either.Left -> { errors.addAll(it.value); null }
        }}
        val email = valEmail("bad").let { when (it) {
            is Either.Right -> it.value; is Either.Left -> { errors.addAll(it.value); null }
        }}
        val age = valAge(5).let { when (it) {
            is Either.Right -> it.value; is Either.Left -> { errors.addAll(it.value); null }
        }}
        assertEquals(3, errors.size)
        assertEquals(null, name); assertEquals(null, email); assertEquals(null, age)
    }

    @Test fun `error accumulation 3 - arrow - zipOrAccumulate`() = runTest {
        val result: Either<NonEmptyList<FErr>, Reg3> = either {
            zipOrAccumulate(
                { valNameArrow("A").bind() },
                { valEmailArrow("bad").bind() },
                { valAgeArrow(5).bind() },
            ) { n, e, a -> Reg3(n, e, a) }
        }
        val left = assertIs<Either.Left<NonEmptyList<FErr>>>(result)
        assertEquals(3, left.value.size)
    }

    @Test fun `error accumulation 3 - kap - zipV`() = runTest {
        val result = Async {
            zipV(
                { valName("A") }, { valEmail("bad") }, { valAge(5) },
            ) { n, e, a -> Reg3(n, e, a) }
        }
        val left = assertIs<Either.Left<NonEmptyList<FErr>>>(result)
        assertEquals(3, left.value.size)
        assertIs<FErr.Name>(left.value[0])
        assertIs<FErr.Email>(left.value[1])
        assertIs<FErr.Age>(left.value[2])
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ERROR ACCUMULATION 4+ — scaling comparison
    // ═══════════════════════════════════════════════════════════════════════

    @Test fun `error accumulation 4 - arrow`() = runTest {
        val result: Either<NonEmptyList<FErr>, Reg4> = either {
            zipOrAccumulate(
                { valNameArrow("A").bind() }, { valEmailArrow("bad").bind() },
                { valAgeArrow(5).bind() }, { valPhoneArrow("123").bind() },
            ) { n, e, a, p -> Reg4(n, e, a, p) }
        }
        assertEquals(4, assertIs<Either.Left<NonEmptyList<FErr>>>(result).value.size)
    }

    @Test fun `error accumulation 4 - kap - zipV scales to 22`() = runTest {
        val result = Async {
            zipV(
                { valName("A") }, { valEmail("bad") }, { valAge(5) }, { valPhone("123") },
            ) { n, e, a, p -> Reg4(n, e, a, p) }
        }
        assertEquals(4, assertIs<Either.Left<NonEmptyList<FErr>>>(result).value.size)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // SUCCESS PATH — all three produce the same result
    // ═══════════════════════════════════════════════════════════════════════

    @Test fun `error accumulation - all agree on valid input`() = runTest {
        val expected = Reg3(VName("Alice"), VEmail("alice@example.com"), VAge(30))
        val raw = Reg3(
            (valName("Alice") as Either.Right).value,
            (valEmail("alice@example.com") as Either.Right).value,
            (valAge(30) as Either.Right).value,
        )
        val arrow: Either<NonEmptyList<FErr>, Reg3> = either {
            zipOrAccumulate(
                { valNameArrow("Alice").bind() },
                { valEmailArrow("alice@example.com").bind() },
                { valAgeArrow(30).bind() },
            ) { n, e, a -> Reg3(n, e, a) }
        }
        val kap = Async {
            kapV<FErr, VName, VEmail, VAge, Reg3>(::Reg3)
                .withV { valName("Alice") }
                .withV { valEmail("alice@example.com") }
                .withV { valAge(30) }
        }
        assertEquals(expected, raw)
        assertEquals(Either.Right(expected), arrow)
        assertEquals(Either.Right(expected), kap)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // PHASED VALIDATION — phase 1 accumulates, phase 2 depends
    // ═══════════════════════════════════════════════════════════════════════

    @Test fun `phased validation - raw - impossible to parallelize`() = runTest {
        val errors = mutableListOf<FErr>()
        valName("A").let { when (it) {
            is Either.Right -> it.value; is Either.Left -> { errors.addAll(it.value); null }
        }}
        valEmail("bad").let { when (it) {
            is Either.Right -> it.value; is Either.Left -> { errors.addAll(it.value); null }
        }}
        if (errors.isNotEmpty()) {
            assertEquals(2, errors.size)
            return@runTest
        }
    }

    @Test fun `phased validation - arrow - zipOrAccumulate + andThen`() = runTest {
        val phase1 = either {
            zipOrAccumulate(
                { valNameArrow("A").bind() }, { valEmailArrow("bad").bind() },
            ) { n, e -> "$n|$e" }
        }
        assertIs<Either.Left<NonEmptyList<FErr>>>(phase1)
        assertEquals(2, phase1.value.size)
    }

    @Test fun `phased validation - kap - zipV + andThenV`() = runTest {
        val result = Async {
            zipV(
                { valName("A") }, { valEmail("bad") },
            ) { n, e -> n to e }
            .andThenV { (name, email) ->
                Kap { valAge(25) }.mapV { age -> Reg3(name, email, age) }
            }
        }
        val left = assertIs<Either.Left<NonEmptyList<FErr>>>(result)
        assertEquals(2, left.value.size)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // CATCHING — bridge exceptions into validated
    // ═══════════════════════════════════════════════════════════════════════

    private suspend fun riskyCall(): String = throw IllegalArgumentException("bad input")

    @Test fun `catching - raw - try-catch to Either`() = runTest {
        val result: Either<String, String> = try {
            Either.Right(riskyCall())
        } catch (e: Exception) {
            Either.Left("caught: ${e.message}")
        }
        assertIs<Either.Left<String>>(result)
        assertEquals("caught: bad input", result.value)
    }

    @Test fun `catching - kap`() = runTest {
        val result = Async {
            Kap<String> { riskyCall() }.catching { "caught: ${it.message}" }
        }
        assertIs<Either.Left<NonEmptyList<String>>>(result)
        assertEquals("caught: bad input", result.value[0])
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ATTEMPT — exceptions into Either<Throwable, A>
    // ═══════════════════════════════════════════════════════════════════════

    @Test fun `attempt - raw - try-catch`() = runTest {
        val result: Either<Throwable, String> = try {
            Either.Right("success")
        } catch (e: Throwable) {
            Either.Left(e)
        }
        assertIs<Either.Right<String>>(result)
    }

    @Test fun `attempt - arrow - either builder`() = runTest {
        val result = either<Throwable, String> { "success" }
        assertIs<Either.Right<String>>(result)
    }

    @Test fun `attempt - kap`() = runTest {
        val success = Async { Kap { "success" }.attempt() }
        assertIs<Either.Right<String>>(success)

        val failure = Async { Kap<String> { error("boom") }.attempt() }
        assertIs<Either.Left<Throwable>>(failure)
        assertEquals("boom", failure.value.message)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // VALIDATE — predicate-based validation
    // ═══════════════════════════════════════════════════════════════════════

    @Test fun `validate - kap`() = runTest {
        val tooShort = Async { Kap.of("A").validate { s -> if (s.length < 2) "too short" else null } }
        val ok = Async { Kap.of("Alice").validate { s -> if (s.length < 2) "too short" else null } }
        assertIs<Either.Left<NonEmptyList<String>>>(tooShort)
        assertIs<Either.Right<String>>(ok)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // TRAVERSE-V — parallel traverse with error accumulation
    // ═══════════════════════════════════════════════════════════════════════

    @Test fun `traverseV - raw - sequential error collection`() = runTest {
        val inputs = listOf("Alice", "A", "ok-name", "B")
        val errors = mutableListOf<FErr>()
        inputs.map { valName(it).let { r -> when (r) {
            is Either.Right -> r.value; is Either.Left -> { errors.addAll(r.value); null }
        }}}
        assertEquals(2, errors.size)
    }

    @Test fun `traverseV - kap - parallel with accumulation`() = runTest {
        val result = Async {
            listOf("Alice", "A", "ok-name", "B").traverseV { Kap { valName(it) } }
        }
        assertIs<Either.Left<NonEmptyList<FErr>>>(result)
        assertEquals(2, result.value.size)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // SEQUENCE-V
    // ═══════════════════════════════════════════════════════════════════════

    @Test fun `sequenceV - kap`() = runTest {
        val result = Async {
            listOf(
                Kap { valName("Alice") },
                Kap { valEmail("bad") },
                Kap { valName("B") },
            ).sequenceV()
        }
        assertIs<Either.Left<NonEmptyList<FErr>>>(result)
        assertEquals(2, result.value.size)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ENSURE-V — validated guard
    // ═══════════════════════════════════════════════════════════════════════

    @Test fun `ensureV - kap - pass`() = runTest {
        val result = Async { Kap { 25 }.ensureV({ "too young" }) { it >= 18 } }
        assertIs<Either.Right<Int>>(result)
        assertEquals(25, result.value)
    }

    @Test fun `ensureV - kap - fail`() = runTest {
        val result = Async { Kap { 15 }.ensureV({ "too young" }) { it >= 18 } }
        assertIs<Either.Left<NonEmptyList<String>>>(result)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // OR-THROW — unwrap validated or throw
    // ═══════════════════════════════════════════════════════════════════════

    @Test fun `orThrow - kap - Right returns value`() = runTest {
        assertEquals(42, Async { valid<String, Int>(42).orThrow() })
    }

    @Test fun `orThrow - kap - Left throws`() = runTest {
        val error = runCatching { Async { invalid<String, Int>("bad").orThrow() } }
        assertTrue(error.isFailure)
        assertIs<ValidationException>(error.exceptionOrNull())
    }

    // ═══════════════════════════════════════════════════════════════════════
    // MAP-ERROR — unify different error types
    // ═══════════════════════════════════════════════════════════════════════

    @Test fun `mapError - kap`() = runTest {
        val result = Async { invalid<String, Int>("name error").mapError { "wrapped: $it" } }
        assertIs<Either.Left<NonEmptyList<String>>>(result)
        assertEquals("wrapped: name error", result.value[0])
    }

    // ═══════════════════════════════════════════════════════════════════════
    // RACE-EITHER — heterogeneous racing
    // ═══════════════════════════════════════════════════════════════════════

    @Test fun `raceEither - raw - select`() = runTest {
        val result = supervisorScope {
            val a = async { networkCall("cache", 30) }
            val b = async { networkCall("network", 100) }
            val w = select<String> { a.onAwait { "left:$it" }; b.onAwait { "right:$it" } }
            a.cancel(); b.cancel(); w
        }
        assertTrue(result.startsWith("left:"))
    }

    @Test fun `raceEither - arrow - raceN`() = runTest {
        val result = arrow.fx.coroutines.raceN(
            { networkCall("cache", 30) },
            { networkCall("network", 100) },
        ).fold({ "left:$it" }, { "right:$it" })
        assertTrue(result.startsWith("left:"))
    }

    @Test fun `raceEither - kap`() = runTest {
        val result = Async {
            raceEither(
                Kap { networkCall("cache", 30) },
                Kap { networkCall("network", 100) },
            )
        }
        assertIs<Either.Left<String>>(result)
        assertEquals("cache", result.value)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // VALIDATED {} BUILDER — accumulate in a DSL
    // ═══════════════════════════════════════════════════════════════════════

    @Test fun `validated builder - arrow - either + zipOrAccumulate`() = runTest {
        val result: Either<NonEmptyList<FErr>, Reg3> = either {
            zipOrAccumulate(
                { valNameArrow("Alice").bind() },
                { valEmailArrow("alice@example.com").bind() },
                { valAgeArrow(30).bind() },
            ) { n, e, a -> Reg3(n, e, a) }
        }
        assertIs<Either.Right<Reg3>>(result)
    }

    @Test fun `validated builder - kap`() = runTest {
        val result = Async {
            validated<FErr, Reg3> {
                val name = Kap<Either<NonEmptyList<FErr>, VName>> { valName("Alice") }.bindV()
                val email = Kap<Either<NonEmptyList<FErr>, VEmail>> { valEmail("alice@example.com") }.bindV()
                val age = Kap<Either<NonEmptyList<FErr>, VAge>> { valAge(30) }.bindV()
                Reg3(name, email, age)
            }
        }
        assertIs<Either.Right<Reg3>>(result)
        assertEquals(Reg3(VName("Alice"), VEmail("alice@example.com"), VAge(30)), result.value)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // FLATMAPV — phased validation (phase 1 accumulates, phase 2 depends)
    // ═══════════════════════════════════════════════════════════════════════

    @Test fun `andThenV - arrow - phase1 then phase2`() = runTest {
        val phase1: Either<NonEmptyList<FErr>, Pair<VName, VEmail>> = either {
            zipOrAccumulate(
                { valNameArrow("Alice").bind() },
                { valEmailArrow("alice@example.com").bind() },
            ) { n, e -> n to e }
        }
        val result = phase1.map { (n, e) -> Reg3(n, e, VAge(30)) }
        assertIs<Either.Right<Reg3>>(result)
    }

    @Test fun `andThenV - kap - zipV + andThenV`() = runTest {
        val result = Async {
            zipV(
                { valName("Alice") },
                { valEmail("alice@example.com") },
            ) { n, e -> n to e }
            .andThenV { (name, email) ->
                Kap { valAge(30) }.mapV { age -> Reg3(name, email, age) }
            }
        }
        assertIs<Either.Right<Reg3>>(result)
        assertEquals(Reg3(VName("Alice"), VEmail("alice@example.com"), VAge(30)), result.value)
    }
}
