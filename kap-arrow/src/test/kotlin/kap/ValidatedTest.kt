package kap

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

// -- Error types for tests (sealed classes must be top-level or in a class) --

private sealed class Err(val msg: String) {
    class InvalidCard(msg: String) : Err(msg)
    class OutOfStock(msg: String) : Err(msg)
    class BadAddress(msg: String) : Err(msg)
}

private data class User(val name: String, val email: String, val age: Int)

private sealed class RegError(val msg: String) {
    class NameTooShort(msg: String) : RegError(msg)
    class InvalidEmail(msg: String) : RegError(msg)
    class TooYoung(msg: String) : RegError(msg)
}

// -- Domain value types for checkout validation tests --

private data class ValidCard(val number: String)
private data class StockStatus(val sku: String)
private data class VerifiedAddress(val addr: String)

private data class ValidatedCheckout(val card: ValidCard, val stock: StockStatus, val address: VerifiedAddress)

class ValidatedTest {

    // ════════════════════════════════════════════════════════════════════════
    // ERROR ACCUMULATION — the killer feature
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `liftV+apV accumulates errors from two failures`() = runTest {
        val result = kapV<Err, ValidCard, StockStatus, Pair<ValidCard, StockStatus>> { card, stock -> card to stock }
            .withV { Either.Left(nonEmptyListOf(Err.InvalidCard("expired"))) }
            .withV { Either.Left(nonEmptyListOf(Err.OutOfStock("sku-99"))) }.evalGraph()

        assertIs<Either.Left<NonEmptyList<Err>>>(result)
        assertEquals(2, result.value.size)
        assertIs<Err.InvalidCard>(result.value[0])
        assertIs<Err.OutOfStock>(result.value[1])
    }

    @Test
    fun `liftV+apV accumulates errors from three failures`() = runTest {
        val result = kapV<Err, ValidCard, StockStatus, VerifiedAddress, ValidatedCheckout>(::ValidatedCheckout)
            .withV { Either.Left(nonEmptyListOf(Err.InvalidCard("expired"))) }
            .withV { Either.Left(nonEmptyListOf(Err.OutOfStock("sku-123"))) }
            .withV { Either.Left(nonEmptyListOf(Err.BadAddress("missing zip"))) }.evalGraph()

        assertIs<Either.Left<NonEmptyList<Err>>>(result)
        assertEquals(3, result.value.size)
        assertIs<Err.InvalidCard>(result.value[0])
        assertIs<Err.OutOfStock>(result.value[1])
        assertIs<Err.BadAddress>(result.value[2])
    }

    @Test
    fun `liftV+apV returns Right when all succeed`() = runTest {
        val result = kapV<Err, ValidCard, StockStatus, VerifiedAddress, ValidatedCheckout>(::ValidatedCheckout)
            .withV { Either.Right(ValidCard("4111-1111-1111-1111")) as Either<NonEmptyList<Err>, ValidCard> }
            .withV { Either.Right(StockStatus("sku-42")) }
            .withV { Either.Right(VerifiedAddress("123 Main St")) }.evalGraph()

        assertEquals(
            Either.Right(ValidatedCheckout(ValidCard("4111-1111-1111-1111"), StockStatus("sku-42"), VerifiedAddress("123 Main St"))),
            result
        )
    }

    @Test
    fun `liftV+apV with mix of success and failure returns only failures`() = runTest {
        val result = kapV<Err, ValidCard, StockStatus, VerifiedAddress, ValidatedCheckout>(::ValidatedCheckout)
            .withV { Either.Right(ValidCard("4111-1111-1111-1111")) as Either<NonEmptyList<Err>, ValidCard> }
            .withV { Either.Left(nonEmptyListOf(Err.OutOfStock("sku-123"))) }
            .withV { Either.Left(nonEmptyListOf(Err.BadAddress("missing zip"))) }.evalGraph()

        assertIs<Either.Left<NonEmptyList<Err>>>(result)
        assertEquals(2, result.value.size)
        assertIs<Err.OutOfStock>(result.value[0])
        assertIs<Err.BadAddress>(result.value[1])
    }

    @Test
    fun `liftV+apV single failure returns that error`() = runTest {
        val result = kapV<Err, ValidCard, StockStatus, Pair<ValidCard, StockStatus>> { card, stock -> card to stock }
            .withV { Either.Right(ValidCard("4111-1111-1111-1111")) as Either<NonEmptyList<Err>, ValidCard> }
            .withV { Either.Left(nonEmptyListOf(Err.OutOfStock("sku-123"))) }.evalGraph()

        assertIs<Either.Left<NonEmptyList<Err>>>(result)
        assertEquals(1, result.value.size)
        assertIs<Err.OutOfStock>(result.value[0])
    }

    // ════════════════════════════════════════════════════════════════════════
    // PARALLELISM PROOF for liftV+apV
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `liftV+apV runs both sides concurrently - barrier proof`() = runTest {
        val latchA = CompletableDeferred<Unit>()
        val latchB = CompletableDeferred<Unit>()

        val result = kapV<Err, ValidCard, StockStatus, Pair<ValidCard, StockStatus>> { card, stock -> card to stock }
            .withV {
                latchA.complete(Unit)
                latchB.await()
                Either.Right(ValidCard("4111"))
            }
            .withV {
                latchB.complete(Unit)
                latchA.await()
                Either.Right(StockStatus("sku-1"))
            }.evalGraph()

        // Would deadlock if sequential
        assertEquals(Either.Right(ValidCard("4111") to StockStatus("sku-1")), result)
    }

    @Test
    fun `liftV+apV with five parallel validations - barrier proof`() = runTest {
        val latches = (0 until 5).map { CompletableDeferred<Unit>() }

        val result = kapV<String, String, String, String, String, String, String> { a, b, c, d, e -> "$a|$b|$c|$d|$e" }
            .withV { latches[0].complete(Unit); latches.awaitOthers(0); Either.Right("A") }
            .withV { latches[1].complete(Unit); latches.awaitOthers(1); Either.Right("B") }
            .withV { latches[2].complete(Unit); latches.awaitOthers(2); Either.Right("C") }
            .withV { latches[3].complete(Unit); latches.awaitOthers(3); Either.Right("D") }
            .withV { latches[4].complete(Unit); latches.awaitOthers(4); Either.Right("E") }.evalGraph()

        assertEquals(Either.Right("A|B|C|D|E"), result)
    }

    // ════════════════════════════════════════════════════════════════════════
    // Sequential validation via andThen chaining
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `sequential liftV+apV accumulates errors across phases`() = runTest {
        val result = kapV<Err, ValidCard, StockStatus, Pair<ValidCard, StockStatus>> { card, stock -> card to stock }
            .withV { Either.Left(nonEmptyListOf(Err.InvalidCard("declined"))) }
            .withV { Either.Left(nonEmptyListOf(Err.OutOfStock("sku-7"))) }.evalGraph()

        assertIs<Either.Left<NonEmptyList<Err>>>(result)
        assertEquals(2, result.value.size)
        assertIs<Err.InvalidCard>(result.value[0])
        assertIs<Err.OutOfStock>(result.value[1])
    }

    @Test
    fun `sequential thenV enforces order`() = runTest {
        val order = mutableListOf<String>()

        val result = kapV<String, String, String, Pair<String, String>> { a, b -> a to b }
            .withV { order.add("first"); Either.Right("A") as Either<NonEmptyList<String>, String> }
            .thenV { order.add("second"); Either.Right("B") as Either<NonEmptyList<String>, String> }.evalGraph()

        assertEquals(Either.Right("A" to "B"), result)
        assertEquals(listOf("first", "second"), order)
    }

    @Test
    fun `thenV short-circuits on left error - does not execute right side`() = runTest {
        val secondCalled = CompletableDeferred<Boolean>()

        val result = kapV<String, String, String, Pair<String, String>> { a, b -> a to b }
            .withV { Either.Left(nonEmptyListOf("left failed")) as Either<NonEmptyList<String>, String> }
            .thenV {
                secondCalled.complete(true)
                Either.Right("should not run") as Either<NonEmptyList<String>, String>
            }.evalGraph()

        assertIs<Either.Left<NonEmptyList<String>>>(result)
        assertEquals(listOf("left failed"), result.value.toList())
        assertTrue(!secondCalled.isCompleted)
    }

    // ════════════════════════════════════════════════════════════════════════
    // MIXED liftV+apV for multi-phase validation
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `liftV+apV e-commerce validation accumulates all errors`() = runTest {
        // All three validations fail
        val result = kapV<Err, ValidCard, StockStatus, VerifiedAddress, ValidatedCheckout>(::ValidatedCheckout)
            .withV { Either.Left(nonEmptyListOf(Err.InvalidCard("expired"))) }
            .withV { Either.Left(nonEmptyListOf(Err.OutOfStock("sku-123"))) }
            .withV { Either.Left(nonEmptyListOf(Err.BadAddress("missing zip"))) }.evalGraph()

        assertIs<Either.Left<NonEmptyList<Err>>>(result)
        assertEquals(3, result.value.size)
        assertIs<Err.InvalidCard>(result.value[0])
        assertIs<Err.OutOfStock>(result.value[1])
        assertIs<Err.BadAddress>(result.value[2])
    }

    // ════════════════════════════════════════════════════════════════════════
    // catching — bridge from exceptions to validated
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `catching wraps exception as Left`() = runTest {
        val result = Kap<String> { throw RuntimeException("boom") }
            .catching { it.message ?: "unknown" }.evalGraph()

        assertIs<Either.Left<NonEmptyList<String>>>(result)
        assertEquals(listOf("boom"), result.value.toList())
    }

    @Test
    fun `catching wraps success as Right`() = runTest {
        val result = Kap.of("ok").catching { it.message ?: "unknown" }.evalGraph()

        assertEquals(Either.Right("ok"), result)
    }

    @Test
    fun `catching does not catch CancellationException`() = runTest {
        val result = runCatching {
                        Kap<String> { throw CancellationException("cancelled") }
                .catching { "caught" }.evalGraph()
        }

        assertTrue(result.isFailure)
        assertIs<CancellationException>(result.exceptionOrNull())
    }

    @Test
    fun `catching composes with liftV+apV for mixed error sources`() = runTest {
        val result = kapV<String, String, String, String> { a, b -> "$a|$b" }
            .withV {
                try {
                    Either.Right(throw RuntimeException("network error")) as Either<NonEmptyList<String>, String>
                } catch (e: Throwable) {
                    Either.Left(nonEmptyListOf(e.message ?: "unknown"))
                }
            }
            .withV { Either.Left(nonEmptyListOf("validation error")) }.evalGraph()

        assertIs<Either.Left<NonEmptyList<String>>>(result)
        assertEquals(listOf("network error", "validation error"), result.value.toList())
    }

    // ════════════════════════════════════════════════════════════════════════
    // validate — predicate-based
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `validate passes when predicate returns null`() = runTest {
        val result = Kap.of(42).validate<String, Int> { if (it > 0) null else "must be positive" }.evalGraph()
        assertEquals(Either.Right(42), result)
    }

    @Test
    fun `validate fails when predicate returns error`() = runTest {
        val result = Kap.of(-1).validate<String, Int> { if (it > 0) null else "must be positive" }.evalGraph()
        assertIs<Either.Left<NonEmptyList<String>>>(result)
        assertEquals(listOf("must be positive"), result.value.toList())
    }

    // ════════════════════════════════════════════════════════════════════════
    // traverseV — parallel traverse with accumulation
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `traverseV accumulates all errors from collection`() = runTest {
        val result = listOf(1, -2, 3, -4, 5).traverseV<String, Int, String> { n ->
            if (n > 0) valid(n.toString())
            else invalid("negative: $n")
        }.evalGraph()

        assertIs<Either.Left<NonEmptyList<String>>>(result)
        assertEquals(listOf("negative: -2", "negative: -4"), result.value.toList())
    }

    @Test
    fun `traverseV returns all results when all succeed`() = runTest {
        val result = listOf(1, 2, 3).traverseV<String, Int, Int> { n -> valid(n * 10) }.evalGraph()
        assertEquals(Either.Right(listOf(10, 20, 30)), result)
    }

    @Test
    fun `traverseV runs in parallel - barrier proof`() = runTest {
        val latches = (0 until 3).map { CompletableDeferred<Unit>() }

        val result = (0 until 3).toList().traverseV<String, Int, String> { i ->
            Kap {
                latches[i].complete(Unit)
                latches.awaitOthers(i)
                Either.Right("v$i")
            }
        }.evalGraph()

        assertEquals(Either.Right(listOf("v0", "v1", "v2")), result)
    }

    @Test
    fun `traverseV on empty list returns Right empty list`() = runTest {
        val result = emptyList<Int>().traverseV<String, Int, Int> { n -> valid(n) }.evalGraph()
        assertEquals(Either.Right(emptyList<Int>()), result)
    }

    // ════════════════════════════════════════════════════════════════════════
    // orThrow — unwrap validated
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `orThrow returns value on Right`() = runTest {
        val result = valid<String, Int>(42).orThrow().evalGraph()
        assertEquals(42, result)
    }

    @Test
    fun `orThrow throws ValidationException on Left`() = runTest {
        val result = runCatching {
            invalid<String, Int>("err1").orThrow().evalGraph()
        }

        assertTrue(result.isFailure)
        assertIs<ValidationException>(result.exceptionOrNull())
        val ex = result.exceptionOrNull() as ValidationException
        assertEquals(1, ex.errors.size)
    }

    // ════════════════════════════════════════════════════════════════════════
    // sequenceV — parallel execution with accumulation
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `sequenceV accumulates errors from list of computations`() = runTest {
        val computations = listOf(
            valid<String, Int>(1),
            invalid<String, Int>("err1"),
            valid<String, Int>(3),
            invalid<String, Int>("err2"),
        )

        val result = computations.sequenceV().evalGraph()

        assertIs<Either.Left<NonEmptyList<String>>>(result)
        assertEquals(listOf("err1", "err2"), result.value.toList())
    }

    @Test
    fun `sequenceV returns all results when all succeed`() = runTest {
        val computations = listOf(
            valid<String, Int>(1),
            valid<String, Int>(2),
            valid<String, Int>(3),
        )

        val result = computations.sequenceV().evalGraph()
        assertEquals(Either.Right(listOf(1, 2, 3)), result)
    }

    @Test
    fun `sequenceV runs in parallel - barrier proof`() = runTest {
        val latches = (0 until 3).map { CompletableDeferred<Unit>() }

        val computations = (0 until 3).map { i ->
            Kap<Either<NonEmptyList<String>, String>> {
                latches[i].complete(Unit)
                latches.awaitOthers(i)
                Either.Right("v$i")
            }
        }

        val result = computations.sequenceV().evalGraph()
        assertEquals(Either.Right(listOf("v0", "v1", "v2")), result)
    }

    // ════════════════════════════════════════════════════════════════════════
    // andThenV — monadic bind for validated (short-circuit)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `andThenV chains on success`() = runTest {
        val result = valid<String, Int>(42)
            .andThenV { n -> valid<String, String>("result=$n") }.evalGraph()
        assertEquals(Either.Right("result=42"), result)
    }

    @Test
    fun `andThenV short-circuits on first error`() = runTest {
        val secondCalled = CompletableDeferred<Boolean>()

        val result = invalid<String, Int>("first error")
            .andThenV { n ->
                secondCalled.complete(true)
                valid<String, String>("result=$n")
            }.evalGraph()

        assertIs<Either.Left<NonEmptyList<String>>>(result)
        assertEquals(listOf("first error"), result.value.toList())
        // Second computation was never executed
        assertTrue(!secondCalled.isCompleted)
    }

    @Test
    fun `andThenV propagates error from second step`() = runTest {
        val result = valid<String, Int>(42)
            .andThenV { invalid<String, String>("second error") }.evalGraph()

        assertIs<Either.Left<NonEmptyList<String>>>(result)
        assertEquals(listOf("second error"), result.value.toList())
    }

    @Test
    fun `andThenV chains multiple steps`() = runTest {
        val result = valid<String, Int>(10)
            .andThenV { n -> valid<String, Int>(n + 1) }
            .andThenV { n -> valid<String, Int>(n * 2) }
            .andThenV { n -> valid<String, String>("final=$n") }.evalGraph()
        assertEquals(Either.Right("final=22"), result)
    }

    @Test
    fun `andThenV composes with apV - dependent validation after parallel`() = runTest {
        fun validateName(name: String): Kap<Either<NonEmptyList<String>, String>> =
            if (name.length >= 2) valid(name) else invalid("name too short")
        fun validateEmail(email: String): Kap<Either<NonEmptyList<String>, String>> =
            if ("@" in email) valid(email) else invalid("invalid email")
        fun checkNotTaken(name: String, email: String): Kap<Either<NonEmptyList<String>, Pair<String, String>>> =
            valid(name to email) // pretend it's available

        val result = // Phase 1: parallel validation with error accumulation
        kapV<String, String, String, Pair<String, String>> { a, b -> a to b }
            .withV(validateName("Alice"))
            .withV(validateEmail("alice@test.com"))
            // Phase 2: sequential check depending on phase 1 values
            .andThenV { (name, email) -> checkNotTaken(name, email) }.evalGraph()

        assertEquals(Either.Right("Alice" to "alice@test.com"), result)
    }

    // ════════════════════════════════════════════════════════════════════════
    // REAL-WORLD SCENARIO
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `complete validation scenario - user registration`() = runTest {
        fun validateName(name: String): Either<NonEmptyList<RegError>, String> =
            if (name.length >= 2) Either.Right(name)
            else Either.Left(nonEmptyListOf(RegError.NameTooShort("Name must be >= 2 chars")))

        fun validateEmail(email: String): Either<NonEmptyList<RegError>, String> =
            if ("@" in email) Either.Right(email)
            else Either.Left(nonEmptyListOf(RegError.InvalidEmail("Missing @")))

        fun validateAge(age: Int): Either<NonEmptyList<RegError>, Int> =
            if (age >= 18) Either.Right(age)
            else Either.Left(nonEmptyListOf(RegError.TooYoung("Must be >= 18")))

        // All invalid
        val allBad = kapV<RegError, String, String, Int, User>(::User)
            .withV { validateName("X") }
            .withV { validateEmail("bad") }
            .withV { validateAge(15) }.evalGraph()

        assertIs<Either.Left<NonEmptyList<RegError>>>(allBad)
        assertEquals(3, allBad.value.size)

        // All valid
        val allGood = kapV<RegError, String, String, Int, User>(::User)
            .withV { validateName("Alice") }
            .withV { validateEmail("alice@example.com") }
            .withV { validateAge(25) }.evalGraph()

        assertEquals(Either.Right(User("Alice", "alice@example.com", 25)), allGood)
    }

    // ════════════════════════════════════════════════════════════════════════
    // zipV — parallel validation with full type inference
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `zipV infers all types - no explicit type params needed`() = runTest {
        fun validateName(name: String): Either<NonEmptyList<String>, String> =
            if (name.length >= 2) Either.Right(name)
            else Either.Left(nonEmptyListOf("name too short"))

        fun validateEmail(email: String): Either<NonEmptyList<String>, String> =
            if ("@" in email) Either.Right(email)
            else Either.Left(nonEmptyListOf("invalid email"))

        fun validateAge(age: Int): Either<NonEmptyList<String>, Int> =
            if (age >= 18) Either.Right(age)
            else Either.Left(nonEmptyListOf("too young"))

        // No type params needed — compare with kapV<String, String, String, Int, User>(::User)
        val result = zipV(
            { validateName("Alice") },
            { validateEmail("alice@test.com") },
            { validateAge(25) },
        ) { name, email, age -> User(name, email, age) }.evalGraph()

        assertEquals(Either.Right(User("Alice", "alice@test.com", 25)), result)
    }

    @Test
    fun `zipV accumulates errors from all branches`() = runTest {
        val result = zipV(
            { Either.Left(nonEmptyListOf("err1")) },
            { Either.Left(nonEmptyListOf("err2")) },
            { Either.Left(nonEmptyListOf("err3")) },
        ) { a: String, b: String, c: String -> "$a|$b|$c" }.evalGraph()

        assertIs<Either.Left<NonEmptyList<String>>>(result)
        assertEquals(listOf("err1", "err2", "err3"), result.value.toList())
    }

    @Test
    fun `zipV runs in parallel - barrier proof`() = runTest {
        val latches = (0 until 3).map { CompletableDeferred<Unit>() }

        val result = zipV(
            { latches[0].complete(Unit); latches.awaitOthers(0); Either.Right("A") as Either<NonEmptyList<String>, String> },
            { latches[1].complete(Unit); latches.awaitOthers(1); Either.Right("B") },
            { latches[2].complete(Unit); latches.awaitOthers(2); Either.Right("C") },
        ) { a, b, c -> "$a|$b|$c" }.evalGraph()

        assertEquals(Either.Right("A|B|C"), result)
    }

    @Test
    fun `zipV2 with two validations`() = runTest {
        val result = zipV(
            { Either.Left(nonEmptyListOf("e1")) },
            { Either.Left(nonEmptyListOf("e2")) },
        ) { a: String, b: String -> "$a|$b" }.evalGraph()

        assertIs<Either.Left<NonEmptyList<String>>>(result)
        assertEquals(listOf("e1", "e2"), result.value.toList())
    }

    @Test
    fun `zipV4 with four validations`() = runTest {
        val result = zipV(
            { Either.Right("a") as Either<NonEmptyList<String>, String> },
            { Either.Right("b") },
            { Either.Right("c") },
            { Either.Right("d") },
        ) { a, b, c, d -> "$a$b$c$d" }.evalGraph()

        assertEquals(Either.Right("abcd"), result)
    }

    // ════════════════════════════════════════════════════════════════════════
    // zipV at scale — 12 fields, real-world user onboarding form
    // ════════════════════════════════════════════════════════════════════════

    // Domain types — each field has its own validated wrapper
    private data class ValidFirstName(val v: String)
    private data class ValidLastName(val v: String)
    private data class ValidEmail2(val v: String)
    private data class ValidPhone(val v: String)
    private data class ValidPassword(val v: String)
    private data class ValidBirthDate(val v: String)
    private data class ValidCountry(val v: String)
    private data class ValidCity(val v: String)
    private data class ValidZipCode(val v: String)
    private data class ValidAddress(val v: String)
    private data class ValidTaxId(val v: String)
    private data class AcceptedTerms(val v: Boolean)

    private data class UserOnboarding(
        val firstName: ValidFirstName, val lastName: ValidLastName,
        val email: ValidEmail2, val phone: ValidPhone,
        val password: ValidPassword, val birthDate: ValidBirthDate,
        val country: ValidCountry, val city: ValidCity,
        val zipCode: ValidZipCode, val address: ValidAddress,
        val taxId: ValidTaxId, val terms: AcceptedTerms,
    )

    private sealed class OnboardingError(val field: String, val msg: String) {
        class FirstName(msg: String) : OnboardingError("firstName", msg)
        class LastName(msg: String) : OnboardingError("lastName", msg)
        class Email(msg: String) : OnboardingError("email", msg)
        class Phone(msg: String) : OnboardingError("phone", msg)
        class Password(msg: String) : OnboardingError("password", msg)
        class BirthDate(msg: String) : OnboardingError("birthDate", msg)
        class Country(msg: String) : OnboardingError("country", msg)
        class City(msg: String) : OnboardingError("city", msg)
        class ZipCode(msg: String) : OnboardingError("zipCode", msg)
        class Address(msg: String) : OnboardingError("address", msg)
        class TaxId(msg: String) : OnboardingError("taxId", msg)
        class Terms(msg: String) : OnboardingError("terms", msg)

        override fun equals(other: Any?) = other is OnboardingError && field == other.field && msg == other.msg
        override fun hashCode() = field.hashCode() * 31 + msg.hashCode()
        override fun toString() = "${this::class.simpleName}($msg)"
    }

    // 12 validators — each returns its own type
    private fun valFirstName(s: String): Either<NonEmptyList<OnboardingError>, ValidFirstName> =
        if (s.length >= 2) Either.Right(ValidFirstName(s)) else Either.Left(nonEmptyListOf(OnboardingError.FirstName("too short")))
    private fun valLastName(s: String): Either<NonEmptyList<OnboardingError>, ValidLastName> =
        if (s.length >= 2) Either.Right(ValidLastName(s)) else Either.Left(nonEmptyListOf(OnboardingError.LastName("too short")))
    private fun valEmail(s: String): Either<NonEmptyList<OnboardingError>, ValidEmail2> =
        if ("@" in s) Either.Right(ValidEmail2(s)) else Either.Left(nonEmptyListOf(OnboardingError.Email("missing @")))
    private fun valPhone(s: String): Either<NonEmptyList<OnboardingError>, ValidPhone> =
        if (s.length >= 8) Either.Right(ValidPhone(s)) else Either.Left(nonEmptyListOf(OnboardingError.Phone("too short")))
    private fun valPassword(s: String): Either<NonEmptyList<OnboardingError>, ValidPassword> =
        if (s.length >= 8) Either.Right(ValidPassword(s)) else Either.Left(nonEmptyListOf(OnboardingError.Password("too weak")))
    private fun valBirthDate(s: String): Either<NonEmptyList<OnboardingError>, ValidBirthDate> =
        if (s.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) Either.Right(ValidBirthDate(s)) else Either.Left(nonEmptyListOf(OnboardingError.BirthDate("bad format")))
    private fun valCountry(s: String): Either<NonEmptyList<OnboardingError>, ValidCountry> =
        if (s.length == 2) Either.Right(ValidCountry(s)) else Either.Left(nonEmptyListOf(OnboardingError.Country("must be ISO 2-letter")))
    private fun valCity(s: String): Either<NonEmptyList<OnboardingError>, ValidCity> =
        if (s.isNotBlank()) Either.Right(ValidCity(s)) else Either.Left(nonEmptyListOf(OnboardingError.City("blank")))
    private fun valZipCode(s: String): Either<NonEmptyList<OnboardingError>, ValidZipCode> =
        if (s.matches(Regex("\\d{4,10}"))) Either.Right(ValidZipCode(s)) else Either.Left(nonEmptyListOf(OnboardingError.ZipCode("invalid")))
    private fun valAddress(s: String): Either<NonEmptyList<OnboardingError>, ValidAddress> =
        if (s.length >= 5) Either.Right(ValidAddress(s)) else Either.Left(nonEmptyListOf(OnboardingError.Address("too short")))
    private fun valTaxId(s: String): Either<NonEmptyList<OnboardingError>, ValidTaxId> =
        if (s.matches(Regex("\\d{8,12}"))) Either.Right(ValidTaxId(s)) else Either.Left(nonEmptyListOf(OnboardingError.TaxId("invalid format")))
    private fun valTerms(accepted: Boolean): Either<NonEmptyList<OnboardingError>, AcceptedTerms> =
        if (accepted) Either.Right(AcceptedTerms(true)) else Either.Left(nonEmptyListOf(OnboardingError.Terms("must accept")))

    @Test
    fun `zipV12 - all valid - full user onboarding`() = runTest {
        val result = zipV(
            { valFirstName("Alice") },
            { valLastName("Smith") },
            { valEmail("alice@example.com") },
            { valPhone("+541155551234") },
            { valPassword("s3cur3p@ss") },
            { valBirthDate("1990-05-15") },
            { valCountry("AR") },
            { valCity("Buenos Aires") },
            { valZipCode("1425") },
            { valAddress("Av. Corrientes 1234") },
            { valTaxId("20345678901") },
            { valTerms(true) },
        ) { fn, ln, em, ph, pw, bd, co, ci, zc, ad, tx, tm ->
            UserOnboarding(fn, ln, em, ph, pw, bd, co, ci, zc, ad, tx, tm)
        }.evalGraph()

        assertEquals(
            Either.Right(UserOnboarding(
                ValidFirstName("Alice"), ValidLastName("Smith"),
                ValidEmail2("alice@example.com"), ValidPhone("+541155551234"),
                ValidPassword("s3cur3p@ss"), ValidBirthDate("1990-05-15"),
                ValidCountry("AR"), ValidCity("Buenos Aires"),
                ValidZipCode("1425"), ValidAddress("Av. Corrientes 1234"),
                ValidTaxId("20345678901"), AcceptedTerms(true),
            )),
            result,
        )
    }

    @Test
    fun `zipV12 - all invalid - accumulates 12 errors`() = runTest {
        val result = zipV(
            { valFirstName("A") },           // too short
            { valLastName("B") },             // too short
            { valEmail("bad") },              // missing @
            { valPhone("123") },              // too short
            { valPassword("1234") },          // too weak
            { valBirthDate("not-a-date") },   // bad format
            { valCountry("ARG") },            // not ISO 2-letter
            { valCity("") },                  // blank
            { valZipCode("ab") },             // invalid
            { valAddress("Hi") },             // too short
            { valTaxId("123") },              // invalid format
            { valTerms(false) },              // must accept
        ) { fn, ln, em, ph, pw, bd, co, ci, zc, ad, tx, tm ->
            UserOnboarding(fn, ln, em, ph, pw, bd, co, ci, zc, ad, tx, tm)
        }.evalGraph()

        assertIs<Either.Left<NonEmptyList<OnboardingError>>>(result)
        assertEquals(12, result.value.size)
        // Verify error order matches parameter order
        assertIs<OnboardingError.FirstName>(result.value[0])
        assertIs<OnboardingError.LastName>(result.value[1])
        assertIs<OnboardingError.Email>(result.value[2])
        assertIs<OnboardingError.Phone>(result.value[3])
        assertIs<OnboardingError.Password>(result.value[4])
        assertIs<OnboardingError.BirthDate>(result.value[5])
        assertIs<OnboardingError.Country>(result.value[6])
        assertIs<OnboardingError.City>(result.value[7])
        assertIs<OnboardingError.ZipCode>(result.value[8])
        assertIs<OnboardingError.Address>(result.value[9])
        assertIs<OnboardingError.TaxId>(result.value[10])
        assertIs<OnboardingError.Terms>(result.value[11])
    }

    @Test
    fun `zipV12 - mix of valid and invalid - accumulates only failures`() = runTest {
        val result = zipV(
            { valFirstName("Alice") },        // OK
            { valLastName("B") },             // FAIL
            { valEmail("alice@test.com") },   // OK
            { valPhone("123") },              // FAIL
            { valPassword("s3cur3p@ss") },    // OK
            { valBirthDate("not-a-date") },   // FAIL
            { valCountry("AR") },             // OK
            { valCity("Buenos Aires") },      // OK
            { valZipCode("ab") },             // FAIL
            { valAddress("Av. Corrientes") }, // OK
            { valTaxId("123") },              // FAIL
            { valTerms(true) },               // OK
        ) { fn, ln, em, ph, pw, bd, co, ci, zc, ad, tx, tm ->
            UserOnboarding(fn, ln, em, ph, pw, bd, co, ci, zc, ad, tx, tm)
        }.evalGraph()

        assertIs<Either.Left<NonEmptyList<OnboardingError>>>(result)
        assertEquals(5, result.value.size)
        assertIs<OnboardingError.LastName>(result.value[0])
        assertIs<OnboardingError.Phone>(result.value[1])
        assertIs<OnboardingError.BirthDate>(result.value[2])
        assertIs<OnboardingError.ZipCode>(result.value[3])
        assertIs<OnboardingError.TaxId>(result.value[4])
    }

    // ════════════════════════════════════════════════════════════════════════
    // zipV + andThenV — phased validation with type inference
    //
    // Phase 1: validate identity fields (parallel, accumulate)
    // Phase 2: validate address fields (parallel, accumulate)
    //          — only runs if phase 1 passes (short-circuit)
    // ════════════════════════════════════════════════════════════════════════

    private data class IdentityInfo(
        val firstName: ValidFirstName, val lastName: ValidLastName,
        val email: ValidEmail2, val phone: ValidPhone,
        val password: ValidPassword, val birthDate: ValidBirthDate,
    )

    private data class AddressInfo(
        val country: ValidCountry, val city: ValidCity,
        val zipCode: ValidZipCode, val address: ValidAddress,
    )

    private data class FullRegistration(val identity: IdentityInfo, val address: AddressInfo, val taxId: ValidTaxId, val terms: AcceptedTerms)

    @Test
    fun `zipV + andThenV - phased validation - both phases pass`() = runTest {
        val result = // Phase 1: identity (6 parallel validations)
        zipV(
            { valFirstName("Alice") },
            { valLastName("Smith") },
            { valEmail("alice@test.com") },
            { valPhone("+541155551234") },
            { valPassword("s3cur3p@ss") },
            { valBirthDate("1990-05-15") },
        ) { fn, ln, em, ph, pw, bd -> IdentityInfo(fn, ln, em, ph, pw, bd) }
        // Phase 2: address + extras (4 parallel validations, only if phase 1 passes)
        .andThenV { identity ->
            zipV(
                { valCountry("AR") },
                { valCity("Buenos Aires") },
                { valZipCode("1425") },
                { valAddress("Av. Corrientes 1234") },
                { valTaxId("20345678901") },
                { valTerms(true) },
            ) { co, ci, zc, ad, tx, tm -> FullRegistration(identity, AddressInfo(co, ci, zc, ad), tx, tm) }
        }.evalGraph()

        assertIs<Either.Right<FullRegistration>>(result)
        assertEquals("Alice", result.value.identity.firstName.v)
        assertEquals("Buenos Aires", result.value.address.city.v)
    }

    @Test
    fun `zipV + andThenV - phase 1 fails - phase 2 never runs`() = runTest {
        var phase2Ran = false

        val result = zipV(
            { valFirstName("A") },       // FAIL
            { valLastName("B") },         // FAIL
            { valEmail("bad") },          // FAIL
            { valPhone("123") },          // FAIL
            { valPassword("weak") },      // FAIL
            { valBirthDate("nope") },     // FAIL
        ) { fn, ln, em, ph, pw, bd -> IdentityInfo(fn, ln, em, ph, pw, bd) }
        .andThenV { identity ->
            phase2Ran = true
            zipV(
                { valCountry("AR") },
                { valCity("Buenos Aires") },
                { valZipCode("1425") },
                { valAddress("Av. Corrientes 1234") },
                { valTaxId("20345678901") },
                { valTerms(true) },
            ) { co, ci, zc, ad, tx, tm -> FullRegistration(identity, AddressInfo(co, ci, zc, ad), tx, tm) }
        }.evalGraph()

        assertIs<Either.Left<NonEmptyList<OnboardingError>>>(result)
        assertEquals(6, result.value.size)  // all 6 phase-1 errors accumulated
        assertEquals(false, phase2Ran)      // phase 2 was short-circuited
    }

    @Test
    fun `zipV + andThenV - phase 1 passes - phase 2 fails and accumulates`() = runTest {
        val result = zipV(
            { valFirstName("Alice") },
            { valLastName("Smith") },
            { valEmail("alice@test.com") },
            { valPhone("+541155551234") },
            { valPassword("s3cur3p@ss") },
            { valBirthDate("1990-05-15") },
        ) { fn, ln, em, ph, pw, bd -> IdentityInfo(fn, ln, em, ph, pw, bd) }
        .andThenV { identity ->
            zipV(
                { valCountry("ARG") },   // FAIL — not ISO 2-letter
                { valCity("") },          // FAIL — blank
                { valZipCode("ab") },     // FAIL — invalid
                { valAddress("Hi") },     // FAIL — too short
                { valTaxId("123") },      // FAIL — invalid
                { valTerms(false) },      // FAIL — must accept
            ) { co, ci, zc, ad, tx, tm -> FullRegistration(identity, AddressInfo(co, ci, zc, ad), tx, tm) }
        }.evalGraph()

        assertIs<Either.Left<NonEmptyList<OnboardingError>>>(result)
        assertEquals(6, result.value.size)  // all 6 phase-2 errors accumulated
        assertIs<OnboardingError.Country>(result.value[0])
        assertIs<OnboardingError.Terms>(result.value[5])
    }
}
